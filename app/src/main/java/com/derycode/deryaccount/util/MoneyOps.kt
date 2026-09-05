package com.derycode.deryaccount.util

import androidx.room.withTransaction
import com.derycode.deryaccount.accounting.AccountingRepo
import com.derycode.deryaccount.data.local.AppDatabase
import com.derycode.deryaccount.data.local.entity.Customer
import com.derycode.deryaccount.data.local.entity.CustomerPayment
import com.derycode.deryaccount.data.local.entity.Product
import com.derycode.deryaccount.data.local.entity.PurchaseOrder
import com.derycode.deryaccount.data.local.entity.PurchaseOrderItem
import com.derycode.deryaccount.data.local.entity.PurchaseReturn
import com.derycode.deryaccount.data.local.entity.PurchaseReturnItem
import com.derycode.deryaccount.data.local.entity.StockMovement
import com.derycode.deryaccount.data.local.entity.Supplier
import com.derycode.deryaccount.data.local.entity.SupplierPayment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * MoneyOps — v0.12.0 money workflows, each ATOMIC like StockOps:
 * payment record, balances, stock and books move together or not at all.
 */
object MoneyOps {

    private fun nowIso() = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())

    private fun docNo(prefix: String): String =
        "$prefix-" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) +
            "-" + UUID.randomUUID().toString().substring(0, 4).uppercase(Locale.US)

    // ---------------------------------------------------------------
    // Customer payments (debt collection)
    // ---------------------------------------------------------------

    /** Customer pays part or all of their debt. */
    suspend fun receiveCustomerPayment(
        db: AppDatabase, branchId: String, userId: String,
        customer: Customer, amount: Double, method: String, note: String?
    ) {
        db.withTransaction {
            val now = nowIso()
            db.customerPaymentDao().upsert(CustomerPayment(
                id = UUID.randomUUID().toString(), customerId = customer.id,
                branchId = branchId, userId = userId, amount = amount,
                method = method, reference = null,
                note = note?.takeIf { it.isNotBlank() }, paidAt = now,
                createdAt = now, updatedAt = now))
            db.customerDao().upsert(customer.copy(
                balance = (customer.balance - amount).coerceAtLeast(0.0),
                totalPaid = customer.totalPaid + amount,
                updatedAt = now, syncState = "pending"))
            val accounting = AccountingRepo(db)
            accounting.ensureSeeded()
            // Money in: Dr Cash, Cr Debtors
            accounting.recordReceipt(
                AccountingRepo.CASH, AccountingRepo.DEBTORS, amount,
                "Debt payment — ${customer.name}")
        }
    }

    // ---------------------------------------------------------------
    // Supplier payments (what we owe)
    // ---------------------------------------------------------------

    /** Business pays a supplier what it owes them. */
    suspend fun paySupplier(
        db: AppDatabase, branchId: String, userId: String,
        supplier: Supplier, amount: Double, method: String, note: String?
    ) {
        db.withTransaction {
            val now = nowIso()
            db.supplierPaymentDao().upsert(SupplierPayment(
                id = UUID.randomUUID().toString(), supplierId = supplier.id,
                branchId = branchId, userId = userId, amount = amount,
                method = method, reference = null,
                note = note?.takeIf { it.isNotBlank() }, paidAt = now,
                createdAt = now, updatedAt = now))
            db.supplierDao().upsert(supplier.copy(
                balance = (supplier.balance - amount).coerceAtLeast(0.0),
                updatedAt = now, syncState = "pending"))
            val accounting = AccountingRepo(db)
            accounting.ensureSeeded()
            // Money out: Dr Creditors, Cr Cash
            accounting.recordPayment(
                AccountingRepo.CASH, AccountingRepo.CREDITORS, amount,
                "Supplier payment — ${supplier.name}")
        }
    }

    // ---------------------------------------------------------------
    // Purchase returns (goods back to the supplier)
    // ---------------------------------------------------------------

    /**
     * Return stock to a supplier. Stock leaves at cost; we either get cash
     * back or our account with the supplier is reduced by the value.
     */
    suspend fun purchaseReturn(
        db: AppDatabase, branchId: String, userId: String,
        supplier: Supplier?, lines: List<Triple<Product, Double, Double>>, // product, qty, unitCost
        refundMethod: String, note: String?
    ): PurchaseReturn? {
        if (lines.isEmpty()) return null
        var saved: PurchaseReturn? = null
        db.withTransaction {
            val now = nowIso()
            val total = lines.sumOf { it.second * it.third }
            val ret = PurchaseReturn(
                id = UUID.randomUUID().toString(), prNo = docNo("PR"),
                supplierId = supplier?.id, branchId = branchId, userId = userId,
                total = total, refundMethod = refundMethod,
                note = note?.takeIf { it.isNotBlank() },
                returnedAt = now, createdAt = now, updatedAt = now)
            db.purchaseReturnDao().upsert(ret)
            db.purchaseReturnItemDao().upsertAll(lines.map { (p, qty, cost) ->
                PurchaseReturnItem(
                    id = UUID.randomUUID().toString(), purchaseReturnId = ret.id,
                    productId = p.id, name = p.name, qty = qty,
                    unitCost = cost, lineTotal = qty * cost)
            })
            lines.forEach { (p, qty, _) ->
                db.productDao().adjustStock(p.id, -qty, now)
                db.stockMovementDao().upsert(StockMovement(
                    id = UUID.randomUUID().toString(), productId = p.id, branchId = branchId,
                    type = "SUPPLIER_RETURN", qty = -qty, reference = ret.prNo,
                    note = note?.takeIf { it.isNotBlank() } ?: "returned to supplier",
                    movedAt = now, createdAt = now, updatedAt = now))
            }
            // Cash back → nothing changes on the supplier account.
            // On account → we simply owe them less.
            if (refundMethod == "SUPPLIER_CREDIT" && supplier != null) {
                db.supplierDao().upsert(supplier.copy(
                    balance = (supplier.balance - total).coerceAtLeast(0.0),
                    updatedAt = now, syncState = "pending"))
            }
            val accounting = AccountingRepo(db)
            accounting.ensureSeeded()
            accounting.postPurchaseReturn(refundMethod, total,
                note?.takeIf { it.isNotBlank() } ?: "purchase return ${ret.prNo}")
            saved = ret
        }
        return saved
    }

    // ---------------------------------------------------------------
    // Purchase orders (promise → stock)
    // ---------------------------------------------------------------

    /** Create a purchase order — no stock, no books. It is a promise. */
    suspend fun createPurchaseOrder(
        db: AppDatabase, branchId: String, userId: String,
        supplierId: String?, lines: List<Triple<Product, Double, Double>>, // product, qty, unitCost
        expectedAt: String?, note: String?
    ): PurchaseOrder? {
        if (lines.isEmpty()) return null
        var saved: PurchaseOrder? = null
        db.withTransaction {
            val now = nowIso()
            val order = PurchaseOrder(
                id = UUID.randomUUID().toString(), poNo = docNo("PO"),
                supplierId = supplierId, branchId = branchId, userId = userId,
                status = "OPEN", expectedAt = expectedAt?.takeIf { it.isNotBlank() },
                note = note?.takeIf { it.isNotBlank() },
                orderedAt = now, createdAt = now, updatedAt = now)
            db.purchaseOrderDao().upsert(order)
            db.purchaseOrderItemDao().upsertAll(lines.map { (p, qty, cost) ->
                PurchaseOrderItem(
                    id = UUID.randomUUID().toString(), orderId = order.id,
                    productId = p.id, name = p.name, qty = qty,
                    unitCost = cost, lineTotal = qty * cost)
            })
            saved = order
        }
        return saved
    }

    /**
     * Receive a purchase order into stock. Each line becomes a stock-in at
     * the ordered cost (reusing the audited StockOps.receiveStock path),
     * then the order is marked RECEIVED.
     */
    suspend fun receivePurchaseOrder(
        db: AppDatabase, branchId: String,
        order: PurchaseOrder, items: List<PurchaseOrderItem>,
        paidHow: String
    ) {
        val now = nowIso()
        db.purchaseOrderDao().upsert(order.copy(
            status = "RECEIVED", updatedAt = now, syncState = "pending"))
        items.forEach { item ->
            com.derycode.deryaccount.ui.inventory.StockOps.receiveStock(
                db, branchId, item.productId, item.qty, item.unitCost, paidHow,
                "PO ${order.poNo} — ${item.name}")
        }
    }

    suspend fun cancelPurchaseOrder(db: AppDatabase, order: PurchaseOrder) {
        val now = nowIso()
        db.purchaseOrderDao().upsert(order.copy(
            status = "CANCELLED", updatedAt = now, syncState = "pending"))
    }
}
