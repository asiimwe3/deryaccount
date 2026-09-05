package com.derycode.deryaccount.data.local.dao

import androidx.room.*
import com.derycode.deryaccount.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE isDeleted = 0 AND branchId = :branchId ORDER BY name")
    fun observeBranchProducts(branchId: String): Flow<List<Product>>

    /** POS tiles: favourites first, then alphabetical. */
    @Query("SELECT * FROM products WHERE isDeleted = 0 AND branchId = :branchId ORDER BY isFavourite DESC, name COLLATE NOCASE ASC")
    fun catalogueForBranch(branchId: String): Flow<List<Product>>

    /** Every product in every branch (stock self-check). */
    @Query("SELECT * FROM products WHERE isDeleted = 0")
    suspend fun allProductsOnce(): List<Product>

    /** Star / unstar a fast seller. */
    @Query("UPDATE products SET isFavourite = :fav WHERE id = :id")
    suspend fun setFavourite(id: String, fav: Boolean)

    @Query("SELECT * FROM products WHERE isDeleted = 0 AND (barcode = :barcode) AND (branchId = :branchId) LIMIT 1")
    suspend fun findByBarcode(barcode: String, branchId: String): Product?

    @Query("SELECT * FROM products WHERE isDeleted = 0 AND (name LIKE '%' || :q || '%' OR barcode LIKE '%' || :q || '%') AND branchId = :branchId ORDER BY name LIMIT 50")
    suspend fun search(q: String, branchId: String): List<Product>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun get(id: String): Product?

    @Query("SELECT * FROM products WHERE isDeleted = 0 AND stockQty <= lowStockAlert ORDER BY stockQty ASC")
    fun observeLowStock(): Flow<List<Product>>

    /** Reorder watchlist: at/below reorder level (or the low-stock alert when no reorder level is set). */
    @Query("""SELECT * FROM products WHERE isDeleted = 0 AND stockQty <=
               CASE WHEN reorderLevel > 0 THEN reorderLevel ELSE lowStockAlert END
               ORDER BY stockQty ASC""")
    fun observeReorder(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE syncState = 'pending' AND isDeleted = 0")
    suspend fun pendingSync(): List<Product>

    @Upsert
    suspend fun upsert(product: Product)

    @Upsert
    suspend fun upsertAll(products: List<Product>)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE products SET stockQty = stockQty + :delta, updatedAt = :now, syncState = 'pending' WHERE id = :id")
    suspend fun adjustStock(id: String, delta: Double, now: String)

    @Query("SELECT COUNT(*) FROM products")
    suspend fun count(): Int
}

@Dao
interface SaleDao {
    @Query("SELECT * FROM sales WHERE isDeleted = 0 AND branchId = :branchId ORDER BY soldAt DESC LIMIT 100")
    fun observeRecentSales(branchId: String): Flow<List<Sale>>

    @Query("SELECT * FROM sales WHERE syncState = 'pending' ORDER BY soldAt ASC")
    suspend fun pendingSync(): List<Sale>

    @Query("SELECT * FROM sales WHERE id = :id")
    suspend fun get(id: String): Sale?

    /** Purchase history shown on a customer's profile (most recent first). */
    @Query("SELECT * FROM sales WHERE isDeleted = 0 AND customerId = :customerId ORDER BY soldAt DESC LIMIT 50")
    suspend fun forCustomer(customerId: String): List<Sale>

    @Upsert
    suspend fun upsert(sale: Sale)

    @Upsert
    suspend fun upsertAll(sales: List<Sale>)

    // Daily totals per branch — used for reports & Z-report
    @Query("""SELECT COALESCE(SUM(total),0) FROM sales
              WHERE isDeleted = 0 AND branchId = :branchId AND soldAt BETWEEN :from AND :to""")
    suspend fun totalBetween(branchId: String, from: String, to: String): Double

    @Query("""SELECT COALESCE(SUM(total),0) FROM sales
              WHERE isDeleted = 0 AND soldAt BETWEEN :from AND :to""")
    suspend fun totalAllBranches(from: String, to: String): Double

    @Query("""SELECT COUNT(*) FROM sales
              WHERE isDeleted = 0 AND branchId = :branchId AND soldAt BETWEEN :from AND :to""")
    suspend fun countBetween(branchId: String, from: String, to: String): Int

    @Query("""SELECT COALESCE(SUM(total),0) FROM sales
              WHERE isDeleted = 0 AND branchId = :branchId AND paymentMethod = :method
              AND soldAt BETWEEN :from AND :to""")
    suspend fun totalByMethodBetween(branchId: String, method: String, from: String, to: String): Double

