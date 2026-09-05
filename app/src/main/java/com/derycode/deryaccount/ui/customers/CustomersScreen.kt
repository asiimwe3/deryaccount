package com.derycode.deryaccount.ui.customers

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
import com.derycode.deryaccount.accounting.AccountingRepo
import androidx.room.withTransaction
import com.derycode.deryaccount.data.local.AppDatabase
import com.derycode.deryaccount.data.local.entity.Customer
import kotlinx.coroutines.launch

/**
 * CustomersScreen — who owes you money. Credit sales grow a customer's
 * balance automatically (POS → Credit); here you can add customers and
 * record repayments, which post to the books (Dr Cash, Cr Debtors).
 */
@Composable
fun CustomersScreen(db: AppDatabase) {
    var showAdd by remember { mutableStateOf(false) }
    var repayFor by remember { mutableStateOf<Customer?>(null) }

    val customers by db.customerDao().observeAll().collectAsState(initial = emptyList())
    val totalOwed = customers.sumOf { it.balance }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Customers", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("TOTAL OWED TO YOU", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("UGX %,d".format(totalOwed.toLong()), fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                Text("${customers.count { it.balance > 0 }} customer(s) with outstanding credit",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { showAdd = true },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Add Customer", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(12.dp))

        LazyColumn {
            items(customers, key = { it.id }) { c ->
                ListItem(
                    headlineContent = { Text(c.name, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(c.phone ?: "no phone") },
                    trailingContent = {
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                            if (c.balance > 0) Text("owes UGX %,d".format(c.balance.toLong()),
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            else Text("settled", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (c.balance > 0) TextButton(
                                onClick = { repayFor = c },
                                contentPadding = PaddingValues(0.dp)
                            ) { Text("Record repayment", fontSize = 12.sp) }
                        }
                    }
                )
                Divider()
            }
        }
    }

    if (showAdd) AddCustomerDialog(db, onDone = { showAdd = false })
    repayFor?.let { c ->
        RepayDialog(db, c, onDone = { repayFor = null })
    }
}

@Composable
private fun AddCustomerDialog(db: AppDatabase, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Add Customer") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
            OutlinedTextField(phone, { phone = it }, label = { Text("Phone (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
        } },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank()) return@Button
                scope.launch {
                    val now = nowIso()
                    db.customerDao().upsert(Customer(
                        id = java.util.UUID.randomUUID().toString(),
                        name = name.trim(), phone = phone.ifBlank { null },
                        createdAt = now, updatedAt = now))
                    onDone()
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } }
    )
}

@Composable
private fun RepayDialog(db: AppDatabase, customer: Customer, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Repayment — ${customer.name}") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Owes: UGX %,d".format(customer.balance.toLong()), fontWeight = FontWeight.Bold)
            OutlinedTextField(amount, { amount = it }, label = { Text("Amount received (UGX)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = {
            Button(onClick = {
                val amt = amount.toDoubleOrNull()
                if (amt == null || amt <= 0) { error = "Enter a valid amount"; return@Button }
                if (amt > customer.balance) { error = "More than owed — pay UGX %,d".format(customer.balance.toLong()); return@Button }
                scope.launch {
                    try {
                        val now = nowIso()
                        // Atomic: customer balance and the Cash Book entry move
                        // together, or neither does.
                        db.withTransaction {
                            db.customerDao().upsert(customer.copy(
                                balance = customer.balance - amt, updatedAt = now, syncState = "pending"))
                            // Books: money came in — Dr Cash, Cr Debtors
                            val accounting = AccountingRepo(db)
                            accounting.ensureSeeded()
                            accounting.recordReceipt(
                                AccountingRepo.CASH, AccountingRepo.DEBTORS, amt,
                                "Repayment — ${customer.name}")
                        }
                    } catch (_: Exception) { }
                    onDone()
                }
            }) { Text("Record") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } }
    )
}

private fun nowIso() = java.text.SimpleDateFormat(
    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(java.util.Date())
