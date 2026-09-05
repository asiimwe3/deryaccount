package com.derycode.deryaccount.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * AutoUpdate — checks GitHub for a newer DeryAccount and installs it in-app,
 * so shop owners never need to manually download APKs again.
 * Every step is wrapped: any failure silently gives up — the app never crashes
 * because of the updater.
 *
 * Release layout (stable "latest" URLs):
 *   .../releases/latest/download/version.json
 *   .../releases/latest/download/deryaccount.apk
 */
object AutoUpdate {

    private const val REPO_LATEST =
        "https://github.com/asiimwe3/deryaccount/releases/latest/download"

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val notes: String,
        val apkUrl: String
    )

    /** Check for a newer version. Returns null on ANY problem (offline, parse, timeout). */
    fun check(currentCode: Int): UpdateInfo? {
        return try {
        val conn = URL("$REPO_LATEST/version.json").openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 8000
        conn.instanceFollowRedirects = true
        if (conn.responseCode != 200) { conn.disconnect(); null }
        else {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val o = JSONObject(body)
            val remote = o.optInt("versionCode", 0)
            if (remote > currentCode) UpdateInfo(
                versionCode = remote,
                versionName = o.optString("versionName", "newer"),
                notes = o.optString("notes", "Bug fixes and improvements."),
                apkUrl = if (o.optString("apkUrl").isBlank())
                    "$REPO_LATEST/deryaccount.apk" else o.optString("apkUrl")
            ) else null
        }
    } catch (_: Exception) { null }
    }

    /**
     * Download the new APK (to app cache) and open the Android installer.
     * Returns true if the installer screen opened. Never throws.
     */
    fun downloadAndInstall(context: Context, info: UpdateInfo,
                           progress: (percent: Int) -> Unit = {}): Boolean {
        return try {
        val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 30000
        conn.instanceFollowRedirects = true
        if (conn.responseCode != 200) { conn.disconnect(); return false }
        val total = conn.contentLengthLong
        val file = File(context.cacheDir, "deryaccount_update.apk")
        val tmp = File(context.cacheDir, "deryaccount_update.part")
        conn.inputStream.use { input ->
            tmp.outputStream().use { out ->
                val buf = ByteArray(16384)
                var read: Int; var done = 0L
                while (input.read(buf).also { read = it } > 0) {
                    out.write(buf, 0, read); done += read
                    if (total > 0) progress((done * 100 / total).toInt())
                }
            }
        }
        conn.disconnect()
        if (tmp.length() < 1_000_000) return false   // sanity: a real APK is >1MB
        if (file.exists()) file.delete()
        tmp.renameTo(file)

        val uri: Uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
            true
        } catch (_: Exception) { false }
    }

    // ----------------------------------------------------------------
    // Crash upload: on next launch, quietly send saved crash logs to
    // support so bugs can be fixed without asking the shop owner to
    // copy files. Never throws; deletes a log only after it uploaded.
    // ----------------------------------------------------------------
    private const val CRASH_URL =
        "https://superagent-d41c313d.base44.app/functions/saveDeryAccountCrash"

    fun uploadCrashLogs(context: Context) {
        Thread {
            try {
                val dir = File(DeviceStore.baseDir(context), "crashes") ?: return@Thread
                if (!dir.isDirectory) return@Thread
                val version = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
                } catch (_: Exception) { "?" }
                val device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (API ${android.os.Build.VERSION.SDK_INT})"
                dir.listFiles { f -> f.name.startsWith("crash_") }?.forEach { f ->
                    try {
                        val report = f.readText()
                        val conn = URL(CRASH_URL).openConnection() as HttpURLConnection
                        conn.connectTimeout = 8000
                        conn.readTimeout = 15000
                        conn.requestMethod = "POST"
                        conn.setRequestProperty("Content-Type", "application/json")
                        conn.doOutput = true
                        val payload = org.json.JSONObject()
                            .put("report", "[$version] $report")
                            .put("device", device)
                            .put("appVersion", version)
                        conn.outputStream.use { it.write(payload.toString().toByteArray()) }
                        val ok = conn.responseCode == 200
                        conn.disconnect()
                        if (ok) f.delete()   // uploaded — remove so we never send twice
                    } catch (_: Exception) { }
                }
            } catch (_: Exception) { }
        }.start()
    }

    // ----------------------------------------------------------------
    // Crash safety net: log any unexpected crash to the DeryAccount
    // folder so it can be diagnosed, then let Android handle it.
    // ----------------------------------------------------------------
    fun installCrashLogger(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val dir = File(DeviceStore.baseDir(context), "crashes").apply { mkdirs() }
                val stamp = java.text.SimpleDateFormat(
                    "yyyy-MM-dd_HHmm", java.util.Locale.US).format(java.util.Date())
                File(dir, "crash_$stamp.txt").writeText(
                    "DeryAccount crash ${android.os.Build.VERSION.SDK_INT}\n" +
                    "Thread: ${thread.name}\n\n" +
                    android.util.Log.getStackTraceString(throwable))
            } catch (_: Exception) { }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
