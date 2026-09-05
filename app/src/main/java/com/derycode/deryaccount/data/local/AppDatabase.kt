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
        HeldSale::class
    ],
    version = 3,
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

        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "deryaccount.db"
                )
                    // v2 -> v3: favourites column + held_sales table.
                    // MUST ship as a real migration — a destructive fallback here
                    // would WIPE every shop's books on update.
                    .addMigrations(MIGRATION_2_3)
                    // Kept only as a last resort for pre-accounting installs (DB v1).
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
