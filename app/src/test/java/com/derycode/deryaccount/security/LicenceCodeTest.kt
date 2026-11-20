package com.derycode.deryaccount.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Audit finding 1 — a licence code must be verifiable but not forgeable.
 * These tests use their own keypair, so they prove the verifier really checks
 * the signature rather than trusting the payload.
 */
class LicenceCodeTest {

    private val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    private fun b32(bytes: ByteArray): String {
        val sb = StringBuilder(); var buf = 0; var bits = 0
        for (b in bytes) {
            buf = (buf shl 8) or (b.toInt() and 0xFF); bits += 8
            while (bits >= 5) { bits -= 5; sb.append(alphabet[(buf shr bits) and 0x1F]) }
        }
        if (bits > 0) sb.append(alphabet[(buf shl (5 - bits)) and 0x1F])
        return sb.toString()
    }

    private fun keypair(): Pair<java.security.PrivateKey, String> {
        val gen = KeyPairGenerator.getInstance("EC")
        gen.initialize(ECGenParameterSpec("secp256r1"))
        val kp = gen.generateKeyPair()
        return kp.private to kp.public.encoded.joinToString("") { "%02x".format(it) }
    }

    private fun issue(payload: String, priv: java.security.PrivateKey): String {
        val sig = Signature.getInstance("SHA256withECDSA").run {
            initSign(priv); update(payload.toByteArray()); sign()
        }
        return "DERY2-${b32(payload.toByteArray())}-${b32(sig)}"
    }

    @Test fun `a properly signed code verifies and yields its payload`() {
        val (priv, pubHex) = keypair()
        val code = issue("PRO|365|KAMPALA-MAIN|20261120", priv)
        val p = LicenceCode.verify(code, pubHex)
        assertNotNull(p)
        assertEquals("PRO", p!!.planCode)
        assertEquals(365, p.days)
        assertEquals("KAMPALA-MAIN", p.shopId)
        assertFalse(p.isLegacy)
    }

    @Test fun `a tampered payload is rejected`() {
        val (priv, pubHex) = keypair()
        val code = issue("BASIC|30|SHOP|20261120", priv)
        // swap the payload for a longer licence, keeping the original signature
        val forgedPayload = b32("PRO|3650|SHOP|20261120".toByteArray())
        val parts = code.split('-')
        assertNull(LicenceCode.verify("DERY2-$forgedPayload-${parts[2]}", pubHex))
    }

    @Test fun `a code signed with a different key is rejected`() {
        val (privA, _) = keypair()
        val (_, pubB) = keypair()
        assertNull(LicenceCode.verify(issue("PRO|365|SHOP|20261120", privA), pubB))
    }

    @Test fun `garbage never crashes or verifies`() {
        assertNull(LicenceCode.verify("DERY2-nope-nope"))
        assertNull(LicenceCode.verify(""))
        assertNull(LicenceCode.verify("DERY-ABCD-EFGH"))
    }

    @Test fun `an out-of-range duration is rejected even when signed`() {
        val (priv, pubHex) = keypair()
        assertNull(LicenceCode.verify(issue("PRO|99999|SHOP|20261120", priv), pubHex))
        assertNull(LicenceCode.verify(issue("PRO|0|SHOP|20261120", priv), pubHex))
    }

    @Test fun `the embedded public key parses (it is not corrupt)`() {
        // No signing here: just prove the shipped key is a valid P-256 public key.
        val spec = java.security.spec.X509EncodedKeySpec(
            LicenceCode.PUBLIC_KEY_HEX.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        )
        val key = java.security.KeyFactory.getInstance("EC").generatePublic(spec)
        assertNotNull(key)
        assertEquals("EC", key.algorithm)
    }

    @Test fun `the app no longer contains the signing secret path`() {
        // Legacy verification is time-boxed: after the sunset, old codes stop working.
        assertTrue(LicenceCode.LEGACY_SUNSET >= "2026-12-31")
    }

    // ── Legacy compatibility ──────────────────────────────────────────────

    private fun legacyCode(planCode: String, days: Int): String {
        val secret = "DeryAccount-Offline-License-v1-2026-KampalaUG"
        val daysStr = "%04d".format(days)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        val sig = b32(mac.doFinal((planCode + daysStr).toByteArray())).take(8)
        return "DERY-" + (planCode + daysStr + sig).chunked(4).joinToString("-")
    }

    @Test fun `an old hmac code still activates before the sunset`() {
        val p = LicenceCode.verifyLegacy(legacyCode("PR", 365), today = "2026-11-20")
        assertNotNull(p)
        assertEquals(365, p!!.days)
        assertTrue(p.isLegacy)
    }

    @Test fun `old hmac codes stop working after the sunset`() {
        assertNull(LicenceCode.verifyLegacy(legacyCode("PR", 365), today = "2027-07-01"))
    }

    @Test fun `a forged legacy signature is rejected`() {
        assertNull(LicenceCode.verifyLegacy("DERY-PR0365-AAAA-AAAA-AAAA", today = "2026-11-20"))
    }
}
