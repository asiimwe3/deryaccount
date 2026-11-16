package com.derycode.deryaccount.ui.hotel

/**
 * HotelDocs — guest receipts and tax invoices on the phone.
 *
 * A hotel hands a guest a receipt when they pay, and a tax invoice when they
 * need one for their employer. Both are built offline from the folio itself:
 * the charges, what was already paid, the VAT inside the total and the balance.
 */

import android.content.Context
import com.derycode.deryaccount.hotel.HotelRepo
import com.derycode.deryaccount.data.local.entity.Booking
import com.derycode.deryaccount.data.local.entity.FolioCharge
import com.derycode.deryaccount.data.local.entity.HotelPayment
import com.derycode.deryaccount.util.PdfExport
import com.derycode.deryaccount.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object HotelDocs {

    private fun money(v: Double): String = "UGX %,d".format(Math.round(v))
    private fun stamp(): String =
        SimpleDateFormat("ddMMyyyy-HHmm", Locale.US).format(Date())

    private suspend fun headerLines(context: Context): List<PdfExport.Line> {
        val p = runCatching { withContext(Dispatchers.IO) { SessionManager(context).businessProfileNow() } }.getOrNull()
        return listOf(
            PdfExport.Line(p?.name?.ifBlank { null } ?: "Hotel", 15f, bold = true),
            PdfExport.Line(
                listOfNotNull(
                    p?.location?.takeIf { it.isNotBlank() },
                    p?.phone?.takeIf { it.isNotBlank() },
                    p?.tin?.takeIf { it.isNotBlank() }?.let { "TIN $it" }
                ).joinToString(" · ").ifBlank { "" },
                9f
            ),
            PdfExport.Line("")
        )
    }

    /** A receipt for money the guest has paid (80mm-style, prints A4 or A5 fine). */
    suspend fun guestReceipt(
        context: Context,
        booking: Booking,
        charges: List<FolioCharge>,
        payments: List<HotelPayment>,
        folio: HotelRepo.Folio
    ): File {
        val lines = buildList {
            addAll(headerLines(context))
            add(PdfExport.Line("GUEST RECEIPT", 13f, bold = true))
            add(PdfExport.Line("Receipt no: ${booking.refNo}", 10f))
            add(PdfExport.Line("Date: ${booking.checkOutDate}", 10f))
            add(PdfExport.Line(""))
            add(PdfExport.Line("Guest: ${booking.guestName}", 10f, bold = true))
            if (booking.guestPhone.isNotBlank()) add(PdfExport.Line("Phone: ${booking.guestPhone}", 9f))
            add(PdfExport.Line("Room stay: ${booking.checkInDate} to ${booking.checkOutDate} · ${booking.nights} night(s)", 9f))
            add(PdfExport.Line("Rate: ${money(booking.rate)} per night", 9f))
            add(PdfExport.Line(""))
            add(PdfExport.Line("CHARGES", 10f, bold = true))
            charges.forEach { c ->
                add(PdfExport.Line("${HotelRepo.kindLabel(c.kind)} · ${c.description}", 9f))
                add(PdfExport.Line("    ${c.chargeDate}                                          ${money(c.amount)}", 9f))
            }
            add(PdfExport.Line(""))
            add(PdfExport.Line("Total charges                                 ${money(folio.gross)}", 10f, bold = true))
            add(PdfExport.Line("VAT included                                  ${money(folio.tax)}", 9f))
            add(PdfExport.Line(""))
            add(PdfExport.Line("PAYMENTS", 10f, bold = true))
            payments.forEach { p ->
                add(PdfExport.Line("${p.paidAt.take(10)} · ${p.method.replace("_", " ")}".padEnd(48) + money(p.amount), 9f))
            }
            if (payments.isEmpty()) add(PdfExport.Line("No payment received yet.", 9f))
            add(PdfExport.Line(""))
            add(PdfExport.Line("Amount paid                                   ${money(folio.paid)}", 11f, bold = true))
            add(PdfExport.Line("Balance                                       ${money(folio.balance)}", 11f, bold = true))
            add(PdfExport.Line(""))
            add(PdfExport.Line("Thank you for staying with us.", 9f))
        }
        return PdfExport.render(context, "GuestReceipt-${booking.refNo}-${stamp()}.pdf", "Guest Receipt", lines)
    }

    /** A formal tax invoice, for guests who need one for their employer. */
    suspend fun taxInvoice(
        context: Context,
        booking: Booking,
        charges: List<FolioCharge>,
        payments: List<HotelPayment>,
        folio: HotelRepo.Folio
    ): File {
        val net = (folio.gross - folio.tax).coerceAtLeast(0.0)
        val lines = buildList {
            addAll(headerLines(context))
            add(PdfExport.Line("TAX INVOICE", 13f, bold = true))
            add(PdfExport.Line("Invoice no: INV-${booking.refNo}", 10f, bold = true))
            add(PdfExport.Line("Invoice date: ${booking.checkOutDate}", 10f))
            add(PdfExport.Line(""))
            add(PdfExport.Line("BILLED TO", 10f, bold = true))
            add(PdfExport.Line(booking.guestName, 10f))
            if (booking.guestPhone.isNotBlank()) add(PdfExport.Line(booking.guestPhone, 9f))
            add(PdfExport.Line(""))
            add(PdfExport.Line("PARTICULARS", 10f, bold = true))
            add(PdfExport.Line("Accommodation", 9f, bold = true))
            add(PdfExport.Line("  ${booking.nights} night(s) at ${money(booking.rate)} per night", 9f))
            val extras = charges.filter { !it.kind.uppercase().contains("ROOM") }
            if (extras.isNotEmpty()) {
                add(PdfExport.Line("Other services", 9f, bold = true))
                extras.forEach { c ->
                    add(PdfExport.Line("  ${HotelRepo.kindLabel(c.kind)} · ${c.description}".padEnd(48) + money(c.amount), 9f))
                }
            }
            add(PdfExport.Line(""))
            add(PdfExport.Line("Subtotal (net of VAT)                          ${money(net)}", 10f))
            add(PdfExport.Line("VAT                                            ${money(folio.tax)}", 10f))
            add(PdfExport.Line("TOTAL                                          ${money(folio.gross)}", 12f, bold = true))
            add(PdfExport.Line(""))
            add(PdfExport.Line("Paid                                           ${money(folio.paid)}", 10f))
            add(PdfExport.Line("Balance due                                    ${money(folio.balance)}", 10f, bold = true))
            add(PdfExport.Line(""))
            add(PdfExport.Line("Payment method: ${payments.joinToString(", ") { it.method.replace("_", " ") }.ifBlank { "-" }}", 9f))
            add(PdfExport.Line(""))
            add(PdfExport.Line("This is a computer-generated tax invoice.", 8f))
        }
        return PdfExport.render(context, "TaxInvoice-${booking.refNo}-${stamp()}.pdf", "Tax Invoice", lines)
    }
}
