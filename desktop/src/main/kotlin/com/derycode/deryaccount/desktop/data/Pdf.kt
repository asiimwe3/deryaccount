package com.derycode.deryaccount.desktop.data

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
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
