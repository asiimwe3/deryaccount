package com.derycode.deryaccount.desktop

import com.derycode.deryaccount.desktop.data.*
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Hotel Edition licence tests: the yearly price, the free trial, a real
 * activation code, renewal on top of unused days, the short grace window, and
 * the promise that a lapsed licence locks only the hotel screens.
 */
class HotelLicenceTest {

    private lateinit var file: File
    private lateinit var repo: Repo

    @BeforeTest
    fun setUp() {
        file = File.createTempFile("deryaccount-licence-", ".db")
        file.delete()
        repo = Repo.open(file)
        repo.saveProfile(mapOf("name" to "Test Lodge", "phone" to "0700000000"))
    }

    @AfterTest
    fun tearDown() {
        runCatching { repo.db.close() }
        file.delete()
    }

    @Test
    fun yearlyPriceIsTwoHundredThousand() {
        assertEquals(200_000L, HotelLicence.YEARLY_UGX)
        assertEquals(200_000L, HotelLicence.priceFor(365))
    }

    @Test
    fun freshInstallGetsAFreeTrialThatIsOpen() {
        HotelLicence.ensureTrialStarted(repo)
        val s = HotelLicence.state(repo)
        assertEquals(HotelLicence.Status.TRIAL, s.status)
        assertTrue(s.open)
        assertEquals(HotelLicence.TRIAL_DAYS, s.daysLeft)
    }

    @Test
    fun aRealCodeActivatesTheYearOnBothPlatforms() {
        val code = HotelLicence.issueCode(365)
        assertTrue(code.startsWith("DERY-HT"))
        val res = HotelLicence.activate(repo, code)
        assertTrue(res.ok, res.message)
        val s = HotelLicence.state(repo)
        assertEquals(HotelLicence.Status.ACTIVE, s.status)
        assertTrue(s.daysLeft in 364..366)
        assertTrue(s.open)
    }

    @Test
    fun aShopCodeIsRejected() {
        // A shop plan code (ST = Starter) must never unlock the hotel module.
        val shopCode = "DERY-ST" + "0365" + "AAAA"
        assertFalse(HotelLicence.activate(repo, shopCode).ok)
        // Nor a tampered hotel code.
        val good = HotelLicence.issueCode(365)
        val tampered = good.dropLast(2) + "ZZ"
        assertFalse(HotelLicence.activate(repo, tampered).ok)
    }

    @Test
    fun renewingEarlyAddsTheNewYearOnTop() {
        HotelLicence.activate(repo, HotelLicence.issueCode(365))
        val first = HotelLicence.state(repo).expiresOn!!
        HotelLicence.activate(repo, HotelLicence.issueCode(365))
        val second = HotelLicence.state(repo).expiresOn!!
        assertTrue(second > first, "renewal should extend, not restart: $first -> $second")
        assertTrue(HotelLicence.state(repo).daysLeft >= 729)
    }

    @Test
    fun aLapsedLicenceLocksOnlyTheHotelScreens() {
        repo.db.meta("hotel_trial_start", Hotel.addDays(Clock.today(), -40))
        val s = HotelLicence.state(repo)
        assertEquals(HotelLicence.Status.LOCKED, s.status)
        assertFalse(s.open)
    }

    @Test
    fun theGraceWindowKeepsTheDeskOpen() {
        repo.db.meta("hotel_key", "DERY-TEST")
        repo.db.meta("hotel_expires", Hotel.addDays(Clock.today(), -3))
        val s = HotelLicence.state(repo)
        assertEquals(HotelLicence.Status.GRACE, s.status)
        assertTrue(s.open, "a hotel must never be cut off mid-check-out")
    }

    @Test
    fun aSubscriptionPaymentIsRememberedForReprinting() {
        val code = HotelLicence.issueCode(365)
        HotelLicence.activate(repo, code)
        HotelLicence.recordPayment(repo, HotelLicence.YEARLY_UGX, "MTN_MOMO", 365, code, "Test Lodge")
        val paid = HotelLicence.payments(repo)
        assertEquals(1, paid.size)
        assertEquals(HotelLicence.YEARLY_UGX, paid.first().amount)
        assertEquals("MTN_MOMO", paid.first().method)
        assertEquals(code, paid.first().code)
    }

    @Test
    fun theSubscriptionReceiptIsARealPdf() {
        val code = HotelLicence.issueCode(365)
        val out = File.createTempFile("hotel-licence-receipt-", ".pdf")
        try {
            Pdf.subscriptionReceipt(
                payer = "Test Lodge", amount = HotelLicence.YEARLY_UGX, method = "MTN_MOMO",
                days = 365, code = code, periodFrom = Clock.today(),
                periodTo = Hotel.addDays(Clock.today(), 365), target = out
            )
            val bytes = out.readBytes()
            assertTrue(bytes.size > 900, "receipt looks too small: ${bytes.size} bytes")
            assertEquals("%PDF", String(bytes.take(4).toByteArray()))
        } finally {
            out.delete()
        }
    }
}