    /** All sales in a period (CSV export). */
    @Query("""SELECT * FROM sales WHERE isDeleted = 0 AND branchId = :branchId
              AND soldAt BETWEEN :from AND :to ORDER BY soldAt DESC""")
    suspend fun between(branchId: String, from: String, to: String): List<Sale>
}

@Dao
interface SaleItemDao {
    @Query("SELECT * FROM sale_items WHERE saleId IN (:saleIds)")
    suspend fun forSales(saleIds: List<String>): List<SaleItem>

    /** Recent sales of one product, with receipt info (most recent first). */
    @Query("""SELECT sale_items.*, sales.receiptNo AS receipt_no, sales.soldAt AS sold_at,
                     sales.paymentMethod AS method
              FROM sale_items JOIN sales ON sale_items.saleId = sales.id
              WHERE sale_items.productId = :productId AND sales.isDeleted = 0
              ORDER BY sales.soldAt DESC LIMIT 30""")
    suspend fun salesForProduct(productId: String): List<SaleItemWithSale>

    @Query("""SELECT COALESCE(SUM(sale_items.qty), 0) FROM sale_items
              JOIN sales ON sale_items.saleId = sales.id
              WHERE sale_items.productId = :productId AND sales.isDeleted = 0""")
    suspend fun totalSoldFor(productId: String): Double

    @Query("""SELECT COALESCE(SUM(sale_items.lineTotal), 0) FROM sale_items
              JOIN sales ON sale_items.saleId = sales.id
              WHERE sale_items.productId = :productId AND sales.isDeleted = 0""")
    suspend fun revenueFor(productId: String): Double

    @Query("""SELECT COALESCE(SUM(sale_items.qty * sale_items.costPrice), 0) FROM sale_items
              JOIN sales ON sale_items.saleId = sales.id
              WHERE sale_items.productId = :productId AND sales.isDeleted = 0""")
    suspend fun cogsFor(productId: String): Double

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    suspend fun forSale(saleId: String): List<SaleItem>

    @Upsert
    suspend fun upsertAll(items: List<SaleItem>)

    @Upsert
    suspend fun upsert(item: SaleItem)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE isDeleted = 0 AND branchId = :branchId ORDER BY spentAt DESC LIMIT 100")
    fun observeRecent(branchId: String): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE syncState = 'pending'")
    suspend fun pendingSync(): List<Expense>

    @Query("""SELECT COALESCE(SUM(amount),0) FROM expenses
              WHERE isDeleted = 0 AND branchId = :branchId AND spentAt BETWEEN :from AND :to""")
    suspend fun totalBetween(branchId: String, from: String, to: String): Double

    @Upsert
    suspend fun upsert(expense: Expense)

    @Upsert
    suspend fun upsertAll(expenses: List<Expense>)
}

@Dao
interface CashDao {
    @Query("SELECT * FROM cash_movements WHERE isDeleted = 0 AND branchId = :branchId ORDER BY movedAt DESC LIMIT 50")
    fun observeRecent(branchId: String): Flow<List<CashMovement>>

    @Query("SELECT * FROM cash_movements WHERE syncState = 'pending'")
    suspend fun pendingSync(): List<CashMovement>

    @Upsert
    suspend fun upsert(movement: CashMovement)

    @Upsert
    suspend fun upsertAll(movements: List<CashMovement>)
}

@Dao
interface ShiftDao {
    @Query("SELECT * FROM shifts WHERE userId = :userId AND closedAt IS NULL ORDER BY openedAt DESC LIMIT 1")
    suspend fun openShift(userId: String): Shift?

    @Query("SELECT * FROM shifts WHERE branchId = :branchId ORDER BY openedAt DESC LIMIT 30")
    fun observeRecent(branchId: String): kotlinx.coroutines.flow.Flow<List<Shift>>

    @Query("SELECT * FROM shifts WHERE syncState = 'pending'")
    suspend fun pendingSync(): List<Shift>

    @Upsert
    suspend fun upsert(shift: Shift)

    @Upsert
    suspend fun upsertAll(shifts: List<Shift>)
}

/** A sale item joined with its sale — for the per-product sales & profit views. */
data class SaleItemWithSale(
    @androidx.room.Embedded val item: SaleItem,
    val receipt_no: String,
    val sold_at: String,
    val method: String
)

/** A purchase item joined with its purchase — for the per-product purchase history. */
data class PurchaseItemWithPurchase(
    @androidx.room.Embedded val item: PurchaseItem,
    val received_at: String,
    val paid_amount: Double
)

@Dao
interface StockMovementDao {
    @Query("SELECT * FROM stock_movements WHERE syncState = 'pending'")
    suspend fun pendingSync(): List<StockMovement>

