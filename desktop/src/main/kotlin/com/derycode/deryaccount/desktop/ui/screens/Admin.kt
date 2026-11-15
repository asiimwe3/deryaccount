package com.derycode.deryaccount.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import java.util.UUID

@Composable
fun AdminScreen(app: AppState) {
    var tab by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        ScreenHeader("Assets, staff & money", "Fixed assets, payroll, budgets and bank reconciliation")
        TabBar(listOf("Fixed assets", "Employees", "Payroll", "Budgets", "Bank reconciliation"), tab, { tab = it })
        Spacer(Modifier.height(14.dp))
        when (tab) {
            0 -> AssetsTab(app)
            1 -> EmployeesTab(app)
            2 -> PayrollTab(app)
            3 -> BudgetsTab(app)
            else -> BankTab(app)
        }
    }
}

@Composable
private fun AssetsTab(app: AppState) {
    val admin = app.admin
    var showAdd by remember { mutableStateOf(false) }
    val assets = remember(app.tick) { admin.assets() }
    val cost = assets.sumOf { it.dbl("cost") }
    val accumulated = assets.sumOf { it.dbl("accumulated_depreciation") }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCard("Assets at cost", Fmt.money(cost), "${assets.size} items", Da.Blue, Modifier.weight(1f))
            KpiCard("Depreciated so far", Fmt.money(accumulated), "charged to the books", Da.Amber, Modifier.weight(1f))
            KpiCard("Book value", Fmt.money(cost - accumulated), "what they are worth now", Da.Green, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Add asset", { showAdd = true }, Icons.Default.Add)
            Btn("Run 1 month depreciation", {
                val posted = admin.runDepreciation(1)
                app.refresh(); app.say(if (posted > 0) "Depreciation ${Fmt.money(posted)} posted" else "Nothing left to depreciate")
            }, Icons.Default.TrendingDown, BtnKind.GHOST)
        }
        Spacer(Modifier.height(12.dp))
        CardBox(Modifier.weight(1f), padding = 0.dp) {
            Table(
                listOf("Asset", "Category", "Bought", "Cost", "Depreciated", "Book value", "Life"),
                listOf(2f, 1f, 1f, 1f, 1.1f, 1.1f, 0.7f),
                assets.map {
                    val book = it.dbl("cost") - it.dbl("accumulated_depreciation")
                    listOf(
                        Cell(it.str("name"), bold = true),
                        Cell(it.str("category"), Da.Muted),
                        Cell(Fmt.date(it.str("purchase_date")), Da.Dim),
                        Cell(Fmt.money(it.dbl("cost")), Da.Text, align = TextAlign.End),
                        Cell(Fmt.money(it.dbl("accumulated_depreciation")), Da.Amber, align = TextAlign.End),
                        Cell(Fmt.money(book), Da.Green, align = TextAlign.End),
                        Cell("${it.int("life_months")}m", Da.Dim, align = TextAlign.End)
                    )
                }
            )
        }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("EQUIPMENT") }
        var costText by remember { mutableStateOf("") }
        var salvage by remember { mutableStateOf("0") }
        var date by remember { mutableStateOf(Clock.today()) }
        var life by remember { mutableStateOf("60") }
        var paidFrom by remember { mutableStateOf("CASH") }
        DialogBox("Add a fixed asset", { showAdd = false }, 560.dp) {
            Field("Asset name", name, { name = it }, placeholder = "e.g. Delivery motorcycle")
            Spacer(Modifier.height(10.dp))
            Row {
                Box(Modifier.weight(1f)) { Drop("Category", listOf("EQUIPMENT", "FURNITURE", "VEHICLE", "BUILDING", "OTHER"), category, { category = it }) }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) { Field("Purchase date", date, { date = it }) }
            }
            Spacer(Modifier.height(10.dp))
            Row {
                Box(Modifier.weight(1f)) { MoneyField("Cost", costText, { costText = it.filter { c -> c.isDigit() || c == '.' } }) }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) { MoneyField("Salvage value", salvage, { salvage = it.filter { c -> c.isDigit() || c == '.' } }) }
            }
            Spacer(Modifier.height(10.dp))
            Row {
                Box(Modifier.weight(1f)) { Field("Useful life (months)", life, { life = it.filter { c -> c.isDigit() } }, numeric = true) }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) { Drop("Paid from", listOf("CASH", "BANK", "MTN_MOMO"), paidFrom, { paidFrom = it }) }
            }
            Spacer(Modifier.height(16.dp))
            Btn("Save asset", {
                try {
                    admin.addAsset(name, category, costText.toDoubleOrNull() ?: 0.0,
                        salvage.toDoubleOrNull() ?: 0.0, date, life.toIntOrNull() ?: 12, paidFrom)
                    app.refresh(); app.say("Asset recorded and posted"); showAdd = false
                } catch (e: Exception) { app.say(e.message ?: "Could not save") }
            }, Icons.Default.Save, BtnKind.PRIMARY)
        }
    }
}

