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
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.data.local.AppDatabase
import com.derycode.deryaccount.data.local.entity.Product
import kotlinx.coroutines.launch

/**
 * InventoryScreen — live product list from local DB. Shows low stock in red.
 * Add-product dialog creates products offline (syncState=pending).
 */
@Composable
fun InventoryScreen(db: AppDatabase, branchId: String) {
    val products by db.productDao().observeBranchProducts(branchId)
        .collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${filtered.size} products", fontWeight = FontWeight.Bold)
                val low = products.count { it.stockQty <= it.lowStockAlert }
                if (low > 0) Text("$low low on stock", color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))

            LazyColumn {
                items(filtered, key = { it.id }) { p ->
                    ProductRow(p, onAdjust = { delta ->
                        val now = nowIso()
                        scope.launch {
                            db.productDao().adjustStock(p.id, delta, now)
                            db.stockMovementDao().upsert(
                                com.derycode.deryaccount.data.local.entity.StockMovement(
                                    id = java.util.UUID.randomUUID().toString(),
                                    productId = p.id, branchId = branchId, type = "ADJUSTMENT",
                                    qty = delta, reference = null, note = "manual adjust", movedAt = now,
                                    createdAt = now, updatedAt = now
                                ))
                        }
                    })
                }
            }
        }
    }

    if (showAdd) AddProductDialog(db, branchId, onDone = { showAdd = false })
}

@Composable
private fun ProductRow(p: Product, onAdjust: (Double) -> Unit) {
    val low = p.stockQty <= p.lowStockAlert
    ListItem(
        headlineContent = { Text(p.name, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text("${p.category} · ${p.barcode ?: "no barcode"}") },
        leadingContent = { Icon(Icons.Default.Inventory2, null) },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text("UGX %,d".format(p.retailPrice.toLong()), fontWeight = FontWeight.Bold)
                Text("${if (p.stockQty % 1.0 == 0.0) p.stockQty.toLong() else p.stockQty} ${p.unit}",
                    color = if (low) MaterialTheme.colorScheme.error
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (low) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 13.sp)
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun AddProductDialog(db: AppDatabase, branchId: String, onDone: () -> Unit) {
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
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } }
    )
}

private fun nowIso() = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
    .format(java.util.Date())