    /** Full movement history for one product (receive, sell, return, damage, count…). */
    @Query("""SELECT * FROM stock_movements WHERE isDeleted = 0 AND productId = :productId
              ORDER BY movedAt DESC LIMIT 100""")
    fun observeForProduct(productId: String): Flow<List<StockMovement>>

    @Upsert
    suspend fun upsert(movement: StockMovement)

    @Upsert
    suspend fun upsertAll(movements: List<StockMovement>)
}

@Dao
interface SupplierDao {
    @Query("SELECT * FROM suppliers WHERE isDeleted = 0 ORDER BY name")
    fun observeAll(): Flow<List<Supplier>>

    @Query("SELECT * FROM suppliers WHERE syncState = 'pending'")
    suspend fun pendingSync(): List<Supplier>

    @Upsert
    suspend fun upsert(supplier: Supplier)

    @Upsert
    suspend fun upsertAll(suppliers: List<Supplier>)
}

@Dao
interface PurchaseDao {
    @Query("SELECT * FROM purchases WHERE isDeleted = 0 ORDER BY receivedAt DESC LIMIT 100")
    fun observeRecent(): Flow<List<Purchase>>

    @Query("SELECT * FROM purchases WHERE syncState = 'pending'")
    suspend fun pendingSync(): List<Purchase>

    @Query("SELECT * FROM purchases WHERE id = :id")
    suspend fun get(id: String): Purchase?

    @Upsert
    suspend fun upsert(purchase: Purchase)

    @Upsert
    suspend fun upsertAll(purchases: List<Purchase>)
}

@Dao
interface PurchaseItemDao {
    @Query("SELECT * FROM purchase_items WHERE purchaseId = :purchaseId")
    suspend fun forPurchase(purchaseId: String): List<PurchaseItem>

    /** Purchase history for one product (most recent first). */
    @Query("""SELECT purchase_items.*, purchases.receivedAt AS received_at,
                     purchases.paidAmount AS paid_amount
              FROM purchase_items JOIN purchases ON purchase_items.purchaseId = purchases.id
              WHERE purchase_items.productId = :productId
              ORDER BY purchases.receivedAt DESC LIMIT 30""")
    suspend fun purchasesForProduct(productId: String): List<PurchaseItemWithPurchase>

    @Query("SELECT COALESCE(SUM(qty), 0) FROM purchase_items WHERE productId = :productId")
    suspend fun totalReceivedFor(productId: String): Double

    @Upsert
    suspend fun upsertAll(items: List<PurchaseItem>)

    @Upsert
    suspend fun upsert(item: PurchaseItem)
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers WHERE isDeleted = 0 ORDER BY name")
    fun observeAll(): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE isDeleted = 0 AND (name LIKE '%' || :q || '%' OR phone LIKE '%' || :q || '%') LIMIT 20")
    suspend fun search(q: String): List<Customer>

    /** Every customer (debtors self-check). */
    @Query("SELECT * FROM customers WHERE isDeleted = 0")
    suspend fun allOnce(): List<Customer>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun get(id: String): Customer?

    @Query("SELECT * FROM customers WHERE syncState = 'pending'")
    suspend fun pendingSync(): List<Customer>

    @Upsert
    suspend fun upsert(customer: Customer)

    @Upsert
    suspend fun upsertAll(customers: List<Customer>)
}

@Dao
interface BranchDao {
    @Query("SELECT * FROM branches WHERE syncState = 'pending' AND isDeleted = 0")
    suspend fun pendingSync(): List<Branch>

    @Query("SELECT * FROM branches WHERE isActive = 1 AND isDeleted = 0 ORDER BY name")
    fun observeAll(): Flow<List<Branch>>

    @Query("SELECT * FROM branches WHERE id = :id")
    suspend fun get(id: String): Branch?

    @Query("SELECT * FROM branches WHERE isDeleted = 0")
    suspend fun all(): List<Branch>

    @Upsert
    suspend fun upsert(branch: Branch)

    @Upsert
    suspend fun upsertAll(branches: List<Branch>)
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE isDeleted = 0 AND isActive = 1")
    suspend fun allOnce(): List<User>

    @Query("SELECT * FROM users WHERE isActive = 1 AND isDeleted = 0 AND username = :username LIMIT 1")
    suspend fun byUsername(username: String): User?

    @Query("SELECT COUNT(*) FROM users")
    suspend fun count(): Int

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun get(id: String): User?

    @Upsert
    suspend fun upsert(user: User)

    @Upsert
    suspend fun upsertAll(users: List<User>)
}

