package com.derycode.deryaccount.desktop.ui.screens

/**
 * HotelManager — the parts of a full hotel system that sit around the desk:
 * rate plans and the daily rate calendar, travel agents with their city
 * ledger, the night audit that closes the day, and the housekeeping and
 * maintenance boards.
 *
 * Everything is computed from the hotel's own SQLite file: no internet, no
 * server, so a front desk in a village rates and closes its day exactly like
 * one in the city.
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.desktop.data.Clock
import com.derycode.deryaccount.desktop.data.Fmt
import com.derycode.deryaccount.desktop.data.Hotel
import com.derycode.deryaccount.desktop.data.Row
import com.derycode.deryaccount.desktop.ui.*

@Composable
private fun SectionTitle(text: String, sub: String? = null) {
    Column(Modifier.padding(bottom = 10.dp)) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Da.Text)
        if (!sub.isNullOrBlank()) Text(sub, fontSize = 11.sp, color = Da.Muted)
    }
}

// ── Rates ────────────────────────────────────────────────────────────────────

@Composable
fun RatesScreen(app: AppState) {
    val h = app.hotel
    var tick by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<Row?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf<String?>(null) }

    val types = remember(tick) { runCatching { h.roomTypes() }.getOrDefault(emptyList()) }
    val plans = remember(tick) { runCatching { h.ratePlans() }.getOrNull() ?: emptyList() }
    val typeId = selectedType ?: types.firstOrNull()?.str("id")
    val calendar = remember(tick, typeId) { typeId?.let { h.rateCalendar(it, 14) } ?: emptyList() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        ScreenHeader(
            "Rates",
            "Selling prices by room type, seasons and packages — the rate a guest is quoted comes from here"
        ) {
            Btn("New rate plan", { showNew = true }, Icons.Default.Add)
        }

        CardBox {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("14-day rate calendar", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                Spacer(Modifier.weight(1f))
                types.forEach { t ->
                    val on = t.str("id") == typeId
                    Box(
                        Modifier.padding(start = 6.dp).clip(RoundedCornerShape(50))
                            .background(if (on) Da.Brand else Da.Surface2)
                            .clickable { selectedType = t.str("id") }
                            .padding(horizontal = 11.dp, vertical = 5.dp)
                    ) {
                        Text(t.str("name"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            color = if (on) Color.White else Da.Muted)
                    }
                }
            }
            if (types.isEmpty()) {
                Text("Add a room type first, on the Rooms screen.", fontSize = 12.sp, color = Da.Muted)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    calendar.forEach { (date, rate) ->
                        var editingRate by remember(date) { mutableStateOf(false) }
                        val weekend = runCatching {
                            val d = java.time.LocalDate.parse(date).dayOfWeek
                            d == java.time.DayOfWeek.SATURDAY || d == java.time.DayOfWeek.SUNDAY
                        }.getOrDefault(false)
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                                .background(if (weekend) Da.GoldSoft else Da.Surface2)
                                .clickable { editingRate = true }
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(date.takeLast(5), fontSize = 9.5.sp, color = Da.Muted)
                            Text(Fmt.money0(rate), fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
                                color = Da.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (editingRate) {
                            RateOverrideDialog(date, rate, onClose = { editingRate = false }) { newRate ->
                                typeId?.let { h.setRateOverride(it, date, newRate) }
                                tick++
                                editingRate = false
                            }
                        }
                    }
                }
                Text("Tap any day to set that night's price. Weekend columns are shaded.",
                    fontSize = 11.sp, color = Da.Dim, modifier = Modifier.padding(top = 8.dp))
            }
        }

        Spacer(Modifier.height(16.dp))

        CardBox {
            SectionTitle("Rate plans", "Packages, seasons and contracts")
            if (plans.isEmpty()) {
                EmptyState("No rate plans yet. Without one, rooms sell at the room type's base rate.") {
                    Btn("New rate plan", { showNew = true }, Icons.Default.Add, BtnKind.GHOST)
                }
            } else {
                Table(
                    headers = listOf("Plan", "Room type", "Board", "Extra per night", "Weekend", "Valid", "Status"),
                    weights = listOf(2.2f, 1.6f, 0.9f, 1.3f, 1.2f, 1.8f, 1f),
                    rows = plans.map { p ->
                        val rt = types.firstOrNull { it.str("id") == p.str("room_type_id") }?.str("name") ?: "All rooms"
                        listOf(
                            Cell(p.str("name"), bold = true),
                            Cell(rt, color = Da.Muted),
                            Cell(p.str("kind")),
                            Cell("+ " + Fmt.money0(p.dbl("price_delta"))),
                            Cell("+ " + Fmt.money0(p.dbl("weekend_delta"))),
                            Cell(
                                listOfNotNull(
                                    p.str("valid_from").take(10).ifBlank { null },
                                    p.str("valid_to").take(10).ifBlank { null }
                                ).joinToString(" → ").ifBlank { "Any date" },
                                color = Da.Muted
                            ),
                            Cell(if (p.int("is_active") == 1) "Active" else "Off",
                                color = if (p.int("is_active") == 1) Da.Green else Da.Dim, bold = true)
                        )
                    },
                    onRowClick = { i -> editing = plans[i] },
                    scrollable = false
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text("A room's price = room type base rate + the highest active plan for that night (+ weekend extra), " +
            "unless a date on the calendar is set. Change a price here and every new booking uses it.",
            fontSize = 11.sp, color = Da.Dim)
    }

    if (showNew || editing != null) {
        RatePlanDialog(app, editing, types, onClose = { showNew = false; editing = null; tick++ })
    }
}

@Composable
private fun RateOverrideDialog(
    date: String, current: Double, onClose: () -> Unit, onSave: (Double?) -> Unit
) {
    var value by remember { mutableStateOf(if (current > 0) current.toInt().toString() else "") }
    DialogBox("Rate for " + date, onClose, 420.dp) {
        Text("Set the price to sell this night at. Clear it to go back to the normal rate.",
            fontSize = 12.sp, color = Da.Muted)
        Spacer(Modifier.height(12.dp))
        MoneyField("Price per night (UGX)", value, { value = it })
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Save", { onSave(value.toDoubleOrNull()) })
            Btn("Use normal rate", { onSave(null) }, kind = BtnKind.GHOST)
        }
    }
}

@Composable
private fun RatePlanDialog(app: AppState, existing: Row?, types: List<Row>, onClose: () -> Unit) {
    var name by remember { mutableStateOf(existing?.str("name") ?: "") }
    var typeId by remember { mutableStateOf(existing?.str("room_type_id") ?: "") }
    var kind by remember { mutableStateOf(existing?.str("kind") ?: "EP") }
    var delta by remember { mutableStateOf(existing?.dbl("price_delta")?.toInt()?.toString() ?: "0") }
    var weekend by remember { mutableStateOf(existing?.dbl("weekend_delta")?.toInt()?.toString() ?: "0") }
    var from by remember { mutableStateOf(existing?.str("valid_from")?.take(10) ?: "") }
    var to by remember { mutableStateOf(existing?.str("valid_to")?.take(10) ?: "") }
    var active by remember { mutableStateOf(existing == null || existing.int("is_active") == 1) }
    var error by remember { mutableStateOf<String?>(null) }

    DialogBox(
        if (existing == null) "New rate plan" else "Edit rate plan", onClose,
        width = 560.dp
    ) {
        val typeNames = listOf("All room types") + types.map { it.str("name") }
        Drop(
            label = "Room type",
            options = typeNames,
            selected = types.firstOrNull { it.str("id") == typeId }?.str("name") ?: "All room types",
            onSelect = { picked ->
                typeId = types.firstOrNull { it.str("name") == picked }?.str("id") ?: ""
            }
        )
        Spacer(Modifier.height(10.dp))
        Field("Plan name", name, { name = it }, placeholder = "Weekend package, Corporate 2026, Peak season")
        Spacer(Modifier.height(10.dp))
        val boards = listOf("EP — room only", "CP — with breakfast", "MAP — with breakfast and dinner", "AP — all meals")
        Drop(
            label = "Board",
            options = boards,
            selected = boards.firstOrNull { it.startsWith(kind) } ?: boards.first(),
            onSelect = { picked -> kind = picked.substringBefore(" ") }
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { MoneyField("Extra per night", delta, { delta = it }) }
            Box(Modifier.weight(1f)) { MoneyField("Weekend extra", weekend, { weekend = it }) }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { Field("From (YYYY-MM-DD)", from, { from = it }, placeholder = "leave blank") }
            Box(Modifier.weight(1f)) { Field("To (YYYY-MM-DD)", to, { to = it }, placeholder = "leave blank") }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = active, onCheckedChange = { active = it })
            Spacer(Modifier.width(8.dp))
            Text("Active", fontSize = 12.5.sp, color = Da.Text)
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontSize = 12.sp, color = Da.Red)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Save plan", {
                runCatching {
                    app.hotel.saveRatePlan(
                        existing?.str("id"), name, typeId.ifBlank { null }, kind,
                        delta.toDoubleOrNull() ?: 0.0, from, to,
                        weekend.toDoubleOrNull() ?: 0.0, 1, active
                    )
                }.onSuccess { onClose() }.onFailure { error = it.message }
            })
            if (existing != null) {
                Btn("Delete", {
                    runCatching { app.hotel.deleteRatePlan(existing.str("id")) }
                        .onSuccess { onClose() }.onFailure { error = it.message }
                }, kind = BtnKind.DANGER)
            }
            Btn("Cancel", onClose, kind = BtnKind.GHOST)
        }
    }
}

// ── Agents and city ledger ───────────────────────────────────────────────────

@Composable
fun AgentsScreen(app: AppState) {
    val h = app.hotel
    var tick by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<Row?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var statementFor by remember { mutableStateOf<Row?>(null) }

    val agents = remember(tick) { runCatching { h.agents() }.getOrNull() ?: emptyList() }
    val ageing = remember(tick) { runCatching { h.agentAgeing() }.getOrNull() ?: Hotel.Ageing(0.0, 0.0, 0.0, 0.0) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        ScreenHeader("Agents & corporate", "Travel agents, companies and the money they owe the hotel") {
            Btn("New agent", { showNew = true }, Icons.Default.Add)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            KpiCard("Owed to hotel", Fmt.money0(ageing.total), "All unpaid agent invoices", Da.Brand, Modifier.weight(1f))
            KpiCard("Not yet due", Fmt.money0(ageing.current), "Within terms", Da.Green, Modifier.weight(1f))
            KpiCard("31-60 days", Fmt.money0(ageing.d30), "Chase these", Da.Gold, Modifier.weight(1f))
            KpiCard("Over 60 days", Fmt.money0(ageing.d60 + ageing.d90), "Escalate", Da.Red, Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))

        CardBox {
            SectionTitle("Agents", "Commission is taken off when you bill them")
            if (agents.isEmpty()) {
                EmptyState("No agents yet. Add the companies and travel agents who book with you.") {
                    Btn("New agent", { showNew = true }, Icons.Default.Add, BtnKind.GHOST)
                }
            } else {
                Table(
                    headers = listOf("Agent", "Type", "Contact", "Commission", "Credit limit", "Owing", "Status"),
                    weights = listOf(2.2f, 1.2f, 1.6f, 1f, 1.3f, 1.3f, 0.9f),
                    rows = agents.map { a ->
                        val owing = h.agentBalance(a.str("id"))
                        listOf(
                            Cell(a.str("name"), bold = true),
                            Cell(a.str("kind").replace("_", " "), color = Da.Muted),
                            Cell(listOf(a.str("contact"), a.str("phone")).filter { it.isNotBlank() }.joinToString(" · "),
                                color = Da.Muted),
                            Cell(Fmt.pct(a.dbl("commission_pct") / 100.0)),
                            Cell(Fmt.money0(a.dbl("credit_limit"))),
                            Cell(Fmt.money0(owing), color = if (owing > 0) Da.Gold else Da.Muted, bold = owing > 0),
                            Cell(if (a.int("is_active") == 1) "Active" else "Off",
                                color = if (a.int("is_active") == 1) Da.Green else Da.Dim)
                        )
                    },
                    onRowClick = { i -> statementFor = agents[i] },
                    scrollable = false
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Tap an agent for their statement, invoice and payments.", fontSize = 11.sp, color = Da.Dim)
    }

    if (showNew || editing != null) {
        AgentDialog(app, editing, onClose = { showNew = false; editing = null; tick++ })
    }
    statementFor?.let { agent ->
        AgentStatementDialog(app, agent, onClose = { statementFor = null; tick++ })
    }
}

@Composable
private fun AgentDialog(app: AppState, existing: Row?, onClose: () -> Unit) {
    var name by remember { mutableStateOf(existing?.str("name") ?: "") }
    var kind by remember { mutableStateOf(existing?.str("kind") ?: "TRAVEL_AGENT") }
    var contact by remember { mutableStateOf(existing?.str("contact") ?: "") }
    var phone by remember { mutableStateOf(existing?.str("phone") ?: "") }
    var email by remember { mutableStateOf(existing?.str("email") ?: "") }
    var commission by remember { mutableStateOf(existing?.dbl("commission_pct")?.toInt()?.toString() ?: "0") }
    var credit by remember { mutableStateOf(existing?.dbl("credit_limit")?.toInt()?.toString() ?: "0") }
    var notes by remember { mutableStateOf(existing?.str("notes") ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    DialogBox(if (existing == null) "New agent" else "Edit agent", onClose, 540.dp) {
        Field("Name", name, { name = it }, placeholder = "Kampala Tours Ltd")
        Spacer(Modifier.height(10.dp))
        val kinds = listOf("Travel agent", "Corporate", "Online", "Government")
        Drop(
            label = "Type",
            options = kinds,
            selected = kinds.firstOrNull { it.replace(" ", "_").uppercase() == kind } ?: kinds.first(),
            onSelect = { picked -> kind = picked.replace(" ", "_").uppercase() }
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { Field("Contact person", contact, { contact = it }) }
            Box(Modifier.weight(1f)) { Field("Phone", phone, { phone = it }) }
        }
        Spacer(Modifier.height(10.dp))
        Field("Email", email, { email = it })
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { Field("Commission %", commission, { commission = it }) }
            Box(Modifier.weight(1f)) { MoneyField("Credit limit", credit, { credit = it }) }
        }
        Spacer(Modifier.height(10.dp))
        Field("Notes", notes, { notes = it })
        error?.let { Spacer(Modifier.height(8.dp)); Text(it, fontSize = 12.sp, color = Da.Red) }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Save agent", {
                runCatching {
                    app.hotel.saveAgent(
                        existing?.str("id"), name, kind, contact, phone, email,
                        commission.toDoubleOrNull() ?: 0.0, credit.toDoubleOrNull() ?: 0.0, notes, true
                    )
                }.onSuccess { onClose() }.onFailure { error = it.message }
            })
            Btn("Cancel", onClose, kind = BtnKind.GHOST)
        }
    }
}

@Composable
private fun AgentStatementDialog(app: AppState, agent: Row, onClose: () -> Unit) {
    val h = app.hotel
    var tick by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    val lines = remember(tick) { runCatching { h.agentStatement(agent.str("id")) }.getOrNull() ?: emptyList() }
    val invoices = remember(tick) { runCatching { h.agentInvoices(agent.str("id")) }.getOrNull() ?: emptyList() }
    val owing = lines.sumOf { it.due }

    DialogBox(agent.str("name"), onClose, 760.dp) {
        Text(
            listOfNotNull(
                agent.str("contact").ifBlank { null },
                agent.str("phone").ifBlank { null },
                "commission " + Fmt.pct(agent.dbl("commission_pct") / 100.0)
            ).joinToString(" · "),
            fontSize = 12.sp, color = Da.Muted
        )
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("Bill the unbilled", {
                runCatching {
                    val from = lines.minOfOrNull { it.inDate } ?: Clock.today()
                    h.createAgentInvoice(agent.str("id"), from, Clock.today())
                }.onSuccess { message = "Invoice raised"; tick++ }
                    .onFailure { message = it.message }
            }, Icons.Default.Receipt)
            Btn("Record payment", {
                runCatching { h.payAgentInvoice(invoices.first { it.str("status") != "PAID" }.str("id"), owing) }
                    .onSuccess { message = "Payment recorded"; tick++ }
                    .onFailure { message = it.message }
            }, Icons.Default.Payments, BtnKind.GHOST)
            Spacer(Modifier.weight(1f))
            Text(Fmt.money0(owing), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Da.Gold)
        }
        message?.let {
            Spacer(Modifier.height(6.dp)); Text(it, fontSize = 12.sp, color = Da.Green)
        }
        Spacer(Modifier.height(12.dp))

        SectionTitle("Stays", "What each booking owes after commission")
        Table(
            headers = listOf("Ref", "Guest", "Stay", "Gross", "Commission", "Paid", "Due"),
            weights = listOf(1.1f, 1.8f, 1.6f, 1.1f, 1.1f, 1.1f, 1.1f),
            rows = lines.map { l ->
                listOf(
                    Cell(l.ref), Cell(l.guest, bold = true),
                    Cell(l.inDate.take(10) + " → " + l.outDate.take(10), color = Da.Muted),
                    Cell(Fmt.money0(l.gross)), Cell(Fmt.money0(l.commission), color = Da.Muted),
                    Cell(Fmt.money0(l.paid), color = Da.Green),
                    Cell(Fmt.money0(l.due), color = if (l.due > 0) Da.Gold else Da.Muted, bold = true)
                )
            },
            scrollable = true, maxRows = 6
        )

        Spacer(Modifier.height(12.dp))
        SectionTitle("Invoices", "What has been billed and paid")
        if (invoices.isEmpty()) {
            Text("No invoice raised yet.", fontSize = 12.sp, color = Da.Muted)
        } else {
            Table(
                headers = listOf("Invoice", "Period", "Amount", "Paid", "Due date", "Status"),
                weights = listOf(1.2f, 1.8f, 1.1f, 1.1f, 1.3f, 0.9f),
                rows = invoices.map { i ->
                    listOf(
                        Cell(i.str("invoice_no"), bold = true),
                        Cell(i.str("period_from").take(10) + " → " + i.str("period_to").take(10), color = Da.Muted),
                        Cell(Fmt.money0(i.dbl("amount"))),
                        Cell(Fmt.money0(i.dbl("paid")), color = Da.Green),
                        Cell(i.str("due_date").take(10), color = Da.Muted),
                        Cell(i.str("status"), color = if (i.str("status") == "PAID") Da.Green else Da.Gold, bold = true)
                    )
                },
                scrollable = false
            )
        }
    }
}

// ── Night audit ──────────────────────────────────────────────────────────────

@Composable
fun NightAuditScreen(app: AppState) {
    val h = app.hotel
    var tick by remember { mutableStateOf(0) }
    var done by remember { mutableStateOf<String?>(null) }
    val date = remember(tick) { runCatching { h.businessDate() }.getOrDefault(Clock.today()) }
    val summary = remember(tick, date) { runCatching { h.auditSummary(date) }.getOrNull() ?: h.emptySummary(date) }
    val history = remember(tick) { runCatching { h.audits(14) }.getOrNull() ?: emptyList() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        ScreenHeader(
            "Night audit",
            "Close the day: no-shows are flagged, the day is totalled and the hotel moves to the next date"
        ) {
            Btn("Close " + date, {
                runCatching { h.closeNightAudit(date, app.user?.str("id") ?: "u-owner") }
                    .onSuccess { done = "Day closed. The hotel is now on " + h.businessDate() }
                    .onFailure { done = it.message }
                tick++
            }, Icons.Default.Lock)
        }

        done?.let {
            Text(it, fontSize = 12.5.sp, color = Da.Green, modifier = Modifier.padding(bottom = 10.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            KpiCard("Business date", date, "${summary.roomsSold} of ${summary.roomsAvailable} rooms sold", Da.Brand, Modifier.weight(1f))
            KpiCard("Occupancy", Fmt.pct((summary.occupancy) / 100.0), "Rooms sold tonight", Da.Green, Modifier.weight(1f))
            KpiCard("Revenue posted", Fmt.money0(summary.revenue), "Charged to guests today", Da.Brand, Modifier.weight(1f))
            KpiCard("Money in", Fmt.money0(summary.payments), "Cash, mobile and bank", Da.Gold, Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) {
                CardBox {
                    SectionTitle("Today at the desk")
                    FormRow("Arrivals", summary.arrivals.toString())
                    FormRow("In house now", summary.inHouse.toString())
                    FormRow("Departures due", summary.departures.toString())
                    FormRow("Booked, never arrived", summary.noShows.toString())
                    FormRow("Deposits held", Fmt.money0(summary.deposits))
                }
            }
            Box(Modifier.weight(1f)) {
                CardBox {
                    SectionTitle("Revenue by outlet")
                    if (summary.byKind.isEmpty()) Text("Nothing posted today.", fontSize = 12.sp, color = Da.Muted)
                    else summary.byKind.forEach { line ->
                        FormRow(line.label, Fmt.money0(line.amount))
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                CardBox {
                    SectionTitle("Money taken")
                    if (summary.byMethod.isEmpty()) Text("No payments today.", fontSize = 12.sp, color = Da.Muted)
                    else summary.byMethod.forEach { line -> FormRow(line.label, Fmt.money0(line.amount)) }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        CardBox {
            SectionTitle("Closed days", "The record the auditor and the owner read")
            if (history.isEmpty()) {
                Text("No day closed yet.", fontSize = 12.sp, color = Da.Muted)
            } else {
                Table(
                    headers = listOf("Date", "Rooms sold", "Occupancy", "Revenue", "Payments", "No-shows", "Closed"),
                    weights = listOf(1.2f, 1f, 1f, 1.2f, 1.2f, 0.9f, 1.6f),
                    rows = history.map { a ->
                        val occ = if (a.int("rooms_available") > 0)
                            a.int("rooms_sold") * 100.0 / a.int("rooms_available") else 0.0
                        listOf(
                            Cell(a.str("business_date"), bold = true),
                            Cell(a.int("rooms_sold").toString()),
                            Cell(Fmt.pct(occ / 100.0)),
                            Cell(Fmt.money0(a.dbl("revenue"))),
                            Cell(Fmt.money0(a.dbl("payments")), color = Da.Green),
                            Cell(a.int("no_shows").toString(),
                                color = if (a.int("no_shows") > 0) Da.Red else Da.Muted),
                            Cell(a.str("closed_at").take(16).replace("T", " "), color = Da.Dim)
                        )
                    },
                    scrollable = false
                )
            }
        }
    }
}

// ── Housekeeping ─────────────────────────────────────────────────────────────

@Composable
fun HousekeepingScreen(app: AppState) {
    val h = app.hotel
    var tick by remember { mutableStateOf(0) }
    var tab by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    val board = remember(tick) { runCatching { h.housekeepingBoard() }.getOrDefault(emptyList()) }
    val tasks = remember(tick) { runCatching { h.housekeepingTasks(true) }.getOrDefault(emptyList()) }
    val maintenance = remember(tick) { runCatching { h.maintenanceTickets(true) }.getOrNull() ?: emptyList() }
    val rooms = remember(tick) { runCatching { h.rooms() }.getOrDefault(emptyList()) }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        ScreenHeader("Housekeeping", "Rooms, cleaning tasks and repairs — what the next guest walks into") {
            Btn("Build today's list", {
                runCatching { h.buildHousekeepingList() }
                    .onSuccess { made -> message = "$made task(s) added from today's departures"; tick++ }
                    .onFailure { message = it.message }
            }, Icons.Default.Autorenew, BtnKind.GHOST)
        }
        message?.let { Text(it, fontSize = 12.sp, color = Da.Green, modifier = Modifier.padding(bottom = 8.dp)) }

        TabBar(listOf("Room board", "Tasks", "Repairs"), tab, { tab = it })

        Spacer(Modifier.height(12.dp))

        when (tab) {
            0 -> RoomBoard(app, board, onRefresh = { tick++ })
            1 -> TaskList(app, tasks, onRefresh = { tick++ })
            else -> MaintenanceList(app, maintenance, rooms, onRefresh = { tick++ })
        }
    }
}

@Composable
private fun RoomBoard(app: AppState, board: List<Hotel.Board>, onRefresh: () -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(board) { b ->
            val r = b.room
            val color = when (b.status) {
                Hotel.VACANT_CLEAN -> Da.Green
                Hotel.VACANT_DIRTY -> Da.Gold
                Hotel.OCCUPIED -> Da.Brand
                else -> Da.Red
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Da.Surface)
                    .border(1.dp, Da.Outline, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(color))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1.1f)) {
                    Text("Room " + r.str("number"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                    Text(r.str("type_name").ifBlank { "—" }, fontSize = 11.sp, color = Da.Muted)
                }
                Column(Modifier.weight(1.4f)) {
                    Text(b.status.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                        fontSize = 12.sp, color = color, fontWeight = FontWeight.SemiBold)
                    if (b.guest.isNotBlank()) Text(b.guest, fontSize = 11.sp, color = Da.Muted)
                }
                Column(Modifier.weight(1f)) {
                    Text(if (b.openTasks == 0) "No tasks" else b.openTasks.toString() + " task(s)",
                        fontSize = 11.5.sp, color = if (b.openTasks > 0) Da.Gold else Da.Dim)
                    if (b.priority == "URGENT") Text("URGENT", fontSize = 10.sp, color = Da.Red, fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Btn("Clean", {
                        runCatching { app.hotel.setRoomStatus(r.str("id"), Hotel.VACANT_CLEAN) }
                        onRefresh()
                    }, kind = BtnKind.SUBTLE)
                    Btn("Dirty", {
                        runCatching { app.hotel.setRoomStatus(r.str("id"), Hotel.VACANT_DIRTY) }
                        onRefresh()
                    }, kind = BtnKind.SUBTLE)
                    Btn("Repair", {
                        runCatching { app.hotel.raiseMaintenance(r.str("id"), "Reported from the room board") }
                        onRefresh()
                    }, kind = BtnKind.SUBTLE)
                }
            }
        }
    }
}

@Composable
private fun TaskList(app: AppState, tasks: List<Row>, onRefresh: () -> Unit) {
    if (tasks.isEmpty()) {
        EmptyState("No open tasks. Press “Build today's list” to create them from departures.")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(tasks) { t ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Da.Surface)
                    .border(1.dp, Da.Outline, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(t.str("room_number").ifBlank { "Room" } + " · " + t.str("kind"),
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                    Text(t.str("note").ifBlank { "—" }, fontSize = 11.sp, color = Da.Muted)
                }
                Pill(
                    t.str("priority").ifBlank { "NORMAL" },
                    when (t.str("priority")) {
                        "URGENT" -> Da.Red
                        "HIGH" -> Da.Gold
                        else -> Da.Muted
                    }
                )
                Spacer(Modifier.width(10.dp))
                Btn("Done", {
                    runCatching { app.hotel.completeTask(t.str("id")) }
                    onRefresh()
                }, Icons.Default.Check, BtnKind.SUBTLE)
            }
        }
    }
}

@Composable
private fun MaintenanceList(app: AppState, tickets: List<Row>, rooms: List<Row>, onRefresh: () -> Unit) {
    var roomId by remember { mutableStateOf(rooms.firstOrNull()?.str("id") ?: "") }
    var issue by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("NORMAL") }

    Column {
        CardBox {
            SectionTitle("Log a repair")
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1.2f)) {
                    Drop(
                        label = "Room",
                        options = rooms.map { it.str("number") },
                        selected = rooms.firstOrNull { it.str("id") == roomId }?.str("number") ?: "",
                        onSelect = { n -> roomId = rooms.firstOrNull { it.str("number") == n }?.str("id") ?: "" }
                    )
                }
                Box(Modifier.weight(2.4f)) { Field("What is wrong", issue, { issue = it }, placeholder = "Air conditioner leaking") }
                Box(Modifier.weight(1f)) {
                    Drop("Priority", listOf("NORMAL", "HIGH", "URGENT"), priority, { priority = it })
                }
                Btn("Log", {
                    runCatching { app.hotel.raiseMaintenance(roomId, issue, priority, app.userName) }
                        .onSuccess { issue = ""; onRefresh() }
                }, Icons.Default.Build)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (tickets.isEmpty()) {
            Text("No open repairs.", fontSize = 12.sp, color = Da.Muted)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tickets) { m ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Da.Surface)
                            .border(1.dp, Da.Outline, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Room " + m.str("room_number").ifBlank { "—" } + " · " + m.str("issue"),
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                            Text(m.str("created_at").take(16).replace("T", " "), fontSize = 11.sp, color = Da.Muted)
                        }
                        Pill(m.str("priority"),
                            if (m.str("priority") == "URGENT") Da.Red else Da.Muted)
                        Spacer(Modifier.width(10.dp))
                        Btn("Fixed", {
                            runCatching { app.hotel.closeMaintenance(m.str("id")) }
                            onRefresh()
                        }, Icons.Default.CheckCircle, BtnKind.SUBTLE)
                    }
                }
            }
        }
    }
}

// ── Availability calendar ────────────────────────────────────────────────────
// The grid every front desk actually runs on: rooms down the side, dates across
// the top, bookings painted across the nights they occupy. Click a free cell to
// take a booking for that room on that date.

@Composable
fun AvailabilityScreen(app: AppState) {
    val h = app.hotel
    val tick = app.tick
    var start by remember(tick) { mutableStateOf(Clock.today()) }
    val days = 14
    val dates = remember(start) { (0 until days).map { Hotel.addDays(start, it.toLong()) } }
    val rooms = remember(tick) { runCatching { h.rooms() }.getOrDefault(emptyList()) }
    val live = remember(start, tick) {
        runCatching { h.bookingsBetween(start, Hotel.addDays(start, days.toLong())) }.getOrDefault(emptyList())
    }
    var pick by remember { mutableStateOf<Pair<String, String>?>(null) }
    val today = Clock.today()

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        ScreenHeader("Availability", "Rooms by date. Click a free night to take a booking.") {
            Btn("Today", { start = Clock.today() }, Icons.Default.Today, BtnKind.GHOST)
            Btn("7 days back", { start = Hotel.addDays(start, -7) }, Icons.Default.ChevronLeft, BtnKind.GHOST)
            Btn("7 days on", { start = Hotel.addDays(start, 7) }, Icons.Default.ChevronRight, BtnKind.GHOST)
        }
        Spacer(Modifier.height(12.dp))

        if (rooms.isEmpty()) {
            EmptyState("Add a room first, then the calendar fills itself.") {
                Btn("Rooms & rates", { app.go("rooms") }, Icons.Default.MeetingRoom)
            }
            return@Column
        }

        CardBox(Modifier.fillMaxWidth(), padding = 0.dp) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.width(132.dp)) {
                    Box(Modifier.height(40.dp).fillMaxWidth().background(Da.Surface2).padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart) {
                        Text("ROOM", fontSize = 10.sp, color = Da.Muted, fontWeight = FontWeight.Bold)
                    }
                    rooms.forEach { r ->
                        Row(Modifier.height(46.dp).fillMaxWidth().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(r.str("number"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(6.dp))
                            Text(r.str("type_name").take(9), fontSize = 10.sp, color = Da.Muted)
                        }
                    }
                }
                Box(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                    Column {
                        Row {
                            dates.forEach { d ->
                                val weekend = runCatching {
                                    val day = java.time.LocalDate.parse(d).dayOfWeek.value
                                    day == 6 || day == 7
                                }.getOrDefault(false)
                                Column(
                                    Modifier.width(74.dp).height(40.dp)
                                        .background(if (weekend) Da.GoldSoft else Da.Surface2)
                                        .padding(vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(d, fontSize = 9.sp, color = Da.Muted)
                                    Text(d.takeLast(5), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        rooms.forEach { r ->
                            Row {
                                dates.forEach { d ->
                                    val b = live.firstOrNull {
                                        it.str("room_id") == r.str("id") &&
                                            it.str("check_in_date") <= d && it.str("check_out_date") > d
                                    }
                                    val arrival = b != null && b.str("check_in_date") == d
                                    val free = b == null && r.str("status") != Hotel.OUT_OF_ORDER
                                    Box(
                                        Modifier.width(74.dp).height(46.dp)
                                            .border(0.5.dp, Da.Outline)
                                            .background(
                                                when {
                                                    r.str("status") == Hotel.OUT_OF_ORDER -> Da.Surface2
                                                    b == null -> Da.Surface
                                                    b.str("status") == Hotel.CHECKED_IN -> Da.Brand.copy(alpha = 0.16f)
                                                    else -> Da.Green.copy(alpha = 0.14f)
                                                }
                                            )
                                            .then(if (free) Modifier.clickable { pick = r.str("id") to d } else Modifier)
                                            .padding(horizontal = 5.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        when {
                                            r.str("status") == Hotel.OUT_OF_ORDER ->
                                                Text("out", fontSize = 9.sp, color = Da.Red)
                                            b != null -> Text(
                                                if (arrival) b.str("guest_name").take(11) else "",
                                                fontSize = 9.5.sp, color = Da.Text, maxLines = 1
                                            )
                                            d == today -> Text("•", fontSize = 12.sp, color = Da.Dim)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Legend(Da.Brand.copy(alpha = 0.16f), "In house")
            Spacer(Modifier.width(14.dp))
            Legend(Da.Green.copy(alpha = 0.14f), "Booked")
            Spacer(Modifier.width(14.dp))
            Legend(Da.Surface2, "Out of order")
            Spacer(Modifier.weight(1f))
            Text("${live.size} live booking(s) in this window", fontSize = 11.sp, color = Da.Muted)
        }
    }

    pick?.let { (roomId, date) ->
        QuickBookDialog(
            h = h, roomId = roomId, date = date,
            onClose = { pick = null },
            onDone = { msg -> app.say(msg); pick = null; app.refresh() }
        )
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color).border(0.5.dp, Da.Outline, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 10.5.sp, color = Da.Muted)
    }
}

/** Take a booking straight from the calendar: guests, nights, rate, agent. */
@Composable
private fun QuickBookDialog(h: Hotel, roomId: String, date: String, onClose: () -> Unit, onDone: (String) -> Unit) {
    val room = remember(roomId) { runCatching { h.room(roomId) }.getOrNull() }
    val typeId = room?.str("room_type_id").orEmpty()
    val agents = remember { h.agents() }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var nightsText by remember { mutableStateOf("1") }
    var rateText by remember(date, typeId) { mutableStateOf(Fmt.money0(if (typeId.isNotBlank()) h.rateFor(typeId, date) else 0.0)) }
    var source by remember { mutableStateOf("WALK_IN") }
    var agentName by remember { mutableStateOf("Direct (no agent)") }
    var groupName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    DialogBox(
        title = "Book room ${room?.str("number") ?: ""} — $date",
        onClose = onClose,
        width = 420.dp
    ) {
        error?.let { Text(it, fontSize = 11.sp, color = Da.Red); Spacer(Modifier.height(8.dp)) }
        Field("Guest name", name, { name = it }, placeholder = "As on the ID")
        Row { Field("Phone", phone, { phone = it }, Modifier.weight(1f)); Spacer(Modifier.width(10.dp)); Field("Nights", nightsText, { nightsText = it }, Modifier.weight(1f), numeric = true) }
        MoneyField("Rate per night", rateText, { rateText = it })
        Drop("Agent / company", listOf("Direct (no agent)") + agents.map { it.str("name") }, agentName, { agentName = it })
        Field("Group / block (optional)", groupName, { groupName = it }, placeholder = "e.g. Kyambogo wedding")
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Take booking", {
                runCatching {
                    val nights = nightsText.trim().toLongOrNull() ?: 1L
                    require(nights >= 1) { "A booking is at least one night" }
                    require(name.isNotBlank()) { "Guest name is required" }
                    val clean = rateText.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0
                    h.createBooking(
                        roomId = roomId, guestName = name.trim(), guestPhone = phone.trim(),
                        checkIn = date, checkOut = Hotel.addDays(date, nights),
                        rate = if (clean > 0) clean else h.rateFor(typeId, date),
                        source = source,
                        agentName = if (agentName.startsWith("Direct")) "" else agentName,
                        groupName = groupName.trim()
                    )
                    "Booked ${name.trim()} into room ${room?.str("number") ?: ""} for $nights night(s)"
                }.onSuccess(onDone).onFailure { error = it.message }
            })
            Btn("Cancel", onClose, kind = BtnKind.GHOST)
        }
    }
}
