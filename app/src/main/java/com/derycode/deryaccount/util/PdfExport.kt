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
                  total: Double, paid: Double, change: Double, method: String): File {
        val lines = buildList {
            add(Line(shopName, 13f, true))
            add(Line("RECEIPT: $receiptNo", 10f, true))
            add(Line("-".repeat(60), 9f))
            items.forEach { (n, q, t) ->
                add(Line("%-28s x%-6.1f %,11.0f".format(n.take(28), q, t), 10f, indent = 4))
            }
            add(Line("-".repeat(60), 9f))
            add(Line("TOTAL:        UGX %,d".format(total.toLong()), 13f, true))
            add(Line("Paid ($method): UGX %,d".format(paid.toLong()), 10f))
            if (change > 0) add(Line("CHANGE:       UGX %,d".format(change.toLong()), 10f, true))
            add(Line("Thank you for your business!", 10f, color = Color.GRAY))
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
