package com.derycode.deryaccount.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.derycode.deryaccount.data.local.AppDatabase
import com.derycode.deryaccount.accounting.Money
import com.derycode.deryaccount.data.local.entity.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/**
 * PosRepository — the offline-safe transactional core.
 *
 * checkout() is atomic (Room withTransaction): sale + items + stock
 * movements are written together or not at all. Receipt numbers use
 * BRANCHCODE-YYYYMMDD-#### generated from a per-day counter, so
 * offline receipts never collide with other branches or days.
 */
class PosRepository(private val context: Context, private val db: AppDatabase) {

    data class CartLine(val product: Product, val qty: Double, val unitPrice: Double) {
        val lineTotal: Double get() = (qty * unitPrice * 100).roundToInt() / 100.0
    }

    data class CheckoutResult(
        val sale: Sale,
        val items: List<SaleItem>,
        val change: Double,
        val createdOffline: Boolean
    )

    fun priceFor(product: Product, qty: Double, saleType: String): Double {
        return if (saleType == "WHOLESALE"
            && product.wholesalePrice != null
            && qty >= product.wholesaleMinQty) product.wholesalePrice!!
        else product.retailPrice
    }

    suspend fun checkout(
        branchId: String,
        userId: String,
        lines: List<CartLine>,
        customerId: String?,
        saleType: String,
        amountPaid: Double,
        paymentMethod: String,
        discount: Double = 0.0
    ): CheckoutResult {
        val now = nowIso()
        val saleId = UUID.randomUUID().toString()

        // VAT comes from each product's own rate (0 = exempt), applied to a
        // VAT-inclusive shelf price. Money owns the rounding so the receipt,
        // the customer balance and the ledger all agree to the cent.
        val totals = Money.totals(lines.map { it.lineTotal to it.product.taxRate }, discount)
        val subtotal = totals.subtotal
        val taxTotal = totals.taxTotal
        val total = totals.total
        val change = Money.changeDue(total, amountPaid)
        val unpaid = Money.outstanding(total, amountPaid)

        val sale = Sale(
            id = saleId,
            receiptNo = "",            // allocated inside the transaction below
            branchId = branchId,
            userId = userId,
            customerId = customerId,
            saleType = saleType,
            subtotal = subtotal,
            taxTotal = taxTotal,
            discount = discount,
            total = total,
            amountPaid = amountPaid,
            changeGiven = if (change > 0) change else 0.0,
            paymentMethod = paymentMethod,
            soldAt = now,
            shiftId = null,
            createdAt = now,
            updatedAt = now,
            syncState = "pending",
            isDeleted = false
        )

        val itemsFor: (String) -> List<SaleItem> = { receipt ->
        lines.map {
            SaleItem(
                id = UUID.randomUUID().toString(),
                saleId = saleId,
                productId = it.product.id,
                name = it.product.name,
                qty = it.qty,
                unitPrice = it.unitPrice,
                costPrice = it.product.costPrice,
                lineTotal = it.lineTotal
            )
        }
        }

        val movementsFor: (String) -> List<StockMovement> = { receipt ->
        lines.map {
            StockMovement(
                id = UUID.randomUUID().toString(),
                productId = it.product.id,
                branchId = branchId,
                type = "SALE",
                qty = -it.qty,
                reference = saleId,
                note = receipt,
                movedAt = now,
                createdAt = now, updatedAt = now,
                syncState = "pending", isDeleted = false
            )
        }
        }

        var receiptNo = ""
        db.withTransaction {
            // Allocated INSIDE the transaction: Room serialises writers, so two
            // tills checking out at the same moment can no longer take the same
            // sequence number (the old code counted before the transaction).
            receiptNo = generateReceiptNo(branchId)
            val sale = sale.copy(receiptNo = receiptNo)
            val items = itemsFor(receiptNo)
            val movements = movementsFor(receiptNo)
            db.saleDao().upsert(sale)
            db.saleItemDao().upsertAll(items)
            db.stockMovementDao().upsertAll(movements)
            lines.forEach {
                db.productDao().adjustStock(it.product.id, -it.qty, now)
            }
            // Customer/debtor tracking — on EVERY sale with a customer attached:
            //   totalPurchases grows by the sale value
            //   credit sales grow the balance by the unpaid portion
            //   totalPaid grows by what was actually received (deposit now, or full cash sale)
            if (customerId != null) {
                db.customerDao().get(customerId)?.let { c ->
                    // Part payment is honoured on every method, not just CREDIT:
                    // anything not received becomes a debtor balance.
                    val owed = unpaid
                    val receivedNow = Money.round2(amountPaid.coerceAtLeast(0.0) - change)
                    db.customerDao().upsert(c.copy(
                        balance = Money.round2(c.balance + owed),
                        totalPurchases = Money.round2(c.totalPurchases + total),
                        totalPaid = Money.round2(c.totalPaid + receivedNow),
                        updatedAt = now, syncState = "pending"))
                }
            }
            // Books of account, ATOMIC with the sale itself: Dr Cash/MoMo/Debtors,
            // Cr Sales, plus Dr COGS / Cr Stock at cost. If any part fails the
            // whole sale rolls back — the ledger can never drift from the sales.
            val accounting = com.derycode.deryaccount.accounting.AccountingRepo(db)
            accounting.ensureSeeded()
            accounting.postSale(
                total, paymentMethod, receiptNo,
                items.size,
                lines.sumOf { it.qty * it.product.costPrice },
                gross = subtotal,                // tax-inclusive price before discount
                discount = discount,             // discount → Sales Discounts account
                tax = taxTotal                   // VAT Payable is credited, Sales stays net
            )
        }

        return CheckoutResult(sale.copy(receiptNo = receiptNo), itemsFor(receiptNo), change, createdOffline = true)
    }

    /** BRANCHCODE-YYYYMMDD-#### — monotonic per branch per day, offline safe. */
    private suspend fun generateReceiptNo(branchId: String): String {
        val branch = db.branchDao().get(branchId)
        val code = branch?.name?.take(3)?.uppercase(Locale.US) ?: "BRX"
        val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val isoDate = "${date.substring(0, 4)}-${date.substring(4, 6)}-${date.substring(6, 8)}"
        val count = db.saleDao().countBetween(branchId, "${isoDate}T00:00:00", "${isoDate}T23:59:59")
        return Money.receiptNo(code, date, count + 1)
    }

    private fun nowIso(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())
}
