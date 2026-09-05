package com.derycode.deryaccount.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PrintDocumentAdapter
import android.print.PageRange
import android.print.PrintDocumentInfo
import android.print.PrintManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PdfExport — generates real PDF documents, fully offline:
 *   receipts (auto-saved every sale), invoices, stock reports,
 *   cash book, trial balance, income statement, balance sheet.
 * Any PDF can be sent to the Android system print dialog
 * (Bluetooth printers, cloud print) or shared from the folder.
 */
object PdfExport {

    private const val PAGE_W = 595   // A4 @72dpi
    private const val PAGE_H = 842

    /** One text line to draw on the page. */
    data class Line(val text: String, val size: Float = 10f, val bold: Boolean = false,
                    val color: Int = Color.BLACK, val indent: Int = 0)

    fun documentsDir(context: Context): File =
        File(DeviceStore.baseDir(context), "documents").apply { mkdirs() }

    /** Render a list of lines into a paged A4 PDF file. */
    fun render(context: Context, fileName: String, title: String, lines: List<Line>): File {
        val doc = PdfDocument()
        val titlePaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textSize = 16f
        }
        val body = Paint().apply { textSize = 10f }
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        var y = 50
        var pageNo = 1
        page.canvas.drawText(title, 40f, y.toFloat(), titlePaint); y += 28
        val stamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date())
        page.canvas.drawText(stamp, 40f, y.toFloat(), body); y += 24

        lines.forEach { ln ->
            if (y > PAGE_H - 40) {
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
                y = 50
            }
            val p = Paint().apply {
                textSize = ln.size
                typeface = if (ln.bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                           else Typeface.DEFAULT
                color = ln.color
                isAntiAlias = true
            }
            page.canvas.drawText(ln.text, (40 + ln.indent).toFloat(), y.toFloat(), p)
            y += (ln.size + 6).toInt()
        }
        doc.finishPage(page)
        val f = File(documentsDir(context), fileName)
        FileOutputStream(f).use { doc.writeTo(it) }
        doc.close()
        return f
    }

    // -------- ready-made documents --------

    fun receiptPdf(context: Context, shopName: String, receiptNo: String,
                  items: List<Triple<String, Double, Double>>,
                  total: Double, paid: Double, change: Double, method: String,
                  header: com.derycode.deryaccount.util.EscPosPrinter.BizHeader? = null): File {
        val biz = header
        val lines = buildList {
            add(Line(biz?.name?.ifBlank { null } ?: shopName, 13f, true))
            biz?.tagline?.takeIf { it.isNotBlank() }?.let { add(Line(it, 9f, color = Color.GRAY)) }
            if (biz != null) {
                if (biz.location.isNotBlank()) add(Line(biz.location, 9f))
                if (biz.phone.isNotBlank()) add(Line("Tel: ${biz.phone}", 9f))
                if (biz.tin.isNotBlank()) add(Line("TIN: ${biz.tin}", 9f))
            }
            add(Line("RECEIPT No: $receiptNo", 10f, true))
            add(Line("-".repeat(60), 9f))
            items.forEach { (n, q, t) ->
                add(Line("%-28s x%-6.1f %,11.0f".format(n.take(28), q, t), 10f, indent = 4))
            }
            add(Line("-".repeat(60), 9f))
            add(Line("TOTAL:        UGX %,d".format(total.toLong()), 13f, true))
            add(Line("Paid ($method): UGX %,d".format(paid.toLong()), 10f))
            if (change > 0) add(Line("CHANGE:       UGX %,d".format(change.toLong()), 10f, true))
            add(Line(biz?.footer?.ifBlank { null } ?: "Thank you for your business!", 10f, color = Color.GRAY))
        }
        val safe = receiptNo.replace(Regex("[^A-Za-z0-9\\-]"), "_")
        return render(context, "receipt_$safe.pdf", "Receipt — $receiptNo", lines)
    }

    fun invoicePdf(context: Context, shopName: String, invoiceNo: String,
                   customer: String, items: List<Triple<String, Double, Double>>, // name, qty, lineTotal
                   total: Double, notes: String = "Payment due on delivery. Thank you."): File {
        val lines = buildList {
            add(Line("INVOICE: $invoiceNo", 13f, true))
            add(Line("Bill To: ${customer.ifBlank { "Customer" }}", 10f, true))
            add(Line("-".repeat(60), 9f))
            add(Line("%-30s %-8s %12s".format("ITEM", "QTY", "AMOUNT"), 10f, true))
            items.forEach { (n, q, t) ->
                add(Line("%-30s x%-8.1f %,12.0f".format(n.take(30), q, t), 10f, indent = 4))
            }
            add(Line("-".repeat(60), 9f))
            add(Line("TOTAL DUE: UGX %,d".format(total.toLong()), 13f, true))
            add(Line(notes, 9f, color = Color.GRAY))
        }
        return render(context, "invoice_${invoiceNo.replace(Regex("[^A-Za-z0-9\\-]"), "_")}.pdf",
            "Invoice — $invoiceNo", lines)
    }

    fun stockPdf(context: Context, shopName: String,
                 rows: List<Array<String>>): File { // name, unit, price, qty
        val lines = buildList {
            add(Line(shopName, 12f, true))
            add(Line("%-34s %-7s %11s %8s".format("ITEM", "UNIT", "PRICE", "STOCK"), 10f, true))
            add(Line("-".repeat(62), 9f))
            rows.forEach { r ->
                add(Line("%-34s %-7s %,11.0f %8.0f".format(r[0].take(34), r[1],
                    r[2].toDoubleOrNull() ?: 0.0, r[3].toDoubleOrNull() ?: 0.0), 10f, indent = 4))
            }
            add(Line("-".repeat(62), 9f))
            add(Line("Total items: ${rows.size}", 10f, true))
        }
        return render(context, "stock_${dateStamp()}.pdf", "Stock Report — $shopName", lines)
    }

    // -------- professional table rendering --------

    /** Table column: title, width in points, alignment (0 = left, 1 = right). */
    data class TCol(val title: String, val width: Float, val align: Int = 0)

    /** One row of a printed product table. */
    data class StockRow(val name: String, val unit: String, val cost: Double,
                        val price: Double, val qty: Double, val low: Boolean)

    private const val ALIGN_LEFT = 0
    private const val ALIGN_RIGHT = 1

    /**
     * Renders a ruled, shaded table — real columns with grid lines,
     * right-aligned figures, bold headers on a dark band, repeated on
     * every page, with footer totals.
     */
    fun tablePdf(context: Context, fileName: String, title: String, shopName: String,
                 cols: List<TCol>, rows: List<List<String>>, footer: List<String>,
                 redCells: Set<Pair<Int, Int>> = emptySet()): File {
        val doc = PdfDocument()
        val margin = 36f
        val tableW = cols.sumOf { it.width.toDouble() }.toFloat()
        val rowH = 16f
        val headerH = 18f
        val topY = 96f

        val titleP = textPaint(16f, bold = true)
        val subP = textPaint(9f, color = Color.GRAY)
        val headP = textPaint(9f, bold = true, color = Color.WHITE)
        val cellP = textPaint(9f)
        val cellBoldP = textPaint(9f, bold = true)
        val redP = textPaint(9f, bold = true, color = Color.rgb(180, 0, 0))
        val footP = textPaint(10f, bold = true)
        val gridP = Paint().apply { color = Color.rgb(180, 180, 180); strokeWidth = 0.6f }
        val bandP = Paint().apply { color = Color.rgb(44, 44, 44); style = Paint.Style.FILL }
        val shadeP = Paint().apply { color = Color.rgb(242, 242, 242); style = Paint.Style.FILL }

        val totalW = tableW
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        var c = page.canvas
        var y = 40f
        var pageNo = 1
        var rowIdx = 0

        fun drawHead() {
            c.drawText(shopName, margin, y, titleP); y += 14f
            c.drawText(title, margin, y, subP)
            c.drawText(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date()),
                PAGE_W - margin - 90f, y, subP)
            y = topY
            // header band
            c.drawRect(margin, y, margin + totalW, y + headerH, bandP)
            var x = margin
            cols.forEach { col ->
                val tx = if (col.align == ALIGN_RIGHT) x + col.width - 4f - headP.measureText(col.title) else x + 4f
                c.drawText(col.title, tx, y + headerH - 5f, headP)
                x += col.width
            }
            y += headerH
        }

        fun finishPage(number: Int) {
            c.drawText("Page $number", PAGE_W - margin - 40f, PAGE_H - 28f, subP)
            doc.finishPage(page)
        }

        drawHead()
        rows.forEachIndexed { i, r ->
            if (y + rowH > PAGE_H - 70) {
                finishPage(pageNo); pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
                c = page.canvas; y = 40f
                drawHead()
            }
            if (i % 2 == 1) c.drawRect(margin, y, margin + totalW, y + rowH, shadeP)
            var x = margin
            var truncated = r.mapIndexed { ci, v ->
                val maxW = cols[ci].width - 8f
                var s = v
                while (cellP.measureText(s) > maxW && s.length > 1) s = s.dropLast(1)
                s
            }
            r.forEachIndexed { ci, v ->
                val p = if (Pair(i, ci) in redCells) redP else cellP
                val s = truncated[ci]
                val tx = if (cols[ci].align == ALIGN_RIGHT) x + cols[ci].width - 4f - p.measureText(s) else x + 4f
                c.drawText(s, tx, y + rowH - 5f, p)
                x += cols[ci].width
            }
            y += rowH
            c.drawLine(margin, y, margin + totalW, y, gridP)
        }
        // column separators on this page's used area
        var vx = margin
        cols.forEach { col ->
            c.drawLine(vx, topY, vx, y, gridP)
            vx += col.width
        }
        c.drawLine(vx, topY, vx, y, gridP)
        // footer totals
        footer.forEach { line ->
            if (y + 14f > PAGE_H - 40) {
                finishPage(pageNo); pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
                c = page.canvas; y = 60f
            }
            c.drawText(line, margin, y + 12f, footP); y += 16f
        }
        finishPage(pageNo)

        val f = File(documentsDir(context), fileName)
        FileOutputStream(f).use { doc.writeTo(it) }
        doc.close()
        return f
    }

    private fun textPaint(size: Float, bold: Boolean = false, color: Int = Color.BLACK) =
        Paint().apply {
            textSize = size
            typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
            this.color = color
            isAntiAlias = true
        }

    private fun qtyShort(v: Double): String =
        if (v % 1.0 == 0.0) v.toLong().toString() else "%.1f".format(v)

    /** Stock report as a ruled table: item, unit, cost, price, stock, value — low stock in red. */
    fun stockTablePdf(context: Context, shopName: String, subtitle: String,
                      rows: List<StockRow>): File {
        val cols = listOf(
            TCol("#", 22f), TCol("ITEM", 176f), TCol("UNIT", 42f),
            TCol("COST UGX", 70f, ALIGN_RIGHT), TCol("PRICE UGX", 70f, ALIGN_RIGHT),
            TCol("STOCK", 50f, ALIGN_RIGHT), TCol("VALUE UGX", 73f, ALIGN_RIGHT))
        val cells = rows.mapIndexed { i, r ->
            listOf("${i + 1}", r.name, r.unit,
                "%,d".format(r.cost.toLong()), "%,d".format(r.price.toLong()),
                qtyShort(r.qty), "%,d".format((r.qty * r.cost).toLong()))
        }
        val redCells = rows.mapIndexed { i, r -> if (r.low) Pair(i, 5) else null }
            .filterNotNull().toSet()
        val totalCost = rows.sumOf { it.qty * it.cost }
        val totalRetail = rows.sumOf { it.qty * it.price }
        val units = rows.sumOf { it.qty }
        val footer = listOf(
            "Items: ${rows.size}    Units: ${qtyShort(units)}",
            "Stock value at cost: UGX %,d".format(totalCost.toLong()),
            "Stock value at retail: UGX %,d".format(totalRetail.toLong()))
        return tablePdf(context, "stock_${dateStamp()}.pdf",
            "Stock Report — $subtitle", shopName, cols, cells, footer, redCells)
    }

    /** Generic book-of-account table (cash book, trial balance, income statement...). */
    fun bookPdf(context: Context, title: String, shopName: String,
                header: List<String>, rows: List<List<String>>, footer: List<String>): File {
        val lines = buildList {
            add(Line(shopName, 12f, true))
            add(Line(header.joinToString("   "), 9f, true))
            add(Line("-".repeat(70), 8f))
            rows.forEach { r -> add(Line(r.joinToString("   "), 10f, indent = 4)) }
            add(Line("-".repeat(70), 8f))
            footer.forEach { add(Line(it, 10f, true)) }
        }
        return render(context, "${title.lowercase().replace(" ", "_")}_${dateStamp()}.pdf",
            "$title — $shopName", lines)
    }

    private fun dateStamp() = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())

    // -------- system print dialog --------

    /** Send any PDF file to Android's print dialog (works with Bluetooth/wifi printers). */
    fun printPdf(context: Context, file: File, jobName: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        printManager.print(jobName, FilePrintAdapter(file), a4Attributes())
    }

    private fun a4Attributes(): android.print.PrintAttributes =
        android.print.PrintAttributes.Builder()
            .setMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4)
            .setColorMode(android.print.PrintAttributes.COLOR_MODE_COLOR)
            .build()

    /** Adapter that streams a ready-made PDF to the print framework. */
    private class FilePrintAdapter(private val file: File) : PrintDocumentAdapter() {
        override fun onLayout(oldAttributes: android.print.PrintAttributes?,
                              newAttributes: android.print.PrintAttributes,
                              cancellationSignal: CancellationSignal?,
                              callback: LayoutResultCallback, extras: Bundle?) {
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled(); return
            }
            val info = PrintDocumentInfo.Builder(file.name)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(pages: Array<out PageRange>?,
                             destination: ParcelFileDescriptor,
                             cancellationSignal: CancellationSignal?,
                             callback: WriteResultCallback) {
            try {
                FileInputStream(file).use { input ->
                    FileOutputStream(destination.fileDescriptor).use { output ->
                        input.copyTo(output)
                    }
                }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            }
        }
    }
}
