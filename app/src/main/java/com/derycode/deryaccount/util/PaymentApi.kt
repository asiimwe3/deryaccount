package com.derycode.deryaccount.util

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * PaymentApi — talks to the DeryAccount PesaPal backend (Base44 functions).
 * The backend creates the order at PesaPal, gives us the payment page URL,
 * and once the shop pays it issues the activation code automatically.
 * Every call is wrapped — offline or server problems never crash the app.
 */
object PaymentApi {

    private const val BASE = "https://superagent-d41c313d.base44.app/functions"

    data class OrderCreated(
        val redirectUrl: String,
        val orderTrackingId: String,
        val merchantRef: String,
        val amountUgx: Long
    )

    data class StatusResult(
        val status: String,               // COMPLETED | PENDING | FAILED | INVALID
        val activationCode: String?,      // set when COMPLETED
        val message: String?
    )

    /** Stable per-install device reference so support can trace payments. */
    fun deviceRef(context: Context): String {
        val prefs = context.getSharedPreferences("dery_pay", Context.MODE_PRIVATE)
        var ref = prefs.getString("deviceRef", null)
        if (ref == null) {
            ref = UUID.randomUUID().toString().substring(0, 8)
            prefs.edit().putString("deviceRef", ref).apply()
        }
        return ref
    }

    // ---- pending order persistence (survives app close mid-payment) ----
    fun savePending(context: Context, order: OrderCreated, planCode: String, months: Int) {
        context.getSharedPreferences("dery_pay", Context.MODE_PRIVATE).edit()
            .putString("pending", JSONObject()
                .put("orderTrackingId", order.orderTrackingId)
                .put("merchantRef", order.merchantRef)
                .put("amountUgx", order.amountUgx)
                .put("planCode", planCode)
                .put("months", months)
                .toString())
            .apply()
    }

    fun pendingOrder(context: Context): Triple<String, String, Long>? {
        val s = context.getSharedPreferences("dery_pay", Context.MODE_PRIVATE)
            .getString("pending", null) ?: return null
        return try {
            val o = JSONObject(s)
            Triple(o.getString("orderTrackingId"), o.getString("planCode"), o.getLong("amountUgx"))
        } catch (_: Exception) { null }
    }

    fun clearPending(context: Context) {
        context.getSharedPreferences("dery_pay", Context.MODE_PRIVATE)
            .edit().remove("pending").apply()
    }

    private fun post(path: String, body: JSONObject): JSONObject? = try {
        val conn = URL("$BASE/$path").openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: "{}"
        conn.disconnect()
        if (code in 200..299) JSONObject(text) else null
    } catch (_: Exception) { null }

    /** Ask the backend to create a PesaPal order. Null on any failure. */
    fun createOrder(context: Context, planCode: String, months: Int,
                    phone: String, name: String): OrderCreated? {
        val j = post("pesapalCreateOrder", JSONObject()
            .put("planCode", planCode)
            .put("months", months)
            .put("phone", phone)
            .put("name", name)
            .put("deviceRef", deviceRef(context))) ?: return null
        if (!j.optBoolean("ok")) return null
        return OrderCreated(
            redirectUrl = j.optString("redirectUrl"),
            orderTrackingId = j.optString("orderTrackingId"),
            merchantRef = j.optString("merchantRef"),
            amountUgx = j.optLong("amountUgx")
        ).takeIf { it.redirectUrl.isNotBlank() && it.orderTrackingId.isNotBlank() }
    }

    /** Poll the backend for the payment status (and the activation code). */
    fun checkStatus(context: Context, orderTrackingId: String): StatusResult? {
        val j = post("pesapalCheckStatus", JSONObject()
            .put("orderTrackingId", orderTrackingId)) ?: return null
        if (!j.optBoolean("ok")) return null
        return StatusResult(
            status = j.optString("status", "PENDING").uppercase(),
            activationCode = j.optString("activationCode").takeIf { it.isNotBlank() },
            message = j.optString("message").takeIf { it.isNotBlank() })
    }
}
