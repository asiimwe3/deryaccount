package com.derycode.deryaccount.data.local.dao

import androidx.room.*
import com.derycode.deryaccount.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE isDeleted = 0 AND branchId = :branchId ORDER BY name")
    fun observeBranchProducts(branchId: String): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE isDeleted = 0 AND (barcode = :barcode) AND (branchId = :branchId) LIMIT 1")
    suspend fun findByBarcode(barcode: String, branchId: String): Product?

    @Query("SELECT * FROM products WHERE isDeleted = 0 AND (name LIKE '%' || :q || '%' OR barcode LIKE '%' || :q || '%') AND branchId = :branchId ORDER BY name LIMIT 50")
    suspend fun search(q: String, branchId: String): List<Product>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun get(id: String): Product?

    @Query("SELECT * FROM products WHERE isDeleted = 0 AND stockQty <= lowStockAlert ORDER BY stockQty ASC")
    fun observeLowStock(): Flow<List<Product>>

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
}

@Dao
interface SaleItemDao {
    @Query("SELECT * FROM sale_items WHERE saleId IN (:saleIds)")
    suspend fun forSales(saleIds: List<String>): List<SaleItem>

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

    @Query("SELECT * FROM shifts WHERE syncState = 'pending'")
    suspend fun pendingSync(): List<Shift>

    @Upsert
    suspend fun upsert(shift: Shift)

    @Upsert
    suspend fun upsertAll(shifts: List<Shift>)
}

@Dao
interface StockMovementDao {
    @Query("SELECT * FROM stock_movements WHERE syncState = 'pending'")
    suspend fun pendingSync(): List<StockMovement>

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
    @Query("SELECT * FROM users WHERE isActive = 1 AND isDeleted = 0 AND username = :username LIMIT 1")
    suspend fun byUsername(username: String): User?

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
    @Query("""
        SELECT je.*, COALESCE(SUM(jl.debit - jl.credit), 0) AS cashEffect
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

    @Query("SELECT COUNT(*) FROM journal_entries")
    suspend fun entryCount(): Int
}

/** A journal entry as seen from one account (for ledger/cashbook views). */
data class AccountEntryView(
    val id: String,
    val entryDate: String,
    val voucherNo: String,
    val particulars: String,
    val cashEffect: Double   // debit - credit from this account's perspective
)

/** Trial balance row. */
data class AccountBalanceView(
    val accountId: String,
    val code: String,
    val name: String,
    val type: String,
    val netBalance: Double
)
