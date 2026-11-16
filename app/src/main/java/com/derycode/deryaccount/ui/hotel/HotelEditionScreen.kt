package com.derycode.deryaccount.ui.hotel

/**
 * HotelEditionScreen — DeryAccount Hotel Edition on the phone.
 *
 * UGX 200,000 a year, paid by MTN or Airtel Money, activated with an offline
 * code. One code licenses the hotel's Windows front desk and its phones.
 * The same page shows the trial status, what the hotel gets, and every
 * subscription receipt, each printable or shareable to WhatsApp.
 */

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.billing.HotelLicence
import com.derycode.deryaccount.ui.theme.*
import com.derycode.deryaccount.util.PdfExport
import com.derycode.deryaccount.util.SessionManager
import com.derycode.deryaccount.util.Share
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun money(v: Long) = "UGX %,d".format(v)

private fun fmtDate(ms: Long) =
    SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(ms))

@Composable
fun HotelEditionScreen(
    session: SessionManager,
    onBack: () -> Unit = {},
    onOpenHotel: () -> Unit = {}
) {
    val context = LocalContext.current
    var reload by remember { mutableStateOf(0) }
    var code by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var messageOk by remember { mutableStateOf(true) }
    val state = remember(reload) { HotelLicence.state(context) }
    val receipts = remember(reload) { HotelLicence.payments(context) }
    var hotelName by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        hotelName = runCatching { session.businessProfileNow()?.name }.getOrNull().orEmpty()
    }

    LaunchedEffect(Unit) { HotelLicence.ensureTrialStarted(context) }

    fun shareSubscriptionReceipt(p: HotelLicence.Payment) {
        runCatching {
            val from = fmtDate(p.at)
            val to = fmtDate(p.at + p.days * 24L * 60 * 60 * 1000)
            val lines = listOf(
                PdfExport.Line("SUBSCRIPTION PAYMENT RECEIPT", 14f, bold = true),
                PdfExport.Line(""),
                PdfExport.Line("DeryCode Technologies · Kyenjojo, Uganda", 10f),
                PdfExport.Line("Support: ${HotelLicence.SUPPORT_PHONE} · derycode.online", 9f),
                PdfExport.Line(""),
                PdfExport.Line("Receipt no:  DCS-${p.code.takeLast(6)}"),
                PdfExport.Line("Date:        ${fmtDate(p.at)}"),
                PdfExport.Line("Received from: ${p.payer}"),
                PdfExport.Line("For:         DeryAccount Hotel Edition"),
                PdfExport.Line("Period:      $from to $to (${p.days} days)"),
                PdfExport.Line("Method:      ${p.method.replace("_", " ")}"),
                PdfExport.Line(""),
                PdfExport.Line("AMOUNT PAID: ${money(p.amount)}", 15f, bold = true, color = 0xFF16A34A.toInt()),
                PdfExport.Line(""),
                PdfExport.Line("Activation code: ${p.code}", 10f, bold = true),
                PdfExport.Line(""),
                PdfExport.Line("The code licenses the hotel's Windows front desk and its Android", 9f),
                PdfExport.Line("phones, and works with no internet.", 9f)
            )
            val file = PdfExport.render(
                context, "HotelEdition-${p.code.takeLast(6)}.pdf",
                "DeryCode Subscription Receipt", lines
            )
            Share.file(context, file, title = "Share hotel subscription receipt")
        }.onFailure { message = "Could not build the receipt: ${it.message}"; messageOk = false }
    }

    Column(Modifier.fillMaxSize().background(DaBlack)) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().background(DaHeader).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = DaTextPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text("Hotel Edition", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = DaTextPrimary)
                Text("Rooms, bookings, guests and folios", fontSize = 11f.sp, color = DaTextMuted)
            }
            if (state.open && state.status != HotelLicence.Status.LOCKED) {
                TextButton(onClick = onOpenHotel) {
                    Text("Open hotel", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DaBlue)
                }
            }
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {

            // ── Price card
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(DaSurface)
                    .border(1.dp, DaOutline, RoundedCornerShape(18.dp)).padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFFEAF0FF)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Hotel, null, tint = DaBlue, modifier = Modifier.size(21.dp)) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("DeryAccount Hotel Edition", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DaTextPrimary)
                        Text("One hotel, all modules, unlimited rooms", fontSize = 11.sp, color = DaTextMuted)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(money(HotelLicence.YEARLY_UGX), fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = DaTextPrimary)
                    Text("  per year", fontSize = 12.sp, color = DaTextMuted, modifier = Modifier.padding(bottom = 4.dp))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusChip(state.headline, when (state.status) {
                        HotelLicence.Status.ACTIVE -> DaGreen
                        HotelLicence.Status.GRACE -> DaAmber
                        HotelLicence.Status.TRIAL -> DaBlue
                        HotelLicence.Status.LOCKED -> DaRed
                    })
                    StatusChip("Works offline", DaGreen)
                }
                state.expiresAtMillis?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        when (state.status) {
                            HotelLicence.Status.ACTIVE -> "Active until ${fmtDate(it)}"
                            HotelLicence.Status.GRACE -> "Expired ${fmtDate(it)} · grace period, renew now"
                            else -> "Last period ended ${fmtDate(it)}"
                        },
                        fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
                        color = when (state.status) {
                            HotelLicence.Status.ACTIVE -> DaGreen
                            HotelLicence.Status.GRACE -> DaAmber
                            else -> DaRed
                        }
                    )
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onOpenHotel,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DaBlue)
                ) { Text("Open the hotel desk", fontWeight = FontWeight.Bold) }
            }

            Spacer(Modifier.height(12.dp))

            // ── How to pay
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(DaSurface)
                    .border(1.dp, DaOutline, RoundedCornerShape(18.dp)).padding(18.dp)
            ) {
                Text("Pay by mobile money", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DaTextPrimary)
                Spacer(Modifier.height(10.dp))
                listOf("MTN MoMo", "Airtel Money").forEach { net ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(10.dp)).background(DaSurface2)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(net, fontSize = 12.sp, color = DaTextMuted, modifier = Modifier.weight(1f))
                        Text(HotelLicence.SUPPORT_PHONE, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = DaTextPrimary)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Name: DeryCode Technologies · amount ${money(HotelLicence.YEARLY_UGX)}",
                    fontSize = 11.sp, color = DaTextMuted)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        val text = "Hello DeryCode, I have paid ${money(HotelLicence.YEARLY_UGX)} for DeryAccount " +
                            "Hotel Edition for ${hotelName.ifBlank { "my hotel" }}. Please send my activation code."
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                                "https://wa.me/${HotelLicence.SUPPORT_WHATSAPP}?text=" + Uri.encode(text))))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(11.dp)
                ) {
                    Icon(Icons.Default.Chat, null, tint = DaGreen, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Send my payment message on WhatsApp", fontSize = 12.sp, color = DaTextPrimary)
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Activation
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(DaSurface)
                    .border(1.dp, DaOutline, RoundedCornerShape(18.dp)).padding(18.dp)
            ) {
                Text("Activate", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DaTextPrimary)
                Text("Type the code DeryCode sent you. It works on this phone and the hotel computer.",
                    fontSize = 11.sp, color = DaTextMuted)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it; message = null },
                    label = { Text("Activation code") },
                    placeholder = { Text("DERY-HTxxxx-xxxx-xxxx") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(11.dp)
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        val res = HotelLicence.activate(context, code)
                        messageOk = res.ok
                        message = res.message
                        if (res.ok) {
                            HotelLicence.recordPayment(
                                context, HotelLicence.YEARLY_UGX, "MTN_MOMO", res.days,
                                code.trim(), hotelName.ifBlank { "Hotel" }
                            )
                            reload++
                        }
                    },
                    enabled = code.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DaGreen)
                ) { Text("Activate Hotel Edition", fontWeight = FontWeight.Bold) }
                message?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
                        color = if (messageOk) DaGreen else DaRed)
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── What is included
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(DaSurface)
                    .border(1.dp, DaOutline, RoundedCornerShape(18.dp)).padding(18.dp)
            ) {
                Text("What the hotel gets", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DaTextPrimary)
                Spacer(Modifier.height(10.dp))
                HotelLicence.FEATURES.forEach { f ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.CheckCircle, null, tint = DaGreen, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(f, fontSize = 12.sp, color = DaTextMuted)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Receipts
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(DaSurface)
                    .border(1.dp, DaOutline, RoundedCornerShape(18.dp)).padding(18.dp)
            ) {
                Text("Subscription receipts", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DaTextPrimary)
                Text("Tap a receipt to print it or send it on WhatsApp.", fontSize = 11.sp, color = DaTextMuted)
                Spacer(Modifier.height(10.dp))
                if (receipts.isEmpty()) {
                    Text("No subscription payment recorded on this phone yet.",
                        fontSize = 12.sp, color = DaTextMuted, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp))
                } else {
                    receipts.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp)).background(DaSurface2)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(money(p.amount), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DaTextPrimary)
                                Text("${fmtDate(p.at)} · ${p.days} days · ${p.method.replace("_", " ")}",
                                    fontSize = 10.5.sp, color = DaTextMuted)
                            }
                            TextButton(onClick = { shareSubscriptionReceipt(p) }) {
                                Text("Receipt", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = DaBlue)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("The shop, stock and books are never locked. Only the hotel screens pause when the licence lapses.",
                fontSize = 10.5.sp, color = DaTextMuted, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp))
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
    }
}
