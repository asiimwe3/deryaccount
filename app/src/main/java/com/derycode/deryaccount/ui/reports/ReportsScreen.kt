package com.derycode.deryaccount.ui.reports

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.data.local.AppDatabase
import kotlinx.coroutines.launch

/**
 * ReportsScreen — today + this month sales, computed from local DB.
 * Everything here is offline-computed; numbers appear the same whether
 * or not sync has run yet.
 */
@Composable
fun ReportsScreen(db: AppDatabase, branchId: String) {
    val scope = rememberCoroutineScope()
    var todayTotal by remember { mutableStateOf(0.0) }
    var monthTotal by remember { mutableStateOf(0.0) }
    var todayCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        scope.launch {
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val today = fmt.format(java.util.Date())
            todayTotal = db.saleDao().totalBetween(branchId, "${today}T00:00:00", "${today}T23:59:59")
            todayCount = db.saleDao().countBetween(branchId, "${today}T00:00:00", "${today}T23:59:59")
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
            val monthStart = fmt.format(cal.time)
            monthTotal = db.saleDao().totalBetween(branchId, "${monthStart}T00:00:00", "${today}T23:59:59")
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Reports", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(16.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("TODAY'S SALES", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("UGX %,d".format(todayTotal.toLong()), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary)
                Text("$todayCount transactions", fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("THIS MONTH", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("UGX %,d".format(monthTotal.toLong()), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Cash drawer reconciliation, expenses by category, best sellers, "
                     + "stock valuation and multi-branch comparison arrive in v1.1 — "
                     + "the database already records everything they need.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
