package com.derycode.deryaccount.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.accounting.AccountingRepo
import com.derycode.deryaccount.data.local.AppDatabase
import com.derycode.deryaccount.ui.theme.*
import kotlinx.coroutines.flow.first

private data class HomeStats(
    val totalCash: Double = 0.0,
    val todaySales: Double = 0.0,
    val stockValue: Double = 0.0,
    val todayExpenses: Double = 0.0
)

/**
 * HomeScreen — the business at a glance. Every number here comes straight
 * from the real database: no placeholders, no sample data. If a shop hasn't
 * recorded anything yet, the cards simply read UGX 0.
 */
@Composable
fun HomeScreen(
    db: AppDatabase,
    branchId: String,
    ownerName: String,
    branchName: String,
    onNavigate: (String) -> Unit
) {
    val accounting = remember { AccountingRepo(db) }
    var stats by remember { mutableStateOf(HomeStats()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(branchId) {
        accounting.ensureSeeded()
        val todayStart = todayStartIso()
        val todayEnd = todayEndIso()

        val cash = db.accountDao().cashAccounts().sumOf { acc ->
            try { accounting.cashBalance(acc.id) } catch (_: Exception) { 0.0 }
        }
        val sales = try {
            db.saleDao().observeRecentSales(branchId).first()
                .filter { !it.isDeleted && it.soldAt >= todayStart && it.soldAt <= todayEnd }
                .sumOf { it.total }
        } catch (_: Exception) { 0.0 }
        val stockVal = try {
            db.productDao().observeBranchProducts(branchId).first()
                .sumOf { it.stockQty * it.costPrice }
        } catch (_: Exception) { 0.0 }
        val expenses = try {
            db.expenseDao().totalBetween(branchId, todayStart, todayEnd)
        } catch (_: Exception) { 0.0 }

        stats = HomeStats(cash, sales, stockVal, expenses)
        loading = false
    }

    Column(
        Modifier.fillMaxSize().background(DaBlack)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column {
            Text(greeting() + ", $ownerName", fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold, color = DaTextPrimary)
            Text("Here's your business at a glance", fontSize = 13.sp, color = DaTextMuted)
        }
        Spacer(Modifier.height(14.dp))

        // ---- stat cards ----
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(Modifier.weight(1f), "Cash & Bank", stats.totalCash, DaGreen, Icons.Default.AccountBalanceWallet, loading)
            StatCard(Modifier.weight(1f), "Today's Sales", stats.todaySales, DaBlue, Icons.Default.PointOfSale, loading)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(Modifier.weight(1f), "Total Stock Value", stats.stockValue, DaAmber, Icons.Default.Inventory2, loading)
            StatCard(Modifier.weight(1f), "Today's Expenses", stats.todayExpenses, DaRed, Icons.Default.Receipt, loading)
        }

        Spacer(Modifier.height(18.dp))
        Text("Quick Actions", fontWeight = FontWeight.Bold, color = DaTextPrimary, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction(Modifier.weight(1f), "New Sale", Icons.Default.PointOfSale, DaGreen) { onNavigate("pos") }
            QuickAction(Modifier.weight(1f), "Add Expense", Icons.Default.Receipt, DaRed) { onNavigate("expenses") }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction(Modifier.weight(1f), "Add Stock", Icons.Default.Inventory2, DaBlue) { onNavigate("inventory") }
            QuickAction(Modifier.weight(1f), "Cash Book", Icons.Default.MenuBook, DaAmber) { onNavigate("books") }
        }

        Spacer(Modifier.height(18.dp))
        Text("Books", fontWeight = FontWeight.Bold, color = DaTextPrimary, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            items(bookTiles) { tile ->
                BookTile(tile) { onNavigate(tile.route) }
            }
        }
    }
}

private data class BookTileData(val label: String, val icon: ImageVector, val route: String)
private val bookTiles = listOf(
    BookTileData("Cash Book", Icons.Default.MenuBook, "books"),
    BookTileData("Petty Cash", Icons.Default.Savings, "books"),
    BookTileData("Bank & MoMo", Icons.Default.AccountBalance, "books"),
    BookTileData("Ledger", Icons.Default.ListAlt, "books"),
    BookTileData("Income Stmt", Icons.Default.TrendingUp, "books"),
    BookTileData("Trial Balance", Icons.Default.Balance, "books"),
    BookTileData("Balance Sheet", Icons.Default.AccountTree, "books"),
    BookTileData("Stock", Icons.Default.Inventory, "inventory"),
)

@Composable
private fun StatCard(
    modifier: Modifier, label: String, amount: Double, accent: Color,
    icon: ImageVector, loading: Boolean
) {
    Surface(
        modifier = modifier.height(88.dp),
        color = DaSurface, shape = RoundedCornerShape(14.dp)
    ) {
        Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).background(accent.copy(alpha = 0.16f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(label, fontSize = 11.sp, color = DaTextMuted, maxLines = 1)
                Text(
                    if (loading) "…" else fmtUgx(amount),
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DaTextPrimary
                )
            }
        }
    }
}

@Composable
private fun QuickAction(modifier: Modifier, label: String, icon: ImageVector, accent: Color, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(52.dp), color = accent, shape = RoundedCornerShape(12.dp),
        onClick = onClick
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color(0xFF03150A), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF03150A))
        }
    }
}

@Composable
private fun BookTile(tile: BookTileData, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.aspectRatio(1f), color = DaSurface, shape = RoundedCornerShape(12.dp),
        onClick = onClick
    ) {
        Column(
            Modifier.fillMaxSize().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(tile.icon, null, tint = DaGreen, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(tile.label, fontSize = 10.sp, color = DaTextPrimary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
        }
    }
}

private fun greeting(): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when { h < 12 -> "Good morning"; h < 17 -> "Good afternoon"; else -> "Good evening" }
}

fun fmtUgx(v: Double): String = "UGX " + "%,d".format(v.toLong())

private fun todayStartIso(): String {
    val cal = java.util.Calendar.getInstance()
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
    return java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(cal.time)
}
private fun todayEndIso(): String {
    val cal = java.util.Calendar.getInstance()
    cal.set(java.util.Calendar.HOUR_OF_DAY, 23); cal.set(java.util.Calendar.MINUTE, 59)
    cal.set(java.util.Calendar.SECOND, 59); cal.set(java.util.Calendar.MILLISECOND, 999)
    return java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(cal.time)
}
