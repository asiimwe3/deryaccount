@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.derycode.deryaccount.ui.expenses

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.accounting.AccountingRepo
import com.derycode.deryaccount.data.local.AppDatabase
import com.derycode.deryaccount.data.local.entity.Expense
import kotlinx.coroutines.launch

/**
 * ExpensesScreen — record day-to-day shop spending (rent, transport,
 * airtime…) in two taps. Every expense is saved locally AND posted to
 * the books (Dr expense account, Cr cash) so the Cash Book, Income
 * Statement and Balance Sheet all stay correct automatically.
 */
@Composable
fun ExpensesScreen(db: AppDatabase, branchId: String, userId: String) {
    var todayTotal by remember { mutableStateOf(0.0) }
    var showAdd by remember { mutableStateOf(false) }
    var justSaved by remember { mutableStateOf<String?>(null) }

    // refresh whenever we come back to this screen or save a new expense
    var refreshKey by remember { mutableStateOf(0) }
    LaunchedEffect(refreshKey) {
        val today = todayStr()
        todayTotal = db.expenseDao().totalBetween(branchId, "${today}T00:00:00", "${today}T23:59:59")
    }

    val recent by db.expenseDao().observeRecent(branchId)
        .collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Expenses", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("SPENT TODAY", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("UGX %,d".format(todayTotal.toLong()), fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.error)
                Text("Every expense is posted to your books automatically.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { showAdd = true },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Record Expense", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        justSaved?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(12.dp))

        Text("Recent expenses", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))
        LazyColumn {
            items(recent, key = { it.id }) { e ->
                ListItem(
                    headlineContent = { Text(e.note?.takeIf { it.isNotBlank() } ?: e.category) },
                    supportingContent = { Text("${e.category} · ${e.spentAt.take(10)}") },
                    trailingContent = {
                        Text("UGX %,d".format(e.amount.toLong()), fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error)
                    }
                )
                Divider()
            }
        }
    }

    if (showAdd) AddExpenseDialog(
        db = db, branchId = branchId, userId = userId,
        onSaved = { msg ->
            showAdd = false
            justSaved = msg
            refreshKey++
        },
        onDismiss = { showAdd = false }
    )
}

private val CATEGORIES = listOf(
    "RENT" to "acc-rent",
    "SALARIES" to "acc-salaries",
    "TRANSPORT" to "acc-transport",
    "UTILITIES" to "acc-utilities",
    "AIRTIME" to "acc-airtime",
    "OTHER" to "acc-sundry"
)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AddExpenseDialog(
    db: AppDatabase, branchId: String, userId: String,
    onSaved: (String) -> Unit, onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var category by remember { mutableStateOf(CATEGORIES.first().first) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var paidFrom by remember { mutableStateOf("CASH") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Expense") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Category", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CATEGORIES.forEach { (cat, _) ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = amount, onValueChange = { amount = it },
                label = { Text("Amount (UGX)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = note, onValueChange = { note = it },
                label = { Text("Note (optional)") }, singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Text("Paid from", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("CASH" to "Cash", "PETTY" to "Petty", "BANK" to "Bank / MoMo").forEach { (id, label) ->
                    FilterChip(
                        selected = paidFrom == id,
                        onClick = { paidFrom = id },
                        label = { Text(label) }
                    )
                }
            }
            error?.let { Spacer(Modifier.height(6.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = {
            Button(onClick = {
                val amt = amount.toDoubleOrNull()
                if (amt == null || amt <= 0) { error = "Enter a valid amount"; return@Button }
                scope.launch {
                    val now = nowIso()
                    db.expenseDao().upsert(Expense(
                        id = java.util.UUID.randomUUID().toString(),
                        branchId = branchId, userId = userId,
                        category = category, amount = amt,
                        note = note.ifBlank { null }, spentAt = now,
                        createdAt = now, updatedAt = now
                    ))
                    // Post to the books: Dr expense account, Cr cash
                    val accounting = AccountingRepo(db)
                    accounting.ensureSeeded()
                    val expenseAccount = CATEGORIES.first { it.first == category }.second
                    val cashAccount = when (paidFrom) {
                        "PETTY" -> AccountingRepo.PETTY
                        "BANK" -> AccountingRepo.BANK
                        else -> AccountingRepo.CASH
                    }
                    accounting.recordPayment(
                        cashAccount, expenseAccount, amt,
                        "Expense — ${category.lowercase()}${if (note.isNotBlank()) " ($note)" else ""}")
                    onSaved("Saved: ${category.lowercase()} UGX %,d".format(amt.toLong()))
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun nowIso() = java.text.SimpleDateFormat(
    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(java.util.Date())
private fun todayStr() = java.text.SimpleDateFormat(
    "yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
