package com.derycode.deryaccount.ui.inventory

import androidx.room.withTransaction
import com.derycode.deryaccount.accounting.AccountingRepo
import com.derycode.deryaccount.data.local.AppDatabase
import com.derycode.deryaccount.data.local.entity.Purchase
import com.derycode.deryaccount.data.local.entity.PurchaseItem
import com.derycode.deryaccount.data.local.entity.StockMovement
import com.derycode.deryaccount.data.local.entity.StockTransfer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * StockOps — every stock action in one place, each one ATOMIC:
 * the stock list, the movement log and the books of account move
 * together or not at all.
 *
 * Movement types: OPENING | PURCHASE | SALE | RETURN | TRANSFER_OUT |
 * TRANSFER_IN | ADJUSTMENT | DAMAGE | EXPIRY | COUNT
 */
object StockOps {

    private fun nowIso() = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())

    private suspend fun log(db: AppDatabase, productId: String, branchId: String,
                           type: String, qty: Double, reference: String?, note: String?) {
        val now = nowIso()
        db.stockMovementDao().upsert(StockMovement(
            id = UUID.randomUUID().toString(), productId = productId, branchId = branchId,
            type = type, qty = qty, reference = reference, note = note, movedAt = now,
            createdAt = now, updatedAt = now))
    }

    /** Stock received — recorded as a purchase (Dr Stock, Cr Cash/Creditors at cost). */
    suspend fun receiveStock(
        db: AppDatabase, branchId: String, productId: String, qty: Double,
        unitCost: Double, paidHow: String, note: String
    ) {
        db.withTransaction {
            val p = db.productDao().get(productId) ?: return@withTransaction
            val now = nowIso()
            val value = qty * unitCost
            val paid = if (paidHow == "CASH") value else 0.0
            val purchase = Purchase(
                id = UUID.randomUUID().toString(), supplierId = null, branchId = branchId,
                total = value, paidAmount = paid, receivedAt = now,
                note = note.ifBlank { "stock received" }, createdAt = now, updatedAt = now)
            db.purchaseDao().upsert(purchase)
            db.purchaseItemDao().upsert(PurchaseItem(
                id = UUID.randomUUID().toString(), purchaseId = purchase.id, productId = p.id,
                qty = qty, unitCost = unitCost, lineTotal = value))
            db.productDao().adjustStock(p.id, qty, now)
            log(db, p.id, branchId, "PURCHASE", qty, purchase.id, note.ifBlank { "stock received" })
            // Latest cost becomes the product cost — books follow the full value change
            val oldQty = p.stockQty
            val oldCost = p.costPrice
            val newCost = if (unitCost > 0) unitCost else oldCost
            if (unitCost > 0 && unitCost != oldCost) {
                db.productDao().get(p.id)?.let { fresh ->
                    db.productDao().upsert(fresh.copy(
                        costPrice = unitCost, updatedAt = now, syncState = "pending"))
                }
            }
            val accounting = AccountingRepo(db)
            accounting.ensureSeeded()
            if (paidHow == "CREDIT") {
                // On account: the stock value grows, the supplier is owed — Dr Stock, Cr Creditors
                val deltaValue = (oldQty + qty) * newCost - oldQty * oldCost
                if (deltaValue != 0.0) {
                    accounting.post(
                        particulars = "Stock purchase on account — ${p.name}", source = "STOCK",
                        debits = listOf(AccountingRepo.STOCK to deltaValue),
                        credits = listOf(AccountingRepo.CREDITORS to deltaValue))
                }
            } else {
                // Cash: Dr Stock, Cr Cash (handles qty AND cost-price changes together)
                accounting.postStockEdit(oldQty, oldCost, oldQty + qty, newCost, p.name)
            }
        }
    }

    /**
     * Customer returned goods — stock comes back and the sale reverses:
     * Dr Sales Returns, Cr Cash (refund) or Cr Debtors (reduce their debt);
     * Dr Stock, Cr Cost of Sales.
     */
    suspend fun customerReturn(
        db: AppDatabase, branchId: String, productId: String, qty: Double,
        refundAmount: Double, refundMethod: String, customerId: String?, note: String
    ) {
        db.withTransaction {
            val p = db.productDao().get(productId) ?: return@withTransaction
            val now = nowIso()
            db.productDao().adjustStock(p.id, qty, now)
            log(db, p.id, branchId, "RETURN", qty, customerId, note.ifBlank { "customer return" })
            // Customer's debt shrinks if the refund comes off their account
            if (refundMethod == "CREDIT" && customerId != null) {
                db.customerDao().get(customerId)?.let { c ->
                    db.customerDao().upsert(c.copy(
                        balance = (c.balance - refundAmount).coerceAtLeast(0.0),
                        totalPaid = c.totalPaid + refundAmount,
                        updatedAt = now, syncState = "pending"))
                }
            }
            val accounting = AccountingRepo(db)
            accounting.ensureSeeded()
            accounting.postSaleReturn(refundAmount, qty * p.costPrice, refundMethod, p.name)
        }
    }

    /** Damaged stock — written off at cost (Dr Stock Loss, Cr Stock). */
    suspend fun damageStock(db: AppDatabase, branchId: String, productId: String,
                            qty: Double, note: String) {
        db.withTransaction {
            val p = db.productDao().get(productId) ?: return@withTransaction
            val now = nowIso()
            db.productDao().adjustStock(p.id, -qty, now)
            log(db, p.id, branchId, "DAMAGE", -qty, null, note.ifBlank { "damaged" })
            val accounting = AccountingRepo(db)
            accounting.ensureSeeded()
            accounting.postStockWriteOff(qty * p.costPrice, "damage — ${p.name}")
        }
    }

    /** Expired stock — written off at cost, tracked separately from damage. */
    suspend fun expireStock(db: AppDatabase, branchId: String, productId: String,
                            qty: Double, note: String) {
        db.withTransaction {
            val p = db.productDao().get(productId) ?: return@withTransaction
            val now = nowIso()
            db.productDao().adjustStock(p.id, -qty, now)
            log(db, p.id, branchId, "EXPIRY", -qty, null, note.ifBlank { "expired" })
            val accounting = AccountingRepo(db)
            accounting.ensureSeeded()
            accounting.postStockWriteOff(qty * p.costPrice, "expiry — ${p.name}")
        }
    }

    /**
     * Stock count — set the book quantity to the physically counted quantity.
     * The variance becomes a COUNT adjustment, valued at cost in the books.
     */
    suspend fun applyCount(db: AppDatabase, branchId: String, productId: String,
                          countedQty: Double, note: String) {
        db.withTransaction {
            val p = db.productDao().get(productId) ?: return@withTransaction
            val delta = countedQty - p.stockQty
            if (delta == 0.0) return@withTransaction
            val now = nowIso()
            db.productDao().upsert(p.copy(stockQty = countedQty, updatedAt = now, syncState = "pending"))
            log(db, p.id, branchId, "COUNT", delta, null,
                note.ifBlank { "stock count" } + " (was ${fmt(p.stockQty)})")
            val accounting = AccountingRepo(db)
            accounting.ensureSeeded()
            accounting.postStockEdit(p.stockQty, p.costPrice, countedQty, p.costPrice, p.name)
        }
    }

    /**
     * Transfer stock between branches. No book entry — the stock belongs to
     * the same business; only the movement log and branch quantities move.
     * Both branches live in the same local DB, so the transfer is instant.
     */
    suspend fun transferStock(db: AppDatabase, fromBranchId: String,
                              fromProduct: com.derycode.deryaccount.data.local.entity.Product,
                              toBranchId: String, toProductId: String, qty: Double, note: String) {
        db.withTransaction {
            val now = nowIso()
            val transfer = StockTransfer(
                id = UUID.randomUUID().toString(), fromBranchId = fromBranchId,
                toBranchId = toBranchId, productId = fromProduct.id, qty = qty,
                status = "RECEIVED", note = note.ifBlank { "stock transfer" },
                transferredAt = now, createdAt = now, updatedAt = now)
            db.transferDao().upsert(transfer)
            db.productDao().adjustStock(fromProduct.id, -qty, now)
            db.productDao().adjustStock(toProductId, qty, now)
            log(db, fromProduct.id, fromBranchId, "TRANSFER_OUT", -qty, transfer.id, note.ifBlank { "transfer out" })
            log(db, toProductId, toBranchId, "TRANSFER_IN", qty, transfer.id, note.ifBlank { "transfer in" })
        }
    }

    private fun fmt(v: Double): String =
        if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
}
