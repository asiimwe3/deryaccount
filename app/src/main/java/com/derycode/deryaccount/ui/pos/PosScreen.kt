package com.derycode.deryaccount.ui.pos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.items as lazyListItems
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.StarBorder
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
import com.derycode.deryaccount.ui.theme.*
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
    onSaleComplete: (receiptNo: String) -> Unit,
    branchName: String = "Main Branch",
    cashierName: String = "Cashier"
) {
    val ui by viewModel.uiState.collectAsState()
    val catalog by viewModel.catalog.collectAsState(initial = emptyList())
    var scanInput by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var showBanner by remember { mutableStateOf(true) }
    var showCalc by remember { mutableStateOf(false) }
    var showCreditPicker by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var showHoldDialog by remember { mutableStateOf(false) }
    var showHeldSheet by remember { mutableStateOf(false) }
    var showCustomerPicker by remember { mutableStateOf(false) }
    var discountInput by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var gridMode by remember { mutableStateOf(true) }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    var visibleCount by remember { mutableStateOf(6) }

    // Lightweight, best-effort connectivity display — the app itself never depends on it
    var online by remember { mutableStateOf(false) }
    var syncTick by remember { mutableStateOf(0) }
    LaunchedEffect(syncTick) { online = viewModel.isOnline() }

    // Keep the discount box in sync when a held sale resumes with a discount
    LaunchedEffect(ui.discount) {
        if (ui.discount > 0 && discountInput.isBlank()) discountInput = ui.discount.toLong().toString()
        if (ui.discount == 0.0) discountInput = ""
    }

    // Auto-open the print dialog after EVERY sale (receipt saved as PDF too)
    ui.lastReceipt?.pdfPath?.let { path ->
        LaunchedEffect(path) {
            try { PdfExport.printPdf(context, File(path), "Receipt") } catch (_: Exception) {}
        }
    }

    Column(Modifier.fillMaxSize().padding(8.dp)) {

        // ---- Status row: branch, cashier, connectivity pill, manual sync ----
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("$branchName  •  Cashier: $cashierName", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Surface(
                color = if (online) DaGreen.copy(alpha = 0.15f) else DaRed.copy(alpha = 0.15f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(if (online) "Online" else "Offline", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = if (online) DaGreen else DaRed,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
            }
            Spacer(Modifier.width(6.dp))
            TextButton(onClick = {
                viewModel.manualSync { msg ->
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                }
                syncTick++
            }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                Icon(Icons.Default.Sync, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(2.dp))
                Text("Sync", fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(4.dp))

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
                    if (ui.heldSales.isNotEmpty()) {
                        androidx.compose.material3.BadgedBox(badge = {
                            androidx.compose.material3.Badge {
                                Text("${ui.heldSales.size}", fontSize = 9.sp)
                            }
                        }) {
                            IconButton(onClick = { showHeldSheet = true }) {
                                Icon(Icons.Outlined.PauseCircle, "Held sales")
                            }
                        }
                    }
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Cart (${ui.cart.size} items)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        TextButton(onClick = { viewModel.clearCart(); discountInput = "" }) {
                            Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(16.dp), tint = DaRed)
                            Spacer(Modifier.width(2.dp))
                            Text("Clear Cart", color = DaRed, fontSize = 12.sp)
                        }
                    }
                    if (ui.customerName != null) {
                        Text("Customer: ${ui.customerName}", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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

        // ---- Category filter chips (derived from the real catalog) ----
        val allCategories = remember(catalog) { catalog.map { it.category }.distinct().sorted() }
        val shownChips = allCategories.take(4)
        val restChips = allCategories.drop(4)
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                FilterChip(selected = selectedCategory == "All", onClick = { selectedCategory = "All" },
                    label = { Text("All") })
            }
            lazyListItems(shownChips) { cat ->
                FilterChip(selected = selectedCategory == cat, onClick = { selectedCategory = cat },
                    label = { Text(cat) })
            }
            if (restChips.isNotEmpty()) item {
                Box {
                    FilterChip(selected = restChips.contains(selectedCategory), onClick = { categoryMenuOpen = true },
                        label = { Text("More") }, trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp)) })
                    DropdownMenu(expanded = categoryMenuOpen, onDismissRequest = { categoryMenuOpen = false }) {
                        restChips.forEach { cat ->
                            DropdownMenuItem(text = { Text(cat) }, onClick = {
                                selectedCategory = cat; categoryMenuOpen = false
                            })
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))

        // ---- Product count + Grid/List toggle ----
        val filtered = remember(catalog, selectedCategory, ui.searchResults) {
            val base = if (ui.searchResults.isNotEmpty()) ui.searchResults
                       else if (selectedCategory == "All") catalog
                       else catalog.filter { it.category == selectedCategory }
            base
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Products (${filtered.size})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            IconButton(onClick = { gridMode = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.GridView, "Grid",
                    tint = if (gridMode) DaGreen else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { gridMode = false }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.List, "List",
                    tint = if (!gridMode) DaGreen else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(4.dp))

        // ---- Product tiles: tap the star to favourite, use the stepper + cart button to sell ----
        val showList = filtered.take(visibleCount)
        if (gridMode) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(showList, key = { it.id }) { p ->
                    ProductCard(p,
                        onAdd = { qty -> viewModel.addProductQty(p, qty); scanInput = "" },
                        onToggleFav = { viewModel.toggleFavourite(p.id, !p.isFavourite) })
                }
                if (filtered.size > visibleCount) item(span = { GridItemSpan(2) }) {
                    TextButton(onClick = { visibleCount += 6 }, modifier = Modifier.fillMaxWidth()) {
                        Text("View more products (${filtered.size - visibleCount} more)")
                    }
                }
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                lazyListItems(showList, key = { it.id }) { p ->
                    ProductTile(p,
                        onTap = { viewModel.addProduct(p); scanInput = "" },
                        onToggleFav = { viewModel.toggleFavourite(p.id, !p.isFavourite) })
                }
                if (filtered.size > visibleCount) item {
                    TextButton(onClick = { visibleCount += 6 }, modifier = Modifier.fillMaxWidth()) {
                        Text("View more products (${filtered.size - visibleCount} more)")
                    }
                }
            }
        }

        // ---- Checkout card: Subtotal/Discount/Tax/TOTAL, then payment + quick actions ----
        Surface(Modifier.fillMaxWidth(), tonalElevation = 4.dp,
            shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Subtotal", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(fmtMoney(ui.subtotal), fontSize = 13.sp)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Discount", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = discountInput,
                        onValueChange = { raw ->
                            discountInput = raw.filter { it.isDigit() }
                            discountInput.toDoubleOrNull()?.let { viewModel.setDiscount(it) }
                        },
                        modifier = Modifier.width(120.dp).height(48.dp),
                        placeholder = { Text("0") },
                        singleLine = true,
                        prefix = { Text("UGX", fontSize = 11.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Tax (0%)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("UGX 0", fontSize = 13.sp)
                }
                Divider(Modifier.padding(vertical = 6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("TOTAL", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text(fmtMoney(ui.total), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = DaGreen)
                }
                Spacer(Modifier.height(8.dp))

                // ---- Payment methods: three equal, big, colour-coded buttons ----
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { if (ui.total > 0) viewModel.checkout("CASH", ui.total) },
                        colors = ButtonDefaults.buttonColors(containerColor = DaGreen, contentColor = Color(0xFF03150A)),
                        modifier = Modifier.weight(1f).height(58.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Payments, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp)); Text("CASH", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("Pay with cash", fontSize = 9.sp)
                        }
                    }
                    Button(
                        onClick = { viewModel.pendingMethod = "MTN_MOMO" },
                        colors = ButtonDefaults.buttonColors(containerColor = DaAmber, contentColor = Color(0xFF241800)),
                        modifier = Modifier.weight(1f).height(58.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Smartphone, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp)); Text("MOBILE MONEY", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("MTN / Airtel", fontSize = 9.sp)
                        }
                    }
                    Button(
                        onClick = { if (ui.total > 0) showCreditPicker = true },
                        colors = ButtonDefaults.buttonColors(containerColor = DaBlue, contentColor = Color.White),
                        modifier = Modifier.weight(1f).height(58.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CreditCard, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp)); Text("CREDIT", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("Card / Credit sale", fontSize = 9.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                // ---- Quick actions: Hold Sale, Customer, Receipt ----
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { if (ui.cart.isNotEmpty()) showHoldDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.PauseCircle, null, modifier = Modifier.size(18.dp))
                            Text("Hold Sale", fontSize = 11.sp)
                            Text("Save for later", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    TextButton(
                        onClick = { showCustomerPicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Person, null, modifier = Modifier.size(18.dp))
                            Text("Customer", fontSize = 11.sp)
                            Text(ui.customerName ?: "Select customer", fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                    TextButton(
                        onClick = {
                            if (ui.lastReceipt != null) viewModel.printReceipt()
                            else scope.launch {
                                android.widget.Toast.makeText(context, "Complete a sale first", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Receipt, null, modifier = Modifier.size(18.dp))
                            Text("Receipt", fontSize = 11.sp)
                            Text("Print / Preview", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
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

    // ---- Hold Sale ----
    if (showHoldDialog) HoldSaleDialog(
        onHold = { note ->
            viewModel.holdSale(note); discountInput = ""; showHoldDialog = false
        },
        onDismiss = { showHoldDialog = false })

    // ---- Parked carts ----
    if (showHeldSheet) HeldSalesSheet(viewModel, onDismiss = { showHeldSheet = false })

    // ---- Attach a customer to this sale (any payment method, not just credit) ----
    if (showCustomerPicker) CustomerPickerDialog(
        viewModel = viewModel,
        onPicked = { id, name -> viewModel.setCustomer(id, name); showCustomerPicker = false },
        onClear = { viewModel.setCustomer(null, null); showCustomerPicker = false },
        onDismiss = { showCustomerPicker = false }
    )

    // ---- Unknown barcode → instant product creation ----
    ui.unknownBarcode?.let { code ->
        QuickAddDialog(
            barcode = code,
            onSave = { name, price, cst, qty -> viewModel.quickAddProduct(name, price, cst, qty, code) },
            onDismiss = viewModel::dismissQuickAdd
        )
    }

    // ---- Payment dialog for MoMo / Credit / change calculation ----
    if (showCreditPicker) CreditPickerDialog(
        viewModel = viewModel, total = ui.total, onDismiss = { showCreditPicker = false }
    )

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
private fun ProductTile(p: Product, onTap: () -> Unit, onToggleFav: () -> Unit = {}) {
    Box(Modifier.height(84.dp)) {
        OutlinedButton(
            onClick = onTap,
            modifier = Modifier.fillMaxSize(),
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
        // Favourite star — top corner, floats the product to the top of the grid
        androidx.compose.material3.IconButton(
            onClick = onToggleFav,
            modifier = Modifier.align(Alignment.TopEnd).size(26.dp)
        ) {
            androidx.compose.material3.Icon(
                if (p.isFavourite) Icons.Filled.Star
                else Icons.Outlined.StarBorder,
                contentDescription = if (p.isFavourite) "Remove favourite" else "Favourite",
                tint = if (p.isFavourite) androidx.compose.ui.graphics.Color(0xFFF5B301)
                       else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(16.dp)
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

/**
 * Credit sale flow: pick the customer this sale goes on (or create them
 * inline), then the sale posts to their balance. No customer = no credit.
 */
@Composable
private fun CreditPickerDialog(
    viewModel: PosViewModel,
    total: Double,
    onDismiss: () -> Unit
) {
    val customers by viewModel.customers.collectAsState(initial = emptyList())
    var search by remember { mutableStateOf("") }
    var newMode by remember { mutableStateOf(customers.isEmpty()) }
    var newName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sell on credit — UGX %,d".format(total.toLong())) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            if (newMode) {
                Text("New customer", fontWeight = FontWeight.Bold)
                OutlinedTextField(newName, { newName = it }, label = { Text("Name") }, singleLine = true)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(newPhone, { newPhone = it }, label = { Text("Phone (optional)") }, singleLine = true)
                Spacer(Modifier.height(6.dp))
                if (customers.isNotEmpty()) {
                    TextButton(onClick = { newMode = false }) { Text("Choose existing instead") }
                }
            } else {
                OutlinedTextField(search, { search = it },
                    label = { Text("Find customer") }, singleLine = true)
                Spacer(Modifier.height(6.dp))
                val shown = customers.filter {
                    search.isBlank() || it.name.contains(search, ignoreCase = true)
                            || (it.phone?.contains(search) == true)
                }
                shown.take(8).forEach { cust ->
                    ListItem(
                        headlineContent = { Text(cust.name) },
                        supportingContent = {
                            Text(if (cust.balance > 0)
                                "already owes UGX %,d".format(cust.balance.toLong())
                            else cust.phone ?: "")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        trailingContent = {
                            TextButton(onClick = {
                                onDismiss()
                                viewModel.checkout("CREDIT", total, cust.id)
                            }) { Text("Sell") }
                        }
                    )
                    Divider()
                }
                TextButton(onClick = { newMode = true }) { Text("+ New customer") }
            }
        } },
        confirmButton = {
            if (newMode) Button(onClick = {
                if (newName.isNotBlank()) viewModel.addCustomer(newName, newPhone.ifBlank { null }) { id ->
                    onDismiss()
                    viewModel.checkout("CREDIT", total, id)
                }
            }) { Text("Create & Sell") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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


/** Hold Sale — park the current cart with an optional note ("blue jerrycan guy"). */
@Composable
private fun HoldSaleDialog(onHold: (String) -> Unit, onDismiss: () -> Unit) {
    var note by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hold this sale?", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("The cart is parked and the screen clears. Resume it any time from the pause button at the top.", fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    placeholder = { Text("e.g. Customer went to ATM") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onHold(note) }) { Text("Hold") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Parked carts — resume one (current cart auto-holds) or discard it. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun HeldSalesSheet(viewModel: PosViewModel, onDismiss: () -> Unit) {
    val ui by viewModel.uiState.collectAsState()
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp)) {
            Text("Held sales", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            if (ui.heldSales.isEmpty()) {
                Text("Nothing on hold right now.", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ui.heldSales.forEach { held ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(held.note, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("${viewModel.heldLineCount(held)} items · ${held.createdAt.take(10)}",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    androidx.compose.material3.TextButton(onClick = {
                        viewModel.resumeHeldSale(held.id); onDismiss()
                    }) { Text("Resume", fontWeight = FontWeight.Bold) }
                    androidx.compose.material3.TextButton(onClick = {
                        viewModel.discardHeldSale(held.id)
                    }) { Text("Discard", color = MaterialTheme.colorScheme.error) }
                }
                androidx.compose.material3.Divider()
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/**
 * Product card for the 2-column Sell grid — image placeholder, favourite star,
 * name, live stock count, price, a qty stepper, and a dedicated add-to-cart
 * button (adds exactly the stepped quantity in one tap).
 */
@Composable
private fun ProductCard(p: Product, onAdd: (Double) -> Unit, onToggleFav: () -> Unit) {
    var qty by remember(p.id) { mutableStateOf(1) }
    Surface(
        color = DaSurface, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(10.dp)) {
            Box(Modifier.fillMaxWidth()) {
                Box(
                    Modifier.fillMaxWidth().height(56.dp)
                        .background(DaSurface2, MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Inventory2, null, tint = DaTextMuted, modifier = Modifier.size(28.dp))
                }
                IconButton(onClick = onToggleFav, modifier = Modifier.align(Alignment.TopEnd).size(26.dp)) {
                    Icon(
                        if (p.isFavourite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (p.isFavourite) "Remove favourite" else "Favourite",
                        tint = if (p.isFavourite) DaAmber else DaTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(p.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                color = DaTextPrimary)
            val left = if (p.stockQty % 1.0 == 0.0) p.stockQty.toLong().toString() else p.stockQty.toString()
            Text(
                if (p.stockQty <= 0) "OUT OF STOCK" else "In stock: $left",
                fontSize = 11.sp,
                color = if (p.stockQty <= p.lowStockAlert) DaRed else DaTextMuted
            )
            Text(fmtMoney(p.retailPrice), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DaGreen)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                FilledTonalIconButton(onClick = { if (qty > 1) qty-- }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Remove, null, modifier = Modifier.size(14.dp))
                }
                Text(" $qty ", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                FilledTonalIconButton(onClick = { qty++ }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { onAdd(qty.toDouble()) },
                    enabled = p.stockQty > 0,
                    modifier = Modifier.size(32.dp).background(DaGreen, MaterialTheme.shapes.small)
                ) {
                    Icon(Icons.Default.AddShoppingCart, "Add to cart", tint = Color(0xFF03150A), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** Attach a customer to the current sale — works for cash/MoMo too, not just credit. */
@Composable
private fun CustomerPickerDialog(
    viewModel: PosViewModel,
    onPicked: (String, String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val customers by viewModel.customers.collectAsState(initial = emptyList())
    var search by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select customer", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(search, { search = it },
                    label = { Text("Find customer") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                val shown = customers.filter {
                    search.isBlank() || it.name.contains(search, ignoreCase = true)
                            || (it.phone?.contains(search) == true)
                }
                if (shown.isEmpty()) Text("No customers yet — add one from Customers.", fontSize = 12.sp)
                shown.take(10).forEach { cust ->
                    ListItem(
                        headlineContent = { Text(cust.name) },
                        supportingContent = { cust.phone?.let { Text(it) } },
                        modifier = Modifier.fillMaxWidth().clickable(onClick = { onPicked(cust.id, cust.name) })
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onClear) { Text("Walk-in (no customer)") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
