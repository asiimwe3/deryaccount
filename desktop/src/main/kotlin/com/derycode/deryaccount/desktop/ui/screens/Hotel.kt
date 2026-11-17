package com.derycode.deryaccount.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.derycode.deryaccount.desktop.data.*
import com.derycode.deryaccount.desktop.ui.*

// ── Small shared pieces ──────────────────────────────────────────────────────

private fun statusColor(status: String): Color = when (status) {
    Hotel.VACANT_CLEAN -> Da.Green
    Hotel.VACANT_DIRTY -> Da.Amber
    Hotel.OCCUPIED -> Da.Blue
    Hotel.OUT_OF_ORDER -> Da.Red
    else -> Da.Dim
}

private fun bookingColor(status: String): Color = when (status) {
    Hotel.BOOKED -> Da.Amber
    Hotel.CHECKED_IN -> Da.Blue
    Hotel.CHECKED_OUT -> Da.Green
    Hotel.CANCELLED, Hotel.NO_SHOW -> Da.Red
    else -> Da.Dim
}

@Composable
private fun LineItem(
    title: String, subtitle: String, right: String, rightSub: String,
    color: Color, onClick: () -> Unit = {}, actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(4.dp).height(30.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1.4f)) {
            Text(title, fontSize = 13.sp, color = Da.Text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 11.sp, color = Da.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(Modifier.weight(0.9f), horizontalAlignment = Alignment.End) {
            Text(right, fontSize = 12.5.sp, color = Da.Text, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(rightSub, fontSize = 10.5.sp, color = Da.Dim, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

@Composable
private fun SectionTitle(text: String, sub: String? = null) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Da.Text)
        if (sub != null) Text(sub, fontSize = 11.sp, color = Da.Dim)
    }
}

// ── Front desk ───────────────────────────────────────────────────────────────
@Composable
fun FrontDeskScreen(app: AppState) {
    val hotel = app.hotel
    var tab by remember { mutableStateOf(0) }
    var showBooking by remember { mutableStateOf(false) }
    var openFolio by remember { mutableStateOf<String?>(null) }
    var kpis by remember { mutableStateOf(hotel.kpis()) }
    var arrivals by remember { mutableStateOf(hotel.arrivalsToday()) }
    var inHouse by remember { mutableStateOf(hotel.inHouse()) }
    var departures by remember { mutableStateOf(hotel.departuresToday()) }
    var tasks by remember { mutableStateOf(hotel.housekeepingTasks()) }

    LaunchedEffect(app.tick) {
        kpis = hotel.kpis(); arrivals = hotel.arrivalsToday(); inHouse = hotel.inHouse()
        departures = hotel.departuresToday(); tasks = hotel.housekeepingTasks()
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        ScreenHeader(
            "Front desk",
            "${app.repo.businessName()} · ${Fmt.date(Clock.today())} · ${kpis.rooms} rooms"
        ) {
            Btn("Book a room", { showBooking = true }, Icons.Default.EventAvailable, BtnKind.GHOST)
            Btn("Walk-in guest", { showBooking = true; tab = 0 }, Icons.Default.Add, BtnKind.PRIMARY)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCard("Occupancy now", Fmt.pct(kpis.occupancyPct), "${kpis.occupied} of ${kpis.rooms} rooms let", Da.Green, Modifier.weight(1f))
            KpiCard("In house", "${kpis.inHouseGuests} guests", "${inHouse.size} rooms occupied", Da.Blue, Modifier.weight(1f))
            KpiCard("Arrivals today", "${kpis.arrivals}", "${kpis.departures} checking out", Da.Amber, Modifier.weight(1f))
            KpiCard("Room revenue", Fmt.money(kpis.revenueThisMonth), "this month", Da.Purple, Modifier.weight(1f))
            KpiCard("Owed by guests", Fmt.money(kpis.outstanding), "open folios", if (kpis.outstanding > 0) Da.Red else Da.Green, Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))

        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CardBox(Modifier.weight(1.7f), padding = 0.dp) {
                TabBar(
                    listOf("Arrivals ${arrivals.size}", "In house ${inHouse.size}", "Departures ${departures.size}"),
                    tab, { tab = it }
                )
                val rows = when (tab) { 1 -> inHouse; 2 -> departures; else -> arrivals }
                if (rows.isEmpty()) {
                    EmptyState(
                        when (tab) {
                            1 -> "No guests in house right now."
                            2 -> "Nobody is checking out today."
                            else -> "No arrivals waiting. Book a room to get started."
                        }
                    ) {
                        Btn("New booking", { showBooking = true }, Icons.Default.Add)
                    }
                } else {
                    LazyColumn(Modifier.fillMaxWidth()) {
                        items(rows) { b ->
                            val folio = hotel.folio(b.str("id"))
                            LineItem(
                                title = b.str("guest_name"),
                                subtitle = "${b.str("ref_no")} · room ${b.str("room_number")} · " +
                                    "${Fmt.date(b.str("check_in_date"))} → ${Fmt.date(b.str("check_out_date"))} · " +
                                    "${b.int("nights")} night${if (b.int("nights") == 1) "" else "s"}",
                                right = Fmt.money(folio.gross),
                                rightSub = if (folio.balance > 0.5) "balance ${Fmt.money(folio.balance)}" else "settled",
                                color = bookingColor(b.str("status")),
                                onClick = { openFolio = b.str("id") }
                            ) {
                                if (b.str("status") == Hotel.BOOKED) {
                                    Btn("Check in", {
                                        runCatching { hotel.checkIn(b.str("id"), app.user?.str("id") ?: "u-owner") }
                                            .onSuccess { app.say("${b.str("guest_name")} checked in"); app.refresh() }
                                            .onFailure { app.say(it.message ?: "Could not check in") }
                                    }, Icons.Default.Login, BtnKind.SUBTLE)
                                }
                                if (b.str("status") == Hotel.CHECKED_IN) {
                                    Btn("Folio", { openFolio = b.str("id") }, Icons.Default.Receipt, BtnKind.SUBTLE)
                                }
                            }
                        }
                    }
                }
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CardBox(Modifier.fillMaxWidth()) {
                    SectionTitle("Rooms right now", "${kpis.vacantClean} clean · ${kpis.dirty} to clean · ${kpis.outOfOrder} out")
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        KpiCard("Clean", "${kpis.vacantClean}", "ready to sell", Da.Green, Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        KpiCard("Dirty", "${kpis.dirty}", "needs housekeeping", Da.Amber, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Btn("Open room board", { app.go("rooms") }, Icons.Default.MeetingRoom, BtnKind.GHOST, modifier = Modifier.fillMaxWidth())
                }

                CardBox(Modifier.weight(1f), padding = 0.dp) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Housekeeping", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Da.Text, modifier = Modifier.weight(1f))
                        Text("${tasks.size} open", fontSize = 11.sp, color = Da.Dim)
                    }
                    if (tasks.isEmpty()) {
                        EmptyState("No open jobs. Rooms are turning over cleanly.")
                    } else {
                        LazyColumn(Modifier.fillMaxWidth()) {
                            items(tasks) { t ->
                                LineItem(
                                    title = "Room ${t.str("room_number")} · ${t.str("kind").lowercase().replaceFirstChar { it.uppercase() }}",
                                    subtitle = t.str("note").ifBlank { "No note" },
                                    right = "", rightSub = Fmt.date(t.str("created_at")), color = Da.Amber
                                ) {
                                    Btn("Done", {
                                        hotel.completeTask(t.str("id")); app.say("Job closed"); app.refresh()
                                    }, Icons.Default.Check, BtnKind.SUBTLE)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBooking) NewBookingDialog(app) { showBooking = false }
    openFolio?.let { id -> FolioDialog(app, id) { openFolio = null } }
}

// ── New booking ──────────────────────────────────────────────────────────────
@Composable
fun NewBookingDialog(app: AppState, onClose: () -> Unit) {
    val hotel = app.hotel
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var checkIn by remember { mutableStateOf(Clock.today()) }
    var checkOut by remember { mutableStateOf(Hotel.addDays(Clock.today(), 1)) }
    var roomPick by remember { mutableStateOf("") }
    var adults by remember { mutableStateOf("1") }
    var children by remember { mutableStateOf("0") }
    var rate by remember { mutableStateOf("") }
    var deposit by remember { mutableStateOf("") }
    var depositMethod by remember { mutableStateOf("CASH") }
    var source by remember { mutableStateOf("WALK_IN") }
    var agentName by remember { mutableStateOf("Direct (no agent)") }
    var groupName by remember { mutableStateOf("") }
    val agentList = remember { runCatching { hotel.agents() }.getOrDefault(emptyList()) }
    var notes by remember { mutableStateOf("") }
    var checkInNow by remember { mutableStateOf(true) }

    val nights = Hotel.nights(checkIn, checkOut)
    val free = remember(checkIn, checkOut, app.tick) { hotel.freeRooms(checkIn, checkOut) }
    LaunchedEffect(free) {
        if (free.none { it.str("number") == roomPick }) {
            roomPick = free.firstOrNull()?.str("number") ?: ""
            rate = free.firstOrNull()?.let { hotel.roomRate(it).toInt().toString() } ?: ""
        }
    }
    val roomRow = free.firstOrNull { it.str("number") == roomPick }

    DialogBox("New booking", onClose, 640.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Guest name", name, { name = it }, Modifier.weight(1f), placeholder = "e.g. Aisha Kabahenda")
            Field("Phone", phone, { phone = it }, Modifier.weight(1f), placeholder = "07..")
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Check-in", checkIn, { checkIn = it }, Modifier.weight(1f), placeholder = "YYYY-MM-DD")
            Field("Check-out", checkOut, { checkOut = it }, Modifier.weight(1f), placeholder = "YYYY-MM-DD")
        }
        Text(
            if (nights > 0) "$nights night${if (nights == 1L) "" else "s"} · ${free.size} rooms free for those dates"
            else "Check-out must be after check-in",
            fontSize = 11.sp, color = if (nights > 0) Da.Muted else Da.Red, modifier = Modifier.padding(top = 6.dp)
        )
        Spacer(Modifier.height(10.dp))
        Drop(
            "Room", free.map { "${it.str("number")} · ${it.str("type_name")} · ${Fmt.money(hotel.roomRate(it))}" },
            free.firstOrNull { it.str("number") == roomPick }?.let { "${it.str("number")} · ${it.str("type_name")} · ${Fmt.money(hotel.roomRate(it))}" } ?: "",
            { picked -> roomPick = picked.substringBefore(" ·"); roomPick.let { num ->
                free.firstOrNull { it.str("number") == num }?.let { rate = hotel.roomRate(it).toInt().toString() }
            } }
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Adults", adults, { adults = it.filter { c -> c.isDigit() } }, Modifier.weight(1f))
            Field("Children", children, { children = it.filter { c -> c.isDigit() } }, Modifier.weight(1f))
            MoneyField("Rate / night", rate, { rate = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.weight(1.2f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MoneyField("Deposit (optional)", deposit, { deposit = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.weight(1f))
            Drop("Deposit to", listOf("CASH", "MTN_MOMO", "AIRTEL_MONEY", "BANK"), depositMethod, { depositMethod = it }, Modifier.weight(1f))
            Drop("Source", Hotel.SOURCES, source, { source = it }, Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            Drop("Agent / company", listOf("Direct (no agent)") + agentList.map { it.str("name") }, agentName,
                { agentName = it }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Field("Group / block (optional)", groupName, { groupName = it },
            placeholder = "e.g. Kyambogo wedding, 6 rooms")
        Spacer(Modifier.height(10.dp))
        Field("Notes", notes, { notes = it }, placeholder = "Any request: extra bed, late arrival, company account…")
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (roomRow != null) "Total for the stay: ${Fmt.money((rate.toDoubleOrNull() ?: 0.0) * nights)}"
                else "No room free for those dates",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = if (roomRow != null) Da.Green else Da.Red, modifier = Modifier.weight(1f)
            )
            Btn(if (checkInNow) "Book & check in" else "Save booking", {
                runCatching {
                    if (roomRow == null) error("Pick a room that is free for those dates")
                    fun book(): String = hotel.createBooking(
                        name, phone, roomRow.str("id"), checkIn, checkOut,
                        adults.toIntOrNull() ?: 1, children.toIntOrNull() ?: 0,
                        rate.toDoubleOrNull(), source, notes,
                        deposit.toDoubleOrNull() ?: 0.0, depositMethod,
                        userId = app.user?.str("id") ?: "u-owner",
                        agentName = if (agentName.startsWith("Direct")) "" else agentName,
                        groupName = groupName
                    )
                    if (checkInNow) hotel.walkIn(
                        name, phone, roomRow.str("id"), checkOut,
                        adults.toIntOrNull() ?: 1, children.toIntOrNull() ?: 0,
                        rate.toDoubleOrNull(), notes, deposit.toDoubleOrNull() ?: 0.0,
                        depositMethod, app.user?.str("id") ?: "u-owner"
                    ) else book()
                }.onSuccess {
                    app.say("$name is booked"); app.refresh(); onClose()
                }.onFailure { app.say(it.message ?: "Could not book") }
            }, Icons.Default.Check, BtnKind.PRIMARY, enabled = roomRow != null)
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            Btn(
                if (checkInNow) "Just reserve, guest arrives later" else "Guest is here, check in now",
                { checkInNow = !checkInNow }, null, BtnKind.GHOST
            )
        }
    }
}

// ── Folio (the guest bill) ───────────────────────────────────────────────────
@Composable
fun FolioDialog(app: AppState, bookingId: String, onClose: () -> Unit) {
    val hotel = app.hotel
    var reload by remember { mutableStateOf(0) }
    val booking = remember(bookingId, reload, app.tick) { hotel.booking(bookingId) }
    val charges = remember(bookingId, reload, app.tick) { hotel.charges(bookingId) }
    val payments = remember(bookingId, reload, app.tick) { hotel.payments(bookingId) }
    val folio = remember(bookingId, reload, app.tick) { hotel.folio(bookingId) }
    var addCharge by remember { mutableStateOf(false) }
    var takePayment by remember { mutableStateOf(false) }
    var extend by remember { mutableStateOf(false) }

    if (booking == null) { onClose(); return }

    DialogBox("Folio ${booking.str("ref_no")} · ${booking.str("guest_name")}", onClose, 700.dp) {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(Hotel.bookingLabel(booking.str("status")), bookingColor(booking.str("status")), filled = true)
            Pill("Room ${booking.str("room_number")}", Da.Blue)
            Pill("${booking.int("nights")} nights", Da.Purple)
            Pill("${booking.int("adults")} adults${if (booking.int("children") > 0) " + ${booking.int("children")} children" else ""}", Da.Muted)
        }
        FormRow("Check-in", Fmt.date(booking.str("check_in_date")))
        FormRow("Check-out", Fmt.date(booking.str("check_out_date")))
        FormRow("Rate per night", Fmt.money(booking.dbl("rate")))
        if (booking.str("guest_phone").isNotBlank()) FormRow("Phone", booking.str("guest_phone"))
        if (booking.str("notes").isNotBlank()) FormRow("Notes", booking.str("notes"))

        Spacer(Modifier.height(12.dp))
        SectionTitle("Charges on the folio", "${charges.size} lines")
        Table(
            listOf("Date", "Item", "Qty", "Rate", "Amount"),
            listOf(1f, 2.4f, 0.6f, 1f, 1f),
            charges.map {
                listOf(
                    Cell(Fmt.date(it.str("charge_date")), Da.Dim),
                    Cell(Hotel.kindLabel(it.str("kind")) + " · " + it.str("description"), Da.Text),
                    Cell(Fmt.qty(it.dbl("qty")), Da.Muted),
                    Cell(Fmt.money(it.dbl("unit_price")), Da.Muted),
                    Cell(Fmt.money(it.dbl("amount")), if (it.dbl("amount") < 0) Da.Red else Da.Text, bold = true, align = TextAlign.End)
                )
            },
            scrollable = false, maxRows = 40
        )
        if (charges.isEmpty()) EmptyState("Nothing charged yet.")

        Spacer(Modifier.height(12.dp))
        SectionTitle("Payments received", "${payments.size} receipts · deposits held as a liability until check-out")
        if (payments.isEmpty()) {
            Text("No payment yet.", fontSize = 12.sp, color = Da.Dim)
        } else {
            payments.forEach { p ->
                FormRow("${Fmt.dateTime(p.str("paid_at"))} · ${p.str("method")}", Fmt.money(p.dbl("amount")))
            }
        }

        Spacer(Modifier.height(14.dp))
        CardBox(Modifier.fillMaxWidth(), background = Da.Surface2) {
            FormRow("Charges", Fmt.money(folio.gross))
            FormRow("VAT inside the bill", Fmt.money(folio.tax))
            FormRow("Paid", Fmt.money(folio.paid))
            FormRow("Balance due", Fmt.money(folio.balance), emphasize = folio.balance > 0.5)
        }

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Add charge", { addCharge = true }, Icons.Default.AddCircle, BtnKind.GHOST)
            // One-tap upsells: breakfast, the airport run, the late checkout.
            hotel.UPSELLS.take(3).forEach { up ->
                Btn(up.label, {
                    runCatching { hotel.addUpsell(bookingId, up, userId = app.user?.str("id") ?: "u-owner") }
                        .onSuccess { app.say("${up.label} added · ${Fmt.money(up.price)}"); app.refresh() }
                        .onFailure { app.say(it.message ?: "Could not add ${up.label}") }
                }, Icons.Default.AddCircleOutline, BtnKind.SUBTLE)
            }
            Btn("Take payment", { takePayment = true }, Icons.Default.Payments, BtnKind.GHOST)
            Btn("Receipt", {
                runCatching {
                    Pdf.hotelFolio(hotel, bookingId, invoice = false,
                        target = java.io.File(java.io.File(System.getProperty("user.home"), "Documents/DeryAccount/receipts"),
                            "H-" + booking.str("ref_no") + "-receipt.pdf"))
                }.onSuccess { app.say("Receipt saved to Documents/DeryAccount/receipts") }
                    .onFailure { app.say(it.message ?: "Could not write the receipt") }
            }, Icons.Default.Receipt, BtnKind.GHOST)
            Btn("Tax invoice", {
                runCatching {
                    Pdf.hotelFolio(hotel, bookingId, invoice = true,
                        target = java.io.File(java.io.File(System.getProperty("user.home"), "Documents/DeryAccount/receipts"),
                            "H-" + booking.str("ref_no") + "-invoice.pdf"))
                }.onSuccess { app.say("Tax invoice saved to Documents/DeryAccount/receipts") }
                    .onFailure { app.say(it.message ?: "Could not write the invoice") }
            }, Icons.Default.Description, BtnKind.GHOST)
            if (booking.str("status") == Hotel.CHECKED_IN) {
                Btn("Extend stay", { extend = true }, Icons.Default.MoreTime, BtnKind.GHOST)
                Btn("Check out", {
                    runCatching { hotel.checkOut(bookingId, settleToDebtor = folio.balance > 0.5, userId = app.user?.str("id") ?: "u-owner") }
                        .onSuccess {
                            app.say("Checked out · ${Fmt.money(it.gross)} billed" + if (it.balance > 0.5) ", ${Fmt.money(it.balance)} on account" else "")
                            app.refresh(); onClose()
                        }
                        .onFailure { app.say(it.message ?: "Could not check out") }
                }, Icons.Default.Logout, BtnKind.PRIMARY)
            }
            if (booking.str("status") == Hotel.BOOKED) {
                Btn("Check in", {
                    runCatching { hotel.checkIn(bookingId, app.user?.str("id") ?: "u-owner") }
                        .onSuccess { app.say("Checked in"); app.refresh(); reload++ }
                        .onFailure { app.say(it.message ?: "Could not check in") }
                }, Icons.Default.Login, BtnKind.PRIMARY)
                Btn("Cancel", {
                    runCatching { hotel.cancelBooking(bookingId, refundDeposit = true) }
                        .onSuccess { app.say("Booking cancelled"); app.refresh(); onClose() }
                        .onFailure { app.say(it.message ?: "Could not cancel") }
                }, Icons.Default.Close, BtnKind.DANGER)
            }
        }
    }

    if (addCharge) {
        var kind by remember { mutableStateOf("RESTAURANT") }
        var desc by remember { mutableStateOf("") }
        var qty by remember { mutableStateOf("1") }
        var price by remember { mutableStateOf("") }
        DialogBox("Charge to room ${booking.str("room_number")}", { addCharge = false }, 480.dp) {
            Drop("What is it", Hotel.CHARGE_KINDS.filter { it != "ROOM" }, kind, { kind = it })
            Spacer(Modifier.height(10.dp))
            Field("Description", desc, { desc = it }, placeholder = "e.g. lunch, 2 sodas, laundry")
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Qty", qty, { qty = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.weight(1f))
                MoneyField("Amount each", price, { price = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.weight(2f))
            }
            Spacer(Modifier.height(14.dp))
            Btn("Post to folio", {
                runCatching {
                    hotel.addCharge(
                        bookingId, kind, desc, qty.toDoubleOrNull() ?: 1.0, price.toDoubleOrNull() ?: 0.0,
                        taxRate = if (kind == "DISCOUNT") 0.0 else 18.0
                    )
                }.onSuccess { app.say("Charge added"); addCharge = false; reload++; app.refresh() }
                    .onFailure { app.say(it.message ?: "Could not post") }
            }, Icons.Default.Check, BtnKind.PRIMARY)
        }
    }

    if (takePayment) {
        var amount by remember { mutableStateOf(if (folio.balance > 0) folio.balance.toInt().toString() else "") }
        var method by remember { mutableStateOf("CASH") }
        DialogBox("Payment from ${booking.str("guest_name")}", { takePayment = false }, 460.dp) {
            MoneyField("Amount", amount, { amount = it.filter { c -> c.isDigit() || c == '.' } })
            Spacer(Modifier.height(10.dp))
            Drop("Method", listOf("CASH", "MTN_MOMO", "AIRTEL_MONEY", "BANK"), method, { method = it })
            Spacer(Modifier.height(8.dp))
            Text("Balance due: ${Fmt.money(folio.balance)}", fontSize = 12.sp, color = Da.Muted)
            Spacer(Modifier.height(14.dp))
            Btn("Record payment", {
                runCatching {
                    hotel.postPayment(bookingId, amount.toDoubleOrNull() ?: 0.0, method, "Front desk", app.user?.str("id") ?: "u-owner")
                }.onSuccess { app.say("Payment received"); takePayment = false; reload++; app.refresh() }
                    .onFailure { app.say(it.message ?: "Could not record") }
            }, Icons.Default.Check, BtnKind.PRIMARY)
        }
    }

    if (extend) {
        var newOut by remember { mutableStateOf(Hotel.addDays(booking.str("check_out_date"), 1)) }
        DialogBox("Extend the stay", { extend = false }, 420.dp) {
            Field("New check-out", newOut, { newOut = it }, placeholder = "YYYY-MM-DD")
            Spacer(Modifier.height(8.dp))
            Text("Room ${booking.str("room_number")} · currently until ${Fmt.date(booking.str("check_out_date"))}",
                fontSize = 12.sp, color = Da.Muted)
            Spacer(Modifier.height(14.dp))
            Btn("Extend", {
                runCatching { hotel.extendStay(bookingId, newOut, app.user?.str("id") ?: "u-owner") }
                    .onSuccess { app.say("Stay extended"); extend = false; reload++; app.refresh() }
                    .onFailure { app.say(it.message ?: "That room is taken") }
            }, Icons.Default.Check, BtnKind.PRIMARY)
        }
    }
}

// ── Rooms & room types ───────────────────────────────────────────────────────
@Composable
fun RoomsScreen(app: AppState) {
    val hotel = app.hotel
    var rooms by remember { mutableStateOf(hotel.rooms()) }
    var types by remember { mutableStateOf(hotel.roomTypes()) }
    var showRoom by remember { mutableStateOf(false) }
    var showType by remember { mutableStateOf(false) }
    var editRoom by remember { mutableStateOf<Row?>(null) }
    var openFolio by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(app.tick) { rooms = hotel.rooms(); types = hotel.roomTypes() }
    val counts = rooms.groupBy { it.str("status") }.mapValues { it.value.size }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        ScreenHeader("Rooms & rates", "${rooms.size} rooms · ${types.size} room types · ${counts[Hotel.OCCUPIED] ?: 0} occupied") {
            Btn("Room type", { showType = true }, Icons.Default.Category, BtnKind.GHOST)
            Btn("Add room", { editRoom = null; showRoom = true }, Icons.Default.Add, BtnKind.PRIMARY)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCard("Vacant clean", "${counts[Hotel.VACANT_CLEAN] ?: 0}", "ready to sell", Da.Green, Modifier.weight(1f))
            KpiCard("Vacant dirty", "${counts[Hotel.VACANT_DIRTY] ?: 0}", "housekeeping", Da.Amber, Modifier.weight(1f))
            KpiCard("Occupied", "${counts[Hotel.OCCUPIED] ?: 0}", "guests in house", Da.Blue, Modifier.weight(1f))
            KpiCard("Out of order", "${counts[Hotel.OUT_OF_ORDER] ?: 0}", "maintenance", Da.Red, Modifier.weight(1f))
            KpiCard("Average rate", Fmt.money(if (rooms.isEmpty()) 0.0 else rooms.sumOf { hotel.roomRate(it) } / rooms.size), "per night", Da.Purple, Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))

        if (rooms.isEmpty()) {
            CardBox(Modifier.fillMaxWidth()) {
                EmptyState("No rooms yet. Create a room type (Single, Double, Suite…) then add the room numbers.") {
                    Btn("Start with a room type", { showType = true }, Icons.Default.Category)
                }
            }
        } else {
            CardBox(Modifier.weight(1f), padding = 14.dp) {
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(rooms.chunked(4)) { row ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { r ->
                                RoomCard(app, r) { editRoom = r; showRoom = true }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }

    if (showRoom) RoomDialog(app, editRoom) { showRoom = false; app.refresh() }
    if (showType) RoomTypeDialog(app) { showType = false; app.refresh() }
    openFolio?.let { id -> FolioDialog(app, id) { openFolio = null } }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.RoomCard(app: AppState, r: Row, onEdit: () -> Unit) {
    val hotel = app.hotel
    val status = r.str("status")
    val live = remember(r.str("id"), app.tick) {
        if (status == Hotel.OCCUPIED) app.repo.db.one(
            "SELECT * FROM bookings WHERE room_id=? AND status='CHECKED_IN' LIMIT 1", r.str("id")
        ) else null
    }
    Column(
        Modifier.weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(Da.Surface2)
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(r.str("number"), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = statusColor(status), modifier = Modifier.weight(1f))
            Pill(status.replace("_", " ").lowercase(), statusColor(status))
        }
        Text(r.str("type_name").ifBlank { "No type" }, fontSize = 11.5.sp, color = Da.Muted, maxLines = 1)
        Text("${Fmt.money(hotel.roomRate(r))} · ${r.str("type_beds").ifBlank { "${r.int("type_capacity")} pax" }}",
            fontSize = 11.sp, color = Da.Dim, maxLines = 1)
        if (live != null) {
            Text(live.str("guest_name"), fontSize = 11.5.sp, color = Da.Text, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("out ${Fmt.date(live.str("check_out_date"))}", fontSize = 10.5.sp, color = Da.Dim)
        } else {
            Spacer(Modifier.height(2.dp))
            Text(r.str("note").ifBlank { " " }, fontSize = 10.5.sp, color = Da.Dim, maxLines = 1)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (status == Hotel.VACANT_DIRTY) Btn("Clean", {
                hotel.setRoomStatus(r.str("id"), Hotel.VACANT_CLEAN); app.say("Room ${r.str("number")} clean"); app.refresh()
            }, Icons.Default.CleaningServices, BtnKind.SUBTLE)
            if (status == Hotel.VACANT_CLEAN) Btn("Dirty", {
                hotel.setRoomStatus(r.str("id"), Hotel.VACANT_DIRTY); app.refresh()
            }, Icons.Default.DryCleaning, BtnKind.SUBTLE)
            if (live != null) Btn("Folio", { app.go("frontdesk") }, Icons.Default.Receipt, BtnKind.SUBTLE)
            Btn("Edit", onEdit, Icons.Default.Edit, BtnKind.GHOST)
        }
    }
}

@Composable
private fun RoomDialog(app: AppState, existing: Row?, onDone: () -> Unit) {
    val hotel = app.hotel
    val types = remember(app.tick) { hotel.roomTypes() }
    var number by remember { mutableStateOf(existing?.str("number") ?: "") }
    var typePick by remember { mutableStateOf(existing?.str("type_name") ?: types.firstOrNull()?.str("name") ?: "") }
    var floor by remember { mutableStateOf(existing?.str("floor") ?: "1") }
    var status by remember { mutableStateOf(existing?.str("status") ?: Hotel.VACANT_CLEAN) }
    var note by remember { mutableStateOf(existing?.str("note") ?: "") }
    var override by remember { mutableStateOf(existing?.dbl("rate_override")?.takeIf { it > 0 }?.toInt()?.toString() ?: "") }

    DialogBox(if (existing == null) "Add a room" else "Edit room ${existing.str("number")}", onDone, 520.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Room number / name", number, { number = it }, Modifier.weight(1f), placeholder = "e.g. 101 or Annex A")
            Field("Floor / block", floor, { floor = it }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Drop("Room type", types.map { it.str("name") }, typePick, { typePick = it })
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Drop("Condition", listOf(Hotel.VACANT_CLEAN, Hotel.VACANT_DIRTY, Hotel.OCCUPIED, Hotel.OUT_OF_ORDER),
                listOf(Hotel.VACANT_CLEAN, Hotel.VACANT_DIRTY, Hotel.OCCUPIED, Hotel.OUT_OF_ORDER)
                    .firstOrNull { it == status } ?: Hotel.VACANT_CLEAN, { status = it }, Modifier.weight(1f))
            MoneyField("Rate override (optional)", override, { override = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Field("Note", note, { note = it }, placeholder = "e.g. balcony, hot shower, near the bar")
        Spacer(Modifier.height(14.dp))
        Btn(if (existing == null) "Add room" else "Save room", {
            runCatching {
                val typeId = types.firstOrNull { it.str("name") == typePick }?.str("id")
                hotel.saveRoom(existing?.str("id"), number, typeId, floor, status, note, override.toDoubleOrNull() ?: 0.0)
            }.onSuccess { app.say("Room ${number} saved"); onDone() }
                .onFailure { app.say(it.message ?: "Could not save") }
        }, Icons.Default.Check, BtnKind.PRIMARY)
    }
}

@Composable
private fun RoomTypeDialog(app: AppState, onDone: () -> Unit) {
    val hotel = app.hotel
    var types by remember { mutableStateOf(hotel.roomTypes()) }
    var name by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var capacity by remember { mutableStateOf("2") }
    var beds by remember { mutableStateOf("1 double") }
    var taxRate by remember { mutableStateOf("18") }

    DialogBox("Room types & rates", onDone, 620.dp) {
        if (types.isEmpty()) {
            Text("No room types yet. A type sets the nightly rate that every room of that kind copies.",
                fontSize = 12.sp, color = Da.Muted)
        } else {
            Table(
                listOf("Type", "Beds", "Sleeps", "Rate / night", "VAT %"),
                listOf(1.6f, 1.2f, 0.7f, 1.2f, 0.6f),
                types.map {
                    listOf(
                        Cell(it.str("name"), Da.Text, bold = true),
                        Cell(it.str("beds"), Da.Muted),
                        Cell("${it.int("capacity")}", Da.Muted),
                        Cell(Fmt.money(it.dbl("base_rate")), Da.Green, bold = true),
                        Cell(Fmt.qty(it.dbl("tax_rate")), Da.Dim)
                    )
                },
                scrollable = false
            )
        }
        Spacer(Modifier.height(14.dp))
        SectionTitle("Add a room type", "Rate is what a night costs; VAT is already inside that price.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Name", name, { name = it }, Modifier.weight(1.4f), placeholder = "Double / Twin / Suite")
            MoneyField("Rate / night", rate, { rate = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Beds", beds, { beds = it }, Modifier.weight(1.4f))
            Field("Sleeps", capacity, { capacity = it.filter { c -> c.isDigit() } }, Modifier.weight(1f))
            Field("VAT %", taxRate, { taxRate = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.weight(0.8f))
        }
        Spacer(Modifier.height(14.dp))
        Btn("Save room type", {
            runCatching {
                hotel.saveRoomType(null, name, rate.toDoubleOrNull() ?: 0.0, capacity.toIntOrNull() ?: 2, beds,
                    taxRate = taxRate.toDoubleOrNull() ?: 0.0)
            }.onSuccess { types = hotel.roomTypes(); name = ""; rate = ""; app.say("Room type saved"); app.refresh() }
                .onFailure { app.say(it.message ?: "Could not save") }
        }, Icons.Default.Check, BtnKind.PRIMARY)
    }
}

// ── Bookings register ────────────────────────────────────────────────────────
@Composable
fun BookingsScreen(app: AppState) {
    val hotel = app.hotel
    var filter by remember { mutableStateOf(0) }
    var search by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf(hotel.bookings("OPEN")) }
    var openFolio by remember { mutableStateOf<String?>(null) }
    var showBooking by remember { mutableStateOf(false) }
    val filters = listOf("OPEN", "ALL", Hotel.CHECKED_OUT, Hotel.CANCELLED)

    LaunchedEffect(app.tick, filter, search) {
        rows = hotel.bookings(filters[filter.coerceIn(0, filters.lastIndex)], search)
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        ScreenHeader("Bookings", "Every reservation, arrival and closed folio in one register") {
            Btn("New booking", { showBooking = true }, Icons.Default.Add, BtnKind.PRIMARY)
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(360.dp)) { SearchBar(search, { search = it }, "Guest, phone, ref or room") }
            Spacer(Modifier.width(12.dp))
            TabBar(listOf("Open", "All", "Checked out", "Cancelled"), filter, { filter = it }, Modifier.width(440.dp))
        }
        CardBox(Modifier.weight(1f), padding = 0.dp) {
            Table(
                listOf("Ref", "Guest", "Room", "From", "To", "Nights", "Status", "Charges", "Paid", "Balance"),
                listOf(0.9f, 1.6f, 0.7f, 0.9f, 0.9f, 0.6f, 0.9f, 1f, 1f, 1f),
                rows.map { b ->
                    val folio = hotel.folio(b.str("id"))
                    listOf(
                        Cell(b.str("ref_no"), Da.Dim),
                        Cell(b.str("guest_name"), Da.Text, bold = true),
                        Cell(b.str("room_number"), Da.Blue),
                        Cell(Fmt.dateShort(b.str("check_in_date")), Da.Muted),
                        Cell(Fmt.dateShort(b.str("check_out_date")), Da.Muted),
                        Cell("${b.int("nights")}", Da.Muted),
                        Cell(Hotel.bookingLabel(b.str("status")), bookingColor(b.str("status"))),
                        Cell(Fmt.money(folio.gross), Da.Text, align = TextAlign.End),
                        Cell(Fmt.money(folio.paid), Da.Green, align = TextAlign.End),
                        Cell(Fmt.money(folio.balance), if (folio.balance > 0.5) Da.Red else Da.Dim, bold = true, align = TextAlign.End)
                    )
                },
                onRowClick = { i -> openFolio = rows[i].str("id") }
            )
        }
    }
    if (showBooking) NewBookingDialog(app) { showBooking = false }
    openFolio?.let { id -> FolioDialog(app, id) { openFolio = null } }
}

// ── Guests ───────────────────────────────────────────────────────────────────
@Composable
fun GuestsScreen(app: AppState) {
    val hotel = app.hotel
    var search by remember { mutableStateOf("") }
    var guests by remember { mutableStateOf(hotel.guests()) }
    var showAdd by remember { mutableStateOf(false) }
    var openGuest by remember { mutableStateOf<Row?>(null) }

    LaunchedEffect(app.tick, search) { guests = hotel.guests(search) }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        ScreenHeader("Guests", "${guests.size} on file · phone numbers, ID and stay history") {
            Btn("Add guest", { showAdd = true }, Icons.Default.PersonAdd, BtnKind.PRIMARY)
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Box(Modifier.width(360.dp)) { SearchBar(search, { search = it }, "Name, phone or ID") }
        }
        CardBox(Modifier.weight(1f), padding = 0.dp) {
            Table(
                listOf("Guest", "Phone", "ID number", "Nationality", "Stays", "Last stay"),
                listOf(1.8f, 1.2f, 1.1f, 1f, 0.6f, 1f),
                guests.map { g ->
                    val stays = hotel.guestStays(g.str("id"))
                    listOf(
                        Cell(g.str("name"), Da.Text, bold = true),
                        Cell(g.str("phone"), Da.Blue),
                        Cell(g.str("id_number"), Da.Muted),
                        Cell(g.str("nationality"), Da.Muted),
                        Cell("${stays.size}", Da.Purple),
                        Cell(stays.firstOrNull()?.let { Fmt.date(it.str("check_in_date")) } ?: "—", Da.Dim)
                    )
                },
                onRowClick = { i -> openGuest = guests[i] }
            )
        }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var idNumber by remember { mutableStateOf("") }
        var nationality by remember { mutableStateOf("Ugandan") }
        var address by remember { mutableStateOf("") }
        DialogBox("Add a guest", { showAdd = false }, 560.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Full name", name, { name = it }, Modifier.weight(1.4f))
                Field("Phone", phone, { phone = it }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("ID / passport", idNumber, { idNumber = it }, Modifier.weight(1f))
                Field("Nationality", nationality, { nationality = it }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Field("Email", email, { email = it }, Modifier.weight(1f))
            Spacer(Modifier.height(10.dp))
            Field("Address", address, { address = it })
            Spacer(Modifier.height(14.dp))
            Btn("Save guest", {
                runCatching { hotel.saveGuest(null, name, phone, email, idNumber, nationality, address) }
                    .onSuccess { app.say("Guest saved"); showAdd = false; app.refresh() }
                    .onFailure { app.say(it.message ?: "Could not save") }
            }, Icons.Default.Check, BtnKind.PRIMARY)
        }
    }

    openGuest?.let { g ->
        val stays = hotel.guestStays(g.str("id"))
        DialogBox("${g.str("name")} · guest file", { openGuest = null }, 620.dp) {
            FormRow("Phone", g.str("phone").ifBlank { "—" })
            FormRow("ID / passport", g.str("id_number").ifBlank { "—" })
            FormRow("Nationality", g.str("nationality").ifBlank { "—" })
            FormRow("Address", g.str("address").ifBlank { "—" })
            if (g.str("notes").isNotBlank()) FormRow("Notes", g.str("notes"))
            Spacer(Modifier.height(12.dp))
            SectionTitle("Stay history", "${stays.size} bookings")
            Table(
                listOf("Ref", "Room", "From", "To", "Status", "Bill"),
                listOf(1f, 0.7f, 1f, 1f, 1f, 1f),
                stays.map {
                    listOf(
                        Cell(it.str("ref_no"), Da.Dim),
                        Cell(it.str("room_id").take(0) + "—", Da.Muted),
                        Cell(Fmt.date(it.str("check_in_date")), Da.Muted),
                        Cell(Fmt.date(it.str("check_out_date")), Da.Muted),
                        Cell(Hotel.bookingLabel(it.str("status")), bookingColor(it.str("status"))),
                        Cell(Fmt.money(it.dbl("invoice_total")), Da.Text, bold = true, align = TextAlign.End)
                    )
                },
                scrollable = false
            )
        }
    }
}

// ── Hotel reports ────────────────────────────────────────────────────────────
@Composable
fun HotelReportsScreen(app: AppState) {
    val hotel = app.hotel
    var from by remember { mutableStateOf(Hotel.addDays(Clock.today(), -29)) }
    var to by remember { mutableStateOf(Clock.today()) }
    var occ by remember { mutableStateOf(hotel.occupancy(from, to)) }
    var revenue by remember { mutableStateOf(hotel.revenueBreakdown(from, to)) }
    var rooms by remember { mutableStateOf(hotel.rooms()) }
    var types by remember { mutableStateOf(hotel.roomTypes()) }

    LaunchedEffect(app.tick, from, to) {
        occ = hotel.occupancy(from, to); revenue = hotel.revenueBreakdown(from, to)
        rooms = hotel.rooms(); types = hotel.roomTypes()
    }
    val totalRevenue = revenue.sumOf { it.amount }
    val deposits = hotel.depositsHeld()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        ScreenHeader("Hotel reports", "Occupancy, average rate and what the rooms actually earned") {
            Btn("Front desk", { app.go("frontdesk") }, Icons.Default.Hotel, BtnKind.GHOST)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(150.dp)) { Field("From", from, { from = it }) }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.width(150.dp)) { Field("To", to, { to = it }) }
            Spacer(Modifier.width(12.dp))
            Btn("This month", { from = Clock.monthStart(); to = Clock.today() }, null, BtnKind.SUBTLE)
            Spacer(Modifier.width(8.dp))
            Btn("Last 30 days", { from = Hotel.addDays(Clock.today(), -29); to = Clock.today() }, null, BtnKind.SUBTLE)
        }
        Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCard("Occupancy", Fmt.pct(occ.occupancyPct), "${occ.roomNightsSold} of ${occ.roomNightsAvailable} room nights", Da.Green, Modifier.weight(1f))
            KpiCard("ADR", Fmt.money(occ.adr), "average room rate", Da.Blue, Modifier.weight(1f))
            KpiCard("RevPAR", Fmt.money(occ.revpar), "per available room", Da.Purple, Modifier.weight(1f))
            KpiCard("Hotel revenue", Fmt.money(totalRevenue), "recognised in the ledger", Da.Amber, Modifier.weight(1f))
            KpiCard("Deposits held", Fmt.money(deposits), "still a liability", Da.Red, Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))
        CardBox(Modifier.fillMaxWidth()) {
            SectionTitle("Rooms occupied per night", "Bars show how many rooms had a guest, night by night")
            BarChart(
                occ.byDay.map { it.date.take(5) + "/" + it.date.take(7).takeLast(2) to it.occupied.toDouble() },
                color = Da.Blue, barHeight = 130.dp,
                valueLabel = { it.toInt().toString() }
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CardBox(Modifier.weight(1f)) {
                SectionTitle("Where the money came from", "Straight from the ledger, VAT excluded")
                revenue.forEach { line ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(line.label, fontSize = 12.5.sp, color = Da.Muted, modifier = Modifier.weight(1f))
                        Text(Fmt.money(line.amount), fontSize = 13.sp, color = Da.Text, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(6.dp))
                MiniProgress((if (totalRevenue <= 0) 0f else (revenue.firstOrNull()?.amount ?: 0.0) / totalRevenue).toFloat(), Da.Green)
                Spacer(Modifier.height(4.dp))
                Text("Accommodation share of hotel revenue", fontSize = 10.5.sp, color = Da.Dim)
            }
            CardBox(Modifier.weight(1f)) {
                SectionTitle("Room types", "${types.size} categories · ${rooms.size} rooms")
                Table(
                    listOf("Type", "Rooms", "Rate", "Sleeps"),
                    listOf(1.6f, 0.7f, 1.2f, 0.7f),
                    types.map { t ->
                        listOf(
                            Cell(t.str("name"), Da.Text, bold = true),
                            Cell("${rooms.count { r -> r.str("room_type_id") == t.str("id") }}", Da.Muted),
                            Cell(Fmt.money(t.dbl("base_rate")), Da.Green),
                            Cell("${t.int("capacity")}", Da.Muted)
                        )
                    },
                    scrollable = false
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        CardBox(Modifier.fillMaxWidth()) {
            SectionTitle("Nightly detail", "Occupancy and room revenue per night")
            Table(
                listOf("Date", "Rooms let", "Available", "Occupancy", "Room revenue"),
                listOf(1.3f, 0.9f, 1f, 1f, 1.3f),
                occ.byDay.reversed().map {
                    listOf(
                        Cell(Fmt.date(it.date), Da.Muted),
                        Cell("${it.occupied}", Da.Blue, bold = true),
                        Cell("${it.available}", Da.Dim),
                        Cell(Fmt.pct(it.occupancyPct), if (it.occupancyPct > 60) Da.Green else Da.Amber),
                        Cell(Fmt.money(it.revenue), Da.Text, align = TextAlign.End)
                    )
                },
                scrollable = false, maxRows = 40
            )
        }
    }
}
