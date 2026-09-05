@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.derycode.deryaccount.ui.business

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyListItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.data.local.AppDatabase
import com.derycode.deryaccount.data.local.entity.Customer
import com.derycode.deryaccount.data.local.entity.Supplier
import com.derycode.deryaccount.ui.theme.DaGreen
import com.derycode.deryaccount.util.MoneyOps
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Money workflows — the two payment screens. Both write ATOMICALLY
 * (payment record + party balance + double-entry journal) via MoneyOps.
 */

private fun ugx(v: Double) = "UGX %,d".format(v.toLong())

private fun prettyDate(iso: String): String = try {
    val parsed = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).parse(iso)
    java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.US).format(parsed!!)
} catch (_: Exception) { iso.take(16) }

@Composable
private fun MethodChips(method: String, onPick: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("CASH", "MTN_MOMO", "AIRTEL_MONEY").forEach { m ->
            FilterChip(selected = method == m, onClick = { onPick(m) },
                label = { Text(m.replace("_", " "), fontSize = 11.sp) })
        }
    }
}

// ------------------------------------------------------------------
// Customer payments (debt collection)
// ------------------------------------------------------------------

@Composable
fun CustomerPaymentsScreen(db: AppDatabase, branchId: String, userId: String) {
    val scope = rememberCoroutineScope()
    var payments by remember { mutableStateOf<List<com.derycode.deryaccount.data.local.entity.CustomerPayment>>(emptyList()) }
    var customers by remember { mutableStateOf<List<Customer>>(emptyList()) }
    var tick by remember { mutableStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(tick) {
        payments = db.customerPaymentDao().observeRecent().first()
        customers = db.customerDao().allOnce()
    }

    if (showAdd) {
        RecordCustomerPaymentDialog(
            db = db, branchId = branchId, userId = userId, customers = customers,
            onDone = { showAdd = false; tick++ })
    }

    Scaffold(floatingActionButton = {
        ExtendedFloatingActionButton(onClick = { showAdd = true }) {
            Text("Receive payment")
        }
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            Text("Customer Payments", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("Debt collection — every shilling collected against what customers owe",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (payments.isEmpty()) {
                Text("No payments recorded yet. Tap \"Receive payment\" when a debtor pays.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    lazyListItems(payments) { p ->
                        val c = customers.find { it.id == p.customerId }
                        Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(c?.name ?: "Customer", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(prettyDate(p.paidAt) + " · " + p.method.replace("_", " "),
                                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(ugx(p.amount), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DaGreen)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordCustomerPaymentDialog(
    db: AppDatabase, branchId: String, userId: String,
    customers: List<Customer>, onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<Customer?>(null) }
    var showPick by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("CASH") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    if (showPick) {
        AlertDialog(onDismissRequest = { showPick = false },
            title = { Text("Choose customer") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    customers.sortedByDescending { it.balance }.forEach { c ->
                        TextButton(onClick = { picked = c; showPick = false }) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(c.name, fontWeight = FontWeight.SemiBold)
                                Text("owes ${ugx(c.balance)}", fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            },
            confirmButton = {}, dismissButton = { TextButton(onClick = { showPick = false }) { Text("Cancel") } })
    }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Receive payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showPick = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(picked?.let { "${it.name} — owes ${ugx(it.balance)}" } ?: "Choose customer…")
                }
                OutlinedTextField(amount, { amount = it; error = null },
                    label = { Text("Amount received (UGX)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                MethodChips(method) { method = it }
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") },
                    singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amount.replace(",", "").toDoubleOrNull()
                val c = picked
                when {
                    c == null -> error = "Choose the customer paying"
                    amt == null || amt <= 0 -> error = "Enter a valid amount"
                    amt > (c.balance + 0.01) -> error = "More than owed — they owe ${ugx(c.balance)}"
                    else -> scope.launch {
                        try {
                            MoneyOps.receiveCustomerPayment(db, branchId, userId, c, amt, method, note)
                        } catch (_: Exception) { }
                        onDone()
                    }
                }
            }) { Text("Record") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } }
    )
}

// ------------------------------------------------------------------
// Supplier payments (what we owe)
// ------------------------------------------------------------------

@Composable
fun SupplierPaymentsScreen(db: AppDatabase, branchId: String, userId: String) {
    var payments by remember { mutableStateOf<List<com.derycode.deryaccount.data.local.entity.SupplierPayment>>(emptyList()) }
    var suppliers by remember { mutableStateOf<List<Supplier>>(emptyList()) }
    var showAdd by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(tick) {
        payments = db.supplierPaymentDao().observeRecent().first()
        suppliers = db.supplierDao().observeAll().first()
    }

    if (showAdd) {
        RecordSupplierPaymentDialog(db, branchId, userId, suppliers) { showAdd = false; tick++ }
    }

    Scaffold(floatingActionButton = {
        ExtendedFloatingActionButton(onClick = { showAdd = true }) {
            Text("Pay supplier")
        }
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            Text("Supplier Payments", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("Settling what your business owes — stock bought on credit",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            val owed = suppliers.sumOf { it.balance }
            if (owed > 0) Text("You currently owe suppliers: ${ugx(owed)}",
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(6.dp))
            if (payments.isEmpty()) {
                Text("No supplier payments recorded yet.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    lazyListItems(payments) { p ->
                        val s = suppliers.find { it.id == p.supplierId }
                        Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(s?.name ?: "Supplier", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(prettyDate(p.paidAt) + " · " + p.method.replace("_", " "),
                                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(ugx(p.amount), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordSupplierPaymentDialog(
    db: AppDatabase, branchId: String, userId: String,
    suppliers: List<Supplier>, onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<Supplier?>(null) }
    var showPick by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("CASH") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    if (showPick) {
        AlertDialog(onDismissRequest = { showPick = false },
            title = { Text("Choose supplier") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    suppliers.sortedByDescending { it.balance }.forEach { s ->
                        TextButton(onClick = { picked = s; showPick = false }) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(s.name, fontWeight = FontWeight.SemiBold)
                                Text("we owe ${ugx(s.balance)}", fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            },
            confirmButton = {}, dismissButton = { TextButton(onClick = { showPick = false }) { Text("Cancel") } })
    }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Pay supplier") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showPick = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(picked?.let { "${it.name} — we owe ${ugx(it.balance)}" } ?: "Choose supplier…")
                }
                OutlinedTextField(amount, { amount = it; error = null },
                    label = { Text("Amount paid (UGX)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                MethodChips(method) { method = it }
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amount.replace(",", "").toDoubleOrNull()
                val s = picked
                when {
                    s == null -> error = "Choose the supplier being paid"
                    amt == null || amt <= 0 -> error = "Enter a valid amount"
                    amt > (s.balance + 0.01) -> error = "More than owed — you owe ${ugx(s.balance)}"
                    else -> scope.launch {
                        try {
                            MoneyOps.paySupplier(db, branchId, userId, s, amt, method, note)
                        } catch (_: Exception) { }
                        onDone()
                    }
                }
            }) { Text("Record payment") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } }
    )
}
