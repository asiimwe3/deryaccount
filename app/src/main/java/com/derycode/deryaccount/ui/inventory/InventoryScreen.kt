package com.derycode.deryaccount.ui.inventory

/**
 * InventoryScreen — the shop's stock list.
 * Shows every product with price and quantity left; supports add,
 * edit, delete and stock adjustments. All writes are crash-protected
 * via DbSafety (storage checks + error logging to DeryAccount/crashes).
 */

import androidx.compose.foundation.clickable
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import androidx.room.withTransaction
import com.derycode.deryaccount.data.local.AppDatabase
import com.derycode.deryaccount.util.PdfExport
import com.derycode.deryaccount.data.local.entity.Product
import kotlinx.coroutines.launch

/**
 * InventoryScreen — live product list from local DB. Shows low stock in red.
 * Add-product dialog creates products offline (syncState=pending).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(db: AppDatabase, branchId: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val products by db.productDao().observeBranchProducts(branchId)
        .collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var editProduct by remember { mutableStateOf<Product?>(null) }
    var deleteProduct by remember { mutableStateOf<Product?>(null) }
    var detailProduct by remember { mutableStateOf<Product?>(null) }
    var showCount by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("ALL") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    if (error != null) {
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("Not saved", fontWeight = FontWeight.Bold) },
            text = { Text(error ?: "") },
            confirmButton = { Button(onClick = { error = null }) { Text("OK") } }
        )
    }

    val searched = if (search.isBlank()) products else products.filter {
        it.name.contains(search, ignoreCase = true) || (it.barcode?.contains(search) == true)
    }
    val in30 = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        .format(java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_MONTH, 30) }.time)
    val filtered = when (filter) {
        "LOW" -> searched.filter { it.stockQty <= it.lowStockAlert && it.stockQty > 0 }
        "OUT" -> searched.filter { it.stockQty <= 0 }
        "EXPIRING" -> searched.filter { it.expiryDate != null && it.expiryDate!! <= in30 }
        else -> searched
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add Product")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 12.dp)) {
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search products…") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) }
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "ALL" to ("All (${searched.size})"),
                    "LOW" to ("Low (${searched.count { it.stockQty <= it.lowStockAlert && it.stockQty > 0 }})"),
                    "OUT" to ("Out (${searched.count { it.stockQty <= 0 }})"),
                    "EXPIRING" to ("Expiring (${searched.count { it.expiryDate != null && it.expiryDate!! <= in30 }})")
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = filter == key,
                        onClick = { filter = key },
                        label = { Text(label, fontSize = 11.sp, maxLines = 1) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("${filtered.size} products", fontWeight = FontWeight.Bold)
                    val low = products.count { it.stockQty <= it.lowStockAlert }
                    if (low > 0) Text("$low low on stock", color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold)
                    val stockValue = products.sumOf { it.stockQty * it.costPrice }
                    Text("Closing stock value (cost): UGX %,d".format(stockValue.toLong()),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold)
                    Text("at retail: UGX %,d".format(products.sumOf { it.stockQty * it.retailPrice }.toLong()),
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                OutlinedButton(onClick = {
                    val file = PdfExport.stockPdf(context, "My Shop",
                        filtered.map { arrayOf(it.name, it.unit ?: "pcs",
                            it.retailPrice.toString(), it.stockQty.toString()) })
                    try { PdfExport.printPdf(context, file, "Stock Report") } catch (_: Exception) {}
                }) {
                    Icon(Icons.Default.Print, null); Text(" Print")
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = { showCount = true }) {
                    Icon(Icons.Default.FactCheck, null); Text(" Stock Count")
                }
                }
            }
            Spacer(Modifier.height(4.dp))

            LazyColumn {
                items(filtered, key = { it.id }) { p ->
                    ProductRow(p,
                        onOpen = { detailProduct = p },
                        onAdjust = { delta ->
                            val now = nowIso()
                            scope.launch {
                                try {
                                    db.withTransaction {
                                        db.productDao().adjustStock(p.id, delta, now)
                                        db.stockMovementDao().upsert(
                                            com.derycode.deryaccount.data.local.entity.StockMovement(
                                                id = java.util.UUID.randomUUID().toString(),
                                                productId = p.id, branchId = branchId, type = "ADJUSTMENT",
                                                qty = delta, reference = null, note = "manual adjust", movedAt = now,
                                                createdAt = now, updatedAt = now
                                            ))
                                        // Books stay in step with the stock list — value at COST
                                        val accounting = com.derycode.deryaccount.accounting.AccountingRepo(db)
                                        accounting.ensureSeeded()
                                        accounting.postStockEdit(p.stockQty, p.costPrice,
                                            p.stockQty + delta, p.costPrice, p.name)
                                    }
                                } catch (e: Exception) {
                                    com.derycode.deryaccount.util.DbSafety.log(context, "Stock adjust", e)
                                    error = com.derycode.deryaccount.util.DbSafety.friendly(e)
                                }
                            }
                        },
                        onEdit = { editProduct = p },
                        onDelete = { deleteProduct = p })
                }
            }
        }
    }

    var manualAdd by remember { mutableStateOf(false) }
    editProduct?.let { p ->
        EditProductDialog(db, p, onDone = { editProduct = null })
    }
    deleteProduct?.let { p ->
        AlertDialog(
            onDismissRequest = { deleteProduct = null },
            title = { Text("Delete ${p.name}?") },
            text = { Text("This removes the item from your stock list. Sales history stays. " +
                    "Remaining stock value (${"%,.0f".format(p.stockQty * p.costPrice)} UGX) is written off in the books.") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        try {
                            // Atomic: the write-off and the deletion succeed or
                            // fail together — books and stock list can't drift.
                            db.withTransaction {
                                val acc = com.derycode.deryaccount.accounting.AccountingRepo(db)
                                acc.ensureSeeded()
                                acc.postStockWriteOff(p.stockQty * p.costPrice, p.name)
                                db.productDao().delete(p.id)
                            }
                        } catch (e: Exception) {
                            com.derycode.deryaccount.util.DbSafety.log(context, "Delete product", e)
                            error = com.derycode.deryaccount.util.DbSafety.friendly(e)
                            return@launch
                        }
                        deleteProduct = null
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteProduct = null }) { Text("Cancel") } }
        )
    }
    if (showAdd) QuickAddStockDialog(
        db, branchId,
        onDone = { showAdd = false },
        onManualAdd = { showAdd = false; manualAdd = true }
    )
    if (manualAdd) AddProductDialog(db, branchId, onDone = { manualAdd = false })
    detailProduct?.let { p ->
        // re-read on each open so stock levels shown are fresh
        val fresh by produceState<Product?>(initialValue = p, p.id) {
            value = try { db.productDao().get(p.id) ?: p } catch (_: Exception) { p }
        }
        fresh?.let { ProductDetailSheet(db, branchId, it,
            onDismiss = { detailProduct = null },
            onEdit = { editProduct = it; detailProduct = null }) }
    }
    if (showCount) BulkCountDialog(db, branchId, products,
        onDone = { showCount = false },
        onError = { error = it; showCount = false })
}

/** Physical stock count over the whole list — enter counted quantities, apply the variances. */
@Composable
private fun BulkCountDialog(db: AppDatabase, branchId: String, products: List<Product>,
                            onDone: () -> Unit, onError: (String) -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val counted = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }
    var busy by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDone,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(16.dp)) {
                Text("Stock Count", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text("Enter the physically counted quantity for each product. " +
                    "Blank or unchanged rows are skipped.", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                LazyColumn(Modifier.weight(1f)) {
                    items(products, key = { it.id }) { p ->
                        val v = counted[p.id] ?: ""
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text("book: ${if (p.stockQty % 1.0 == 0.0) p.stockQty.toLong() else p.stockQty} ${p.unit}",
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedTextField(
                                value = v,
                                onValueChange = { counted[p.id] = it },
                                label = { Text("counted") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.width(120.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDone, enabled = !busy) { Text("Cancel") }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = {
                            val ctx = androidx.compose.ui.platform.LocalContext.current
                            scope.launch {
                                busy = true
                                try {
                                    products.forEach { p ->
                                        val c = counted[p.id]?.replace(",", "")?.toDoubleOrNull()
                                        if (c != null && c != p.stockQty) {
                                            StockOps.applyCount(db, branchId, p.id, c, "bulk stock count")
                                        }
                                    }
                                    onDone()
                                } catch (e: Exception) {
                                    com.derycode.deryaccount.util.DbSafety.log(ctx, "Stock count", e)
                                    onError(com.derycode.deryaccount.util.DbSafety.friendly(e))
                                }
                            }
                        },
                        enabled = !busy
                    ) { Text(if (busy) "Applying…" else "Apply count") }
                }
            }
        }
    }
}

/** Edit + delete any product — full control over the information. */
@Composable
private fun ProductRow(p: Product, onOpen: () -> Unit, onAdjust: (Double) -> Unit,
                      onEdit: () -> Unit, onDelete: () -> Unit) {
    val low = p.stockQty <= p.lowStockAlert
    ListItem(
        headlineContent = { Text(p.name, fontWeight = FontWeight.SemiBold) },
        supportingContent = {
            Column {
                Text("${p.category} · ${p.barcode ?: "no barcode"} · cost %,d".format(p.costPrice.toLong()),
                    fontSize = 12.sp)
                if (p.expiryDate != null) {
                    val expired = p.expiryDate!! < nowYmd()
                    Text("expiry ${p.expiryDate}" + if (expired) " — EXPIRED" else "",
                        fontSize = 11.sp,
                        color = if (expired) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        leadingContent = { Icon(Icons.Default.Inventory2, null) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("UGX %,d".format(p.retailPrice.toLong()), fontWeight = FontWeight.Bold)
                    Text("${if (p.stockQty % 1.0 == 0.0) p.stockQty.toLong() else p.stockQty} ${p.unit}",
                        color = if (low) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (low) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp)
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit",
                    modifier = Modifier.size(18.dp)) }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp))
                }
            }
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)
    )
}

