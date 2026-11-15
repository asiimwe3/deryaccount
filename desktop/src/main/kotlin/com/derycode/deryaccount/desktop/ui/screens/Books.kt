package com.derycode.deryaccount.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.desktop.data.*
import com.derycode.deryaccount.desktop.ui.*
import java.io.File

@Composable
fun BooksScreen(app: AppState) {
    val repo = app.repo
    var tab by remember { mutableStateOf(0) }
    var from by remember { mutableStateOf(Clock.monthStart()) }
    var to by remember { mutableStateOf(Clock.today()) }
    var showEntry by remember { mutableStateOf(false) }
    var openEntry by remember { mutableStateOf<Row?>(null) }
    var showRemit by remember { mutableStateOf(false) }

    val fromIso = "${from}T00:00:00"
    val toIso = "${to}T23:59:59"

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        ScreenHeader("Books of account", "Double entry, always balanced · period $from → $to") {
            Btn("Journal entry", { showEntry = true }, Icons.Default.Edit)
        }
        TabBar(listOf("Chart of accounts", "Journal", "Trial balance", "Income statement", "Balance sheet", "VAT"), tab, { tab = it })
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.width(150.dp)) { Field("From", from, { from = it }) }
            Box(Modifier.width(150.dp)) { Field("To", to, { to = it }) }
            Spacer(Modifier.weight(1f))
            Btn("This month", { from = Clock.monthStart(); to = Clock.today() }, kind = BtnKind.GHOST)
            Btn("This year", { from = Clock.today().take(4) + "-01-01"; to = Clock.today() }, kind = BtnKind.GHOST)
        }
        Spacer(Modifier.height(12.dp))

        when (tab) {
            0 -> {
                val accounts = remember(app.tick, from, to) {
                    repo.db.q("SELECT * FROM accounts ORDER BY sort_order, code")
                }
                CardBox(Modifier.fillMaxSize(), padding = 0.dp) {
                    Table(
                        listOf("Code", "Account", "Type", "Balance"),
                        listOf(0.7f, 2.4f, 1f, 1.1f),
                        accounts.map { a ->
                            val bal = repo.accounting.balanceOf(a.str("id"), fromIso, toIso)
                            listOf(
                                Cell(a.str("code"), Da.Muted),
                                Cell(a.str("name"), bold = true),
                                Cell(a.str("type"), Da.Dim),
                                Cell(Fmt.moneySigned(bal), if (bal >= 0) Da.Green else Da.Red, align = TextAlign.End)
                            )
                        }
                    )
                }
            }
            1 -> {
                val entries = remember(app.tick, from, to) {
                    repo.db.q(
                        """SELECT e.*, COALESCE(SUM(l.debit),0) AS amount, COUNT(l.id) AS lines
                           FROM journal_entries e LEFT JOIN journal_lines l ON l.entry_id=e.id
                           WHERE e.entry_date BETWEEN ? AND ?
                           GROUP BY e.id ORDER BY e.entry_date DESC LIMIT 400""",
                        fromIso, toIso
                    )
                }
                CardBox(Modifier.fillMaxSize(), padding = 0.dp) {
                    Table(
                        listOf("Voucher", "Date", "Particulars", "Source", "Amount"),
                        listOf(1f, 1.1f, 3f, 1f, 1.1f),
                        entries.map {
                            listOf(
                                Cell(it.str("voucher_no"), Da.Muted),
                                Cell(Fmt.date(it.str("entry_date")), Da.Dim),
                                Cell(it.str("particulars")),
                                Cell(it.str("source"), Da.Blue),
                                Cell(Fmt.money(it.dbl("amount")), Da.Text, align = TextAlign.End)
                            )
                        },
                        onRowClick = { i -> openEntry = entries[i] }
                    )
                }
            }
            2 -> {
                val rows = remember(app.tick, from, to) { app.reports.trialBalance(fromIso, toIso) }
                val totalDr = rows.sumOf { if (it.balance > 0) it.balance else 0.0 }
                val totalCr = rows.sumOf { if (it.balance < 0) -it.balance else 0.0 }
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        KpiCard("Debit balances", Fmt.money(totalDr), "assets & expenses", Da.Green, Modifier.weight(1f))
                        KpiCard("Credit balances", Fmt.money(totalCr), "liabilities, equity & income", Da.Blue, Modifier.weight(1f))
                        KpiCard("Difference", Fmt.money(totalDr - totalCr),
                            if (kotlin.math.abs(totalDr - totalCr) < 1) "the books balance" else "something is wrong",
                            if (kotlin.math.abs(totalDr - totalCr) < 1) Da.Green else Da.Red, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    CardBox(Modifier.weight(1f), padding = 0.dp) {
                        Table(
                            listOf("Code", "Account", "Debit", "Credit"),
                            listOf(0.7f, 2.6f, 1.1f, 1.1f),
                            rows.map {
                                listOf(
                                    Cell(it.code, Da.Muted), Cell(it.name, bold = true),
                                    Cell(if (it.balance > 0) Fmt.money(it.balance) else "", Da.Text, align = TextAlign.End),
                                    Cell(if (it.balance < 0) Fmt.money(-it.balance) else "", Da.Text, align = TextAlign.End)
                                )
                            }
                        )
                    }
                }
            }
            3 -> {
                val is1 = remember(app.tick, from, to) { app.reports.incomeStatement(fromIso, toIso) }
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CardBox(Modifier.weight(2f)) {
                        Text("INCOME STATEMENT", fontSize = 10.sp, color = Da.Muted, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text("${Fmt.date(fromIso)} → ${Fmt.date(toIso)}", fontSize = 11.sp, color = Da.Dim)
                        Spacer(Modifier.height(14.dp))
                        StatementLine("Sales (gross)", Fmt.money(is1.sales))
                        StatementLine("Less: sales returns", "-" + Fmt.money(is1.returns))
                        StatementLine("Less: sales discounts", "-" + Fmt.money(is1.discounts))
                        StatementLine("NET SALES", Fmt.money(is1.netSales), bold = true)
                        Spacer(Modifier.height(10.dp))
                        StatementLine("Cost of goods sold", Fmt.money(is1.cogs))
                        StatementLine("GROSS PROFIT", Fmt.money(is1.grossProfit), bold = true)
                        Spacer(Modifier.height(10.dp))
                        Text("OPERATING EXPENSES", fontSize = 10.sp, color = Da.Muted, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        is1.expenses.forEach { (name, v) -> StatementLine(name, Fmt.money(v)) }
                        if (is1.expenses.isEmpty()) Text("No expenses in this period", fontSize = 11.sp, color = Da.Dim)
                        Spacer(Modifier.height(8.dp))
                        StatementLine("Total expenses", Fmt.money(is1.expenses.sumOf { it.second }))
                        Spacer(Modifier.height(10.dp))
                        StatementLine("Other income", Fmt.money(is1.otherIncome))
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Da.Surface2).padding(12.dp)
                        ) {
                            Text("NET PROFIT", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Da.Text, modifier = Modifier.weight(1f))
                            Text(Fmt.money(is1.netProfit), fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                color = if (is1.netProfit >= 0) Da.Green else Da.Red)
                        }
                    }
                    CardBox(Modifier.weight(1f)) {
                        Text("How it is built", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Revenue is what customers were charged, before discounts. Cost of goods sold comes from the cost price captured on each sale, " +
                                "so gross profit is real profit, not cash in the drawer.\n\nPurchases in this period: ${Fmt.money(is1.purchases)}.\n\n" +
                                "Stock, debtors and cash are on the balance sheet, not here: only income and expenses belong in this statement.",
                            fontSize = 11.5.sp, color = Da.Muted, lineHeight = 17.sp
                        )
                    }
                }
            }
            4 -> {
                val (assets, liabilities, equity) = remember(app.tick, from, to) { app.reports.balanceSheet(toIso) }
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CardBox(Modifier.weight(1f)) {
                        Text("ASSETS", fontSize = 10.sp, color = Da.Muted, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(10.dp))
                        assets.forEach { StatementLine(it.label, Fmt.money(it.amount), bold = it.bold) }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        CardBox(Modifier.fillMaxWidth()) {
                            Text("LIABILITIES", fontSize = 10.sp, color = Da.Muted, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Spacer(Modifier.height(10.dp))
                            if (liabilities.isEmpty()) Text("Nothing owed", fontSize = 11.5.sp, color = Da.Dim)
                            liabilities.forEach { StatementLine(it.label, Fmt.money(it.amount), bold = it.bold) }
                        }
                        CardBox(Modifier.fillMaxWidth()) {
                            Text("EQUITY", fontSize = 10.sp, color = Da.Muted, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Spacer(Modifier.height(10.dp))
                            equity.forEach { StatementLine(it.label, Fmt.money(it.amount), bold = it.bold) }
                        }
                        CardBox(Modifier.fillMaxWidth()) {
                            val check = app.reports.checkBalance()
                            Text("Balance check", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                            Text(
                                if (kotlin.math.abs(check) < 1) "Assets = liabilities + equity. The books are sound."
                                else "Off by ${Fmt.money(check)} — check the journal for a one-sided entry.",
                                fontSize = 11.5.sp, color = if (kotlin.math.abs(check) < 1) Da.Green else Da.Red
                            )
                        }
                    }
                }
            }
            5 -> {
                val ym by remember { mutableStateOf(Clock.thisMonth()) }
                var month by remember { mutableStateOf(Clock.thisMonth()) }
                val (output, remitted, payable) = remember(app.tick, month) { app.reports.vatReport(month) }
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                    CardBox(Modifier.weight(1.3f)) {
                        Text("VAT position", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                        Spacer(Modifier.height(8.dp))
                        Field("Month (yyyy-mm)", month, { month = it })
                        Spacer(Modifier.height(12.dp))
                        StatementLine("Output VAT charged", Fmt.money(output))
                        StatementLine("Remitted to URA", Fmt.money(remitted))
                        StatementLine("Still payable", Fmt.money(payable), bold = true)
                        Spacer(Modifier.height(12.dp))
                        Btn("Record VAT remittance", { showRemit = true }, Icons.Default.AccountBalance, BtnKind.PRIMARY)
                    }
                    CardBox(Modifier.weight(1f)) {
                        Text("VAT only applies if you charge it", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Most small shops in Uganda are not VAT registered, so products default to 0% and the VAT report stays at zero. " +
                                "Set a VAT rate on a product (say 18%) and every sale of it starts building the VAT payable above, " +
                                "while the price the customer sees stays what you typed.",
                            fontSize = 11.5.sp, color = Da.Muted, lineHeight = 17.sp
                        )
                    }
                }
            }
        }
    }

    openEntry?.let { e ->
        val lines = remember(e.str("id")) {
            repo.db.q(
                "SELECT l.*, a.code, a.name FROM journal_lines l JOIN accounts a ON a.id=l.account_id WHERE l.entry_id=? ORDER BY l.debit DESC",
                e.str("id")
            )
        }
        DialogBox("${e.str("voucher_no")} · ${e.str("particulars")}", { openEntry = null }, 560.dp) {
            FormRow("Date", Fmt.dateTime(e.str("entry_date")))
            FormRow("Source", e.str("source"))
            Spacer(Modifier.height(10.dp))
            Table(
                listOf("Account", "Debit", "Credit"),
                listOf(2.5f, 1f, 1f),
                lines.map {
                    listOf(
                        Cell("${it.str("code")} ${it.str("name")}"),
                        Cell(if (it.dbl("debit") > 0) Fmt.money(it.dbl("debit")) else "", Da.Text, align = TextAlign.End),
                        Cell(if (it.dbl("credit") > 0) Fmt.money(it.dbl("credit")) else "", Da.Text, align = TextAlign.End)
                    )
                }
            )
        }
    }

    if (showEntry) ManualJournalDialog(app, onClose = { showEntry = false })

    if (showRemit) {
        var amount by remember { mutableStateOf("") }
        var period by remember { mutableStateOf(Clock.thisMonth()) }
        DialogBox("Record VAT remittance", { showRemit = false }, 480.dp) {
            Field("Period (yyyy-mm)", period, { period = it })
            Spacer(Modifier.height(10.dp))
            MoneyField("Amount paid to URA", amount, { amount = it.filter { c -> c.isDigit() || c == '.' } })
            Spacer(Modifier.height(14.dp))
            Btn("Post remittance", {
                repo.accounting.postVatRemittance(amount.toDoubleOrNull() ?: 0.0, period)
                app.refresh(); app.say("VAT remittance posted"); showRemit = false
            }, Icons.Default.Check, BtnKind.PRIMARY)
        }
    }
}

