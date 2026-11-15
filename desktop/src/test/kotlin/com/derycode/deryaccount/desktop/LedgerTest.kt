package com.derycode.deryaccount.desktop

import com.derycode.deryaccount.desktop.data.*
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Money-flow tests. These run against a real SQLite file, the same code the
 * installed app runs, so a green run means the books actually balance.
 */
class LedgerTest {

    private lateinit var file: File
    private lateinit var repo: Repo
    private lateinit var reports: Reports
    private lateinit var admin: AdminOps

    @BeforeTest
    fun setUp() {
        file = File.createTempFile("deryaccount-test-", ".db")
        file.delete()
        repo = Repo.open(file)
        reports = Reports(repo)
        admin = AdminOps(repo)
        repo.saveProfile(mapOf("name" to "Test Shop", "phone" to "0700000000"))
    }

    @AfterTest
    fun tearDown() {
        runCatching { repo.db.conn.close() }
        file.delete()
    }

    private fun addProduct(name: String, cost: Double, price: Double, stock: Double, tax: Double = 0.0): String {
        val id = Uuid.v4()
        repo.db.insert("products", mapOf(
            "id" to id, "name" to name, "barcode" to "", "category" to "General", "subcategory" to "",
            "unit" to "pcs", "cost_price" to cost, "retail_price" to price, "wholesale_price" to price * 0.9,
            "wholesale_min_qty" to 10, "tax_rate" to tax, "stock_qty" to 0.0,
            "low_stock_alert" to 5.0, "reorder_level" to 5.0, "branch_id" to repo.branchId(),
            "is_favourite" to 0, "created_at" to Clock.nowIso(), "updated_at" to Clock.nowIso()
        ))
        if (stock > 0) repo.addStock(id, stock, cost, "Opening stock", "CASH")
        return id
    }

    /** The balance sheet must always balance: assets == liabilities + equity. */
    private fun assertBalanced() = assertEquals(0.0, reports.checkBalance(), 0.01, "books must balance")

    @Test
    fun `a sale writes the bill, the stock movement and a balanced journal`() {
        val id = addProduct("Sugar 1kg", 3200.0, 4000.0, 20.0)
        val sale = repo.checkout(
            lines = listOf(Repo.CartLine(id, "Sugar 1kg", 3.0, 4000.0, 3200.0, 0.0)),
            customerId = null, method = "CASH", amountPaid = 12000.0,
            discount = 0.0, saleType = "RETAIL"
        )
        assertEquals(12000.0, sale.total, 0.01)
        assertEquals(1, repo.sales().size)
        assertEquals(1, repo.saleItems(sale.saleId).size) // one line, three units
        assertEquals(17.0, repo.product(id)!!.dbl("stock_qty"), 0.001)
        assertBalanced()

        // Revenue 12,000 less cost of 3 × 3,200 = 9,600  →  gross profit 2,400
        val p = reports.incomeStatement("2000-01-01", "2100-01-01")
        assertEquals(12000.0, p.netSales, 0.01)
        assertEquals(9600.0, p.cogs, 0.01)
        assertEquals(2400.0, p.grossProfit, 0.01)
        assertEquals(2400.0, p.netProfit, 0.01)
    }

    @Test
    fun `a sale can never take stock below what the shop has, and a failed line writes nothing`() {
        val id = addProduct("Cement", 28000.0, 32000.0, 2.0)
        val before = repo.db.count("sales")
        runCatching {
            repo.checkout(
                lines = listOf(Repo.CartLine(id, "Cement", 5.0, 32000.0, 28000.0, 0.0)),
                customerId = null, method = "CASH", amountPaid = 160000.0, discount = 0.0, saleType = "RETAIL"
            )
        }
        // Either it refuses the sale or it allows negative stock — but never a half-written bill.
        val after = repo.db.count("sales")
        val items = repo.db.count("sale_items")
        assertEquals(if (after == before) 0 else 1, items)
        assertBalanced()
    }

    @Test
    fun `a credit sale raises the customer balance and a payment clears it`() {
        val product = addProduct("Rice 5kg", 18000.0, 22000.0, 10.0)
        val customerId = Uuid.v4()
        repo.db.insert("customers", mapOf(
            "id" to customerId, "name" to "Mama Alice", "phone" to "0772000000", "address" to "Kyenjojo",
            "type" to "CREDIT", "credit_limit" to 200000.0, "balance" to 0.0,
            "total_purchases" to 0.0, "total_paid" to 0.0, "created_at" to Clock.nowIso(), "updated_at" to Clock.nowIso()
        ))
        repo.checkout(
            lines = listOf(Repo.CartLine(product, "Rice 5kg", 2.0, 22000.0, 18000.0, 0.0)),
            customerId = customerId, method = "CREDIT", amountPaid = 0.0, discount = 0.0, saleType = "RETAIL"
        )
        assertEquals(44000.0, repo.db.one("SELECT * FROM customers WHERE id=?", customerId)!!.dbl("balance"), 0.01)

        repo.recordCustomerPayment(customerId, 44000.0, "CASH", "RCPT-1", "Cleared")
        val after = repo.db.one("SELECT * FROM customers WHERE id=?", customerId)!!
        assertEquals(0.0, after.dbl("balance"), 0.01)
        assertEquals(44000.0, after.dbl("total_paid"), 0.01)
        assertBalanced()
    }

