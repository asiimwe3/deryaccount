package com.derycode.deryaccount.data.local

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
        Account::class, JournalEntry::class, JournalLine::class
    ],
    version = 2,
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

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "deryaccount.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