@Dao
interface TransferDao {
    @Query("SELECT * FROM stock_transfers WHERE isDeleted = 0 AND toBranchId = :branchId AND status = 'SENT' ORDER BY transferredAt DESC")
    fun observeIncoming(branchId: String): Flow<List<StockTransfer>>

    @Query("SELECT * FROM stock_transfers WHERE syncState = 'pending'")
    suspend fun pendingSync(): List<StockTransfer>

    @Upsert
    suspend fun upsert(transfer: StockTransfer)

    @Upsert
    suspend fun upsertAll(transfers: List<StockTransfer>)
}

// ---------- markSynced helpers used by SyncEngine ----------
@Dao
interface SyncDao {
    @Query("UPDATE branches SET syncState='synced' WHERE id IN (:ids)")
    suspend fun markBranches(ids: List<String>)
    @Query("UPDATE users SET syncState='synced' WHERE id IN (:ids)")
    suspend fun markUsers(ids: List<String>)
    @Query("UPDATE products SET syncState='synced' WHERE id IN (:ids)")
    suspend fun markProducts(ids: List<String>)
    @Query("UPDATE customers SET syncState='synced' WHERE id IN (:ids)")
    suspend fun markCustomers(ids: List<String>)
    @Query("UPDATE sales SET syncState='synced' WHERE id IN (:ids)")
    suspend fun markSales(ids: List<String>)
    @Query("UPDATE sale_items SET id = id WHERE saleId IN (:ids)")
    suspend fun touchSaleItems(ids: List<String>)
    @Query("UPDATE stock_movements SET syncState='synced' WHERE id IN (:ids)")
    suspend fun markStockMovements(ids: List<String>)
    @Query("UPDATE suppliers SET syncState='synced' WHERE id IN (:ids)")
    suspend fun markSuppliers(ids: List<String>)
    @Query("UPDATE purchases SET syncState='synced' WHERE id IN (:ids)")
    suspend fun markPurchases(ids: List<String>)
    @Query("UPDATE purchase_items SET id = id WHERE purchaseId IN (:ids)")
    suspend fun touchPurchaseItems(ids: List<String>)
    @Query("UPDATE expenses SET syncState='synced' WHERE id IN (:ids)")
    suspend fun markExpenses(ids: List<String>)
    @Query("UPDATE cash_movements SET syncState='synced' WHERE id IN (:ids)")
    suspend fun markCashMovements(ids: List<String>)
    @Query("UPDATE shifts SET syncState='synced' WHERE id IN (:ids)")
    suspend fun markShifts(ids: List<String>)
    @Query("UPDATE stock_transfers SET syncState='synced' WHERE id IN (:ids)")
    suspend fun markTransfers(ids: List<String>)
}


// ================= ACCOUNTING (books of account) =================

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY sortOrder, code")
    fun observeAll(): Flow<List<Account>>

    @Query("SELECT * FROM accounts ORDER BY sortOrder, code")
    suspend fun all(): List<Account>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: String): Account?

    @Query("SELECT * FROM accounts WHERE code = :code LIMIT 1")
    suspend fun byCode(code: String): Account?

    @Query("SELECT * FROM accounts WHERE isCash = 1 ORDER BY sortOrder")
    suspend fun cashAccounts(): List<Account>

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(a: Account)
}

@Dao
interface JournalDao {
    @androidx.room.RewriteQueriesToDropUnusedColumns
    @Query("""
        SELECT je.*, COALESCE(SUM(jl.debit), 0) AS debitTotal,
               COALESCE(SUM(jl.credit), 0) AS creditTotal,
               COALESCE(SUM(jl.debit - jl.credit), 0) AS cashEffect
        FROM journal_entries je
        JOIN journal_lines jl ON jl.entryId = je.id
        WHERE jl.accountId = :accountId AND je.entryDate BETWEEN :from AND :to
        GROUP BY je.id
        ORDER BY je.entryDate DESC, je.voucherNo DESC
    """)
    fun observeAccountEntries(accountId: String, from: String, to: String):
        Flow<List<AccountEntryView>>

    @Query("SELECT * FROM journal_entries WHERE id = :id")
    suspend fun getEntry(id: String): JournalEntry?

    @Query("SELECT * FROM journal_lines WHERE entryId = :entryId")
    suspend fun linesFor(entryId: String): List<JournalLine>

    @Query("SELECT * FROM journal_entries WHERE entryDate BETWEEN :from AND :to ORDER BY entryDate DESC LIMIT 300")
    suspend fun entriesBetween(from: String, to: String): List<JournalEntry>

