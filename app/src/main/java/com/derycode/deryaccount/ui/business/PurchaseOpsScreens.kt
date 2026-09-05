@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.derycode.deryaccount.ui.business

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyListItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.data.local.AppDatabase
import com.derycode.deryaccount.data.local.entity.Product
import com.derycode.deryaccount.data.local.entity.PurchaseOrder
import com.derycode.deryaccount.data.local.entity.PurchaseOrderItem
import com.derycode.deryaccount.data.local.entity.PurchaseReturn
import com.derycode.deryaccount.data.local.entity.Supplier
import com.derycode.deryaccount.ui.theme.DaGreen
import com.derycode.deryaccount.util.MoneyOps
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Purchase operations — returns to suppliers and purchase orders.
 * A purchase order is a promise (no stock, no books); receiving it turns
 * every line into audited stock-in via StockOps. A return moves stock out
 * at cost and posts proper double entries.
 */

private fun ugx(v: Double) = "UGX %,d".format(v.toLong())

private fun prettyDate(iso: String): String = try {
    val parsed = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).parse(iso)
    java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.US).format(parsed!!)
} catch (_: Exception) { iso.take(10) }

/** One line being built in a return / PO dialog. */
private data class OpLine(val product: Product, val qty: Double, val unitCost: Double)

