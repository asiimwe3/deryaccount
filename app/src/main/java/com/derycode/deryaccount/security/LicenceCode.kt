package com.derycode.deryaccount.security

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * LicenceCode — how an activation code is proven authentic.
 *
 * Audit finding 1: the app shipped the HMAC *signing* secret, so anyone who
 * unpacked the APK could mint unlimited valid licences. The secret is gone.
 *
 * New codes are signed with an ECDSA P-256 private key that exists only on
 * DeryCode's offline issuer. The app carries the matching **public** key, which
 * can verify a signature but cannot create one.
 *
 * Code shape (paste-friendly, one line):
 *
 *     DERY2-<base32(payload)>-<base32(derSignature)>
 *     payload = PLAN|DAYS|SHOP|YYYYMMDD      e.g. PRO|365|KAMPALA-MAIN|20261120
 *
 * Codes issued by older builds (16-character `DERY-XXXX-…` HMAC codes) still
 * verify through [verifyLegacy], so no paying shop is locked out. They cannot be
 * forged any more easily than before, but no new ones can be generated from the
 * app, because the generator is no longer in the app.
 */
object LicenceCode {

    /**
     * X.509 SubjectPublicKeyInfo for the P-256 public key, hex encoded.
     * Hex, not Base64: java.util.Base64 needs Android 8 and minSdk here is 24.
     */
    const val PUBLIC_KEY_HEX = "3059301306072a8648ce3d020106082a8648ce3d030107034200044ff45a0548b729ef9ae6ae1c7f047c1dde7fc1bc01e44d0f5308865a4db51f3f67dbff1095587c90adc7685c873e1d163e474cc6c13a80c291e7a02b18719bc5"

    const val PREFIX = "DERY2"
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    data class Payload(
        val planCode: String,
        val days: Int,
        val shopId: String,
        val issued: String,
        val isLegacy: Boolean
    )

    /** Verifies any code the app accepts: current scheme first, then legacy. */
    fun verifyAny(raw: String): Payload? = verify(raw) ?: verifyLegacy(raw)

    /**
     * Verifies a DERY2 code against [publicKeyB64] (the embedded key by default,
     * overridable so tests can use their own keypair).
     */
    fun verify(raw: String, publicKeyHex: String = PUBLIC_KEY_HEX): Payload? {
        val clean = raw.trim().replace(" ", "").replace("\n", "")
        if (!clean.startsWith("$PREFIX-")) return null
        val parts = clean.split('-')
        if (parts.size != 3) return null
        val payloadBytes = runCatching { unb32(parts[1]) }.getOrNull() ?: return null
        val sigBytes = runCatching { unb32(parts[2]) }.getOrNull() ?: return null
        if (payloadBytes.isEmpty() || sigBytes.isEmpty()) return null

        val authentic = runCatching {
            val key = KeyFactory.getInstance("EC")
                .generatePublic(X509EncodedKeySpec(hexToBytes(publicKeyHex)))
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(key)
                update(payloadBytes)
                verify(sigBytes)
            }
        }.getOrDefault(false)
        if (!authentic) return null

        val payload = String(payloadBytes, Charsets.UTF_8)
        val f = payload.split('|')
        if (f.size != 4) return null
        val days = f[1].toIntOrNull() ?: return null
        if (days < 1 || days > 3650) return null
        if (f[0].isBlank() || f[0].length > 16) return null
        return Payload(f[0], days, f[2], f[3], isLegacy = false)
    }

    /**
     * The last day legacy codes are accepted. After this, every shop must hold a
     * DERY2 code. Without a cutoff, keeping the old verifier alive would keep the
     * original forgery hole open for ever, because the legacy secret is by
     * definition still in old builds.
     */
    const val LEGACY_SUNSET = "2027-06-30"

    /**
     * Legacy HMAC codes (`DERY-XXXX-XXXX-XXXX-XXXX`). Kept only so licences
     * already sold keep working until [LEGACY_SUNSET]; no new ones can be
     * generated from the app.
     */
    fun verifyLegacy(raw: String, today: String = todayIso()): Payload? {
        if (today > LEGACY_SUNSET) return null
        val clean = raw.trim().uppercase()
            .removePrefix("DERY-").replace("-", "").replace(" ", "")
        if (clean.length != 14) return null
        val code = clean.substring(0, 2)
        val daysStr = clean.substring(2, 6)
        val sig = clean.substring(6, 14)
        val expected = runCatching {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(LEGACY_SECRET.toByteArray(), "HmacSHA256"))
            base32(mac.doFinal((code + daysStr).toByteArray())).take(8)
        }.getOrNull() ?: return null
        if (expected != sig) return null
        val days = daysStr.toIntOrNull() ?: return null
        if (days < 1 || days > 3650) return null
        return Payload(code, days, shopId = "", issued = "", isLegacy = true)
    }

    /**
     * Retained ONLY to verify licences already in the field. It cannot be used to
     * issue new codes: the issuer uses the private key instead.
     */
    private const val LEGACY_SECRET = "DeryAccount-Offline-License-v1-2026-KampalaUG"

    /** yyyy-MM-dd, comparable as a string. */
    private fun todayIso(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Bad hex" }
        return ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    private fun base32(bytes: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                sb.append(ALPHABET[(buffer shr bits) and 0x1F])
            }
        }
        if (bits > 0) sb.append(ALPHABET[(buffer shl (5 - bits)) and 0x1F])
        return sb.toString()
    }

    private fun unb32(s: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (c in s.uppercase()) {
            val v = ALPHABET.indexOf(c)
            if (v < 0) continue
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }
}
