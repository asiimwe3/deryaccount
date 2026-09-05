package com.derycode.deryaccount.accounting

import com.derycode.deryaccount.data.local.AppDatabase
import com.derycode.deryaccount.data.local.entity.Account
import com.derycode.deryaccount.data.local.entity.JournalEntry
import com.derycode.deryaccount.data.local.entity.JournalLine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * AccountingRepo — the double-entry engine behind the books of account.
 * Every entry is validated (debits == credits) before it touches the ledger.
 * 100% local: no internet, no external services, ever.
 */
class AccountingRepo(private val db: AppDatabase) {

    companion object {
        // Standard chart of accounts familiar to Ugandan bookkeepers
        val COA = listOf(
            Account("acc-cash",   "1000", "Cash on Hand",   "ASSET", isCash = true, sortOrder = 1),
            Account("acc-petty",  "1001", "Petty Cash",     "ASSET", isCash = true, sortOrder = 2),
            Account("acc-bank",   "1010", "Bank / Mobile Money", "ASSET", isCash = true, sortOrder = 3),
            Account("acc-debtors","1100", "Debtors (Receivables)", "ASSET", sortOrder = 4),
            Account("acc-stock",  "1200", "Stock on Hand",  "ASSET", sortOrder = 5),
            Account("acc-creditors","2000","Creditors (Payables)","LIABILITY", sortOrder = 6),
            Account("acc-capital","3000", "Capital",        "EQUITY", sortOrder = 7),
            Account("acc-drawings","3100","Drawings",       "EQUITY", sortOrder = 8),
            Account("acc-sales",  "4000", "Sales / Revenue","INCOME", sortOrder = 9),
            Account("acc-reval",   "4900", "Stock Revaluation Gain", "INCOME", sortOrder = 9),
            Account("acc-returns","4150","Sales Returns",   "INCOME", sortOrder = 9),
            Account("acc-cogs",   "5000", "Cost of Sales",  "EXPENSE", sortOrder = 10),
            Account("acc-purchases","5100","Purchases",     "EXPENSE", sortOrder = 11),
            Account("acc-rent",   "5200", "Rent",           "EXPENSE", sortOrder = 12),
            Account("acc-salaries","5210","Salaries & Wages","EXPENSE", sortOrder = 13),
            Account("acc-transport","5220","Transport & Fuel","EXPENSE", sortOrder = 14),
            Account("acc-utilities","5230","Water & Electricity (UEL)","EXPENSE", sortOrder = 15),
            Account("acc-airtime","5240","Airtime & Data", "EXPENSE", sortOrder = 16),
            Account("acc-sundry", "5900", "Sundry / Other Expenses","EXPENSE", sortOrder = 17),
            Account("acc-assets", "1300", "Fixed Assets",  "ASSET", sortOrder = 5),
            Account("acc-accdepr","1350", "Accumulated Depreciation", "ASSET", sortOrder = 5),
            Account("acc-vatout", "2500", "VAT Payable",   "LIABILITY", sortOrder = 6),
            Account("acc-depexp","5310", "Depreciation",   "EXPENSE", sortOrder = 13)
        )
        val CASH = "acc-cash"
        val PETTY = "acc-petty"
        val BANK = "acc-bank"
        val DEBTORS = "acc-debtors"
        val SALES = "acc-sales"
        val STOCK = "acc-stock"
        val CREDITORS = "acc-creditors"
        val REVAL = "acc-reval"
        val RETURNS = "acc-returns"
        val COGS = "acc-cogs"
        val SUNDAY_RUN = "acc-sundry"
    }

    /**
     * Seed the chart of accounts. Idempotent: any account missing from an
     * existing install (e.g. new accounts added in later versions) is added,
     * existing accounts are never overwritten.
     */
    suspend fun ensureSeeded() {
        val existing = db.accountDao().all().map { it.code }.toSet()
        val missing = COA.filter { it.code !in existing }
        if (missing.isNotEmpty()) missing.forEach { db.accountDao().upsert(it) }
    }