    @Query("""
        SELECT a.id AS accountId, a.code AS code, a.name AS name, a.type AS type,
               COALESCE(SUM(jl.debit - jl.credit), 0) AS netBalance
        FROM accounts a
        LEFT JOIN journal_lines jl ON jl.accountId = a.id
        JOIN journal_entries je ON je.id = jl.entryId AND je.entryDate BETWEEN :from AND :to
        GROUP BY a.id
        ORDER BY a.sortOrder, a.code
    """)
    suspend fun trialBalance(from: String, to: String): List<AccountBalanceView>

    /** Closing balances of cash accounts for the running balance. */
    @Query("SELECT COALESCE(SUM(jl.debit - jl.credit), 0) FROM journal_lines jl JOIN journal_entries je ON je.id = jl.entryId WHERE jl.accountId = :accountId AND je.entryDate < :date")
    suspend fun balanceBefore(accountId: String, date: String): Double

    @Insert
    suspend fun insertEntry(e: JournalEntry)

    @Insert
    suspend fun insertLines(lines: List<JournalLine>)

    /** Total debits and credits across the whole ledger (for the self-check). */
    @Query("SELECT COALESCE(SUM(debit), 0) FROM journal_lines")
    suspend fun totalDebits(): Double

    @Query("SELECT COALESCE(SUM(credit), 0) FROM journal_lines")
    suspend fun totalCredits(): Double

    @Query("SELECT COUNT(*) FROM journal_entries")
    suspend fun entryCount(): Int
}

/** A journal entry as seen from one account (for ledger/cashbook views). */
data class AccountEntryView(
    val id: String,
    val entryDate: String,
    val voucherNo: String,
    val particulars: String,
    val debitTotal: Double,  // total debited to this account in the entry
    val creditTotal: Double, // total credited to this account in the entry
    val cashEffect: Double    // debit - credit from this account's perspective
)

/** Trial balance row. */
data class AccountBalanceView(
    val accountId: String,
    val code: String,
    val name: String,
    val type: String,
    val netBalance: Double
)


/** Parked carts (Hold Sale). Local only — never synced, never in the books. */
@Dao
interface HeldSaleDao {
    @Query("SELECT * FROM held_sales WHERE branchId = :branchId ORDER BY createdAt DESC")
    fun forBranch(branchId: String): Flow<List<HeldSale>>

    @Query("SELECT * FROM held_sales WHERE id = :id")
    suspend fun byId(id: String): HeldSale?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(held: HeldSale)

    @Query("DELETE FROM held_sales WHERE id = :id")
    suspend fun delete(id: String)
}

// =====================================================================
// v0.12.0 — analytics & money workflow DAOs
// =====================================================================

// ---- projection rows for the analytics screens ----
data class ProductStat(
    val id: String?, val name: String?,
    val totalQty: Double = 0.0, val totalRevenue: Double = 0.0)
data class ProductProfit(
    val id: String?, val name: String?,
    val totalRevenue: Double = 0.0, val totalCost: Double = 0.0, val totalProfit: Double = 0.0)
data class CategoryProfit(
    val name: String?,
    val totalRevenue: Double = 0.0, val totalCost: Double = 0.0, val totalProfit: Double = 0.0)
data class DayTotal(val day: String?, val total: Double = 0.0, val cnt: Int = 0)
data class MonthTotal(val month: String?, val total: Double = 0.0, val cnt: Int = 0)
data class BranchTotal(val branchId: String?, val total: Double = 0.0, val cnt: Int = 0)
data class DeadStockRow(
    val id: String?, val name: String?, val stockQty: Double = 0.0, val lastSold: String?)
data class ValuationRow(
    val category: String?, val costValue: Double = 0.0, val retailValue: Double = 0.0)
data class OpenCreditSale(
    val id: String, val receiptNo: String, val customerId: String?,
    val soldAt: String, val remaining: Double)

@Dao
interface AnalyticsDao {
    // Best sellers (by revenue) for a period
    @Query("""SELECT sale_items.productId AS id, sale_items.name AS name,
                     SUM(sale_items.qty) AS totalQty, SUM(sale_items.lineTotal) AS totalRevenue
              FROM sale_items JOIN sales ON sale_items.saleId = sales.id
              WHERE sales.isDeleted = 0
                AND (:branchId = '' OR sales.branchId = :branchId)
                AND sales.soldAt BETWEEN :from AND :to
              GROUP BY sale_items.productId ORDER BY totalRevenue DESC LIMIT 100""")
    suspend fun bestSellers(branchId: String, from: String, to: String): List<ProductStat>