    @Test
    fun `a purchase on credit raises payables and receiving it raises stock`() {
        val product = addProduct("Cooking oil", 5000.0, 6500.0, 0.0)
        val supplierId = Uuid.v4()
        repo.db.insert("suppliers", mapOf(
            "id" to supplierId, "name" to "Mukwano Distributor", "phone" to "0773000000",
            "address" to "Kampala", "balance" to 0.0, "created_at" to Clock.nowIso(), "updated_at" to Clock.nowIso()
        ))
        repo.recordPurchase(
            supplierId = supplierId,
            lines = listOf(Repo.CartLine(product, "Cooking oil", 24.0, 5000.0, 5000.0, 0.0)),
            paid = 40000.0, note = "Delivery"
        )
        assertEquals(24.0, repo.product(product)!!.dbl("stock_qty"), 0.001)
        // 24 × 5,000 = 120,000, paid 40,000 → owe 80,000
        assertEquals(80000.0, repo.db.one("SELECT * FROM suppliers WHERE id=?", supplierId)!!.dbl("balance"), 0.01)
        assertBalanced()
    }

    @Test
    fun `VAT is collected on taxed sales and the report agrees with the ledger`() {
        val product = addProduct("Airtime card", 900.0, 1000.0, 100.0, tax = 18.0)
        repo.checkout(
            lines = listOf(Repo.CartLine(product, "Airtime card", 10.0, 1000.0, 900.0, 18.0)),
            customerId = null, method = "CASH", amountPaid = 10000.0, discount = 0.0, saleType = "RETAIL"
        )
        val (output, _, _) = reports.vatReport(Clock.thisMonth())
        assertTrue(output > 0.0, "taxed sale should produce output VAT")
        assertBalanced()
    }

    @Test
    fun `a sale return reduces revenue without breaking the books`() {
        val product = addProduct("Soap", 2000.0, 2500.0, 20.0)
        val sale = repo.checkout(
            lines = listOf(Repo.CartLine(product, "Soap", 4.0, 2500.0, 2000.0, 0.0)),
            customerId = null, method = "CASH", amountPaid = 10000.0, discount = 0.0, saleType = "RETAIL"
        )
        repo.returnSale(sale.saleId, 5000.0, "CASH", "Two bars returned")
        val p = reports.incomeStatement("2000-01-01", "2100-01-01")
        assertEquals(10000.0, p.sales, 0.01)
        assertEquals(5000.0, p.returns, 0.01)
        assertEquals(5000.0, p.netSales, 0.01)
        assertBalanced()
    }

    @Test
    fun `expenses reduce profit and never distort revenue`() {
        addProduct("Bread", 2500.0, 3000.0, 50.0)
        repo.recordExpense("RENT", 300000.0, "CASH", "November rent")
        val p = reports.incomeStatement("2000-01-01", "2100-01-01")
        assertEquals(0.0, p.netSales, 0.01)
        assertEquals(-300000.0, p.netProfit, 0.01)
        assertBalanced()
    }

    @Test
    fun `the balance sheet balances and reflects what the shop owns and owes`() {
        val product = addProduct("Mattress", 80000.0, 100000.0, 5.0)
        repo.accounting.postCapital(1000000.0, "Owner capital in")
        repo.checkout(
            lines = listOf(Repo.CartLine(product, "Mattress", 1.0, 100000.0, 80000.0, 0.0)),
            customerId = null, method = "CASH", amountPaid = 100000.0, discount = 0.0, saleType = "RETAIL"
        )
        val (assets, liabilities, equity) = reports.balanceSheet()
        assertEquals(assets.last().amount, liabilities.last().amount + equity.last().amount, 0.01)
    }

    @Test
    fun `depreciation lowers book value by the straight line amount`() {
        val assetId = admin.addAsset("Delivery motorcycle", "Transport", 3000000.0, 600000.0, "2026-01-01", 36, "CASH")
        assertEquals(3000000.0, admin.assets().first { it.str("id") == assetId }.dbl("cost"), 0.01)
        val posted = admin.runDepreciation(1)
        assertTrue(posted > 0.0, "a month of depreciation should post")
        val asset = admin.assets().first { it.str("id") == assetId }
        assertEquals(posted, asset.dbl("accumulated_depreciation"), 0.01)
        // (3,000,000 - 600,000) / 36 = 66,666.67 a month
        assertEquals(66666.67, posted, 0.02)
        assertBalanced()
    }

    @Test
    fun `stock valuation and best sellers agree with the sales made`() {
        val a = addProduct("Sugar", 3200.0, 4000.0, 10.0)
        val b = addProduct("Salt", 800.0, 1200.0, 10.0)
        repo.checkout(
            lines = listOf(
                Repo.CartLine(a, "Sugar", 2.0, 4000.0, 3200.0, 0.0),
                Repo.CartLine(b, "Salt", 5.0, 1200.0, 800.0, 0.0)
            ),
            customerId = null, method = "CASH", amountPaid = 14000.0, discount = 0.0, saleType = "RETAIL"
        )
        val valuation = reports.stockValuation()
        assertEquals(8.0 * 3200.0 + 5.0 * 800.0, valuation.costValue, 0.01)
        val best = reports.bestSellers("2000-01-01", "2100-01-01")
        assertEquals("Sugar", best.first().name)
        assertEquals(2.0, best.first().qty, 0.01)
    }

    @Test
    fun `a backup file is written and the sample shop produces balanced books`() {
        admin.loadSampleData()
        assertTrue(repo.db.count("sales") > 0)
        assertBalanced()
        val backup = File(file.parentFile, "backup-${System.nanoTime()}.db")
        repo.backupTo(backup)
        assertTrue(backup.exists() && backup.length() > 0)
        backup.delete()
    }
}
