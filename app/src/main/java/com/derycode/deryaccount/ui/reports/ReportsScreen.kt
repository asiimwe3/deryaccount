package com.derycode.deryaccount.ui.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.data.local.AppDatabase
import com.derycode.deryaccount.util.DeviceStore
import kotlinx.coroutines.launch

/**
 * ReportsScreen — today + this month sales, plus the End-of-Day close.
 * Everything is offline-computed from the local DB; numbers are the
 * same whether or not any sync has run.
 */
@Composable
fun ReportsScreen(db: AppDatabase, branchId: String) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    var todayTotal by remember { mutableStateOf(0.0) }
    var monthTotal by remember { mutableStateOf(0.0) }
    var todayCount by remember { mutableStateOf(0) }

    // End-of-day figures
    var cashTotal by remember { mutableStateOf(0.0) }
    var momoTotal by remember { mutableStateOf(0.0) }
    var creditTotal by remember { mutableStateOf(0.0) }
    var expensesTotal by remember { mutableStateOf(0.0) }
    var zReportSaved by remember { mutableStateOf<String?>(null) }
    var showEndOfDay by remember { mutableStateOf(false) }

    suspend fun loadToday() {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val today = fmt.format(java.util.Date())
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        val monthStart = fmt.format(cal.time)
        todayTotal = db.saleDao().totalBetween(branchId, "${today}T00:00:00", "${today}T23:59:59")
        todayCount = db.saleDao().countBetween(branchId, "${today}T00:00:00", "${today}T23:59:59")
        monthTotal = db.saleDao().totalBetween(branchId, "${monthStart}T00:00:00", "${today}T23:59:59")
        cashTotal = db.saleDao().totalByMethodBetween(branchId, "CASH", "${today}T00:00:00", "${today}T23:59:59")
        momoTotal = db.saleDao().totalByMethodBetween(branchId, "MTN_MOMO", "${today}T00:00:00", "${today}T23:59:59") +
                db.saleDao().totalByMethodBetween(branchId, "AIRTEL_MONEY", "${today}T00:00:00", "${today}T23:59:59")
        creditTotal = db.saleDao().totalByMethodBetween(branchId, "CREDIT", "${today}T00:00:00", "${today}T23:59:59")
        expensesTotal = db.expenseDao().totalBetween(branchId, "${today}T00:00:00", "${today}T23:59:59")
    }

    LaunchedEffect(Unit) { loadToday() }

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

        // ---- End of Day ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("END OF DAY", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Close the day, check what should be in the cash box, and save "
                     + "a Z-report to the DeryAccount folder.", fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    scope.launch { loadToday(); showEndOfDay = true }
                }, modifier = Modifier.fillMaxWidth()) { Text("Close the day") }
                zReportSaved?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Best sellers, stock valuation and multi-branch comparison arrive in v1.1 — "
                     + "the database already records everything they need.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showEndOfDay) {
        val expectedCash = cashTotal - expensesTotal
        AlertDialog(
            onDismissRequest = { showEndOfDay = false },
            title = { Text("End of Day") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                RowText("Sales ($todayCount)", "UGX %,d".format(todayTotal.toLong()), bold = true)
                RowText("Cash sales", "UGX %,d".format(cashTotal.toLong()))
                RowText("Mobile money", "UGX %,d".format(momoTotal.toLong()))
                RowText("Credit sales", "UGX %,d".format(creditTotal.toLong()))
                Divider(Modifier.padding(vertical = 6.dp))
                RowText("Expenses today", "-UGX %,d".format(expensesTotal.toLong()))
                RowText("Expected cash in box", "UGX %,d".format(expectedCash.toLong()), bold = true)
                Spacer(Modifier.height(8.dp))
                Text("Count the physical cash. If it differs, the difference is "
                     + "recorded on paper reports only — no stock or books change.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val branchName = db.branchDao().get(branchId)?.name ?: "My Shop"
                        val text = buildString {
                    appendLine(branchName)
                    appendLine("END OF DAY REPORT — ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.US).format(java.util.Date())}")
                    appendLine("================================")
                    appendLine("Transactions: $todayCount")
                    appendLine("Total sales:      UGX %,d".format(todayTotal.toLong()))
                    appendLine("  Cash:           UGX %,d".format(cashTotal.toLong()))
                    appendLine("  Mobile money:   UGX %,d".format(momoTotal.toLong()))
                    appendLine("  Credit:         UGX %,d".format(creditTotal.toLong()))
                    appendLine("Expenses:         UGX %,d".format(expensesTotal.toLong()))
                    appendLine("--------------------------------")
                    appendLine("Expected in cash box: UGX %,d".format((cashTotal - expensesTotal).toLong()))
                    appendLine("================================")
                    appendLine("Actual cash counted: ______________")
                    appendLine("Difference:           ______________")
                    appendLine("Signed: __________________________")
                        }
                        try {
                            DeviceStore.saveReport(context, text)
                            zReportSaved = "Z-report saved to the DeryAccount folder ✓"
                        } catch (_: Exception) {
                            zReportSaved = "Could not save the report file"
                        }
                        showEndOfDay = false
                    }
                }) { Text("Save Z-Report") }
            },
            dismissButton = { TextButton(onClick = { showEndOfDay = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun RowText(left: String, right: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(left, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
        Text(right, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
    }
}
