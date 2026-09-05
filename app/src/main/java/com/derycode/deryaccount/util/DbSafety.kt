package com.derycode.deryaccount.util

import android.content.Context
import android.os.Environment
import android.os.StatFs
import java.io.File

/**
 * DbSafety — turns silent crashes into clear messages.
 *
 * Typical real-world failure: the tablet's storage fills up (the app saves a
 * PDF receipt on every sale). Reads still work — the shop opens, the product
 * list shows — but the next WRITE (adding stock, making a sale) throws and the
 * app dies. This utility checks space up front and, when a save fails,
 * logs the exact error where it can be shared with support.
 */
object DbSafety {

    /** True when there's enough space for the database + receipts to keep working. */
    fun freeSpaceOk(context: Context): Boolean = try {
        val stat = StatFs(context.filesDir.absolutePath)
        stat.availableBlocksLong * stat.blockSizeLong > 20L * 1024 * 1024   // >20MB
    } catch (_: Exception) { true }

    /** Writes the full error to DeryAccount/crashes so it can be diagnosed. */
    fun log(context: Context, where: String, e: Throwable) {
        try {
            val dir = File(DeviceStore.baseDir(context), "crashes").apply { mkdirs() }
            val stamp = java.text.SimpleDateFormat(
                "yyyy-MM-dd_HHmmss", java.util.Locale.US).format(java.util.Date())
            File(dir, "error_${stamp}.txt").writeText(
                "DeryAccount save error in $where\n" +
                "Free disk: ${freeMb(context)} MB\n" +
                "StatFs usable: ${usableMb(context)} MB\n\n" +
                android.util.Log.getStackTraceString(e))
        } catch (_: Exception) { }
    }

    /** Real free disk space (MB) where the database lives. */
    private fun freeMb(context: Context): Long = try {
        android.os.StatFs(context.filesDir.absolutePath).availableBytes / 1_048_576
    } catch (_: Exception) { -1 }

    private fun usableMb(context: Context): Long = try {
        StatFs(context.filesDir.absolutePath).availableBytes / (1024 * 1024)
    } catch (_: Exception) { -1 }

    /** A plain-language message the shop owner can act on. */
    fun friendly(e: Throwable): String {
        val m = (e.message ?: "").lowercase()
        return when {
            "full" in m || e is android.database.sqlite.SQLiteFullException ->
                "Phone storage is FULL. Delete some videos or old files to free space, then try again."
            "disk i/o" in m || "diskio" in m.replace(" ", "") || "corrupt" in m || "malformed" in m ->
                "There is a problem with the saved records on this phone. " +
                "The exact details were saved in DeryAccount > crashes — " +
                "send that file to Derycode so we can recover your data safely."
            else ->
                "The save did not go through. Exact details were saved in " +
                "DeryAccount > crashes — send that file to Derycode and we will fix it fast."
        }
    }
}
