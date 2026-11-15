package com.derycode.deryaccount.desktop.data

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max

/** Every read-only query the screens need: KPIs, ledger statements and analytics. */
class Reports(private val repo: Repo) {
    private val db = repo.db

    // ── Period helpers ───────────────────────────────────────────────────────
    fun todayRange(): Pair<String, String> = "${Clock.today()}T00:00:00" to "${Clock.today()}T23:59:59"
    fun monthRange(ym: String = Clock.thisMonth()): Pair<String, String> =
        "$ym-01T00:00:00" to LocalDate.parse("$ym-01").plusMonths(1).minusDays(1).toString() + "T23:59:59"

    // ── Dashboard ────────────────────────────────────────────────────────────
    data class Kpis(
        val todaySales: Double, val todayProfit: Double, val todayCount: Int,
        val weekSales: Double, val monthSales: Double, val monthProfit: Double,
        val monthExpenses: Double, val receivables: Double, val payables: Double,
        val stockValue: Double, val stockRetail: Double, val cash: Double, val bank: Double,
        val lowStock: Int, val outOfStock: Int, val products: Int, val customers: Int
    )

    fun kpis(): Kpis {
        val (t0, t1) = todayRange()
        val monthStart = Clock.monthStart() + "T00:00:00"
        val weekStart = LocalDate.now().minusDays(6).toString() + "T00:00:00"

        val todaySales = db.scalar("SELECT COALESCE(SUM(total),0) FROM sales WHERE sold_at BETWEEN ? AND ?", t0, t1)
        val todayCount = db.count("sales", "sold_at BETWEEN ? AND ?", t0, t1).toInt()
        val todayCogs = db.scalar(
            """SELECT COALESCE(SUM(i.qty * i.cost_price),0) FROM sale_items i
               JOIN sales s ON s.id = i.sale_id WHERE s.sold_at BETWEEN ? AND ?""", t0, t1
        )
        val weekSales = db.scalar("SELECT COALESCE(SUM(total),0) FROM sales WHERE sold_at >= ?", weekStart)
        val monthSales = db.scalar("SELECT COALESCE(SUM(total),0) FROM sales WHERE sold_at >= ?", monthStart)
        val monthCogs = db.scalar(
            """SELECT COALESCE(SUM(i.qty * i.cost_price),0) FROM sale_items i
               JOIN sales s ON s.id = i.sale_id WHERE s.sold_at >= ?""", monthStart
        )
        val monthExpenses = db.scalar("SELECT COALESCE(SUM(amount),0) FROM expenses WHERE spent_at >= ?", monthStart)
        val receivables = db.scalar("SELECT COALESCE(SUM(balance),0) FROM customers")
        val payables = db.scalar("SELECT COALESCE(SUM(balance),0) FROM suppliers")
        val stockValue = db.scalar("SELECT COALESCE(SUM(stock_qty * cost_price),0) FROM products")
        val stockRetail = db.scalar("SELECT COALESCE(SUM(stock_qty * retail_price),0) FROM products")
        val cash = repo.accounting.balanceOf(Accounts.CASH)
        val bank = repo.accounting.balanceOf(Accounts.BANK)
        val lowStock = db.q("SELECT id FROM products WHERE stock_qty > 0 AND stock_qty <= low_stock_alert").size
        val outOfStock = db.q("SELECT id FROM products WHERE stock_qty <= 0").size
        val products = db.count("products")
        val customers = db.count("customers")
        return Kpis(
            Fmt.r2(todaySales), Fmt.r2(todaySales - todayCogs), todayCount,
            Fmt.r2(weekSales), Fmt.r2(monthSales), Fmt.r2(monthSales - monthCogs - monthExpenses),
            Fmt.r2(monthExpenses), Fmt.r2(receivables), Fmt.r2(payables),
            Fmt.r2(stockValue), Fmt.r2(stockRetail), Fmt.r2(cash), Fmt.r2(bank),
            lowStock, outOfStock, products, customers
        )
    }