@Composable
private fun EmployeesTab(app: AppState) {
    var showAdd by remember { mutableStateOf(false) }
    val employees = remember(app.tick) { app.admin.employees() }
    Column(Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Add employee", { showAdd = true }, Icons.Default.PersonAdd)
        }
        Spacer(Modifier.height(12.dp))
        CardBox(Modifier.weight(1f), padding = 0.dp) {
            Table(
                listOf("Name", "Role", "Phone", "Monthly salary", "Active"),
                listOf(2f, 1.4f, 1.3f, 1.2f, 0.8f),
                employees.map {
                    listOf(
                        Cell(it.str("name"), bold = true),
                        Cell(it.str("role"), Da.Muted),
                        Cell(it.str("phone"), Da.Dim),
                        Cell(Fmt.money(it.dbl("monthly_salary")), Da.Green, align = TextAlign.End),
                        Cell(if (it.bool("is_active")) "yes" else "no", if (it.bool("is_active")) Da.Green else Da.Dim)
                    )
                },
                onRowClick = { i ->
                    app.repo.db.update("employees", employees[i].str("id"),
                        mapOf("is_active" to if (employees[i].bool("is_active")) 0 else 1))
                    app.refresh()
                }
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("Click a row to switch someone active or inactive. Only staff you pay appear in payroll.",
            fontSize = 11.sp, color = Da.Dim)
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var role by remember { mutableStateOf("SHOP ASSISTANT") }
        var phone by remember { mutableStateOf("") }
        var salary by remember { mutableStateOf("") }
        DialogBox("Add employee", { showAdd = false }, 520.dp) {
            Field("Full name", name, { name = it })
            Spacer(Modifier.height(10.dp))
            Row {
                Box(Modifier.weight(1f)) { Drop("Role", listOf("SHOP ASSISTANT", "CASHIER", "DRIVER", "GUARD", "OTHER"), role, { role = it }) }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) { Field("Phone", phone, { phone = it }) }
            }
            Spacer(Modifier.height(10.dp))
            MoneyField("Monthly salary", salary, { salary = it.filter { c -> c.isDigit() || c == '.' } })
            Spacer(Modifier.height(16.dp))
            Btn("Save employee", {
                if (name.isBlank()) { app.say("Name is required"); return@Btn }
                app.repo.db.insert("employees", mapOf(
                    "id" to UUID.randomUUID().toString(), "name" to name.trim(), "role" to role,
                    "phone" to phone.trim().ifBlank { null }, "monthly_salary" to (salary.toDoubleOrNull() ?: 0.0),
                    "is_active" to 1, "created_at" to Clock.nowIso()
                ))
                app.refresh(); app.say("Employee saved"); showAdd = false
            }, Icons.Default.Save, BtnKind.PRIMARY)
        }
    }
}

