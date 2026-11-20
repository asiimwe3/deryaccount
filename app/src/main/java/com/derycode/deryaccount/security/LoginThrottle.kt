package com.derycode.deryaccount.security

import android.content.Context

/**
 * LoginThrottle — makes guessing a staff PIN impractical.
 *
 * Audit finding 6 ("account protection"). Without this, a 4-digit PIN is 10,000
 * taps: on a shop counter phone that is a coffee break. After [MAX_ATTEMPTS]
 * wrong PINs the username is locked for [LOCK_MS]; a correct PIN clears it.
 *
 * State is in SharedPreferences so killing the app does not reset the counter.
 * Failures are counted per username, so one careless cashier cannot lock the owner out.
 */
object LoginThrottle {

    const val MAX_ATTEMPTS = 5
    const val LOCK_MS = 60_000L

    private const val PREFS = "deryaccount_login_guard"

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Milliseconds still to wait, 0 when the user may try again now. */
    fun remainingMillis(context: Context, username: String): Long {
        val until = prefs(context).getLong("lock_until_${username.lowercase()}", 0L)
        return (until - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    /**
     * Records a failed attempt. Returns how many attempts are left before the
     * lock, or 0 when this attempt triggered the lock.
     */
    fun recordFailure(context: Context, username: String): Int {
        val p = prefs(context)
        val key = "fails_${username.lowercase()}"
        val fails = p.getInt(key, 0) + 1
        return if (fails >= MAX_ATTEMPTS) {
            p.edit()
                .putInt(key, 0)
                .putLong("lock_until_${username.lowercase()}", System.currentTimeMillis() + LOCK_MS)
                .apply()
            0
        } else {
            p.edit().putInt(key, fails).apply()
            MAX_ATTEMPTS - fails
        }
    }

    /** A good login wipes the counter and any lock. */
    fun clear(context: Context, username: String) {
        prefs(context).edit()
            .remove("fails_${username.lowercase()}")
            .remove("lock_until_${username.lowercase()}")
            .apply()
    }
}
