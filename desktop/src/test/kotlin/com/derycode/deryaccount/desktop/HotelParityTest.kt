package com.derycode.deryaccount.desktop

import com.derycode.deryaccount.desktop.data.Clock
import com.derycode.deryaccount.desktop.data.Hotel
import com.derycode.deryaccount.desktop.data.Repo
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Yanolja-parity work: rates, agents and the city ledger, night audit,
 * housekeeping and upsells. These run against a real SQLite file so a green run
 * means the calendar prices the rooms the way the desk quotes them, an agent's
 * debt cannot silently drift, and the night audit closes a day exactly once.
 */
class HotelParityTest {

    private lateinit var file: File
    private lateinit var repo: Repo
    private lateinit var hotel: Hotel

    private lateinit var typeId: String
    private lateinit var roomId: String

    private val today = Clock.today()
    private val tomorrow = Hotel.addDays(today, 1)

    @BeforeTest
    fun setUp() {
        file = File.createTempFile("deryaccount-parity-", ".db")
        file.delete()
        repo = Repo.open(file)
        hotel = Hotel(repo)
        repo.saveProfile(mapOf("name" to "Parity Lodge", "phone" to "0700000000"))
        typeId = hotel.saveRoomType(null, "Deluxe", 100_000.0, 2, "1 queen", taxRate = 18.0)
        roomId = hotel.saveRoom(null, "201", typeId, "2", Hotel.VACANT_CLEAN, "")
        hotel.saveRoom(null, "202", typeId, "2", Hotel.VACANT_CLEAN, "")
    }

    @AfterTest
    fun tearDown() {
        file.delete()
        file.delete()
    }

    // ── Rates ────────────────────────────────────────────────────────────────

    @Test
    fun `an override beats the plan, the plan beats the base rate`() {
        assertEquals(100_000.0, hotel.rateFor(typeId, today), "the bare room type rate")

        hotel.setRateOverride(typeId, today, 130_000.0)
        assertEquals(130_000.0, hotel.rateFor(typeId, today), "the night's own rate wins")

        hotel.saveRatePlan(null, "Weekend +20k", typeId, "EP", 20_000.0, "", "", minNights = 1)
        assertEquals(120_000.0, hotel.rateFor(typeId, tomorrow), "the plan applies where there is no override")
        assertEquals(130_000.0, hotel.rateFor(typeId, today), "the override still wins on its own night")

        hotel.setRateOverride(typeId, today, null)
        assertEquals(120_000.0, hotel.rateFor(typeId, today), "without an override the plan prices the night")
    }

    @Test
    fun `a weekend surcharge only touches Saturday and Sunday`() {
        val saturday = nextDayOfWeek(6)
        val monday = nextDayOfWeek(1)
        hotel.saveRatePlan(null, "Weekend", typeId, "EP", 0.0, "", "", weekendDelta = 25_000.0, minNights = 1)
        assertEquals(125_000.0, hotel.rateFor(typeId, saturday))
        assertEquals(100_000.0, hotel.rateFor(typeId, monday))
    }

    // ── Agents and the city ledger ────────────────────────────────────────────

    @Test
    fun `an agent booking carries its commission and can be invoiced and paid`() {
        val agentId = hotel.saveAgent(null, "Lake Tours", "TRAVEL_AGENT", "Moses", "0772000111", commissionPct = 10.0, creditLimit = 2_000_000.0)
        val bookingId = hotel.createBooking(
            guestName = "Amina K", guestPhone = "0700111222", roomId = roomId,
            checkIn = today, checkOut = tomorrow, rate = 100_000.0,
            source = "AGENT", agentName = "Lake Tours", groupName = "Lake group"
        )
        val b = hotel.booking(bookingId)!!
        assertEquals(agentId, b.str("agent_id"), "the booking remembers which agent sent the guest")
        assertEquals(10.0, b.dbl("commission_pct"))
        assertEquals("Lake group", b.str("group_name"))

        hotel.checkIn(bookingId)
        hotel.checkOut(bookingId, settleToDebtor = true)

        val invoiceId = hotel.createAgentInvoice(agentId, Hotel.addDays(today, -1), Hotel.addDays(today, 5))
        assertTrue(invoiceId.isNotBlank(), "an invoice is raised for the stay")
        val invoice = hotel.agentInvoices(agentId).first()
        assertTrue(invoice.dbl("amount") > 0, "the invoice carries the stay value")

        hotel.payAgentInvoice(invoiceId, invoice.dbl("amount"), note = "Settled in full")
        val paid = hotel.agentInvoices(agentId).first()
        assertEquals(0.0, paid.dbl("amount") - paid.dbl("paid"), "nothing outstanding after payment")
        assertEquals(0.0, hotel.agentAgeing().total, "the city ledger clears")
    }

