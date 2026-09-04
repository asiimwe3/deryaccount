package com.derycode.deryaccount.ui.books

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.accounting.AccountingRepo
import com.derycode.deryaccount.data.local.AppDatabase
import com.derycode.deryaccount.data.local.dao.AccountEntryView
import com.derycode.deryaccount.data.local.entity.Account
import com.derycode.deryaccount.util.PdfExport
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * BooksScreen — the traditional books of account, 100% offline:
 *   Cash Book | Ledger | Trial Balance | Income Statement
 * Format matches what shop bookkeepers already know: ruled columns,
 * receipts in, payments out, running balance.
 */
@Composable
fun BooksScreen(db: AppDatabase, accounting: AccountingRepo) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Cash Book", "Ledger", "Trial Balance", "Income Stmt", "Balance Sheet")
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
            tabs.forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i },
                    text = { Text(t, fontSize = 13.sp, fontWeight = FontWeight.Bold) })
            }
        }
        when (tab) {
            0 -> CashBookTab(db, accounting, context)
            1 -> LedgerTab(db, accounting, context)
            2 -> TrialBalanceTab(accounting, context)
            3 -> IncomeStatementTab(accounting, context)
            4 -> BalanceSheetTab(accounting, context)
        }
    }
}

private data class BookRow(val date: String, val voucher: String, val particulars: String, val effect: Double)

