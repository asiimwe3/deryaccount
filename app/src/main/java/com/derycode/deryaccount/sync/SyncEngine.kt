package com.derycode.deryaccount.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.derycode.deryaccount.data.local.AppDatabase
import com.derycode.deryaccount.data.remote.SupabaseClient
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * SyncEngine — pushes locally-created (offline) records to Supabase and
 * pulls remote changes (head-office price updates, new products) down.
 *
 * Push: every row where syncState = 'pending' is batch-upserted.
 * soldAt timestamps are preserved, so offline sales report at their
 * real time in head-office reports.
 *
 * Conflict rule: branch-generated records (sales, movements) always win;
 * head-office product edits are pulled with updated_at ordering.
 */
class SyncEngine(private val context: Context, private val db: AppDatabase) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var periodicJob: Job? = null

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Start periodic sync attempts every 30s.
     * CRITICAL: this is a cheap no-op when offline — SupabaseClient.instance
     * is null or !online(), so syncAll() returns immediately. The shop's
     * books keep working with zero internet; sync only catches up when a
     * connection appears. Never blocks the POS or accounting screens.
     */
    fun startPeriodic() {
        if (periodicJob?.isActive == true) return
        periodicJob = scope.launch {
            while (isActive) {
                try { syncAll() } catch (e: Exception) { /* keep looping */ }
                delay(30_000)
            }
        }
    }

    fun stopPeriodic() {
        periodicJob?.cancel()
        periodicJob = null
    }

    /** One full push + pull cycle. */
    suspend fun syncAll(): SyncResult {
        val client = SupabaseClient.instance ?: return SyncResult(0, 0, false)
        if (!client.online()) return SyncResult(0, 0, false)

        var pushed = 0
        var pulled = 0

        // ---- PUSH pending local rows ----
        pushed += push("branches", "markBranches") { db.branchDao().pendingSync().map { it.toJson() } }
        pushed += push("products", "markProducts") { db.productDao().pendingSync().map { it.toJson() } }
        pushed += push("sales", "markSales") { db.saleDao().pendingSync().map { s -> saleWithItemsJson(s) } }
        pushed += push("customers", "markCustomers") { db.customerDao().pendingSync().map { it.toJson() } }
        pushed += push("stock_movements", "markStockMovements") { db.stockMovementDao().pendingSync().map { it.toJson() } }
        pushed += push("suppliers", "markSuppliers") { db.supplierDao().pendingSync().map { it.toJson() } }
        pushed += push("purchases", "markPurchases") { db.purchaseDao().pendingSync().map { it.toJson() } }
        pushed += push("expenses", "markExpenses") { db.expenseDao().pendingSync().map { it.toJson() } }
        pushed += push("cash_movements", "markCashMovements") { db.cashDao().pendingSync().map { it.toJson() } }
        pushed += push("shifts", "markShifts") { db.shiftDao().pendingSync().map { it.toJson() } }
        pushed += push("stock_transfers", "markTransfers") { db.transferDao().pendingSync().map { it.toJson() } }

        // ---- PULL remote changes ----
        pulled += pullBranches(client)
        pulled += pullProducts(client)

        return SyncResult(pushed, pulled, true)
    }

    private suspend fun push(table: String, marker: String, fetchPending: suspend () -> List<JSONObject>): Int {
        val client = SupabaseClient.instance ?: return 0
        val pending = fetchPending()
        if (pending.isEmpty()) return 0

        val arr = JSONArray()
        pending.forEach { arr.put(it) }
        if (!client.upsert(table, arr)) return 0

        val ids = pending.map { it.getString("id") }
        when (marker) {
            "markBranches" -> db.syncDao().markBranches(ids)
            "markProducts" -> db.syncDao().markProducts(ids)
            "markSales" -> db.syncDao().markSales(ids)
            "markCustomers" -> db.syncDao().markCustomers(ids)
            "markStockMovements" -> db.syncDao().markStockMovements(ids)
            "markSuppliers" -> db.syncDao().markSuppliers(ids)
            "markPurchases" -> db.syncDao().markPurchases(ids)
            "markExpenses" -> db.syncDao().markExpenses(ids)
            "markCashMovements" -> db.syncDao().markCashMovements(ids)
            "markShifts" -> db.syncDao().markShifts(ids)
            "markTransfers" -> db.syncDao().markTransfers(ids)
        }
        return pending.size
    }

    private suspend fun pullBranches(client: SupabaseClient): Int {
        val rows = client.select("branches", "?select=*") ?: return 0
        val list = mutableListOf<com.derycode.deryaccount.data.local.entity.Branch>()
        for (i in 0 until rows.length()) list.add(rows.getJSONObject(i).toBranch())
        if (list.isNotEmpty()) { db.branchDao().upsertAll(list); return list.size }
        return 0
    }

    private suspend fun pullProducts(client: SupabaseClient): Int {
        val rows = client.select("products", "?select=*&updated_at=gt.${lastPullIso()}") ?: return 0
        val list = mutableListOf<com.derycode.deryaccount.data.local.entity.Product>()
        for (i in 0 until rows.length()) list.add(rows.getJSONObject(i).toProduct())
        if (list.isNotEmpty()) { db.productDao().upsertAll(list); return list.size }
        return 0
    }

    // ---------- JSON mappers (local fields mirror Supabase snake_case columns) ----------

    private suspend fun saleWithItemsJson(s: com.derycode.deryaccount.data.local.entity.Sale): JSONObject {
        val items = db.saleItemDao().forSale(s.id)
        val o = s.toJson()
        val jItems = JSONArray()
        items.forEach {
            jItems.put(JSONObject()
                .put("id", it.id).put("sale_id", it.saleId).put("product_id", it.productId)
                .put("name", it.name).put("qty", it.qty).put("unit_price", it.unitPrice)
                .put("cost_price", it.costPrice).put("line_total", it.lineTotal))
        }
        o.put("items", jItems)
        return o
    }

    private fun com.derycode.deryaccount.data.local.entity.Sale.toJson() = JSONObject()
        .put("id", id).put("receipt_no", receiptNo).put("branch_id", branchId)
        .put("user_id", userId).put("customer_id", customerId ?: JSONObject.NULL)
        .put("sale_type", saleType).put("subtotal", subtotal).put("tax_total", taxTotal)
        .put("discount", discount).put("total", total).put("amount_paid", amountPaid)
        .put("change_given", changeGiven).put("payment_method", paymentMethod)
        .put("sold_at", soldAt).put("shift_id", shiftId ?: JSONObject.NULL)
        .put("created_at", createdAt).put("updated_at", updatedAt)

    private fun com.derycode.deryaccount.data.local.entity.Product.toJson() = JSONObject()
        .put("id", id).put("name", name).put("barcode", barcode ?: JSONObject.NULL)
        .put("category", category).put("unit", unit).put("cost_price", costPrice)
        .put("retail_price", retailPrice).put("wholesale_price", wholesalePrice ?: JSONObject.NULL)
        .put("wholesale_min_qty", wholesaleMinQty).put("tax_rate", taxRate)
        .put("stock_qty", stockQty).put("low_stock_alert", lowStockAlert)
        .put("reorder_level", reorderLevel)
        .put("expiry_date", expiryDate ?: JSONObject.NULL).put("branch_id", branchId)
        .put("created_at", createdAt).put("updated_at", updatedAt)

    private fun com.derycode.deryaccount.data.local.entity.Customer.toJson() = JSONObject()
        .put("id", id).put("name", name).put("phone", phone ?: JSONObject.NULL)
        .put("address", address ?: JSONObject.NULL)
        .put("type", type).put("credit_limit", creditLimit).put("balance", balance)
        .put("total_purchases", totalPurchases).put("total_paid", totalPaid)
        .put("created_at", createdAt).put("updated_at", updatedAt)

    private fun com.derycode.deryaccount.data.local.entity.Supplier.toJson() = JSONObject()
        .put("id", id).put("name", name).put("phone", phone ?: JSONObject.NULL)
        .put("balance", balance).put("created_at", createdAt).put("updated_at", updatedAt)

    private fun com.derycode.deryaccount.data.local.entity.Purchase.toJson() = JSONObject()
        .put("id", id).put("supplier_id", supplierId ?: JSONObject.NULL).put("branch_id", branchId)
        .put("total", total).put("paid_amount", paidAmount).put("received_at", receivedAt)
        .put("note", note ?: JSONObject.NULL).put("created_at", createdAt).put("updated_at", updatedAt)

    private fun com.derycode.deryaccount.data.local.entity.Expense.toJson() = JSONObject()
        .put("id", id).put("branch_id", branchId).put("user_id", userId).put("category", category)
        .put("amount", amount).put("note", note ?: JSONObject.NULL).put("spent_at", spentAt)
        .put("created_at", createdAt).put("updated_at", updatedAt)

    private fun com.derycode.deryaccount.data.local.entity.CashMovement.toJson() = JSONObject()
        .put("id", id).put("branch_id", branchId).put("user_id", userId).put("type", type)
        .put("amount", amount).put("note", note ?: JSONObject.NULL).put("moved_at", movedAt)
        .put("created_at", createdAt).put("updated_at", updatedAt)

    private fun com.derycode.deryaccount.data.local.entity.Shift.toJson() = JSONObject()
        .put("id", id).put("branch_id", branchId).put("user_id", userId).put("opened_at", openedAt)
        .put("closed_at", closedAt ?: JSONObject.NULL).put("opening_cash", openingCash)
        .put("closing_cash", closingCash ?: JSONObject.NULL).put("expected_cash", expectedCash ?: JSONObject.NULL)
        .put("variance", variance ?: JSONObject.NULL).put("created_at", createdAt).put("updated_at", updatedAt)

    private fun com.derycode.deryaccount.data.local.entity.StockMovement.toJson() = JSONObject()
        .put("id", id).put("product_id", productId).put("branch_id", branchId).put("type", type)
        .put("qty", qty).put("reference", reference ?: JSONObject.NULL).put("note", note ?: JSONObject.NULL)
        .put("moved_at", movedAt).put("created_at", createdAt).put("updated_at", updatedAt)

    private fun com.derycode.deryaccount.data.local.entity.StockTransfer.toJson() = JSONObject()
        .put("id", id).put("from_branch_id", fromBranchId).put("to_branch_id", toBranchId)
        .put("product_id", productId).put("qty", qty).put("status", status)
        .put("note", note ?: JSONObject.NULL).put("transferred_at", transferredAt)
        .put("created_at", createdAt).put("updated_at", updatedAt)

    private fun com.derycode.deryaccount.data.local.entity.Branch.toJson() = JSONObject()
        .put("id", id).put("name", name).put("location", location)
        .put("is_active", isActive).put("created_at", createdAt).put("updated_at", updatedAt)

    private fun JSONObject.toBranch() = com.derycode.deryaccount.data.local.entity.Branch(
        id = getString("id"), name = getString("name"),
        location = optString("location", ""), isActive = optBoolean("is_active", true),
        createdAt = optString("created_at", nowIso()), updatedAt = optString("updated_at", nowIso()),
        syncState = "synced", isDeleted = optBoolean("is_deleted", false)
    )

    private fun JSONObject.toProduct() = com.derycode.deryaccount.data.local.entity.Product(
        id = getString("id"), name = getString("name"),
        barcode = if (isNull("barcode")) null else optString("barcode"),
        category = optString("category", "General"), unit = optString("unit", "pcs"),
        costPrice = optDouble("cost_price", 0.0), retailPrice = optDouble("retail_price", 0.0),
        wholesalePrice = if (isNull("wholesale_price")) null else optDouble("wholesale_price"),
        wholesaleMinQty = optInt("wholesale_min_qty", 0), taxRate = optDouble("tax_rate", 0.0),
        stockQty = optDouble("stock_qty", 0.0), lowStockAlert = optDouble("low_stock_alert", 5.0),
        expiryDate = if (isNull("expiry_date")) null else optString("expiry_date"),
        branchId = getString("branch_id"),
        createdAt = optString("created_at", nowIso()), updatedAt = optString("updated_at", nowIso()),
        syncState = "synced", isDeleted = optBoolean("is_deleted", false)
    )

    private fun nowIso(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())

    private fun lastPullIso(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .format(Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000))

    data class SyncResult(val pushed: Int, val pulled: Int, val wasOnline: Boolean)
}
