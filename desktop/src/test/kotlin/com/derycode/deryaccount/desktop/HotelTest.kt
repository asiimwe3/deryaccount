package com.derycode.deryaccount.desktop

import com.derycode.deryaccount.desktop.data.*
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Hotel & accommodation tests: a reservation, a stay, a bill and the ledger.
 * They run against a real SQLite file, so a green run means the folio and the
 * books agree — no revenue before check-out, no deposit ever counted as income,
 * and no room sold twice.
 */
class HotelTest {

    private lateinit var file: File
    private lateinit var repo: Repo
    private lateinit var reports: Reports
    private lateinit var hotel: Hotel

    private lateinit var roomId: String
    private val today = Clock.today()
    private val tomorrow = Hotel.addDays(today, 1)
    private val inThree = Hotel.addDays(today, 3)

    @BeforeTest
    fun setUp() {
        file = File.createTempFile("deryaccount-hotel-", ".db")
        file.delete()
        repo = Repo.open(file)
        reports = Reports(repo)
        hotel = Hotel(repo)
        repo.saveProfile(mapOf("name" to "Test Lodge", "phone" to "0700000000"))

        val typeId = hotel.saveRoomType(null, "Double", 120_000.0, 2, "1 double", taxRate = 18.0)
        roomId = hotel.saveRoom(null, "101", typeId, "1", Hotel.VACANT_CLEAN, "")
        hotel.saveRoom(null, "102", typeId, "1", Hotel.VACANT_CLEAN, "")
    }

    @AfterTest
    fun tearDown() {
        runCatching { repo.db.conn.close() }
        file.delete()
    }

    private fun assertBalanced() = assertEquals(0.0, reports.checkBalance(), 0.01, "books must balance")

    private fun balanceOf(accountId: String): Double = repo.db.scalar(
        "SELECT COALESCE(SUM(debit) - SUM(credit),0) FROM journal_lines WHERE account_id=?", accountId
    )

    @Test
    fun `a booking keeps the deposit as a liability, not income`() {
        val id = hotel.createBooking(
            "Aisha Kabahenda", "0772000111", roomId, today, inThree,
            adults = 2, deposit = 100_000.0, depositMethod = "CASH"
        )
        val b = hotel.booking(id)!!
        assertEquals(Hotel.BOOKED, b.str("status"))
        assertEquals(3, b.int("nights"))

        // Cash came in, but it is money we owe back — a liability, not revenue.
        assertEquals(100_000.0, balanceOf(Accounts.CASH), 0.01)
        assertEquals(-100_000.0, balanceOf(Accounts.GUEST_DEPOSITS), 0.01)
        assertEquals(0.0, balanceOf(Accounts.ROOM_REVENUE), 0.01)
        assertEquals(0.0, hotel.revenueBreakdown(today, inThree).sumOf { it.amount }, 0.01)
        assertBalanced()
    }

    @Test
    fun `check-in puts one room charge per night on the folio`() {
        val id = hotel.createBooking("John Mugisa", "0752", roomId, today, inThree, adults = 1)
        hotel.checkIn(id)
        val charges = hotel.charges(id)
        assertEquals(3, charges.count { it.str("kind") == "ROOM" })
        val folio = hotel.folio(id)
        assertEquals(360_000.0, folio.gross, 0.01)
        assertEquals(Hotel.OCCUPIED, hotel.room(roomId)!!.str("status"))
        // Charging the room does not touch the books yet.
        assertEquals(0.0, balanceOf(Accounts.ROOM_REVENUE), 0.01)
        assertBalanced()
    }

    @Test
    fun `check-out recognises revenue, clears the deposit and balances`() {
        val id = hotel.createBooking(
            "Aisha Kabahenda", "0772000111", roomId, today, inThree, adults = 2,
            deposit = 100_000.0
        )
        hotel.checkIn(id)
        hotel.addCharge(id, "RESTAURANT", "Lunch", 2.0, 15_000.0)
        hotel.postPayment(id, 260_000.0, "MTN_MOMO")

        val folio = hotel.folio(id)
        assertEquals(360_000.0 + 30_000.0, folio.gross, 0.01)
        assertEquals(360_000.0, folio.paid, 0.01)
        assertEquals(30_000.0, folio.balance, 0.01)

        assertFailsWith<IllegalStateException> { hotel.checkOut(id) } // balance still open

        hotel.checkOut(id, settleToDebtor = true)
        val after = hotel.booking(id)!!
        assertEquals(Hotel.CHECKED_OUT, after.str("status"))
        assertEquals(Hotel.VACANT_DIRTY, hotel.room(roomId)!!.str("status"))

        // Revenue recognised, split between accommodation and restaurant.
        val revenue = hotel.revenueBreakdown(today, today)
        val roomsRevenue = revenue.first { it.label.startsWith("Accommodation") }.amount
        val fbRevenue = revenue.first { it.label.startsWith("Restaurant") }.amount
        assertTrue(roomsRevenue > 300_000.0, "room revenue was $roomsRevenue")
        assertTrue(fbRevenue > 20_000.0, "restaurant revenue was $fbRevenue")

        // No deposit left over, the unpaid balance sits in debtors, VAT is owed.
        assertEquals(0.0, balanceOf(Accounts.GUEST_DEPOSITS), 0.01)
        assertEquals(30_000.0, balanceOf(Accounts.DEBTORS), 0.01)
        assertTrue(balanceOf(Accounts.VAT_OUT) < 0, "VAT should be a credit balance")
        assertBalanced()
    }

