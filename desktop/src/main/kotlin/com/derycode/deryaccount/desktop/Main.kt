package com.derycode.deryaccount.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.derycode.deryaccount.desktop.data.AdminOps
import com.derycode.deryaccount.desktop.data.Clock
import com.derycode.deryaccount.desktop.data.Fmt
import com.derycode.deryaccount.desktop.data.Repo
import com.derycode.deryaccount.desktop.data.Reports
import com.derycode.deryaccount.desktop.ui.*
import com.derycode.deryaccount.desktop.ui.screens.*
import kotlinx.coroutines.delay

data class NavItem(val key: String, val label: String, val icon: ImageVector, val group: String)

val navItems = listOf(
    NavItem("dashboard", "Dashboard", Icons.Default.Home, "MAIN"),
    NavItem("pos", "Sell / POS", Icons.Default.PointOfSale, "MAIN"),

    NavItem("sales", "Sales register", Icons.Default.ReceiptLong, "SALES"),
    NavItem("customers", "Customers", Icons.Default.People, "SALES"),
    NavItem("shift", "Shift & cash", Icons.Default.Payments, "SALES"),

    NavItem("frontdesk", "Front desk", Icons.Default.Hotel, "HOTEL"),
    NavItem("rooms", "Rooms & rates", Icons.Default.MeetingRoom, "HOTEL"),
    NavItem("bookings", "Bookings", Icons.Default.EventAvailable, "HOTEL"),
    NavItem("guests", "Guests", Icons.Default.PersonSearch, "HOTEL"),
    NavItem("hotelreports", "Hotel reports", Icons.Default.BarChart, "HOTEL"),
    NavItem("hotelreceipts", "Receipts & invoices", Icons.Default.ReceiptLong, "HOTEL"),
    NavItem("hotelicence", "Hotel Edition", Icons.Default.WorkspacePremium, "HOTEL"),

    NavItem("stock", "Stock & inventory", Icons.Default.Inventory2, "STOCK"),
    NavItem("purchases", "Purchases", Icons.Default.ShoppingCart, "STOCK"),

    NavItem("expenses", "Expenses", Icons.Default.RequestQuote, "MONEY"),
    NavItem("suppliers", "Suppliers", Icons.Default.LocalShipping, "MONEY"),

    NavItem("books", "Books of account", Icons.Default.MenuBook, "BOOKS"),
    NavItem("reports", "Reports & analytics", Icons.Default.Insights, "BOOKS"),
    NavItem("admin", "Assets & staff", Icons.Default.AccountBalance, "BOOKS"),

    NavItem("data", "Data & backup", Icons.Default.Storage, "SYSTEM"),
    NavItem("settings", "Settings", Icons.Default.Settings, "SYSTEM"),
    NavItem("docs", "Help & docs", Icons.Default.HelpOutline, "SYSTEM")
)

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "DeryAccount — Business Suite",
        state = rememberWindowState(width = 1440.dp, height = 900.dp)
    ) {
        DeryTheme {
            val repo = remember { Repo.open() }
            val app = remember { AppState(repo, Reports(repo), AdminOps(repo)) }
            if (app.user == null) {
                LoginScreen(app)
            } else {
                AppShell(app)
            }
        }
    }
}

