package com.derycode.deryaccount.billing

import android.content.Context
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Hotel Edition on the phone.
 *
 * The hotel side of DeryAccount is sold separately: UGX 200,000 a year. A hotel
 * pays DeryCode by MTN or Airtel Money, gets an activation code, and types it
 * once. The code verifies offline with the same HMAC scheme and the same secret
 * as the Windows app, so ONE code licenses the front-desk computer and every
 * phone in the hotel.
 *
 * Every install gets a 30-day free trial of the full hotel module, then 7 days
 * of grace. When the licence lapses only the hotel screens pause: sales, stock
 * and the books keep working, because a business must never lose access to its
 * own records.
 */
object HotelLicence {

    const val YEARLY_UGX = 200_000L
    const val TRIAL_DAYS = 30
    const val GRACE_DAYS = 7
    const val SUPPORT_PHONE = "0762 306 675"
    const val SUPPORT_WHATSAPP = "256762306675"

    private const val PREFS = "dery_hotel_licence"
    private const val K_TRIAL = "trial_start"
    private const val K_KEY = "key"
    private const val K_EXPIRES = "expires_at"
    private const val K_RECEIPTS = "receipts"

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private const val PLAN_CODE = "HT"

    private const val DAY = 24L * 60 * 60 * 1000

    enum class Status { TRIAL, ACTIVE, GRACE, LOCKED }

    data class State(
        val status: Status,
        val daysLeft: Int,
        val expiresAtMillis: Long?,
        val key: String?
    ) {
        val open: Boolean get() = status != Status.LOCKED
        val headline: String
            get() = when (status) {
                Status.ACTIVE -> "Hotel Edition active"
                Status.GRACE -> "Renew soon"
                Status.TRIAL -> "Free trial · $daysLeft day${if (daysLeft == 1) "" else "s"} left"
                Status.LOCKED -> "Hotel Edition not active"
            }
    }

    data class Activation(val ok: Boolean, val message: String, val days: Int = 0, val expiresAtMillis: Long? = null)

    data class Payment(
        val at: Long, val amount: Long, val method: String, val days: Int,
        val code: String, val payer: String
    )

    val FEATURES = listOf(
        "Rooms, room types and nightly rates",
        "Bookings, walk-ins, extensions and no-shows",
        "Guest register with stay history",
        "Front desk: arrivals, in-house, departures",
        "Guest folios with restaurant, bar and extras",
        "Deposits held, revenue at check-out",
        "Housekeeping board",
        "Occupancy, ADR and RevPAR reports",
        "Guest receipts and tax invoices",
        "Works offline on the phone"
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun ensureTrialStarted(context: Context) {
        val p = prefs(context)
        if (!p.contains(K_TRIAL)) p.edit().putLong(K_TRIAL, System.currentTimeMillis()).apply()
    }

    fun state(context: Context): State {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        val key = p.getString(K_KEY, null)
        val expires = if (p.contains(K_EXPIRES)) p.getLong(K_EXPIRES, 0L) else null

        if (expires != null && expires > 0) {
            val left = ((expires - now) / DAY).toInt()
            if (now < expires) {
                return State(Status.ACTIVE, left.coerceAtLeast(0) + 1, expires, key)
            }
            val graceEnd = expires + GRACE_DAYS * DAY
            if (now < graceEnd) return State(Status.GRACE, ((-(expires - now)) / DAY).toInt(), expires, key)
            return State(Status.LOCKED, 0, expires, key)
        }

        if (!p.contains(K_TRIAL)) {
            // Nothing recorded yet: a fresh install shows the whole trial ahead.
            return State(Status.TRIAL, TRIAL_DAYS, null, null)
        }
        val trialEnd = p.getLong(K_TRIAL, now) + TRIAL_DAYS * DAY
        return if (now < trialEnd) {
            State(Status.TRIAL, (((trialEnd - now) / DAY).toInt()) + 1, trialEnd, key)
        } else {
            State(Status.LOCKED, 0, trialEnd, key)
        }
    }

    fun activate(context: Context, rawCode: String): Activation {
        val days = verify(rawCode) ?: return Activation(false, "That is not a Hotel Edition code. Check it and try again.")
        val p = prefs(context)
        val now = System.currentTimeMillis()
        val current = if (p.contains(K_EXPIRES)) p.getLong(K_EXPIRES, 0L) else 0L
        // Renewing early stacks the new year on top of the days still unused.
        val base = if (current > now) current else now
        val expires = base + days * DAY
        p.edit()
            .putString(K_KEY, rawCode.trim().uppercase(Locale.US))
            .putLong(K_EXPIRES, expires)
            .apply()
        ensureTrialStarted(context)
        return Activation(true, "Hotel Edition active", days, expires)
    }

    /**
     * Hotel codes are issued offline with the private key now, so the app cannot
     * mint one. Returns empty so any caller shows a clear "issue offline" state
     * instead of handing out a code it signed itself.
     */
    fun issueCode(days: Int = 365): String = ""

    private fun verify(rawInput: String): Int? {
        // Verified by the same public-key scheme as the main licence, so the
        // hotel unlock no longer carries a shared secret in the APK.
        val payload = com.derycode.deryaccount.security.LicenceCode.verifyAny(rawInput) ?: return null
        if (payload.planCode != PLAN_CODE) return null
        return payload.days
    }

    private fun base32(bytes: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                bitsLeft -= 5
                sb.append(ALPHABET[(buffer shr bitsLeft) and 0x1F])
            }
        }
        return sb.toString()
    }

    // ── Subscription payments, kept so a receipt can be reprinted ────────────

    fun recordPayment(context: Context, amount: Long, method: String, days: Int, code: String, payer: String) {
        val line = listOf(
            System.currentTimeMillis().toString(), amount.toString(), method.uppercase(),
            days.toString(), code.trim().uppercase(Locale.US), payer.replace("\u001f", " ")
        ).joinToString("\u001f")
        val p = prefs(context)
        val existing = p.getString(K_RECEIPTS, "") ?: ""
        p.edit().putString(K_RECEIPTS, if (existing.isBlank()) line else "$existing\n$line").apply()
    }

    fun payments(context: Context): List<Payment> =
        (prefs(context).getString(K_RECEIPTS, "") ?: "").split("\n")
            .filter { it.isNotBlank() }
            .mapNotNull { row ->
                val f = row.split("\u001f")
                if (f.size < 6) null else Payment(
                    f[0].toLongOrNull() ?: 0L, f[1].toLongOrNull() ?: 0L, f[2],
                    f[3].toIntOrNull() ?: 0, f[4], f[5]
                )
            }
            .reversed()
}
