package com.derycode.deryaccount.ui.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.catalog.BusinessCatalog
import com.derycode.deryaccount.data.local.AppDatabase
import com.derycode.deryaccount.data.local.entity.Product
import com.derycode.deryaccount.data.local.entity.StockMovement
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * QuickAddStock — simple stock setup:
 *   1. Choose your business category
 *   2. Tick the items you sell, set price & opening stock
 *   3. One tap adds everything — opening stock recorded as a purchase.
 * Owners just pick from the ready list; typing is optional.
 */
@Composable
fun QuickAddStockDialog(
    db: AppDatabase,
    branchId: String,
    onDone: () -> Unit,
    onManualAdd: () -> Unit   // "type my own item" fallback
) {
    val scope = rememberCoroutineScope()
    var category by remember { mutableStateOf<String?>(null) }

    if (category == null) {
        CategoryPicker(
            onPick = { category = it },
            onSkip = onManualAdd
        )
    } else {
        ItemPicker(
            category = category!!,
            onAdd = { picked, customName, customPrice, customQty ->
                scope.launch {
                    db.createProducts(picked, category!!, branchId)
                    if (customName.isNotBlank() && customPrice > 0) {
                        db.createProducts(listOf(
                            Picked(BusinessCatalog.CatalogItem(customName, "pcs", customPrice), customQty)
                        ), category!!, branchId)
                    }
                    onDone()
                }
            },
            onBack = { category = null }
        )
    }
}

/** A catalog item the owner ticked, with their price & opening stock. */
data class Picked(val item: BusinessCatalog.CatalogItem, val qty: Double)

private suspend fun AppDatabase.createProducts(
    picked: List<Picked>, category: String, branchId: String
) {
    val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())
    picked.forEach { p ->
        val id = UUID.randomUUID().toString()
        productDao().upsert(Product(
            id = id, name = p.item.name, barcode = null, category = category,
            unit = p.item.unit, costPrice = 0.0, retailPrice = p.item.price,
            wholesalePrice = null, stockQty = p.qty, lowStockAlert = 5.0,
            expiryDate = null, branchId = branchId, createdAt = now, updatedAt = now
        ))
        if (p.qty > 0) {
            stockMovementDao().upsert(StockMovement(
                id = UUID.randomUUID().toString(), productId = id,
                branchId = branchId, type = "PURCHASE", qty = p.qty,
                reference = null, note = "opening stock", movedAt = now,
                createdAt = now, updatedAt = now))
        }
    }
}

// ----------------------------------------------------------------
// STEP 1 — business category
// ----------------------------------------------------------------
@Composable
private fun CategoryPicker(onPick: (String) -> Unit, onSkip: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("What kind of business?", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Pick your business type to see a ready list of items you can stock.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.height(400.dp)) {
                    items(BusinessCatalog.CATEGORIES) { c ->
                        Surface(
                            onClick = { onPick(c) },
                            shape = MaterialTheme.shapes.medium,
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Store, null,
                                    tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                                Text(c, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                                    modifier = Modifier.weight(1f))
                                Icon(Icons.Default.Add, null,
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onSkip) { Text("Type my own item instead") } }
    )
}

// ----------------------------------------------------------------
// STEP 2 — tick items, set price & qty
// ----------------------------------------------------------------
@Composable
private fun ItemPicker(
    category: String,
    onAdd: (List<Picked>, String, Double, Double) -> Unit,
    onBack: () -> Unit
) {
    val catalog = remember(category) { BusinessCatalog.itemsFor(category) }
    var selected by remember { mutableStateOf(setOf<Int>()) }
    var prices by remember { mutableStateOf(mutableMapOf<Int, String>()) }
    var qtys by remember { mutableStateOf(mutableMapOf<Int, String>()) }
    var customName by remember { mutableStateOf("") }
    var customPrice by remember { mutableStateOf("") }
    var customQty by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onBack,
        title = { Text(category, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxHeight(0.85f)) {
                Text("Tick what you sell. Opening stock is recorded as a purchase.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                LazyColumn(Modifier.weight(1f)) {
                    items(catalog.size) { i ->
                        val ci = catalog[i]
                        val isSel = i in selected
                        Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = isSel, onCheckedChange = { on ->
                                    selected = if (on) selected + i else selected - i
                                })
                                Text(ci.name, fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f))
                                Text(ci.unit, fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(
                                Modifier.padding(start = 46.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedTextField(
                                    value = prices[i] ?: ci.price.toLong().toString(),
                                    onValueChange = {
                                        prices[i] = it
                                        if (it.toDoubleOrNull() != null) selected = selected + i
                                    },
                                    label = { Text("Price UGX", fontSize = 11.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.width(120.dp).height(52.dp)
                                )
                                OutlinedTextField(
                                    value = qtys[i] ?: "",
                                    onValueChange = {
                                        qtys[i] = it
                                        if (it.toDoubleOrNull() != null) selected = selected + i
                                    },
                                    label = { Text("Opening stock", fontSize = 11.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.width(120.dp).height(52.dp)
                                )
                            }
                        }
                    }
                    // custom item at the bottom
                    item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Add, null,
                                    tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                OutlinedTextField(customName, { customName = it },
                                    label = { Text("Other item not in list") },
                                    singleLine = true, modifier = Modifier.weight(1f))
                            }
                            if (customName.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedTextField(customPrice, { customPrice = it },
                                        label = { Text("Price UGX") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true, modifier = Modifier.width(120.dp))
                                    OutlinedTextField(customQty, { customQty = it },
                                        label = { Text("Opening stock") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true, modifier = Modifier.width(120.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val chosen = selected.sorted().mapNotNull { i ->
                    val ci = catalog[i]
                    val pr = (prices[i] ?: ci.price.toString()).toDoubleOrNull()
                        ?: return@mapNotNull null
                    val q = (qtys[i] ?: "0").toDoubleOrNull() ?: 0.0
                    Picked(BusinessCatalog.CatalogItem(ci.name, ci.unit, pr), q)
                }
                onAdd(chosen, customName,
                    customPrice.toDoubleOrNull() ?: 0.0,
                    customQty.toDoubleOrNull() ?: 0.0)
            }) {
                Icon(Icons.Default.Check, null)
                Text("  Add ${selected.size} items")
            }
        },
        dismissButton = { TextButton(onClick = onBack) { Text("Back") } }
    )
}
