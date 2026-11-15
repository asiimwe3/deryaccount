package com.derycode.deryaccount.desktop.data

import java.util.UUID
import kotlin.math.abs

/**
 * Double-entry engine. Every posting is validated (debits == credits) and written
 * in one transaction, so the ledger can never drift from the trading records.
 */
class Accounting(private val db: DatabaseShop) {

    data class Line(val accountId: String, val debit: Double = 0.0, val credit: Double = 0.0)

    fun post(
        particulars: String,
        source: String,
        debits: List<Pair<String, Double>>,
        credits: List<Pair<String, Double>>,
        date: String = Clock.nowIso(),
        branchId: String? = null
    ): String {
        val d = Fmt.r2(debits.sumOf { it.second })
        val c = Fmt.r2(credits.sumOf { it.second })
        require(abs(d - c) < 0.01) { "Entry does not balance: Dr $d vs Cr $c" }
        require(d > 0) { "Entry amount must be greater than zero" }
        val prefix = when (source) {
            "POS" -> "POS"
            "EXPENSE" -> "PV"
            "CAPITAL" -> "CAP"
            "OPENING" -> "OS"
            "PURCHASE" -> "PU"
            "RETURN" -> "RT"
            "PAYROLL" -> "PY"
            else -> "JV"
        }
        val entryId = UUID.randomUUID().toString()
        db.insert("journal_entries", mapOf(
            "id" to entryId, "entry_date" to date, "voucher_no" to nextVoucher(prefix),
            "particulars" to particulars, "source" to source, "branch_id" to branchId,
            "created_at" to Clock.nowIso()
        ))
        debits.filter { it.second > 0 }.forEach {
            db.insert("journal_lines", mapOf(
                "id" to UUID.randomUUID().toString(), "entry_id" to entryId,
                "account_id" to it.first, "debit" to Fmt.r2(it.second), "credit" to 0.0
            ))
        }
        credits.filter { it.second > 0 }.forEach {
            db.insert("journal_lines", mapOf(
                "id" to UUID.randomUUID().toString(), "entry_id" to entryId,
                "account_id" to it.first, "debit" to 0.0, "credit" to Fmt.r2(it.second)
            ))
        }
        return entryId
    }

    private fun nextVoucher(prefix: String): String {
        val n = db.count("journal_entries") + 1
        return String.format("%s-%05d", prefix, n)
    }

    // ── Sales ────────────────────────────────────────────────────────────────
    /**
     * Dr cash/bank/debtors (net collected) + Dr sales discounts
     * Cr sales at gross. Then Dr cost of sales / Cr stock at cost.
     */
    fun postSale(
        amount: Double, method: String, receiptNo: String, itemCount: Int,
        costTotal: Double, gross: Double, discount: Double,
        collected: Double = 0.0, vatAmount: Double = 0.0, branchId: String? = null
    ) {
        val detail = if (itemCount > 0) "$itemCount item${if (itemCount == 1) "" else "s"}" else "sale"
        val g = Fmt.r2(if (gross > 0) gross else amount + discount)
        val vat = Fmt.r2(vatAmount.coerceIn(0.0, g))
        if (g > 0) {
            val debits = mutableListOf<Pair<String, Double>>()
            if (method.uppercase() == "CREDIT") {
                // Part cash, part credit: debtors carry only what is still owed.
                val onCredit = Fmt.r2((amount - collected).coerceAtLeast(0.0))
                if (onCredit > 0) debits.add(Accounts.DEBTORS to onCredit)
                if (collected > 0) debits.add(Accounts.CASH to Fmt.r2(collected))
            } else {
                debits.add(Accounts.accountForMethod(method) to Fmt.r2(amount))
            }
            if (discount > 0) debits.add(Accounts.SALES_DISCOUNT to Fmt.r2(discount))
            val credits = mutableListOf(Accounts.SALES to Fmt.r2(g - vat))
            if (vat > 0) credits.add(Accounts.VAT_OUT to vat)
            post(
                particulars = "Sales — $detail ($receiptNo)", source = "POS",
                debits = debits, credits = credits, branchId = branchId
            )
        }
        if (costTotal > 0) {
            post(
                particulars = "Cost of sales — $detail ($receiptNo)", source = "POS",
                debits = listOf(Accounts.COGS to Fmt.r2(costTotal)),
                credits = listOf(Accounts.STOCK to Fmt.r2(costTotal)), branchId = branchId
            )
        }
    }

    fun postSaleReturn(refund: Double, costValue: Double, method: String, note: String) {
        if (refund <= 0) return
        val refundAccount = if (method == "CREDIT") Accounts.DEBTORS else Accounts.CASH
        post(
            particulars = "Sales return ($note)", source = "RETURN",
            debits = listOf(Accounts.SALES_RETURNS to refund),
            credits = listOf(refundAccount to refund)
        )
        if (costValue > 0) post(
            particulars = "Stock returned ($note)", source = "RETURN",
            debits = listOf(Accounts.STOCK to costValue),
            credits = listOf(Accounts.COGS to costValue)
        )
    }

    // ── Purchases & stock ────────────────────────────────────────────────────
    fun postPurchase(amount: Double, paidHow: String, ref: String) {
        if (amount <= 0) return
        val credit = if (paidHow == "CREDIT") Accounts.CREDITORS else Accounts.CASH
        post(
            particulars = "Stock purchase $ref", source = "PURCHASE",
            debits = listOf(Accounts.STOCK to amount), credits = listOf(credit to amount)
        )
    }

