package com.derycode.deryaccount.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.desktop.data.*
import com.derycode.deryaccount.desktop.ui.*

@Composable
fun PurchasesScreen(app: AppState) {
    val repo = app.repo
    var tab by remember { mutableStateOf(0) }
    var showPurchase by remember { mutableStateOf(false) }
    var showOrder by remember { mutableStateOf(false) }
    var showReturn by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        ScreenHeader("Purchases & suppliers' goods", "Deliveries, orders you are waiting for, and goods sent back") {
            when (tab) {
                0 -> Btn("Record purchase", { showPurchase = true }, Icons.Default.AddShoppingCart)
                1 -> Btn("New purchase order", { showOrder = true }, Icons.Default.PostAdd)
                else -> Btn("Return to supplier", { showReturn = true }, Icons.Default.Undo)
            }
        }
        TabBar(listOf("Purchases", "Purchase orders", "Returns to supplier"), tab, { tab = it })
        Spacer(Modifier.height(14.dp))
        when (tab) {
            0 -> {
                val rows = remember(app.tick) { repo.purchases() }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KpiCard("Purchases this month",
                        Fmt.money(rows.filter { it.str("received_at").startsWith(Clock.thisMonth()) }.sumOf { it.dbl("total") }),
                        "${rows.count { it.str("received_at").startsWith(Clock.thisMonth()) }} deliveries", Da.Blue, Modifier.weight(1f))
                    KpiCard("Unpaid", Fmt.money(rows.sumOf { Fmt.r2(it.dbl("total") - it.dbl("paid_amount")) }),
                        "still owed to suppliers", Da.Red, Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
                CardBox(Modifier.weight(1f), padding = 0.dp) {
                    Table(
                        listOf("Date", "Supplier", "Total", "Paid", "Balance", "Note"),
                        listOf(1.2f, 1.8f, 1f, 1f, 1f, 1.6f),
                        rows.map {
                            val bal = Fmt.r2(it.dbl("total") - it.dbl("paid_amount"))
                            listOf(
                                Cell(Fmt.date(it.str("received_at")), Da.Dim),
                                Cell(it.str("supplier_name"), bold = true),
                                Cell(Fmt.money(it.dbl("total")), Da.Text, align = TextAlign.End),
                                Cell(Fmt.money(it.dbl("paid_amount")), Da.Green, align = TextAlign.End),
                                Cell(Fmt.money(bal), if (bal > 0) Da.Red else Da.Dim, align = TextAlign.End),
                                Cell(it.str("note"), Da.Muted)
                            )
                        }
                    )
                }
            }
            1 -> {
                val rows = remember(app.tick) { repo.purchaseOrders() }
                CardBox(Modifier.fillMaxSize(), padding = 0.dp) {
                    Table(
                        listOf("Order", "Supplier", "Lines", "Value", "Expected", "Status", "Action"),
                        listOf(1.3f, 1.6f, 0.6f, 1f, 1.1f, 0.9f, 1.4f),
                        rows.map { o ->
                            listOf(
                                Cell(o.str("po_no"), bold = true),
                                Cell(o.str("supplier_name"), Da.Muted),
                                Cell(o.int("lines").toString(), Da.Dim, align = TextAlign.End),
                                Cell(Fmt.money(o.dbl("value")), Da.Text, align = TextAlign.End),
                                Cell(if (o.str("expected_at").isBlank()) "—" else Fmt.date(o.str("expected_at")), Da.Dim),
                                Cell(o.str("status"), when (o.str("status")) {
                                    "OPEN" -> Da.Amber; "RECEIVED" -> Da.Green; else -> Da.Dim
                                }),
                                Cell(
                                    when (o.str("status")) {
                                        "OPEN" -> "Receive now"
                                        else -> "—"
                                    },
                                    Da.Green
                                )
                            )
                        },
                        onRowClick = { i ->
                            val o = rows[i]
                            if (o.str("status") == "OPEN") {
                                repo.receiveOrder(o.str("id")); app.refresh(); app.say("Order ${o.str("po_no")} received into stock")
                            }
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("Click an open order to receive it: stock, supplier balance and the books update together.",
                    fontSize = 11.sp, color = Da.Dim)
            }
            else -> {
                val rows = remember(app.tick) { repo.purchaseReturns() }
                CardBox(Modifier.fillMaxSize(), padding = 0.dp) {
                    Table(
                        listOf("Return", "Date", "Supplier", "Amount", "Refund", "Note"),
                        listOf(1.2f, 1.1f, 1.6f, 1f, 1.1f, 1.6f),
                        rows.map {
                            listOf(
                                Cell(it.str("pr_no"), bold = true),
                                Cell(Fmt.date(it.str("returned_at")), Da.Dim),
                                Cell(it.str("supplier_name"), Da.Muted),
                                Cell(Fmt.money(it.dbl("total")), Da.Red, align = TextAlign.End),
                                Cell(if (it.str("refund_method") == "CASH") "Cash back" else "Supplier credit", Da.Blue),
                                Cell(it.str("note"), Da.Muted)
                            )
                        }
                    )
                }
            }
        }
    }

    if (showPurchase) PurchaseDialog(app, onClose = { showPurchase = false })
    if (showOrder) PurchaseOrderDialog(app, onClose = { showOrder = false })
    if (showReturn) PurchaseReturnDialog(app, onClose = { showReturn = false })
}

/** Add-a-line editor shared by purchases, orders and returns. */
@Composable
private fun LineEditor(
    products: List<Row>,
    lines: List<Repo.CartLine>,
    priceLabel: String,
    onChange: (List<Repo.CartLine>) -> Unit
) {
    var productName by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(2f)) { Drop("Product", products.map { it.str("name") }, productName, {
                productName = it
                products.firstOrNull { p -> p.str("name") == it }?.let { p -> price = p.dbl("cost_price").toString() }
            }) }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(0.8f)) { MoneyField("Qty", qty, { qty = it.filter { c -> c.isDigit() || c == '.' } }) }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) { MoneyField(priceLabel, price, { price = it.filter { c -> c.isDigit() || c == '.' } }) }
            Spacer(Modifier.width(8.dp))
            Btn("Add", {
                val p = products.firstOrNull { it.str("name") == productName } ?: return@Btn
                val q = qty.toDoubleOrNull() ?: return@Btn
                val cost = price.toDoubleOrNull() ?: p.dbl("cost_price")
                onChange(lines + Repo.CartLine(p.str("id"), p.str("name"), q, cost, cost))
                qty = ""
            }, Icons.Default.Add, BtnKind.SUBTLE)
        }
        Spacer(Modifier.height(10.dp))
        lines.forEachIndexed { i, l ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Da.Surface2)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(l.name, fontSize = 12.sp, color = Da.Text, modifier = Modifier.weight(1f))
                Text("${Fmt.qty(l.qty)} × ${Fmt.money(l.unitPrice)}", fontSize = 11.5.sp, color = Da.Muted)
                Spacer(Modifier.width(12.dp))
                Text(Fmt.money(l.qty * l.unitPrice), fontSize = 12.sp, color = Da.Green, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.Close, null, tint = Da.Dim,
                    modifier = Modifier.size(14.dp).clickable { onChange(lines.filterIndexed { idx, _ -> idx != i }) })
            }
            Spacer(Modifier.height(4.dp))
        }
        val total = lines.sumOf { it.qty * it.unitPrice }
        if (lines.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                Text("Total", fontSize = 12.sp, color = Da.Muted, modifier = Modifier.weight(1f))
                Text(Fmt.money(total), fontSize = 13.sp, color = Da.Green, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PurchaseDialog(app: AppState, onClose: () -> Unit) {
    val products = remember { app.repo.products() }
    val suppliers = remember { app.repo.suppliers() }
    var supplier by remember { mutableStateOf("Cash purchase") }
    var lines by remember { mutableStateOf<List<Repo.CartLine>>(emptyList()) }
    var paid by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    DialogBox("Record a purchase", onClose, 720.dp) {
        Drop("Supplier", listOf("Cash purchase") + suppliers.map { it.str("name") }, supplier, { supplier = it })
        Spacer(Modifier.height(10.dp))
        LineEditor(products, lines, "Unit cost") { lines = it }
        Spacer(Modifier.height(12.dp))
        Row {
            Box(Modifier.weight(1f)) { MoneyField("Amount paid now", paid, { paid = it.filter { c -> c.isDigit() || c == '.' } }) }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) { Field("Note", note, { note = it }, placeholder = "delivery note number") }
        }
        Spacer(Modifier.height(8.dp))
        Text("Leave the paid amount empty to record the whole purchase on supplier credit.",
            fontSize = 11.sp, color = Da.Dim)
        Spacer(Modifier.height(16.dp))
        Btn("Save purchase", {
            try {
                val supplierId = suppliers.firstOrNull { it.str("name") == supplier }?.str("id")
                val total = lines.sumOf { it.qty * it.unitPrice }
                app.repo.recordPurchase(supplierId, lines, paid.toDoubleOrNull() ?: total, note)
                app.refresh(); app.say("Purchase recorded, stock updated"); onClose()
            } catch (e: Exception) { app.say(e.message ?: "Could not save") }
        }, Icons.Default.Save, BtnKind.PRIMARY, enabled = lines.isNotEmpty())
    }
}

@Composable
private fun PurchaseOrderDialog(app: AppState, onClose: () -> Unit) {
    val products = remember { app.repo.products() }
    val suppliers = remember { app.repo.suppliers() }
    var supplier by remember { mutableStateOf(suppliers.firstOrNull()?.str("name") ?: "") }
    var lines by remember { mutableStateOf<List<Repo.CartLine>>(emptyList()) }
    var expected by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    DialogBox("New purchase order", onClose, 720.dp) {
        Drop("Supplier", suppliers.map { it.str("name") }, supplier, { supplier = it })
        Spacer(Modifier.height(10.dp))
        LineEditor(products, lines, "Unit cost") { lines = it }
        Spacer(Modifier.height(12.dp))
        Row {
            Box(Modifier.weight(1f)) { Field("Expected date (yyyy-mm-dd)", expected, { expected = it }) }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) { Field("Note", note, { note = it }) }
        }
        Spacer(Modifier.height(16.dp))
        Btn("Create order", {
            try {
                app.repo.createPurchaseOrder(
                    suppliers.firstOrNull { it.str("name") == supplier }?.str("id"),
                    lines, expected.ifBlank { null }, note
                )
                app.refresh(); app.say("Purchase order created"); onClose()
            } catch (e: Exception) { app.say(e.message ?: "Could not save") }
        }, Icons.Default.Save, BtnKind.PRIMARY, enabled = lines.isNotEmpty())
    }
}

