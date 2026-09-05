package com.derycode.deryaccount.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.runBlocking

/**
 * LogoStore — the shop logo, printed on receipts, invoices and all PDF reports.
 * The image is stored offline in app storage (via ProductImages) and the
 * current path is kept in the business profile.
 */
object LogoStore {

    @Volatile private var cached: Pair<String, Bitmap>? = null

    /** Store the picked gallery/camera image as the shop logo. Returns the path. */
    fun save(context: Context, path: String?): String? = path

    /** The current logo bitmap, or null when the shop has none (or it fails to load). */
    fun bitmap(context: Context): Bitmap? {
        return try {
            val path = runBlocking {
                SessionManager(context).businessProfileNow()?.logoPath
            } ?: return null
            if (path.isBlank()) return null
            cached?.let { if (it.first == path) return it.second }
            val b = BitmapFactory.decodeFile(path) ?: return null
            cached = path to b
            b
        } catch (_: Exception) { null }
    }

    /** Load a logo directly from a path (no profile read). */
    fun bitmapFrom(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        cached?.let { if (it.first == path) return it.second }
        return try {
            val b = BitmapFactory.decodeFile(path) ?: return null
            cached = path to b
            b
        } catch (_: Exception) { null }
    }
}