@Composable
fun AppShell(app: AppState) {
    // Toast clears itself
    LaunchedEffect(app.toast) {
        if (app.toast != null) { delay(3200); app.toast = null }
    }

    Row(Modifier.fillMaxSize().background(Da.Black)) {
        Sidebar(app)
        Box(Modifier.width(1.dp).fillMaxHeight().background(Da.Outline))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            TopBar(app)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (app.screen) {
                    "dashboard" -> DashboardScreen(app)
                    "pos" -> PosScreen(app)
                    "sales" -> SalesScreen(app)
                    "customers" -> CustomersScreen(app)
                    "shift" -> ShiftScreen(app)
                    "frontdesk" -> HotelGate(app) { FrontDeskScreen(app) }
                    "rooms" -> HotelGate(app) { RoomsScreen(app) }
                    "bookings" -> HotelGate(app) { BookingsScreen(app) }
                    "guests" -> HotelGate(app) { GuestsScreen(app) }
                    "hotelreports" -> HotelGate(app) { HotelReportsScreen(app) }
                    "hotelreceipts" -> HotelGate(app) { HotelReceiptsScreen(app) }
                    "hotelicence" -> HotelEditionScreen(app)
                    "stock" -> StockScreen(app)
                    "purchases" -> PurchasesScreen(app)
                    "expenses" -> ExpensesScreen(app)
                    "suppliers" -> SuppliersScreen(app)
                    "books" -> BooksScreen(app)
                    "reports" -> ReportsScreen(app)
                    "admin" -> AdminScreen(app)
                    "data" -> DataScreen(app)
                    "settings" -> SettingsScreen(app)
                    "docs" -> DocsScreen(app)
                    "legal" -> LegalScreen(app)
                    else -> DashboardScreen(app)
                }
                app.toast?.let { msg ->
                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp)) {
                        Row(
                            Modifier.clip(RoundedCornerShape(10.dp)).background(Da.Surface2)
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = Da.Green, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(msg, fontSize = 12.5.sp, color = Da.Text)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Sidebar(app: AppState) {
    val profile = remember(app.tick) { app.repo.profile() }
    val visible = navItems.filter { item ->
        if (!app.isCashier) true
        else item.key in listOf("dashboard", "pos", "sales", "stock", "purchases", "shift", "customers", "docs")
    }
    Column(
        Modifier.width(238.dp).fillMaxHeight().background(Da.Sidebar)
            .border(0.dp, Color.Transparent)
            .padding(vertical = 14.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandLogo(size = 38.dp, radius = 11.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("DeryAccount", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                Text(
                    profile.str("name").ifBlank { "Business Suite" }.take(22),
                    fontSize = 10.sp, color = Da.Green, maxLines = 1
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            var lastGroup = ""
            visible.forEach { item ->
                if (item.group != lastGroup) {
                    lastGroup = item.group
                    Text(
                        item.group, fontSize = 9.sp, color = Da.Dim, letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 6.dp)
                    )
                }
                NavRow(item, app.screen == item.key) { app.screen = item.key }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Da.Surface2)
                    .clickable { app.screen = "settings" }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFFEAF0FF)),
                    contentAlignment = Alignment.Center
                ) { Text(initials(app.userName), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Da.Brand) }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.userName, fontSize = 12.sp, color = Da.Text, maxLines = 1)
                    Text(app.role.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 9.5.sp, color = Da.Dim)
                }
                Icon(
                    Icons.Default.Logout, "Sign out", tint = Da.Muted,
                    modifier = Modifier.size(15.dp).clickable { app.user = null }
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("v${Version.name} · offline", fontSize = 9.sp, color = Da.Dim, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
private fun NavRow(item: NavItem, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) Color(0xFFEAF0FF) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(item.icon, null, tint = if (selected) Da.Brand else Da.Muted, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            item.label, fontSize = 12.5.sp,
            color = if (selected) Da.BrandDeep else Da.Muted,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun TopBar(app: AppState) {
    val profile = remember(app.tick) { app.repo.profile() }
    val current = navItems.firstOrNull { it.key == app.screen }
    Row(
        Modifier.fillMaxWidth().height(58.dp).background(Da.Surface)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(current?.icon ?: Icons.Default.Home, null, tint = Da.Green, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(current?.label ?: "Dashboard", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Da.Text)
        Spacer(Modifier.weight(1f))
        Text("${Fmt.date(Clock.today())} · ${app.repo.branchName(app.branchId)}", fontSize = 11.5.sp, color = Da.Dim)
        Spacer(Modifier.width(16.dp))
        if (!app.isCashier) {
            Btn("New sale", { app.screen = "pos" }, Icons.Default.Add, BtnKind.PRIMARY)
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Da.Outline))
}

private fun initials(name: String): String =
    name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }

@Composable
private fun BoxScopeSpacer(height: Dp) { Spacer(Modifier.height(height)) }
