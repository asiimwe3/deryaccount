package com.derycode.deryaccount.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Share.kt — share receipts, invoices and backups to WhatsApp, Drive,
 *  TeraBox, Gmail or any installed app. 100% offline: only local files
 *  are attached, nothing is uploaded anywhere by DeryAccount itself. */
object Share {

    private fun uri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Open the Android share sheet — the user picks WhatsApp, Drive, TeraBox, Gmail… */
    fun file(context: Context, file: File, title: String = "Share DeryAccount document",
             mime: String = "application/pdf") {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri(context, file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(i, title))
    }

    /** Share directly to WhatsApp (falls back to the chooser if not installed). */
    fun toWhatsApp(context: Context, file: File, mime: String = "application/pdf",
                  caption: String = "") {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri(context, file))
            putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage("com.whatsapp")
        }
        try { context.startActivity(i) }
        catch (_: Exception) { file(context, file, mime = mime) }  // no WhatsApp → chooser
    }

    /** Attach the file to an email draft (Gmail / any mail app). */
    fun byEmail(context: Context, file: File, subject: String,
                body: String = "", mime: String = "application/pdf") {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_EMAIL, arrayOf("info@derycode.com"))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            putExtra(Intent.EXTRA_STREAM, uri(context, file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try { context.startActivity(Intent.createChooser(i, "Send backup by email")) }
        catch (_: Exception) { file(context, file, mime = mime) }
    }

    /** Open a WhatsApp chat with Derycode support (feedback). */
    fun feedbackWhatsApp(context: Context) {
        val n = "256762306675" // +256 762 306 675 — Derycode support
        val url = "https://wa.me/$n?text=" +
            Uri.encode("Hello Derycode, here is my feedback on DeryAccount: ")
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.whatsapp"))
        } catch (_: Exception) {
            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            catch (_: Exception) {}
        }
    }
    /** Share the app with other shop owners — text message via WhatsApp or any app. */
    fun shareApp(context: Context) {
        val text = "I run my shop with DeryAccount — offline POS + books of account, made for Ugandan shops. " +
            "Get it free: https://github.com/asiimwe3/deryaccount/releases/latest/download/deryaccount.apk\n" +
            "Learn more: https://deryaccount.vercel.app"
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
        context.startActivity(android.content.Intent.createChooser(send, "Share DeryAccount"))
    }

}