@Composable
private fun PayrollTab(app: AppState) {
    var month by remember { mutableStateOf(Clock.thisMonth()) }
    var deductionPct by remember { mutableStateOf("5") }
    var method by remember { mutableStateOf("CASH") }
    val employees = remember(app.tick) { app.admin.employees().filter { it.bool("is_active") } }
    val payslips = remember(app.tick, month) { app.admin.payslips(month) }
    val gross = employees.sumOf { it.dbl("monthly_salary") }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        CardBox(Modifier.weight(1f)) {
            Text("Run payroll", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Da.Text)
            Spacer(Modifier.height(8.dp))
            Field("Month (yyyy-mm)", month, { month = it })
            Spacer(Modifier.height(8.dp))
            Row {
                Box(Modifier.weight(1f)) { Field("Deductions % (NSSF, PAYE)", deductionPct, { deductionPct = it.filter { c -> c.isDigit() || c == '.' } }, numeric = true) }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) { Drop("Paid via", listOf("CASH", "MTN_MOMO", "AIRTEL_MONEY", "BANK"), method, { method = it }) }
            }
            Spacer(Modifier.height(12.dp))
            employees.forEach {
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(it.str("name"), fontSize = 12.sp, color = Da.Text, modifier = Modifier.weight(1f))
                    Text(Fmt.money(it.dbl("monthly_salary")), fontSize = 12.sp, color = Da.Muted)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Gross payroll: ${Fmt.money(gross)}", fontSize = 12.sp, color = Da.Green, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Btn("Pay ${Fmt.month(month)}", {
                try {
                    app.admin.runPayroll(month, deductionPct.toDoubleOrNull() ?: 0.0, method, employees.map { it.str("id") })
                    app.refresh(); app.say("Payroll posted for ${Fmt.month(month)}")
                } catch (e: Exception) { app.say(e.message ?: "Could not run payroll") }
            }, Icons.Default.Payments, BtnKind.PRIMARY, enabled = employees.isNotEmpty())
        }
        CardBox(Modifier.weight(1.4f), padding = 0.dp) {
            Column {
                Text("Payslips ${Fmt.month(month)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Da.Text,
                    modifier = Modifier.padding(12.dp))
                Table(
                    listOf("Employee", "Gross", "Deductions", "Net paid", "Method"),
                    listOf(2f, 1f, 1f, 1f, 1f),
                    payslips.map {
                        listOf(
                            Cell(it.str("employee_name"), bold = true),
                            Cell(Fmt.money(it.dbl("gross")), Da.Muted, align = TextAlign.End),
                            Cell(Fmt.money(it.dbl("deductions")), Da.Amber, align = TextAlign.End),
                            Cell(Fmt.money(it.dbl("net")), Da.Green, align = TextAlign.End),
                            Cell(methodLabel(it.str("method")), Da.Blue)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun BudgetsTab(app: AppState) {
    var month by remember { mutableStateOf(Clock.thisMonth()) }
    var revenueTarget by remember { mutableStateOf("") }
    var expenseTargets by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val rows = remember(app.tick, month) { app.reports.budgetVsActual(month) }
    val expenseAccounts = remember { app.repo.db.scalar("SELECT COALESCE(SUM(amount),0) FROM budgets WHERE 1=0") }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        CardBox(Modifier.weight(1f)) {
            Text("Set budgets", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Da.Text)
            Spacer(Modifier.height(8.dp))
            Field("Month (yyyy-mm)", month, { month = it })
            Spacer(Modifier.height(10.dp))
            MoneyField("Revenue target", revenueTarget, { revenueTarget = it.filter { c -> c.isDigit() || c == '.' } })
            Spacer(Modifier.height(6.dp))
            listOf("RENT", "SALARIES", "TRANSPORT", "UTILITIES", "AIRTIME", "OTHER").forEach { cat ->
                Spacer(Modifier.height(6.dp))
                MoneyField("$cat budget", expenseTargets[cat] ?: "", { v ->
                    expenseTargets = expenseTargets + (cat to v.filter { c -> c.isDigit() || c == '.' })
                })
            }
            Spacer(Modifier.height(12.dp))
            Btn("Save budgets", {
                revenueTarget.toDoubleOrNull()?.let { app.admin.saveBudget(month, "REVENUE", "REVENUE", it) }
                expenseTargets.forEach { (cat, v) ->
                    v.toDoubleOrNull()?.let { app.admin.saveBudget(month, "EXPENSE", cat, it) }
                }
                app.refresh(); app.say("Budgets saved")
            }, Icons.Default.Save, BtnKind.PRIMARY)
        }
        CardBox(Modifier.weight(1.5f)) {
            Text("Budget vs actual · ${Fmt.month(month)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Da.Text)
            Spacer(Modifier.height(12.dp))
            if (rows.isEmpty()) EmptyState("No budgets set for this month yet.")
            rows.forEach { (b, actual, remaining) ->
                val target = b.dbl("amount")
                val pct = if (target > 0) actual / target else 0.0
                Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${b.str("kind")} · ${b.str("category")}", fontSize = 12.sp, color = Da.Text,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${Fmt.money(actual)} of ${Fmt.money(target)}", fontSize = 11.5.sp, color = Da.Muted)
                    }
                    Spacer(Modifier.height(5.dp))
                    MiniProgress(pct.toFloat(), if (b.str("kind") == "REVENUE") Da.Green else if (pct > 1f) Da.Red else Da.Amber)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (b.str("kind") == "REVENUE") "${Fmt.pct(pct * 100)} of target"
                        else if (pct > 1) "over budget by ${Fmt.money(actual - target)}" else "${Fmt.money(remaining)} still available",
                        fontSize = 10.5.sp, color = Da.Dim
                    )
                }
            }
        }
    }
}

@Composable
private fun BankTab(app: AppState) {
    var showAdd by remember { mutableStateOf(false) }
    val lines = remember(app.tick) { app.admin.bankLines() }
    val matched = lines.count { it.bool("is_matched") }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCard("Statement lines", "${lines.size}", "imported or typed", Da.Blue, Modifier.weight(1f))
            KpiCard("Matched", "$matched", "found in the books", Da.Green, Modifier.weight(1f))
            KpiCard("Unmatched", "${lines.size - matched}", "needs a look", Da.Amber, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Add statement line", { showAdd = true }, Icons.Default.Add)
            Btn("Auto-match now", {
                val n = app.admin.autoMatch(); app.refresh()
                app.say(if (n > 0) "$n lines matched to sales and expenses" else "Nothing new matched today")
            }, Icons.Default.CompareArrows, BtnKind.GHOST)
        }
        Spacer(Modifier.height(12.dp))
        CardBox(Modifier.weight(1f), padding = 0.dp) {
            Table(
                listOf("Date", "Description", "In", "Out", "Status", "Reference"),
                listOf(1f, 2.4f, 1f, 1f, 0.9f, 1.2f),
                lines.map {
                    listOf(
                        Cell(Fmt.date(it.str("statement_date")), Da.Dim),
                        Cell(it.str("description")),
                        Cell(if (it.str("direction") == "IN") Fmt.money(it.dbl("amount")) else "", Da.Green, align = TextAlign.End),
                        Cell(if (it.str("direction") == "OUT") Fmt.money(it.dbl("amount")) else "", Da.Red, align = TextAlign.End),
                        Cell(if (it.bool("is_matched")) "matched" else "open", if (it.bool("is_matched")) Da.Green else Da.Amber),
                        Cell(it.str("reference"), Da.Muted)
                    )
                }
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("Auto-match pairs statement lines with a sale or expense of the same amount on the same day. Anything left over is worth investigating.",
            fontSize = 11.sp, color = Da.Dim)
    }

    if (showAdd) {
        var date by remember { mutableStateOf(Clock.today()) }
        var description by remember { mutableStateOf("") }
        var direction by remember { mutableStateOf("IN") }
        var amount by remember { mutableStateOf("") }
        var reference by remember { mutableStateOf("") }
        DialogBox("Add a statement line", { showAdd = false }, 520.dp) {
            Field("Date", date, { date = it })
            Spacer(Modifier.height(10.dp))
            Field("Description", description, { description = it }, placeholder = "e.g. MoMo deposit")
            Spacer(Modifier.height(10.dp))
            Row {
                Box(Modifier.weight(1f)) { Drop("Direction", listOf("IN", "OUT"), direction, { direction = it }) }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) { MoneyField("Amount", amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }) }
            }
            Spacer(Modifier.height(10.dp))
            Field("Reference", reference, { reference = it }, placeholder = "optional")
            Spacer(Modifier.height(16.dp))
            Btn("Save line", {
                app.admin.addBankLine(date, description, direction, amount.toDoubleOrNull() ?: 0.0, reference)
                app.refresh(); app.say("Statement line added"); showAdd = false
            }, Icons.Default.Save, BtnKind.PRIMARY)
        }
    }
}
