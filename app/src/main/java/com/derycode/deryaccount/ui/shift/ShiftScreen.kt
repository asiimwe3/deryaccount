package com.derycode.deryaccount.ui.shift

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.data.local.AppDatabase
import com.derycode.deryaccount.data.local.entity.Shift
import kotlinx.coroutines.launch

/**
 * ShiftScreen — till management. Open the shift with a float, sell all
 * day, then close by counting the cash box: the app shows expected vs
 * counted and records the variance. Multiple cashiers can't overlap:
 * one open shift per user at a time.
 */
@Composable
fun ShiftScreen(db: AppDatabase, branchId: String, userId: String) {
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf<Shift?>(null) }
    var showOpen by remember { mutableStateOf(false) }
    var showClose by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }

    // cash sales + expenses since the shift opened, for the expected figure
    var cashSales by remember { mutableStateOf(0.0) }
    var shiftExpenses by remember { mutableStateOf(0.0) }

    LaunchedEffect(refresh) {
        open = db.shiftDao().openShift(userId)
        open?.let { s ->
            cashSales = db.saleDao().totalByMethodBetween(branchId, "CASH", s.openedAt, nowIso())
            shiftExpenses = db.expenseDao().totalBetween(branchId, s.openedAt, nowIso())
        }
    }

    val history by db.shiftDao().observeRecent(branchId).collectAsState(initial = emptyList())
    val expected = (open?.openingCash ?: 0.0) + cashSales - shiftExpenses

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Shift", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(12.dp))

        if (open == null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("TILL CLOSED", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Open a shift with your opening float before selling starts.",
                        fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { showOpen = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text("Open Shift", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("SHIFT OPEN", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Text("since ${open!!.openedAt.take(16).replace('T', ' ')}",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    RowText("Opening float", "UGX %,d".format(open!!.openingCash.toLong()))
                    RowText("Cash sales (this shift)", "UGX %,d".format(cashSales.toLong()))
                    RowText("Expenses (this shift)", "-UGX %,d".format(shiftExpenses.toLong()))
                    Divider(Modifier.padding(vertical = 6.dp))
                    RowText("Expected in cash box", "UGX %,d".format(expected.toLong()), bold = true)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { showClose = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text("Count & Close Shift", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Recent shifts", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))
        LazyColumn {
            items(history, key = { it.id }) { s ->
                ListItem(
                    headlineContent = {
                        Text(if (s.closedAt == null) "Open"
                             else "Closed ${s.closedAt!!.take(10)}")
                    },
                    supportingContent = { Text("Float UGX %,d".format(s.openingCash.toLong())) },
                    trailingContent = {
                        if (s.closedAt != null && s.variance != null) {
                            val v = s.variance!!
                            Text(
                                if (v >= 0) "+UGX %,d".format(v.toLong()) else "-UGX %,d".format((-v).toLong()),
                                fontWeight = FontWeight.Bold,
                                color = if (java.lang.Math.abs(v) < 1.0) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error
                            )
                        } else Text("…", fontSize = 12.sp)
                    }
                )
                Divider()
            }
        }
    }

    if (showOpen) OpenShiftDialog(db, branchId, userId,
        onDone = { showOpen = false; refresh++ },
        onDismiss = { showOpen = false })

    if (showClose && open != null) CloseShiftDialog(db, open!!, expected,
        onDone = { showClose = false; open = null; refresh++ },
        onDismiss = { showClose = false })
}

@Composable
private fun OpenShiftDialog(db: AppDatabase, branchId: String, userId: String,
                            onDone: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open Shift") },
        text = {
            Column {
            OutlinedTextField(amount, { amount = it },
                label = { Text("Opening float — cash in the box (UGX)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            error?.let { Spacer(Modifier.height(6.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
        }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amount.toDoubleOrNull()
                if (amt == null || amt < 0) { error = "Enter a valid amount (0 is fine)"; return@Button }
                scope.launch {
                    val now = nowIso()
                    db.shiftDao().upsert(Shift(
                        id = java.util.UUID.randomUUID().toString(),
                        branchId = branchId, userId = userId,
                        openedAt = now, closedAt = null,
                        openingCash = amt, closingCash = null,
                        expectedCash = null, variance = null,
                        createdAt = now, updatedAt = now))
                    onDone()
                }
            }) { Text("Open") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CloseShiftDialog(db: AppDatabase, shift: Shift, expected: Double,
                             onDone: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var counted by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Count & Close") },
        text = {
            Column {
            Text("Expected in the box:", fontSize = 13.sp)
            Text("UGX %,d".format(expected.toLong()), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(counted, { counted = it },
                label = { Text("Cash actually counted (UGX)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            error?.let { Spacer(Modifier.height(6.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
        }
        },
        confirmButton = {
            Button(onClick = {
                val amt = counted.toDoubleOrNull()
                if (amt == null || amt < 0) { error = "Enter the counted amount"; return@Button }
                scope.launch {
                    val now = nowIso()
                    db.shiftDao().upsert(shift.copy(
                        closedAt = now, closingCash = amt,
                        expectedCash = expected, variance = amt - expected,
                        updatedAt = now, syncState = "pending"))
                    onDone()
                }
            }) { Text("Close Shift") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun RowText(left: String, right: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(left, fontSize = 14.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(right, fontSize = 14.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

private fun nowIso() = java.text.SimpleDateFormat(
    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(java.util.Date())
