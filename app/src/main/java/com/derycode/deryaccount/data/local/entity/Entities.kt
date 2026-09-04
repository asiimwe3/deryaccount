package com.derycode.deryaccount.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Every record carries a UUID + sync fields so it can be created offline
 * and synced to Supabase later. The local Room DB is the source of truth.
 */
interface Syncable {
    val createdAt: String
    val updatedAt: String
    val syncState: String   // "synced" | "pending" | "conflict"
    val isDeleted: Boolean
}

@Entity(tableName = "branches")
data class Branch(
    @PrimaryKey val id: String,
    val name: String,
    val location: String,
    val isActive: Boolean = true,
    override val createdAt: String,
    override val updatedAt: String,
    override val syncState: String = "pending",
    override val isDeleted: Boolean = false
) : Syncable

@Entity(
    tableName = "users",
    indices = [Index("username", unique = true)]
)
data class User(
    @PrimaryKey val id: String,
    val username: String,
    val pinHash: String,          // 4-6 digit PIN hashed
    val fullName: String,
    val role: String,             // OWNER | MANAGER | CASHIER | ACCOUNTANT
    val branchId: String,         // branch the user belongs to (OWNER sees all)
    val isActive: Boolean = true,
    override val createdAt: String,
    override val updatedAt: String,
    override val syncState: String = "pending",
    override val isDeleted: Boolean = false
) : Syncable

@Entity(
    tableName = "products",
    indices = [Index(value = ["barcode", "branchId"], unique = false)]
)
data class Product(
    @PrimaryKey val id: String,
    val name: String,
    val barcode: String?,          // may be null for weighed/loose items
    val category: String,
    val unit: String = "pcs",      // pcs, kg, l, pkt, crate...
    val costPrice: Double,         // purchase price per unit
    val retailPrice: Double,       // retail price per unit
    val wholesalePrice: Double?,   // optional wholesale tier
    val wholesaleMinQty: Int = 0,  // minimum qty for wholesale price
    val taxRate: Double = 0.0,     // VAT % (0 for most UG shop items)
    val stockQty: Double,          // current stock at branch
    val lowStockAlert: Double = 5.0,
    val expiryDate: String?,       // for perishables
    val branchId: String,
    override val createdAt: String,
    override val updatedAt: String,
    override val syncState: String = "pending",
    override val isDeleted: Boolean = false
) : Syncable

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String?,
    val type: String = "RETAIL",    // RETAIL | WHOLESALE | DISTRIBUTOR
    val creditLimit: Double = 0.0,
    val balance: Double = 0.0,      // outstanding credit (they owe us)
    override val createdAt: String,
    override val updatedAt: String,
    override val syncState: String = "pending",
    override val isDeleted: Boolean = false
) : Syncable

@Entity(tableName = "sales")
data class Sale(
    @PrimaryKey val id: String,
    val receiptNo: String,          // e.g. KLA-20260904-0132 (branch-date-seq)
    val branchId: String,
    val userId: String,             // cashier
    val customerId: String?,
    val saleType: String = "RETAIL",// RETAIL | WHOLESALE
    val subtotal: Double,
    val taxTotal: Double,
    val discount: Double,
    val total: Double,
    val amountPaid: Double,
    val changeGiven: Double,
    val paymentMethod: String,      // CASH | MTN_MOMO | AIRTEL_MONEY | CREDIT
    val soldAt: String,             // real time of sale (kept when offline)
    val shiftId: String?,
    override val createdAt: String,
    override val updatedAt: String,
    override val syncState: String = "pending",
    override val isDeleted: Boolean = false
) : Syncable

@Entity(tableName = "sale_items")
data class SaleItem(
    @PrimaryKey val id: String,
    val saleId: String,
    val productId: String,
    val name: String,               // snapshot of product name
    val qty: Double,
    val unitPrice: Double,          // price actually charged
    val costPrice: Double,          // for margin calc
    val lineTotal: Double
)

@Entity(tableName = "stock_movements")
data class StockMovement(
    @PrimaryKey val id: String,
    val productId: String,
    val branchId: String,
    val type: String,               // SALE | PURCHASE | TRANSFER_OUT | TRANSFER_IN | ADJUSTMENT | WASTE
    val qty: Double,                // signed: negative out, positive in
    val reference: String?,         // sale id / purchase id / transfer id
    val note: String?,
    val movedAt: String,
    override val createdAt: String,
    override val updatedAt: String,
    override val syncState: String = "pending",
    override val isDeleted: Boolean = false
) : Syncable

@Entity(tableName = "suppliers")
data class Supplier(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String?,
    val balance: Double = 0.0,      // we owe them
    override val createdAt: String,
    override val updatedAt: String,
    override val syncState: String = "pending",
    override val isDeleted: Boolean = false
) : Syncable

@Entity(tableName = "purchases")
data class Purchase(
    @PrimaryKey val id: String,
    val supplierId: String?,
    val branchId: String,
    val total: Double,
    val paidAmount: Double,         // if < total, supplier balance grows
    val receivedAt: String,
    val note: String?,
    override val createdAt: String,
    override val updatedAt: String,
    override val syncState: String = "pending",
    override val isDeleted: Boolean = false
) : Syncable

@Entity(tableName = "purchase_items")
data class PurchaseItem(
    @PrimaryKey val id: String,
    val purchaseId: String,
    val productId: String,
    val qty: Double,
    val unitCost: Double,
    val lineTotal: Double
)

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey val id: String,
    val branchId: String,
    val userId: String,
    val category: String,           // RENT | SALARIES | TRANSPORT | UTILITIES | OTHER
    val amount: Double,
    val note: String?,
    val spentAt: String,
    override val createdAt: String,
    override val updatedAt: String,
    override val syncState: String = "pending",
    override val isDeleted: Boolean = false
) : Syncable

@Entity(tableName = "cash_movements")
data class CashMovement(
    @PrimaryKey val id: String,
    val branchId: String,
    val userId: String,
    val type: String,               // OPEN_SHIFT | CASH_IN | CASH_OUT | CLOSE_SHIFT | DEPOSIT
    val amount: Double,
    val note: String?,
    val movedAt: String,
    override val createdAt: String,
    override val updatedAt: String,
    override val syncState: String = "pending",
    override val isDeleted: Boolean = false
) : Syncable

@Entity(tableName = "shifts")
data class Shift(
    @PrimaryKey val id: String,
    val branchId: String,
    val userId: String,
    val openedAt: String,
    val closedAt: String?,
    val openingCash: Double,
    val closingCash: Double?,
    val expectedCash: Double?,       // computed from sales
    val variance: Double?,
    override val createdAt: String,
    override val updatedAt: String,
    override val syncState: String = "pending",
    override val isDeleted: Boolean = false
) : Syncable

@Entity(tableName = "stock_transfers")
data class StockTransfer(
    @PrimaryKey val id: String,
    val fromBranchId: String,
    val toBranchId: String,
    val productId: String,
    val qty: Double,
    val status: String = "SENT",     // SENT | RECEIVED | CANCELLED
    val note: String?,
    val transferredAt: String,
    override val createdAt: String,
    override val updatedAt: String,
    override val syncState: String = "pending",
    override val isDeleted: Boolean = false
) : Syncable
