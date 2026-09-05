package com.derycode.deryaccount.ui.customers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.accounting.AccountingRepo
import androidx.room.withTransaction
import com.derycode.deryaccount.data.local.AppDatabase
import com.derycode.deryaccount.data.local.entity.Customer
import com.derycode.deryaccount.data.local.entity.Sale
import kotlinx.coroutines.launch

/**
 * CustomersScreen — the full customer/debtor book.
 * Each customer has a profile (name, phone, address, type, credit limit) and
 * live lifetime stats: total purchases, total paid, and outstanding debt.
 * Credit sales grow the balance automatically (POS → Credit), repayments
 * shrink it (posting Dr Cash, Cr Debtors to the books), and the POS blocks
 * any credit sale that would push a customer past their credit limit.
 */

private fun ugx(v: Double): String = "UGX %,d".format(v.toLong())

@Composable
fun CustomersScreen(db: AppDatabase) {
    var showAdd by remember { mutableStateOf(false) }
    var detailFor by remember { mutableStateOf<Customer?>(null) }
    var editFor by remember { mutableStateOf<Customer?>(null) }
    var repayFor by remember { mutableStateOf<Customer?>(null) }
    var query by remember { mutableStateOf("") }

    val customers by db.customerDao().observeAll().collectAsState(initial = emptyList())
    val filtered = remember(customers, query) {
        if (query.isBlank()) customers
        else customers.filter {
            it.name.contains(query, ignoreCase = true) ||
                (it.phone?.contains(query) ?: false)
        }
    }
    val totalOwed = customers.sumOf { it.balance }
    val totalPurchases = customers.sumOf { it.totalPurchases }
    val debtors = customers.count { it.balance > 0 }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Customers & Debtors", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("TOTAL OWED TO YOU", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(ugx(totalOwed), fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                Text("$debtors of ${customers.size} customer(s) with outstanding credit",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (totalPurchases > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text("Lifetime sales through customers: ${ugx(totalPurchases)}",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search by name or phone…") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) }
        )
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { showAdd = true },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Add Customer", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(12.dp))

        if (filtered.isEmpty()) {
            Text(if (query.isBlank()) "No customers yet — add your first one above"
                 else "No customer matches \"$query\"",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        LazyColumn {
            items(filtered, key = { it.id }) { c ->
                CustomerRow(c, onClick = { detailFor = c })
                Divider()
            }
        }
    }

    if (showAdd) CustomerFormDialog(db, existing = null, onDone = { showAdd = false })
    detailFor?.let { c ->
        CustomerProfileDialog(
            db, c,
            onRepay = { repayFor = c; detailFor = null },
            onEdit = { editFor = c; detailFor = null },
            onDismiss = { detailFor = null }
        )
    }
    editFor?.let { c ->
        CustomerFormDialog(db, existing = c, onDone = { editFor = null })
    }
    repayFor?.let { c ->
        RepayDialog(db, c, onDone = { repayFor = null })
    }
}

