package com.derycode.deryaccount.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.desktop.data.*
import com.derycode.deryaccount.desktop.ui.*

@Composable
fun DashboardScreen(app: AppState) {
    val repo = app.repo
    val reports = app.reports
    var kpis by remember { mutableStateOf(reports.kpis()) }
    var daily by remember { mutableStateOf(reports.salesByDay(14)) }
    var top by remember { mutableStateOf(reports.bestSellers(Clock.monthStart() + "T00:00:00", Clock.nowIso(), 5)) }
    var low by remember { mutableStateOf(reports.lowStockList(6)) }
    var recent by remember { mutableStateOf(reports.recentSales(6)) }
    var shift by remember { mutableStateOf(repo.openShiftNow()) }

    LaunchedEffect(app.tick, app.screen) {
        kpis = reports.kpis()
        daily = reports.salesByDay(14)
        top = reports.bestSellers(Clock.monthStart() + "T00:00:00", Clock.nowIso(), 5)
        low = reports.lowStockList(6)
        recent = reports.recentSales(6)
        shift = repo.openShiftNow()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        ScreenHeader(
            "Good ${greeting()}, ${app.userName.split(" ").first()}",
            "${repo.businessName()} · ${Fmt.date(Clock.today())}" +
                if (shift == null) " · no shift open" else " · shift open since ${Fmt.dateTime(shift!!.str("opened_at"))}"
        ) {
            Btn("Add stock", { app.go("stock") }, Icons.Default.AddBox, BtnKind.GHOST)
            Btn("New sale", { app.go("pos") }, Icons.Default.PointOfSale, BtnKind.PRIMARY)
        }

        // ── KPI row 1
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCard("Today's sales", Fmt.money(kpis.todaySales), "${kpis.todayCount} receipts today", Da.Green, Modifier.weight(1f))
            KpiCard("Today's profit", Fmt.money(kpis.todayProfit), "after cost of goods", Da.Blue, Modifier.weight(1f))
            KpiCard("This month", Fmt.money(kpis.monthSales), "expenses ${Fmt.money(kpis.monthExpenses)}", Da.Amber, Modifier.weight(1f))
            KpiCard("Cash on hand", Fmt.money(kpis.cash), "mobile money ${Fmt.money(kpis.bank)}", Da.Purple, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCard("Stock at cost", Fmt.money(kpis.stockValue), "${kpis.products} products · ${kpis.outOfStock} out of stock", Da.Green, Modifier.weight(1f))
            KpiCard("Customers owe you", Fmt.money(kpis.receivables), "${kpis.customers} customers on file", Da.Red, Modifier.weight(1f))
            KpiCard("You owe suppliers", Fmt.money(kpis.payables), "unpaid purchases", Da.Amber, Modifier.weight(1f))
            KpiCard("Month profit", Fmt.money(kpis.monthProfit), "sales less cost and expenses", Da.Blue, Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            CardBox(Modifier.weight(2f)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Sales, last 14 days", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Da.Text, modifier = Modifier.weight(1f))
                    Pill("peak ${Fmt.money(daily.maxOfOrNull { it.second } ?: 0.0)}", Da.Green, filled = true)
                }
                Spacer(Modifier.height(14.dp))
                BarChart(daily.map { Fmt.dateShort(it.first).take(5) to it.second }, labelEvery = 2, barHeight = 150.dp)
            }
            CardBox(Modifier.weight(1f)) {
                Text("Best sellers this month", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                Spacer(Modifier.height(10.dp))
                if (top.isEmpty()) EmptyState("No sales yet. Open Sell / POS to ring up the first sale.")
                top.forEachIndexed { i, p ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .clickable { app.go("reports") }
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(Da.Surface2),
                            contentAlignment = Alignment.Center
                        ) { Text("${i + 1}", fontSize = 10.sp, color = Da.Green, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(8.dp))
                        Text(p.name, fontSize = 12.sp, color = Da.Text, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(Fmt.money(p.revenue), fontSize = 11.sp, color = Da.Muted)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            CardBox(Modifier.weight(2f)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Latest receipts", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Da.Text, modifier = Modifier.weight(1f))
                    Text("Open sales register", fontSize = 11.sp, color = Da.Green,
                        modifier = Modifier.clickable { app.go("sales") })
                }
                Spacer(Modifier.height(8.dp))
                Table(
                    listOf("Receipt", "Customer", "Method", "Amount", "When"),
                    listOf(1.1f, 1.4f, 0.9f, 1f, 1.1f),
                    recent.map { s ->
                        listOf(
                            Cell(s.str("receipt_no"), Da.Muted),
                            Cell(s.str("customer_name")),
                            Cell(methodLabel(s.str("payment_method")), Da.Blue),
                            Cell(Fmt.money(s.dbl("total")), Da.Green, bold = true),
                            Cell(Fmt.date(s.str("sold_at")), Da.Dim)
                        )
                    }
                ,
                    scrollable = false, maxRows = 6)
            }
            CardBox(Modifier.weight(1f)) {
                Text("Needs attention", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                Spacer(Modifier.height(10.dp))
                attentionRow(Icons.Default.Warning, "Out of stock", "${kpis.outOfStock} products", Da.Red) { app.go("stock") }
                attentionRow(Icons.Default.TrendingDown, "Low stock", "${kpis.lowStock} products", Da.Amber) { app.go("stock") }
                attentionRow(Icons.Default.AccountBalanceWallet, "Unpaid customers", Fmt.money(kpis.receivables), Da.Blue) { app.go("customers") }
                attentionRow(Icons.Default.LocalShipping, "Supplier balances", Fmt.money(kpis.payables), Da.Purple) { app.go("suppliers") }
                Spacer(Modifier.height(8.dp))
                Text("LOW STOCK", fontSize = 10.sp, color = Da.Muted, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                if (low.isEmpty()) Text("Everything is above its alert level.", fontSize = 11.sp, color = Da.Dim)
                low.take(5).forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(p.str("name"), fontSize = 11.5.sp, color = Da.Text, modifier = Modifier.weight(1f), maxLines = 1)
                        Text("${Fmt.qty(p.dbl("stock_qty"))} ${p.str("unit")}", fontSize = 11.sp,
                            color = if (p.dbl("stock_qty") <= 0) Da.Red else Da.Amber)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun attentionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onClick() }.padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = color, modifier = Modifier.size(14.dp)) }
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 12.sp, color = Da.Text, modifier = Modifier.weight(1f))
        Text(value, fontSize = 11.5.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

fun methodLabel(method: String): String = when (method) {
    "MTN_MOMO" -> "MTN MoMo"
    "AIRTEL_MONEY" -> "Airtel Money"
    "CREDIT" -> "Credit"
    "BANK" -> "Bank"
    else -> "Cash"
}

fun greeting(): String = when (java.time.LocalTime.now().hour) {
    in 5..11 -> "morning"
    in 12..16 -> "afternoon"
    in 17..21 -> "evening"
    else -> "night"
}
