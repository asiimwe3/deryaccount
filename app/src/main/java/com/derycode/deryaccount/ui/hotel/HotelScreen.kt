@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.derycode.deryaccount.ui.hotel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import kotlinx.coroutines.flow.first
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.data.local.AppDatabase
import com.derycode.deryaccount.data.local.entity.*
import com.derycode.deryaccount.hotel.HotelRepo
import com.derycode.deryaccount.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Hotel & accommodation on the phone: a front desk in your pocket.
 * Desk tab for today's work, Rooms for the board and housekeeping,
 * Bookings for the register, Reports for occupancy and revenue.
 */
@Composable
fun HotelScreen(db: AppDatabase, branchId: String, userId: String) {
    val repo = remember { HotelRepo(db, branchId, userId) }
    var tab by remember { mutableStateOf(0) }
    var refresh by remember { mutableStateOf(0) }
    var newBooking by remember { mutableStateOf(false) }
    var folioFor by remember { mutableStateOf<String?>(null) }
    var roomsSetup by remember { mutableStateOf(false) }
    var guestFile by remember { mutableStateOf<Guest?>(null) }

    val rooms by repo.rooms().collectAsState(initial = emptyList())
    val types by repo.roomTypes().collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab, containerColor = DaSurface) {
            listOf("Desk", "Rooms", "Bookings", "Audit", "Reports").forEachIndexed { i, label ->
                Tab(
                    selected = tab == i, onClick = { tab = i },
                    text = { Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                )
            }
        }

        when (tab) {
            0 -> DeskTab(
                repo, rooms, types, refresh,
                onNewBooking = { newBooking = true },
                onSetup = { roomsSetup = true },
                onOpenFolio = { folioFor = it },
                onChanged = { refresh++ }
            )
            1 -> RoomsTab(
                repo, rooms, types, refresh,
                onOpenFolio = { folioFor = it },
                onSetup = { roomsSetup = true },
                onChanged = { refresh++ }
            )
            2 -> BookingsTab(repo, rooms, refresh, onOpenFolio = { folioFor = it })
            3 -> AuditTab(repo, refresh, onChanged = { refresh++ })
            else -> ReportsTab(repo, refresh)
        }
    }

    if (newBooking) NewBookingDialog(repo, rooms, types) {
        newBooking = false; refresh++
    }
    if (roomsSetup) RoomsSetupDialog(repo, rooms, types) {
        roomsSetup = false; refresh++
    }
    folioFor?.let { id ->
        FolioDialog(repo, id, onClose = { folioFor = null; refresh++ })
    }
    guestFile?.let { g ->
        GuestDialog(repo, g) { guestFile = null }
    }
}

// ── Shared bits ──────────────────────────────────────────────────────────────

private fun roomStatusColor(status: String): Color = when (status) {
    HotelRepo.VACANT_CLEAN -> DaGreen
    HotelRepo.VACANT_DIRTY -> DaAmber
    HotelRepo.OCCUPIED -> DaBlue
    else -> DaRed
}

private fun bookingColor(status: String): Color = when (status) {
    HotelRepo.BOOKED -> DaAmber
    HotelRepo.CHECKED_IN -> DaBlue
    HotelRepo.CHECKED_OUT -> DaGreen
    else -> DaRed
}

@Composable
private fun KpiChip(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DaSurface2)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label.uppercase(), fontSize = 9.sp, color = DaTextMuted, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = accent, maxLines = 1)
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Box(
        Modifier
            .background(color.copy(alpha = 0.16f), androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, fontSize = 10.sp, color = color, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun SectionHeader(text: String, sub: String? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DaTextPrimary)
            if (sub != null) Text(sub, fontSize = 11.sp, color = DaTextMuted)
        }
    }
}