    fun salesByDay(days: Int = 14): List<Pair<String, Double>> {
        val out = mutableListOf<Pair<String, Double>>()
        for (i in days - 1 downTo 0) {
            val d = LocalDate.now().minusDays(i.toLong()).toString()
            val v = db.scalar("SELECT COALESCE(SUM(total),0) FROM sales WHERE sold_at BETWEEN ? AND ?",
                "${d}T00:00:00", "${d}T23:59:59")
            out.add(d to v)
        }
        return out
    }

    fun salesByMonth(months: Int = 12): List<Pair<String, Double>> {
        val out = mutableListOf<Pair<String, Double>>()
        for (i in months - 1 downTo 0) {
            val ym = Clock.shiftMonth(Clock.thisMonth(), -i.toLong())
            val v = db.scalar("SELECT COALESCE(SUM(total),0) FROM sales WHERE sold_at LIKE ?", "$ym%")
            out.add(ym to v)
        }
        return out
    }

    fun recentSales(limit: Int = 8): List<Row> = repo.sales(limit)

    fun lowStockList(limit: Int = 10): List<Row> = db.q(
        "SELECT * FROM products WHERE stock_qty <= low_stock_alert ORDER BY stock_qty ASC LIMIT ?", limit
    )

    // ── Ledger statements ────────────────────────────────────────────────────
    data class TbRow(val code: String, val name: String, val type: String, val debit: Double, val credit: Double) {
        val balance: Double get() = debit - credit
    }

    fun trialBalance(from: String, to: String): List<TbRow> = db.q(
        """SELECT a.code, a.name, a.type,
                  COALESCE(SUM(l.debit),0) AS d, COALESCE(SUM(l.credit),0) AS c
           FROM accounts a
           LEFT JOIN journal_lines l ON l.account_id = a.id
           LEFT JOIN journal_entries e ON e.id = l.entry_id AND e.entry_date >= ? AND e.entry_date <= ?
           GROUP BY a.id ORDER BY a.sort_order, a.code""", from, to
    ).map { TbRow(it.str("code"), it.str("name"), it.str("type"), Fmt.r2(it.dbl("d")), Fmt.r2(it.dbl("c"))) }
        .filter { it.debit != 0.0 || it.credit != 0.0 }

    data class IncomeStatement(
        val sales: Double, val returns: Double, val discounts: Double, val netSales: Double,
        val purchases: Double, val cogs: Double, val grossProfit: Double,
        val expenses: List<Pair<String, Double>>, val otherIncome: Double, val netProfit: Double
    )

    fun incomeStatement(from: String, to: String): IncomeStatement {
        fun sum(acc: String, side: String): Double = db.scalar(
            """SELECT COALESCE(SUM(l.$side),0) FROM journal_lines l
               JOIN journal_entries e ON e.id=l.entry_id
               WHERE l.account_id=? AND e.entry_date >= ? AND e.entry_date <= ?""", acc, from, to
        )
        val sales = sum(Accounts.SALES, "credit")
        val returns = sum(Accounts.SALES_RETURNS, "debit")
        val discounts = sum(Accounts.SALES_DISCOUNT, "debit")
        val netSales = Fmt.r2(sales - returns - discounts)
        val cogs = Fmt.r2(sum(Accounts.COGS, "debit") - sum(Accounts.COGS, "credit"))
        val purchases = Fmt.r2(
            db.scalar(
                """SELECT COALESCE(SUM(m.qty * p.cost_price),0) FROM stock_movements m
                   JOIN products p ON p.id=m.product_id
                   WHERE m.type='PURCHASE' AND m.moved_at >= ? AND m.moved_at <= ?""", from, to
            )
        )
        val otherIncome = Fmt.r2(sum(Accounts.REVALUATION, "credit") - sum(Accounts.REVALUATION, "debit"))
        val expenseAccounts = db.q("SELECT id, name FROM accounts WHERE type='EXPENSE' AND id <> ? ORDER BY sort_order", Accounts.COGS)
        val expenses = expenseAccounts.mapNotNull { a ->
            val v = Fmt.r2(sum(a.str("id"), "debit") - sum(a.str("id"), "credit"))
            if (abs(v) < 0.005) null else a.str("name") to v
        }
        val totalExpenses = expenses.sumOf { it.second }
        return IncomeStatement(
            Fmt.r2(sales), Fmt.r2(returns), Fmt.r2(discounts), netSales,
            purchases, cogs, Fmt.r2(netSales - cogs),
            expenses, otherIncome, Fmt.r2(netSales - cogs + otherIncome - totalExpenses)
        )
    }

