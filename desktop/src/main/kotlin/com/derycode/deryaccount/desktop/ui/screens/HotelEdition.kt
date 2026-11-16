package com.derycode.deryaccount.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.desktop.data.*
import com.derycode.deryaccount.desktop.ui.*

/** Where printed hotel receipts and invoices land. */
private fun hotelDocs(): java.io.File =
    java.io.File(System.getProperty("user.home"), "Documents/DeryAccount/receipts")

@Composable
private fun statusTint(s: HotelLicence.Status): Color = when (s) {
    HotelLicence.Status.ACTIVE -> Da.Green
    HotelLicence.Status.GRACE -> Da.Amber
    HotelLicence.Status.TRIAL -> Da.Brand
    HotelLicence.Status.LOCKED -> Da.Red
}

/**
 * The Hotel Edition page: the yearly subscription, what it includes, how to
 * pay, the activation box, and every subscription receipt DeryCode has issued
 * on this computer.
 */
@Composable
fun HotelEditionScreen(app: AppState, compact: Boolean = false) {
    val repo = app.repo
    var reload by remember { mutableStateOf(0) }
    var code by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var messageOk by remember { mutableStateOf(true) }
    var payer by remember { mutableStateOf(repo.businessName()) }
    var method by remember { mutableStateOf("MTN_MOMO") }
    val state = remember(reload, app.tick) { HotelLicence.state(repo) }
    val receipts = remember(reload, app.tick) { HotelLicence.payments(repo) }

    LaunchedEffect(Unit) { HotelLicence.ensureTrialStarted(repo) }

    fun printReceipt(p: HotelLicence.Payment) {
        runCatching {
            val from = if (p.at.length >= 10) p.at.take(10) else Clock.today()
            val to = Hotel.addDays(from, p.days.toLong())
            Pdf.subscriptionReceipt(
                payer = p.payer, amount = p.amount, method = p.method, days = p.days,
                code = p.code, periodFrom = from, periodTo = to,
                target = java.io.File(hotelDocs(), "HotelEdition-${p.id.take(8)}.pdf")
            )
        }.onSuccess { app.say("Receipt saved to Documents/DeryAccount/receipts") }
            .onFailure { app.say(it.message ?: "Could not write the receipt") }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        ScreenHeader(
            "Hotel Edition",
            "Rooms, bookings, guests and folios · UGX ${Fmt.money(HotelLicence.YEARLY_UGX.toDouble())} per year"
        ) {
            if (state.status == HotelLicence.Status.ACTIVE || state.status == HotelLicence.Status.GRACE) {
                Btn("Renew now", { code = "" }, Icons.Default.Autorenew, BtnKind.GHOST)
            }
        }

        // ── Price + status
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            Column(
                Modifier.weight(1.15f)
                    .shadow(2.dp, RoundedCornerShape(18.dp), ambientColor = Color(0x140F1B2D), spotColor = Color(0x140F1B2D))
                    .clip(RoundedCornerShape(18.dp))
                    .background(Da.Surface)
                    .border(1.dp, Da.Outline, RoundedCornerShape(18.dp))
                    .padding(22.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFEAF0FF)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Hotel, null, tint = Da.Brand, modifier = Modifier.size(22.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("DeryAccount Hotel Edition", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                        Text("The complete front office for a hotel, lodge or guest house", fontSize = 11.5.sp, color = Da.Muted)
                    }
                    Pill(state.headline, statusTint(state.status), filled = true)
                }
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("UGX " + Fmt.money(HotelLicence.YEARLY_UGX.toDouble()), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                    Text("  per year", fontSize = 12.sp, color = Da.Muted, modifier = Modifier.padding(bottom = 5.dp))
                }
                Text("Unlimited rooms and users, one hotel, all modules included.",
                    fontSize = 11.5.sp, color = Da.Muted, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Works offline", Da.Green)
                    Pill("Windows + Android", Da.Brand)
                    Pill("Free 30-day trial", Da.Purple)
                }
                Spacer(Modifier.height(16.dp))
                if (state.expiresOn != null) {
                    Text(
                        when (state.status) {
                            HotelLicence.Status.ACTIVE -> "Active until ${Fmt.date(state.expiresOn!!)} · ${state.daysLeft} days left"
                            HotelLicence.Status.GRACE -> "Expired on ${Fmt.date(state.expiresOn!!)} · ${-state.daysLeft} days into grace, renew to keep the hotel desk open"
                            else -> "Last period ended ${Fmt.date(state.expiresOn!!)}"
                        },
                        fontSize = 11.5.sp,
                        color = when (state.status) {
                            HotelLicence.Status.ACTIVE -> Da.Green
                            HotelLicence.Status.GRACE -> Da.Amber
                            else -> Da.Red
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // ── Pay + activate
            CardBox(Modifier.weight(1f)) {
                Text("Pay by mobile money", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                Spacer(Modifier.height(8.dp))
                Text("Send UGX ${Fmt.money(HotelLicence.YEARLY_UGX.toDouble())} to:",
                    fontSize = 11.5.sp, color = Da.Muted)
                Spacer(Modifier.height(8.dp))
                listOf("MTN MoMo" to "0762 306 675", "Airtel Money" to "0762 306 675").forEach { (net, number) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(9.dp)).background(Da.Surface2)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(net, fontSize = 11.5.sp, color = Da.Muted, modifier = Modifier.weight(1f))
                        Text(number, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Name: DeryCode Technologies. Send the payment message to ${HotelLicence.SUPPORT_PHONE} and you get an activation code back.",
                    fontSize = 10.5.sp, color = Da.Dim)
                Spacer(Modifier.height(14.dp))
                Text("Activate", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                Spacer(Modifier.height(6.dp))
                Field("Activation code", code, { code = it; message = null }, placeholder = "DERY-XXXX-XXXX-XXXX-XXXX")
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("Hotel / payer name", payer, { payer = it }, Modifier.weight(1f))
                    Drop("Paid with", listOf("MTN_MOMO", "AIRTEL_MONEY", "BANK", "CASH"), method, { method = it }, Modifier.weight(0.8f))
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("Activate Hotel Edition", {
                        val res = HotelLicence.activate(repo, code)
                        messageOk = res.ok
                        message = res.message
                        if (res.ok) {
                            HotelLicence.recordPayment(
                                repo, HotelLicence.priceFor(res.days), method, res.days,
                                code.trim().uppercase(), payer
                            )
                            app.say("Hotel Edition is active")
                            reload++
                            app.refresh()
                        }
                    }, Icons.Default.Key, BtnKind.PRIMARY, enabled = code.isNotBlank())
                    Btn("Clear trial state", {
                        HotelLicence.clear(repo); reload++; app.say("Licence cleared")
                    }, Icons.Default.RestartAlt, BtnKind.GHOST)
                }
                message?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, fontSize = 11.5.sp, color = if (messageOk) Da.Green else Da.Red, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Feature list
        CardBox(Modifier.fillMaxWidth()) {
            Text("Everything a hotel needs", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Da.Text)
            Text("One system for the front desk, the restaurant and the books.", fontSize = 11.5.sp, color = Da.Muted,
                modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.height(14.dp))
            HotelLicence.FEATURES.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { f ->
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.CheckCircle, null, tint = Da.Green, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(7.dp))
                            Text(f, fontSize = 11.5.sp, color = Da.Muted)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── What happens when it lapses
        CardBox(Modifier.fillMaxWidth(), background = Color(0xFFF8FAFF)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Info, null, tint = Da.Brand, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("The shop and the books are never locked", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                    Text("If the hotel subscription lapses you keep selling, keep the stock and keep every report and receipt already issued. " +
                        "Only the hotel screens (front desk, rooms, bookings, guests) pause until you renew. After the free trial ends there are " +
                        "7 days of grace so no guest is ever stranded at check-out.",
                        fontSize = 11.5.sp, color = Da.Muted, lineHeight = 17.sp, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Subscription receipts
        CardBox(Modifier.fillMaxWidth(), padding = 0.dp) {
            Column(Modifier.padding(18.dp)) {
                Text("Subscription receipts", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                Text("Every payment made on this computer. Reprint any receipt.", fontSize = 11.sp, color = Da.Dim)
            }
            if (receipts.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 26.dp), contentAlignment = Alignment.Center) {
                    Text("No subscription payment recorded yet", fontSize = 12.sp, color = Da.Dim)
                }
            } else {
                Table(
                    listOf("Date", "Paid with", "Period", "Amount", "Code", ""),
                    listOf(1.2f, 0.9f, 0.8f, 1f, 1.4f, 0.9f),
                    receipts.map { p ->
                        listOf(
                            Cell(Fmt.dateTime(p.at), Da.Muted),
                            Cell(p.method.replace("_", " "), Da.Text),
                            Cell("${p.days} days", Da.Muted),
                            Cell("UGX " + Fmt.money(p.amount.toDouble()), Da.Text, bold = true),
                            Cell(p.code, Da.Dim),
                            Cell("Print", Da.Brand, bold = true, align = TextAlign.End)
                        )
                    },
                    scrollable = false, maxRows = 30,
                    onRowClick = { i -> printReceipt(receipts[i]) }
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Questions or renewals: WhatsApp ${HotelLicence.SUPPORT_PHONE} · support@steadycompanyltd.com · derycode.online",
            fontSize = 10.5.sp, color = Da.Dim
        )
    }
}

/**
 * Wraps a hotel screen: shows it when the licence is open (trial, active or in
 * grace) and shows the subscription page when it is not.
 */
@Composable
fun HotelGate(app: AppState, content: @Composable () -> Unit) {
    val state = remember(app.tick) { HotelLicence.state(app.repo) }
    LaunchedEffect(Unit) { HotelLicence.ensureTrialStarted(app.repo) }
    if (state.open) content() else HotelEditionScreen(app)
}

/** Every guest payment taken, with one-click reprint of a receipt or a tax invoice. */
@Composable
fun HotelReceiptsScreen(app: AppState) {
    val hotel = app.hotel
    var search by remember { mutableStateOf("") }
    var reload by remember { mutableStateOf(0) }
    val rows = remember(search, reload, app.tick) { hotel.allPayments(600) }
    val filtered = remember(rows, search) {
        val q = search.trim().lowercase()
        if (q.isBlank()) rows else rows.filter {
            it.str("guest_name").lowercase().contains(q) ||
                it.str("ref_no").lowercase().contains(q) ||
                it.str("room_number").lowercase().contains(q) ||
                it.str("method").lowercase().contains(q)
        }
    }
    val todayTotal = filtered.sumOf { it.dbl("amount") }

    fun print(bookingId: String, invoice: Boolean) {
        runCatching {
            val b = hotel.booking(bookingId) ?: error("Booking not found")
            val name = "H-" + b.str("ref_no") + (if (invoice) "-invoice" else "-receipt")
            Pdf.hotelFolio(hotel, bookingId, invoice, java.io.File(hotelDocs(), "$name.pdf"))
        }.onSuccess { app.say(if (invoice) "Tax invoice saved to Documents/DeryAccount/receipts" else "Receipt saved to Documents/DeryAccount/receipts") }
            .onFailure { app.say(it.message ?: "Could not write the PDF") }
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        ScreenHeader("Receipts & invoices", "${filtered.size} guest payments · UGX ${Fmt.money(todayTotal)}") {
            Btn("Refresh", { reload++ }, Icons.Default.Refresh, BtnKind.GHOST)
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SearchBar(search, { search = it }, "Guest, booking ref, room or method", Modifier.weight(1f))
        }
        CardBox(Modifier.fillMaxWidth(), padding = 0.dp) {
            if (filtered.isEmpty()) {
                EmptyState("No guest payments recorded yet. Take a payment on a folio and it appears here.")
            } else {
                Table(
                    listOf("Receipt", "Guest", "Room", "Method", "Date", "Amount", "Print"),
                    listOf(1f, 1.5f, 0.6f, 0.9f, 1.1f, 1f, 1.5f),
                    filtered.map { p ->
                        listOf(
                            Cell("H-" + p.str("ref_no"), Da.Dim),
                            Cell(p.str("guest_name"), Da.Text, bold = true),
                            Cell(p.str("room_number"), Da.Muted),
                            Cell(p.str("method").replace("_", " "), Da.Muted),
                            Cell(Fmt.dateTime(p.str("paid_at")), Da.Dim),
                            Cell(Fmt.money(p.dbl("amount")), Da.Green, bold = true, align = TextAlign.End),
                            Cell("Receipt  ·  Tax invoice", Da.Brand, bold = true, align = TextAlign.End)
                        )
                    },
                    scrollable = false, maxRows = 200,
                    onRowClick = { i ->
                        val p = filtered[i]
                        print(p.str("booking_id"), false)
                    }
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("Print the last receipt", {
                filtered.firstOrNull()?.let { print(it.str("booking_id"), false) }
            }, Icons.Default.Receipt, BtnKind.GHOST, enabled = filtered.isNotEmpty())
            Btn("Print the last tax invoice", {
                filtered.firstOrNull()?.let { print(it.str("booking_id"), true) }
            }, Icons.Default.Description, BtnKind.GHOST, enabled = filtered.isNotEmpty())
        }
        Spacer(Modifier.height(8.dp))
        Text("Click a row to print its guest receipt. PDFs are saved to Documents/DeryAccount/receipts.",
            fontSize = 10.5.sp, color = Da.Dim)
    }
}
