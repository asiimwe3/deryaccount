package com.derycode.deryaccount.desktop.data

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** PDF receipts and statements, written straight to disk — no printer driver needed. */
object Pdf {

    private const val W = 226f      // 80mm thermal receipt width in points
    private const val M = 12f

    fun receipt(repo: Repo, saleId: String, target: File): File {
        val sale = repo.sale(saleId) ?: error("Sale not found")
        val items = repo.saleItems(saleId)
        val p = repo.profile()
        val doc = PDDocument()
        val page = PDPage(PDRectangle(W, 720f))
        doc.addPage(page)
        val cs = PDPageContentStream(doc, page)
        val bold = PDType1Font.HELVETICA_BOLD
        val reg = PDType1Font.HELVETICA
        var y = 700f

        fun line(text: String, size: Float = 8f, font: PDType1Font = reg, indent: Float = M) {
            cs.beginText()
            cs.setFont(font, size)
            cs.newLineAtOffset(indent, y)
            cs.showText(safe(text, font, size, W - 2 * M))
            cs.endText()
            y -= size + 3.5f
        }

        fun center(text: String, size: Float = 8f, font: PDType1Font = reg) {
            val width = font.getStringWidth(safe(text, font, size, W - 2 * M)) / 1000 * size
            line(text, size, font, ((W - width) / 2).coerceAtLeast(M))
        }

        fun row(left: String, right: String, size: Float = 8f, font: PDType1Font = reg) {
            cs.beginText(); cs.setFont(font, size); cs.newLineAtOffset(M, y); cs.showText(safe(left, font, size, 120f)); cs.endText()
            val rw = font.getStringWidth(safe(right, font, size, 70f)) / 1000 * size
            cs.beginText(); cs.setFont(font, size); cs.newLineAtOffset(W - M - rw, y); cs.showText(safe(right, font, size, 70f)); cs.endText()
            y -= size + 3.5f
        }

        center(p.str("name").ifBlank { "DeryAccount" }, 11f, bold)
        if (p.str("address").isNotBlank()) center(p.str("address"), 7.5f)
        if (p.str("phone").isNotBlank()) center(p.str("phone"), 7.5f)
        if (p.str("tin").isNotBlank()) center("TIN: ${p.str("tin")}", 7.5f)
        y -= 2f
        center("SALES RECEIPT", 8.5f, bold)
        y -= 4f
        line("Receipt : ${sale.str("receipt_no")}", 8f)
        line("Date    : ${Fmt.dateTime(sale.str("sold_at"))}", 8f)
        line("Cashier : ${sale.str("cashier")}", 8f)
        line("Customer: ${sale.str("customer_name")}", 8f)
        y -= 3f
        line("-".repeat(40), 8f)
        items.forEach { it2 ->
            line(it2.str("name"), 8f)
            row("  ${Fmt.qty(it2.dbl("qty"))} x ${Fmt.money(it2.dbl("unit_price"))}", Fmt.money(it2.dbl("line_total")), 7.5f)
        }
        line("-".repeat(40), 8f)
        row("Subtotal", Fmt.money(sale.dbl("subtotal")), 8f)
        if (sale.dbl("discount") > 0) row("Discount", "-" + Fmt.money(sale.dbl("discount")), 8f)
        row("TOTAL", Fmt.money(sale.dbl("total")), 9.5f, bold)
        row("Paid (${methodName(sale.str("payment_method"))})", Fmt.money(sale.dbl("amount_paid")), 8f)
        if (sale.dbl("change_given") > 0) row("Change", Fmt.money(sale.dbl("change_given")), 8f)
        y -= 4f
        center("Goods sold are not returnable after 7 days", 7f)
        center("Thank you for your business", 7.5f, bold)
        center("Powered by DeryAccount", 7f)

        cs.close()
        if (target.parentFile != null) target.parentFile.mkdirs()
        doc.save(target)
        doc.close()
        return target
    }

