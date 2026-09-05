@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.derycode.deryaccount.ui.data

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyListItems
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.data.local.AppDatabase
import com.derycode.deryaccount.data.local.entity.Customer
import com.derycode.deryaccount.data.local.entity.Product
import com.derycode.deryaccount.ui.theme.DaGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * v0.12.0 — Data suite: CSV import/export and sync status.
 * Import reads any CSV straight from the file picker; export writes to
 * Downloads and offers a share sheet (WhatsApp, email...).
 */

private fun ugx(v: Double) = "UGX %,d".format(v.toLong())

private fun nowIso() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())

private fun csv(v: String?): String =
    if (v == null) "" else if (v.contains(',') || v.contains('"')) "\"${v.replace("\"", "\"\"")}\"" else v

// ------------------------------------------------------------------
// CSV import
// ------------------------------------------------------------------

@Composable
fun CsvImportScreen(db: AppDatabase, branchId: String) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var msg by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) importing = true; msg = null
        if (uri != null) {
            scope.launch {
                try {
                    val text = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                    } ?: throw IllegalStateException("cannot read")
                    val lines = text.lines().filter { it.isNotBlank() }
                    if (lines.isEmpty()) throw IllegalStateException("empty file")

                    // Detect kind: header contains "name" + "cost"/"price" → products,
                    // "name" + "phone" → customers.
                    val header = lines.first().lowercase().split(",").map { it.trim() }
                    val body = if (header.any { it in listOf("name", "cost", "price", "retail") }) lines.drop(1) else lines
                    val isProducts = header.any { it == "cost" || it == "price" || it == "retail" }

                    var created = 0
                    if (isProducts) {
                        body.forEach { line ->
                            val f = line.split(",").map { it.trim().removeSurrounding("\"") }
                            if (f.isEmpty() || f[0].isBlank()) return@forEach
                            val name = f[0]
                            val cost = f.getOrNull(1)?.toDoubleOrNull() ?: 0.0
                            val retail = f.getOrNull(2)?.toDoubleOrNull() ?: cost * 1.3
                            val qty = f.getOrNull(3)?.toDoubleOrNull() ?: 0.0
                            val barcode = f.getOrNull(4)?.takeIf { it.isNotBlank() }
                            val category = f.getOrNull(5)?.takeIf { it.isNotBlank() } ?: "General Shop"
                            val now = nowIso()
                            db.productDao().upsert(Product(
                                id = UUID.randomUUID().toString(), name = name,
                                barcode = barcode, category = category,
                                unit = f.getOrNull(6)?.takeIf { it.isNotBlank() } ?: "pcs",
                                costPrice = cost, retailPrice = retail,
                                wholesalePrice = null, stockQty = qty,
                                branchId = branchId,
                                createdAt = now, updatedAt = now))
                            created++
                        }
                    } else {
                        body.forEach { line ->
                            val f = line.split(",").map { it.trim().removeSurrounding("\"") }
                            if (f.isEmpty() || f[0].isBlank()) return@forEach
                            val now = nowIso()
                            db.customerDao().upsert(Customer(
                                id = UUID.randomUUID().toString(), name = f[0],
                                phone = f.getOrNull(1)?.takeIf { it.isNotBlank() },
                                type = f.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "RETAIL",
                                createdAt = now, updatedAt = now))
                            created++
                        }
                    }
                    msg = "Imported $created ${if (isProducts) "products" else "customers"} ✓"
                } catch (e: Exception) {
                    msg = "Import failed: ${e.message}"
                } finally {
                    importing = false
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Import CSV", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text("Bring your products or customers in from any spreadsheet",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Products file columns", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("name, cost, retail, qty, barcode, category, unit", fontSize = 12.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                Text("Only \"name\" is required. Works with or without a header row.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text("Customers file columns", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("name, phone, type", fontSize = 12.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
        }
        Spacer(Modifier.height(14.dp))
        Button(onClick = { launcher.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values")) },
            modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Text("Choose CSV file…", fontSize = 16.sp)
        }
        if (importing) {
            Spacer(Modifier.height(8.dp))
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        }
        msg?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = if (it.contains("✓")) DaGreen else MaterialTheme.colorScheme.error)
        }
    }
}

// ------------------------------------------------------------------
// CSV export
// ------------------------------------------------------------------

