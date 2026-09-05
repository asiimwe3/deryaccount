package com.derycode.deryaccount.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import java.io.File
import java.util.UUID

/**
 * ProductImages — offline product photos.
 * Photos are copied INTO the app's private storage (not the gallery), then
 * shown on POS tiles and stock rows straight from disk — no internet, ever.
 * Images are downscaled to ~512px and stored as JPEG so a full shop's
 * catalogue stays tiny even on entry-level tablets.
 */
object ProductImages {

    private const val DIR = "product_images"
    private const val MAX_DIM = 512

    // small in-memory cache so scrolling the POS grid stays smooth
    private val cache = object : LruCache<String, ImageBitmap>(24) {}

    /** Copy the picked gallery/camera image into app storage. Returns the stored path. */
    fun save(context: Context, source: Uri): String? {
        val bitmap = try {
            decodeScaled(context, source)
        } catch (_: Exception) { null } ?: return null
        return try {
            val dir = File(context.filesDir, DIR).apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.jpg")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it) }
            file.absolutePath
        } catch (_: Exception) { null }
    }

    /** Delete a stored photo (when a product is removed or its photo changed). */
    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        try { File(path).takeIf { it.exists() }?.delete() } catch (_: Exception) { }
    }

    /** Load a thumbnail for compose, cached in memory. Null when no photo/missing file. */
    fun load(path: String?): ImageBitmap? {
        if (path.isNullOrBlank()) return null
        cache.get(path)?.let { return it }
        val bitmap = try {
            BitmapFactory.decodeFile(path)?.let { scaleDown(it) }
        } catch (_: Exception) { null } ?: return null
        val img = bitmap.asImageBitmap()
        cache.put(path, img)
        return img
    }

    private fun decodeScaled(context: Context, uri: Uri): Bitmap? {
        // read bounds first, then sample close to the target — saves memory
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        var w = bounds.outWidth
        var h = bounds.outHeight
        while (maxOf(w, h) / sample / 2 >= MAX_DIM) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }

    private fun scaleDown(b: Bitmap): Bitmap {
        val maxDim = maxOf(b.width, b.height)
        if (maxDim <= MAX_DIM) return b
        val scale = MAX_DIM.toFloat() / maxDim
        return Bitmap.createScaledBitmap(
            b, (b.width * scale).toInt(), (b.height * scale).toInt(), true)
    }
}
