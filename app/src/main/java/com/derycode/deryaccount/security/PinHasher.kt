package com.derycode.deryaccount.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PinHasher — how a staff PIN is stored.
 *
 * Audit finding 6: PINs were stored as a bare SHA-256 of the digits. A 4-digit
 * PIN is 10,000 possibilities, so that hash is reversible on a phone in well
 * under a second: anyone who copied the database could read every cashier's PIN.
 *
 * Now a PIN is stored as:
 *
 *     pbkdf2$<iterations>$<saltBase64>$<hashBase64>
 *
 * PBKDF2-HMAC-SHA1 with a per-user random salt and a high iteration count. SHA-1
 * is used only because it is the KDF digest every Android version supports; the
 * security comes from the iteration count and the salt, not from the digest.
 *
 * Legacy `sha256` hashes still verify (so nobody is locked out of their shop) and
 * are silently upgraded to PBKDF2 the next time that PIN is entered correctly.
 *
 * Pure JVM: no Android imports, so this is unit tested directly.
 */
object PinHasher {

    const val ITERATIONS = 150_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val PREFIX = "pbkdf2"

    /** Cheap to compute, impossible to reverse: this is what gets stored. */
    fun hash(pin: String, iterations: Int = ITERATIONS): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val key = derive(pin, salt, iterations)
        return "$PREFIX\$$iterations\$${b64(salt)}\$${b64(key)}"
    }

    /**
     * True when [pin] matches [stored], whichever scheme [stored] uses.
     * Never throws: a corrupt stored value is treated as a failed match, so a bad
     * record cannot crash the login screen or open the shop.
     */
    fun verify(pin: String, stored: String?): Boolean {
        if (stored.isNullOrBlank() || pin.isEmpty()) return false
        return try {
            when {
                stored.startsWith("$PREFIX\$") -> verifyPbkdf2(pin, stored)
                // Legacy unsalted SHA-256 hex, exactly as older installs wrote it.
                stored.length == 64 && stored.all { it.isDigit() || it in 'a'..'f' } ->
                    constantTimeEquals(sha256Hex(pin), stored.lowercase())
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    /** True when [stored] is the old scheme and should be re-hashed after a good login. */
    fun needsUpgrade(stored: String?): Boolean =
        stored.isNullOrBlank() || !stored.startsWith("$PREFIX\$")

    private fun verifyPbkdf2(pin: String, stored: String): Boolean {
        val parts = stored.split('$')
        if (parts.size != 4) return false
        val iterations = parts[1].toIntOrNull() ?: return false
        if (iterations < 1) return false
        val salt = unb64(parts[2])
        val expected = unb64(parts[3])
        val actual = derive(pin, salt, iterations, expected.size * 8)
        return constantTimeEquals(actual, expected)
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int, bits: Int = KEY_BITS): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, bits)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun sha256Hex(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /** Comparison time depends only on length, not on how many bytes matched. */
    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    private fun constantTimeEquals(a: String, b: String): Boolean =
        constantTimeEquals(a.toByteArray(), b.toByteArray())

    // Hex, not Base64: java.util.Base64 needs Android 8, and this app still runs
    // on Android 7 tablets. Hex is portable and needs no dependency.
    private fun b64(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun unb64(s: String): ByteArray {
        require(s.length % 2 == 0) { "Bad hex" }
        return ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
}