// ── Desk ─────────────────────────────────────────────────────────────────────
@Composable
private fun DeskTab(
    repo: HotelRepo, rooms: List<HotelRoom>, types: List<RoomType>, refresh: Int,
    onNewBooking: () -> Unit, onSetup: () -> Unit, onOpenFolio: (String) -> Unit,
    onChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var kpis by remember { mutableStateOf<HotelRepo.Kpis?>(null) }
    var arrivals by remember { mutableStateOf<List<Booking>>(emptyList()) }
    var inHouse by remember { mutableStateOf<List<Booking>>(emptyList()) }
    var departures by remember { mutableStateOf<List<Booking>>(emptyList()) }
    var tasks by remember { mutableStateOf<List<HousekeepingTask>>(emptyList()) }
    var balances by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refresh, rooms.size) {
        kpis = repo.kpis()
        arrivals = repo.arrivals()
        inHouse = repo.inHouse()
        departures = repo.departures()
        tasks = repo.openTasksOnce()
        balances = (arrivals + inHouse + departures).distinctBy { it.id }
            .associate { it.id to repo.folio(it.id).balance }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        if (rooms.isEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text("Set up your rooms first", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DaTextPrimary)
            Text(
                "Add a room type with its nightly rate, then every room number. After that the front desk runs itself.",
                fontSize = 12.5.sp, color = DaTextMuted, modifier = Modifier.padding(vertical = 6.dp)
            )
            Button(onClick = onSetup, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("Set up rooms & rates", fontWeight = FontWeight.Bold)
            }
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpiChip("Occupancy", kpis?.let { HotelRepo.pct(it.occupancyPct) } ?: "—", DaGreen, Modifier.weight(1f))
                    KpiChip("In house", "${kpis?.guestsInHouse ?: 0}", DaBlue, Modifier.weight(1f))
                    KpiChip("Arrivals", "${kpis?.arrivals ?: 0}", DaAmber, Modifier.weight(1f))
                    KpiChip("Owed", kpis?.let { "UGX %,d".format(it.outstanding.toLong()) } ?: "—", DaRed, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "${kpis?.vacantClean ?: 0} clean · ${kpis?.dirty ?: 0} to clean · " +
                        "room revenue this month ${kpis?.let { HotelRepo.money(it.revenueThisMonth) } ?: ""}",
                    fontSize = 11.5.sp, color = DaTextMuted
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onNewBooking, modifier = Modifier.weight(1f).height(46.dp)) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text("New booking", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = onSetup, modifier = Modifier.height(46.dp)) {
                        Text("Rooms")
                    }
                }
                message?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontSize = 12.sp, color = DaGreen)
                }

                BookingSection(
                    "Arrivals today", "${arrivals.size} waiting", arrivals, rooms, balances,
                    emptyText = "No arrivals waiting.",
                    primaryLabel = "Check in",
                    onPrimary = { b ->
                        scope.launch {
                            runCatching { repo.checkIn(b.id) }
                                .onSuccess { message = "${b.guestName} checked in"; onChanged() }
                                .onFailure { message = it.message ?: "Could not check in" }
                        }
                    },
                    onOpenFolio = onOpenFolio
                )

                BookingSection(
                    "In house", "${inHouse.size} rooms occupied", inHouse, rooms, balances,
                    emptyText = "No guests in house right now.",
                    primaryLabel = "Folio",
                    onPrimary = { b -> onOpenFolio(b.id) },
                    onOpenFolio = onOpenFolio
                )

                BookingSection(
                    "Departures", "${departures.size} checking out", departures, rooms, balances,
                    emptyText = "Nobody is checking out today.",
                    primaryLabel = "Check out",
                    onPrimary = { b -> onOpenFolio(b.id) },
                    onOpenFolio = onOpenFolio
                )

                SectionHeader("Housekeeping", "${tasks.size} open jobs")
                if (tasks.isEmpty()) {
                    Text("Nothing to clean or fix. Rooms are turning over cleanly.",
                        fontSize = 12.sp, color = DaTextMuted, modifier = Modifier.padding(bottom = 12.dp))
                } else {
                    tasks.forEach { t ->
                        val room = rooms.firstOrNull { it.id == t.roomId }
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Room ${room?.number ?: "?"} · ${t.kind.lowercase().replaceFirstChar { it.uppercase() }}",
                                    fontSize = 13.sp, color = DaTextPrimary, fontWeight = FontWeight.SemiBold)
                                Text(t.note.ifBlank { "No note" }, fontSize = 11.sp, color = DaTextMuted)
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    repo.completeTask(t.id)
                                    message = "Job closed"
                                    onChanged()
                                }
                            }) { Text("Done", color = DaGreen, fontWeight = FontWeight.SemiBold) }
                        }
                        Divider(color = DaOutline)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun BookingSection(
    title: String, sub: String, rows: List<Booking>, rooms: List<HotelRoom>, balances: Map<String, Double>,
    emptyText: String, primaryLabel: String,
    onPrimary: (Booking) -> Unit, onOpenFolio: (String) -> Unit
) {
    SectionHeader(title, sub)
    if (rows.isEmpty()) {
        Text(emptyText, fontSize = 12.sp, color = DaTextMuted, modifier = Modifier.padding(bottom = 12.dp))
        return
    }
    rows.forEach { b ->
        Card(
            Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onOpenFolio(b.id) },
            colors = CardDefaults.cardColors(containerColor = DaSurface)
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(b.guestName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DaTextPrimary)
                        Text(
                            "${b.refNo} · room ${rooms.firstOrNull { it.id == b.roomId }?.number ?: "?"} · " +
                                "${b.checkInDate} → ${b.checkOutDate}",
                            fontSize = 10.5.sp, color = DaTextMuted, maxLines = 1
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        StatusPill(HotelRepo.bookingLabel(b.status), bookingColor(b.status))
                        val balance = balances[b.id] ?: 0.0
                        if (balance > 0.5) Text(
                            "owes %,d".format(balance.toLong()),
                            fontSize = 10.5.sp, color = DaRed, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onPrimary(b) },
                        modifier = Modifier.weight(1f).height(38.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text(primaryLabel, fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = { onOpenFolio(b.id) },
                        modifier = Modifier.weight(1f).height(38.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("Open folio", fontSize = 12.sp) }
                }
            }
        }
    }
}

