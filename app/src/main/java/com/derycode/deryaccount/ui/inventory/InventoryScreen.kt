package com.derycode.deryaccount.ui.inventory

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
import com.derycode.deryaccount.data.local.AppDatabase
import com.derycode.deryaccount.util.PdfExport
import com.derycode.deryaccount.data.local.entity.Product
import kotlinx.coroutines.launch

/**
 * InventoryScreen — live product list from local DB. Shows low stock in red.
 * Add-product dialog creates products offline (syncState=pending).
 */
@Composable
fun InventoryScreen(db: AppDatabase, branchId: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val products by db.productDao().observeBranchProducts(branchId)
        .collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var editProduct by remember { mutableStateOf<Product?>(null) }
    var deleteProduct by remember { mutableStateOf<Product?>(null) }
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

    val filtered = if (search.isBlank()) products else products.filter {
        it.name.contains(search, ignoreCase = true) || (it.barcode?.contains(search) == true)
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
                }
                OutlinedButton(onClick = {
                    val file = PdfExport.stockPdf(context, "My Shop",
                        filtered.map { arrayOf(it.name, it.unit ?: "pcs",
                            it.retailPrice.toString(), it.stockQty.toString()) })
                    try { PdfExport.printPdf(context, file, "Stock Report") } catch (_: Exception) {}
                }) {
                    Icon(Icons.Default.Print, null); Text(" Print Stock")
                }
            }
            Spacer(Modifier.height(4.dp))

            LazyColumn {
                items(filtered, key = { it.id }) { p ->
                    ProductRow(p,
                        onAdjust = { delta ->
                            val now = nowIso()
                            scope.launch {
                                try {
                                db.productDao().adjustStock(p.id, delta, now)
                                db.stockMovementDao().upsert(
                                    com.derycode.deryaccount.data.local.entity.StockMovement(
                                        id = java.util.UUID.randomUUID().toString(),
                                        productId = p.id, branchId = branchId, type = "ADJUSTMENT",
                                        qty = delta, reference = null, note = "manual adjust", movedAt = now,
                                        createdAt = now, updatedAt = now
                                    ))
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
                            val acc = com.derycode.deryaccount.accounting.AccountingRepo(db).apply {
                                ensureSeeded()
                                postStockWriteOff(p.stockQty * p.costPrice, p.name)
                            }
                        } catch (_: Exception) {}
                        try {
                            db.productDao().delete(p.id)
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
}

/** Edit + delete any product — full control over the information. */
@Composable
private fun ProductRow(p: Product, onAdjust: (Double) -> Unit,
                      onEdit: () -> Unit, onDelete: () -> Unit) {
    val low = p.stockQty <= p.lowStockAlert
    ListItem(
        headlineContent = { Text(p.name, fontWeight = FontWeight.SemiBold) },
        supportingContent = {
            Text("${p.category} · ${p.barcode ?: "no barcode"} · cost %,d".format(p.costPrice.toLong()),
                fontSize = 12.sp)
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
        modifier = Modifier.fillMaxWidth()
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
                    OutlinedTextField(alert, { alert = it }, label = { Text("Low stock alert") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                scope.launch {
                  try {
                    val now = nowIso()
                    val delta = q - p.stockQty
                    val deltaValue = delta * cs
                    db.productDao().upsert(p.copy(
                        name = name.ifBlank { p.name },
                        retailPrice = pr, costPrice = cs, stockQty = q,
                        lowStockAlert = al, updatedAt = now))
                    if (delta != 0.0) {
                        db.stockMovementDao().upsert(
                            com.derycode.deryaccount.data.local.entity.StockMovement(
                                id = java.util.UUID.randomUUID().toString(),
                                productId = p.id, branchId = p.branchId, type = "ADJUSTMENT",
                                qty = delta, reference = null, note = "edit item", movedAt = now,
                                createdAt = now, updatedAt = now))
                        try {
                            com.derycode.deryaccount.accounting.AccountingRepo(db).apply {
                                ensureSeeded()
                                postStockChange(deltaValue, p.name)
                            }
                        } catch (_: Exception) {}
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
                    if ((stock.toDoubleOrNull() ?: 0.0) > 0) {
                        db.stockMovementDao().upsert(
                            com.derycode.deryaccount.data.local.entity.StockMovement(
                                id = java.util.UUID.randomUUID().toString(), productId = id,
                                branchId = branchId, type = "PURCHASE",
                                qty = stock.toDoubleOrNull() ?: 0.0,
                                reference = null, note = "opening stock", movedAt = now,
                                createdAt = now, updatedAt = now
                            ))
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

private fun nowIso() = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
    .format(java.util.Date())
