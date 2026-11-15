package com.derycode.deryaccount.desktop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.desktop.data.*
import com.derycode.deryaccount.desktop.ui.*
import java.io.File

@Composable
fun ReportsScreen(app: AppState) {
    val reports = app.reports
    var tab by remember { mutableStateOf(0) }
    var from by remember { mutableStateOf(Clock.monthStart()) }
    var to by remember { mutableStateOf(Clock.today()) }
    var deadDays by remember { mutableStateOf("60") }
    val fromIso = "${from}T00:00:00"
    val toIso = "${to}T23:59:59"

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        ScreenHeader("Reports & analytics", "What sells, what sits, what it earns") {
            Btn("Export PDF", {
                val (title, headers, widths, rows) = when (tab) {
                    0 -> ExportData("Sales by day", listOf("Day", "Sales"),
                        listOf(1f, 1f), reports.salesByDay(30).map { listOf(Fmt.dateShort(it.first), Fmt.money(it.second)) })
                    1 -> ExportData("Best sellers", listOf("Product", "Qty", "Revenue", "Profit"),
                        listOf(2.2f, 0.8f, 1.2f, 1.2f),
                        reports.bestSellers(fromIso, toIso, 40).map {
                            listOf(it.name, Fmt.qty(it.qty), Fmt.money(it.revenue), Fmt.money(it.profit))
                        })
                    2 -> ExportData("Profit by product", listOf("Product", "Qty", "Revenue", "Profit"),
                        listOf(2.2f, 0.8f, 1.2f, 1.2f),
                        reports.bestSellers(fromIso, toIso, 100).map {
                            listOf(it.name, Fmt.qty(it.qty), Fmt.money(it.revenue), Fmt.money(it.profit))
                        })
                    3 -> ExportData("Profit by category", listOf("Category", "Qty", "Revenue", "Profit"),
                        listOf(2f, 0.8f, 1.2f, 1.2f),
                        reports.profitByCategory(fromIso, toIso).map {
                            listOf(it.name, Fmt.qty(it.qty), Fmt.money(it.revenue), Fmt.money(it.profit))
                        })
                    4 -> ExportData("Dead stock", listOf("Product", "Stock", "Value", "Last sold"),
                        listOf(2.2f, 0.8f, 1.2f, 1.2f),
                        reports.deadStock(deadDays.toIntOrNull() ?: 60, 200).map {
                            listOf(it.str("name"), Fmt.qty(it.dbl("stock_qty")),
                                Fmt.money(it.dbl("stock_qty") * it.dbl("cost_price")),
                                if (it.str("last_sold").isBlank()) "never" else Fmt.date(it.str("last_sold")))
                        })
                    5 -> ExportData("Stock valuation", listOf("Product", "Qty", "Cost", "Value at cost", "Retail value"),
                        listOf(2f, 0.7f, 1f, 1.2f, 1.2f),
                        app.repo.products().map {
                            listOf(it.str("name"), Fmt.qty(it.dbl("stock_qty")), Fmt.money(it.dbl("cost_price")),
                                Fmt.money(it.dbl("stock_qty") * it.dbl("cost_price")),
                                Fmt.money(it.dbl("stock_qty") * it.dbl("retail_price")))
                        })
                    6 -> ExportData("Branch comparison", listOf("Branch", "Receipts", "Revenue"),
                        listOf(2f, 1f, 1.4f),
                        reports.branchComparison().map { listOf(it.str("branch"), it.int("sales").toString(), Fmt.money(it.dbl("revenue"))) })
                    else -> ExportData("Aging", listOf("Name", "Balance"),
                        listOf(2.5f, 1.2f),
                        reports.customerAging().map { listOf(it.str("name"), Fmt.money(it.dbl("balance"))) })
                }
                val dir = File(System.getProperty("user.home"), "Documents/DeryAccount/reports")
                val f = Pdf.statement(title, "Period $from to $to", app.repo.businessName(),
                    rows, headers, widths, target = File(dir, "$title.pdf"))
                app.say("Saved ${f.name}")
            }, Icons.Default.PictureAsPdf, BtnKind.GHOST)
        }
        TabBar(listOf("Sales chart", "Best sellers", "By product", "By category", "Dead stock", "Stock value", "Branches", "Aging"),
            tab, { tab = it })
        if (tab in 1..3) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                Box(Modifier.width(140.dp)) { Field("From", from, { from = it }) }
                Box(Modifier.width(140.dp)) { Field("To", to, { to = it }) }
                Btn("This month", { from = Clock.monthStart(); to = Clock.today() }, kind = BtnKind.GHOST)
                Btn("Last 30 days", { from = java.time.LocalDate.now().minusDays(29).toString(); to = Clock.today() }, kind = BtnKind.GHOST)
            }
        }
        Spacer(Modifier.height(14.dp))

        when (tab) {
            0 -> {
                val daily = remember(app.tick) { reports.salesByDay(30) }
                val monthly = remember(app.tick) { reports.salesByMonth(12) }
                Column(Modifier.fillMaxSize()) {
                    CardBox(Modifier.fillMaxWidth()) {
                        Text("Daily sales, last 30 days", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                        Spacer(Modifier.height(10.dp))
                        BarChart(daily.map { Fmt.dateShort(it.first).take(5) to it.second }, barHeight = 170.dp, labelEvery = 3)
                    }
                    Spacer(Modifier.height(12.dp))
                    CardBox(Modifier.fillMaxWidth()) {
                        Text("Monthly sales, last 12 months", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                        Spacer(Modifier.height(10.dp))
                        BarChart(monthly.map { Fmt.month(it.first).take(3) + " " + it.first.take(4).takeLast(2) to it.second },
                            color = Da.Blue, barHeight = 150.dp)
                    }
                }
            }
            1 -> {
                val rows = remember(app.tick, from, to) { reports.bestSellers(fromIso, toIso, 40) }
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CardBox(Modifier.weight(2f), padding = 0.dp) {
                        Table(
                            listOf("Product", "Units", "Revenue", "Profit", "Margin"),
                            listOf(2.4f, 0.8f, 1.2f, 1.2f, 0.9f),
                            rows.map {
                                listOf(
                                    Cell(it.name, bold = true),
                                    Cell(Fmt.qty(it.qty), Da.Muted, align = TextAlign.End),
                                    Cell(Fmt.money(it.revenue), Da.Text, align = TextAlign.End),
                                    Cell(Fmt.money(it.profit), Da.Green, align = TextAlign.End),
                                    Cell(if (it.revenue > 0) Fmt.pct(it.profit / it.revenue * 100) else "—", Da.Blue, align = TextAlign.End)
                                )
                            }
                        )
                    }
                    CardBox(Modifier.weight(1f)) {
                        Text("Revenue by product", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                        Spacer(Modifier.height(10.dp))
                        BarChart(rows.take(8).map { it.name.take(6) to it.revenue }, barHeight = 140.dp, color = Da.Green)
                    }
                }
            }
            2 -> {
                val rows = remember(app.tick, from, to) { reports.bestSellers(fromIso, toIso, 200) }
                CardBox(Modifier.fillMaxSize(), padding = 0.dp) {
                    Table(
                        listOf("Product", "Units", "Revenue", "Profit", "Margin"),
                        listOf(2.6f, 0.8f, 1.2f, 1.2f, 0.9f),
                        rows.map {
                            listOf(
                                Cell(it.name, bold = true),
                                Cell(Fmt.qty(it.qty), Da.Muted, align = TextAlign.End),
                                Cell(Fmt.money(it.revenue), Da.Text, align = TextAlign.End),
                                Cell(Fmt.money(it.profit), Da.Green, align = TextAlign.End),
                                Cell(if (it.revenue > 0) Fmt.pct(it.profit / it.revenue * 100) else "—", Da.Blue, align = TextAlign.End)
                            )
                        }
                    )
                }
            }
            3 -> {
                val rows = remember(app.tick, from, to) { reports.profitByCategory(fromIso, toIso) }
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CardBox(Modifier.weight(1.4f), padding = 0.dp) {
                        Table(
                            listOf("Category", "Units", "Revenue", "Profit"),
                            listOf(2f, 0.8f, 1.2f, 1.2f),
                            rows.map {
                                listOf(
                                    Cell(it.name, bold = true),
                                    Cell(Fmt.qty(it.qty), Da.Muted, align = TextAlign.End),
                                    Cell(Fmt.money(it.revenue), Da.Text, align = TextAlign.End),
                                    Cell(Fmt.money(it.profit), Da.Green, align = TextAlign.End)
                                )
                            }
                        )
                    }
                    CardBox(Modifier.weight(1f)) {
                        Text("Profit by category", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                        Spacer(Modifier.height(10.dp))
                        BarChart(rows.map { it.name.take(10) to it.profit }, barHeight = 150.dp, color = Da.Amber)
                    }
                }
            }
            4 -> {
                var days by remember { mutableStateOf("60") }
                val rows = remember(app.tick, days) { reports.deadStock(days.toIntOrNull() ?: 60, 200) }
                Column(Modifier.fillMaxSize()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                        Box(Modifier.width(220.dp)) { Field("No sale in the last … days", days, { days = it.filter { c -> c.isDigit() } }) }
                        Text("${rows.size} products · ${Fmt.money(rows.sumOf { it.dbl("stock_qty") * it.dbl("cost_price") })} tied up",
                            fontSize = 12.sp, color = Da.Amber, modifier = Modifier.padding(bottom = 12.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    CardBox(Modifier.weight(1f), padding = 0.dp) {
                        Table(
                            listOf("Product", "Stock", "Cost value", "Last sold"),
                            listOf(2.4f, 0.9f, 1.2f, 1.2f),
                            rows.map {
                                listOf(
                                    Cell(it.str("name"), bold = true),
                                    Cell("${Fmt.qty(it.dbl("stock_qty"))} ${it.str("unit")}", Da.Muted, align = TextAlign.End),
                                    Cell(Fmt.money(it.dbl("stock_qty") * it.dbl("cost_price")), Da.Amber, align = TextAlign.End),
                                    Cell(if (it.str("last_sold").isBlank()) "never sold" else Fmt.date(it.str("last_sold")), Da.Dim)
                                )
                            }
                        )
                    }
                }
            }
            5 -> {
                val v = remember(app.tick) { reports.stockValuation() }
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        KpiCard("Units on hand", Fmt.qty(v.units), "across all products", Da.Blue, Modifier.weight(1f))
                        KpiCard("Value at cost", Fmt.money(v.costValue), "what the stock cost you", Da.Green, Modifier.weight(1f))
                        KpiCard("Value at retail", Fmt.money(v.retailValue), "if it all sells at list price", Da.Purple, Modifier.weight(1f))
                        KpiCard("Potential profit", Fmt.money(v.potential), "locked inside the stock", Da.Amber, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    val products = remember(app.tick) { app.repo.products() }
                    CardBox(Modifier.weight(1f), padding = 0.dp) {
                        Table(
                            listOf("Product", "Qty", "Cost", "At cost", "At retail", "Potential"),
                            listOf(2.2f, 0.8f, 1f, 1.1f, 1.1f, 1.1f),
                            products.map {
                                val qty = it.dbl("stock_qty")
                                listOf(
                                    Cell(it.str("name"), bold = true),
                                    Cell(Fmt.qty(qty), Da.Muted, align = TextAlign.End),
                                    Cell(Fmt.money(it.dbl("cost_price")), Da.Dim, align = TextAlign.End),
                                    Cell(Fmt.money(qty * it.dbl("cost_price")), Da.Text, align = TextAlign.End),
                                    Cell(Fmt.money(qty * it.dbl("retail_price")), Da.Blue, align = TextAlign.End),
                                    Cell(Fmt.money(qty * (it.dbl("retail_price") - it.dbl("cost_price"))), Da.Green, align = TextAlign.End)
                                )
                            }
                        )
                    }
                }
            }
            6 -> {
                val rows = remember(app.tick) { reports.branchComparison() }
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CardBox(Modifier.weight(1.5f), padding = 0.dp) {
                        Table(
                            listOf("Branch", "Receipts", "Revenue", "Gross profit"),
                            listOf(2f, 1f, 1.3f, 1.3f),
                            rows.map {
                                listOf(
                                    Cell(it.str("branch"), bold = true),
                                    Cell(it.int("sales").toString(), Da.Muted, align = TextAlign.End),
                                    Cell(Fmt.money(it.dbl("revenue")), Da.Text, align = TextAlign.End),
                                    Cell(Fmt.money(it.dbl("revenue") - it.dbl("cogs")), Da.Green, align = TextAlign.End)
                                )
                            }
                        )
                    }
                    CardBox(Modifier.weight(1f)) {
                        Text("Branches", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                        Spacer(Modifier.height(8.dp))
                        app.repo.branches().forEach { b ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(b.str("name"), fontSize = 12.sp, color = Da.Text, modifier = Modifier.weight(1f))
                                Text(b.str("location"), fontSize = 11.sp, color = Da.Dim)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Add branches in Settings to compare performance.", fontSize = 11.sp, color = Da.Dim)
                    }
                }
            }
            else -> {
                val cust = remember(app.tick) { reports.customerAging() }
                val sup = remember(app.tick) { reports.supplierAging() }
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CardBox(Modifier.weight(1f)) {
                        Text("Customers who owe you", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                        Spacer(Modifier.height(8.dp))
                        Table(
                            listOf("Customer", "0-30", "60+", "Total"),
                            listOf(2f, 0.9f, 0.9f, 1f),
                            cust.map {
                                listOf(
                                    Cell(it.str("name")),
                                    Cell(Fmt.money(it.dbl("d30")), Da.Muted, align = TextAlign.End),
                                    Cell(Fmt.money(it.dbl("d90")), Da.Red, align = TextAlign.End),
                                    Cell(Fmt.money(it.dbl("balance")), Da.Text, bold = true, align = TextAlign.End)
                                )
                            }
                        )
                    }
                    CardBox(Modifier.weight(1f)) {
                        Text("Suppliers you owe", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                        Spacer(Modifier.height(8.dp))
                        Table(
                            listOf("Supplier", "0-30", "60+", "Total"),
                            listOf(2f, 0.9f, 0.9f, 1f),
                            sup.map {
                                listOf(
                                    Cell(it.str("name")),
                                    Cell(Fmt.money(it.dbl("d30")), Da.Muted, align = TextAlign.End),
                                    Cell(Fmt.money(it.dbl("d90")), Da.Red, align = TextAlign.End),
                                    Cell(Fmt.money(it.dbl("balance")), Da.Text, bold = true, align = TextAlign.End)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

data class ExportData(val title: String, val headers: List<String>, val widths: List<Float>, val rows: List<List<String>>)