    // Profit by product (revenue − cost of goods sold, at the cost captured at sale time)
    @Query("""SELECT sale_items.productId AS id, sale_items.name AS name,
                     SUM(sale_items.lineTotal) AS totalRevenue,
                     SUM(sale_items.qty * sale_items.costPrice) AS totalCost,
                     SUM(sale_items.lineTotal - sale_items.qty * sale_items.costPrice) AS totalProfit
              FROM sale_items JOIN sales ON sale_items.saleId = sales.id
              WHERE sales.isDeleted = 0
                AND (:branchId = '' OR sales.branchId = :branchId)
                AND sales.soldAt BETWEEN :from AND :to
              GROUP BY sale_items.productId ORDER BY totalProfit DESC LIMIT 200""")
    suspend fun profitByProduct(branchId: String, from: String, to: String): List<ProductProfit>

    // Profit by category
    @Query("""SELECT products.category AS name,
                     SUM(sale_items.lineTotal) AS totalRevenue,
                     SUM(sale_items.qty * sale_items.costPrice) AS totalCost,
                     SUM(sale_items.lineTotal - sale_items.qty * sale_items.costPrice) AS totalProfit
              FROM sale_items
              JOIN sales ON sale_items.saleId = sales.id
              JOIN products ON sale_items.productId = products.id
              WHERE sales.isDeleted = 0
                AND (:branchId = '' OR sales.branchId = :branchId)
                AND sales.soldAt BETWEEN :from AND :to
              GROUP BY products.category ORDER BY totalProfit DESC""")
    suspend fun profitByCategory(branchId: String, from: String, to: String): List<CategoryProfit>

    // Dead / slow stock: in stock but not sold since the cutoff (or never sold)
    @Query("""SELECT products.id AS id, products.name AS name, products.stockQty AS stockQty,
                     MAX(sales.soldAt) AS lastSold
              FROM products
              LEFT JOIN sale_items ON sale_items.productId = products.id
              LEFT JOIN sales ON sale_items.saleId = sales.id AND sales.isDeleted = 0
              WHERE products.isDeleted = 0 AND products.branchId = :branchId
                AND products.stockQty > 0
              GROUP BY products.id
              HAVING lastSold IS NULL OR lastSold < :cutoff
              ORDER BY lastSold IS NULL DESC, lastSold ASC LIMIT 200""")
    suspend fun deadStock(branchId: String, cutoff: String): List<DeadStockRow>

    // Stock valuation by category
    @Query("""SELECT category AS category,
                     SUM(stockQty * costPrice) AS costValue,
                     SUM(stockQty * retailPrice) AS retailValue
              FROM products WHERE isDeleted = 0 AND branchId = :branchId
              GROUP BY category ORDER BY costValue DESC""")
    suspend fun valuationByCategory(branchId: String): List<ValuationRow>

    // Daily totals for charts (last N days)
    @Query("""SELECT substr(soldAt, 1, 10) AS day, SUM(total) AS total, COUNT(*) AS cnt
              FROM sales WHERE isDeleted = 0
                AND (:branchId = '' OR branchId = :branchId)
                AND soldAt BETWEEN :from AND :to
              GROUP BY day ORDER BY day""")
    suspend fun dailyTotals(branchId: String, from: String, to: String): List<DayTotal>

    // Monthly totals for charts
    @Query("""SELECT substr(soldAt, 1, 7) AS month, SUM(total) AS total, COUNT(*) AS cnt
              FROM sales WHERE isDeleted = 0
                AND (:branchId = '' OR branchId = :branchId)
                AND soldAt BETWEEN :from AND :to
              GROUP BY month ORDER BY month""")
    suspend fun monthlyTotals(branchId: String, from: String, to: String): List<MonthTotal>

    // Branch comparison: sales
    @Query("""SELECT branchId AS branchId, SUM(total) AS total, COUNT(*) AS cnt
              FROM sales WHERE isDeleted = 0 AND soldAt BETWEEN :from AND :to
              GROUP BY branchId""")
    suspend fun salesByBranch(from: String, to: String): List<BranchTotal>

    // Branch comparison: expenses
    @Query("""SELECT branchId AS branchId, SUM(amount) AS total, COUNT(*) AS cnt
              FROM expenses WHERE isDeleted = 0 AND spentAt BETWEEN :from AND :to
              GROUP BY branchId""")
    suspend fun expensesByBranch(from: String, to: String): List<BranchTotal>

    // Customer aging: open credit invoices (never fully paid at sale time)
    @Query("""SELECT id, receiptNo, customerId, soldAt, (total - amountPaid) AS remaining
              FROM sales WHERE isDeleted = 0 AND paymentMethod = 'CREDIT'
                AND total > amountPaid
                AND (:branchId = '' OR branchId = :branchId)
              ORDER BY soldAt ASC""")
    suspend fun openCreditSales(branchId: String): List<OpenCreditSale>

