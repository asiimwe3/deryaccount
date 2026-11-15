package com.derycode.deryaccount.desktop.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.desktop.data.*
import com.derycode.deryaccount.desktop.ui.*
import java.io.File

@Composable
fun SalesScreen(app: AppState) {
    val repo = app.repo
    var search by remember { mutableStateOf("") }
    var sales by remember { mutableStateOf(repo.sales(search = "")) }
    var selected by remember { mutableStateOf<Row?>(null) }

    LaunchedEffect(app.tick, search) { sales = repo.sales(search = search) }

    val today = sales.filter { it.str("sold_at").startsWith(Clock.today()) }
    val month = sales.filter { it.str("sold_at").startsWith(Clock.thisMonth()) }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        ScreenHeader("Sales register", "Every receipt, with its own audit trail") {
            Btn("New sale", { app.go("pos") }, Icons.Default.PointOfSale)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCard("Today", Fmt.money(today.sumOf { it.dbl("total") }), "${today.size} receipts", Da.Green, Modifier.weight(1f))
            KpiCard("This month", Fmt.money(month.sumOf { it.dbl("total") }), "${month.size} receipts", Da.Blue, Modifier.weight(1f))
            KpiCard("Average receipt", Fmt.money(if (month.isEmpty()) 0.0 else month.sumOf { it.dbl("total") } / month.size),
                "this month", Da.Amber, Modifier.weight(1f))
            KpiCard("On credit", Fmt.money(month.filter { it.str("payment_method") == "CREDIT" }.sumOf { it.dbl("total") }),
                "in the debtor book", Da.Red, Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        CardBox(Modifier.weight(1f), padding = 0.dp) {
            Row(Modifier.fillMaxWidth().padding(12.dp)) {
                Box(Modifier.weight(1f)) { SearchBar(search, { search = it }, "Receipt number or customer") }
            }
            Table(
                listOf("Receipt", "Date", "Customer", "Cashier", "Method", "Total"),
                listOf(1.2f, 1.3f, 1.6f, 1f, 0.9f, 1f),
                sales.map { s ->
                    listOf(
                        Cell(s.str("receipt_no"), Da.Muted),
                        Cell(Fmt.dateTime(s.str("sold_at")), Da.Dim),
                        Cell(s.str("customer_name")),
                        Cell(s.str("cashier"), Da.Muted),
                        Cell(methodLabel(s.str("payment_method")), Da.Blue),
                        Cell(Fmt.money(s.dbl("total")), Da.Green, bold = true, align = androidx.compose.ui.text.style.TextAlign.End)
                    )
                },
                onRowClick = { i -> selected = sales[i] }
            )
        }
    }

    selected?.let { sale ->
        SaleDetailDialog(app, sale, onClose = { selected = null })
    }
}

@Composable
private fun SaleDetailDialog(app: AppState, sale: Row, onClose: () -> Unit) {
    val repo = app.repo
    val items = remember(sale.str("id")) { repo.saleItems(sale.str("id")) }
    var mode by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    DialogBox("Receipt ${sale.str("receipt_no")}", onClose, 620.dp) {
        FormRow("Date", Fmt.dateTime(sale.str("sold_at")))
        FormRow("Customer", sale.str("customer_name"))
        FormRow("Cashier", sale.str("cashier"))
        FormRow("Payment", methodLabel(sale.str("payment_method")))
        Spacer(Modifier.height(10.dp))
        CardBox(Modifier.fillMaxWidth(), background = Da.Surface2, padding = 12.dp) {
            Table(
                listOf("Item", "Qty", "Price", "Total"),
                listOf(2.4f, 0.7f, 1f, 1f),
                items.map {
                    listOf(
                        Cell(it.str("name")), Cell(Fmt.qty(it.dbl("qty")), Da.Muted),
                        Cell(Fmt.money(it.dbl("unit_price")), Da.Muted),
                        Cell(Fmt.money(it.dbl("line_total")), Da.Text, align = androidx.compose.ui.text.style.TextAlign.End)
                    )
                }
            )
        }
        Spacer(Modifier.height(10.dp))
        FormRow("Subtotal", Fmt.money(sale.dbl("subtotal")))
        if (sale.dbl("discount") > 0) FormRow("Discount", "-" + Fmt.money(sale.dbl("discount")))
        FormRow("Total", Fmt.money(sale.dbl("total")), emphasize = true)
        FormRow("Paid", Fmt.money(sale.dbl("amount_paid")))
        if (sale.dbl("change_given") > 0) FormRow("Change", Fmt.money(sale.dbl("change_given")))

        Spacer(Modifier.height(14.dp))
        if (mode.isEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Receipt PDF", {
                    val dir = File(System.getProperty("user.home"), "Documents/DeryAccount/receipts")
                    val f = Pdf.receipt(repo, sale.str("id"), File(dir, "${sale.str("receipt_no")}.pdf"))
                    app.say("Saved ${f.name}")
                }, Icons.Default.PictureAsPdf, BtnKind.PRIMARY)
                Btn("Return / refund", { mode = "return" }, Icons.Default.Undo, BtnKind.GHOST)
            }
        } else {
            Text("Customer return", fontSize = 13.sp, color = Da.Text, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row {
                Box(Modifier.weight(1f)) { MoneyField("Refund amount", amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }) }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    var method by remember { mutableStateOf("CASH") }
                    Drop("Refund via", listOf("CASH", "MTN_MOMO", "AIRTEL_MONEY", "CREDIT"), method, {
                        method = it
                        AppStateHolder.refundMethod = it
                    })
                }
            }
            Spacer(Modifier.height(8.dp))
            Field("Reason / note", note, { note = it })
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Post return", {
                    try {
                        repo.returnSale(sale.str("id"), amount.toDoubleOrNull() ?: 0.0,
                            AppStateHolder.refundMethod, note)
                        app.refresh(); app.say("Return posted, goods back in stock"); onClose()
                    } catch (e: Exception) { app.say(e.message ?: "Could not post the return") }
                }, Icons.Default.Check, BtnKind.DANGER)
                Btn("Cancel", { mode = "" }, kind = BtnKind.GHOST)
            }
        }
    }
}

/** Tiny holder so the refund method chosen in the dropdown is readable at posting time. */
object AppStateHolder {
    var refundMethod: String = "CASH"
}