    /** A general-purpose A4 statement: trial balance, income statement, stock list, Z-report. */
    fun statement(
        title: String,
        subtitle: String,
        business: String,
        rows: List<List<String>>,
        headers: List<String>,
        columnWidths: List<Float>,
        totals: List<String>? = null,
        target: File
    ): File {
        val doc = PDDocument()
        val page = PDPage(PDRectangle.A4)
        doc.addPage(page)
        val cs = PDPageContentStream(doc, page)
        val bold = PDType1Font.HELVETICA_BOLD
        val reg = PDType1Font.HELVETICA
        val left = 40f
        val right = PDRectangle.A4.width - 40f
        var y = PDRectangle.A4.height - 50f

        fun text(s: String, x: Float, size: Float, font: PDType1Font, alignRight: Boolean = false, max: Float = right - left) {
            val t = safe(s, font, size, max)
            val w = font.getStringWidth(t) / 1000 * size
            cs.beginText()
            cs.setFont(font, size)
            cs.newLineAtOffset(if (alignRight) x - w else x, y)
            cs.showText(t)
            cs.endText()
        }

        text(business, left, 14f, bold)
        y -= 18f
        text(title, left, 12f, bold)
        y -= 14f
        text(subtitle, left, 9f, reg)
        y -= 20f

        val totalWidth = columnWidths.sum()
        fun xs(): List<Float> {
            val out = mutableListOf<Float>()
            var acc = left
            columnWidths.forEach { w ->
                out.add(acc)
                acc += (right - left) * (w / totalWidth)
            }
            return out
        }

        val positions = xs()
        headers.forEachIndexed { i, h ->
            text(h, positions[i], 9f, bold, alignRight = i > 0)
        }
        y -= 6f
        cs.setLineWidth(0.6f)
        cs.moveTo(left, y); cs.lineTo(right, y); cs.stroke()
        y -= 12f

        rows.forEach { r ->
            if (y < 60f) return@forEach
            r.forEachIndexed { i, cell -> text(cell, positions[i], 8.5f, reg, alignRight = i > 0) }
            y -= 13f
        }
        totals?.let { t ->
            y -= 4f
            cs.moveTo(left, y + 8f); cs.lineTo(right, y + 8f); cs.stroke()
            t.forEachIndexed { i, cell -> text(cell, positions[i], 9.5f, bold, alignRight = i > 0) }
            y -= 14f
        }
        cs.beginText(); cs.setFont(reg, 7.5f); cs.newLineAtOffset(left, 30f)
        cs.showText("Generated ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.US))} · DeryAccount")
        cs.endText()

        cs.close()
        if (target.parentFile != null) target.parentFile.mkdirs()
        doc.save(target)
        doc.close()
        return target
    }

    // ── Hotel: guest receipt, tax invoice and licence receipt ────────────────