@Composable
private fun CustomerRow(c: Customer, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.name, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(c.phone, c.address?.take(40))
                        .ifEmpty { listOf("no contact details") }
                        .joinToString(" · "),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (c.creditLimit > 0) {
                    Text("limit ${ugx(c.creditLimit)}", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (c.balance > 0) {
                    Text("owes ${ugx(c.balance)}",
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                } else {
                    Text("settled", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (c.totalPurchases > 0) {
                    Text("bought ${ugx(c.totalPurchases)}", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * The full customer profile — contact details, credit limit, lifetime stats,
 * outstanding debt and the last 50 purchases, with repay & edit actions.
 */
@Composable
private fun CustomerProfileDialog(
    db: AppDatabase,
    customer: Customer,
    onRepay: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    var history by remember { mutableStateOf<List<Sale>>(emptyList()) }
    LaunchedEffect(customer.id) {
        history = try { db.saleDao().forCustomer(customer.id) } catch (_: Exception) { emptyList() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(customer.name, fontWeight = FontWeight.ExtraBold)
                Text(customer.type, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ---- Contact details ----
                if (customer.phone != null || customer.address != null) {
                    Column {
                        Text("PROFILE", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        customer.phone?.let { Text(it, fontSize = 14.sp) }
                        customer.address?.let {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Place, null, modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(it, fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // ---- Lifetime stats ----
                Text("LIFETIME", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                StatRow("Total purchases", ugx(customer.totalPurchases))
                StatRow("Total paid", ugx(customer.totalPaid))
                StatRow("Credit limit",
                    if (customer.creditLimit > 0) ugx(customer.creditLimit) else "no limit set")
                Divider()
                StatRow("Current balance", ugx(customer.balance),
                    bold = true,
                    color = if (customer.balance > 0) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary)
                StatRow("Outstanding debt", ugx(customer.balance.coerceAtLeast(0.0)),
                    bold = true,
                    color = if (customer.balance > 0) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary)
                if (customer.balance > 0 && customer.creditLimit > 0) {
                    val left = customer.creditLimit - customer.balance
                    Text(
                        if (left > 0) "Credit remaining: ${ugx(left)}"
                        else "LIMIT EXCEEDED — collect a repayment before more credit",
                        fontSize = 12.sp,
                        color = if (left > 0) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.error
                    )
                }

                // ---- Purchase history ----
                if (history.isNotEmpty()) {
                    Text("PURCHASE HISTORY (${history.size})", fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    history.take(20).forEach { s ->
                        Row(Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(s.receiptNo, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Text("${s.soldAt.take(10)} · ${s.paymentMethod}",
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(ugx(s.total), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else if (customer.totalPurchases > 0) {
                    Text("Older purchases (before this profile existed) are not listed",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("No purchases recorded yet",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Row {
                if (customer.balance > 0) {
                    TextButton(onClick = onRepay) { Text("Repayment") }
                }
                TextButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun StatRow(label: String, value: String, bold: Boolean = false,
                    color: androidx.compose.ui.graphics.Color? = null) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = if (bold) 15.sp else 13.sp,
            fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Medium,
            color = color ?: MaterialTheme.colorScheme.onSurface)
    }
}

/** Add (existing = null) or edit (existing != null) a customer profile. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerFormDialog(db: AppDatabase, existing: Customer?, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var address by remember { mutableStateOf(existing?.address ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: "RETAIL") }
    var creditLimit by remember {
        mutableStateOf(if ((existing?.creditLimit ?: 0.0) > 0) existing!!.creditLimit.toLong().toString() else "")
    }
    var error by remember { mutableStateOf<String?>(null) }
    val types = listOf("RETAIL", "WHOLESALE", "DISTRIBUTOR")

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (existing == null) "Add Customer" else "Edit Customer") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(name, { name = it; error = null }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                OutlinedTextField(address, { address = it }, label = { Text("Address / location (optional)") },
                    singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    types.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t, fontSize = 11.sp) }
                        )
                    }
                }
                OutlinedTextField(creditLimit, { creditLimit = it; error = null },
                    label = { Text("Credit limit (0 = no limit)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Text("The POS will refuse credit sales that push this customer beyond the limit.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank()) { error = "Name is required"; return@Button }
                val limit = creditLimit.replace(",", "").trim().toDoubleOrNull()
                if (limit == null || limit < 0) { error = "Credit limit must be a number (0 or more)"; return@Button }
                scope.launch {
                    val now = nowIso()
                    if (existing == null) {
                        db.customerDao().upsert(Customer(
                            id = java.util.UUID.randomUUID().toString(),
                            name = name.trim(),
                            phone = phone.ifBlank { null },
                            address = address.ifBlank { null },
                            type = type,
                            creditLimit = limit,
                            createdAt = now, updatedAt = now))
                    } else {
                        db.customerDao().upsert(existing.copy(
                            name = name.trim(),
                            phone = phone.ifBlank { null },
                            address = address.ifBlank { null },
                            type = type,
                            creditLimit = limit,
                            updatedAt = now, syncState = "pending"))
                    }
                    onDone()
                }
            }) { Text(if (existing == null) "Save" else "Update") }
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
            Text("Outstanding debt: ${ugx(customer.balance)}", fontWeight = FontWeight.Bold)
            OutlinedTextField(amount, { amount = it; error = null },
                label = { Text("Amount received (UGX)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = {
            Button(onClick = {
                val amt = amount.replace(",", "").toDoubleOrNull()
                if (amt == null || amt <= 0) { error = "Enter a valid amount"; return@Button }
                if (amt > customer.balance) { error = "More than owed — pay ${ugx(customer.balance)}"; return@Button }
                scope.launch {
                    try {
                        val now = nowIso()
                        // Atomic: customer balance + lifetime stats and the Cash
                        // Book entry move together, or neither does.
                        db.withTransaction {
                            db.customerDao().upsert(customer.copy(
                                balance = customer.balance - amt,
                                totalPaid = customer.totalPaid + amt,
                                updatedAt = now, syncState = "pending"))
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
