package com.derycode.deryaccount.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * Audit finding 6 — PIN storage must be salted, slow, and never a bare hash.
 * Legacy hashes must still verify so no shop is locked out.
 */
class PinHasherTest {

    private fun legacySha256(s: String) =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    @Test fun `a pin verifies against its own hash`() {
        val stored = PinHasher.hash("1234")
        assertTrue(PinHasher.verify("1234", stored))
    }

    @Test fun `a wrong pin is rejected`() {
        val stored = PinHasher.hash("1234")
        assertFalse(PinHasher.verify("4321", stored))
        assertFalse(PinHasher.verify("12345", stored))
        assertFalse(PinHasher.verify("", stored))
    }

    @Test fun `the stored value is not a bare hash of the pin`() {
        val stored = PinHasher.hash("1234")
        assertFalse("stored value leaked a plain sha256", stored.contains(legacySha256("1234")))
        assertTrue(stored.startsWith("pbkdf2\$"))
    }

    @Test fun `the same pin hashes differently every time (salt)`() {
        val a = PinHasher.hash("1234")
        val b = PinHasher.hash("1234")
        assertNotEquals(a, b)
        assertTrue(PinHasher.verify("1234", a))
        assertTrue(PinHasher.verify("1234", b))
    }

    @Test fun `a legacy sha256 record still lets the owner in`() {
        val legacy = legacySha256("1234")
        assertTrue(PinHasher.verify("1234", legacy))
        assertFalse(PinHasher.verify("9999", legacy))
    }

    @Test fun `legacy records are flagged for upgrade, new ones are not`() {
        assertTrue(PinHasher.needsUpgrade(legacySha256("1234")))
        assertFalse(PinHasher.needsUpgrade(PinHasher.hash("1234")))
        assertTrue(PinHasher.needsUpgrade(null))
        assertTrue(PinHasher.needsUpgrade(""))
    }

    @Test fun `a corrupt stored value fails closed instead of crashing`() {
        assertFalse(PinHasher.verify("1234", "pbkdf2\$notanumber\$zz\$zz"))
        assertFalse(PinHasher.verify("1234", "pbkdf2\$1000\$zz\$zz"))
        assertFalse(PinHasher.verify("1234", "garbage"))
        assertFalse(PinHasher.verify("1234", null))
    }

    @Test fun `a hash cannot be replayed across schemes`() {
        // The legacy hash of a pin must not be accepted as a pbkdf2 record.
        val legacy = legacySha256("1234")
        assertFalse(PinHasher.verify("1234", "pbkdf2\$150000\$$legacy\$$legacy"))
    }
}