    // VAT: sales of products that carry a tax rate (VAT-inclusive pricing)
    @Query("""SELECT sale_items.productId AS id, sale_items.name AS name,
                     MAX(products.taxRate) AS rate, SUM(sale_items.lineTotal) AS revenue
              FROM sale_items
              JOIN sales ON sale_items.saleId = sales.id
              JOIN products ON sale_items.productId = products.id
              WHERE sales.isDeleted = 0 AND products.taxRate > 0
                AND (:branchId = '' OR sales.branchId = :branchId)
                AND sales.soldAt BETWEEN :from AND :to
              GROUP BY sale_items.productId ORDER BY revenue DESC""")
    suspend fun vatableSales(branchId: String, from: String, to: String): List<VatableRow>

    // Expense totals per category in one month (for budgets vs actual)
    @Query("""SELECT category AS category, SUM(amount) AS total
              FROM expenses WHERE isDeleted = 0
                AND (:branchId = '' OR branchId = :branchId)
                AND spentAt BETWEEN :from AND :to
              GROUP BY category""")
    suspend fun expensesByCategory(branchId: String, from: String, to: String): List<ExpenseCategoryTotal>

    // Supplier aging: open purchase invoices (paid less than total)
    @Query("""SELECT id, supplierId, receivedAt, (total - paidAmount) AS remaining
              FROM purchases WHERE isDeleted = 0 AND total > paidAmount
                AND (:branchId = '' OR branchId = :branchId)
              ORDER BY receivedAt ASC""")
    suspend fun openPurchases(branchId: String): List<OpenPurchaseRow>
}

data class VatableRow(
    val id: String?, val name: String?, val rate: Double = 0.0, val revenue: Double = 0.0)
data class ExpenseCategoryTotal(
    val category: String?, val total: Double = 0.0)
data class OpenPurchaseRow(
    val id: String, val supplierId: String?, val receivedAt: String, val remaining: Double)

@Dao
interface CustomerPaymentDao {
    @Query("SELECT * FROM customer_payments WHERE isDeleted = 0 ORDER BY paidAt DESC LIMIT 200")
    fun observeRecent(): Flow<List<CustomerPayment>>

    @Query("SELECT * FROM customer_payments WHERE customerId = :customerId AND isDeleted = 0 ORDER BY paidAt DESC")
    suspend fun forCustomer(customerId: String): List<CustomerPayment>

    @Query("SELECT * FROM customer_payments WHERE syncState = 'pending'")
    suspend fun pendingSync(): List<CustomerPayment>

    @Upsert
    suspend fun upsert(payment: CustomerPayment)

    @Upsert
    suspend fun upsertAll(payments: List<CustomerPayment>)
}

@Dao
interface SupplierPaymentDao {
    @Query("SELECT * FROM supplier_payments WHERE isDeleted = 0 ORDER BY paidAt DESC LIMIT 200")
    fun observeRecent(): Flow<List<SupplierPayment>>

    @Query("SELECT * FROM supplier_payments WHERE supplierId = :supplierId AND isDeleted = 0 ORDER BY paidAt DESC")
    suspend fun forSupplier(supplierId: String): List<SupplierPayment>

    @Query("SELECT * FROM supplier_payments WHERE syncState = 'pending'")
    suspend fun pendingSync(): List<SupplierPayment>

    @Upsert
    suspend fun upsert(payment: SupplierPayment)

    @Upsert
    suspend fun upsertAll(payments: List<SupplierPayment>)
}

@Dao
interface PurchaseReturnDao {
    @Query("SELECT * FROM purchase_returns WHERE isDeleted = 0 ORDER BY returnedAt DESC LIMIT 100")
    fun observeRecent(): Flow<List<PurchaseReturn>>

    @Query("SELECT * FROM purchase_returns WHERE syncState = 'pending'")
    suspend fun pendingSync(): List<PurchaseReturn>

    @Query("SELECT * FROM purchase_returns WHERE id = :id")
    suspend fun get(id: String): PurchaseReturn?

    @Upsert
    suspend fun upsert(ret: PurchaseReturn)

    @Upsert
    suspend fun upsertAll(rets: List<PurchaseReturn>)
}

@Dao
interface PurchaseReturnItemDao {
    @Query("SELECT * FROM purchase_return_items WHERE purchaseReturnId = :returnId")
    suspend fun forReturn(returnId: String): List<PurchaseReturnItem>

    @Upsert
    suspend fun upsertAll(items: List<PurchaseReturnItem>)
}

@Dao
interface PurchaseOrderDao {
    @Query("SELECT * FROM purchase_orders WHERE isDeleted = 0 ORDER BY orderedAt DESC LIMIT 100")
    fun observeRecent(): Flow<List<PurchaseOrder>>