    data class BsRow(val label: String, val amount: Double, val bold: Boolean = false)

    fun balanceSheet(asOf: String = Clock.nowIso()): Triple<List<BsRow>, List<BsRow>, List<BsRow>> {
        fun bal(acc: String) = repo.accounting.balanceOf(acc, "0000", asOf)
        val assets = mutableListOf<BsRow>()
        val liabilities = mutableListOf<BsRow>()
        val equity = mutableListOf<BsRow>()

        val cash = bal(Accounts.CASH); if (cash != 0.0) assets.add(BsRow("Cash on Hand", cash))
        val petty = bal(Accounts.PETTY); if (petty != 0.0) assets.add(BsRow("Petty Cash", petty))
        val bank = bal(Accounts.BANK); if (bank != 0.0) assets.add(BsRow("Bank / Mobile Money", bank))
        val debtors = bal(Accounts.DEBTORS); if (debtors != 0.0) assets.add(BsRow("Debtors (Receivables)", debtors))
        val stock = bal(Accounts.STOCK); if (stock != 0.0) assets.add(BsRow("Stock on Hand", stock))
        val fa = bal(Accounts.FIXED_ASSETS); if (fa != 0.0) assets.add(BsRow("Fixed Assets at cost", fa))
        val dep = bal(Accounts.ACC_DEPR); if (dep != 0.0) assets.add(BsRow("Less: Accumulated Depreciation", dep))

        val creditors = bal(Accounts.CREDITORS); if (creditors != 0.0) liabilities.add(BsRow("Creditors (Payables)", -creditors))
        val vat = bal(Accounts.VAT_OUT); if (vat != 0.0) liabilities.add(BsRow("VAT Payable", -vat))
        // Hotel deposits: money held for guests, refundable until they check out.
        val deposits = bal(Accounts.GUEST_DEPOSITS)
        if (deposits != 0.0) liabilities.add(BsRow("Guest Deposits Held", -deposits))

        val capital = bal(Accounts.CAPITAL); if (capital != 0.0) equity.add(BsRow("Capital", -capital))
        val drawings = bal("acc-drawings"); if (drawings != 0.0) equity.add(BsRow("Drawings", -drawings))

        // Retained earnings: every income less every expense since the books opened.
        val income = db.scalar(
            """SELECT COALESCE(SUM(l.credit - l.debit),0) FROM journal_lines l
               JOIN accounts a ON a.id=l.account_id JOIN journal_entries e ON e.id=l.entry_id
               WHERE a.type='INCOME' AND e.entry_date <= ?""", asOf
        )
        val expense = db.scalar(
            """SELECT COALESCE(SUM(l.debit - l.credit),0) FROM journal_lines l
               JOIN accounts a ON a.id=l.account_id JOIN journal_entries e ON e.id=l.entry_id
               WHERE a.type='EXPENSE' AND e.entry_date <= ?""", asOf
        )
        equity.add(BsRow("Retained Earnings", Fmt.r2(income - expense), bold = true))

        assets.add(BsRow("TOTAL ASSETS", Fmt.r2(assets.sumOf { it.amount }), bold = true))
        liabilities.add(BsRow("TOTAL LIABILITIES", Fmt.r2(liabilities.sumOf { it.amount }), bold = true))
        equity.add(BsRow("TOTAL EQUITY", Fmt.r2(equity.sumOf { it.amount }), bold = true))
        return Triple(assets, liabilities, equity)
    }

