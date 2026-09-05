@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.derycode.deryaccount.ui.accounting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyListItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.accounting.AccountingRepo
import com.derycode.deryaccount.data.local.AppDatabase
import com.derycode.deryaccount.data.local.entity.BankLine
import com.derycode.deryaccount.data.local.entity.Budget
import com.derycode.deryaccount.data.local.entity.Employee
import com.derycode.deryaccount.data.local.entity.FixedAsset
import com.derycode.deryaccount.data.local.entity.Payslip
import com.derycode.deryaccount.data.local.entity.User
import com.derycode.deryaccount.ui.theme.DaGreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * v0.12.0 — Accounting suite: journal viewer, VAT report, bank
 * reconciliation, fixed assets with depreciation, budgets, payroll.
 */

private fun ugx(v: Double) = "UGX %,d".format(v.toLong())

private fun nowIso() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())

private fun prettyDate(iso: String): String = try {
    val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).parse(iso)
    SimpleDateFormat("dd MMM yyyy", java.util.Locale.US).format(parsed!!)
} catch (_: Exception) { iso.take(10) }

private fun monthString(offset: Long = 0): String {
    val cal = java.util.Calendar.getInstance()
    cal.add(java.util.Calendar.MONTH, -offset.toInt())
    return SimpleDateFormat("yyyy-MM", Locale.US).format(cal.time)
}

private fun monthRange(month: String): Pair<String, String> =
    "$month-01T00:00:00.000Z" to ("$month-31T23:59:59.999Z")

// ------------------------------------------------------------------
// Journal — every accounting entry the business has ever posted
// ------------------------------------------------------------------

