package com.derycode.deryaccount.data.local

/**
 * AppDatabase — the single Room database for DeryAccount.
 * Holds all 14+ tables (products, sales, stock, accounts, journal, users...)
 * and exposes the DAOs. get() returns the app-wide singleton so every
 * screen shares one connection — essential for the offline-first design.
 */

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.derycode.deryaccount.data.local.dao.*
import com.derycode.deryaccount.data.local.entity.*

@Database(
    entities = [
        Branch::class, User::class, Product::class, Customer::class,
        Sale::class, SaleItem::class, StockMovement::class, Supplier::class,
        Purchase::class, PurchaseItem::class, Expense::class, CashMovement::class,
        Shift::class, StockTransfer::class,
        Account::class, JournalEntry::class, JournalLine::class,
        HeldSale::class,
        CustomerPayment::class, SupplierPayment::class,
        PurchaseReturn::class, PurchaseReturnItem::class,
        PurchaseOrder::class, PurchaseOrderItem::class,
        FixedAsset::class, Employee::class, Payslip::class,
        Budget::class, BankLine::class,
        Batch::class, SerialNumber::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun branchDao(): BranchDao
    abstract fun userDao(): UserDao
    abstract fun productDao(): ProductDao
    abstract fun customerDao(): CustomerDao
    abstract fun saleDao(): SaleDao
    abstract fun saleItemDao(): SaleItemDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun supplierDao(): SupplierDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun purchaseItemDao(): PurchaseItemDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun cashDao(): CashDao
    abstract fun shiftDao(): ShiftDao
    abstract fun transferDao(): TransferDao
    abstract fun syncDao(): SyncDao
    abstract fun accountDao(): AccountDao
    abstract fun journalDao(): JournalDao
    abstract fun heldSaleDao(): HeldSaleDao
    abstract fun analyticsDao(): AnalyticsDao
    abstract fun customerPaymentDao(): CustomerPaymentDao
    abstract fun supplierPaymentDao(): SupplierPaymentDao
    abstract fun purchaseReturnDao(): PurchaseReturnDao
    abstract fun purchaseReturnItemDao(): PurchaseReturnItemDao
    abstract fun purchaseOrderDao(): PurchaseOrderDao
    abstract fun purchaseOrderItemDao(): PurchaseOrderItemDao
    abstract fun fixedAssetDao(): FixedAssetDao
    abstract fun employeeDao(): EmployeeDao
    abstract fun payslipDao(): PayslipDao
    abstract fun budgetDao(): BudgetDao
    abstract fun bankLineDao(): BankLineDao
    abstract fun batchDao(): BatchDao
    abstract fun serialDao(): SerialDao

    companion object {
        /** v2 → v3: add products.isFavourite + held_sales (Hold Sale). */
        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Favourites — backfill 0 so existing products start un-starred
                db.execSQL("ALTER TABLE products ADD COLUMN isFavourite INTEGER NOT NULL DEFAULT 0")
                // Held (parked) carts — matches the HeldSale entity exactly
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS held_sales (
                        id TEXT NOT NULL PRIMARY KEY,
                        branchId TEXT NOT NULL,
                        userId TEXT NOT NULL,
                        discount REAL NOT NULL,
                        linesJson TEXT NOT NULL,
                        note TEXT NOT NULL,
                        createdAt TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        /** v3 → v4: customer profiles — address + lifetime purchase/payment stats. */
        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN address TEXT")
                db.execSQL("ALTER TABLE customers ADD COLUMN totalPurchases REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE customers ADD COLUMN totalPaid REAL NOT NULL DEFAULT 0")
                // Backfill lifetime stats from existing sales so profiles start accurate.
                // totalPaid = cash paid at sale time + everything ever repaid on credit
                // (repaid = credit issued − balance still outstanding).
                db.execSQL("""
                    UPDATE customers SET totalPurchases = (
                        SELECT COALESCE(SUM(total), 0) FROM sales
                        WHERE sales.customerId = customers.id AND sales.isDeleted = 0)
                """.trimIndent())
                db.execSQL("""
                    UPDATE customers SET totalPaid =
                        (SELECT COALESCE(SUM(amountPaid), 0) FROM sales
                         WHERE sales.customerId = customers.id AND sales.isDeleted = 0)
                        + (SELECT COALESCE(SUM(total), 0) FROM sales
                           WHERE sales.customerId = customers.id
                             AND sales.paymentMethod = 'CREDIT' AND sales.isDeleted = 0)
                        - balance
                """.trimIndent())
            }
        }

        /** v4 → v5: products.reorderLevel — the reorder point for stock alerts. */
        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN reorderLevel REAL NOT NULL DEFAULT 0")
            }
        }

        /** v5 → v6: products.subcategory + products.imagePath (photos & subcategories). */
        private val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN subcategory TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE products ADD COLUMN imagePath TEXT")
            }
        }

        /** v6 → v7: money workflows — payments, purchase returns, purchase orders. */
        private val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS customer_payments (
                        id TEXT NOT NULL PRIMARY KEY,
                        customerId TEXT NOT NULL,
                        branchId TEXT NOT NULL,
                        userId TEXT NOT NULL,
                        amount REAL NOT NULL,
                        method TEXT NOT NULL,
                        reference TEXT,
                        note TEXT,
                        paidAt TEXT NOT NULL,
                        createdAt TEXT NOT NULL,
                        updatedAt TEXT NOT NULL,
                        syncState TEXT NOT NULL,
                        isDeleted INTEGER NOT NULL
                    )""")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS supplier_payments (
                        id TEXT NOT NULL PRIMARY KEY,
                        supplierId TEXT NOT NULL,
                        branchId TEXT NOT NULL,
                        userId TEXT NOT NULL,
                        amount REAL NOT NULL,
                        method TEXT NOT NULL,
                        reference TEXT,
                        note TEXT,
                        paidAt TEXT NOT NULL,
                        createdAt TEXT NOT NULL,
                        updatedAt TEXT NOT NULL,
                        syncState TEXT NOT NULL,
                        isDeleted INTEGER NOT NULL
                    )""")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS purchase_returns (
                        id TEXT NOT NULL PRIMARY KEY,
                        prNo TEXT NOT NULL,
                        supplierId TEXT,
                        branchId TEXT NOT NULL,
                        userId TEXT NOT NULL,
                        total REAL NOT NULL,
                        refundMethod TEXT NOT NULL,
                        note TEXT,
                        returnedAt TEXT NOT NULL,
                        createdAt TEXT NOT NULL,
                        updatedAt TEXT NOT NULL,
                        syncState TEXT NOT NULL,
                        isDeleted INTEGER NOT NULL
                    )""")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS purchase_return_items (
                        id TEXT NOT NULL PRIMARY KEY,
                        purchaseReturnId TEXT NOT NULL,
                        productId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        qty REAL NOT NULL,
                        unitCost REAL NOT NULL,
                        lineTotal REAL NOT NULL
                    )""")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS purchase_orders (
                        id TEXT NOT NULL PRIMARY KEY,
                        poNo TEXT NOT NULL,
                        supplierId TEXT,
                        branchId TEXT NOT NULL,
                        userId TEXT NOT NULL,
                        status TEXT NOT NULL,
                        expectedAt TEXT,
                        note TEXT,
                        orderedAt TEXT NOT NULL,
                        createdAt TEXT NOT NULL,
                        updatedAt TEXT NOT NULL,
                        syncState TEXT NOT NULL,
                        isDeleted INTEGER NOT NULL
                    )""")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS purchase_order_items (
                        id TEXT NOT NULL PRIMARY KEY,
                        orderId TEXT NOT NULL,
                        productId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        qty REAL NOT NULL,
                        unitCost REAL NOT NULL,
                        lineTotal REAL NOT NULL
                    )""")
            }
        }

                /** v7 → v8: accounting suite + tracking. */
        private val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS fixed_assets (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL, category TEXT NOT NULL,
                        cost REAL NOT NULL, salvage REAL NOT NULL,
                        purchaseDate TEXT NOT NULL, lifeMonths INTEGER NOT NULL,
                        accumulatedDepreciation REAL NOT NULL,
                        soldAt TEXT,
                        createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL,
                        syncState TEXT NOT NULL, isDeleted INTEGER NOT NULL
                    )""")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS employees (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL, role TEXT NOT NULL, phone TEXT,
                        monthlySalary REAL NOT NULL, isActive INTEGER NOT NULL,
                        createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL,
                        syncState TEXT NOT NULL, isDeleted INTEGER NOT NULL
                    )""")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS payslips (
                        id TEXT NOT NULL PRIMARY KEY,
                        employeeId TEXT NOT NULL, employeeName TEXT NOT NULL,
                        month TEXT NOT NULL, gross REAL NOT NULL,
                        deductions REAL NOT NULL, net REAL NOT NULL,
                        method TEXT NOT NULL, note TEXT, paidAt TEXT NOT NULL,
                        createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL,
                        syncState TEXT NOT NULL, isDeleted INTEGER NOT NULL
                    )""")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS budgets (
                        id TEXT NOT NULL PRIMARY KEY,
                        month TEXT NOT NULL, kind TEXT NOT NULL,
                        category TEXT NOT NULL, amount REAL NOT NULL,
                        createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL,
                        syncState TEXT NOT NULL, isDeleted INTEGER NOT NULL
                    )""")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS bank_lines (
                        id TEXT NOT NULL PRIMARY KEY,
                        branchId TEXT NOT NULL, statementDate TEXT NOT NULL,
                        description TEXT NOT NULL, direction TEXT NOT NULL,
                        amount REAL NOT NULL, isMatched INTEGER NOT NULL,
                        note TEXT,
                        createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL,
                        syncState TEXT NOT NULL, isDeleted INTEGER NOT NULL
                    )""")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS batches (
                        id TEXT NOT NULL PRIMARY KEY,
                        productId TEXT NOT NULL, productName TEXT NOT NULL,
                        batchNo TEXT NOT NULL, expiryDate TEXT,
                        qty REAL NOT NULL, branchId TEXT NOT NULL, note TEXT,
                        createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL,
                        syncState TEXT NOT NULL, isDeleted INTEGER NOT NULL
                    )""")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS serials (
                        id TEXT NOT NULL PRIMARY KEY,
                        productId TEXT NOT NULL, productName TEXT NOT NULL,
                        serial TEXT NOT NULL, status TEXT NOT NULL,
                        soldAt TEXT, branchId TEXT NOT NULL,
                        createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL,
                        syncState TEXT NOT NULL, isDeleted INTEGER NOT NULL
                    )""")
            }
        }

        @Volatile private var INSTANCE: AppDatabase? = null

        /**
         * If the user picked a backup file in Settings → Restore, it was
         * staged as restore_pending.db next to the live database. On the
         * next open (fresh app start) we swap it in, atomically replacing
         * the live DB. Runs BEFORE Room opens any file.
         */
        private fun applyPendingRestore(context: Context) {
            try {
                val dbDir = context.getDatabasePath("deryaccount.db").parentFile ?: return
                val pending = java.io.File(dbDir, "restore_pending.db")
                if (!pending.exists() || pending.length() < 1024L) return
                val live = java.io.File(dbDir, "deryaccount.db")
                listOf("-wal", "-shm").forEach {
                    java.io.File(dbDir, "deryaccount.db$it").delete()
                }
                if (live.exists()) live.delete()
                pending.renameTo(live)
            } catch (_: Exception) { }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                applyPendingRestore(context.applicationContext)
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "deryaccount.db"
                )
                    // v2 -> v3: favourites column + held_sales table.
                    // MUST ship as a real migration — a destructive fallback here
                    // would WIPE every shop's books on update.
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    // Kept only as a last resort for pre-accounting installs (DB v1).
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