    fun postPurchaseReturn(refundMethod: String, costValue: Double, note: String) {
        if (costValue <= 0) return
        val dr = if (refundMethod == "SUPPLIER_CREDIT") Accounts.CREDITORS else Accounts.CASH
        post(
            particulars = "Return to supplier ($note)", source = "STOCK",
            debits = listOf(dr to costValue), credits = listOf(Accounts.STOCK to costValue)
        )
    }

    fun postExpense(amount: Double, category: String, paidFrom: String, note: String) {
        if (amount <= 0) return
        post(
            particulars = "Expense — $category${if (note.isBlank()) "" else " ($note)"}", source = "EXPENSE",
            debits = listOf(Accounts.accountForExpense(category) to amount),
            credits = listOf(Accounts.accountForMethod(paidFrom) to amount)
        )
    }

    fun postCustomerPayment(amount: Double, method: String, customer: String) {
        if (amount <= 0) return
        post(
            particulars = "Customer payment — $customer", source = "RECEIPT",
            debits = listOf(Accounts.accountForMethod(method) to amount),
            credits = listOf(Accounts.DEBTORS to amount)
        )
    }

    fun postSupplierPayment(amount: Double, method: String, supplier: String) {
        if (amount <= 0) return
        post(
            particulars = "Supplier payment — $supplier", source = "EXPENSE",
            debits = listOf(Accounts.CREDITORS to amount),
            credits = listOf(Accounts.accountForMethod(method) to amount)
        )
    }

    fun postStockEdit(oldQty: Double, oldCost: Double, newQty: Double, newCost: Double, note: String) {
        val delta = Fmt.r2(newQty * newCost - oldQty * oldCost)
        val qtyDelta = newQty - oldQty
        when {
            delta > 0 && qtyDelta > 0 -> post(
                particulars = "Stock top-up ($note)", source = "STOCK",
                debits = listOf(Accounts.STOCK to delta), credits = listOf(Accounts.CASH to delta)
            )
            delta > 0 -> post(
                particulars = "Stock revaluation gain ($note)", source = "STOCK",
                debits = listOf(Accounts.STOCK to delta), credits = listOf(Accounts.REVALUATION to delta)
            )
            delta < 0 -> post(
                particulars = "Stock reduction ($note)", source = "STOCK",
                debits = listOf(Accounts.WRITEOFF to -delta), credits = listOf(Accounts.STOCK to -delta)
            )
        }
    }

    fun postCountAdjustment(deltaQty: Double, cost: Double, note: String) {
        val delta = Fmt.r2(deltaQty * cost)
        when {
            delta > 0 -> post(
                particulars = "Stock found in count ($note)", source = "STOCK",
                debits = listOf(Accounts.STOCK to delta), credits = listOf(Accounts.REVALUATION to delta)
            )
            delta < 0 -> post(
                particulars = "Stock shrinkage in count ($note)", source = "STOCK",
                debits = listOf(Accounts.WRITEOFF to -delta), credits = listOf(Accounts.STOCK to -delta)
            )
        }
    }

    fun postOpeningStock(value: Double, note: String) {
        if (value <= 0) return
        post(
            particulars = "Opening stock ($note)", source = "OPENING",
            debits = listOf(Accounts.STOCK to value), credits = listOf(Accounts.CAPITAL to value)
        )
    }

    fun postCapital(amount: Double, note: String = "Owner capital in") {
        if (amount <= 0) return
        post(
            particulars = note, source = "CAPITAL",
            debits = listOf(Accounts.CASH to amount), credits = listOf(Accounts.CAPITAL to amount)
        )
    }

    fun postDepreciation(total: Double, note: String) {
        if (total <= 0) return
        post(
            particulars = "Depreciation — $note", source = "EXPENSE",
            debits = listOf(Accounts.DEPRECIATION to total),
            credits = listOf(Accounts.ACC_DEPR to total)
        )
    }

    /** Dr Salaries (gross), Cr Cash (net), Cr Creditors (statutory deductions owed). */
    fun postPayroll(gross: Double, net: Double, deductions: Double, month: String) {
        if (gross <= 0) return
        val credits = mutableListOf(Accounts.CASH to net)
        if (deductions > 0) credits.add(Accounts.CREDITORS to deductions)
        post(
            particulars = "Payroll — ${Fmt.month(month)}", source = "PAYROLL",
            debits = listOf(Accounts.SALARY to gross), credits = credits
        )
    }

    fun postVatRemittance(amount: Double, period: String) {
        if (amount <= 0) return
        post(
            particulars = "VAT remittance — $period", source = "EXPENSE",
            debits = listOf(Accounts.VAT_OUT to amount), credits = listOf(Accounts.CASH to amount)
        )
    }

    /** Manual journal entry typed by the owner: two sides, must balance. */
    fun postManual(date: String, particulars: String, debits: List<Pair<String, Double>>, credits: List<Pair<String, Double>>) =
        post(particulars = particulars, source = "MANUAL", debits = debits, credits = credits, date = date)

    // ── Balances ─────────────────────────────────────────────────────────────
    fun balanceOf(accountId: String, from: String = "0000", to: String = "9999"): Double {
        val r = db.one(
            """SELECT COALESCE(SUM(l.debit),0) AS d, COALESCE(SUM(l.credit),0) AS c
               FROM journal_lines l JOIN journal_entries e ON e.id = l.entry_id
               WHERE l.account_id = ? AND e.entry_date >= ? AND e.entry_date <= ?""",
            accountId, from, to
        )
        return Fmt.r2((r?.dbl("d") ?: 0.0) - (r?.dbl("c") ?: 0.0))
    }
}
