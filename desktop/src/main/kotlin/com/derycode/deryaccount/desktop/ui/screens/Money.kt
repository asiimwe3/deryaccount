package com.derycode.deryaccount.desktop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.desktop.data.*
import com.derycode.deryaccount.desktop.ui.*
import java.util.UUID

// ── Expenses ─────────────────────────────────────────────────────────────────
@Composable
fun ExpensesScreen(app: AppState) {
    val repo = app.repo
    var search by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var expenses by remember { mutableStateOf(repo.expenses()) }

    LaunchedEffect(app.tick, search) {
        expenses = repo.expenses().filter {
            search.isBlank() || it.str("category").contains(search, true) || it.str("note").contains(search, true)
        }
    }

    val monthRows = expenses.filter { it.str("spent_at").startsWith(Clock.thisMonth()) }
    val byCategory = monthRows.groupBy { it.str("category") }.mapValues { e -> e.value.sumOf { it.dbl("amount") } }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        ScreenHeader("Expenses", "Rent, transport, power, airtime and everything else that leaves the till") {
            Btn("Record expense", { showAdd = true }, Icons.Default.Add)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCard("This month", Fmt.money(monthRows.sumOf { it.dbl("amount") }), "${monthRows.size} entries", Da.Red, Modifier.weight(1f))
            byCategory.entries.sortedByDescending { it.value }.take(3).forEach { (cat, v) ->
                KpiCard(cat, Fmt.money(v), "this month", Da.Amber, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(16.dp))
        CardBox(Modifier.weight(1f), padding = 0.dp) {
            Row(Modifier.fillMaxWidth().padding(12.dp)) { Box(Modifier.weight(1f)) { SearchBar(search, { search = it }, "Search category or note") } }
            Table(
                listOf("Date", "Category", "Note", "Paid from", "Amount"),
                listOf(1.2f, 1.3f, 2f, 1.1f, 1f),
                expenses.map {
                    listOf(
                        Cell(Fmt.date(it.str("spent_at")), Da.Dim),
                        Cell(it.str("category"), Da.Text, bold = true),
                        Cell(it.str("note"), Da.Muted),
                        Cell(it.str("paid_from"), Da.Blue),
                        Cell(Fmt.money(it.dbl("amount")), Da.Red, bold = true, align = TextAlign.End)
                    )
                }
            )
        }
    }

    if (showAdd) {
        var category by remember { mutableStateOf("RENT") }
        var amount by remember { mutableStateOf("") }
        var paidFrom by remember { mutableStateOf("CASH") }
        var note by remember { mutableStateOf("") }
        DialogBox("Record an expense", { showAdd = false }, 520.dp) {
            Drop("Category", listOf("RENT", "SALARIES", "TRANSPORT", "UTILITIES", "AIRTIME", "OTHER"), category, { category = it })
            Spacer(Modifier.height(10.dp))
            MoneyField("Amount", amount, { amount = it.filter { c -> c.isDigit() || c == '.' } })
            Spacer(Modifier.height(10.dp))
            Drop("Paid from", listOf("CASH", "MTN_MOMO", "AIRTEL_MONEY", "PETTY"), paidFrom, { paidFrom = it })
            Spacer(Modifier.height(10.dp))
            Field("What was it for", note, { note = it }, placeholder = "e.g. monthly shop rent")
            Spacer(Modifier.height(16.dp))
            Btn("Post expense", {
                try {
                    repo.recordExpense(category, amount.toDoubleOrNull() ?: 0.0, paidFrom, note)
                    app.refresh(); app.say("Expense posted"); showAdd = false
                } catch (e: Exception) { app.say(e.message ?: "Could not post") }
            }, Icons.Default.Check, BtnKind.PRIMARY)
        }
    }
}

// ── Customers ────────────────────────────────────────────────────────────────
@Composable
fun CustomersScreen(app: AppState) {
    val repo = app.repo
    var tab by remember { mutableStateOf(0) }
    var search by remember { mutableStateOf("") }
    var customers by remember { mutableStateOf(repo.customers()) }
    var editing by remember { mutableStateOf<Row?>(null) }
    var creating by remember { mutableStateOf(false) }
    var paying by remember { mutableStateOf<Row?>(null) }

    LaunchedEffect(app.tick, search) {
        customers = repo.customers().filter {
            search.isBlank() || it.str("name").contains(search, true) || it.str("phone").contains(search, true)
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        ScreenHeader("Customers", "Who buys from you, who owes you, and how much") {
            Btn("Add customer", { creating = true }, Icons.Default.PersonAdd)
        }
        TabBar(listOf("Customer list", "Debtors aging"), tab, { tab = it })
        Spacer(Modifier.height(14.dp))
        if (tab == 0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KpiCard("Customers", "${customers.size}", "on file", Da.Green, Modifier.weight(1f))
                KpiCard("Total owed to you", Fmt.money(customers.sumOf { it.dbl("balance") }), "outstanding credit", Da.Red, Modifier.weight(1f))
                KpiCard("Lifetime sales", Fmt.money(customers.sumOf { it.dbl("total_purchases") }), "all customers", Da.Blue, Modifier.weight(1f))
                KpiCard("Lifetime collected", Fmt.money(customers.sumOf { it.dbl("total_paid") }), "cash received", Da.Amber, Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            CardBox(Modifier.weight(1f), padding = 0.dp) {
                Row(Modifier.fillMaxWidth().padding(12.dp)) { Box(Modifier.weight(1f)) { SearchBar(search, { search = it }, "Search name or phone") } }
                Table(
                    listOf("Customer", "Phone", "Type", "Owes you", "Lifetime sales", ""),
                    listOf(1.8f, 1.3f, 0.9f, 1.1f, 1.2f, 0.8f),
                    customers.map {
                        listOf(
                            Cell(it.str("name"), bold = true),
                            Cell(it.str("phone"), Da.Muted),
                            Cell(it.str("type"), Da.Dim),
                            Cell(Fmt.money(it.dbl("balance")), if (it.dbl("balance") > 0) Da.Red else Da.Green, align = TextAlign.End),
                            Cell(Fmt.money(it.dbl("total_purchases")), Da.Blue, align = TextAlign.End),
                            Cell("open", Da.Green)
                        )
                    },
                    onRowClick = { i -> editing = customers[i] }
                )
            }
        } else {
            val aging = remember(app.tick) { app.reports.customerAging() }
            CardBox(Modifier.fillMaxSize(), padding = 0.dp) {
                Table(
                    listOf("Customer", "0-30 days", "31-60 days", "60+ days", "Total owed"),
                    listOf(1.8f, 1f, 1f, 1f, 1.1f),
                    aging.map {
                        listOf(
                            Cell(it.str("name"), bold = true),
                            Cell(Fmt.money(it.dbl("d30")), Da.Muted, align = TextAlign.End),
                            Cell(Fmt.money(it.dbl("d60")), Da.Amber, align = TextAlign.End),
                            Cell(Fmt.money(it.dbl("d90")), Da.Red, align = TextAlign.End),
                            Cell(Fmt.money(it.dbl("balance")), Da.Text, bold = true, align = TextAlign.End)
                        )
                    }
                )
            }
        }
    }

    if (creating || editing != null) {
        CustomerDialog(app, editing, onClose = { creating = false; editing = null })
    }
    paying?.let { c ->
        RecordPaymentDialog(app, c, isCustomer = true, onClose = { paying = null })
    }
    editing?.let { } // detail opens the edit form; payments open from the row action below
}

@Composable
private fun CustomerDialog(app: AppState, existing: Row?, onClose: () -> Unit) {
    val repo = app.repo
    var name by remember { mutableStateOf(existing?.str("name") ?: "") }
    var phone by remember { mutableStateOf(existing?.str("phone") ?: "") }
    var address by remember { mutableStateOf(existing?.str("address") ?: "") }
    var type by remember { mutableStateOf(existing?.str("type") ?: "RETAIL") }
    var creditLimit by remember { mutableStateOf(existing?.dbl("credit_limit")?.toString() ?: "0") }
    var paying by remember { mutableStateOf(false) }

    if (paying && existing != null) {
        RecordPaymentDialog(app, existing, isCustomer = true, onClose = { paying = false })
    }

    DialogBox(if (existing == null) "New customer" else existing.str("name"), onClose, 560.dp) {
        if (existing != null) {
            FormRow("Balance owed", Fmt.money(existing.dbl("balance")), emphasize = true)
            FormRow("Lifetime sales", Fmt.money(existing.dbl("total_purchases")))
            FormRow("Lifetime paid", Fmt.money(existing.dbl("total_paid")))
            Spacer(Modifier.height(12.dp))
        }
        Field("Customer name", name, { name = it })
        Spacer(Modifier.height(10.dp))
        Row {
            Box(Modifier.weight(1f)) { Field("Phone", phone, { phone = it }) }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) { Drop("Type", listOf("RETAIL", "WHOLESALE", "DISTRIBUTOR"), type, { type = it }) }
        }
        Spacer(Modifier.height(10.dp))
        Field("Address / village", address, { address = it })
        Spacer(Modifier.height(10.dp))
        MoneyField("Credit limit (0 = no credit)", creditLimit, { creditLimit = it.filter { c -> c.isDigit() || c == '.' } })
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn(if (existing == null) "Create customer" else "Save", {
                if (name.isBlank()) { app.say("Name is required"); return@Btn }
                val values = mapOf(
                    "name" to name.trim(), "phone" to phone.trim().ifBlank { null },
                    "address" to address.trim().ifBlank { null }, "type" to type,
                    "credit_limit" to (creditLimit.toDoubleOrNull() ?: 0.0)
                )
                if (existing == null) repo.db.insert("customers", values + mapOf(
                    "id" to UUID.randomUUID().toString(), "balance" to 0.0, "total_purchases" to 0.0,
                    "total_paid" to 0.0, "created_at" to Clock.nowIso(), "updated_at" to Clock.nowIso()
                )) else repo.db.update("customers", existing.str("id"), values + mapOf("updated_at" to Clock.nowIso()))
                app.refresh(); app.say("Customer saved"); onClose()
            }, Icons.Default.Save, BtnKind.PRIMARY)
            if (existing != null) {
                Btn("Receive payment", { paying = true }, Icons.Default.Payments, BtnKind.GHOST)
                Btn("Delete", {
                    repo.db.delete("customers", existing.str("id")); app.refresh(); app.say("Customer deleted"); onClose()
                }, Icons.Default.Delete, BtnKind.DANGER)
            }
            Btn("Close", onClose, kind = BtnKind.GHOST)
        }
    }
}

// ── Suppliers ────────────────────────────────────────────────────────────────
@Composable
fun SuppliersScreen(app: AppState) {
    val repo = app.repo
    var tab by remember { mutableStateOf(0) }
    var search by remember { mutableStateOf("") }
    var suppliers by remember { mutableStateOf(repo.suppliers()) }
    var editing by remember { mutableStateOf<Row?>(null) }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(app.tick, search) {
        suppliers = repo.suppliers().filter {
            search.isBlank() || it.str("name").contains(search, true) || it.str("phone").contains(search, true)
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        ScreenHeader("Suppliers", "Who you buy from and what you still owe them") {
            Btn("Add supplier", { creating = true }, Icons.Default.LocalShipping)
        }
        TabBar(listOf("Supplier list", "Creditors aging"), tab, { tab = it })
        Spacer(Modifier.height(14.dp))
        if (tab == 0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KpiCard("Suppliers", "${suppliers.size}", "on file", Da.Green, Modifier.weight(1f))
                KpiCard("You owe", Fmt.money(suppliers.sumOf { it.dbl("balance") }), "outstanding balances", Da.Red, Modifier.weight(1f))
                KpiCard("Unpaid purchases", "${repo.db.q("SELECT id FROM purchases WHERE total > paid_amount").size}", "awaiting payment", Da.Amber, Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            CardBox(Modifier.weight(1f), padding = 0.dp) {
                Row(Modifier.fillMaxWidth().padding(12.dp)) { Box(Modifier.weight(1f)) { SearchBar(search, { search = it }, "Search supplier or phone") } }
                Table(
                    listOf("Supplier", "Phone", "You owe", "Purchases", "Open"),
                    listOf(2f, 1.3f, 1.1f, 1.1f, 0.6f),
                    suppliers.map {
                        listOf(
                            Cell(it.str("name"), bold = true),
                            Cell(it.str("phone"), Da.Muted),
                            Cell(Fmt.money(it.dbl("balance")), if (it.dbl("balance") > 0) Da.Red else Da.Green, align = TextAlign.End),
                            Cell(Fmt.money(repo.db.scalar("SELECT COALESCE(SUM(total),0) FROM purchases WHERE supplier_id=?", it.str("id"))), Da.Muted, align = TextAlign.End),
                            Cell("open", Da.Green)
                        )
                    },
                    onRowClick = { i -> editing = suppliers[i] }
                )
            }
        } else {
            val aging = remember(app.tick) { app.reports.supplierAging() }
            CardBox(Modifier.fillMaxSize(), padding = 0.dp) {
                Table(
                    listOf("Supplier", "0-30 days", "31-60 days", "60+ days", "Total owed"),
                    listOf(1.8f, 1f, 1f, 1f, 1.1f),
                    aging.map {
                        listOf(
                            Cell(it.str("name"), bold = true),
                            Cell(Fmt.money(it.dbl("d30")), Da.Muted, align = TextAlign.End),
                            Cell(Fmt.money(it.dbl("d60")), Da.Amber, align = TextAlign.End),
                            Cell(Fmt.money(it.dbl("d90")), Da.Red, align = TextAlign.End),
                            Cell(Fmt.money(it.dbl("balance")), Da.Text, bold = true, align = TextAlign.End)
                        )
                    }
                )
            }
        }
    }

    if (creating || editing != null) {
        SupplierDialog(app, editing, onClose = { creating = false; editing = null })
    }
}

@Composable
private fun SupplierDialog(app: AppState, existing: Row?, onClose: () -> Unit) {
    val repo = app.repo
    var name by remember { mutableStateOf(existing?.str("name") ?: "") }
    var phone by remember { mutableStateOf(existing?.str("phone") ?: "") }
    var address by remember { mutableStateOf(existing?.str("address") ?: "") }
    var paying by remember { mutableStateOf(false) }

    if (paying && existing != null) {
        RecordPaymentDialog(app, existing, isCustomer = false, onClose = { paying = false })
    }

    DialogBox(if (existing == null) "New supplier" else existing.str("name"), onClose, 540.dp) {
        if (existing != null) {
            FormRow("Balance you owe", Fmt.money(existing.dbl("balance")), emphasize = true)
            Spacer(Modifier.height(12.dp))
        }
        Field("Supplier name", name, { name = it })
        Spacer(Modifier.height(10.dp))
        Row {
            Box(Modifier.weight(1f)) { Field("Phone", phone, { phone = it }) }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) { Field("Address", address, { address = it }) }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn(if (existing == null) "Create supplier" else "Save", {
                if (name.isBlank()) { app.say("Name is required"); return@Btn }
                val values = mapOf("name" to name.trim(), "phone" to phone.trim().ifBlank { null },
                    "address" to address.trim().ifBlank { null })
                if (existing == null) repo.db.insert("suppliers", values + mapOf(
                    "id" to UUID.randomUUID().toString(), "balance" to 0.0,
                    "created_at" to Clock.nowIso(), "updated_at" to Clock.nowIso()
                )) else repo.db.update("suppliers", existing.str("id"), values + mapOf("updated_at" to Clock.nowIso()))
                app.refresh(); app.say("Supplier saved"); onClose()
            }, Icons.Default.Save, BtnKind.PRIMARY)
            if (existing != null) {
                Btn("Pay supplier", { paying = true }, Icons.Default.Payments, BtnKind.GHOST)
                Btn("Delete", {
                    repo.db.delete("suppliers", existing.str("id")); app.refresh(); onClose()
                }, Icons.Default.Delete, BtnKind.DANGER)
            }
            Btn("Close", onClose, kind = BtnKind.GHOST)
        }
    }
}

@Composable
private fun RecordPaymentDialog(app: AppState, party: Row, isCustomer: Boolean, onClose: () -> Unit) {
    val repo = app.repo
    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("CASH") }
    var reference by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    DialogBox(if (isCustomer) "Money received from ${party.str("name")}" else "Money paid to ${party.str("name")}", onClose, 500.dp) {
        FormRow("Current balance", Fmt.money(party.dbl("balance")), emphasize = true)
        Spacer(Modifier.height(10.dp))
        MoneyField("Amount", amount, { amount = it.filter { c -> c.isDigit() || c == '.' } })
        Spacer(Modifier.height(10.dp))
        Drop("Method", listOf("CASH", "MTN_MOMO", "AIRTEL_MONEY"), method, { method = it })
        Spacer(Modifier.height(10.dp))
        Row {
            Box(Modifier.weight(1f)) { Field("Reference", reference, { reference = it }, placeholder = "phone ref / receipt") }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) { Field("Note", note, { note = it }) }
        }
        Spacer(Modifier.height(16.dp))
        Btn("Post payment", {
            try {
                val amt = amount.toDoubleOrNull() ?: 0.0
                if (isCustomer) repo.recordCustomerPayment(party.str("id"), amt, method, reference, note)
                else repo.recordSupplierPayment(party.str("id"), amt, method, reference, note)
                app.refresh(); app.say("Payment posted"); onClose()
            } catch (e: Exception) { app.say(e.message ?: "Could not post") }
        }, Icons.Default.Check, BtnKind.PRIMARY)
    }
}