/** Edit a product: name, prices, stock, low-stock alert — books stay in step. */
@Composable
private fun EditProductDialog(db: AppDatabase, p: Product, onDone: () -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var saveError by remember { mutableStateOf<String?>(null) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    if (saveError != null) {
        AlertDialog(
            onDismissRequest = { saveError = null },
            title = { Text("Not saved", fontWeight = FontWeight.Bold) },
            text = { Text(saveError ?: "") },
            confirmButton = { Button(onClick = { saveError = null }) { Text("OK") } }
        )
    }
    var name by remember { mutableStateOf(p.name) }
    var price by remember { mutableStateOf(p.retailPrice.toLong().toString()) }
    var cost by remember { mutableStateOf(p.costPrice.toLong().toString()) }
    var stock by remember { mutableStateOf(
        if (p.stockQty % 1.0 == 0.0) p.stockQty.toLong().toString() else p.stockQty.toString()) }
    var alert by remember { mutableStateOf(p.lowStockAlert.toLong().toString()) }
    var reorder by remember { mutableStateOf(if (p.reorderLevel > 0) p.reorderLevel.toLong().toString() else "") }
    var expiry by remember { mutableStateOf(p.expiryDate ?: "") }
    val context = androidx.compose.ui.platform.LocalContext.current

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Edit item", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(price, { price = it }, label = { Text("Sell UGX") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(cost, { cost = it }, label = { Text("Cost UGX") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(stock, { stock = it }, label = { Text("Stock qty") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(alert, { alert = it }, label = { Text("Min level") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(reorder, { reorder = it }, label = { Text("Reorder level (0=off)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(expiry, { expiry = it }, label = { Text("Expiry date (YYYY-MM-DD)") },
                        singleLine = true, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val pr = price.toDoubleOrNull() ?: return@Button
                val cs = cost.toDoubleOrNull() ?: pr
                val q = stock.toDoubleOrNull() ?: return@Button
                val al = alert.toDoubleOrNull() ?: 5.0
                val rl = reorder.replace(",", "").trim().toDoubleOrNull() ?: 0.0
                val exp = expiry.trim().takeIf { it.isNotBlank() && it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
                scope.launch {
                  try {
                    val now = nowIso()
                    val delta = q - p.stockQty
                    db.withTransaction {
                        db.productDao().upsert(p.copy(
                            name = name.ifBlank { p.name },
                            retailPrice = pr, costPrice = cs, stockQty = q,
                            lowStockAlert = al, reorderLevel = rl, expiryDate = exp,
                            updatedAt = now))
                        if (delta != 0.0) {
                            db.stockMovementDao().upsert(
                                com.derycode.deryaccount.data.local.entity.StockMovement(
                                    id = java.util.UUID.randomUUID().toString(),
                                    productId = p.id, branchId = p.branchId, type = "ADJUSTMENT",
                                    qty = delta, reference = null, note = "edit item", movedAt = now,
                                    createdAt = now, updatedAt = now))
                        }
                        // Books follow the FULL value change (qty AND cost price),
                        // so the Stock account always equals the stock list.
                        val accounting = com.derycode.deryaccount.accounting.AccountingRepo(db)
                        accounting.ensureSeeded()
                        accounting.postStockEdit(p.stockQty, p.costPrice, q, cs, p.name)
                    }
                    onDone()
                  } catch (e: Exception) {
                    com.derycode.deryaccount.util.DbSafety.log(ctx, "Edit product", e)
                    saveError = com.derycode.deryaccount.util.DbSafety.friendly(e)
                  }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } }
    )
}

@Composable
private fun AddProductDialog(db: AppDatabase, branchId: String, onDone: () -> Unit) {
    var saveError by remember { mutableStateOf<String?>(null) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    if (saveError != null) {
        AlertDialog(
            onDismissRequest = { saveError = null },
            title = { Text("Not saved", fontWeight = FontWeight.Bold) },
            text = { Text(saveError ?: "") },
            confirmButton = { Button(onClick = { saveError = null }) { Text("OK") } }
        )
    }
    var name by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("") }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("New Product") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(barcode, { barcode = it }, label = { Text("Barcode (optional)") }, singleLine = true)
                OutlinedTextField(cost, { cost = it }, label = { Text("Cost price (UGX)") }, singleLine = true)
                OutlinedTextField(price, { price = it }, label = { Text("Selling price (UGX)") }, singleLine = true)
                OutlinedTextField(stock, { stock = it }, label = { Text("Opening stock") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank() || price.isBlank()) return@Button
                val now = nowIso()
                scope.launch {
                    try {
                    val id = java.util.UUID.randomUUID().toString()
                    // Atomic: product, movement AND the purchase entry in the books
                    // (Dr Stock, Cr Cash at cost x qty) save together or not at all.
                    db.withTransaction {
                        db.productDao().upsert(Product(
                            id = id, name = name, barcode = barcode.ifBlank { null },
                            category = "General", unit = "pcs",
                            costPrice = cost.toDoubleOrNull() ?: 0.0,
                            retailPrice = price.toDoubleOrNull() ?: 0.0,
                            wholesalePrice = null, taxRate = 0.0,
                            stockQty = stock.toDoubleOrNull() ?: 0.0,
                            lowStockAlert = 5.0, expiryDate = null, branchId = branchId,
                            createdAt = now, updatedAt = now
                        ))
                        val openingQty = stock.toDoubleOrNull() ?: 0.0
                        val openingCost = cost.toDoubleOrNull() ?: 0.0
                        if (openingQty > 0) {
                            db.stockMovementDao().upsert(
                                com.derycode.deryaccount.data.local.entity.StockMovement(
                                    id = java.util.UUID.randomUUID().toString(), productId = id,
                                    branchId = branchId, type = "PURCHASE",
                                    qty = openingQty,
                                    reference = null, note = "opening stock", movedAt = now,
                                    createdAt = now, updatedAt = now
                                ))
                            // Books: Dr Stock, Cr Cash at COST x qty
                            val accounting = com.derycode.deryaccount.accounting.AccountingRepo(db)
                            accounting.ensureSeeded()
                            accounting.postPurchase(openingQty * openingCost, "CASH",
                                "opening stock — $name")
                        }
                    }
                    onDone()
                    } catch (e: Exception) {
                        com.derycode.deryaccount.util.DbSafety.log(ctx, "Add product", e)
                        saveError = com.derycode.deryaccount.util.DbSafety.friendly(e)
                    }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } }
    )
}

/** Today as yyyy-MM-dd — API-24 safe (no java.time without desugaring). */
private fun nowYmd(): String = java.text.SimpleDateFormat(
    "yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

private fun nowIso() = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
    .format(java.util.Date())
