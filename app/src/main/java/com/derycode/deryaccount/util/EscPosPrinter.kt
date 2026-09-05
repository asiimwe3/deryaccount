package com.derycode.deryaccount.util

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.annotation.RequiresPermission
import com.derycode.deryaccount.data.local.entity.Sale
import com.derycode.deryaccount.data.local.entity.SaleItem
import com.derycode.deryaccount.data.local.entity.Branch
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * EscPosPrinter — builds 58mm/80mm ESC/POS receipt bytes and sends them
 * to a paired Bluetooth thermal printer over an SPP socket.
 * Works fully offline. Falls back silently if no printer is paired
 * (caller can then print via Android print framework / PDF).
 */
class EscPosPrinter(private val context: Context) {

    /** 58mm printers (most common cheap UG market ones): 32 chars/line. */
    private val width = 32

/** The shop's identity as printed at the top of every receipt. */
    data class BizHeader(
        val name: String,
        val tagline: String = "",
        val phone: String = "",
        val location: String = "",
        val tin: String = "",
        val footer: String = "Thank you! Karibu tena!",
        /** App-storage path of the shop logo — raster-printed at the top of the receipt. */
        val logoPath: String = ""
    ) {
        companion object {
            fun fromProfile(p: com.derycode.deryaccount.util.SessionManager.BusinessProfile) =
                BizHeader(p.name, p.tagline, p.phone, p.location, p.tin,
                    p.footer.ifBlank { "Thank you! Karibu tena!" }, p.logoPath)
        }
    }

    fun buildReceiptBytes(header: BizHeader, sale: Sale, items: List<SaleItem>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0x1B); out.write(0x40)            // init printer
        out.write(0x1B); out.write(0x61); out.write(1) // center

        // ---- Shop logo as a centered ESC/POS raster (GS v 0) ----
        try {
            val lg = com.derycode.deryaccount.util.LogoStore.bitmapFrom(header.logoPath)
            if (lg != null) out.write(rasterBytes(lg, dotsPerLine = 384))
        } catch (_: Exception) { /* logo problems never block the receipt */ }

        out.write(text("${header.name.ifBlank { "MY SHOP" }}\n", true, 1))
        if (header.tagline.isNotBlank()) out.write(text("${header.tagline}\n"))
        if (header.location.isNotBlank()) out.write(text("${header.location}\n"))
        if (header.phone.isNotBlank()) out.write(text("Tel: ${header.phone}\n"))
        if (header.tin.isNotBlank()) out.write(text("TIN: ${header.tin}\n"))
        out.write(text("------------------------------\n"))
        out.write(text("RECEIPT: ${sale.receiptNo}\n", true))
        val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
        out.write(text("Date: ${fmt.format(java.util.Date())}\n"))
        out.write(text("------------------------------\n"))

        out.write(0x1B); out.write(0x61); out.write(0) // left
        items.forEach { it ->
            out.write(text(it.name))
            val qtyStr = "x${fmtQty(it.qty)}"
            out.write(line("${fmtMoney(it.unitPrice)}$qtyStr", fmtMoney(it.lineTotal)))
        }
        out.write(text("------------------------------\n"))
        out.write(line("Subtotal:", fmtMoney(sale.subtotal)))
        if (sale.discount > 0) out.write(line("Discount:", "-${fmtMoney(sale.discount)}"))
        out.write(line("TOTAL:", fmtMoney(sale.total)))
        out.write(line("Paid (${sale.paymentMethod}):", fmtMoney(sale.amountPaid)))
        if (sale.changeGiven > 0) out.write(line("Change:", fmtMoney(sale.changeGiven)))