    @Query("SELECT * FROM purchase_orders WHERE syncState = 'pending'")
    suspend fun pendingSync(): List<PurchaseOrder>

    @Query("SELECT * FROM purchase_orders WHERE id = :id")
    suspend fun get(id: String): PurchaseOrder?

    @Upsert
    suspend fun upsert(order: PurchaseOrder)

    @Upsert
    suspend fun upsertAll(orders: List<PurchaseOrder>)
}

@Dao
interface PurchaseOrderItemDao {
    @Query("SELECT * FROM purchase_order_items WHERE orderId = :orderId")
    suspend fun forOrder(orderId: String): List<PurchaseOrderItem>

    @Upsert
    suspend fun upsertAll(items: List<PurchaseOrderItem>)
}

@Dao
interface FixedAssetDao {
    @Query("SELECT * FROM fixed_assets WHERE isDeleted = 0 AND soldAt IS NULL ORDER BY name")
    suspend fun allOnce(): List<FixedAsset>

    @Query("SELECT * FROM fixed_assets WHERE syncState = 'pending'")
    suspend fun pendingSync(): List<FixedAsset>

    @Query("SELECT * FROM fixed_assets WHERE id = :id")
    suspend fun get(id: String): FixedAsset?

    @Upsert
    suspend fun upsert(asset: FixedAsset)

    @Upsert
    suspend fun upsertAll(assets: List<FixedAsset>)
}

@Dao
interface EmployeeDao {
    @Query("SELECT * FROM employees WHERE isDeleted = 0 AND isActive = 1 ORDER BY name")
    suspend fun allOnce(): List<Employee>

    @Query("SELECT * FROM employees WHERE syncState = 'pending'")
    suspend fun pendingSync(): List<Employee>

    @Upsert
    suspend fun upsert(employee: Employee)

    @Upsert
    suspend fun upsertAll(employees: List<Employee>)
}

@Dao
interface PayslipDao {
    @Query("SELECT * FROM payslips WHERE isDeleted = 0 AND month = :month ORDER BY paidAt DESC")
    suspend fun forMonth(month: String): List<Payslip>

    @Query("SELECT * FROM payslips WHERE isDeleted = 0 ORDER BY paidAt DESC LIMIT 200")
    fun observeRecent(): Flow<List<Payslip>>

    @Query("SELECT * FROM payslips WHERE syncState = 'pending'")
    suspend fun pendingSync(): List<Payslip>

    @Upsert
    suspend fun upsert(payslip: Payslip)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE isDeleted = 0 AND month = :month AND kind = :kind")
    suspend fun forMonth(month: String, kind: String): List<Budget>

    @Query("SELECT * FROM budgets WHERE isDeleted = 0 AND month = :month AND kind = :kind AND category = :category LIMIT 1")
    suspend fun get(month: String, kind: String, category: String): Budget?

    @Query("SELECT * FROM budgets WHERE syncState = 'pending'")
    suspend fun pendingSync(): List<Budget>

    @Upsert
    suspend fun upsert(budget: Budget)
}

@Dao
interface BankLineDao {
    @Query("SELECT * FROM bank_lines WHERE isDeleted = 0 ORDER BY statementDate DESC LIMIT 300")
    fun observeRecent(): Flow<List<BankLine>>

    @Query("SELECT * FROM bank_lines WHERE syncState = 'pending'")
    suspend fun pendingSync(): List<BankLine>

    @Query("SELECT * FROM bank_lines WHERE id = :id")
    suspend fun get(id: String): BankLine?

    @Upsert
    suspend fun upsert(line: BankLine)

    @Upsert
    suspend fun upsertAll(lines: List<BankLine>)
}

@Dao
interface BatchDao {
    @Query("SELECT * FROM batches WHERE isDeleted = 0 AND branchId = :branchId ORDER BY expiryDate IS NULL, expiryDate ASC")
    fun observeForBranch(branchId: String): Flow<List<Batch>>

    @Query("SELECT * FROM batches WHERE syncState = 'pending'")
    suspend fun pendingSync(): List<Batch>

    @Upsert
    suspend fun upsert(batch: Batch)
}

@Dao
interface SerialDao {
    @Query("SELECT * FROM serials WHERE isDeleted = 0 AND branchId = :branchId ORDER BY createdAt DESC LIMIT 300")
    fun observeForBranch(branchId: String): Flow<List<SerialNumber>>

    @Query("SELECT * FROM serials WHERE syncState = 'pending'")
    suspend fun pendingSync(): List<SerialNumber>

    @Query("SELECT * FROM serials WHERE id = :id")
    suspend fun get(id: String): SerialNumber?

    @Upsert
    suspend fun upsert(serial: SerialNumber)
}
