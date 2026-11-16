package com.derycode.deryaccount

import android.content.Context
import android.os.Build
import java.io.PrintWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Last-resort crash capture.
 *
 * If the app ever dies on a phone we cannot hold in our hands, the stack trace
 * is posted to our own endpoint before the system kills the process, so the
 * next bug can be fixed from the real error instead of a guess.
 */
object CrashReporter {

    private const val ENDPOINT = "https://superagent-d41c313d.base44.app/functions/saveSrsCrash"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                val report = sw.toString().take(20000)
                post(payload(report, app))
                Thread.sleep(900)
            } catch (_: Throwable) {
                // Never let the reporter turn one crash into two.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun payload(report: String, context: Context): String {
        val versionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
        val versionCode = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION") info.versionCode.toString()
        }.getOrNull() ?: "?"
        return buildString {
            append("{")
            append("\"source\":\"deryaccount\",")
            append("\"appVersion\":\"").append(esc(versionName)).append("\",")
            append("\"appVersionCode\":\"").append(esc(versionCode)).append("\",")
            append("\"phoneModel\":\"").append(esc("${Build.MANUFACTURER} ${Build.MODEL}")).append("\",")
            append("\"androidVersion\":\"").append(esc("Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")).append("\",")
            append("\"report\":\"").append(esc(report)).append("\"")
            append("}")
        }
    }

    private fun esc(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    private fun post(body: String) {
        Thread {
            runCatching {
                val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                conn.responseCode
                conn.disconnect()
            }
        }.start()
    }
}
