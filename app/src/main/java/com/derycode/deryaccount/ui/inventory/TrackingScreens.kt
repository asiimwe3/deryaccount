@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.derycode.deryaccount.ui.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyListItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.data.local.AppDatabase
import com.derycode.deryaccount.data.local.entity.Batch
import com.derycode.deryaccount.data.local.entity.Product
import com.derycode.deryaccount.data.local.entity.SerialNumber
import com.derycode.deryaccount.ui.theme.DaGreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * v0.12.0 — batch/lot and serial-number tracking.
 * Standalone registries: add batches (with expiry) and serials, list them
 * with status. These keep the audit trail without deep POS wiring.
 */

private fun nowIso() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())

private fun daysToExpiry(dateStr: String?): Int {
    if (dateStr.isNullOrBlank()) return Int.MAX_VALUE
    return try {
        val target = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr)
        val days = ((target!!.time - System.currentTimeMillis()) / 86_400_000L).toInt()
        days
    } catch (_: Exception) { Int.MAX_VALUE }
}

// ------------------------------------------------------------------
// Batch / lot tracking
// ------------------------------------------------------------------

@Composable
fun BatchTrackingScreen(db: AppDatabase, branchId: String) {
    val scope = rememberCoroutineScope()
    var batches by remember { mutableStateOf<List<Batch>>(emptyList()) }
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var showAdd by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(tick) {
        batches = db.batchDao().observeForBranch(branchId).first()
        products = db.productDao().observeBranchProducts(branchId).first()
    }

    if (showAdd) {
        AddBatchDialog(db, branchId, products) { showAdd = false; tick++ }
    }

    val expiringSoon = batches.count { val d = daysToExpiry(it.expiryDate); d in 0..30 }
    val expired = batches.count { daysToExpiry(it.expiryDate) < 0 }

    Scaffold(floatingActionButton = {
        ExtendedFloatingActionButton(onClick = { showAdd = true }) { Text("Add batch") }
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            Text("Batch / Lot Tracking", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("Track stock by batch and expiry — catch what's about to expire",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(Modifier.weight(1f)) {
                    Column(Modifier.padding(10.dp)) {
                        Text("EXPIRING ≤30D", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$expiringSoon", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            color = if (expiringSoon > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    }
                }
                Card(Modifier.weight(1f)) {
                    Column(Modifier.padding(10.dp)) {
                        Text("EXPIRED", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$expired", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            color = if (expired > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (batches.isEmpty()) {
                Text("No batches yet. Add batches for products with expiry dates.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    lazyListItems(batches) { b ->
                        val days = daysToExpiry(b.expiryDate)
                        val color = when {
                            days < 0 -> MaterialTheme.colorScheme.error
                            days <= 30 -> MaterialTheme.colorScheme.error
                            days <= 60 -> MaterialTheme.colorScheme.tertiary
                            else -> DaGreen
                        }
                        Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(b.productName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("Batch ${b.batchNo} · ${"%,.0f".format(b.qty)} units",
                                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(b.expiryDate?.take(10) ?: "no expiry", fontSize = 12.sp,
                                        color = color, fontWeight = FontWeight.Bold)
                                    if (b.expiryDate != null) {
                                        Text(if (days < 0) "EXPIRED ${-days}d ago"
                                             else "expires in ${days}d",
                                            fontSize = 10.sp, color = color)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddBatchDialog(db: AppDatabase, branchId: String, products: List<Product>, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<Product?>(null) }
    var showPick by remember { mutableStateOf(false) }
    var batchNo by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    if (showPick) {
        AlertDialog(onDismissRequest = { showPick = false },
            title = { Text("Choose product") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    products.forEach { p ->
                        TextButton(onClick = { picked = p; showPick = false }) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(p.name, fontWeight = FontWeight.SemiBold)
                                Text("${"%,.0f".format(p.stockQty)} ${p.unit} in stock",
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {}, dismissButton = { TextButton(onClick = { showPick = false }) { Text("Cancel") } })
    }

    AlertDialog(onDismissRequest = onDone,
        title = { Text("Add batch") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showPick = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(picked?.name ?: "Choose product…")
                }
                OutlinedTextField(batchNo, { batchNo = it }, label = { Text("Batch / lot number") }, singleLine = true)
                OutlinedTextField(expiry, { expiry = it }, label = { Text("Expiry date (2026-10-31)") }, singleLine = true)
                OutlinedTextField(qty, { qty = it }, label = { Text("Quantity") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val q = qty.replace(",", "").toDoubleOrNull()
                when {
                    picked == null -> error = "Choose the product"
                    batchNo.isBlank() -> error = "Enter the batch number"
                    q == null || q <= 0 -> error = "Enter a valid quantity"
                    else -> scope.launch {
                        val now = nowIso()
                        db.batchDao().upsert(Batch(
                            id = UUID.randomUUID().toString(), productId = picked!!.id,
                            productName = picked!!.name, batchNo = batchNo.trim(),
                            expiryDate = expiry.takeIf { it.isNotBlank() },
                            qty = q, branchId = branchId,
                            note = note.takeIf { it.isNotBlank() },
                            createdAt = now, updatedAt = now))
                        onDone()
                    }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } })
}

// ------------------------------------------------------------------
// Serial-number tracking
// ------------------------------------------------------------------

@Composable
fun SerialTrackingScreen(db: AppDatabase, branchId: String) {
    val scope = rememberCoroutineScope()
    var serials by remember { mutableStateOf<List<SerialNumber>>(emptyList()) }
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var showAdd by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("ALL") }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(tick) {
        serials = db.serialDao().observeForBranch(branchId).first()
        products = db.productDao().observeBranchProducts(branchId).first()
    }

    val shown = if (filter == "ALL") serials
                else if (filter == "IN_STOCK") serials.filter { it.status == "IN_STOCK" }
                else serials.filter { it.status == "SOLD" }

    if (showAdd) {
        AddSerialsDialog(db, branchId, products) { showAdd = false; tick++ }
    }

    Scaffold(floatingActionButton = {
        ExtendedFloatingActionButton(onClick = { showAdd = true }) { Text("Add serials") }
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            Text("Serial Numbers", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("Track phones, electronics and any item sold by its unique serial",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ALL" to "All", "IN_STOCK" to "In stock", "SOLD" to "Sold").forEach { (f, label) ->
                    FilterChip(selected = filter == f, onClick = { filter = f },
                        label = { Text(label, fontSize = 11.sp) })
                }
            }
            Spacer(Modifier.height(8.dp))
            if (shown.isEmpty()) {
                Text("No serials yet. Add serials for any tracked products.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    lazyListItems(shown) { s ->
                        Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(s.serial, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                    Text(s.productName, fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                val statusColor = if (s.status == "IN_STOCK") DaGreen
                                                  else MaterialTheme.colorScheme.onSurfaceVariant
                                Text(s.status.replace("_", " "), fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold, color = statusColor)
                                if (s.status == "IN_STOCK") {
                                    TextButton(onClick = {
                                        scope.launch {
                                            db.serialDao().upsert(s.copy(
                                                status = "SOLD", soldAt = nowIso(),
                                                updatedAt = nowIso(), syncState = "pending"))
                                            tick++
                                        }
                                    }) { Text("Mark sold") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddSerialsDialog(db: AppDatabase, branchId: String, products: List<Product>, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<Product?>(null) }
    var showPick by remember { mutableStateOf(false) }
    var bulk by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    if (showPick) {
        AlertDialog(onDismissRequest = { showPick = false },
            title = { Text("Choose product") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    products.forEach { p ->
                        TextButton(onClick = { picked = p; showPick = false }) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(p.name, fontWeight = FontWeight.SemiBold)
                                Text("${"%,.0f".format(p.stockQty)} ${p.unit} in stock",
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {}, dismissButton = { TextButton(onClick = { showPick = false }) { Text("Cancel") } })
    }

    AlertDialog(onDismissRequest = onDone,
        title = { Text("Add serials") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showPick = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(picked?.name ?: "Choose product…")
                }
                OutlinedTextField(bulk, { bulk = it },
                    label = { Text("One serial per line") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp))
                Text("Paste multiple serials — one per line, all added at once.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (picked == null) { error = "Choose the product"; return@Button }
                val serials = bulk.lines().map { it.trim() }.filter { it.isNotBlank() }
                if (serials.isEmpty()) { error = "Enter at least one serial"; return@Button }
                scope.launch {
                    val now = nowIso()
                    serials.forEach { sn ->
                        db.serialDao().upsert(SerialNumber(
                            id = UUID.randomUUID().toString(), productId = picked!!.id,
                            productName = picked!!.name, serial = sn,
                            status = "IN_STOCK", branchId = branchId,
                            createdAt = now, updatedAt = now))
                    }
                    onDone()
                }
            }) { Text("Save ${if (bulk.isNotBlank()) bulk.lines().count { it.isNotBlank() } else ""}") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } })
}