@Composable
fun CsvExportScreen(db: AppDatabase, branchId: String) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var msg by remember { mutableStateOf<String?>(null) }

    fun saveAndShare(context: Context, name: String, content: String, done: (String) -> Unit) {
        try {
            val downloads = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS)
            if (!downloads.exists()) downloads.mkdirs()
            val file = File(downloads, name)
            file.writeText(content)
            done(file.absolutePath)
        } catch (e: Exception) {
            done("ERROR: ${e.message}")
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Export CSV", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text("Send your data to Excel, WhatsApp or email — saved in Downloads",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        msg?.let {
            Text(it, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = if (it.contains("Saved")) DaGreen else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp))
        }
        listOf(
            "Products & stock" to "products",
            "Customers (with balances)" to "customers",
            "Sales (this year)" to "sales",
            "Suppliers" to "suppliers"
        ).forEach { (label, kind) ->
            OutlinedButton(onClick = {
                scope.launch {
                    try {
                        val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                        when (kind) {
                            "products" -> {
                                val rows = db.productDao().allProductsOnce()
                                    .filter { it.branchId == branchId }
                                val sb = StringBuilder("name,category,barcode,unit,cost,retail,stockQty\n")
                                rows.forEach { sb.append("${csv(it.name)},${csv(it.category)},${csv(it.barcode)},${csv(it.unit)},${it.costPrice},${it.retailPrice},${it.stockQty}\n") }
                                saveAndShare(context, "deryaccount_products_$date.csv", sb.toString()) { msg = "Saved: $it" }
                            }
                            "customers" -> {
                                val rows = db.customerDao().allOnce()
                                val sb = StringBuilder("name,phone,type,balance,totalPurchases,totalPaid\n")
                                rows.forEach { sb.append("${csv(it.name)},${csv(it.phone)},${it.type},${it.balance},${it.totalPurchases},${it.totalPaid}\n") }
                                saveAndShare(context, "deryaccount_customers_$date.csv", sb.toString()) { msg = "Saved: $it" }
                            }
                            "sales" -> {
                                val year = SimpleDateFormat("yyyy", Locale.US).format(Date())
                                val rows = db.saleDao().between(
                                    branchId, "$year-01-01T00:00:00.000Z", "2099-01-01")
                                val sb = StringBuilder("receiptNo,date,customerId,type,subtotal,discount,total,amountPaid,method\n")
                                rows.forEach { r ->
                                    sb.append("${csv(r.receiptNo)},${csv(r.soldAt)},${csv(r.customerId)},${r.saleType},${r.subtotal},${r.discount},${r.total},${r.amountPaid},${r.paymentMethod}\n")
                                }
                                saveAndShare(context, "deryaccount_sales_$date.csv", sb.toString()) { msg = "Saved: $it" }
                            }
                            "suppliers" -> {
                                val rows = db.supplierDao().observeAll().first()
                                val sb = StringBuilder("name,phone,balance\n")
                                rows.forEach { sb.append("${csv(it.name)},${csv(it.phone)},${it.balance}\n") }
                                saveAndShare(context, "deryaccount_suppliers_$date.csv", sb.toString()) { msg = "Saved: $it" }
                            }
                        }
                    } catch (e: Exception) { msg = "Export failed: ${e.message}" }
                }
            }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

// ------------------------------------------------------------------
// Sync status (cloud sync + conflict policy)
// ------------------------------------------------------------------

@Composable
fun SyncStatusScreen(db: AppDatabase, syncEngine: Any?, onNavigate: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var counts by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var msg by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(tick) {
        counts = listOf(
            "Products" to db.productDao().pendingSync().size,
            "Sales" to db.saleDao().pendingSync().size,
            "Customers" to db.customerDao().pendingSync().size,
            "Suppliers" to db.supplierDao().pendingSync().size,
            "Purchases" to db.purchaseDao().pendingSync().size,
            "Users" to db.userDao().pendingSync().size,
            "Payments" to (db.customerPaymentDao().pendingSync().size +
                db.supplierPaymentDao().pendingSync().size),
            "Purchase orders" to db.purchaseOrderDao().pendingSync().size,
            "Returns" to db.purchaseReturnDao().pendingSync().size
        )
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Sync Status", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text("Cloud sync (multi-branch) — what's waiting to reach the cloud",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp)) {
                Text("How conflicts are handled", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Every record carries updatedAt. When the same record changed in two " +
                    "places, the NEWEST change wins (last-write-wins) — the shop that edited " +
                    "most recently keeps its version, and the older edit is replaced. Sales " +
                    "and payments are additive, so they never conflict — they merge.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            lazyListItems(counts) { (name, count) ->
                Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, Modifier.weight(1f), fontSize = 14.sp)
                        if (count > 0) Badge { Text("$count") }
                        else Text("all synced ✓", fontSize = 12.sp, color = DaGreen)
                    }
                }
            }
        }
        msg?.let { Text(it, fontSize = 12.sp, color = DaGreen) }
    }
}