        out.write(text("\n------------------------------\n"))
        out.write(text("${header.footer}\n"))
        out.write(0x1B); out.write(0x61); out.write(1)
        out.write(text("Powered by DeryAccount\n"))
        out.write(text("\n\n\n"))
        out.write(0x1D); out.write(0x56); out.write(0x00) // cut paper
        return out.toByteArray()
    }

    /** Build a simple canvas bitmap of the receipt for the Android print framework. */
    fun buildReceiptBitmap(header: BizHeader, sale: Sale, items: List<SaleItem>): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 26f }
        val lines = buildList {
            add(header.name.ifBlank { "SHOP" })
            if (header.phone.isNotBlank()) add("Tel: ${header.phone}")
            add("Receipt: ${sale.receiptNo}")
            add("--------------------------------")
            items.forEach { add("${it.name.take(20)} x${fmtQty(it.qty)}  ${fmtMoney(it.lineTotal)}") }
            add("--------------------------------")
            add("TOTAL: ${fmtMoney(sale.total)}")
            add("Thank you!")
        }
        var w = 0
        lines.forEach { w = maxOf(w, paint.measureText(it).toInt()) }
        var logoBm: Bitmap? = null
        try { logoBm = com.derycode.deryaccount.util.LogoStore.bitmapFrom(header.logoPath) } catch (_: Exception) {}
        val logoH = if (logoBm != null) 100 else 0
        val bitmap = Bitmap.createBitmap(maxOf(w + 40, logoBm?.let { it.width } ?: 0),
            lines.size * 38 + 40 + logoH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawRGB(255, 255, 255)
        paint.color = 0xFF000000.toInt()
        var y = 30f
        logoBm?.let { lg ->
            try {
                val scale = minOf(100f / lg.height, (bitmap.width - 40f) / lg.width.coerceAtLeast(1))
                val lw = (lg.width * scale).toInt().coerceAtLeast(1)
                val lh = (lg.height * scale).toInt().coerceAtLeast(1)
                canvas.drawBitmap(Bitmap.createScaledBitmap(lg, lw, lh, true),
                    (bitmap.width - lw) / 2f, 10f, null)
                y += lh + 10f
            } catch (_: Exception) { }
        }
        lines.forEach { canvas.drawText(it, 20f, y, paint); y += 38f }
        return bitmap
    }

    /** Send to a paired printer whose name contains "print" (case-insensitive). */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun printBluetooth(header: BizHeader, sale: Sale, items: List<SaleItem>): Boolean {
        return try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter: BluetoothAdapter = bm.adapter ?: return false
            val device = adapter.bondedDevices.firstOrNull {
                it.name?.contains("print", ignoreCase = true) == true
            } ?: adapter.bondedDevices.firstOrNull() ?: return false

            val socket = device.createRfcommSocketToServiceRecord(
                java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
            adapter.cancelDiscovery()
            socket.connect()
            socket.outputStream.use { os ->
                os.write(buildReceiptBytes(header, sale, items))
                os.flush()
            }
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * ESC/POS raster image (GS v 0): grayscale → 1-bit threshold, MSB-first,
     * centered on the printable line (384 dots on 58mm, 576 on 80mm printers).
     */
    private fun rasterBytes(src: Bitmap, dotsPerLine: Int): ByteArray {
        val targetW = minOf(256, dotsPerLine)
        val scale = minOf(targetW.toFloat() / src.width, 96f / src.height.coerceAtLeast(1))
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createScaledBitmap(src, w, h, true)
        val bytesW = (w + 7) / 8                    // bytes per raster row
        val xOff = (dotsPerLine / 8 - bytesW) / 2   // whole-byte centering
        val row = ByteArray(dotsPerLine / 8)
        val data = ByteArrayOutputStream()
        // GS v 0 — xL xH = raster width in BYTES (full line, zero-padded),
        // yL yH = height in dots. Centering comes from the xOff padding.
        val bytesPerLine = dotsPerLine / 8
        data.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00,
            (bytesPerLine and 0xFF).toByte(), ((bytesPerLine shr 8) and 0xFF).toByte(),
            (h and 0xFF).toByte(), ((h shr 8) and 0xFF).toByte()))
        for (yy in 0 until h) {
            java.util.Arrays.fill(row, 0.toByte())
            for (xx in 0 until w) {
                val p = bmp.getPixel(xx, yy)
                val lum = (android.graphics.Color.red(p) * 0.30f +
                           android.graphics.Color.green(p) * 0.59f +
                           android.graphics.Color.blue(p) * 0.11f)
                if (lum < 160f) row[xOff + xx / 8] =
                    (row[xOff + xx / 8].toInt() or (0x80 shr (xx % 8))).toByte()
            }
            data.write(row)
        }
        data.write(0x0A)                            // feed one line after the image
        return data.toByteArray()
    }

    // ---- text helpers ----
    private fun text(s: String, bold: Boolean = false, size: Int = 0): ByteArray {
        val bos = ByteArrayOutputStream()
        if (bold) { bos.write(0x1B); bos.write(0x45); bos.write(1) }
        if (size > 0) { bos.write(0x1D); bos.write(0x21); bos.write(size) }
        bos.write(s.toByteArray(Charsets.US_ASCII))
        if (size > 0) { bos.write(0x1D); bos.write(0x21); bos.write(0) }
        if (bold) { bos.write(0x1B); bos.write(0x45); bos.write(0) }
        return bos.toByteArray()
    }

    private fun line(left: String, right: String): ByteArray {
        val space = width - left.length - right.length
        return if (space > 0) text(left + " ".repeat(space) + right + "\n")
        else text("$left\n$right\n")
    }

    private fun fmtMoney(v: Double): String = "UGX %,d".format(v.toLong())
    private fun fmtQty(q: Double): String = if (q % 1.0 == 0.0) q.toLong().toString() else "%.1f".format(q)
}
