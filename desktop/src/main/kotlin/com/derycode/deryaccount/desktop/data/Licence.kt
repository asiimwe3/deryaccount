package com.derycode.deryaccount.desktop.data

import java.util.Locale
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Hotel Edition licensing.
 *
 * The hotel side of DeryAccount is sold as its own subscription — UGX
 * 200,000 per year — separate from the shop plans. A hotel pays DeryCode by
 * MTN or Airtel Money, DeryCode issues an activation code, and the code is
 * verified here on the computer with no internet at all.
 *
 * Every install gets a 30-day free trial of the full hotel module, so a hotel
 * can load its rooms, take a few bookings and print real receipts before it
 * pays anything. When the trial or the subscription runs out the hotel screens
 * lock, but the shop side and the books keep working: DeryAccount never locks
 * a business out of its own records.
 *
 * The code scheme is the same one the phone app uses, so one code licenses
 * both the Windows front desk and the Android app.
 */
object HotelLicence {

    const val YEARLY_UGX = 200_000L
    const val MONTHLY_UGX = 20_000L
    const val TRIAL_DAYS = 30
    const val SUPPORT_PHONE = "0762 306 675"
    const val SUPPORT_WHATSAPP = "256762306675"

    private const val SECRET = "DeryAccount-Offline-License-v1-2026-KampalaUG"
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private const val PLAN_CODE = "HT"

    private const val K_TRIAL = "hotel_trial_start"
    private const val K_KEY = "hotel_key"
    private const val K_EXPIRES = "hotel_expires"
    private const val K_DAYS = "hotel_days"
    private const val K_RECEIPTS = "hotel_licence_receipts"

    val FEATURES = listOf(
        "Rooms, room types, floors and nightly rates",
        "Bookings, walk-ins, extensions, moves and no-shows",
        "Guest register with stay history and ID capture",
        "Front desk: arrivals, in-house, departures, one-tap check-in",
        "Guest folios with restaurant, bar, laundry and extras",
        "Deposits held safely and revenue recognised at check-out",
        "Housekeeping board: clean, dirty, out of order, jobs",
        "Occupancy, ADR, RevPAR and revenue by department",
        "Printable guest receipts, tax invoices and statements",
        "Restaurant and bar POS on the same books",
        "Full double-entry accounting behind every posting",
        "Unlimited rooms and guests, works with no internet"
    )

    enum class Status { TRIAL, ACTIVE, GRACE, LOCKED }

    data class State(
        val status: Status,
        val daysLeft: Int,
        val expiresOn: String?,
        val key: String?,
        val trialStarted: String?
    ) {
        /** true when the hotel screens may be used right now. */
        val open: Boolean get() = status != Status.LOCKED

        val headline: String
            get() = when (status) {
                Status.ACTIVE -> "Hotel Edition active"
                Status.GRACE -> "Renew soon"
                Status.TRIAL -> "Free trial · $daysLeft day${if (daysLeft == 1) "" else "s"} left"
                Status.LOCKED -> "Hotel Edition not active"
            }
    }

    data class Activation(val ok: Boolean, val message: String, val days: Int = 0, val expiresOn: String? = null)

    // ── State ────────────────────────────────────────────────────────────────

    /** Starts the trial clock the first time the hotel area is opened. */
    fun ensureTrialStarted(repo: Repo) {
        if (repo.setting(K_TRIAL, "").isBlank()) {
            repo.db.meta(K_TRIAL, Clock.today())
        }
    }

    fun state(repo: Repo): State {
        val today = Clock.today()
        val expires = repo.setting(K_EXPIRES, "")
        val key = repo.setting(K_KEY, "").ifBlank { null }
        val trialStart = repo.setting(K_TRIAL, "").ifBlank { null }

        if (expires.isNotBlank()) {
            val left = daysBetween(today, expires.take(10))
            if (left >= 0) {
                return State(Status.ACTIVE, left, expires.take(10), key, trialStart)
            }
            // A short grace window so a hotel is never cut off mid-check-out.
            if (left >= -7) return State(Status.GRACE, left, expires.take(10), key, trialStart)
            return State(Status.LOCKED, 0, expires.take(10), key, trialStart)
        }

        if (trialStart == null) {
            // Nothing recorded yet: treat as a fresh trial that has not begun.
            return State(Status.TRIAL, TRIAL_DAYS, null, null, null)
        }
        val trialEnd = Hotel.addDays(trialStart, TRIAL_DAYS.toLong())
        val left = daysBetween(today, trialEnd)
        return if (left > 0) State(Status.TRIAL, left, trialEnd, key, trialStart)
        else State(Status.LOCKED, 0, trialEnd, key, trialStart)
    }