    private fun now(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())

    private suspend fun nextVoucher(prefix: String): String {
        val n = db.journalDao().entryCount() + 1
        return String.format(Locale.US, "%s-%04d", prefix, n)
    }

    /**
     * Post a balanced double-entry transaction.
     * debitTo/creditTo: pairs of (accountId, amount).
     * Throws if the entry would not balance.
     */
    suspend fun post(
        date: String? = null,
        particulars: String,
        source: String,
        debits: List<Pair<String, Double>>,
        credits: List<Pair<String, Double>>,
        branchId: String? = null
    ): String {
        val d = debits.sumOf { it.second }
        val c = credits.sumOf { it.second }
        require(kotlin.math.abs(d - c) < 0.005) { "Entry does not balance: Dr $d vs Cr $c" }
        require(d > 0) { "Entry amount must be greater than zero" }

        val prefix = when (source) {
            "POS" -> "POS"
            "EXPENSE" -> "PV"
            "CAPITAL" -> "CAP"
            else -> "CB"
        }
        val entryId = UUID.randomUUID().toString()
        db.journalDao().insertEntry(JournalEntry(
            id = entryId,
            entryDate = date ?: now(),
            voucherNo = nextVoucher(prefix),
            particulars = particulars,
            source = source,
            branchId = branchId
        ))
        db.journalDao().insertLines(
            debits.map { JournalEntryLine(entryId, it.first, it.second, 0.0).toLine() } +
            credits.map { JournalEntryLine(entryId, it.first, 0.0, it.second).toLine() }
        )
        return entryId
    }

    // ------- Common one-tap postings -------

    /** Money received: Dr cash/bank/debtors, Cr the account credited. */
    suspend fun recordReceipt(cashAccount: String, fromAccount: String, amount: Double, particulars: String) {
        post(particulars = particulars, source = "MANUAL",
            debits = listOf(cashAccount to amount),
            credits = listOf(fromAccount to amount))
    }

    /** Money paid out: Cr cash, Dr the expense/asset account. */
    suspend fun recordPayment(cashAccount: String, toAccount: String, amount: Double, particulars: String) {
        post(particulars = particulars, source = "EXPENSE",
            debits = listOf(toAccount to amount),
            credits = listOf(cashAccount to amount))
    }

    /** Capital injection: Dr cash, Cr capital. */
    suspend fun recordCapital(amount: Double, cashAccount: String = CASH, particulars: String = "Owner capital in") {
        post(particulars = particulars, source = "CAPITAL",
            debits = listOf(cashAccount to amount), credits = listOf("acc-capital" to amount))
    }

    /** POS sale posting: Dr cash/bank/debtors, Cr sales. */
    suspend fun postSale(amount: Double, method: String, receiptNo: String,
                        itemCount: Int = 0, costTotal: Double = 0.0) {
        val debitAccount = when (method) {
            "MTN_MOMO", "AIRTEL_MONEY" -> BANK
            "CREDIT" -> DEBTORS
            else -> CASH
        }
        val detail = if (itemCount > 0) "$itemCount item${if (itemCount == 1) "" else "s"}" else "sale"
        if (amount > 0) {
            post(particulars = "Sales — $detail ($receiptNo)", source = "POS",
                debits = listOf(debitAccount to amount), credits = listOf(SALES to amount))
        }
        // Cost of sales: keeps the Stock account in step with physical stock
        if (costTotal > 0) {
            post(particulars = "Cost of sales — $detail ($receiptNo)", source = "POS",
                debits = listOf(COGS to costTotal), credits = listOf(STOCK to costTotal))
        }
    }

    /** Stock edit: +/- change in stock value at cost, so books match the Stock screen. */
    suspend fun postStockChange(deltaValue: Double, note: String) {
        if (deltaValue > 0)
            post(particulars = "Stock top-up ($note)", source = "STOCK",
                debits = listOf(STOCK to deltaValue), credits = listOf(CASH to deltaValue))
        else if (deltaValue < 0)
            post(particulars = "Stock reduction ($note)", source = "STOCK",
                debits = listOf(SUNDAY_RUN to -deltaValue), credits = listOf(STOCK to -deltaValue))
    }