@Composable
fun StatementLine(label: String, value: String, bold: Boolean = false, color: androidx.compose.ui.graphics.Color = Da.Text) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = if (bold) 12.5.sp else 11.5.sp,
            color = if (bold) Da.Text else Da.Muted,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f))
        Text(value, fontSize = if (bold) 12.5.sp else 11.5.sp,
            color = if (bold) color else Da.Text,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun ManualJournalDialog(app: AppState, onClose: () -> Unit) {
    val accounts = remember { app.repo.db.q("SELECT * FROM accounts ORDER BY sort_order, code") }
    var particulars by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(Clock.today()) }
    var rows by remember { mutableStateOf(listOf(Triple("", "", ""), Triple("", "", ""))) }
    val labels = accounts.map { "${it.str("code")} ${it.str("name")}" }
    val totalDr = rows.sumOf { it.second.toDoubleOrNull() ?: 0.0 }
    val totalCr = rows.sumOf { it.third.toDoubleOrNull() ?: 0.0 }

    DialogBox("Manual journal entry", onClose, 700.dp) {
        Text("Every entry needs debits that equal credits. Use this for capital, drawings, corrections and adjustments.",
            fontSize = 11.5.sp, color = Da.Muted)
        Spacer(Modifier.height(12.dp))
        Row {
            Box(Modifier.weight(2f)) { Field("Particulars", particulars, { particulars = it }) }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) { Field("Date", date, { date = it }) }
        }
        Spacer(Modifier.height(12.dp))
        rows.forEachIndexed { i, row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(2.2f)) {
                    Drop("Account", labels, row.first, { v ->
                        rows = rows.toMutableList().also { it[i] = Triple(v, row.second, row.third) }
                    })
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    MoneyField("Debit", row.second, { v ->
                        rows = rows.toMutableList().also { it[i] = Triple(row.first, v, "") }
                    })
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    MoneyField("Credit", row.third, { v ->
                        rows = rows.toMutableList().also { it[i] = Triple(row.first, "", v) }
                    })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Btn("Add line", { rows = rows + Triple("", "", "") }, Icons.Default.Add, BtnKind.GHOST)
            Spacer(Modifier.width(8.dp))
            Text("Dr ${Fmt.money(totalDr)} · Cr ${Fmt.money(totalCr)}", fontSize = 12.sp,
                color = if (kotlin.math.abs(totalDr - totalCr) < 0.01 && totalDr > 0) Da.Green else Da.Red,
                modifier = Modifier.padding(top = 11.dp))
        }
        Spacer(Modifier.height(16.dp))
        Btn("Post entry", {
            try {
                val debits = rows.filter { it.first.isNotBlank() && (it.second.toDoubleOrNull() ?: 0.0) > 0 }
                    .map { accounts.first { a -> "${a.str("code")} ${a.str("name")}" == it.first }.str("id") to (it.second.toDoubleOrNull() ?: 0.0) }
                val credits = rows.filter { it.first.isNotBlank() && (it.third.toDoubleOrNull() ?: 0.0) > 0 }
                    .map { accounts.first { a -> "${a.str("code")} ${a.str("name")}" == it.first }.str("id") to (it.third.toDoubleOrNull() ?: 0.0) }
                app.repo.accounting.postManual("${date}T12:00:00", particulars.ifBlank { "Manual journal" }, debits, credits)
                app.refresh(); app.say("Journal entry posted"); onClose()
            } catch (e: Exception) { app.say(e.message ?: "Entry rejected") }
        }, Icons.Default.Check, BtnKind.PRIMARY)
    }
}
