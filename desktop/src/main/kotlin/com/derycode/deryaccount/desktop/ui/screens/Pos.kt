package com.derycode.deryaccount.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.desktop.data.*
import com.derycode.deryaccount.desktop.ui.*
import java.io.File

@Composable
fun PosScreen(app: AppState) {
    val repo = app.repo
    var search by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    var products by remember { mutableStateOf(repo.products()) }
    val cart = remember { mutableStateListOf<Repo.CartLine>() }
    var customerId by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("CASH") }
    var discount by remember { mutableStateOf("") }
    var amountPaid by remember { mutableStateOf("") }
    var saleType by remember { mutableStateOf("RETAIL") }
    var customers by remember { mutableStateOf(repo.customers()) }
    var categories by remember { mutableStateOf(listOf("All") + repo.categories()) }
    var receiptToShow by remember { mutableStateOf<Repo.CheckoutResult?>(null) }
    var showHeld by remember { mutableStateOf(false) }
    var heldNote by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(app.tick, search, category) {
        products = repo.products(search = search).filter {
            category == "All" || it.str("category") == category
        }
        customers = repo.customers()
        categories = listOf("All") + repo.categories()
    }

    val subtotal = Fmt.r2(cart.sumOf { it.lineTotal })
    val discountValue = discount.toDoubleOrNull() ?: 0.0
    val total = Fmt.r2(subtotal - discountValue).coerceAtLeast(0.0)

    fun addProduct(p: Row) {
        val existing = cart.indexOfFirst { it.productId == p.str("id") }
        val price = repo.priceFor(p, if (existing >= 0) cart[existing].qty + 1 else 1.0, saleType)
        if (existing >= 0) {
            val line = cart[existing]
            cart[existing] = line.copy(qty = line.qty + 1, unitPrice = price)
        } else {
            cart.add(Repo.CartLine(p.str("id"), p.str("name"), 1.0, price, p.dbl("cost_price"), p.dbl("tax_rate")))
        }
    }

    Row(Modifier.fillMaxSize().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // ── Catalogue
        Column(Modifier.weight(1.75f).fillMaxHeight()) {
            ScreenHeader("Sell / POS", "${app.repo.businessName()} · receipt ${repo.nextReceiptNo()}") {
                if (cart.isNotEmpty()) {
                    Btn("Park sale", {
                        repo.holdSale(cart.toList(), heldNote.ifBlank { "Parked ${Fmt.dateTime(Clock.nowIso())}" }, discountValue)
                        cart.clear(); discount = ""; amountPaid = ""; app.refresh(); app.say("Sale parked")
                    }, Icons.Default.Pause, BtnKind.GHOST)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SearchBar(search, { search = it }, "Search product, barcode or category") }
                Box(Modifier.width(220.dp)) {
                    Field("Barcode", barcode, { barcode = it }, placeholder = "scan or type")
                }
                Btn("Add", {
                    val hit = repo.products(search = barcode.trim()).firstOrNull {
                        it.str("barcode").equals(barcode.trim(), true) || it.str("name").equals(barcode.trim(), true)
                    }
                    if (hit != null) { addProduct(hit); barcode = "" } else error = "No product matches \"$barcode\""
                }, Icons.Default.Add, BtnKind.GHOST)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().horizontalScrollCompat(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                categories.forEach { c ->
                    val active = c == category
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .background(if (active) Da.Green.copy(alpha = 0.18f) else Da.Surface2)
                            .border(1.dp, if (active) Da.Green.copy(alpha = 0.56f) else Da.Outline, RoundedCornerShape(8.dp))
                            .clickable { category = c }
                            .padding(horizontal = 11.dp, vertical = 7.dp)
                    ) { Text(c, fontSize = 11.5.sp, color = if (active) Da.Green else Da.Muted, fontWeight = FontWeight.SemiBold) }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (products.isEmpty()) {
                CardBox(Modifier.fillMaxWidth()) {
                    EmptyState("No products match. Add products in Stock, or load the sample shop in Data.")
                    Btn("Open stock", { app.go("stock") }, Icons.Default.Inventory2, BtnKind.GHOST)
                }
            } else {
                LazyVerticalGrid(GridCells.Adaptive(158.dp), Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(products, key = { it.str("id") }) { p ->
                        val out = p.dbl("stock_qty") <= 0
                        Column(
                            Modifier.clip(RoundedCornerShape(12.dp)).background(Da.Surface)
                                .border(1.dp, Da.Outline, RoundedCornerShape(12.dp))
                                .clickable { addProduct(p) }
                                .padding(12.dp).height(104.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                Text(p.str("name"), fontSize = 12.5.sp, color = Da.Text, fontWeight = FontWeight.SemiBold,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                if (p.bool("is_favourite")) Text("★", fontSize = 11.sp, color = Da.Amber)
                            }
                            Column {
                                Text(Fmt.money(p.dbl("retail_price")), fontSize = 13.sp, color = Da.Green, fontWeight = FontWeight.Bold)
                                Text(
                                    if (out) "Out of stock" else "${Fmt.qty(p.dbl("stock_qty"))} ${p.str("unit")} left",
                                    fontSize = 10.sp, color = if (out) Da.Red else Da.Dim
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Cart
        Column(
            Modifier.width(390.dp).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(Da.Surface)
                .border(1.dp, Da.Outline, RoundedCornerShape(16.dp))
        ) {
            Row(Modifier.fillMaxWidth().background(Da.Header).padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Current sale", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Da.Text, modifier = Modifier.weight(1f))
                if (cart.isNotEmpty()) Text("clear", fontSize = 11.sp, color = Da.Muted, modifier = Modifier.clickable { cart.clear() })
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                if (cart.isEmpty()) EmptyState("Tap a product to start the sale.")
                cart.forEachIndexed { i, line ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(line.name, fontSize = 12.sp, color = Da.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Fmt.money(line.unitPrice) + " each", fontSize = 10.sp, color = Da.Dim)
                        }
                        QtyStepper(line.qty, onMinus = {
                            if (line.qty <= 1) cart.removeAt(i) else cart[i] = line.copy(qty = line.qty - 1)
                        }, onPlus = { cart[i] = line.copy(qty = line.qty + 1) })
                        Spacer(Modifier.width(8.dp))
                        Text(Fmt.money(line.lineTotal), fontSize = 12.sp, color = Da.Green, fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(78.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.Close, null, tint = Da.Dim, modifier = Modifier.size(14.dp).clickable { cart.removeAt(i) })
                    }
                }
            }
            Column(Modifier.fillMaxWidth().background(Da.Surface2).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Drop("Customer", listOf("Walk-in") + customers.map { it.str("name") },
                        customers.firstOrNull { it.str("id") == customerId }?.str("name") ?: "Walk-in",
                        { name ->
                            customerId = customers.firstOrNull { it.str("name") == name }?.str("id") ?: ""
                        }, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.width(118.dp)) {
                        Drop("Price", listOf("RETAIL", "WHOLESALE"), saleType, { saleType = it })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    Box(Modifier.weight(1f)) { MoneyField("Discount", discount, { discount = it.filter { c -> c.isDigit() || c == '.' } }) }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) { MoneyField("Amount paid", amountPaid, { amountPaid = it.filter { c -> c.isDigit() || c == '.' } }) }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("CASH" to "Cash", "MTN_MOMO" to "MTN", "AIRTEL_MONEY" to "Airtel", "CREDIT" to "Credit").forEach { (key, label) ->
                        val active = method == key
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                .background(if (active) Da.Green.copy(alpha = 0.2f) else Da.Surface)
                                .border(1.dp, if (active) Da.Green else Da.Outline, RoundedCornerShape(8.dp))
                                .clickable {
                                    method = key
                                    if (key == "CREDIT" && amountPaid.isBlank()) amountPaid = "0"
                                }.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) { Text(label, fontSize = 11.sp, color = if (active) Da.Green else Da.Muted, fontWeight = FontWeight.SemiBold) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text("Subtotal", fontSize = 11.5.sp, color = Da.Muted, modifier = Modifier.weight(1f))
                    Text(Fmt.money(subtotal), fontSize = 11.5.sp, color = Da.Text)
                }
                if (discountValue > 0) Row(Modifier.fillMaxWidth()) {
                    Text("Discount", fontSize = 11.5.sp, color = Da.Muted, modifier = Modifier.weight(1f))
                    Text("-" + Fmt.money(discountValue), fontSize = 11.5.sp, color = Da.Amber)
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("TOTAL", fontSize = 13.sp, color = Da.Text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(Fmt.money(total), fontSize = 20.sp, color = Da.Green, fontWeight = FontWeight.Bold)
                }
                if (method != "CREDIT" && (amountPaid.toDoubleOrNull() ?: 0.0) > total) {
                    Text("Change: ${Fmt.money((amountPaid.toDoubleOrNull() ?: 0.0) - total)}", fontSize = 11.sp, color = Da.Blue)
                }
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontSize = 11.sp, color = Da.Red)
                }
                Spacer(Modifier.height(10.dp))
                Btn(
                    if (cart.isEmpty()) "Add items to sell" else "Complete sale · ${Fmt.money(total)}",
                    {
                        error = null
                        try {
                            if (method == "CREDIT" && customerId.isBlank()) error = "Choose the customer who is taking credit"
                            else {
                                val paid = if (method == "CREDIT") (amountPaid.toDoubleOrNull() ?: 0.0)
                                else (amountPaid.toDoubleOrNull() ?: total)
                                val res = repo.checkout(
                                    lines = cart.toList(), customerId = customerId.ifBlank { null },
                                    saleType = saleType, amountPaid = paid, method = method,
                                    discount = discountValue, userId = app.user?.str("id") ?: "u-owner"
                                )
                                cart.clear(); discount = ""; amountPaid = ""; customerId = ""
                                receiptToShow = res
                                app.refresh()
                            }
                        } catch (e: Exception) {
                            error = e.message
                        }
                    },
                    Icons.Default.CheckCircle,
                    BtnKind.PRIMARY,
                    enabled = cart.isNotEmpty()
                )
                Spacer(Modifier.height(6.dp))
                val held = repo.heldSales()
                if (held.isNotEmpty()) {
                    Text("${held.size} parked sale${if (held.size == 1) "" else "s"} waiting",
                        fontSize = 11.sp, color = Da.Amber, modifier = Modifier.clickable { showHeld = true }.padding(4.dp))
                }
            }
        }
    }

    receiptToShow?.let { res ->
        ReceiptDialog(app, res, onClose = { receiptToShow = null; app.go("dashboard") })
    }

    if (showHeld) {
        HeldDialog(app, onClose = { showHeld = false }) { lines, disc ->
            cart.clear(); cart.addAll(lines); discount = if (disc > 0) disc.toString() else ""
            showHeld = false
        }
    }
}

@Composable
private fun QtyStepper(qty: Double, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(Da.Surface)
                .clickable { onMinus() }, contentAlignment = Alignment.Center
        ) { Text("−", fontSize = 12.sp, color = Da.Muted) }
        Text(Fmt.qty(qty), fontSize = 12.sp, color = Da.Text, modifier = Modifier.width(30.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Box(
            Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(Da.Surface)
                .clickable { onPlus() }, contentAlignment = Alignment.Center
        ) { Text("+", fontSize = 12.sp, color = Da.Muted) }
    }
}

@Composable
private fun ReceiptDialog(app: AppState, res: Repo.CheckoutResult, onClose: () -> Unit) {
    val sale = app.repo.sale(res.saleId)
    val items = app.repo.saleItems(res.saleId)
    var savedPath by remember { mutableStateOf<String?>(null) }
    DialogBox("Sale complete", onClose, 460.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Da.Green.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.CheckCircle, null, tint = Da.Green, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("${Fmt.money(res.total)} received", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                Text("Receipt ${res.receiptNo}" + if (res.change > 0) " · change ${Fmt.money(res.change)}" else "",
                    fontSize = 11.5.sp, color = Da.Muted)
            }
        }
        Spacer(Modifier.height(14.dp))
        CardBox(Modifier.fillMaxWidth(), background = Da.Surface2) {
            items.forEach { i ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text("${Fmt.qty(i.dbl("qty"))} × ${i.str("name")}", fontSize = 12.sp, color = Da.Text, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(Fmt.money(i.dbl("line_total")), fontSize = 12.sp, color = Da.Muted)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                Text("Total", fontSize = 12.sp, color = Da.Muted, modifier = Modifier.weight(1f))
                Text(Fmt.money(sale?.dbl("total") ?: res.total), fontSize = 12.5.sp, color = Da.Green, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(14.dp))
        savedPath?.let {
            Text("Saved: $it", fontSize = 11.sp, color = Da.Blue)
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Save PDF receipt", {
                val dir = File(System.getProperty("user.home"), "Documents/DeryAccount/receipts")
                savedPath = Pdf.receipt(app.repo, res.saleId, File(dir, "${res.receiptNo}.pdf")).absolutePath
                app.say("Receipt saved")
            }, Icons.Default.PictureAsPdf, BtnKind.PRIMARY)
            Btn("Next sale", { onClose() }, Icons.Default.Add, BtnKind.GHOST)
        }
    }
}

@Composable
private fun HeldDialog(app: AppState, onClose: () -> Unit, onRestore: (List<Repo.CartLine>, Double) -> Unit) {
    val held = remember { app.repo.heldSales() }
    DialogBox("Parked sales", onClose, 520.dp) {
        if (held.isEmpty()) EmptyState("No parked sales right now.")
        held.forEach { h ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(h.str("note"), fontSize = 12.5.sp, color = Da.Text, maxLines = 1)
                    Text(Fmt.dateTime(h.str("created_at")), fontSize = 10.5.sp, color = Da.Dim)
                }
                Btn("Restore", {
                    onRestore(app.repo.heldLines(h), h.dbl("discount"))
                    app.repo.dropHeld(h.str("id"))
                    app.refresh()
                }, Icons.Default.Restore, BtnKind.GHOST)
                Spacer(Modifier.width(6.dp))
                Btn("Discard", {
                    app.repo.dropHeld(h.str("id")); app.refresh(); onClose()
                }, Icons.Default.Delete, BtnKind.DANGER)
            }
        }
    }
}

@Composable
private fun Modifier.horizontalScrollCompat(): Modifier =
    this.horizontalScroll(rememberScrollState())