    /**
     * Post the value change of an edited stock item so the Stock account always
     * equals the physical stock list (qty x cost), no matter what changed:
     *  - qty UP: the increase is a purchase paid in cash (Dr Stock, Cr Cash)
     *  - value UP with qty same/lower (cost price raised): revaluation gain
     *    (Dr Stock, Cr Stock Revaluation Gain)
     *  - value DOWN: write-off (Dr Sundry Expenses, Cr Stock)
     */
    suspend fun postStockEdit(oldQty: Double, oldCost: Double,
                              newQty: Double, newCost: Double, note: String) {
        val oldValue = oldQty * oldCost
        val newValue = newQty * newCost
        val deltaValue = newValue - oldValue
        val qtyDelta = newQty - oldQty
        when {
            deltaValue > 0 && qtyDelta > 0 ->
                post(particulars = "Stock top-up ($note)", source = "STOCK",
                    debits = listOf(STOCK to deltaValue), credits = listOf(CASH to deltaValue))
            deltaValue > 0 ->
                post(particulars = "Stock revaluation gain ($note)", source = "STOCK",
                    debits = listOf(STOCK to deltaValue), credits = listOf(REVAL to deltaValue))
            deltaValue < 0 ->
                post(particulars = "Stock reduction ($note)", source = "STOCK",
                    debits = listOf(SUNDAY_RUN to -deltaValue), credits = listOf(STOCK to -deltaValue))
        }
    }

    /** Stock removed from the business (delete/write-off): Cr Stock, Dr Sundry expense. */
    suspend fun postStockWriteOff(value: Double, note: String) {
        if (value > 0)
            post(particulars = "Stock write-off ($note)", source = "STOCK",
                debits = listOf(SUNDAY_RUN to value), credits = listOf(STOCK to value))
    }

    /**
     * Physical stock count variance — NEVER touches cash:
     *  - stock found (count up): Dr Stock, Cr Stock Revaluation Gain
     *  - stock missing (count down): Dr Sundry (shrinkage), Cr Stock
     * Finding stock in a count is not a purchase; no money left the business.
     */
    suspend fun postCountAdjustment(deltaQty: Double, cost: Double, note: String) {
        val deltaValue = deltaQty * cost
        when {
            deltaQty > 0 && deltaValue > 0 ->
                post(particulars = "Stock found in count ($note)", source = "STOCK",
                    debits = listOf(STOCK to deltaValue), credits = listOf(REVAL to deltaValue))
            deltaQty < 0 && deltaValue < 0 ->
                post(particulars = "Stock shrinkage in count ($note)", source = "STOCK",
                    debits = listOf(SUNDAY_RUN to -deltaValue), credits = listOf(STOCK to -deltaValue))
        }
    }

    /**
     * Stock purchase / opening stock: Dr Stock, Cr Cash (or Creditors if unpaid).
     * Keeps the Trial Balance complete as the owner adds stock.
     */
    /**
     * Customer returned goods — reverses the sale in the books:
     *   Dr Sales Returns, Cr Cash (refund) or Cr Debtors (reduce what they owe)
     *   Dr Stock, Cr Cost of Sales (goods back on the shelf at cost)
     */
    suspend fun postSaleReturn(refundAmount: Double, costValue: Double,
                               refundMethod: String, note: String) {
        val refundAccount = if (refundMethod == "CREDIT") DEBTORS else CASH
        post(particulars = "Sales return ($note)", source = "RETURN",
            debits = listOf(RETURNS to refundAmount),
            credits = listOf(refundAccount to refundAmount))
        if (costValue > 0) {
            post(particulars = "Stock returned ($note)", source = "RETURN",
                debits = listOf(STOCK to costValue), credits = listOf(COGS to costValue))
        }
    }