    /**
     * A guest receipt (80mm, for the thermal printer) or a tax invoice (A5,
     * for a company account). Same numbers as the folio on screen.
     */
    fun hotelFolio(hotel: Hotel, bookingId: String, invoice: Boolean, target: File): File {
        val b = hotel.booking(bookingId) ?: error("Booking not found")
        val charges = hotel.charges(bookingId).filter { it.dbl("amount") != 0.0 }
        val payments = hotel.payments(bookingId)
        val folio = hotel.folio(bookingId)
        val p = hotel.profileRow()

        val doc = PDDocument()
        val a5 = PDRectangle(420f, 595f)
        val page = PDPage(if (invoice) a5 else PDRectangle(W, 760f))
        doc.addPage(page)
        val cs = PDPageContentStream(doc, page)
        val bold = PDType1Font.HELVETICA_BOLD
        val reg = PDType1Font.HELVETICA
        val pageW = if (invoice) a5.width else W
        val m = if (invoice) 26f else M
        val inner = pageW - 2 * m
        var y = page.mediaBox.height - (if (invoice) 40f else 26f)

        fun line(text: String, size: Float = 8f, font: PDType1Font = reg, x: Float = m) {
            cs.beginText(); cs.setFont(font, size); cs.newLineAtOffset(x, y)
            cs.showText(safe(text, font, size, inner)); cs.endText()
            y -= size + (if (invoice) 5f else 3.5f)
        }
        fun center(text: String, size: Float = 8f, font: PDType1Font = reg) {
            val w = font.getStringWidth(safe(text, font, size, inner)) / 1000 * size
            line(text, size, font, ((pageW - w) / 2).coerceAtLeast(m))
        }
        fun row(left: String, right: String, size: Float = 8f, font: PDType1Font = reg) {
            cs.beginText(); cs.setFont(font, size); cs.newLineAtOffset(m, y)
            cs.showText(safe(left, font, size, inner - 80f)); cs.endText()
            val rs = safe(right, font, size, 80f)
            val rw = font.getStringWidth(rs) / 1000 * size
            cs.beginText(); cs.setFont(font, size); cs.newLineAtOffset(pageW - m - rw, y)
            cs.showText(rs); cs.endText()
            y -= size + (if (invoice) 5f else 3.5f)
        }
        fun gap(v: Float = 6f) { y -= v }
        fun rule() {
            cs.setLineWidth(0.5f); cs.moveTo(m, y + 3f); cs.lineTo(pageW - m, y + 3f); cs.stroke()
        }

        val hotelName = if (p.str("name").isBlank()) "Hotel" else p.str("name")
        center(hotelName, if (invoice) 15f else 11f, bold)
        if (p.str("address").isNotBlank()) center(p.str("address"), if (invoice) 8.5f else 7.5f)
        val contact = listOf(p.str("phone"), p.str("email")).filter { it.isNotBlank() }.joinToString(" - ")
        if (contact.isNotBlank()) center(contact, if (invoice) 8.5f else 7.5f)
        if (p.str("tin").isNotBlank()) center("TIN: " + p.str("tin"), if (invoice) 8.5f else 7.5f)
        gap(4f)
        center(if (invoice) "TAX INVOICE" else "GUEST RECEIPT", if (invoice) 11f else 9f, bold)
        gap(4f)
        rule()
        row(if (invoice) "Invoice no" else "Receipt no", "H-" + b.str("ref_no"))
        row("Date", Fmt.dateTime(if (payments.isNotEmpty()) payments.last().str("paid_at") else b.str("created_at")))
        row("Guest", b.str("guest_name"))
        if (b.str("guest_phone").isNotBlank()) row("Phone", b.str("guest_phone"))
        row("Room", b.str("room_number") + (if (b.str("type_name").isNotBlank()) " - " + b.str("type_name") else ""))
        row("Stay", Fmt.date(b.str("check_in_date")) + " to " + Fmt.date(b.str("check_out_date")) + " (" + b.int("nights") + " nights)")
        rule()
        gap(2f)

        val groups = charges.groupBy { it.str("kind") }
            .toList().sortedBy { Hotel.CHARGE_KINDS.indexOf(it.first) }
        groups.forEach { (kind, lines) ->
            line(Hotel.kindLabel(kind).uppercase(), if (invoice) 8f else 7.5f, bold)
            lines.forEach { c ->
                val qty = if (c.dbl("qty") != 1.0) "  (" + Fmt.qty(c.dbl("qty")) + " x " + Fmt.money(c.dbl("unit_price")) + ")" else ""
                row("  " + c.str("description").ifBlank { Hotel.kindLabel(kind) } + qty,
                    Fmt.money(c.dbl("amount")), if (invoice) 8.5f else 7.5f)
            }
        }
        rule()
        gap(2f)
        row("Total charges", Fmt.money(folio.gross), 8.5f)
        row("VAT included", Fmt.money(folio.tax), 8f)
        row("Paid", Fmt.money(folio.paid), 8f)
        row(if (folio.balance > 0.5) "BALANCE DUE" else "TOTAL PAID",
            Fmt.money(if (folio.balance > 0.5) folio.balance else folio.paid),
            if (invoice) 11f else 9.5f, bold)
        gap(6f)
        if (payments.isNotEmpty()) {
            line("PAYMENTS", if (invoice) 8f else 7.5f, bold)
            payments.forEach { pay ->
                row("  " + methodName(pay.str("method")) + " - " + Fmt.date(pay.str("paid_at")),
                    Fmt.money(pay.dbl("amount")), 7.5f)
            }
            gap(4f)
        }
        if (invoice) {
            line("Payment is due on presentation. Thank you for staying with us.", 8f)
        } else {
            center("Thank you for staying with us", 8f, bold)
        }
        gap(4f)
        center("Issued by " + hotelName + " - Powered by DeryAccount Hotel Edition", 6.5f)

        cs.close()
        target.parentFile?.mkdirs()
        doc.save(target)
        doc.close()
        return target
    }

