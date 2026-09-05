package com.derycode.deryaccount.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DeviceStore — creates a visible "DeryAccount" folder on the device and
 * stores business info safely there:
 *
 *  Documents/DeryAccount (or Downloads/DeryAccount on Android 10+)
 *    ├── receipts/     one text file per sale (never lost, printable)
 *    ├── backups/      full daily JSON snapshot of all business data
 *    └── reports/      end-of-day Z-reports
 *
 * Works on any Android — no permission dance for the app's own files,
 * and the folder is visible in the device file manager.
 */
object DeviceStore {

    const val FOLDER = "DeryAccount"

    /** The main folder: public Documents on old Android, app-external (visible) otherwise. */
    fun baseDir(context: Context): File {
        val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Public Documents via MediaStore needs per-file writes; use the
            // app-visible external dir which every file manager can browse.
            File(context.getExternalFilesDir(null), FOLDER)
        } else {
            File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), FOLDER)
        }
        return dir.apply {
            File(this, "receipts").mkdirs()
            File(this, "backups").mkdirs()
            File(this, "reports").mkdirs()
        }
    }

    /** Save a sale receipt as a readable text file. Returns the file path. */
    fun saveReceipt(context: Context, receiptNo: String, content: String): File {
        val safe = receiptNo.replace(Regex("[^A-Za-z0-9\\-]"), "_")
        val f = File(File(baseDir(context), "receipts"), "$safe.txt")
        f.writeText(content)
        return f
    }

    /**
     * Backup that SURVIVES an uninstall: written into public Downloads
     * via MediaStore (Android 10+). Copies to public Documents on older
     * devices. Returns the visible location, or null on failure.
     */
    fun backupToDownloads(context: Context, dbPath: String): String? {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        val name = "deryaccount_backup_$stamp.db"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cv = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                }
                val uri = context.contentResolver.insert(
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: return null
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    File(dbPath).inputStream().use { it.copyTo(out) }
                }
                "Downloads/$name"
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), FOLDER).apply { mkdirs() }
                val dst = File(dir, name)
                File(dbPath).copyTo(dst, overwrite = true)
                "Downloads/${FOLDER}/$name"
            }
        } catch (e: Exception) { null }
    }

    /** Full daily backup of the database file — copy of deryaccount.db. */
    fun backupDatabase(context: Context, dbPath: String): File? {
        return try {
            val src = File(dbPath)
            if (!src.exists()) return null
            val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
            val dst = File(File(baseDir(context), "backups"), "deryaccount_$stamp.db")
            src.copyTo(dst, overwrite = true)
            dst
        } catch (e: Exception) { null }
    }

    /** Save the end-of-day Z-report text. */
    fun saveReport(context: Context, content: String): File {
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val f = File(File(baseDir(context), "reports"), "zreport_$stamp.txt")
        f.writeText(content)
        return f
    }

    /** On Android 10+, also publish a file to the public Downloads folder so
     *  it's visible in every file manager without USB digging. */
    fun publishToDownloads(context: Context, fileName: String, content: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/$FOLDER")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return false
                context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                true
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), FOLDER)
                dir.mkdirs()
                File(dir, fileName).writeText(content)
                true
            }
        } catch (e: Exception) { false }
    }

    fun buildReceiptText(
        shopName: String,
        receiptNo: String,
        lines: List<Triple<String, Double, Double>>, // name, qty, lineTotal
        total: Double, paid: Double, change: Double, method: String
    ): String {
        val sb = StringBuilder()
        sb.appendLine(shopName)
        sb.appendLine("Receipt: $receiptNo")
        sb.appendLine(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date()))
        sb.appendLine("--------------------------------")
        lines.forEach { (n, q, t) -> sb.appendLine("$n  x$q  ${"%,.0f".format(t)}") }
        sb.appendLine("--------------------------------")
        sb.appendLine("TOTAL: ${"%,.0f".format(total)} UGX")
        if (change > 0) sb.appendLine("Change: ${"%,.0f".format(change)} UGX")
        sb.appendLine("Paid by: ${method.replace("_", " ")}")
        sb.appendLine("Thank you! Karibu tena!")
        sb.appendLine("- DeryAccount -")
        return sb.toString()
    }
}