@Composable
fun JournalScreen(db: AppDatabase) {
    var entries by remember { mutableStateOf<List<com.derycode.deryaccount.data.local.entity.JournalEntry>>(emptyList()) }
    var accounts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var linesCache by remember { mutableStateOf<Map<String, List<com.derycode.deryaccount.data.local.entity.JournalLine>>>(emptyMap()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val from = SimpleDateFormat("yyyy-01-01", Locale.US).format(Date()) + "T00:00:00.000Z"
        entries = db.journalDao().entriesBetween(from, "2099-01-01")
        accounts = db.accountDao().all().associate { it.id to it.name }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Journal", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text("Every accounting entry behind every transaction — balanced or it never saves",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        if (entries.isEmpty()) {
            Text("No entries yet.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                lazyListItems(entries) { e ->
                    Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    Text(e.voucherNo, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(e.particulars, fontSize = 12.sp)
                                }
                                Text(prettyDate(e.entryDate), fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = {
                                if (expandedId == e.id) expandedId = null
                                else {
                                    expandedId = e.id
                                    scope.launch {
                                        linesCache = linesCache + (e.id to db.journalDao().linesFor(e.id))
                                    }
                                }
                            }) { Text(if (expandedId == e.id) "Hide entry" else "Show Dr/Cr") }
                            if (expandedId == e.id) {
                                (linesCache[e.id] ?: emptyList()).forEach { l ->
                                    Row(Modifier.fillMaxWidth()) {
                                        Text(accounts[l.accountId] ?: l.accountId,
                                            Modifier.weight(1f), fontSize = 12.sp)
                                        Text("Dr ${"%,.0f".format(l.debit)}",
                                            fontSize = 11.sp, color = DaGreen,
                                            modifier = Modifier.width(90.dp))
                                        Text("Cr ${"%,.0f".format(l.credit)}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------
// VAT report (products with a tax rate are VAT-inclusive)
// ------------------------------------------------------------------

@Composable
fun VatScreen(db: AppDatabase, branchId: String) {
    val scope = rememberCoroutineScope()
    var period by remember { mutableStateOf(0) }
    var rows by remember { mutableStateOf<List<com.derycode.deryaccount.data.local.dao.VatableRow>>(emptyList()) }
    var remitted by remember { mutableStateOf(false) }

    LaunchedEffect(period, remitted) {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, -if (period == 0) 90 else period)
        val from = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(cal.time)
        val to = nowIso()
        rows = db.analyticsDao().vatableSales(branchId, from, to)
    }

    val outputVat = rows.sumOf { it.revenue * it.rate / (100 + it.rate) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("VAT / Tax Report", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text("VAT collected on sales of products that carry a tax rate (VAT-inclusive pricing)",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0 to "This quarter (90d)", 30 to "30 days", 7 to "7 days").forEach { (p, label) ->
                FilterChip(selected = period == p, onClick = { period = p },
                    label = { Text(label, fontSize = 12.sp) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("OUTPUT VAT COLLECTED", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(ugx(outputVat), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = DaGreen)
                Text("Set tax rates per product in Stock → Edit product. Sales of " +
                    "products with 0% rate (most UG shop items) are not taxed.",
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (rows.isEmpty()) {
            Text("No VAT-rated sales in this period.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                lazyListItems(rows) { r ->
                    Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.name ?: "?", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Sold ${ugx(r.revenue)} at ${"%.0f".format(r.rate)}% VAT",
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("VAT " + ugx(r.revenue * r.rate / (100 + r.rate)),
                                fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DaGreen)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                scope.launch {
                    val accounting = AccountingRepo(db)
                    accounting.ensureSeeded()
                    accounting.postVatRemittance(outputVat, monthString())
                    remitted = !remitted
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Record VAT remittance to URA — ${ugx(outputVat)}")
            }
        }
    }
}

// ------------------------------------------------------------------
// Bank reconciliation
// ------------------------------------------------------------------

@Composable
fun BankReconciliationScreen(db: AppDatabase) {
    val scope = rememberCoroutineScope()
    var lines by remember { mutableStateOf<List<BankLine>>(emptyList()) }
    var bookCash by remember { mutableStateOf(0.0) }
    var showAdd by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(tick) {
        lines = db.bankLineDao().observeRecent().first()
        bookCash = AccountingRepo(db).cashBalance()
    }

    val statementNet = lines.filter { it.isMatched }.sumOf { if (it.direction == "IN") it.amount else -it.amount }
    val unmatched = lines.filter { !it.isMatched }

    if (showAdd) {
        AddBankLineDialog(db) { showAdd = false; tick++ }
    }

    Scaffold(floatingActionButton = {
        ExtendedFloatingActionButton(onClick = { showAdd = true }) { Text("Add statement line") }
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            Text("Bank Reconciliation", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("Type lines from your bank / mobile-money statement, then tick them off as they match the books",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(Modifier.weight(1f)) {
                    Column(Modifier.padding(10.dp)) {
                        Text("BOOK (CASH BALANCE)", fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(ugx(bookCash), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Card(Modifier.weight(1f)) {
                    Column(Modifier.padding(10.dp)) {
                        Text("STATEMENT (MATCHED)", fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(ugx(statementNet), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DaGreen)
                    }
                }
            }
            val diff = bookCash - statementNet
            Text(if (kotlin.math.abs(diff) < 0.5) "Reconciled ✓"
                else "Difference: ${ugx(diff)} — tick statement lines as they clear the books",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = if (kotlin.math.abs(diff) < 0.5) DaGreen else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 6.dp))
            Spacer(Modifier.height(4.dp))
            if (unmatched.isNotEmpty()) {
                Text("Unmatched (${unmatched.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                lazyListItems(lines) { l ->
                    Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(l.description, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(l.statementDate.take(10) + (l.note?.let { " · $it" } ?: ""),
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(if (l.direction == "IN") "+" else "−" + ugx(l.amount).removePrefix("UGX "),
                                fontSize = 13.sp,
                                color = if (l.direction == "IN") DaGreen else MaterialTheme.colorScheme.error)
                            Checkbox(checked = l.isMatched, onCheckedChange = { checked ->
                                scope.launch {
                                    db.bankLineDao().upsert(l.copy(
                                        isMatched = checked, updatedAt = nowIso(), syncState = "pending"))
                                    tick++
                                }
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddBankLineDialog(db: AppDatabase, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var desc by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf("IN") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(onDismissRequest = onDone,
        title = { Text("Statement line") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(date, { date = it }, label = { Text("Date (2026-09-05)") }, singleLine = true)
                OutlinedTextField(desc, { desc = it }, label = { Text("Description") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = direction == "IN", onClick = { direction = "IN" },
                        label = { Text("Money in") })
                    FilterChip(selected = direction == "OUT", onClick = { direction = "OUT" },
                        label = { Text("Money out") })
                }
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount (UGX)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amount.replace(",", "").toDoubleOrNull()
                if (desc.isBlank()) { error = "Describe the line"; return@Button }
                if (amt == null || amt <= 0) { error = "Enter a valid amount"; return@Button }
                scope.launch {
                    val now = nowIso()
                    db.bankLineDao().upsert(BankLine(
                        id = UUID.randomUUID().toString(), branchId = "all",
                        statementDate = date.take(10), description = desc.trim(),
                        direction = direction, amount = amt, isMatched = false,
                        note = note.takeIf { it.isNotBlank() },
                        createdAt = now, updatedAt = now))
                    onDone()
                }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } })
}

// ------------------------------------------------------------------
// Fixed assets + straight-line depreciation
// ------------------------------------------------------------------

@Composable
fun FixedAssetsScreen(db: AppDatabase) {
    val scope = rememberCoroutineScope()
    var assets by remember { mutableStateOf<List<FixedAsset>>(emptyList()) }
    var showAdd by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }
    var msg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tick) { assets = db.fixedAssetDao().allOnce() }

    if (showAdd) {
        AddAssetDialog(db) { showAdd = false; tick++ }
    }

    val totalCost = assets.sumOf { it.cost }
    val totalDepr = assets.sumOf { it.accumulatedDepreciation }

    Scaffold(floatingActionButton = {
        ExtendedFloatingActionButton(onClick = { showAdd = true }) { Text("Add asset") }
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            Text("Fixed Assets", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("Equipment, furniture, vehicles — depreciated straight-line each month",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(Modifier.weight(1f)) {
                    Column(Modifier.padding(10.dp)) {
                        Text("AT COST", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(ugx(totalCost), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Card(Modifier.weight(1f)) {
                    Column(Modifier.padding(10.dp)) {
                        Text("DEPRECIATED", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(ugx(totalDepr), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DaGreen)
                        Text("Book value: ${ugx(totalCost - totalDepr)}", fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Button(onClick = {
                scope.launch {
                    var total = 0.0
                    val now = nowIso()
                    assets.forEach { a ->
                        val monthly = (a.cost - a.salvage) / a.lifeMonths.coerceAtLeast(1)
                        val notYetDepreciated = a.cost - a.salvage - a.accumulatedDepreciation
                        if (notYetDepreciated > 0.01) {
                            val take = kotlin.math.min(monthly, notYetDepreciated)
                            db.fixedAssetDao().upsert(a.copy(
                                accumulatedDepreciation = a.accumulatedDepreciation + take,
                                updatedAt = now, syncState = "pending"))
                            total += take
                        }
                    }
                    if (total > 0) {
                        val accounting = AccountingRepo(db)
                        accounting.ensureSeeded()
                        accounting.postDepreciation(total, monthString())
                        msg = "Depreciation of ${ugx(total)} posted to the books"
                    } else msg = "Everything is fully depreciated already"
                    tick++
                }
            }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text("Post this month's depreciation")
            }
            msg?.let { Text(it, fontSize = 12.sp, color = DaGreen, fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(4.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                lazyListItems(assets) { a ->
                    Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    Text(a.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("${a.category} · ${a.lifeMonths} months · bought ${a.purchaseDate.take(10)}",
                                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("Book: ${ugx(a.cost - a.accumulatedDepreciation)}",
                                    fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddAssetDialog(db: AppDatabase, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("EQUIPMENT") }
    var cost by remember { mutableStateOf("") }
    var salvage by remember { mutableStateOf("0") }
    var life by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(onDismissRequest = onDone,
        title = { Text("Add fixed asset") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name (e.g. Fridge)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("EQUIPMENT", "FURNITURE", "VEHICLE", "BUILDING", "OTHER").forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c },
                            label = { Text(c.take(4), fontSize = 10.sp) })
                    }
                }
                OutlinedTextField(cost, { cost = it }, label = { Text("Cost (UGX)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(salvage, { salvage = it }, label = { Text("Salvage value (UGX)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(life, { life = it }, label = { Text("Useful life (months)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val c = cost.replace(",", "").toDoubleOrNull()
                val l = life.toIntOrNull()
                val sv = salvage.replace(",", "").toDoubleOrNull() ?: 0.0
                when {
                    name.isBlank() -> error = "Name the asset"
                    c == null || c <= 0 -> error = "Enter a valid cost"
                    l == null || l <= 0 -> error = "Enter useful life in months"
                    else -> scope.launch {
                        val now = nowIso()
                        db.fixedAssetDao().upsert(FixedAsset(
                            id = UUID.randomUUID().toString(), name = name.trim(),
                            category = category, cost = c, salvage = sv,
                            purchaseDate = now, lifeMonths = l,
                            createdAt = now, updatedAt = now))
                        onDone()
                    }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } })
}

// ------------------------------------------------------------------
// Budgets — plan vs actual per category, per month
// ------------------------------------------------------------------

@Composable
fun BudgetsScreen(db: AppDatabase, branchId: String) {
    val scope = rememberCoroutineScope()
    var month by remember { mutableStateOf(monthString()) }
    var budgets by remember { mutableStateOf<List<Budget>>(emptyList()) }
    var actuals by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var actualRevenue by remember { mutableStateOf(0.0) }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(month, tick) {
        budgets = db.budgetDao().forMonth(month, "EXPENSE") +
            db.budgetDao().forMonth(month, "REVENUE")
        val (from, to) = monthRange(month)
        actuals = db.analyticsDao().expensesByCategory(branchId, from, to)
            .filter { !it.category.isNullOrBlank() }
            .associate { it.category!! to it.total }
        actualRevenue = db.saleDao().totalBetween(branchId, from, to)
    }

    val expenseCats = listOf("RENT", "SALARIES", "TRANSPORT", "UTILITIES", "OTHER")
    val planned = budgets.filter { it.kind == "EXPENSE" }
    val plannedRevenue = budgets.find { it.kind == "REVENUE" && it.category == "REVENUE" }?.amount ?: 0.0

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Budgets", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text("Plan the month, then watch actuals chase the plan",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0L..5L).map { monthString(it) }.forEach { m ->
                FilterChip(selected = month == m, onClick = { month = m },
                    label = { Text(m.takeLast(3), fontSize = 11.sp) })
            }
        }
        Spacer(Modifier.height(8.dp))
        // Revenue budget row
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("SALES REVENUE", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Actual: ${ugx(actualRevenue)}", fontSize = 11.sp)
                    }
                    TextButton(onClick = {
                        scope.launch {
                            val existing = db.budgetDao().get(month, "REVENUE", "REVENUE")
                            val now = nowIso()
                            db.budgetDao().upsert(existing?.copy(
                                amount = if (actualRevenue > 0) actualRevenue else 0.0,
                                updatedAt = now, syncState = "pending")
                                ?: Budget(id = UUID.randomUUID().toString(), month = month,
                                    kind = "REVENUE", category = "REVENUE",
                                    amount = if (actualRevenue > 0) actualRevenue else 0.0,
                                    createdAt = now, updatedAt = now))
                            tick++
                        }
                    }) { Text(if (plannedRevenue > 0) "Set ${ugx(plannedRevenue)}" else "Set target") }
                }
                if (plannedRevenue > 0) {
                    LinearProgressIndicator(
                        progress = (actualRevenue / plannedRevenue).toFloat().coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth())
                    Text("${
                        if (plannedRevenue > 0) "%.0f".format(actualRevenue / plannedRevenue * 100) else "0"}% of target",
                        fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Expense budgets", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            lazyListItems(expenseCats) { cat ->
                val plan = planned.find { it.category == cat }?.amount ?: 0.0
                val actual = actuals[cat] ?: 0.0
                Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(cat, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(if (plan > 0) "Budget ${ugx(plan)} · Actual ${ugx(actual)}"
                                    else "No budget set",
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    val existing = db.budgetDao().get(month, "EXPENSE", cat)
                                    val now = nowIso()
                                    val amount = if (actual > 0) actual else 0.0
                                    db.budgetDao().upsert(existing?.copy(
                                        amount = amount, updatedAt = now, syncState = "pending")
                                        ?: Budget(id = UUID.randomUUID().toString(), month = month,
                                            kind = "EXPENSE", category = cat, amount = amount,
                                            createdAt = now, updatedAt = now))
                                    tick++
                                }
                            }) { Text("Budget from actual") }
                        }
                        if (plan > 0) {
                            LinearProgressIndicator(
                                progress = (actual / plan).toFloat().coerceIn(0f, 1f),
                                modifier = Modifier.fillMaxWidth(),
                                color = if (actual > plan) MaterialTheme.colorScheme.error else DaGreen)
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------
// Payroll — employees, payslips, one-tap pay run
// ------------------------------------------------------------------

@Composable
fun PayrollScreen(db: AppDatabase) {
    val scope = rememberCoroutineScope()
    var month by remember { mutableStateOf(monthString()) }
    var employees by remember { mutableStateOf<List<Employee>>(emptyList()) }
    var slips by remember { mutableStateOf<List<Payslip>>(emptyList()) }
    var showAddEmployee by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }
    var msg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tick, month) {
        employees = db.employeeDao().allOnce()
        slips = db.payslipDao().forMonth(month)
    }

    if (showAddEmployee) {
        AddEmployeeDialog(db) { showAddEmployee = false; tick++ }
    }

    val paidThisMonth = slips.sumOf { it.net }

    Scaffold(floatingActionButton = {
        ExtendedFloatingActionButton(onClick = { showAddEmployee = true }) { Text("Add employee") }
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            Text("Payroll", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("Salaries for $month — paid ${ugx(paidThisMonth)} so far",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0L..2L).map { monthString(it) }.forEach { m ->
                    FilterChip(selected = month == m, onClick = { month = m },
                        label = { Text(m.takeLast(3), fontSize = 11.sp) })
                }
            }
            msg?.let { Text(it, fontSize = 12.sp, color = DaGreen, fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(4.dp))
            Button(onClick = {
                scope.launch {
                    var gross = 0.0; var net = 0.0; var deduct = 0.0; var paid = 0
                    val now = nowIso()
                    employees.forEach { e ->
                        val already = slips.any { it.employeeId == e.id }
                        if (!already) {
                            val g = e.monthlySalary
                            val d = 0.0
                            val n = g - d
                            db.payslipDao().upsert(Payslip(
                                id = UUID.randomUUID().toString(), employeeId = e.id,
                                employeeName = e.name, month = month, gross = g,
                                deductions = d, net = n, method = "CASH", paidAt = now,
                                createdAt = now, updatedAt = now))
                            gross += g; net += n; deduct += d; paid++
                        }
                    }
                    if (paid > 0) {
                        val accounting = AccountingRepo(db)
                        accounting.ensureSeeded()
                        accounting.postPayroll(gross, net, deduct, month)
                        msg = "Paid $paid employees — ${ugx(net)}"
                    } else msg = "Everyone is already paid for $month"
                    tick++
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Pay everyone for $month (cash)")
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                lazyListItems(employees) { e ->
                    val slip = slips.find { it.employeeId == e.id }
                    Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(e.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(e.role, fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(ugx(e.monthlySalary) + "/mo", fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold)
                                if (slip != null) Text("PAID ${ugx(slip.net)}",
                                    fontSize = 11.sp, color = DaGreen,
                                    fontWeight = FontWeight.Bold)
                                else Text("unpaid", fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddEmployeeDialog(db: AppDatabase, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("SHOP ASSISTANT") }
    var phone by remember { mutableStateOf("") }
    var salary by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(onDismissRequest = onDone,
        title = { Text("Add employee") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("SHOP ASSISTANT", "CASHIER", "DRIVER", "GUARD", "OTHER").forEach { r ->
                        FilterChip(selected = role == r, onClick = { role = r },
                            label = { Text(r.take(4), fontSize = 10.sp) })
                    }
                }
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone (optional)") }, singleLine = true)
                OutlinedTextField(salary, { salary = it }, label = { Text("Monthly salary (UGX)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val s = salary.replace(",", "").toDoubleOrNull()
                when {
                    name.isBlank() -> error = "Enter the name"
                    s == null || s <= 0 -> error = "Enter a valid salary"
                    else -> scope.launch {
                        val now = nowIso()
                        db.employeeDao().upsert(Employee(
                            id = UUID.randomUUID().toString(), name = name.trim(), role = role,
                            phone = phone.takeIf { it.isNotBlank() }, monthlySalary = s,
                            createdAt = now, updatedAt = now))
                        onDone()
                    }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } })
}

// ------------------------------------------------------------------
// Permissions — user roles & what each role can reach
// ------------------------------------------------------------------

@Composable
fun PermissionsScreen(db: AppDatabase) {
    val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf<List<User>>(emptyList()) }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(tick) { users = db.userDao().allOnce() }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Users & Permissions", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text("Change a person's role to control what they can reach",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    "OWNER" to "Everything: books, reports, settings, subscription, all branches",
                    "MANAGER" to "Books, reports, stock operations, payments — no subscription/billing",
                    "ACCOUNTANT" to "Books, reports, payments — no POS or settings",
                    "CASHIER" to "Sell screen and customers only — no books, no reports, no settings"
                ).forEach { (role, desc) ->
                    Text(role, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DaGreen)
                    Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            lazyListItems(users) { u ->
                Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(u.fullName.ifBlank { u.username }, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(u.username, fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("OWNER", "MANAGER", "ACCOUNTANT", "CASHIER").forEach { r ->
                                FilterChip(selected = u.role == r, onClick = {
                                    scope.launch {
                                        db.userDao().upsert(u.copy(
                                            role = r, updatedAt = nowIso(), syncState = "pending"))
                                        tick++
                                    }
                                }, label = { Text(r.take(3), fontSize = 10.sp) })
                            }
                        }
                    }
                }
            }
        }
    }
}