    /** DeryCode's own receipt for a hotel paying the UGX 200,000 yearly subscription. */
    fun subscriptionReceipt(
        payer: String, amount: Long, method: String, days: Int, code: String,
        periodFrom: String, periodTo: String, target: File
    ): File {
        val doc = PDDocument()
        val a5 = PDRectangle(420f, 595f)
        val page = PDPage(a5)
        doc.addPage(page)
        val cs = PDPageContentStream(doc, page)
        val bold = PDType1Font.HELVETICA_BOLD
        val reg = PDType1Font.HELVETICA
        val m = 30f
        val inner = a5.width - 2 * m
        var y = a5.height - 46f
        fun line(text: String, size: Float = 9f, font: PDType1Font = reg, x: Float = m) {
            cs.beginText(); cs.setFont(font, size); cs.newLineAtOffset(x, y)
            cs.showText(safe(text, font, size, inner - (x - m))); cs.endText()
            y -= size + 6f
        }
        fun row(left: String, right: String, size: Float = 9f, font: PDType1Font = reg) {
            cs.beginText(); cs.setFont(font, size); cs.newLineAtOffset(m, y)
            cs.showText(safe(left, font, size, inner - 120f)); cs.endText()
            val rs = safe(right, font, size, 120f)
            val rw = font.getStringWidth(rs) / 1000 * size
            cs.beginText(); cs.setFont(font, size); cs.newLineAtOffset(a5.width - m - rw, y)
            cs.showText(rs); cs.endText()
            y -= size + 6f
        }
        fun rule() { cs.setLineWidth(0.6f); cs.moveTo(m, y + 4f); cs.lineTo(a5.width - m, y + 4f); cs.stroke(); y -= 10f }

        // DeryCode's own mark on its own receipt.
        brandLogo?.let { bytes ->
            runCatching {
                val img = PDImageXObject.createFromByteArray(doc, bytes, "derycode-logo")
                cs.drawImage(img, m, y - 46f, 46f, 46f)
            }
        }
        val hx = m + 54f
        line("DeryCode Technologies", 15f, bold, hx)
        line("Kyenjojo, Uganda - derycode.online", 8.5f, reg, hx)
        line("Support: 0762 306 675", 8.5f, reg, hx)
        y = (a5.height - 46f) - 54f
        line("SUBSCRIPTION PAYMENT RECEIPT", 11f, bold)
        rule()
        row("Receipt no", "DCS-" + code.takeLast(6).replace("-", ""))
        row("Date", Fmt.dateTime(Clock.nowIso()))
        row("Received from", payer)
        row("For", "DeryAccount Hotel Edition")
        row("Period", periodFrom + " to " + periodTo + " (" + days + " days)")
        row("Method", methodName(method))
        rule()
        row("AMOUNT PAID", "UGX " + Fmt.money(amount.toDouble()), 13f, bold)
        rule()
        row("Activation code", code)
        y -= 12f
        line("This receipt covers the DeryAccount Hotel Edition subscription for the period above.", 8f)
        line("The activation code licenses the Windows front desk and the Android app on the", 8f)
        line("same hotel's computers and phones, and works with no internet.", 8f)
        y -= 16f
        line("Thank you for choosing DeryCode.", 9.5f, bold)
        y -= 22f
        line("Authorised by: ____________________", 8.5f)
        line("DeryCode Technologies", 8f)

        cs.close()
        target.parentFile?.mkdirs()
        doc.save(target)
        doc.close()
        return target
    }

    /** The exact DeryCode logo, packaged under src/main/resources/brand. */
    private val brandLogo: ByteArray? by lazy {
        runCatching {
            object {}.javaClass.getResourceAsStream("/brand/derycode-logo.png")?.use { it.readBytes() }
        }.getOrNull()
    }

    private fun safe(s: String, font: PDType1Font, size: Float, maxWidth: Float): String {
        val clean = s.replace("\u2014", "-").replace("\u2013", "-").replace("\u00A0", " ")
            .filter { it.code in 32..126 }
        if (font.getStringWidth(clean) / 1000 * size <= maxWidth) return clean
        var out = clean
        while (out.length > 1 && font.getStringWidth("$out…") / 1000 * size > maxWidth) out = out.dropLast(1)
        return "$out.."
    }

    private fun methodName(m: String) = when (m) {
        "MTN_MOMO" -> "MTN MoMo"
        "AIRTEL_MONEY" -> "Airtel Money"
        "CREDIT" -> "Credit"
        "BANK" -> "Bank"
        else -> "Cash"
    }
}