// ----------------------------------------------------------------
// CASH BOOK
// ----------------------------------------------------------------
@Composable
private fun CashBookTab(db: AppDatabase, accounting: AccountingRepo, context: android.content.Context) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf(emptyList<BookRow>()) }
    var opening by remember { mutableStateOf(0.0) }
    var accounts by remember { mutableStateOf(emptyList<Account>()) }
    var showEntryDialog by remember { mutableStateOf(false) }

    suspend fun reload() {
        accounting.ensureSeeded()
        accounts = db.accountDao().all()
        val cash = db.accountDao().byCode("1000") ?: return
        opening = db.journalDao().balanceBefore(cash.id, monthStart())
        val list = db.journalDao().observeAccountEntries(cash.id, monthStart(), todayEnd()).first()
        rows = list.sortedByDescending { it.entryDate }.map {
            BookRow(it.entryDate, it.voucherNo, it.particulars, it.cashEffect)
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Surface(Modifier.fillMaxWidth(), tonalElevation = 2.dp,
            shape = MaterialTheme.shapes.medium) {
            Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("CASH BOOK — THIS MONTH", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Opening B/F: ${fmt(opening)}", fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(fmt(opening + rows.sumOf { it.effect }),
                    fontWeight = FontWeight.ExtraBold, fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(6.dp))

        BookTable(Modifier.weight(1f), rows)
        Spacer(Modifier.height(6.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showEntryDialog = true },
                modifier = Modifier.weight(1f).height(56.dp)) {
                Icon(Icons.Default.Add, null)
                Text("  New Entry", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = { printBook(context, "Cash Book", opening, rows) },
                modifier = Modifier.height(56.dp)) {
                Icon(Icons.Default.Print, null)
            }
        }
    }

    if (showEntryDialog) {
        NewEntryDialog(accounting, accounts,
            onDone = { showEntryDialog = false; scope.launch { reload() } })
    }
}

/** Ruled cashbook table — shared by Cash Book and Ledger. */
@Composable
private fun BookTable(modifier: Modifier = Modifier, rows: List<BookRow>) {
    Column(modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Row(Modifier.width(600.dp).padding(vertical = 6.dp)) {
            Text("DATE", Modifier.weight(0.7f), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text("VCHR", Modifier.weight(0.5f), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text("PARTICULARS", Modifier.weight(1.9f), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text("RECEIPT", Modifier.weight(0.8f), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text("PAYMENT", Modifier.weight(0.8f), fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
        Divider()
        LazyColumn {
            if (rows.isEmpty()) item {
                Text("No entries yet. Tap New Entry to record money received or paid.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 10.dp))
            }
            items(rows) { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(shortDate(e.date), Modifier.weight(0.7f), fontSize = 12.sp)
                    Text(e.voucher, Modifier.weight(0.5f), fontSize = 11.sp)
                    Text(e.particulars, Modifier.weight(1.9f), fontSize = 12.sp, maxLines = 2)
                    Text(if (e.effect > 0) fmt(e.effect) else "", Modifier.weight(0.8f),
                        fontSize = 12.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                    Text(if (e.effect < 0) fmt(-e.effect) else "", Modifier.weight(0.8f),
                        fontSize = 12.sp, color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                }
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}

// ----------------------------------------------------------------
// NEW ENTRY — Receive / Pay
// ----------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewEntryDialog(
    accounting: AccountingRepo,
    accounts: List<Account>,
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isReceipt by remember { mutableStateOf(true) }
    var amount by remember { mutableStateOf("") }
    var particulars by remember { mutableStateOf("") }
    var cashAccountId by remember { mutableStateOf(AccountingRepo.CASH) }
    var otherAccountId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val cashAccounts = accounts.filter { it.isCash }
    val otherAccounts = accounts.filter { !it.isCash }

    AlertDialog(onDismissRequest = onDone,
        title = { Text(if (isReceipt) "Money RECEIVED" else "Money PAID OUT",
            fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = isReceipt, onClick = { isReceipt = true },
                        label = { Text("Received") })
                    FilterChip(selected = !isReceipt, onClick = { isReceipt = false },
                        label = { Text("Paid out") })
                }
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    cashAccounts.forEach { ca ->
                        FilterChip(selected = cashAccountId == ca.id,
                            onClick = { cashAccountId = ca.id },
                            label = { Text(ca.name, fontSize = 12.sp) })
                    }
                }
                DropdownField(
                    label = if (isReceipt) "From (account credited)" else "To (expense/asset account)",
                    options = otherAccounts.map { it.id to it.name },
                    selected = otherAccountId,
                    onSelect = { otherAccountId = it }
                )
                OutlinedTextField(particulars, { particulars = it },
                    label = { Text("Particulars") }, singleLine = true)
                OutlinedTextField(amount, { amount = it },
                    label = { Text("Amount (UGX)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amount.toDoubleOrNull()
                when {
                    amt == null || amt <= 0 -> error = "Enter a valid amount"
                    otherAccountId == null -> error = "Choose the account"
                    particulars.isBlank() -> error = "Enter particulars"
                    else -> scope.launch {
                        try {
                            if (isReceipt)
                                accounting.recordReceipt(cashAccountId, otherAccountId!!, amt, particulars)
                            else
                                accounting.recordPayment(cashAccountId, otherAccountId!!, amt, particulars)
                            onDone()
                        } catch (e: Exception) { error = e.message }
                    }
                }
            }) { Text("Post Entry") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = options.firstOrNull { it.first == selected }?.second ?: ""
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) },
                    onClick = { onSelect(id); expanded = false })
            }
        }
    }
}

// ----------------------------------------------------------------
// LEDGER
// ----------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LedgerTab(db: AppDatabase, accounting: AccountingRepo, context: android.content.Context) {
    var accounts by remember { mutableStateOf(emptyList<Account>()) }
    var selected by remember { mutableStateOf<String?>(null) }
    var rows by remember { mutableStateOf(emptyList<BookRow>()) }
    var opening by remember { mutableStateOf(0.0) }

    LaunchedEffect(Unit) {
        accounting.ensureSeeded()
        accounts = db.accountDao().all()
        selected = accounts.firstOrNull()?.id
    }

    LaunchedEffect(selected) {
        val acc = selected ?: return@LaunchedEffect
        opening = db.journalDao().balanceBefore(acc, monthStart())
        val list = db.journalDao().observeAccountEntries(acc, monthStart(), todayEnd()).first()
        rows = list.sortedByDescending { it.entryDate }.map {
            BookRow(it.entryDate, it.voucherNo, it.particulars, it.cashEffect)
        }
    }

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        DropdownField("Ledger account",
            accounts.map { it.id to "${it.code} — ${it.name}" }, selected) { selected = it }
        Spacer(Modifier.height(6.dp))
        Text("Balance: ${fmt(opening + rows.sumOf { it.effect })}",
            fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        BookTable(Modifier.weight(1f), rows)
        OutlinedButton(onClick = { printBook(context, "Ledger", opening, rows) },
            modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Print, null); Text("  Print / Save PDF")
        }
    }
}

// ----------------------------------------------------------------
// TRIAL BALANCE
// ----------------------------------------------------------------
@Composable
private fun TrialBalanceTab(accounting: AccountingRepo, context: android.content.Context) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf(emptyList<TBRow>()) }
    LaunchedEffect(Unit) {
        val tb = accounting.trialBalance(monthStart(), todayEnd())
        rows = tb.filter { it.netBalance != 0.0 }.map {
            TBRow(it.code, it.name,
                if (it.netBalance > 0) it.netBalance else 0.0,
                if (it.netBalance < 0) -it.netBalance else 0.0)
        }
    }
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Text("TRIAL BALANCE — this month", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("ACCOUNT", Modifier.weight(2.4f), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text("DEBIT", Modifier.weight(0.9f), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text("CREDIT", Modifier.weight(0.9f), fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
        Divider()
        LazyColumn(Modifier.weight(1f)) {
            items(rows) { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text("${r.code} ${r.name}", Modifier.weight(2.4f), fontSize = 12.sp, maxLines = 1)
                    Text(if (r.debit > 0) fmt(r.debit) else "", Modifier.weight(0.9f), fontSize = 12.sp)
                    Text(if (r.credit > 0) fmt(r.credit) else "", Modifier.weight(0.9f), fontSize = 12.sp)
                }
            }
        }
        Divider()
        Text("Dr ${fmt(rows.sumOf { it.debit })}  =  Cr ${fmt(rows.sumOf { it.credit })}",
            fontWeight = FontWeight.Bold, fontSize = 14.sp,
            modifier = Modifier.padding(vertical = 6.dp))
        OutlinedButton(onClick = {
            val file = PdfExport.bookPdf(context, "Trial Balance", "DeryAccount",
                listOf("CODE  ACCOUNT  DR  CR"),
                rows.map { listOf("${it.code} ${it.name}",
                    if (it.debit > 0) fmt(it.debit) else "",
                    if (it.credit > 0) fmt(it.credit) else "") },
                listOf("Dr ${fmt(rows.sumOf { it.debit })} = Cr ${fmt(rows.sumOf { it.credit })}"))
            try { PdfExport.printPdf(context, file, "Trial Balance") } catch (_: Exception) {}
        }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Print, null); Text("  Print / Save PDF")
        }
    }
}

private data class TBRow(val code: String, val name: String, val debit: Double, val credit: Double)

// ----------------------------------------------------------------
// INCOME STATEMENT
// ----------------------------------------------------------------
@Composable
private fun IncomeStatementTab(accounting: AccountingRepo, context: android.content.Context) {
    val scope = rememberCoroutineScope()
    var stmt by remember { mutableStateOf<AccountingRepo.IncomeStatement?>(null) }
    LaunchedEffect(Unit) {
        stmt = accounting.incomeStatement(monthStart(), todayEnd())
    }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
        item {
            Text("INCOME STATEMENT — this month", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
        }
        stmt?.let { s ->
            if (s.income.isEmpty() && s.expenses.isEmpty()) {
                item {
                    Text("No entries yet. Post entries in the Cash Book first.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@LazyColumn
            }
            item { Text("INCOME", fontWeight = FontWeight.Bold, fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary) }
            items(s.income.size) { i ->
                StatementRow(s.income[i].first, s.income[i].second, false)
            }
            item {
                Text("Total Income: ${fmt(s.totalIncome)}", fontWeight = FontWeight.Bold,
                    fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                Spacer(Modifier.height(8.dp))
                Text("EXPENSES", fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error)
            }
            items(s.expenses.size) { i ->
                StatementRow(s.expenses[i].first, s.expenses[i].second, true)
            }
            item {
                Text("Total Expenses: ${fmt(s.totalExpenses)}", fontWeight = FontWeight.Bold,
                    fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                Divider()
                Text(
                    if (s.netProfit >= 0) "NET PROFIT: ${fmt(s.netProfit)}"
                    else "NET LOSS: ${fmt(-s.netProfit)}",
                    fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                    color = if (s.netProfit >= 0) Color(0xFF2E7D32) else Color(0xFFC62828),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                OutlinedButton(onClick = {
                    val body = s.income.map { listOf("Income", it.first, fmt(it.second)) } +
                        s.expenses.map { listOf("Expense", it.first, fmt(it.second)) }
                    val file = PdfExport.bookPdf(context, "Income Statement", "DeryAccount",
                        listOf("TYPE  ACCOUNT  AMOUNT"), body,
                        listOf("Total Income ${fmt(s.totalIncome)}",
                               "Total Expenses ${fmt(s.totalExpenses)}",
                               if (s.netProfit >= 0) "NET PROFIT ${fmt(s.netProfit)}"
                               else "NET LOSS ${fmt(-s.netProfit)}"))
                    try { PdfExport.printPdf(context, file, "Income Statement") } catch (_: Exception) {}
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Print, null); Text("  Print / Save PDF")
                }
            }
        }
    }
}

@Composable
private fun StatementRow(name: String, amount: Double, isExpense: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, fontSize = 13.sp)
        Text(fmt(amount), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = if (isExpense) Color(0xFFC62828) else Color(0xFF2E7D32))
    }
}

// ----------------------------------------------------------------
// BALANCE SHEET
// ----------------------------------------------------------------
@Composable
private fun BalanceSheetTab(accounting: AccountingRepo, context: android.content.Context) {
    var sheet by remember { mutableStateOf<BS?>(null) }
    LaunchedEffect(Unit) {
        val tb = accounting.trialBalance("1970-01-01T00:00:00.000Z", todayEnd())
        val assets = tb.filter { it.type == "ASSET" && it.netBalance != 0.0 }
            .map { it.name to it.netBalance }
        val liabilities = tb.filter { it.type == "LIABILITY" && it.netBalance != 0.0 }
            .map { it.name to -it.netBalance }
        val equityAcc = tb.filter { it.type == "EQUITY" && it.netBalance != 0.0 }
            .map { it.name to -it.netBalance }
        val income = tb.filter { it.type == "INCOME" }.sumOf { -it.netBalance }
        val expenses = tb.filter { it.type == "EXPENSE" }.sumOf { it.netBalance }
        val profit = income - expenses
        sheet = BS(assets, liabilities, equityAcc, profit)
    }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("BALANCE SHEET", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        LazyColumn(Modifier.weight(1f)) {
            sheet?.let { sh ->
                item { Section("ASSETS", Color(0xFF2E7D32)) }
                items(sh.assets.size) { i ->
                    StatementRow(sh.assets[i].first, sh.assets[i].second, false)
                }
                item {
                    Text("Total Assets: ${fmt(sh.assets.sumOf { it.second })}",
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                    Section("LIABILITIES", Color(0xFFC62828))
                }
                items(sh.liabilities.size) { i ->
                    StatementRow(sh.liabilities[i].first, sh.liabilities[i].second, true)
                }
                item {
                    Text("Total Liabilities: ${fmt(sh.liabilities.sumOf { it.second })}",
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                    Section("EQUITY", MaterialTheme.colorScheme.primary)
                }
                items(sh.equity.size) { i ->
                    StatementRow(sh.equity[i].first, sh.equity[i].second, false)
                }
                item {
                    StatementRow("Net Profit (this period)", sh.profit, false)
                    val equityTotal = sh.equity.sumOf { it.second } + sh.profit
                    val total = sh.assets.sumOf { it.second }
                    Text("Total Equity: ${fmt(equityTotal)}",
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                    Divider()
                    Text("ASSETS ${fmt(total)}  =  LIAB + EQUITY ${fmt(sh.liabilities.sumOf { it.second } + equityTotal)}",
                        fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = {
                        val body = sh.assets.map { listOf("Asset", it.first, fmt(it.second)) } +
                            sh.liabilities.map { listOf("Liability", it.first, fmt(it.second)) } +
                            sh.equity.map { listOf("Equity", it.first, fmt(it.second)) } +
                            listOf(listOf("Equity", "Net Profit", fmt(sh.profit)))
                        val file = PdfExport.bookPdf(context, "Balance Sheet", "DeryAccount",
                            listOf("TYPE  ACCOUNT  AMOUNT"), body,
                            listOf("Assets ${fmt(sh.assets.sumOf { it.second })}",
                                   "Liabilities ${fmt(sh.liabilities.sumOf { it.second })}",
                                   "Equity ${fmt(sh.equity.sumOf { it.second } + sh.profit)}"))
                        try { PdfExport.printPdf(context, file, "Balance Sheet") } catch (_: Exception) {}
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Print, null); Text("  Print / Save PDF")
                    }
                }
            } ?: item {
                Text("No entries yet.", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private data class BS(val assets: List<Pair<String, Double>>,
                      val liabilities: List<Pair<String, Double>>,
                      val equity: List<Pair<String, Double>>,
                      val profit: Double)

@Composable
private fun Section(title: String, color: Color) {
    Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = color,
        modifier = Modifier.padding(top = 6.dp))
}

/** Print the ruled cashbook/ledger rows as a PDF book page. */
private fun printBook(context: android.content.Context, title: String,
                      opening: Double, rows: List<BookRow>) {
    val body = rows.map { listOf(shortDate(it.date), it.voucher, it.particulars,
        if (it.effect > 0) fmt(it.effect) else "",
        if (it.effect < 0) fmt(-it.effect) else "") }
    val footer = mutableListOf("Opening B/F ${fmt(opening)}",
        "Closing balance ${fmt(opening + rows.sumOf { it.effect })}")
    val file = PdfExport.bookPdf(context, title, "DeryAccount",
        listOf("DATE  VCHR  PARTICULARS  RECEIPT  PAYMENT"), body, footer)
    try { PdfExport.printPdf(context, file, title) } catch (_: Exception) {}
}

// ----------------------------------------------------------------
private fun fmt(v: Double): String = "UGX %,d".format(v.toLong())
private fun shortDate(iso: String): String = try {
    val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).parse(iso)
    SimpleDateFormat("dd/MM", Locale.US).format(parsed!!)
} catch (e: Exception) { iso.take(10) }
private fun monthStart(): String {
    val cal = Calendar.getInstance()
    cal.set(Calendar.DAY_OF_MONTH, 1)
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time) + "T00:00:00.000Z"
}
private fun todayEnd(): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())