    suspend fun postPurchase(amount: Double, paidHow: String, ref: String) {
        if (amount <= 0) return
        val creditAccount = if (paidHow == "CREDIT") CREDITORS else CASH
        post(particulars = "Stock purchase $ref", source = "STOCK",
            debits = listOf(STOCK to amount), credits = listOf(creditAccount to amount))
    }

    // ------- Reports -------

    /**
     * Goods returned to supplier: stock leaves the books (Cr Stock) and we
     * either get money back (Dr Cash) or owe less (Dr Creditors).
     */
    suspend fun postPurchaseReturn(refundMethod: String, costValue: Double, note: String) {
        if (costValue <= 0.0) return
        val dr = if (refundMethod == "SUPPLIER_CREDIT") CREDITORS else CASH
        post(particulars = "Return to supplier ($note)", source = "STOCK",
            debits = listOf(dr to costValue),
            credits = listOf(STOCK to costValue))
    }

    /** Monthly straight-line depreciation: Dr Depreciation, Cr Accumulated Depreciation. */
    suspend fun postDepreciation(total: Double, note: String) {
        if (total <= 0.0) return
        post(particulars = "Depreciation — $note", source = "EXPENSE",
            debits = listOf("acc-depexp" to total),
            credits = listOf("acc-accdepr" to total))
    }

    /** Payroll: Dr Salaries (gross), Cr Cash (net), Cr Creditors (deductions owed). */
    suspend fun postPayroll(gross: Double, net: Double, deductions: Double, month: String) {
        if (gross <= 0.0) return
        val debits = listOf("acc-salaries" to gross)
        val credits = buildList {
            add(CASH to net)
            if (deductions > 0.0) add(CREDITORS to deductions)
        }
        post(particulars = "Payroll — $month (net paid ${"%,.0f".format(net)})", source = "EXPENSE",
            debits = debits, credits = credits)
    }

    /** VAT remitted to URA: Dr VAT Payable, Cr Cash. */
    suspend fun postVatRemittance(amount: Double, period: String) {
        if (amount <= 0.0) return
        post(particulars = "VAT remittance — $period", source = "EXPENSE",
            debits = listOf("acc-vatout" to amount),
            credits = listOf(CASH to amount))
    }

    suspend fun trialBalance(from: String, to: String) = db.journalDao().trialBalance(from, to)

    /**
     * Income statement, in the format bookkeepers expect:
     *   Net Sales = Sales − Sales Returns
     *   Gross Profit = Net Sales − Cost of Sales
     *   Net Profit = Gross Profit + Other Income − Operating Expenses
     * Cost of Sales is kept OUT of operating expenses; revaluation gains are
     * unrealized, so they appear after gross profit as Other Income.
     */
    data class IncomeStatement(
        val revenue: List<Pair<String, Double>>,      // sales + returns (returns negative)
        val cogs: Double,
        val otherIncome: List<Pair<String, Double>>,  // unrealized gains (revaluation)
        val operatingExpenses: List<Pair<String, Double>>
    ) {
        val netSales: Double get() = revenue.sumOf { it.second }
        val grossProfit: Double get() = netSales - cogs
        val otherIncomeTotal: Double get() = otherIncome.sumOf { it.second }
        val totalOperatingExpenses: Double get() = operatingExpenses.sumOf { it.second }
        val netProfit: Double get() = grossProfit + otherIncomeTotal - totalOperatingExpenses
    }

    suspend fun incomeStatement(from: String, to: String): IncomeStatement {
        val tb = trialBalance(from, to)
        // income accounts have credit balances (netBalance negative) → flip sign
        val revenue = tb.filter {
            (it.accountId == SALES || it.accountId == RETURNS) && it.netBalance != 0.0
        }.map { it.name to -it.netBalance }
        val cogs = tb.filter { it.accountId == COGS }.sumOf { it.netBalance }
        val otherIncome = tb.filter {
            it.type == "INCOME" && it.accountId != SALES && it.accountId != RETURNS && it.netBalance != 0.0
        }.map { it.name to -it.netBalance }
        val operatingExpenses = tb.filter {
            it.type == "EXPENSE" && it.accountId != COGS && it.netBalance != 0.0
        }.map { it.name to it.netBalance }
        return IncomeStatement(revenue, cogs, otherIncome, operatingExpenses)
    }

