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

    Scaffold(
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
            composable("books") {
                val accounting = remember { AccountingRepo(db) }
                BooksScreen(db, accounting)
            }
            composable("more") {
                MoreScreen(db, syncEngine, session, context) {
                    sessionState = null
                    scope.launch { session.logout() }
                }
            }
        }
    }
}
