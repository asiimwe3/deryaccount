package com.derycode.deryaccount.ui.pos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.derycode.deryaccount.data.local.entity.Product
import com.derycode.deryaccount.util.PdfExport
import kotlinx.coroutines.launch
import java.io.File

/**
 * PosScreen — designed for SPEED in a busy shop:
 *  1. Tap product tile → it's in the cart. Tap again → +1.
 *  2. Scan barcode → auto-added. Unknown barcode → instant product creation.
 *  3. Big green CASH button → done. Quick note buttons for common cash amounts.
 * No menus, no navigation, zero thinking for the cashier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(
    viewModel: PosViewModel,
    onSaleComplete: (receiptNo: String) -> Unit
) {
    val ui by viewModel.uiState.collectAsState()
    val catalog by viewModel.catalog.collectAsState(initial = emptyList())
    var scanInput by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var showBanner by remember { mutableStateOf(true) }
    var showCalc by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }

    // Auto-open the print dialog after EVERY sale (receipt saved as PDF too)
    ui.lastReceipt?.pdfPath?.let { path ->
        LaunchedEffect(path) {
            try { PdfExport.printPdf(context, File(path), "Receipt") } catch (_: Exception) {}
        }
    }

    Column(Modifier.fillMaxSize().padding(8.dp)) {

        // ---- Hero banner: free for the first 100 ----
        if (showBanner) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "FREE for the first 100 Ugandan businesses — every feature, 100% offline.",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showBanner = false }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, "Close", modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        // ---- Scan bar (the fastest path) ----
        OutlinedTextField(
            value = scanInput,
            onValueChange = { scanInput = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Scan barcode / search…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (scanInput.isNotBlank()) { viewModel.onScan(scanInput.trim()); scanInput = "" }
            }),
            leadingIcon = {
                Row {
                    IconButton(onClick = { showScanner = true }) {
                        Icon(Icons.Default.QrCodeScanner, "Scan with camera")
                    }
                    Icon(Icons.Default.Search, null,
                        modifier = Modifier.align(Alignment.CenterVertically))
                }
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showCalc = true }) {
                        Icon(Icons.Default.Calculate, "Calculator")
                    }
                    FilterChip(
                        selected = ui.saleType == "WHOLESALE",
                        onClick = viewModel::toggleSaleType,
                        label = { Text("Wholesale") }
                    )
                }
            }
        )
        Spacer(Modifier.height(6.dp))

        // ---- Cart (when items present) ----
        if (ui.cart.isNotEmpty()) {
            Surface(
                Modifier.fillMaxWidth().weight(if (ui.cart.size > 3) 1f else 0f),
                tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium
            ) {
                Column(Modifier.padding(6.dp)) {
                    ui.cart.take(6).forEach { line ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(line.product.name, Modifier.weight(1f),
                                fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            // big +/- steppers, no typing needed
                            FilledTonalIconButton(onClick = { viewModel.setQty(line.product.id, line.qty - 1) }) {
                                Icon(Icons.Default.Remove, null)
                            }
                            Text(" ${fmtQty(line.qty)} ", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            FilledTonalIconButton(onClick = { viewModel.setQty(line.product.id, line.qty + 1) }) {
                                Icon(Icons.Default.Add, null)
                            }
                            Text(fmtMoney(line.lineTotal), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            IconButton(onClick = { viewModel.removeLine(line.product.id) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    if (ui.cart.size > 6) Text("+${ui.cart.size - 6} more items…", fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        // ---- Product tiles: tap = sell. Ordered alphabetically, big buttons. ----
        LazyVerticalGrid(
            columns = GridCells.Adaptive(110.dp),
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val showList = if (ui.searchResults.isNotEmpty()) ui.searchResults else catalog
            items(showList, key = { it.id }) { p ->
                ProductTile(p, onTap = {
                    viewModel.addProduct(p)
                    scanInput = ""
                })
            }
        }

        // ---- Total + big CASH button ----
        Surface(Modifier.fillMaxWidth(), tonalElevation = 4.dp,
            shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("TOTAL", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text(fmtMoney(ui.total), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { if (ui.total > 0) viewModel.checkout("CASH", ui.total) },
                        modifier = Modifier.weight(2f).height(58.dp)
                    ) {
                        Icon(Icons.Default.Payments, null); Spacer(Modifier.width(6.dp))
                        Text("CASH", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { viewModel.pendingMethod = "MTN_MOMO" },
                        modifier = Modifier.weight(1f).height(58.dp)
                    ) { Text("MoMo", fontSize = 14.sp) }
                    OutlinedButton(
                        onClick = { viewModel.pendingMethod = "CREDIT" },
                        modifier = Modifier.weight(1f).height(58.dp)
                    ) { Text("Credit", fontSize = 14.sp) }
                }
            }
        }
    }

    // ---- Camera barcode scanner ----
    if (showScanner) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showScanner = false }) {
            Surface(Modifier.fillMaxSize()) {
                BarcodeScannerScreen(
                    onScanned = { code ->
                        showScanner = false
                        viewModel.onScan(code)
                    },
                    onClose = { showScanner = false })
            }
        }
    }

    // ---- Calculator ----
    if (showCalc) CalculatorDialog(onClose = { showCalc = false })

    // ---- Unknown barcode → instant product creation ----
    ui.unknownBarcode?.let { code ->
        QuickAddDialog(
            barcode = code,
            onSave = { name, price, cst, qty -> viewModel.quickAddProduct(name, price, cst, qty, code) },
            onDismiss = viewModel::dismissQuickAdd
        )
    }

    // ---- Payment dialog for MoMo / Credit / change calculation ----
    if (ui.showPaymentDialog) PaymentDialog(
        total = ui.total,
        method = ui.pendingMethod ?: "CASH",
        onPay = { amt -> viewModel.checkout(ui.pendingMethod ?: "CASH", amt) },
        onDismiss = { viewModel.pendingMethod = null; viewModel.showPaymentDialog = false }
    )

    // ---- Receipt after sale ----
    ui.lastReceipt?.let { receipt ->
        ReceiptDialog(
            receipt = receipt,
            onPrint = { viewModel.printReceipt() },
            onPdf = {
                receipt.pdfPath?.let { path ->
                    try { PdfExport.printPdf(context, File(path), "Receipt ${receipt.sale.receiptNo}") } catch (_: Exception) {}
                }
            },
            onInvoice = {
                scope.launch {
                    try {
                        val f = PdfExport.invoicePdf(
                            context, receipt.shopName, receipt.sale.receiptNo, "Customer",
                            receipt.items.map { Triple(it.name, it.qty, it.lineTotal) },
                            receipt.sale.total)
                        PdfExport.printPdf(context, f, "Invoice ${receipt.sale.receiptNo}")
                    } catch (_: Exception) {}
                }
            },
            onDone = { viewModel.clearReceipt(); onSaleComplete(receipt.sale.receiptNo) }
        )
    }
}

/** Big tappable product button — name + price, thumb friendly. */
@Composable
private fun ProductTile(p: Product, onTap: () -> Unit) {
    OutlinedButton(
        onClick = onTap,
        modifier = Modifier.height(84.dp),
        contentPadding = PaddingValues(8.dp)
    ) {
        Column(
            Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(p.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, modifier = Modifier.padding(horizontal = 2.dp))
            Spacer(Modifier.height(2.dp))
            Text(fmtMoney(p.retailPrice), fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            val left = if (p.stockQty % 1.0 == 0.0) p.stockQty.toLong() else p.stockQty
            Text(
                if (p.stockQty <= 0) "OUT" else "$left left",
                fontSize = 10.sp,
                color = if (p.stockQty <= p.lowStockAlert) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (p.stockQty <= p.lowStockAlert) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

/** One-shot product creation from an unknown barcode — 2 fields only. */
@Composable
private fun QuickAddDialog(barcode: String, onSave: (String, Double, Double, Double) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New product — barcode $barcode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(price, { price = it }, label = { Text("Selling price") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(cost, { cost = it }, label = { Text("Cost price (optional)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(qty, { qty = it }, label = { Text("Stock qty") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = {
            Button(onClick = {
                val pr = price.toDoubleOrNull() ?: return@Button
                val q = qty.toDoubleOrNull() ?: 1.0
                val c = cost.toDoubleOrNull() ?: pr
                if (name.isNotBlank()) onSave(name, pr, c, q)
            }) { Text("Save & Sell") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PaymentDialog(total: Double, method: String, onPay: (Double) -> Unit, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(method.replace("_", " ")) },
        text = {
            Column {
                Text("Total: ${fmtMoney(total)}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it; error = null },
                    label = { Text("Amount received") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Spacer(Modifier.height(8.dp))
                // quick note buttons — most common Ugandan notes, zero typing
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(10000, 20000, 50000).forEach { note ->
                        OutlinedButton(onClick = { amount = note.toString(); error = null }) {
                            Text("${note / 1000}k")
                        }
                    }
                    OutlinedButton(onClick = { amount = total.toLong().toString(); error = null }) { Text("Exact") }
                }
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amount.toDoubleOrNull()
                when {
                    amt == null || amt <= 0 -> error = "Enter the amount received"
                    amt < total -> error = "Not enough — balance due ${fmtMoney(total - amt)}"
                    else -> onPay(amt)
                }
            }) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ReceiptDialog(
    receipt: PosViewModel.ReceiptUi,
    onPrint: () -> Unit,
    onPdf: () -> Unit,
    onInvoice: () -> Unit,
    onDone: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Sold ✓  ${fmtMoney(receipt.sale.total)}", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                Text("Receipt: ${receipt.sale.receiptNo}", fontWeight = FontWeight.Bold)
                if (receipt.sale.changeGiven > 0)
                    Text("Change: ${fmtMoney(receipt.sale.changeGiven)}", fontSize = 16.sp,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                receipt.printed?.let { Text(it, fontSize = 12.sp) }
                Text("Next customer ready…", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onPdf) { Icon(Icons.Default.PictureAsPdf, null); Text(" PDF") }
                TextButton(onClick = onInvoice) { Icon(Icons.Default.Receipt, null); Text(" Invoice") }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = onPrint) { Icon(Icons.Default.Print, null); Text("Print") }
                TextButton(onClick = onDone) { Text("Done") }
            }
        }
    )
}

private fun fmtMoney(v: Double): String = "UGX %,d".format(v.toLong())
private fun fmtQty(q: Double): String = if (q % 1.0 == 0.0) q.toLong().toString() else "%.1f".format(q)


// ----------------------------------------------------------------
// CALCULATOR — shop math at the counter, big thumb-friendly keys
// ----------------------------------------------------------------
@Composable
private fun CalculatorDialog(onClose: () -> Unit) {
    var display by remember { mutableStateOf("0") }
    var acc by remember { mutableStateOf(0.0) }   // accumulator
    var pendingOp by remember { mutableStateOf<String?>(null) }
    var fresh by remember { mutableStateOf(true) }  // next digit starts new number

    fun inputDigit(d: String) {
        display = if (fresh || display == "0") d else display + d
        fresh = false
    }
    fun applyOp(op: String) {
        val cur = display.toDoubleOrNull() ?: 0.0
        if (pendingOp != null && !fresh) {
            val r = when (pendingOp) {
                "+" -> acc + cur; "-" -> acc - cur
                "x" -> acc * cur
                "÷" -> if (cur != 0.0) acc / cur else 0.0
                else -> cur
            }
            acc = r
            display = if (r == r.toLong().toDouble()) r.toLong().toString() else "%.2f".format(r)
        } else acc = cur
        pendingOp = op
        fresh = true
    }
    fun equals() {
        val cur = display.toDoubleOrNull() ?: 0.0
        val r = when (pendingOp) {
            "+" -> acc + cur; "-" -> acc - cur
            "x" -> acc * cur
            "÷" -> if (cur != 0.0) acc / cur else 0.0
            else -> cur
        }
        display = if (r == r.toLong().toDouble()) r.toLong().toString() else "%.2f".format(r)
        acc = 0.0; pendingOp = null; fresh = true
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Calculator", fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.End) {
                Text(display, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Right)
                Spacer(Modifier.height(8.dp))
                val rows = listOf(
                    listOf("7", "8", "9", "÷"),
                    listOf("4", "5", "6", "x"),
                    listOf("1", "2", "3", "-"),
                    listOf("0", ".", "=", "+")
                )
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()) {
                        row.forEach { key ->
                            val isOp = key in listOf("÷", "x", "-", "+", "=")
                            if (isOp) FilledTonalButton(
                                onClick = { if (key == "=") equals() else applyOp(key) },
                                modifier = Modifier.weight(1f).height(52.dp)
                            ) { Text(key, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                            else OutlinedButton(
                                onClick = {
                                    if (key == ".") { if (fresh) { display = "0."; fresh = false }
                                        else if (!display.contains(".")) display += "." }
                                    else inputDigit(key)
                                },
                                modifier = Modifier.weight(1f).height(52.dp)
                            ) { Text(key, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                Row(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = {
                        display = "0"; acc = 0.0; pendingOp = null; fresh = true
                    }, modifier = Modifier.weight(1f)) { Text("Clear") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Close") }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}
