@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.derycode.deryaccount.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyListItems
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.data.local.AppDatabase
import com.derycode.deryaccount.data.local.dao.CategoryProfit
import com.derycode.deryaccount.data.local.dao.DayTotal
import com.derycode.deryaccount.data.local.dao.MonthTotal
import com.derycode.deryaccount.data.local.dao.ProductProfit
import com.derycode.deryaccount.data.local.dao.ProductStat
import com.derycode.deryaccount.data.local.dao.DeadStockRow
import com.derycode.deryaccount.data.local.dao.ValuationRow
import com.derycode.deryaccount.ui.theme.DaGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v0.12.0 — Analytics screens: best sellers, dead stock, stock valuation,
 * branch comparison, profit by product/category, and sales charts.
 * All computed from the shop's own local data — fully offline.
 */

// ------------------------------------------------------------------
// shared helpers
// ------------------------------------------------------------------

private fun ugx(v: Double) = "UGX %,d".format(v.toLong())

private fun isoDaysAgo(days: Long): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        .format(Date(System.currentTimeMillis() - days * 86_400_000L))

private fun isoMonthsAgo(months: Long): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        .format(Date(System.currentTimeMillis() - months * 30L * 86_400_000L))

private fun prettyDate(iso: String?): String {
    if (iso.isNullOrBlank()) return "never"
    return try {
        val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).parse(iso)
        SimpleDateFormat("dd MMM yyyy", Locale.US).format(parsed!!)
    } catch (_: Exception) { iso.take(10) }
}

@Composable
private fun PeriodChips(period: Int, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(7 to "7 days", 30 to "30 days", 90 to "90 days", 0 to "All time").forEach { (p, label) ->
            FilterChip(selected = period == p, onClick = { onPick(p) },
                label = { Text(label, fontSize = 12.sp) })
        }
    }
}