    /**
     * Self-check: verifies the three invariants that guarantee the books are
     * correct. Returns null when everything balances, or a human-readable
     * description of what is off (for diagnostics/logging).
     */
    data class SelfCheckResult(
        val debitsEqCredits: Boolean,
        val stockMatchesLedger: Boolean,
        val debtorsMatchCustomers: Boolean,
        val totalDebits: Double, val totalCredits: Double,
        val ledgerStockValue: Double, val physicalStockValue: Double,
        val ledgerDebtors: Double, val customerBalances: Double
    ) {
        val ok: Boolean get() = debitsEqCredits && stockMatchesLedger && debtorsMatchCustomers
        fun describe(): String = buildString {
            if (!debitsEqCredits) appendLine("Ledger out of balance: Dr $totalDebits vs Cr $totalCredits")
            if (!stockMatchesLedger) appendLine("Stock account UGX ${"%,.0f".format(ledgerStockValue)} != stock list UGX ${"%,.0f".format(physicalStockValue)}")
            if (!debtorsMatchCustomers) appendLine("Debtors account UGX ${"%,.0f".format(ledgerDebtors)} != customer balances UGX ${"%,.0f".format(customerBalances)}")
            if (ok) appendLine("All book checks passed")
        }
    }

    suspend fun selfCheck(): SelfCheckResult {
        val totalDr = db.journalDao().totalDebits()
        val totalCr = db.journalDao().totalCredits()
        val ledgerStock = cashBalanceLike(STOCK)
        val physicalStock = try {
            db.productDao().observeBranchProducts("").first().sumOf { it.stockQty * it.costPrice }
        } catch (_: Exception) { 0.0 }
        // all products regardless of branch
        val physicalStockAll = try {
            db.productDao().allProductsOnce().sumOf { it.stockQty * it.costPrice }
        } catch (_: Exception) { physicalStock }
        val ledgerDebtors = cashBalanceLike(DEBTORS)
        val custBalances = try {
            db.customerDao().allOnce().sumOf { it.balance }
        } catch (_: Exception) { 0.0 }
        return SelfCheckResult(
            debitsEqCredits = kotlin.math.abs(totalDr - totalCr) < 0.01,
            stockMatchesLedger = kotlin.math.abs(ledgerStock - physicalStockAll) < 0.01,
            debtorsMatchCustomers = kotlin.math.abs(ledgerDebtors - custBalances) < 0.01,
            totalDebits = totalDr, totalCredits = totalCr,
            ledgerStockValue = ledgerStock, physicalStockValue = physicalStockAll,
            ledgerDebtors = ledgerDebtors, customerBalances = custBalances
        )
    }

    /** Net (Dr-Cr) balance of any account over all time. */
    private suspend fun cashBalanceLike(accountId: String): Double {
        val tb = trialBalance("1970-01-01T00:00:00.000Z", "2999-12-31T23:59:59.999Z")
        return tb.firstOrNull { it.accountId == accountId }?.netBalance ?: 0.0
    }

    suspend fun cashBalance(accountId: String = CASH): Double {
        // debit - credit for a cash account is its balance
        val tb = trialBalance("1970-01-01T00:00:00Z", "2999-12-31T23:59:59Z")
        return tb.firstOrNull { it.accountId == accountId }?.netBalance ?: 0.0
    }
}

/** Small helper for building journal lines. */
private data class JournalEntryLine(
    val entryId: String, val accountId: String, val debit: Double, val credit: Double
) {
    fun toLine() = JournalLine(
        id = UUID.randomUUID().toString(),
        entryId = entryId, accountId = accountId, debit = debit, credit = credit
    )
}
