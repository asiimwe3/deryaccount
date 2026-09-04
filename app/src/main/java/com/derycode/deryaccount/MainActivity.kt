package com.derycode.deryaccount

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.derycode.deryaccount.data.local.AppDatabase
import com.derycode.deryaccount.data.remote.SupabaseClient
import com.derycode.deryaccount.sync.SyncEngine
import com.derycode.deryaccount.ui.auth.LoginScreen
import com.derycode.deryaccount.ui.pos.PosScreen
import com.derycode.deryaccount.ui.inventory.InventoryScreen
import com.derycode.deryaccount.ui.pos.PosViewModel
import com.derycode.deryaccount.ui.reports.ReportsScreen
import com.derycode.deryaccount.ui.expenses.ExpensesScreen
import com.derycode.deryaccount.ui.customers.CustomersScreen
import com.derycode.deryaccount.ui.shift.ShiftScreen
import com.derycode.deryaccount.ui.books.BooksScreen
import com.derycode.deryaccount.accounting.AccountingRepo
import com.derycode.deryaccount.ui.settings.MoreScreen
import com.derycode.deryaccount.ui.theme.DeryAccountTheme
import com.derycode.deryaccount.util.SessionManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Crash safety net: every unexpected crash is logged to the
        // DeryAccount/crashes folder on the device — never silently lost.
        com.derycode.deryaccount.util.AutoUpdate.installCrashLogger(this)
        enableEdgeToEdge()
        setContent { DeryAccountTheme { DeryAccountApp() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeryAccountApp() {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val session = remember { SessionManager(context) }
    val scope = rememberCoroutineScope()

    // Logged-in session (survives restarts)
    var sessionState by remember { mutableStateOf<Pair<String, String>?>(null) } // userId + branchId
    var role by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        session.supabaseConfig()?.let { (url, key) -> SupabaseClient.init(url, key) }
        combine(session.userId, session.branchId) { uid, bid -> uid to bid }
            .collect { (uid, bid) ->
                sessionState = if (uid != null && bid != null) uid to bid else null
            }
    }

    // ---- In-app auto-update: checks GitHub once per launch, never crashes ----
    var updateInfo by remember { mutableStateOf<com.derycode.deryaccount.util.AutoUpdate.UpdateInfo?>(null) }
    var updating by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val current = context.packageManager
                .getPackageInfo(context.packageName, 0).let {
                    if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode.toInt()
                    else @Suppress("DEPRECATION") it.versionCode
                }
            updateInfo = com.derycode.deryaccount.util.AutoUpdate.check(current)
        }
    }
    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Update available — v${info.versionName}") },
            text = { Text(info.notes) },
            confirmButton = {
                Button(onClick = {
                    updating = true
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        com.derycode.deryaccount.util.AutoUpdate.downloadAndInstall(context, info)
                        updating = false
                    }
                }) { if (updating) Text("Downloading…") else Text("Update now") }
            },
            dismissButton = {
                TextButton(onClick = { updateInfo = null }) { Text("Later") }
            }
        )
    }

    val syncEngine = remember { SyncEngine(context, db) }
    DisposableEffect(Unit) {
        syncEngine.startPeriodic()
        onDispose { syncEngine.stopPeriodic() }
    }

    val navController = rememberNavController()
    val backStack = navController.currentBackStackEntryAsState().value
    val currentRoute = backStack?.destination?.route

    // ---- login gate ----
    if (sessionState == null) {
        LoginScreen(session, db) { userId, username, userRole, branchId, branchName ->
            sessionState = userId to branchId
            role = userRole
        }
        return
    }

    val (userId, branchId) = sessionState!!

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scopeDrawer = rememberCoroutineScope()
    val drawerItems = listOf(
        Triple("pos", "Sell / POS", Icons.Default.PointOfSale),
        Triple("inventory", "Stock", Icons.Default.Inventory2),
        Triple("books", "Books of Account", Icons.Default.MenuBook),
        Triple("reports", "Reports", Icons.Default.BarChart),
        Triple("more", "More", Icons.Default.Menu)
    )
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("DeryAccount", fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(20.dp))
                Text("Books of account, offline",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(12.dp))
                drawerItems.forEach { (route, label, icon) ->
                    NavigationDrawerItem(
                        label = { Text(label, fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(icon, null) },
                        selected = currentRoute == route,
                        onClick = {
                            scopeDrawer.launch { drawerState.close() }
                            navController.navigate(route) { launchSingleTop = true }
                        },
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("FREE for the first 100", fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary)
                        Text("Ugandan businesses — every feature, offline.",
                            fontSize = 12.sp)
                    }
                }
            }
        }
    ) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(appTitle(currentRoute),
                    fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { scopeDrawer.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, "Menu")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == "pos",
                    onClick = { navController.navigate("pos") { launchSingleTop = true } },
                    icon = { Icon(Icons.Default.PointOfSale, null) },
                    label = { Text("Sell") }
                )
                NavigationBarItem(
                    selected = currentRoute == "inventory",
                    onClick = { navController.navigate("inventory") { launchSingleTop = true } },
                    icon = { Icon(Icons.Default.Inventory2, null) },
                    label = { Text("Stock") }
                )
                NavigationBarItem(
                    selected = currentRoute == "reports",
                    onClick = { navController.navigate("reports") { launchSingleTop = true } },
                    icon = { Icon(Icons.Default.BarChart, null) },
                    label = { Text("Reports") }
                )
                NavigationBarItem(
                    selected = currentRoute == "books",
                    onClick = { navController.navigate("books") { launchSingleTop = true } },
                    icon = { Icon(Icons.Default.MenuBook, null) },
                    label = { Text("Books") }
                )
                NavigationBarItem(
                    selected = currentRoute == "more",
                    onClick = { navController.navigate("more") { launchSingleTop = true } },
                    icon = { Icon(Icons.Default.Menu, null) },
                    label = { Text("More") }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "pos",
            modifier = Modifier.padding(padding)
        ) {
            composable("pos") {
                val vm: PosViewModel = viewModel(
                    factory = PosViewModel.Factory(db, branchId, userId, context)
                )
                PosScreen(viewModel = vm, onSaleComplete = { })
            }
            composable("inventory") {
                InventoryScreen(db, branchId)
            }
            composable("reports") {
                ReportsScreen(db, branchId)
            }
            composable("expenses") {
                ExpensesScreen(db, branchId, userId)
            }
            composable("customers") {
                CustomersScreen(db)
            }
            composable("shift") {
                ShiftScreen(db, branchId, userId)
            }
            composable("books") {
                val accounting = remember { AccountingRepo(db) }
                BooksScreen(db, accounting)
            }
            composable("more") {
                MoreScreen(db, syncEngine, session, context,
                    onNavigate = { route -> navController.navigate(route) { launchSingleTop = true } }) {
                    sessionState = null
                    scope.launch { session.logout() }
                }
            }
        }
    }
    } // drawer
}

/** Screen titles for the top bar. */
private fun appTitle(route: String?): String = when (route) {
    "pos" -> "DeryAccount — Sell"
    "inventory" -> "Stock"
    "books" -> "Books of Account"
    "reports" -> "Reports"
    "expenses" -> "Expenses"
    "customers" -> "Customers"
    "shift" -> "Shift"
    "more" -> "More"
    else -> "DeryAccount"
}