@Composable
private fun PurchaseReturnDialog(app: AppState, onClose: () -> Unit) {
    val products = remember { app.repo.products() }
    val suppliers = remember { app.repo.suppliers() }
    var supplier by remember { mutableStateOf(suppliers.firstOrNull()?.str("name") ?: "") }
    var product by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var refund by remember { mutableStateOf("SUPPLIER_CREDIT") }
    var note by remember { mutableStateOf("") }

    DialogBox("Return goods to a supplier", onClose, 560.dp) {
        Drop("Supplier", suppliers.map { it.str("name") }, supplier, { supplier = it })
        Spacer(Modifier.height(10.dp))
        Drop("Product", products.map { it.str("name") }, product, { name ->
            product = name
            products.firstOrNull { it.str("name") == name }?.let { cost = it.dbl("cost_price").toString() }
        })
        Spacer(Modifier.height(10.dp))
        Row {
            Box(Modifier.weight(1f)) { MoneyField("Quantity", qty, { qty = it.filter { c -> c.isDigit() || c == '.' } }) }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) { MoneyField("Unit cost", cost, { cost = it.filter { c -> c.isDigit() || c == '.' } }) }
        }
        Spacer(Modifier.height(10.dp))
        Drop("Refund as", listOf("SUPPLIER_CREDIT", "CASH"), refund, { refund = it })
        Spacer(Modifier.height(10.dp))
        Field("Reason", note, { note = it })
        Spacer(Modifier.height(16.dp))
        Btn("Post return", {
            val p = products.firstOrNull { it.str("name") == product }
            if (p == null) { app.say("Choose a product"); return@Btn }
            try {
                app.repo.recordPurchaseReturn(
                    suppliers.firstOrNull { it.str("name") == supplier }?.str("id"),
                    p.str("id"), qty.toDoubleOrNull() ?: 0.0,
                    cost.toDoubleOrNull() ?: p.dbl("cost_price"), refund, note
                )
                app.refresh(); app.say("Return posted"); onClose()
            } catch (e: Exception) { app.say(e.message ?: "Could not post") }
        }, Icons.Default.Save, BtnKind.PRIMARY)
    }
}