@Composable
private fun LineBuilder(
    products: List<Product>,
    lines: List<OpLine>,
    onAdd: (OpLine) -> Unit,
    onRemove: (Int) -> Unit
) {
    var picked by remember { mutableStateOf<Product?>(null) }
    var showPick by remember { mutableStateOf(false) }
    var qty by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }

    if (showPick) {
        AlertDialog(onDismissRequest = { showPick = false },
            title = { Text("Choose product") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    products.forEach { p ->
                        TextButton(onClick = {
                            picked = p
                            cost = if (p.costPrice > 0) p.costPrice.toString() else ""
                            showPick = false
                        }) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(p.name, fontWeight = FontWeight.SemiBold)
                                Text("in stock: ${"%,.0f".format(p.stockQty)} ${p.unit} · cost ${ugx(p.costPrice)}",
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {}, dismissButton = { TextButton(onClick = { showPick = false }) { Text("Cancel") } })
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = { showPick = true }, modifier = Modifier.fillMaxWidth()) {
            Text(picked?.name ?: "Add product…", maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(qty, { qty = it }, label = { Text("Qty") },
                modifier = Modifier.weight(1f), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(cost, { cost = it }, label = { Text("Unit cost") },
                modifier = Modifier.weight(1f), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
        Button(onClick = {
            val q = qty.replace(",", "").toDoubleOrNull()
            val c = cost.replace(",", "").toDoubleOrNull()
            val p = picked
            if (p != null && q != null && q > 0 && c != null && c >= 0) {
                onAdd(OpLine(p, q, c))
                picked = null; qty = ""; cost = ""
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Add line") }
        lines.forEachIndexed { i, l ->
            Surface(shape = MaterialTheme.shapes.small, tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(l.product.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("${"%,.0f".format(l.qty)} × ${ugx(l.unitCost)} = ${ugx(l.qty * l.unitCost)}",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { onRemove(i) }) { Text("Remove") }
                }
            }
        }
    }
}

// ------------------------------------------------------------------
// Purchase returns (goods back to the supplier)
// ------------------------------------------------------------------

@Composable
fun PurchaseReturnsScreen(db: AppDatabase, branchId: String, userId: String) {
    var returns by remember { mutableStateOf<List<PurchaseReturn>>(emptyList()) }
    var suppliers by remember { mutableStateOf<List<Supplier>>(emptyList()) }
    var showAdd by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(tick) {
        returns = db.purchaseReturnDao().observeRecent().first()
        suppliers = db.supplierDao().observeAll().first()
    }

    if (showAdd) {
        NewPurchaseReturnDialog(db, branchId, userId, suppliers) { showAdd = false; tick++ }
    }

    Scaffold(floatingActionButton = {
        ExtendedFloatingActionButton(onClick = { showAdd = true }) { Text("New return") }
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            Text("Purchase Returns", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("Send faulty or over-supplied stock back — cash back or off the supplier account",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (returns.isEmpty()) {
                Text("No returns yet.", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    lazyListItems(returns) { r ->
                        val s = suppliers.find { it.id == r.supplierId }
                        Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp)) {
                                Row(Modifier.fillMaxWidth()) {
                                    Column(Modifier.weight(1f)) {
                                        Text(r.prNo, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text((s?.name ?: "No supplier") + " · " +
                                            if (r.refundMethod == "CASH") "cash back"
                                            else "off account",
                                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(ugx(r.total), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                                Text(prettyDate(r.returnedAt), fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewPurchaseReturnDialog(
    db: AppDatabase, branchId: String, userId: String,
    suppliers: List<Supplier>, onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var lines by remember { mutableStateOf<List<OpLine>>(emptyList()) }
    var supplier by remember { mutableStateOf<Supplier?>(null) }
    var showPickSupplier by remember { mutableStateOf(false) }
    var refundMethod by remember { mutableStateOf("SUPPLIER_CREDIT") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }

    LaunchedEffect(Unit) {
        products = db.productDao().observeBranchProducts(branchId).first().filter { it.stockQty > 0 }
    }

    if (showPickSupplier) {
        AlertDialog(onDismissRequest = { showPickSupplier = false },
            title = { Text("Supplier (optional)") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TextButton(onClick = { supplier = null; showPickSupplier = false }) { Text("No supplier") }
                    suppliers.forEach { s ->
                        TextButton(onClick = { supplier = s; showPickSupplier = false }) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(s.name, fontWeight = FontWeight.SemiBold)
                                Text("we owe ${ugx(s.balance)}", fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            },
            confirmButton = {}, dismissButton = { TextButton(onClick = { showPickSupplier = false }) { Text("Cancel") } })
    }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Return to supplier") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showPickSupplier = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(supplier?.name ?: "Supplier (optional)…")
                }
                LineBuilder(products, lines, { lines = lines + it }, { lines = lines.filterIndexed { i, _ -> i != it } })
                Text("How do you get the money back?", fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = refundMethod == "SUPPLIER_CREDIT",
                        onClick = { refundMethod = "SUPPLIER_CREDIT" },
                        label = { Text("Off our account", fontSize = 11.sp) })
                    FilterChip(selected = refundMethod == "CASH",
                        onClick = { refundMethod = "CASH" },
                        label = { Text("Cash back", fontSize = 11.sp) })
                }
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, singleLine = true)
                Text("Total: ${ugx(lines.sumOf { it.qty * it.unitCost })}",
                    fontWeight = FontWeight.Bold)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (lines.isEmpty()) { error = "Add at least one product"; return@Button }
                if (lines.any { it.qty > it.product.stockQty + 0.001 }) { error = "One line returns more than is in stock"; return@Button }
                if (refundMethod == "SUPPLIER_CREDIT" && supplier == null) { error = "Off-account refunds need a supplier"; return@Button }
                scope.launch {
                    try {
                        MoneyOps.purchaseReturn(db, branchId, userId, supplier,
                            lines.map { Triple(it.product, it.qty, it.unitCost) },
                            refundMethod, note)
                    } catch (_: Exception) { }
                    onDone()
                }
            }) { Text("Return stock") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } }
    )
}

// ------------------------------------------------------------------
// Purchase orders
// ------------------------------------------------------------------

@Composable
fun PurchaseOrdersScreen(db: AppDatabase, branchId: String, userId: String) {
    val scope = rememberCoroutineScope()
    var orders by remember { mutableStateOf<List<PurchaseOrder>>(emptyList()) }
    var suppliers by remember { mutableStateOf<List<Supplier>>(emptyList()) }
    var showAdd by remember { mutableStateOf(false) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var itemsCache by remember { mutableStateOf<Map<String, List<PurchaseOrderItem>>>(emptyMap()) }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(tick) {
        orders = db.purchaseOrderDao().observeRecent().first()
        suppliers = db.supplierDao().observeAll().first()
    }

    if (showAdd) {
        NewPurchaseOrderDialog(db, branchId, userId, suppliers) { showAdd = false; tick++ }
    }

    Scaffold(floatingActionButton = {
        ExtendedFloatingActionButton(onClick = { showAdd = true }) { Text("New order") }
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            Text("Purchase Orders", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("Order stock from suppliers — receive it when it arrives and it becomes audited stock",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (orders.isEmpty()) {
                Text("No purchase orders yet. Tap \"New order\" to raise one.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    lazyListItems(orders) { o ->
                        val s = suppliers.find { it.id == o.supplierId }
                        val items = itemsCache[o.id] ?: emptyList()
                        val total = items.sumOf { it.lineTotal }
                        Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(o.poNo, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text((s?.name ?: "No supplier") + " · " + prettyDate(o.orderedAt),
                                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    val color = when (o.status) {
                                        "OPEN" -> DaGreen
                                        "RECEIVED" -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.error
                                    }
                                    Text(o.status, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
                                }
                                TextButton(onClick = {
                                    if (expandedId == o.id) { expandedId = null }
                                    else {
                                        expandedId = o.id
                                        scope.launch {
                                            itemsCache = itemsCache +
                                                (o.id to db.purchaseOrderItemDao().forOrder(o.id))
                                        }
                                    }
                                }) { Text(if (expandedId == o.id) "Hide lines" else "View lines") }
                                if (expandedId == o.id) {
                                    items.forEach { it ->
                                        Text("• ${it.name} — ${"%,.0f".format(it.qty)} × ${ugx(it.unitCost)}",
                                            fontSize = 12.sp)
                                    }
                                    Text("Total: ${ugx(total)}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    if (o.status == "OPEN") {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Button(onClick = {
                                                scope.launch {
                                                    val its = db.purchaseOrderItemDao().forOrder(o.id)
                                                    if (its.isNotEmpty()) {
                                                        MoneyOps.receivePurchaseOrder(db, branchId, o, its, "CASH")
                                                    }
                                                    tick++
                                                }
                                            }) { Text("Receive & pay cash") }
                                            OutlinedButton(onClick = {
                                                scope.launch {
                                                    val its = db.purchaseOrderItemDao().forOrder(o.id)
                                                    if (its.isNotEmpty()) {
                                                        MoneyOps.receivePurchaseOrder(db, branchId, o, its, "CREDIT")
                                                    }
                                                    tick++
                                                }
                                            }) { Text("Receive on credit") }
                                            TextButton(onClick = {
                                                scope.launch { MoneyOps.cancelPurchaseOrder(db, o); tick++ }
                                            }) { Text("Cancel", color = MaterialTheme.colorScheme.error) }
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
}

@Composable
private fun NewPurchaseOrderDialog(
    db: AppDatabase, branchId: String, userId: String,
    suppliers: List<Supplier>, onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var lines by remember { mutableStateOf<List<OpLine>>(emptyList()) }
    var supplier by remember { mutableStateOf<Supplier?>(null) }
    var showPickSupplier by remember { mutableStateOf(false) }
    var expected by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }

    LaunchedEffect(Unit) {
        products = db.productDao().observeBranchProducts(branchId).first()
    }

    if (showPickSupplier) {
        AlertDialog(onDismissRequest = { showPickSupplier = false },
            title = { Text("Supplier (optional)") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TextButton(onClick = { supplier = null; showPickSupplier = false }) { Text("No supplier") }
                    suppliers.forEach { s ->
                        TextButton(onClick = { supplier = s; showPickSupplier = false }) {
                            Text(s.name, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {}, dismissButton = { TextButton(onClick = { showPickSupplier = false }) { Text("Cancel") } })
    }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("New purchase order") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showPickSupplier = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(supplier?.name ?: "Supplier (optional)…")
                }
                LineBuilder(products, lines, { lines = lines + it }, { lines = lines.filterIndexed { i, _ -> i != it } })
                OutlinedTextField(expected, { expected = it },
                    label = { Text("Expected date (e.g. 2026-09-10)") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, singleLine = true)
                Text("Order total: ${ugx(lines.sumOf { it.qty * it.unitCost })}",
                    fontWeight = FontWeight.Bold)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (lines.isEmpty()) { error = "Add at least one product"; return@Button }
                scope.launch {
                    try {
                        MoneyOps.createPurchaseOrder(db, branchId, userId, supplier?.id,
                            lines.map { Triple(it.product, it.qty, it.unitCost) },
                            expected.takeIf { it.isNotBlank() }, note)
                    } catch (_: Exception) { }
                    onDone()
                }
            }) { Text("Create order") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } }
    )
}