    @Test
    fun `a full cash stay leaves every shilling accounted for`() {
        val id = hotel.createBooking("Walk In", "", roomId, today, tomorrow)
        hotel.checkIn(id)
        val folio = hotel.folio(id)
        assertEquals(120_000.0, folio.gross, 0.01)
        hotel.checkOut(id, extraPayment = folio.balance, method = "CASH")
        assertBalanced()
        assertEquals(0.0, hotel.folio(id).balance, 0.01)
        assertEquals(0.0, hotel.depositsHeld(), 0.01)

        // Revenue net of the 18% VAT already inside the 120,000 price.
        val net = hotel.revenueBreakdown(today, today).first { it.label.startsWith("Accommodation") }.amount
        assertEquals(101_694.92, net, 0.5)
    }

    @Test
    fun `the same room cannot be sold twice for the same nights`() {
        hotel.createBooking("First Guest", "0700", roomId, today, inThree)
        assertFailsWith<IllegalArgumentException> {
            hotel.createBooking("Second Guest", "0701", roomId, tomorrow, inThree)
        }
        // Back to back is fine: one guest out in the morning, the next in that afternoon.
        val backToBack = hotel.createBooking("Second Guest", "0701", roomId, inThree, Hotel.addDays(inThree, 1))
        assertEquals(Hotel.BOOKED, hotel.booking(backToBack)!!.str("status"))
        assertEquals(1, hotel.freeRooms(today, inThree).size)
    }

    @Test
    fun `leaving early drops the unused nights from the bill`() {
        val id = hotel.createBooking("Early Bird", "", roomId, today, inThree)
        hotel.checkIn(id)
        assertEquals(360_000.0, hotel.folio(id).gross, 0.01)
        hotel.trimStay(id, tomorrow)
        assertEquals(1, hotel.booking(id)!!.int("nights"))
        assertEquals(120_000.0, hotel.folio(id).gross, 0.01)
        hotel.checkOut(id, extraPayment = 120_000.0)
        assertBalanced()
    }

    @Test
    fun `extending a stay adds the extra nights and their rent`() {
        val id = hotel.createBooking("Long Stay", "", roomId, today, tomorrow)
        hotel.checkIn(id)
        hotel.extendStay(id, inThree)
        assertEquals(3, hotel.booking(id)!!.int("nights"))
        assertEquals(360_000.0, hotel.folio(id).gross, 0.01)
        assertEquals(3, hotel.charges(id).count { it.str("kind") == "ROOM" })
    }

    @Test
    fun `cancelling refunds the deposit and leaves no revenue behind`() {
        val id = hotel.createBooking("Cancelling Guest", "", roomId, today, inThree, deposit = 50_000.0)
        hotel.cancelBooking(id, refundDeposit = true)
        assertEquals(Hotel.CANCELLED, hotel.booking(id)!!.str("status"))
        assertEquals(0.0, balanceOf(Accounts.GUEST_DEPOSITS), 0.01)
        assertEquals(0.0, balanceOf(Accounts.ROOM_REVENUE), 0.01)
        assertBalanced()
    }

    @Test
    fun `a no-show keeps the deposit as revenue`() {
        val id = hotel.createBooking("No Show", "", roomId, today, inThree, deposit = 40_000.0)
        hotel.markNoShow(id)
        assertEquals(Hotel.NO_SHOW, hotel.booking(id)!!.str("status"))
        assertEquals(0.0, balanceOf(Accounts.GUEST_DEPOSITS), 0.01)
        assertTrue(balanceOf(Accounts.ROOM_REVENUE) < 0, "forfeited deposit is income")
        assertBalanced()
    }

    @Test
    fun `occupancy counts sold room nights and average rate`() {
        val a = hotel.createBooking("Guest A", "", roomId, today, inThree)
        hotel.checkIn(a)
        hotel.checkOut(a, extraPayment = 360_000.0)
        val occ = hotel.occupancy(today, inThree)
        assertEquals(3L, occ.roomNightsSold, "three nights sold")
        assertEquals(6L, occ.roomNightsAvailable, "two rooms x three nights")
        assertEquals(50.0, occ.occupancyPct, 0.01)
        assertEquals(101_694.92, occ.adr, 0.5)
        assertEquals(3, occ.byDay.size)
    }

    @Test
    fun `housekeeping marks a room out of order until the job is done`() {
        hotel.raiseTask(roomId, "MAINTENANCE", "Broken tap")
        assertEquals(Hotel.OUT_OF_ORDER, hotel.room(roomId)!!.str("status"))
        assertEquals(1, hotel.freeRooms(today, tomorrow).size, "out of order room is not sellable")
        hotel.completeTask(hotel.housekeepingTasks().first().str("id"))
        assertEquals(Hotel.VACANT_CLEAN, hotel.room(roomId)!!.str("status"))
    }

    @Test
    fun `a company account is billed on checkout and settles later`() {
        val id = hotel.createBooking("Ministry of Health", "0414", roomId, today, tomorrow, source = "CORPORATE")
        hotel.checkIn(id)
        hotel.checkOut(id, settleToDebtor = true)
        assertEquals(120_000.0, balanceOf(Accounts.DEBTORS), 0.01)

        val customerId = hotel.saveGuest(null, "Ministry of Health", "0414")
        assertTrue(customerId.isNotBlank())
        assertBalanced()
    }
}