    @Test
    fun `ageing buckets the outstanding agent money by invoice age`() {
        val agentId = hotel.saveAgent(null, "Corporate Ltd", "CORPORATE", "", "0700999888", commissionPct = 0.0)
        repo.db.insert(
            "agent_invoices",
            mapOf(
                "id" to "inv-old", "agent_id" to agentId,
                "period_from" to Hotel.addDays(today, -100), "period_to" to Hotel.addDays(today, -90),
                "amount" to 500_000.0, "paid" to 0.0, "status" to "OPEN",
                "due_date" to Hotel.addDays(today, -90), "created_at" to Clock.nowIso()
            )
        )
        val ageing = hotel.agentAgeing()
        assertEquals(500_000.0, ageing.d90, "a 90-day-old invoice sits in the last bucket")
        assertEquals(500_000.0, ageing.total)
    }

    // ── Night audit ──────────────────────────────────────────────────────────

    @Test
    fun `the night audit counts the day, flags no-shows and cannot run twice`() {
        val stayed = hotel.createBooking(
            guestName = "John S", guestPhone = "0700000001", roomId = roomId,
            checkIn = today, checkOut = tomorrow, rate = 100_000.0
        )
        val noShowId = hotel.createBooking(
            guestName = "Grace N", guestPhone = "0700000002", roomId = hotel.rooms().first { it.str("number") == "202" }.str("id"),
            checkIn = today, checkOut = tomorrow, rate = 100_000.0
        )
        hotel.checkIn(stayed)

        val summary = hotel.auditSummary(today)
        assertEquals(2, summary.roomsAvailable)
        assertEquals(1, summary.roomsSold)
        assertEquals(50.0, summary.occupancy)
        assertEquals(1, summary.noShows, "a booking that never arrived is a no-show")

        val auditId = hotel.closeNightAudit(today, userId = "Owner")
        assertTrue(auditId.isNotBlank())
        assertEquals(Hotel.NO_SHOW, hotel.booking(noShowId)!!.str("status"), "the audit sweeps no-shows")

        assertFailsWith<IllegalStateException>("a closed day cannot be closed again") {
            hotel.closeNightAudit(today, userId = "Owner")
        }
        assertEquals(Hotel.addDays(today, 1), hotel.businessDate(), "the business date moves on")
    }

    // ── Housekeeping, maintenance and upsells ─────────────────────────────────

    @Test
    fun `housekeeping builds today's list from tomorrow's departures`() {
        val bookingId = hotel.createBooking(
            guestName = "Peter M", guestPhone = "0700000003", roomId = roomId,
            checkIn = today, checkOut = tomorrow, rate = 100_000.0
        )
        hotel.checkIn(bookingId)
        val made = hotel.buildHousekeepingList(tomorrow)
        assertTrue(made >= 1, "the departing room gets a cleaning task")
        assertTrue(hotel.housekeepingTasks(true).any { it.str("room_id") == roomId })
        assertEquals(0, hotel.buildHousekeepingList(), "running it twice does not duplicate work")
    }

    @Test
    fun `a repair ticket takes the room off the board and releasing it puts it back`() {
        hotel.raiseMaintenance(roomId, "Air conditioner not cooling", "HIGH")
        assertEquals(Hotel.OUT_OF_ORDER, hotel.room(roomId)!!.str("status"), "a repair takes the room off the board")
        val ticket = hotel.maintenanceTickets(true).first { it.str("room_id") == roomId }
        hotel.closeMaintenance(ticket.str("id"), note = "Compressor replaced")
        assertEquals(Hotel.VACANT_DIRTY, hotel.room(roomId)!!.str("status"))
    }

    @Test
    fun `an upsell lands on the folio and in the room's revenue`() {
        val bookingId = hotel.createBooking(
            guestName = "Sarah T", guestPhone = "0700000004", roomId = roomId,
            checkIn = today, checkOut = tomorrow, rate = 100_000.0
        )
        hotel.checkIn(bookingId)
        val breakfast = hotel.UPSELLS.first { it.label == "Breakfast" }
        hotel.addUpsell(bookingId, breakfast)
        val folio = hotel.folio(bookingId)
        assertTrue(folio.gross > 100_000.0, "the breakfast is on the bill")
        assertTrue(hotel.charges(bookingId).any { it.str("description") == "Breakfast" })
    }

    @Test
    fun `the availability window only returns live bookings that touch it`() {
        val booked = hotel.createBooking(
            guestName = "Moses A", guestPhone = "0700000005", roomId = roomId,
            checkIn = tomorrow, checkOut = Hotel.addDays(today, 3), rate = 100_000.0
        )
        hotel.createBooking(
            guestName = "Old Guest", guestPhone = "0700000006", roomId = hotel.rooms().first { it.str("number") == "202" }.str("id"),
            checkIn = Hotel.addDays(today, 10), checkOut = Hotel.addDays(today, 12), rate = 100_000.0
        )
        val live = hotel.bookingsBetween(today, Hotel.addDays(today, 5))
        assertEquals(1, live.size)
        assertEquals(booked, live.first().str("id"))
        assertFalse(live.any { it.str("guest_name") == "Old Guest" })
    }

    private fun nextDayOfWeek(target: Int): String {
        var d = java.time.LocalDate.parse(today)
        while (d.dayOfWeek.value != target) d = d.plusDays(1)
        return d.toString()
    }
}