    /** VAT position for a month: output VAT charged, less remitted, less what is still owed. */
    fun vatReport(ym: String): Triple<Double, Double, Double> {
        val (from, to) = monthRange(ym)
        val output = db.scalar(
            """SELECT COALESCE(SUM(i.line_total * i.tax_rate / (100.0 + i.tax_rate)),0)
               FROM sale_items i JOIN sales s ON s.id=i.sale_id
               WHERE i.tax_rate > 0 AND s.sold_at BETWEEN ? AND ?""", from, to
        )
        val remitted = db.scalar(
            """SELECT COALESCE(SUM(l.debit),0) FROM journal_lines l
               JOIN journal_entries e ON e.id=l.entry_id
               WHERE l.account_id=? AND e.entry_date >= ? AND e.entry_date <= ?""",
            Accounts.VAT_OUT, from, to
        )
        val payable = -repo.accounting.balanceOf(Accounts.VAT_OUT)
        return Triple(Fmt.r2(output), Fmt.r2(remitted), Fmt.r2(payable))
    }

    // ── Analytics ────────────────────────────────────────────────────────────
    data class ProductStat(val id: String, val name: String, val qty: Double, val revenue: Double, val profit: Double)

    fun bestSellers(from: String, to: String, limit: Int = 25): List<ProductStat> = db.q(
        """SELECT i.product_id AS id, i.name AS name, SUM(i.qty) AS qty,
                  SUM(i.line_total) AS revenue, SUM(i.line_total - i.qty*i.cost_price) AS profit
           FROM sale_items i JOIN sales s ON s.id=i.sale_id
           WHERE s.sold_at BETWEEN ? AND ?
           GROUP BY i.product_id ORDER BY revenue DESC LIMIT ?""", from, to, limit
    ).map { ProductStat(it.str("id"), it.str("name"), it.dbl("qty"), Fmt.r2(it.dbl("revenue")), Fmt.r2(it.dbl("profit"))) }

    fun profitByCategory(from: String, to: String): List<ProductStat> = db.q(
        """SELECT COALESCE(p.category,'Uncategorised') AS id, COALESCE(p.category,'Uncategorised') AS name,
                  SUM(i.qty) AS qty, SUM(i.line_total) AS revenue,
                  SUM(i.line_total - i.qty*i.cost_price) AS profit
           FROM sale_items i JOIN sales s ON s.id=i.sale_id LEFT JOIN products p ON p.id=i.product_id
           WHERE s.sold_at BETWEEN ? AND ? GROUP BY p.category ORDER BY profit DESC""", from, to
    ).map { ProductStat(it.str("id"), it.str("name"), it.dbl("qty"), Fmt.r2(it.dbl("revenue")), Fmt.r2(it.dbl("profit"))) }

    fun deadStock(days: Int = 60, limit: Int = 60): List<Row> = db.q(
        """SELECT p.*, COALESCE((SELECT MAX(s.sold_at) FROM sale_items i
                  JOIN sales s ON s.id=i.sale_id WHERE i.product_id=p.id), '') AS last_sold
           FROM products p WHERE p.stock_qty > 0
             AND COALESCE((SELECT MAX(s.sold_at) FROM sale_items i
                  JOIN sales s ON s.id=i.sale_id WHERE i.product_id=p.id), '') < ?
           ORDER BY p.stock_qty * p.cost_price DESC LIMIT ?""",
        LocalDate.now().minusDays(days.toLong()).toString(), limit
    )

    data class Valuation(val costValue: Double, val retailValue: Double, val potential: Double, val units: Double)

    fun stockValuation(): Valuation {
        val r = db.one(
            """SELECT COALESCE(SUM(stock_qty*cost_price),0) AS c, COALESCE(SUM(stock_qty*retail_price),0) AS r,
                      COALESCE(SUM(stock_qty),0) AS u FROM products"""
        )
        val c = r?.dbl("c") ?: 0.0
        val rv = r?.dbl("r") ?: 0.0
        return Valuation(Fmt.r2(c), Fmt.r2(rv), Fmt.r2(rv - c), r?.dbl("u") ?: 0.0)
    }