    // ── Activation ───────────────────────────────────────────────────────────

    fun activate(repo: Repo, rawCode: String): Activation {
        val parsed = verify(rawCode) ?: return Activation(false, "That code is not a DeryAccount Hotel Edition code. Check it and try again.")
        val days = parsed
        val startsFrom = run {
            val current = repo.setting(K_EXPIRES, "")
            // Renewing early adds the new year on top of what is left.
            if (current.isNotBlank() && daysBetween(Clock.today(), current.take(10)) > 0) current.take(10) else Clock.today()
        }
        val expires = Hotel.addDays(startsFrom, days.toLong())
        repo.db.meta(K_KEY, rawCode.trim().uppercase(Locale.US))
        repo.db.meta(K_EXPIRES, expires)
        repo.db.meta(K_DAYS, days.toString())
        if (repo.setting(K_TRIAL, "").isBlank()) repo.db.meta(K_TRIAL, Clock.today())
        return Activation(true, "Hotel Edition activated until $expires", days, expires)
    }

    fun clear(repo: Repo) {
        repo.db.meta(K_KEY, "")
        repo.db.meta(K_EXPIRES, "")
        repo.db.meta(K_DAYS, "")
    }

    /** The yearly price actually paid, from the number of days in the code. */
    fun priceFor(days: Int): Long = when {
        days >= 300 -> YEARLY_UGX
        else -> (MONTHLY_UGX * (days / 30).coerceAtLeast(1))
    }

    /** Support tool: issue a code for a hotel that has paid. 365 days = one year. */
    fun issueCode(days: Int = 365): String {
        val daysStr = String.format(Locale.US, "%04d", days)
        val payload = PLAN_CODE + daysStr
        val raw = payload + sign(payload).take(8)
        return "DERY-" + raw.chunked(4).joinToString("-")
    }

    private fun verify(rawInput: String): Int? {
        val clean = rawInput.trim().uppercase(Locale.US)
            .removePrefix("DERY-").replace("-", "").replace(" ", "")
        if (clean.length < 14) return null
        if (clean.substring(0, 2) != PLAN_CODE) return null
        val daysStr = clean.substring(2, 6)
        val sig = clean.substring(6, 14)
        if (sign(PLAN_CODE + daysStr).take(8) != sig) return null
        val days = daysStr.toIntOrNull() ?: return null
        if (days < 1 || days > 3650) return null
        return days
    }

    private fun sign(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(SECRET.toByteArray(), "HmacSHA256"))
        return base32(mac.doFinal(payload.toByteArray()))
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

    private fun daysBetween(from: String, to: String): Int = runCatching {
        java.time.temporal.ChronoUnit.DAYS.between(
            java.time.LocalDate.parse(from.take(10)), java.time.LocalDate.parse(to.take(10))
        ).toInt()
    }.getOrDefault(0)

    // ── Subscription payment record (for receipts) ────────────────────────────

    /**
     * A hotel's subscription payment is kept on this computer so the hotel can
     * reprint the DeryCode receipt any time: meta key holds a small ledger.
     */
    data class Payment(val id: String, val at: String, val amount: Long, val method: String, val days: Int, val code: String, val payer: String)

    fun recordPayment(repo: Repo, amount: Long, method: String, days: Int, code: String, payer: String): Payment {
        val p = Payment(UUID.randomUUID().toString(), Clock.nowIso(), amount, method.uppercase(), days, code.trim().uppercase(Locale.US), payer.ifBlank { repo.businessName() })
        val line = listOf(p.id, p.at, p.amount, p.method, p.days, p.code, p.payer).joinToString("\u001f")
        val existing = repo.setting(K_RECEIPTS, "")
        repo.db.meta(K_RECEIPTS, if (existing.isBlank()) line else "$existing\n$line")
        return p
    }

    fun payments(repo: Repo): List<Payment> =
        repo.setting(K_RECEIPTS, "").split("\n").filter { it.isNotBlank() }.mapNotNull { row ->
            val f = row.split("\u001f")
            if (f.size < 7) null else Payment(f[0], f[1], f[2].toLongOrNull() ?: 0L, f[3], f[4].toIntOrNull() ?: 0, f[5], f[6])
        }.reversed()
}
