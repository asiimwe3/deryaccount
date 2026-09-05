package com.derycode.deryaccount.ui.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.derycode.deryaccount.data.local.AppDatabase
import com.derycode.deryaccount.data.local.entity.Product
import kotlinx.coroutines.launch

/**
 * ProductDetailSheet — a product's whole world on one screen:
 * Stock (levels, valuation, expiry) → Transactions (every movement,
 * with receipt/purchase references) → Sales → Purchases → Profit.
 * Stock actions (receive, return, damage, expiry, count, transfer)
 * all post to the books atomically — see StockOps.
 */

/** Today as yyyy-MM-dd — API-24 safe. */
private fun nowYmd(): String = java.text.SimpleDateFormat(
    "yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

private fun ugx(v: Double): String = "UGX %,d".format(v.toLong())
private fun qtyFmt(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else "%.1f".format(v)

private data class ProductStats(
    val soldQty: Double = 0.0, val revenue: Double = 0.0, val cogs: Double = 0.0,
    val receivedQty: Double = 0.0
) {
    val grossProfit: Double get() = revenue - cogs
    val margin: Double get() = if (revenue > 0) grossProfit / revenue * 100 else 0.0
}

private fun movementTint(type: String): Color = when (type) {
    "SALE", "TRANSFER_OUT", "DAMAGE", "EXPIRY" -> Color(0xFFCC0000)
    "PURCHASE", "RETURN", "TRANSFER_IN", "OPENING", "SUPPLIER_RETURN" -> Color(0xFF1B8A3A)
    else -> Color(0xFF666666)
}

@Composable
fun ProductDetailSheet(
    db: AppDatabase, branchId: String, product: Product,
    onDismiss: () -> Unit, onEdit: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    val movements by db.stockMovementDao().observeForProduct(product.id)
        .collectAsState(initial = emptyList())
    var stats by remember { mutableStateOf(ProductStats()) }
    var sales by remember { mutableStateOf<List<com.derycode.deryaccount.data.local.dao.SaleItemWithSale>>(emptyList()) }
    var purchases by remember { mutableStateOf<List<com.derycode.deryaccount.data.local.dao.PurchaseItemWithPurchase>>(emptyList()) }
    var branches by remember { mutableStateOf(emptyList<com.derycode.deryaccount.data.local.entity.Branch>()) }

    LaunchedEffect(product.id) {
        stats = try {
            ProductStats(
                soldQty = db.saleItemDao().totalSoldFor(product.id),
                revenue = db.saleItemDao().revenueFor(product.id),
                cogs = db.saleItemDao().cogsFor(product.id),
                receivedQty = db.purchaseItemDao().totalReceivedFor(product.id))
        } catch (_: Exception) { ProductStats() }
        sales = try { db.saleItemDao().salesForProduct(product.id) } catch (_: Exception) { emptyList() }
        purchases = try { db.purchaseItemDao().purchasesForProduct(product.id) } catch (_: Exception) { emptyList() }
        branches = try { db.branchDao().all() } catch (_: Exception) { emptyList() }
    }

    var op by remember { mutableStateOf<String?>(null) }   // action dialog key
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                // ---- Header ----
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(product.name, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Text("${product.category} · ${product.barcode ?: "no barcode"}",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, "Edit", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }
                Spacer(Modifier.height(10.dp))

                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // ---- Stock card ----
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("STOCK ON HAND", fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f))
                                val low = product.stockQty <= product.lowStockAlert
                                Text("${qtyFmt(product.stockQty)} ${product.unit}",
                                    fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
                                    color = if (low) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.primary)
                            }
                            Text("Value at cost ${ugx(product.stockQty * product.costPrice)}  ·  " +
                                "at retail ${ugx(product.stockQty * product.retailPrice)}",
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Divider(Modifier.padding(vertical = 4.dp))
                            Stat("Minimum level", "${qtyFmt(product.lowStockAlert)} ${product.unit}")
                            Stat("Reorder level",
                                if (product.reorderLevel > 0) "${qtyFmt(product.reorderLevel)} ${product.unit}"
                                else "not set (uses minimum)")
                            product.expiryDate?.let {
                                val expired = it < nowYmd()
                                Stat("Expiry", it,
                                    color = if (expired) MaterialTheme.colorScheme.error else null)
                            }
                            Stat("Cost ${ugx(product.costPrice)} · Retail ${ugx(product.retailPrice)}", "")
                        }
                    }

                    // ---- Profit card ----
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("PROFIT (LIFETIME)", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    Text("Sold", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${qtyFmt(stats.soldQty)} ${product.unit}", fontWeight = FontWeight.Bold)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("Received", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${qtyFmt(stats.receivedQty)} ${product.unit}", fontWeight = FontWeight.Bold)
                                }
                            }
                            Stat("Revenue", ugx(stats.revenue))
                            Stat("Cost of goods sold", ugx(stats.cogs))
                            Row(Modifier.fillMaxWidth()) {
                                Text("Gross profit", fontSize = 13.sp,
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(ugx(stats.grossProfit), fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp,
                                    color = if (stats.grossProfit >= 0) Color(0xFF1B8A3A)
                                            else MaterialTheme.colorScheme.error)
                            }
                            Text("Margin ${"%.0f".format(stats.margin)}%", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // ---- Stock actions ----
                    Text("STOCK ACTIONS", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ActionChip("Receive", Icons.Default.Add, Modifier.weight(1f)) { op = "RECEIVE" }
                        ActionChip("Return", Icons.Default.Replay, Modifier.weight(1f)) { op = "RETURN" }
                        ActionChip("Damage", Icons.Default.Delete, Modifier.weight(1f)) { op = "DAMAGE" }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ActionChip("Expiry", Icons.Default.Schedule, Modifier.weight(1f)) { op = "EXPIRY" }
                        ActionChip("Count", Icons.Default.FactCheck, Modifier.weight(1f)) { op = "COUNT" }
                        if (branches.size > 1) {
                            ActionChip("Transfer", Icons.Default.SwapHoriz, Modifier.weight(1f)) { op = "TRANSFER" }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }

                    // ---- Movement history ----
                    SectionHeader("TRANSACTIONS (${movements.size})")
                    if (movements.isEmpty()) EmptyHint("No stock movements recorded yet")
                    movements.take(30).forEach { m ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(labelFor(m.type), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text("${m.movedAt.take(10)} · ${m.note ?: ""}",
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("${if (m.qty > 0) "+" else ""}${qtyFmt(m.qty)}",
                                fontWeight = FontWeight.Bold,
                                color = movementTint(m.type))
                        }
                    }

                    // ---- Sales ----
                    SectionHeader("SALES (${sales.size})")
                    if (sales.isEmpty()) EmptyHint("Not sold yet")
                    sales.take(15).forEach { s ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(s.receipt_no, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Text("${s.sold_at.take(10)} · ${qtyFmt(s.item.qty)} @ ${ugx(s.item.unitPrice)} · ${s.method}",
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(ugx(s.item.lineTotal), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // ---- Purchases ----
                    SectionHeader("PURCHASES (${purchases.size})")
                    if (purchases.isEmpty()) EmptyHint("No purchases recorded")
                    purchases.take(15).forEach { p ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Received ${p.received_at.take(10)}", fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium)
                                Text("${qtyFmt(p.item.qty)} @ ${ugx(p.item.unitCost)}",
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(ugx(p.item.lineTotal), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }

    // ---- Action dialogs ----
    when (op) {
        "RECEIVE" -> ReceiveStockDialog(db, branchId, product,
            onDone = { op = null }, onError = { error = it; op = null })
        "RETURN" -> CustomerReturnDialog(db, branchId, product,
            onDone = { op = null }, onError = { error = it; op = null })
        "DAMAGE" -> WriteOffDialog(db, branchId, product, expiry = false,
            onDone = { op = null }, onError = { error = it; op = null })
        "EXPIRY" -> WriteOffDialog(db, branchId, product, expiry = true,
            onDone = { op = null }, onError = { error = it; op = null })
        "COUNT" -> CountDialog(db, branchId, product,
            onDone = { op = null }, onError = { error = it; op = null })
        "TRANSFER" -> TransferDialog(db, branchId, product, branches,
            onDone = { op = null }, onError = { error = it; op = null })
    }

    error?.let { msg ->
        AlertDialog(onDismissRequest = { error = null },
            title = { Text("Not saved", fontWeight = FontWeight.Bold) },
            text = { Text(msg) },
            confirmButton = { Button(onClick = { error = null }) { Text("OK") } })
    }
}

private fun labelFor(type: String) = when (type) {
    "OPENING" -> "Opening stock"
    "PURCHASE" -> "Stock received"
    "SALE" -> "Sold"
    "RETURN" -> "Customer return"
    "TRANSFER_OUT" -> "Transfer out"
    "TRANSFER_IN" -> "Transfer in"
    "ADJUSTMENT" -> "Adjustment"
    "DAMAGE" -> "Damaged"
    "EXPIRY" -> "Expired"
    "COUNT" -> "Stock count"
    "SUPPLIER_RETURN" -> "Returned to supplier"
    else -> type
}

@Composable
private fun Stat(label: String, value: String, color: Color? = null) {
    if (value.isBlank()) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Row(Modifier.fillMaxWidth()) {
            Text(label, fontSize = 12.sp, modifier = Modifier.weight(1f),
                color = color ?: MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                color = color ?: MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun SectionHeader(t: String) {
    Text(t, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun EmptyHint(t: String) {
    Text(t, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
                       modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(6.dp)) {
        Icon(icon, null, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, maxLines = 1)
    }
}

// ---------------------------------------------------------------- actions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiveStockDialog(db: AppDatabase, branchId: String, product: Product,
                               onDone: () -> Unit, onError: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var qty by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf(product.costPrice.toLong().toString()) }
    var paidHow by remember { mutableStateOf("CASH") }
    var note by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(onDismissRequest = onDone,
        title = { Text("Receive stock — ${product.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(qty, { qty = it; err = null },
                    label = { Text("Quantity received") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(cost, { cost = it; err = null },
                    label = { Text("Unit cost (UGX)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = paidHow == "CASH", onClick = { paidHow = "CASH" },
                        label = { Text("Paid cash", fontSize = 11.sp) })
                    FilterChip(selected = paidHow == "CREDIT", onClick = { paidHow = "CREDIT" },
                        label = { Text("On supplier credit", fontSize = 11.sp) })
                }
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, singleLine = true)
                Text("Posts to the books: Dr Stock, Cr ${if (paidHow == "CASH") "Cash" else "Creditors"} at cost.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val q = qty.replace(",", "").toDoubleOrNull()
                val c = cost.replace(",", "").toDoubleOrNull()
                when {
                    q == null || q <= 0 -> err = "Enter a valid quantity"
                    c == null || c < 0 -> err = "Enter a valid unit cost"
                    else -> scope.launch {
                        try {
                            StockOps.receiveStock(db, branchId, product.id, q, c, paidHow, note)
                            onDone()
                        } catch (e: Exception) {
                            onError(com.derycode.deryaccount.util.DbSafety.friendly(e))
                        }
                    }
                }
            }) { Text("Receive") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerReturnDialog(db: AppDatabase, branchId: String, product: Product,
                                 onDone: () -> Unit, onError: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var qty by remember { mutableStateOf("") }
    var refund by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("CASH") }
    var customerId by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    val customers by db.customerDao().observeAll().collectAsState(initial = emptyList())

    fun syncRefund() {
        val q = qty.replace(",", "").toDoubleOrNull()
        if (q != null && refund.isBlank()) refund = (q * product.retailPrice).toLong().toString()
    }

    AlertDialog(onDismissRequest = onDone,
        title = { Text("Customer return — ${product.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(qty, { qty = it; err = null },
                    label = { Text("Quantity returned") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(refund, { refund = it; err = null },
                    label = { Text("Refund amount (UGX)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = method == "CASH", onClick = { method = "CASH" },
                        label = { Text("Cash refund", fontSize = 11.sp) })
                    FilterChip(selected = method == "CREDIT", onClick = { method = "CREDIT" },
                        label = { Text("Off customer account", fontSize = 11.sp) })
                }
                if (method == "CREDIT") {
                    Text("Which customer's debt should reduce?", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyColumn(Modifier.heightIn(max = 180.dp)) {
                        items(customers, key = { it.id }) { c ->
                            ListItem(
                                headlineContent = { Text(c.name, fontSize = 13.sp) },
                                supportingContent = {
                                    if (c.balance > 0) Text("owes ${ugx(c.balance)}", fontSize = 11.sp) },
                                trailingContent = {
                                    RadioButton(selected = customerId == c.id,
                                        onClick = { customerId = c.id })
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, singleLine = true)
                Text("Reverses the sale: Dr Sales Returns, Cr ${if (method == "CREDIT") "Debtors" else "Cash"}; " +
                    "stock comes back at cost.", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val q = qty.replace(",", "").toDoubleOrNull()
                val r = refund.replace(",", "").toDoubleOrNull()
                when {
                    q == null || q <= 0 -> err = "Enter a valid quantity"
                    r == null || r < 0 -> err = "Enter a valid refund amount"
                    method == "CREDIT" && customerId == null -> err = "Pick the customer whose account reduces"
                    else -> scope.launch {
                        try {
                            StockOps.customerReturn(db, branchId, product.id, q, r, method, customerId, note)
                            onDone()
                        } catch (e: Exception) {
                            onError(com.derycode.deryaccount.util.DbSafety.friendly(e))
                        }
                    }
                }
            }) { Text("Record return") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } })
}

@Composable
private fun WriteOffDialog(db: AppDatabase, branchId: String, product: Product,
                           expiry: Boolean, onDone: () -> Unit, onError: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var qty by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    val max = product.stockQty

    AlertDialog(onDismissRequest = onDone,
        title = { Text("${if (expiry) "Expired" else "Damaged"} stock — ${product.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("On hand: ${qtyFmt(max)} ${product.unit}", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(qty, { qty = it; err = null },
                    label = { Text("Quantity ${if (expiry) "expired" else "damaged"}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, singleLine = true)
                Text("Writes the stock off at cost (${ugx(product.costPrice)}/unit) — " +
                    "Dr Stock Loss, Cr Stock.", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val q = qty.replace(",", "").toDoubleOrNull()
                when {
                    q == null || q <= 0 -> err = "Enter a valid quantity"
                    q > max -> err = "More than stock on hand (${qtyFmt(max)})"
                    else -> scope.launch {
                        try {
                            if (expiry) StockOps.expireStock(db, branchId, product.id, q, note)
                            else StockOps.damageStock(db, branchId, product.id, q, note)
                            onDone()
                        } catch (e: Exception) {
                            onError(com.derycode.deryaccount.util.DbSafety.friendly(e))
                        }
                    }
                }
            }) { Text(if (expiry) "Write off" else "Record damage") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } })
}

@Composable
private fun CountDialog(db: AppDatabase, branchId: String, product: Product,
                        onDone: () -> Unit, onError: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var counted by remember { mutableStateOf(qtyFmt(product.stockQty)) }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(onDismissRequest = onDone,
        title = { Text("Stock count — ${product.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Book quantity: ${qtyFmt(product.stockQty)} ${product.unit}",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(counted, { counted = it; err = null },
                    label = { Text("Physically counted quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                val c = counted.replace(",", "").toDoubleOrNull()
                if (c != null && c != product.stockQty) {
                    val d = c - product.stockQty
                    Text("Variance: ${if (d > 0) "+" else ""}${qtyFmt(d)} ${product.unit} " +
                        "(${if (d > 0) "+" else ""}${ugx(d * product.costPrice)})",
                        fontWeight = FontWeight.Bold,
                        color = if (d < 0) MaterialTheme.colorScheme.error
                                else Color(0xFF1B8A3A))
                }
                Text("The book quantity becomes the counted quantity; the variance is " +
                    "valued at cost in the books.", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val c = counted.replace(",", "").toDoubleOrNull()
                if (c == null || c < 0) { err = "Enter a valid counted quantity"; return@Button }
                scope.launch {
                    try {
                        StockOps.applyCount(db, branchId, product.id, c, "physical count")
                        onDone()
                    } catch (e: Exception) {
                        onError(com.derycode.deryaccount.util.DbSafety.friendly(e))
                    }
                }
            }) { Text("Apply count") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransferDialog(db: AppDatabase, fromBranchId: String, product: Product,
                            branches: List<com.derycode.deryaccount.data.local.entity.Branch>,
                            onDone: () -> Unit, onError: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val others = branches.filter { it.id != fromBranchId }
    var toBranch by remember { mutableStateOf<String?>(null) }
    var qty by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var match by remember { mutableStateOf<com.derycode.deryaccount.data.local.entity.Product?>(null) }

    LaunchedEffect(toBranch) {
        match = null
        if (toBranch != null) {
            match = try {
                db.productDao().findByBarcode(product.barcode ?: "", toBranch!!)
                    ?: db.productDao().search(product.name, toBranch!!)
                        .firstOrNull { it.name.equals(product.name, ignoreCase = true) }
            } catch (_: Exception) { null }
        }
    }

    AlertDialog(onDismissRequest = onDone,
        title = { Text("Transfer stock — ${product.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("On hand: ${qtyFmt(product.stockQty)} ${product.unit}", fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    others.take(3).forEach { b ->
                        FilterChip(selected = toBranch == b.id, onClick = { toBranch = b.id },
                            label = { Text(b.name, fontSize = 11.sp) })
                    }
                }
                if (toBranch != null && match == null) {
                    Text("No matching product in that branch yet — add \"${product.name}\" there first.",
                        color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                val matchBranchName = branches.firstOrNull { it.id == match?.branchId }?.name
                if (match != null && matchBranchName != null) {
                    Text("Matches \"$matchBranchName\" — stock will land there.",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(qty, { qty = it; err = null },
                    label = { Text("Quantity to transfer") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                Text("Same business, so the books don't change — only branch quantities move.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val q = qty.replace(",", "").toDoubleOrNull()
                val m = match
                when {
                    toBranch == null -> err = "Pick the destination branch"
                    m == null -> err = "No matching product in that branch"
                    q == null || q <= 0 -> err = "Enter a valid quantity"
                    q > product.stockQty -> err = "More than on hand (${qtyFmt(product.stockQty)})"
                    else -> scope.launch {
                        try {
                            StockOps.transferStock(db, fromBranchId, product, toBranch!!, m.id, q, "stock transfer")
                            onDone()
                        } catch (e: Exception) {
                            onError(com.derycode.deryaccount.util.DbSafety.friendly(e))
                        }
                    }
                }
            }) { Text("Transfer") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } })
}