// ── Rooms ────────────────────────────────────────────────────────────────────
@Composable
private fun RoomsTab(
    repo: HotelRepo, rooms: List<HotelRoom>, types: List<RoomType>, refresh: Int,
    onOpenFolio: (String) -> Unit, onSetup: () -> Unit, onChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var live by remember { mutableStateOf<Map<String, Booking>>(emptyMap()) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refresh, rooms.size) {
        live = repo.inHouse().associateBy { it.roomId }
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KpiChip("Clean", "${rooms.count { it.status == HotelRepo.VACANT_CLEAN }}", DaGreen, Modifier.weight(1f))
                KpiChip("To clean", "${rooms.count { it.status == HotelRepo.VACANT_DIRTY }}", DaAmber, Modifier.weight(1f))
                KpiChip("Occupied", "${rooms.count { it.status == HotelRepo.OCCUPIED }}", DaBlue, Modifier.weight(1f))
                KpiChip("Broken", "${rooms.count { it.status == HotelRepo.OUT_OF_ORDER }}", DaRed, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onSetup, modifier = Modifier.fillMaxWidth().height(46.dp)) {
                Icon(Icons.Default.MeetingRoom, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text("Manage rooms & rates", fontWeight = FontWeight.Bold)
            }
            message?.let {
                Spacer(Modifier.height(6.dp)); Text(it, fontSize = 12.sp, color = DaGreen)
            }
            Spacer(Modifier.height(8.dp))
        }
        items(rooms) { room ->
            val type = types.firstOrNull { it.id == room.roomTypeId }
            val guest = live[room.id]
            Card(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = DaSurface)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(room.number, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            color = roomStatusColor(room.status), modifier = Modifier.width(56.dp))
                        Column(Modifier.weight(1f)) {
                            Text(type?.name ?: "No type", fontSize = 13.sp, color = DaTextPrimary, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${HotelRepo.money(repo.rateOf(room, type))} per night" +
                                    (type?.beds?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                                fontSize = 11.sp, color = DaTextMuted
                            )
                        }
                        StatusPill(HotelRepo.statusLabel(room.status), roomStatusColor(room.status))
                    }
                    if (guest != null) {
                        Spacer(Modifier.height(4.dp))
                        Text("${guest.guestName} · out ${guest.checkOutDate}", fontSize = 11.5.sp, color = DaBlue)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (room.status == HotelRepo.VACANT_DIRTY) TextButton(onClick = {
                            scope.launch { repo.setRoomStatus(room.id, HotelRepo.VACANT_CLEAN); message = "Room ${room.number} clean"; onChanged() }
                        }) { Text("Mark clean", fontSize = 12.sp, color = DaGreen) }
                        if (room.status == HotelRepo.VACANT_CLEAN) TextButton(onClick = {
                            scope.launch { repo.setRoomStatus(room.id, HotelRepo.VACANT_DIRTY); onChanged() }
                        }) { Text("Needs cleaning", fontSize = 12.sp) }
                        if (room.status != HotelRepo.OUT_OF_ORDER) TextButton(onClick = {
                            scope.launch {
                                repo.raiseTask(room.id, "MAINTENANCE", "Reported from the room board")
                                message = "Room ${room.number} out of order"; onChanged()
                            }
                        }) { Text("Out of order", fontSize = 12.sp, color = DaRed) }
                        if (guest != null) TextButton(onClick = { onOpenFolio(guest.id) }) {
                            Text("Folio", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ── Bookings ─────────────────────────────────────────────────────────────────
@Composable
private fun BookingsTab(
    repo: HotelRepo, rooms: List<HotelRoom>, refresh: Int, onOpenFolio: (String) -> Unit
) {
    var showAll by remember { mutableStateOf(false) }
    val open by repo.openBookings().collectAsState(initial = emptyList())
    val all by repo.allBookings().collectAsState(initial = emptyList())
    val rows = if (showAll) all else open
    var filter by remember { mutableStateOf(0) }
    val filters = listOf("Open", "Checked out", "Cancelled")
    val filtered = when (filter) {
        1 -> all.filter { it.status == HotelRepo.CHECKED_OUT }
        2 -> all.filter { it.status == HotelRepo.CANCELLED || it.status == HotelRepo.NO_SHOW }
        else -> rows
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            filters.forEachIndexed { i, label ->
                FilterChip(selected = filter == i, onClick = {
                    filter = i
                    showAll = i != 0
                }, label = { Text(label, fontSize = 12.sp) })
            }
        }
        if (filtered.isEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("Nothing here yet.", fontSize = 13.sp, color = DaTextMuted)
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(filtered) { b ->
                val room = rooms.firstOrNull { it.id == b.roomId }
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onOpenFolio(b.id) },
                    colors = CardDefaults.cardColors(containerColor = DaSurface)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(b.guestName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DaTextPrimary)
                            Text("${b.refNo} · room ${room?.number ?: "?"} · ${b.nights} night${if (b.nights == 1) "" else "s"}",
                                fontSize = 11.sp, color = DaTextMuted)
                            Text("${b.checkInDate} → ${b.checkOutDate}", fontSize = 10.5.sp, color = DaTextMuted)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            StatusPill(HotelRepo.bookingLabel(b.status), bookingColor(b.status))
                            Spacer(Modifier.height(3.dp))
                            Text(HotelRepo.money(b.invoiceTotal), fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold, color = DaTextPrimary)
                            if (b.paidTotal < b.invoiceTotal - 0.5) Text(
                                "owes %,d".format((b.invoiceTotal - b.paidTotal).toLong()),
                                fontSize = 10.sp, color = DaRed
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ── Reports ──────────────────────────────────────────────────────────────────
@Composable
private fun ReportsTab(repo: HotelRepo, refresh: Int) {
    var range by remember { mutableStateOf(1) }
    var occ by remember { mutableStateOf<HotelRepo.Occupancy?>(null) }
    var revenue by remember { mutableStateOf<List<HotelRepo.RevenueLine>>(emptyList()) }
    var deposits by remember { mutableStateOf(0.0) }

    val labels = listOf("Today", "7 days", "30 days")
    LaunchedEffect(refresh, range) {
        val to = HotelRepo.today()
        val from = when (range) {
            0 -> to
            1 -> HotelRepo.addDays(to, -6)
            else -> HotelRepo.addDays(to, -29)
        }
        occ = repo.occupancy(from, to)
        revenue = repo.revenueBreakdown(from, to)
        deposits = repo.depositsHeld()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp).verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            labels.forEachIndexed { i, label ->
                FilterChip(selected = range == i, onClick = { range = i }, label = { Text(label, fontSize = 12.sp) })
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpiChip("Occupancy", occ?.let { HotelRepo.pct(it.pct) } ?: "—", DaGreen, Modifier.weight(1f))
            KpiChip("ADR", occ?.let { "%,d".format(it.adr.toLong()) } ?: "—", DaBlue, Modifier.weight(1f))
            KpiChip("RevPAR", occ?.let { "%,d".format(it.revpar.toLong()) } ?: "—", DaAmber, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpiChip("Room nights", occ?.let { "${it.sold}/${it.available}" } ?: "—", DaTextPrimary, Modifier.weight(1f))
            KpiChip("Deposits held", "%,d".format(deposits.toLong()), DaRed, Modifier.weight(1f))
        }

        SectionHeader("Where the money came from", "Recognised at check-out, VAT excluded")
        revenue.forEach { line ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Text(line.label, fontSize = 12.5.sp, color = DaTextMuted, modifier = Modifier.weight(1f))
                Text(HotelRepo.money(line.amount), fontSize = 13.sp, color = DaTextPrimary, fontWeight = FontWeight.SemiBold)
            }
            Divider(color = DaOutline)
        }

        SectionHeader("Night by night", "Rooms let and room revenue")
        occ?.byDay?.reversed()?.forEach { day ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(day.date, fontSize = 11.5.sp, color = DaTextMuted, modifier = Modifier.weight(1f))
                Text("${day.occupied}/${day.available}", fontSize = 12.sp, color = DaBlue, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(12.dp))
                Text(HotelRepo.money(day.revenue), fontSize = 12.sp, color = DaTextPrimary)
            }
            Divider(color = DaOutline)
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ── Dialogs ──────────────────────────────────────────────────────────────────

@Composable
private fun NewBookingDialog(
    repo: HotelRepo, rooms: List<HotelRoom>, types: List<RoomType>, onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var checkIn by remember { mutableStateOf(HotelRepo.today()) }
    var checkOut by remember { mutableStateOf(HotelRepo.addDays(HotelRepo.today(), 1)) }
    var roomId by remember { mutableStateOf("") }
    var adults by remember { mutableStateOf("1") }
    var children by remember { mutableStateOf("0") }
    var rate by remember { mutableStateOf("") }
    var deposit by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("CASH") }
    var checkInNow by remember { mutableStateOf(true) }
    var free by remember { mutableStateOf<List<HotelRoom>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(checkIn, checkOut, rooms.size) {
        free = repo.freeRooms(checkIn, checkOut)
        if (free.none { it.id == roomId }) {
            roomId = free.firstOrNull()?.id ?: ""
            rate = free.firstOrNull()?.let { repo.rateOf(it).toInt().toString() } ?: ""
        }
    }
    val nights = HotelRepo.nights(checkIn, checkOut)

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("New booking") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Guest name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = phone, onValueChange = { phone = it },
                    label = { Text("Phone") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = checkIn, onValueChange = { checkIn = it },
                        label = { Text("Check-in") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = checkOut, onValueChange = { checkOut = it },
                        label = { Text("Check-out") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Text(
                    if (nights > 0) "$nights night(s) · ${free.size} rooms free" else "Check-out must be after check-in",
                    fontSize = 11.sp, color = if (nights > 0) DaTextMuted else DaRed,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Text("Room", fontSize = 11.sp, color = DaTextMuted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    free.forEach { r ->
                        FilterChip(
                            selected = roomId == r.id,
                            onClick = {
                                roomId = r.id
                                rate = repo.rateOf(r, types.firstOrNull { it.id == r.roomTypeId }).toInt().toString()
                            },
                            label = {
                                Text("${r.number} · ${types.firstOrNull { it.id == r.roomTypeId }?.name ?: ""}", fontSize = 11.sp)
                            }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = adults, onValueChange = { adults = it.filter { c -> c.isDigit() } },
                        label = { Text("Adults") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = children, onValueChange = { children = it.filter { c -> c.isDigit() } },
                        label = { Text("Kids") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = rate, onValueChange = { rate = it.filter { c -> c.isDigit() } },
                        label = { Text("Rate/night") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1.4f))
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = deposit, onValueChange = { deposit = it.filter { c -> c.isDigit() } },
                    label = { Text("Deposit (optional)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("CASH", "MTN_MOMO", "AIRTEL_MONEY").forEach { m ->
                        FilterChip(selected = method == m, onClick = { method = m },
                            label = { Text(m.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }, fontSize = 11.sp) })
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = checkInNow, onCheckedChange = { checkInNow = it })
                    Text("Guest is here now (check in)", fontSize = 12.sp)
                }
                Text(
                    "Total for the stay: ${HotelRepo.money((rate.toDoubleOrNull() ?: 0.0) * nights)}",
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DaGreen
                )
                error?.let { Text(it, fontSize = 11.5.sp, color = DaRed, modifier = Modifier.padding(top = 4.dp)) }
            }
        },
        confirmButton = {
            Button(enabled = roomId.isNotBlank() && !saving, onClick = {
                scope.launch {
                    saving = true
                    runCatching {
                        if (checkInNow) repo.walkIn(
                            name, phone, roomId, checkOut,
                            adults.toIntOrNull() ?: 1, children.toIntOrNull() ?: 0,
                            rate.toDoubleOrNull(), "", deposit.toDoubleOrNull() ?: 0.0, method
                        ) else repo.createBooking(
                            name, phone, roomId, checkIn, checkOut,
                            adults.toIntOrNull() ?: 1, children.toIntOrNull() ?: 0,
                            rate.toDoubleOrNull(), "WALK_IN", "",
                            deposit.toDoubleOrNull() ?: 0.0, method
                        )
                    }.onSuccess { onDone() }.onFailure { error = it.message ?: "Could not book" }
                    saving = false
                }
            }) { Text(if (checkInNow) "Book & check in" else "Save booking") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } }
    )
}

@Composable
private fun FolioDialog(repo: HotelRepo, bookingId: String, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val booking by repo.bookingFlow(bookingId).collectAsState(initial = null)
    val charges by repo.charges(bookingId).collectAsState(initial = emptyList())
    val payments by repo.payments(bookingId).collectAsState(initial = emptyList())
    var refresh by remember { mutableStateOf(0) }
    var folio by remember { mutableStateOf(HotelRepo.Folio(0.0, 0.0, 0.0, 0.0, emptyMap())) }
    var showCharge by remember { mutableStateOf(false) }
    var showPayment by remember { mutableStateOf(false) }
    var showExtend by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(bookingId, refresh, charges.size, payments.size) { folio = repo.folio(bookingId) }

    // Guest receipt / tax invoice, built offline from this folio and shared.
    fun printDoc(invoice: Boolean) {
        val b = booking ?: return
        scope.launch {
            runCatching {
                val file = if (invoice)
                    HotelDocs.taxInvoice(context, b, charges, payments, folio)
                else HotelDocs.guestReceipt(context, b, charges, payments, folio)
                com.derycode.deryaccount.util.Share.file(
                    context, file,
                    title = if (invoice) "Share tax invoice" else "Share guest receipt"
                )
            }.onFailure { message = it.message ?: "Could not build the document" }
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Column {
                Text(booking?.guestName ?: "Folio", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${booking?.refNo ?: ""} · ${HotelRepo.bookingLabel(booking?.status ?: "")}",
                    fontSize = 11.sp, color = DaTextMuted
                )
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                booking?.let { b ->
                    Text(
                        "${b.checkInDate} → ${b.checkOutDate} · ${b.nights} night(s) · " +
                            "${HotelRepo.money(b.rate)} per night",
                        fontSize = 11.5.sp, color = DaTextMuted
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("Charges", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DaTextPrimary)
                charges.forEach { c ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${HotelRepo.kindLabel(c.kind)} · ${c.description}",
                                fontSize = 12.sp, color = DaTextPrimary
                            )
                            Text(c.chargeDate, fontSize = 10.sp, color = DaTextMuted)
                        }
                        Text(HotelRepo.money(c.amount), fontSize = 12.sp,
                            color = if (c.amount < 0) DaGreen else DaTextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                    Divider(color = DaOutline)
                }
                if (charges.isEmpty()) Text("Nothing charged yet.", fontSize = 11.5.sp, color = DaTextMuted)

                Spacer(Modifier.height(8.dp))
                Text("Payments", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DaTextPrimary)
                payments.forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text("${p.method.replace("_", " ")} · ${p.paidAt.take(10)}",
                            fontSize = 11.5.sp, color = DaTextMuted, modifier = Modifier.weight(1f))
                        Text(HotelRepo.money(p.amount), fontSize = 12.sp, color = DaGreen, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (payments.isEmpty()) Text("No payment yet.", fontSize = 11.5.sp, color = DaTextMuted)

                Spacer(Modifier.height(10.dp))
                Card(colors = CardDefaults.cardColors(containerColor = DaSurface2)) {
                    Column(Modifier.padding(10.dp)) {
                        Row { Text("Charges", fontSize = 12.sp, modifier = Modifier.weight(1f),
                            color = DaTextMuted); Text(HotelRepo.money(folio.gross), fontSize = 12.sp) }
                        Row { Text("VAT inside", fontSize = 12.sp, modifier = Modifier.weight(1f),
                            color = DaTextMuted); Text(HotelRepo.money(folio.tax), fontSize = 12.sp) }
                        Row { Text("Paid", fontSize = 12.sp, modifier = Modifier.weight(1f),
                            color = DaTextMuted); Text(HotelRepo.money(folio.paid), fontSize = 12.sp) }
                        Row {
                            Text("Balance", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f), color = DaTextPrimary)
                            Text(HotelRepo.money(folio.balance), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = if (folio.balance > 0.5) DaRed else DaGreen)
                        }
                    }
                }
                message?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontSize = 11.5.sp, color = DaGreen)
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { printDoc(false) }) {
                        Text("Receipt", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DaBlue)
                    }
                    TextButton(onClick = { printDoc(true) }) {
                        Text("Tax invoice", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DaBlue)
                    }
                    TextButton(onClick = { showCharge = true }) { Text("Add charge", fontSize = 12.sp) }
                    // One tap for the extras the desk sells every day.
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repo.upsells.take(4).forEach { up ->
                            AssistChip(
                                onClick = {
                                    scope.launch {
                                        runCatching { repo.addUpsell(bookingId, up) }
                                            .onSuccess { message = up.label + " added"; refresh++ }
                                            .onFailure { message = it.message ?: "Could not add " + up.label }
                                    }
                                },
                                label = { Text(up.label, fontSize = 11.sp, maxLines = 1) }
                            )
                        }
                    }
                    TextButton(onClick = { showPayment = true }) { Text("Take payment", fontSize = 12.sp) }
                    if (booking?.status == HotelRepo.CHECKED_IN)
                        TextButton(onClick = { showExtend = true }) { Text("Extend", fontSize = 12.sp) }
                    if (booking?.status == HotelRepo.BOOKED)
                        TextButton(onClick = {
                            scope.launch {
                                runCatching { repo.cancelBooking(bookingId, refundDeposit = true) }
                                    .onSuccess { message = "Booking cancelled" }
                                    .onFailure { message = it.message }
                                refresh++
                            }
                        }) { Text("Cancel booking", fontSize = 12.sp, color = DaRed) }
                }
            }
        },
        confirmButton = {
            when (booking?.status) {
                HotelRepo.BOOKED -> Button(onClick = {
                    scope.launch {
                        runCatching { repo.checkIn(bookingId) }
                            .onSuccess { message = "Checked in" }
                            .onFailure { message = it.message ?: "Could not check in" }
                        refresh++
                    }
                }) { Text("Check in") }
                HotelRepo.CHECKED_IN -> Button(onClick = {
                    scope.launch {
                        runCatching {
                            repo.checkOut(bookingId, settleToDebtor = folio.balance > 0.5)
                        }.onSuccess {
                            message = "Checked out"
                            onClose()
                        }.onFailure { message = it.message ?: "Could not check out" }
                        refresh++
                    }
                }) { Text("Check out") }
                else -> TextButton(onClick = onClose) { Text("Close") }
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Done") } }
    )

    if (showCharge) {
        var kind by remember { mutableStateOf("RESTAURANT") }
        var desc by remember { mutableStateOf("") }
        var qty by remember { mutableStateOf("1") }
        var price by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showCharge = false },
            title = { Text("Charge to the room") },
            text = {
                Column {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        HotelRepo.CHARGE_KINDS.forEach { k ->
                            FilterChip(selected = kind == k, onClick = { kind = k },
                                label = { Text(HotelRepo.kindLabel(k), fontSize = 11.sp) })
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(value = desc, onValueChange = { desc = it },
                        label = { Text("What is it") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = qty, onValueChange = { qty = it.filter { c -> c.isDigit() } },
                            label = { Text("Qty") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = price, onValueChange = { price = it.filter { c -> c.isDigit() } },
                            label = { Text("Amount each") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(2f))
                    }
                    err?.let { Text(it, fontSize = 11.5.sp, color = DaRed) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        runCatching {
                            repo.addCharge(bookingId, kind, desc, qty.toDoubleOrNull() ?: 1.0,
                                price.toDoubleOrNull() ?: 0.0, if (kind == "DISCOUNT") 0.0 else 18.0)
                        }.onSuccess { showCharge = false; refresh++ }
                            .onFailure { err = it.message ?: "Could not post" }
                    }
                }) { Text("Post") }
            },
            dismissButton = { TextButton(onClick = { showCharge = false }) { Text("Cancel") } }
        )
    }

    if (showPayment) {
        var amount by remember { mutableStateOf(if (folio.balance > 0) folio.balance.toInt().toString() else "") }
        var method by remember { mutableStateOf("CASH") }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showPayment = false },
            title = { Text("Guest payment") },
            text = {
                Column {
                    OutlinedTextField(value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() } },
                        label = { Text("Amount") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("CASH", "MTN_MOMO", "AIRTEL_MONEY", "BANK").forEach { m ->
                            FilterChip(selected = method == m, onClick = { method = m },
                                label = { Text(m.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }, fontSize = 11.sp) })
                        }
                    }
                    Text("Balance due: ${HotelRepo.money(folio.balance)}", fontSize = 11.5.sp, color = DaTextMuted)
                    err?.let { Text(it, fontSize = 11.5.sp, color = DaRed) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        runCatching { repo.postPayment(bookingId, amount.toDoubleOrNull() ?: 0.0, method, "Front desk") }
                            .onSuccess { showPayment = false; refresh++ }
                            .onFailure { err = it.message ?: "Could not record" }
                    }
                }) { Text("Record") }
            },
            dismissButton = { TextButton(onClick = { showPayment = false }) { Text("Cancel") } }
        )
    }

    if (showExtend) {
        var newOut by remember {
            mutableStateOf(HotelRepo.addDays(booking?.checkOutDate ?: HotelRepo.today(), 1))
        }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showExtend = false },
            title = { Text("Extend the stay") },
            text = {
                Column {
                    OutlinedTextField(value = newOut, onValueChange = { newOut = it },
                        label = { Text("New check-out") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Currently until ${booking?.checkOutDate ?: ""}", fontSize = 11.5.sp, color = DaTextMuted)
                    err?.let { Text(it, fontSize = 11.5.sp, color = DaRed) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        runCatching { repo.extendStay(bookingId, newOut) }
                            .onSuccess { showExtend = false; refresh++ }
                            .onFailure { err = it.message ?: "That room is taken" }
                    }
                }) { Text("Extend") }
            },
            dismissButton = { TextButton(onClick = { showExtend = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun RoomsSetupDialog(
    repo: HotelRepo, rooms: List<HotelRoom>, types: List<RoomType>, onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(if (types.isEmpty()) 0 else 1) }
    var typeName by remember { mutableStateOf("") }
    var typeRate by remember { mutableStateOf("") }
    var typeBeds by remember { mutableStateOf("1 double") }
    var typeCapacity by remember { mutableStateOf("2") }
    var roomNumber by remember { mutableStateOf("") }
    var roomFloor by remember { mutableStateOf("1") }
    var roomTypeId by remember { mutableStateOf(types.firstOrNull()?.id ?: "") }
    var message by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Rooms & rates") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("Add room", fontSize = 11.sp) })
                    FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("Room type", fontSize = 11.sp) })
                }
                Spacer(Modifier.height(8.dp))

                if (mode == 1) {
                    if (types.isEmpty()) {
                        Text("Create a room type first — it sets the nightly rate.",
                            fontSize = 12.sp, color = DaAmber)
                    } else {
                        Text("Room type", fontSize = 11.sp, color = DaTextMuted)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            types.forEach { t ->
                                FilterChip(selected = roomTypeId == t.id, onClick = { roomTypeId = t.id },
                                    label = { Text(t.name, fontSize = 11.sp) })
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(value = roomNumber, onValueChange = { roomNumber = it },
                            label = { Text("Room number (101)") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(value = roomFloor, onValueChange = { roomFloor = it },
                            label = { Text("Floor / block") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Button(onClick = {
                            scope.launch {
                                runCatching {
                                    repo.saveRoom(null, roomNumber, roomTypeId, roomFloor,
                                        HotelRepo.VACANT_CLEAN, "")
                                }.onSuccess {
                                    message = "Room $roomNumber added"
                                    roomNumber = ""
                                    onDone()
                                }.onFailure { message = it.message ?: "Could not save" }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Add room") }
                    }
                } else {
                    OutlinedTextField(value = typeName, onValueChange = { typeName = it },
                        label = { Text("Name (Single, Double, Suite)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(value = typeRate, onValueChange = { typeRate = it.filter { c -> c.isDigit() } },
                        label = { Text("Rate per night") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = typeBeds, onValueChange = { typeBeds = it },
                            label = { Text("Beds") }, singleLine = true, modifier = Modifier.weight(1.5f))
                        OutlinedTextField(value = typeCapacity, onValueChange = { typeCapacity = it.filter { c -> c.isDigit() } },
                            label = { Text("Sleeps") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = {
                        scope.launch {
                            runCatching {
                                repo.saveRoomType(null, typeName, typeRate.toDoubleOrNull() ?: 0.0,
                                    typeCapacity.toIntOrNull() ?: 2, typeBeds, taxRate = 18.0)
                            }.onSuccess {
                                message = "Room type saved"
                                typeName = ""; typeRate = ""
                                mode = 1
                            }.onFailure { message = it.message ?: "Could not save" }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Save room type") }
                    Spacer(Modifier.height(6.dp))
                    Text("VAT of 18% is treated as part of the rate, not added on top.",
                        fontSize = 10.5.sp, color = DaTextMuted)
                }

                if (rooms.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("${rooms.size} rooms: " + rooms.joinToString(", ") { it.number },
                        fontSize = 11.sp, color = DaTextMuted)
                }
                message?.let { Text(it, fontSize = 11.5.sp, color = DaGreen) }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Done") } }
    )
}

@Composable
private fun GuestDialog(repo: HotelRepo, guest: Guest, onDone: () -> Unit) {
    var stays by remember { mutableStateOf<List<Booking>>(emptyList()) }
    LaunchedEffect(guest.id) { stays = repo.staysForGuest(guest.id) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(guest.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (guest.phone.isNotBlank()) Text("Phone: ${guest.phone}", fontSize = 12.sp, color = DaTextMuted)
                if (guest.idNumber.isNotBlank()) Text("ID: ${guest.idNumber}", fontSize = 12.sp, color = DaTextMuted)
                if (guest.nationality.isNotBlank()) Text("Nationality: ${guest.nationality}", fontSize = 12.sp, color = DaTextMuted)
                Spacer(Modifier.height(8.dp))
                Text("Stays (${stays.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                stays.forEach { b ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("${b.checkInDate} → ${b.checkOutDate}", fontSize = 11.5.sp, color = DaTextPrimary)
                            Text(HotelRepo.bookingLabel(b.status), fontSize = 10.5.sp, color = bookingColor(b.status))
                        }
                        Text(HotelRepo.money(b.invoiceTotal), fontSize = 11.5.sp, color = DaTextMuted)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Close") } }
    )
}

// ── Night audit: close the day on the phone too ──────────────────────────────

@Composable
private fun AuditTab(repo: HotelRepo, refresh: Int, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var summary by remember { mutableStateOf<HotelRepo.AuditSummary?>(null) }
    var history by remember { mutableStateOf<List<NightAudit>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }

    LaunchedEffect(refresh) {
        runCatching { repo.auditSummary() }.onSuccess { summary = it }
            .onFailure { message = it.message }
        runCatching { repo.audits().first() }.onSuccess { history = it }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        Text("Night audit", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = DaTextPrimary)
        Text(
            "Close the day once the last guest has been served. Rooms sold, money taken and " +
                "bookings that never arrived are written down for good.",
            fontSize = 12.sp, color = DaTextMuted, modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(12.dp))

        val s = summary
        if (s == null) {
            Text("Reading the day…", fontSize = 12.sp, color = DaTextMuted)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KpiChip("Business date", s.businessDate.takeLast(5), DaBlue, Modifier.weight(1f))
                KpiChip("Occupancy", HotelRepo.pct(s.occupancy / 100.0), DaGreen, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KpiChip("Charged", HotelRepo.money(s.revenue), DaTextPrimary, Modifier.weight(1f))
                KpiChip("Received", HotelRepo.money(s.payments), DaGreen, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = DaSurface)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Rooms sold: ${s.roomsSold} of ${s.roomsAvailable}", fontSize = 12.5.sp, color = DaTextPrimary)
                    Text("Booked, never arrived: ${s.noShows}", fontSize = 12.5.sp, color = DaTextPrimary)
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { confirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = DaBlue)
                    ) { Text("Close " + s.businessDate, fontSize = 13.sp) }
                }
            }
        }

        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontSize = 12.sp, color = DaGreen)
        }

        Spacer(Modifier.height(16.dp))
        Text("Closed days", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DaTextPrimary)
        if (history.isEmpty()) {
            Text("No day closed yet.", fontSize = 12.sp, color = DaTextMuted, modifier = Modifier.padding(top = 6.dp))
        }
        history.take(14).forEach { a ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(a.businessDate, fontSize = 12.5.sp, color = DaTextPrimary, modifier = Modifier.weight(1f))
                Text("${a.roomsSold}/${a.roomsAvailable} rooms", fontSize = 11.sp, color = DaTextMuted,
                    modifier = Modifier.weight(1f))
                Text(HotelRepo.money(a.revenue), fontSize = 11.5.sp, color = DaTextPrimary,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            }
        }
    }

    if (confirm) {
        val date = summary?.businessDate ?: HotelRepo.today()
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Close " + date + "?", fontSize = 15.sp) },
            text = {
                Text("Every booking still marked as due to arrive today will be marked as a no-show. " +
                    "The day's totals are written down and cannot be closed twice.", fontSize = 13.sp)
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    scope.launch {
                        runCatching { repo.closeNightAudit(date) }
                            .onSuccess { message = it.businessDate + " closed"; onChanged() }
                            .onFailure { message = it.message ?: "Could not close the day" }
                    }
                }) { Text("Close the day", fontWeight = FontWeight.Bold, color = DaBlue) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Not yet") } }
        )
    }
}
