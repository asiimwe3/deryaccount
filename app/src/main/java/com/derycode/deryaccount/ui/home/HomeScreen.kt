package com.derycode.deryaccount.ui.home

/**
 * HomeScreen — the business at a glance.
 * Every figure comes straight from the real database — no sample data.
 * Stat cards show Cash & Bank balance, today's sales, stock value and
 * today's expenses; quick actions jump to the screens shopkeepers use
 * most; the books grid opens each book of account.
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val licenseState = remember { com.derycode.deryaccount.billing.LicenseManager.state(context) }
    var stats by remember { mutableStateOf(HomeStats()) }
    var loading by remember { mutableStateOf(true) }
    var reorderList by remember { mutableStateOf(emptyList<com.derycode.deryaccount.data.local.entity.Product>()) }
    var reorderCount by remember { mutableStateOf(0) }

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
        val reorder = try { db.productDao().observeReorder().first() } catch (_: Exception) { emptyList() }
        reorderList = reorder.take(3)
        reorderCount = reorder.size
        loading = false

        // Book integrity self-check: debits must equal credits, the Stock
        // account must equal the physical stock list, Debtors must equal the
        // customers' balances. Any mismatch is logged to DeryAccount/crashes
        // so it can be traced and repaired.
        try {
            val check = accounting.selfCheck()
            if (!check.ok) {
                com.derycode.deryaccount.util.DbSafety.log(
                    context, "Books self-check",
                    IllegalStateException(check.describe().trim()))
            }
        } catch (_: Exception) { }
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
        Spacer(Modifier.height(10.dp))

        // ---- subscription trial banner (soft — never blocks the books) ----
        if (licenseState.isTrial && licenseState.trialDaysLeft <= 5) {
            Card(Modifier.fillMaxWidth().clickable { onNavigate("subscription") },
                colors = CardDefaults.cardColors(containerColor = DaAmber.copy(alpha = 0.14f))) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = DaAmber, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Free trial ends in ${licenseState.trialDaysLeft} day(s) — tap to see plans",
                        fontSize = 12.sp, color = DaTextPrimary, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(10.dp))
        } else if (!licenseState.isTrial && !licenseState.isLicensed) {
            Card(Modifier.fillMaxWidth().clickable { onNavigate("subscription") },
                colors = CardDefaults.cardColors(containerColor = DaBlue.copy(alpha = 0.14f))) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WorkspacePremium, null, tint = DaBlue, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Running on Starter — tap to unlock Business/Professional features",
                        fontSize = 12.sp, color = DaTextPrimary, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(10.dp))
        }

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

        // ---- Stock alerts: reorder watchlist ----
        if (reorderCount > 0) {
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                containerColor = DaRed.copy(alpha = 0.12f))) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = DaRed, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Reorder stock", fontWeight = FontWeight.Bold, color = DaTextPrimary, fontSize = 13.sp)
                        Text("$reorderCount product(s) at or below the reorder level" +
                            (if (reorderList.isNotEmpty()) ": " + reorderList.joinToString(", ") { it.name } else ""),
                            fontSize = 11.sp, color = DaTextMuted, maxLines = 2)
                    }
                    TextButton(onClick = { onNavigate("inventory") }) { Text("Open", color = DaRed) }
                }
            }
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
    BookTileData("Cash Book", Icons.Default.MenuBook, "books?tab=0&book=1000"),
    BookTileData("Petty Cash", Icons.Default.Savings, "books?tab=0&book=1001"),
    BookTileData("Bank & MoMo", Icons.Default.AccountBalance, "books?tab=0&book=1010"),
    BookTileData("Ledger", Icons.Default.ListAlt, "books?tab=1"),
    BookTileData("Trial Balance", Icons.Default.Balance, "books?tab=2"),
    BookTileData("Income Stmt", Icons.Default.TrendingUp, "books?tab=3"),
    BookTileData("Balance Sheet", Icons.Default.AccountTree, "books?tab=4"),
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