@Composable
private fun AnalyticsHeader(title: String, subtitle: String) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyNote(text: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Minimal bar chart drawn on canvas — no chart library needed offline. */
@Composable
private fun BarChart(values: List<Double>, labels: List<String>, title: String,
                     subtitle: String, barColor: Color = DaGreen) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                if (values.isEmpty()) return@Canvas
                val max = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
                val barW = size.width / values.size
                values.forEachIndexed { i, v ->
                    val h = (v / max * (size.height - 6)).toFloat()
                    drawRect(
                        color = barColor,
                        topLeft = Offset(i * barW + barW * 0.15f, size.height - h),
                        size = Size(barW * 0.7f, h))
                }
            }
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(labels.firstOrNull() ?: "", fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(labels.lastOrNull() ?: "", fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ------------------------------------------------------------------
// Best sellers
// ------------------------------------------------------------------

@Composable
fun BestSellersScreen(db: AppDatabase, branchId: String) {
    var period by remember { mutableStateOf(30) }
    var rows by remember { mutableStateOf<List<ProductStat>>(emptyList()) }
    LaunchedEffect(period) {
        val from = if (period == 0) isoDaysAgo(3650) else isoDaysAgo(period.toLong())
        rows = db.analyticsDao().bestSellers(branchId, from, isoDaysAgo(-1))
    }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        AnalyticsHeader("Best Sellers", "Products ranked by revenue — restock what actually moves")
        PeriodChips(period) { period = it }
        Spacer(Modifier.height(8.dp))
        if (rows.isEmpty()) { EmptyNote("No sales in this period yet."); return@Column }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            lazyListItems(rows) { r ->
                Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("#${rows.indexOf(r) + 1}", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = DaGreen, modifier = Modifier.width(36.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.name ?: "Unknown", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Sold: ${"%,.0f".format(r.totalQty)} units", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(ugx(r.totalRevenue), fontWeight = FontWeight.Bold,
                            fontSize = 14.sp, color = DaGreen)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------
// Dead / slow stock
// ------------------------------------------------------------------

@Composable
fun DeadStockScreen(db: AppDatabase, branchId: String) {
    var days by remember { mutableStateOf(60) }
    var rows by remember { mutableStateOf<List<DeadStockRow>>(emptyList()) }
    LaunchedEffect(days) {
        rows = db.analyticsDao().deadStock(branchId, isoDaysAgo(days.toLong()))
    }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        AnalyticsHeader("Dead / Slow Stock",
            "In stock but not sold recently — your capital sitting on shelves")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(30 to "30 days", 60 to "60 days", 90 to "90 days", 3650 to "Never sold").forEach { (d, label) ->
                FilterChip(selected = days == d, onClick = { days = d },
                    label = { Text(label, fontSize = 12.sp) })
            }
        }
        Spacer(Modifier.height(8.dp))
        if (rows.isEmpty()) { EmptyNote("Nothing is dead — every stocked item sold in this window."); return@Column }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            lazyListItems(rows) { r ->
                Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name ?: "?", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Last sold: ${prettyDate(r.lastSold)}", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error)
                        }
                        Text("${"%,.0f".format(r.stockQty)} in stock", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------
// Stock valuation
// ------------------------------------------------------------------

@Composable
fun StockValuationScreen(db: AppDatabase, branchId: String) {
    var rows by remember { mutableStateOf<List<ValuationRow>>(emptyList()) }
    LaunchedEffect(Unit) { rows = db.analyticsDao().valuationByCategory(branchId) }
    val atCost = rows.sumOf { it.costValue }
    val atRetail = rows.sumOf { it.retailValue }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        AnalyticsHeader("Stock Valuation",
            "What your inventory is worth right now — at cost and at selling price")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(Modifier.weight(1f)) {
                Column(Modifier.padding(12.dp)) {
                    Text("AT COST", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(ugx(atCost), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Text("money invested", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Card(Modifier.weight(1f)) {
                Column(Modifier.padding(12.dp)) {
                    Text("AT RETAIL", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(ugx(atRetail), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = DaGreen)
                    Text("if everything sells", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Potential gross margin: ${ugx(atRetail - atCost)}",
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        if (rows.isEmpty()) { EmptyNote("No stock to value yet."); return@Column }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            lazyListItems(rows) { r ->
                Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text(r.category ?: "?", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("At cost: ${ugx(r.costValue)}", fontSize = 12.sp)
                            Text("At retail: ${ugx(r.retailValue)}", fontSize = 12.sp, color = DaGreen)
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------
// Branch comparison
// ------------------------------------------------------------------

@Composable
fun BranchComparisonScreen(db: AppDatabase) {
    var period by remember { mutableStateOf(30) }
    var branchNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var sales by remember { mutableStateOf<Map<String, Pair<Double, Int>>>(emptyMap()) }
    var expenses by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    LaunchedEffect(period) {
        val branches = db.branchDao().all()
        branchNames = branches.associate { it.id to it.name }
        val from = if (period == 0) isoDaysAgo(3650) else isoDaysAgo(period.toLong())
        val to = isoDaysAgo(-1)
        sales = db.analyticsDao().salesByBranch(from, to)
            .filter { !it.branchId.isNullOrBlank() }
            .associate { it.branchId!! to (it.total to it.cnt) }
        expenses = db.analyticsDao().expensesByBranch(from, to)
            .filter { !it.branchId.isNullOrBlank() }
            .associate { it.branchId!! to it.total }
    }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        AnalyticsHeader("Branch Comparison", "Sales and expenses side by side, per branch")
        PeriodChips(period) { period = it }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val ids = (sales.keys + expenses.keys).distinct()
            if (ids.isEmpty()) item { EmptyNote("No activity in this period.") }
            lazyListItems(ids) { bid ->
                val (saleTotal, txns) = sales[bid] ?: (0.0 to 0)
                val exp = expenses[bid] ?: 0.0
                Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text(branchNames[bid] ?: "Branch", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Sales: ${ugx(saleTotal)} ($txns)", fontSize = 12.sp)
                            Text("Expenses: ${ugx(exp)}", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error)
                        }
                        Text("Net: ${ugx(saleTotal - exp)}", fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (saleTotal - exp >= 0) DaGreen else MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------
// Profit by product
// ------------------------------------------------------------------

@Composable
fun ProfitByProductScreen(db: AppDatabase, branchId: String) {
    var period by remember { mutableStateOf(30) }
    var rows by remember { mutableStateOf<List<ProductProfit>>(emptyList()) }
    LaunchedEffect(period) {
        val from = if (period == 0) isoDaysAgo(3650) else isoDaysAgo(period.toLong())
        rows = db.analyticsDao().profitByProduct(branchId, from, isoDaysAgo(-1))
    }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        AnalyticsHeader("Profit by Product",
            "Real gross profit per item (selling price − cost at time of sale)")
        PeriodChips(period) { period = it }
        Spacer(Modifier.height(8.dp))
        if (rows.isEmpty()) { EmptyNote("No sales in this period yet."); return@Column }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            lazyListItems(rows) { r ->
                Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name ?: "?", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Revenue ${ugx(r.totalRevenue)} · Cost ${ugx(r.totalCost)}",
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(ugx(r.totalProfit), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            color = if (r.totalProfit >= 0) DaGreen else MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------
// Profit by category
// ------------------------------------------------------------------

@Composable
fun ProfitByCategoryScreen(db: AppDatabase, branchId: String) {
    var period by remember { mutableStateOf(30) }
    var rows by remember { mutableStateOf<List<CategoryProfit>>(emptyList()) }
    LaunchedEffect(period) {
        val from = if (period == 0) isoDaysAgo(3650) else isoDaysAgo(period.toLong())
        rows = db.analyticsDao().profitByCategory(branchId, from, isoDaysAgo(-1))
    }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        AnalyticsHeader("Profit by Category",
            "Which business lines make the real money")
        PeriodChips(period) { period = it }
        Spacer(Modifier.height(8.dp))
        if (rows.isEmpty()) { EmptyNote("No sales in this period yet."); return@Column }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            lazyListItems(rows) { r ->
                Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name ?: "?", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Revenue ${ugx(r.totalRevenue)} · Cost ${ugx(r.totalCost)}",
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(ugx(r.totalProfit), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            color = if (r.totalProfit >= 0) DaGreen else MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------
// Daily / monthly sales charts
// ------------------------------------------------------------------

@Composable
fun SalesChartsScreen(db: AppDatabase, branchId: String) {
    var daily by remember { mutableStateOf<List<DayTotal>>(emptyList()) }
    var monthly by remember { mutableStateOf<List<MonthTotal>>(emptyList()) }
    LaunchedEffect(Unit) {
        daily = db.analyticsDao().dailyTotals(branchId, isoDaysAgo(29), isoDaysAgo(-1))
        monthly = db.analyticsDao().monthlyTotals(branchId, isoMonthsAgo(11), isoDaysAgo(-1))
    }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        AnalyticsHeader("Sales Charts", "How sales are trending — daily and monthly")
        if (daily.isEmpty() && monthly.isEmpty()) {
            EmptyNote("No sales yet — charts appear after the first sale.")
            return@Column
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BarChart(
                values = daily.map { it.total },
                labels = listOf(prettyDate(daily.firstOrNull()?.day),
                                prettyDate(daily.lastOrNull()?.day)),
                title = "Last 30 days",
                subtitle = "Total UGX ${"%,d".format(daily.sumOf { it.total }.toLong())} " +
                    "across ${daily.sumOf { it.cnt }} sales")
            BarChart(
                values = monthly.map { it.total },
                labels = monthly.map { it.month?.takeLast(2) ?: "" },
                title = "Last 12 months",
                subtitle = "Best month: ${
                    ugx(monthly.maxByOrNull { it.total }?.total ?: 0.0)}",
                barColor = MaterialTheme.colorScheme.primary)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    val best = daily.maxByOrNull { it.total }
                    if (best != null) {
                        Text("Best day: ${prettyDate(best.day)} — ${ugx(best.total)}",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    val avg = if (daily.isNotEmpty()) daily.sumOf { it.total } / daily.size else 0.0
                    Text("30-day daily average: ${ugx(avg)}", fontSize = 13.sp)
                }
            }
        }
    }
}