    fun branchComparison(): List<Row> = db.q(
        """SELECT b.name AS branch, COUNT(s.id) AS sales, COALESCE(SUM(s.total),0) AS revenue,
                  COALESCE(SUM((SELECT SUM(i.qty*i.cost_price) FROM sale_items i WHERE i.sale_id=s.id)),0) AS cogs
           FROM branches b LEFT JOIN sales s ON s.branch_id=b.id
           GROUP BY b.id ORDER BY revenue DESC"""
    )

    data class AgingBucket(val label: String, val amount: Double)

    /** Customer balances aged by how long each credit sale has been outstanding. */
    fun customerAging(): List<Row> = db.q(
        """SELECT c.id, c.name, c.balance,
                  COALESCE(SUM(CASE WHEN julianday('now') - julianday(s.sold_at) <= 30 THEN s.total - s.amount_paid ELSE 0 END),0) AS d30,
                  COALESCE(SUM(CASE WHEN julianday('now') - julianday(s.sold_at) BETWEEN 31 AND 60 THEN s.total - s.amount_paid ELSE 0 END),0) AS d60,
                  COALESCE(SUM(CASE WHEN julianday('now') - julianday(s.sold_at) > 60 THEN s.total - s.amount_paid ELSE 0 END),0) AS d90
           FROM customers c LEFT JOIN sales s ON s.customer_id = c.id AND s.payment_method='CREDIT'
           GROUP BY c.id HAVING c.balance > 0 ORDER BY c.balance DESC"""
    )

    fun supplierAging(): List<Row> = db.q(
        """SELECT s.id, s.name, s.balance,
                  COALESCE(SUM(CASE WHEN julianday('now') - julianday(p.received_at) <= 30 THEN p.total - p.paid_amount ELSE 0 END),0) AS d30,
                  COALESCE(SUM(CASE WHEN julianday('now') - julianday(p.received_at) BETWEEN 31 AND 60 THEN p.total - p.paid_amount ELSE 0 END),0) AS d60,
                  COALESCE(SUM(CASE WHEN julianday('now') - julianday(p.received_at) > 60 THEN p.total - p.paid_amount ELSE 0 END),0) AS d90
           FROM suppliers s LEFT JOIN purchases p ON p.supplier_id = s.id
           GROUP BY s.id HAVING s.balance > 0 ORDER BY s.balance DESC"""
    )

    /** Budget vs actual for a month. */
    fun budgetVsActual(ym: String): List<Triple<Row, Double, Double>> {
        val (from, to) = monthRange(ym)
        val budgets = db.q("SELECT * FROM budgets WHERE month=? ORDER BY kind, category", ym)
        return budgets.map { b ->
            val actual = if (b.str("kind") == "REVENUE") {
                db.scalar(
                    """SELECT COALESCE(SUM(l.credit - l.debit),0) FROM journal_lines l
                       JOIN journal_entries e ON e.id=l.entry_id WHERE l.account_id=? AND e.entry_date BETWEEN ? AND ?""",
                    Accounts.SALES, from, to
                )
            } else {
                db.scalar(
                    """SELECT COALESCE(SUM(l.debit - l.credit),0) FROM journal_lines l
                       JOIN journal_entries e ON e.id=l.entry_id WHERE l.account_id=? AND e.entry_date BETWEEN ? AND ?""",
                    Accounts.accountForExpense(b.str("category")), from, to
                )
            }
            Triple(b, Fmt.r2(actual), max(0.0, b.dbl("amount") - actual))
        }
    }

    /** All-time profit for the balance-sheet check (assets == liabilities + equity). */
    fun checkBalance(): Double {
        val (a, l, e) = balanceSheet()
        return Fmt.r2(a.last().amount - l.last().amount - e.last().amount)
    }
}
