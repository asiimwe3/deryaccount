package com.derycode.deryaccount.desktop.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Hotel & accommodation: rooms, bookings, guest folios, housekeeping and the
 * numbers a front desk lives by (occupancy, ADR, RevPAR).
 *
 * Money only reaches the books through the ledger, and always balanced:
 *   deposit / part payment   Dr cash or mobile money   Cr guest deposits (held)
 *   check-out                Dr guest deposits + debtors + discounts
 *                            Cr room revenue + F&B revenue + other guest services + VAT
 * Revenue is recognised when the guest checks out, so a held deposit is never
 * counted as income, and an unpaid folio lands in debtors.
 */
class Hotel(private val repo: Repo) {

    private val db: DatabaseShop = repo.db
    private val acc: Accounting = repo.accounting

    companion object {
        const val VACANT_CLEAN = "VACANT_CLEAN"
        const val VACANT_DIRTY = "VACANT_DIRTY"
        const val OCCUPIED = "OCCUPIED"
        const val OUT_OF_ORDER = "OUT_OF_ORDER"

        const val BOOKED = "BOOKED"
        const val CHECKED_IN = "CHECKED_IN"
        const val CHECKED_OUT = "CHECKED_OUT"
        const val CANCELLED = "CANCELLED"
        const val NO_SHOW = "NO_SHOW"

        val OPEN_STATUSES = listOf(BOOKED, CHECKED_IN)
        val CHARGE_KINDS = listOf("ROOM", "RESTAURANT", "BAR", "LAUNDRY", "EXTRA", "DISCOUNT")
        val SOURCES = listOf("WALK_IN", "PHONE", "ONLINE", "AGENT", "CORPORATE")

        fun nights(from: String, to: String): Long = runCatching {
            ChronoUnit.DAYS.between(LocalDate.parse(from.take(10)), LocalDate.parse(to.take(10)))
        }.getOrDefault(0L)

        fun addDays(date: String, days: Long): String =
            runCatching { LocalDate.parse(date.take(10)).plusDays(days).toString() }.getOrDefault(date)

        fun kindLabel(kind: String): String = when (kind.uppercase()) {
            "ROOM" -> "Accommodation"
            "RESTAURANT" -> "Restaurant"
            "BAR" -> "Bar"
            "LAUNDRY" -> "Laundry"
            "EXTRA" -> "Extra service"
            "DISCOUNT" -> "Discount"
            else -> kind.lowercase().replaceFirstChar { it.uppercase() }
        }

        fun statusLabel(status: String): String = when (status) {
            VACANT_CLEAN -> "Vacant · clean"
            VACANT_DIRTY -> "Vacant · dirty"
            OCCUPIED -> "Occupied"
            OUT_OF_ORDER -> "Out of order"
            else -> status
        }

        fun bookingLabel(status: String): String = when (status) {
            BOOKED -> "Booked"
            CHECKED_IN -> "In house"
            CHECKED_OUT -> "Checked out"
            CANCELLED -> "Cancelled"
            NO_SHOW -> "No show"
            else -> status
        }

        /** Charge kind → the income account it belongs to. */
        fun accountFor(kind: String): String = when (kind.uppercase()) {
            "ROOM" -> Accounts.ROOM_REVENUE
            "RESTAURANT", "BAR" -> Accounts.FB_REVENUE
            else -> Accounts.GUEST_REVENUE
        }
    }

    // ── Room types ───────────────────────────────────────────────────────────
    fun roomTypes(): List<Row> = db.q("SELECT * FROM room_types ORDER BY base_rate DESC, name")

    fun roomType(id: String): Row? = db.one("SELECT * FROM room_types WHERE id=?", id)

    fun saveRoomType(
        id: String?, name: String, baseRate: Double, capacity: Int, beds: String,
        description: String = "", taxRate: Double = 0.0
    ): String {
        require(name.isNotBlank()) { "Give the room type a name" }
        require(baseRate >= 0) { "Rate cannot be negative" }
        val values = mapOf(
            "name" to name.trim(), "base_rate" to Fmt.r2(baseRate), "capacity" to capacity,
            "beds" to beds, "description" to description, "tax_rate" to taxRate
        )
        return db.tx {
            if (id == null) db.insert("room_types", values + mapOf("id" to UUID.randomUUID().toString(), "created_at" to Clock.nowIso()))
            else { db.update("room_types", id, values); id }
        }
    }

    // ── Rooms ────────────────────────────────────────────────────────────────
    fun rooms(): List<Row> = db.q(
        """SELECT r.*, t.name AS type_name, t.base_rate AS type_rate, t.capacity AS type_capacity,
                  t.beds AS type_beds, t.tax_rate AS type_tax
           FROM rooms r LEFT JOIN room_types t ON t.id = r.room_type_id
           WHERE r.is_active=1
           ORDER BY CAST(r.number AS INTEGER), r.number"""
    )

    fun room(id: String): Row? = db.one(
        """SELECT r.*, t.name AS type_name, t.base_rate AS type_rate, t.capacity AS type_capacity,
                  t.beds AS type_beds, t.tax_rate AS type_tax
           FROM rooms r LEFT JOIN room_types t ON t.id = r.room_type_id WHERE r.id=?""", id
    )

    fun saveRoom(id: String?, number: String, roomTypeId: String?, floor: String, status: String, note: String, rateOverride: Double = 0.0): String {
        require(number.isNotBlank()) { "Every room needs a number or name" }
        require(roomTypeId != null) { "Pick a room type first" }
        val clash = db.one("SELECT id FROM rooms WHERE lower(number)=lower(?) AND is_active=1 AND id<>?", number.trim(), id ?: "")
        require(clash == null) { "Room $number already exists" }
        val values = mapOf(
            "number" to number.trim(), "room_type_id" to roomTypeId, "floor" to floor,
            "status" to status, "note" to note, "rate_override" to Fmt.r2(rateOverride),
            "branch_id" to repo.branchId(), "is_active" to 1, "updated_at" to Clock.nowIso()
        )
        return db.tx {
            if (id == null) db.insert("rooms", values + mapOf("id" to UUID.randomUUID().toString(), "created_at" to Clock.nowIso()))
            else { db.update("rooms", id, values); id }
        }
    }

    fun setRoomStatus(roomId: String, status: String) =
        db.update("rooms", roomId, mapOf("status" to status, "updated_at" to Clock.nowIso()))

    fun retireRoom(roomId: String) {
        val live = db.count("bookings", "room_id=? AND status IN ('BOOKED','CHECKED_IN')", roomId)
        require(live == 0) { "This room still has an open booking" }
        db.update("rooms", roomId, mapOf("is_active" to 0, "updated_at" to Clock.nowIso()))
    }

    fun roomRate(room: Row): Double {
        val override = room.dbl("rate_override")
        return if (override > 0) override else room.dbl("type_rate")
    }

    fun roomTaxRate(room: Row): Double = room.dbl("type_tax")

    // ── Guests ───────────────────────────────────────────────────────────────
    fun guests(search: String = ""): List<Row> {
        val like = "%${search.trim()}%"
        return if (search.isBlank()) db.q("SELECT * FROM guests ORDER BY name")
        else db.q("SELECT * FROM guests WHERE name LIKE ? OR phone LIKE ? OR id_number LIKE ? ORDER BY name", like, like, like)
    }

    fun guest(id: String): Row? = db.one("SELECT * FROM guests WHERE id=?", id)

    fun saveGuest(id: String?, name: String, phone: String, email: String = "", idNumber: String = "",
                  nationality: String = "", address: String = "", notes: String = ""): String {
        require(name.isNotBlank()) { "Guest name is required" }
        val values = mapOf(
            "name" to name.trim(), "phone" to phone.trim(), "email" to email.trim(),
            "id_number" to idNumber.trim(), "nationality" to nationality.trim(),
            "address" to address.trim(), "notes" to notes.trim(), "updated_at" to Clock.nowIso()
        )
        return db.tx {
            if (id == null) db.insert("guests", values + mapOf("id" to UUID.randomUUID().toString(), "created_at" to Clock.nowIso()))
            else { db.update("guests", id, values); id }
        }
    }

    fun guestStays(guestId: String): List<Row> =
        db.q("SELECT * FROM bookings WHERE guest_id=? ORDER BY check_in_date DESC", guestId)

    // ── Availability ─────────────────────────────────────────────────────────
    /** True when no open booking covers any night between the two dates. */
    fun isRoomFree(roomId: String, from: String, to: String, excludeBookingId: String? = null): Boolean {
        if (nights(from, to) <= 0) return false
        val clash = db.one(
            """SELECT id FROM bookings
               WHERE room_id=? AND status IN ('BOOKED','CHECKED_IN') AND id<>?
                 AND check_in_date < ? AND check_out_date > ? LIMIT 1""",
            roomId, excludeBookingId ?: "", to.take(10), from.take(10)
        )
        return clash == null
    }

    fun freeRooms(from: String, to: String, excludeBookingId: String? = null): List<Row> =
        rooms().filter { it.str("status") != OUT_OF_ORDER && isRoomFree(it.str("id"), from, to, excludeBookingId) }

    // ── Bookings ─────────────────────────────────────────────────────────────
    fun nextRef(): String {
        val n = db.count("bookings") + 1
        return String.format("BK-%05d", n)
    }

    fun bookings(status: String = "OPEN", search: String = ""): List<Row> {
        val where = when (status) {
            "OPEN" -> "b.status IN ('BOOKED','CHECKED_IN')"
            "ALL" -> "1=1"
            else -> "b.status='${status.replace("'", "")}'"
        }
        val like = "%${search.trim()}%"
        return db.q(
            """SELECT b.*, r.number AS room_number, t.name AS type_name,
                      (SELECT COALESCE(SUM(amount),0) FROM folio_charges c WHERE c.booking_id=b.id) AS charges,
                      (SELECT COALESCE(SUM(amount),0) FROM hotel_payments p WHERE p.booking_id=b.id) AS paid
               FROM bookings b
               LEFT JOIN rooms r ON r.id=b.room_id
               LEFT JOIN room_types t ON t.id=b.room_type_id
               WHERE $where ${if (search.isBlank()) "" else "AND (b.guest_name LIKE ? OR b.guest_phone LIKE ? OR b.ref_no LIKE ? OR r.number LIKE ?)"}
               ORDER BY CASE b.status WHEN 'CHECKED_IN' THEN 0 WHEN 'BOOKED' THEN 1 ELSE 2 END,
                        b.check_in_date, r.number""",
            *if (search.isBlank()) emptyArray() else arrayOf(like, like, like, like)
        )
    }

    fun booking(id: String): Row? = db.one(
        """SELECT b.*, r.number AS room_number, t.name AS type_name, t.tax_rate AS type_tax
           FROM bookings b LEFT JOIN rooms r ON r.id=b.room_id LEFT JOIN room_types t ON t.id=b.room_type_id
           WHERE b.id=?""", id
    )

    fun charges(bookingId: String): List<Row> =
        db.q("SELECT * FROM folio_charges WHERE booking_id=? ORDER BY charge_date, posted_at", bookingId)

    fun payments(bookingId: String): List<Row> =
        db.q("SELECT * FROM hotel_payments WHERE booking_id=? ORDER BY paid_at", bookingId)
    /** The hotel's own profile row, for receipts and invoices. */
    fun profileRow(): Row = repo.profile()

    /** Every guest payment, newest first — this is the receipts register. */
    fun allPayments(limit: Int = 400): List<Row> = db.q(
        """SELECT p.*, b.ref_no, b.guest_name, b.room_id, r.number AS room_number, b.status AS booking_status
           FROM hotel_payments p
           JOIN bookings b ON b.id=p.booking_id
           LEFT JOIN rooms r ON r.id=b.room_id
           ORDER BY p.paid_at DESC LIMIT ?""", limit
    )

    /** Payments taken between two dates (inclusive), for a shift or a day close. */
    fun paymentsBetween(from: String, to: String): List<Row> = db.q(
        """SELECT p.*, b.ref_no, b.guest_name, r.number AS room_number
           FROM hotel_payments p
           JOIN bookings b ON b.id=p.booking_id
           LEFT JOIN rooms r ON r.id=b.room_id
           WHERE substr(p.paid_at,1,10) >= ? AND substr(p.paid_at,1,10) <= ?
           ORDER BY p.paid_at DESC""", from, to
    )


    data class Folio(
        val gross: Double, val tax: Double, val paid: Double, val balance: Double,
        val byKind: Map<String, Double>
    )

    fun folio(bookingId: String): Folio {
        val rows = charges(bookingId)
        val gross = Fmt.r2(rows.filter { it.str("kind") != "DISCOUNT" }.sumOf { it.dbl("amount") })
        val discount = Fmt.r2(rows.filter { it.str("kind") == "DISCOUNT" }.sumOf { it.dbl("amount") })
        val tax = Fmt.r2(rows.filter { it.str("kind") != "DISCOUNT" }.sumOf { it.dbl("tax_amount") })
        val paid = Fmt.r2(payments(bookingId).sumOf { it.dbl("amount") })
        val total = Fmt.r2(gross - discount)
        val byKind = rows.filter { it.str("kind") != "DISCOUNT" }
            .groupBy { it.str("kind") }
            .mapValues { e -> Fmt.r2(e.value.sumOf { it.dbl("amount") }) }
        return Folio(gross = total, tax = tax, paid = paid, balance = Fmt.r2(total - paid), byKind = byKind)
    }

    fun createBooking(
        guestName: String, guestPhone: String, roomId: String, checkIn: String, checkOut: String,
        adults: Int = 1, children: Int = 0, rate: Double? = null, source: String = "WALK_IN",
        notes: String = "", deposit: Double = 0.0, depositMethod: String = "CASH",
        guestId: String? = null, userId: String = "u-owner",
        agentName: String = "", groupName: String = "", commissionPct: Double? = null
    ): String {
        require(guestName.isNotBlank()) { "Who is the guest?" }
        val room = room(roomId) ?: error("Pick a room")
        val n = nights(checkIn, checkOut)
        require(n > 0) { "Check-out must be after check-in" }
        require(isRoomFree(roomId, checkIn, checkOut)) { "Room ${room.str("number")} is already taken for those dates" }
        val nightRate = Fmt.r2(rate ?: roomRate(room))
        require(nightRate >= 0) { "Rate cannot be negative" }
        val id = UUID.randomUUID().toString()
        val now = Clock.nowIso()
        db.tx {
            db.insert("bookings", mapOf(
                "id" to id, "ref_no" to nextRef(), "guest_id" to guestId, "guest_name" to guestName.trim(),
                "guest_phone" to guestPhone.trim(), "room_id" to roomId, "room_type_id" to room.str("room_type_id"),
                "check_in_date" to checkIn.take(10), "check_out_date" to checkOut.take(10),
                "adults" to adults, "children" to children, "nights" to n.toInt(), "rate" to nightRate,
                "status" to BOOKED, "source" to source, "notes" to notes,
                "agent_id" to (agents().firstOrNull { it.str("name").equals(agentName.trim(), true) }?.str("id") ?: ""),
                "group_name" to groupName.trim(),
                "invoice_total" to Fmt.r2(nightRate * n), "paid_total" to 0.0,
                "user_id" to userId, "branch_id" to repo.branchId(),
                "created_at" to now, "updated_at" to now
            ))
            val namedAgent = agents().firstOrNull { it.str("name").equals(agentName.trim(), true) }
            if (namedAgent != null) {
                val pct = commissionPct ?: namedAgent.dbl("commission_pct")
                db.update("bookings", id, mapOf("agent_name" to namedAgent.str("name"), "commission_pct" to pct))
            }
            if (deposit > 0) postPaymentInternal(id, deposit, depositMethod, "Deposit at booking", userId)
        }
        return id
    }

    /** Walk-in with no reservation: book and check in in one action. */
    fun walkIn(
        guestName: String, guestPhone: String, roomId: String, checkOut: String,
        adults: Int = 1, children: Int = 0, rate: Double? = null, notes: String = "",
        deposit: Double = 0.0, depositMethod: String = "CASH", userId: String = "u-owner"
    ): String {
        val id = createBooking(
            guestName, guestPhone, roomId, Clock.today(), checkOut, adults, children,
            rate, "WALK_IN", notes, deposit, depositMethod, userId = userId
        )
        checkIn(id, userId)
        return id
    }

    fun checkIn(bookingId: String, userId: String = "u-owner") {
        val b = booking(bookingId) ?: error("Booking not found")
        require(b.str("status") == BOOKED) { "Only a booked reservation can be checked in" }
        val roomId = b.str("room_id")
        if (!isRoomFree(roomId, b.str("check_in_date"), b.str("check_out_date"), bookingId))
            throw IllegalStateException("Room ${b.str("room_number")} has another guest for those dates")
        val now = Clock.nowIso()
        db.tx {
            db.update("bookings", bookingId, mapOf("status" to CHECKED_IN, "checked_in_at" to now, "updated_at" to now))
            db.update("rooms", roomId, mapOf("status" to OCCUPIED, "updated_at" to now))
            postRoomCharges(bookingId, b, userId)
        }
    }

    /** One ROOM line per night, so the folio reads like a hotel bill. */
    private fun postRoomCharges(bookingId: String, b: Row, userId: String) {
        val room = room(b.str("room_id"))
        val taxRate = room?.let { roomTaxRate(it) } ?: 0.0
        val rate = b.dbl("rate")
        val tax = if (taxRate > 0) Fmt.r2(rate * taxRate / (100.0 + taxRate)) else 0.0
        val nightsCount = nights(b.str("check_in_date"), b.str("check_out_date"))
        val already = charges(bookingId).filter { it.str("kind") == "ROOM" }.map { it.str("charge_date") }.toSet()
        for (i in 0 until nightsCount) {
            val date = addDays(b.str("check_in_date"), i)
            if (date in already) continue
            db.insert("folio_charges", mapOf(
                "id" to UUID.randomUUID().toString(), "booking_id" to bookingId, "kind" to "ROOM",
                "description" to "Room ${room?.str("number") ?: ""} · night ${i + 1}", "qty" to 1.0,
                "unit_price" to rate, "amount" to rate, "tax_amount" to tax,
                "charge_date" to date, "posted_at" to Clock.nowIso(), "posted_by" to userId,
                "created_at" to Clock.nowIso()
            ))
        }
        refreshTotals(bookingId)
    }

    fun addCharge(
        bookingId: String, kind: String, description: String, qty: Double, unitPrice: Double,
        taxRate: Double = 0.0, userId: String = "u-owner"
    ) {
        require(qty > 0) { "Quantity must be more than zero" }
        require(unitPrice > 0) { "Amount must be more than zero" }
        val amount = Fmt.r2(qty * unitPrice)
        val tax = if (taxRate > 0) Fmt.r2(amount * taxRate / (100.0 + taxRate)) else 0.0
        val net = if (kind == "DISCOUNT") -amount else amount
        db.tx {
            db.insert("folio_charges", mapOf(
                "id" to UUID.randomUUID().toString(), "booking_id" to bookingId, "kind" to kind.uppercase(),
                "description" to description.ifBlank { kindLabel(kind) }, "qty" to qty,
                "unit_price" to unitPrice, "amount" to net, "tax_amount" to if (kind == "DISCOUNT") 0.0 else tax,
                "charge_date" to Clock.today(), "posted_at" to Clock.nowIso(), "posted_by" to userId,
                "created_at" to Clock.nowIso()
            ))
            refreshTotals(bookingId)
        }
    }

    fun removeCharge(chargeId: String) {
        val c = db.one("SELECT * FROM folio_charges WHERE id=?", chargeId) ?: return
        val b = booking(c.str("booking_id")) ?: return
        require(b.str("status") != CHECKED_OUT) { "This folio is already closed" }
        db.tx {
            db.delete("folio_charges", chargeId)
            refreshTotals(c.str("booking_id"))
        }
    }

    /** Shorten the stay: drop the room nights after the new check-out date. */
    fun trimStay(bookingId: String, newCheckOut: String) {
        val b = booking(bookingId) ?: error("Booking not found")
        require(b.str("status") == CHECKED_IN || b.str("status") == BOOKED) { "This booking is already closed" }
        val newNights = nights(b.str("check_in_date"), newCheckOut)
        require(newNights > 0) { "Check-out must be after check-in" }
        db.tx {
            db.q(
                "SELECT id FROM folio_charges WHERE booking_id=? AND kind='ROOM' AND charge_date >= ?",
                bookingId, newCheckOut.take(10)
            ).forEach { db.delete("folio_charges", it.str("id")) }
            db.update("bookings", bookingId, mapOf(
                "check_out_date" to newCheckOut.take(10), "nights" to newNights.toInt(), "updated_at" to Clock.nowIso()
            ))
            refreshTotals(bookingId)
        }
    }

    /** Push the check-out date out and add the extra room nights. */
    fun extendStay(bookingId: String, newCheckOut: String, userId: String = "u-owner") {
        val b = booking(bookingId) ?: error("Booking not found")
        require(b.str("status") == CHECKED_IN || b.str("status") == BOOKED) { "This booking is already closed" }
        val newNights = nights(b.str("check_in_date"), newCheckOut)
        require(newNights >= b.int("nights")) { "New check-out must be later than the current one" }
        require(isRoomFree(b.str("room_id"), b.str("check_in_date"), newCheckOut, bookingId)) {
            "Room ${b.str("room_number")} is booked for someone else on those dates"
        }
        db.tx {
            db.update("bookings", bookingId, mapOf(
                "check_out_date" to newCheckOut.take(10), "nights" to newNights.toInt(), "updated_at" to Clock.nowIso()
            ))
            postRoomCharges(bookingId, booking(bookingId) ?: b, userId)
            refreshTotals(bookingId)
        }
    }

    fun moveRoom(bookingId: String, newRoomId: String) {
        val b = booking(bookingId) ?: error("Booking not found")
        require(b.str("status") != CHECKED_OUT) { "This booking is already closed" }
        require(isRoomFree(newRoomId, Clock.today(), b.str("check_out_date"), bookingId)) {
            "That room is not free for the rest of this stay"
        }
        val newRoom = room(newRoomId) ?: error("Room not found")
        val oldRoomId = b.str("room_id")
        db.tx {
            db.update("bookings", bookingId, mapOf(
                "room_id" to newRoomId, "room_type_id" to newRoom.str("room_type_id"),
                "rate" to roomRate(newRoom), "updated_at" to Clock.nowIso()
            ))
            if (b.str("status") == CHECKED_IN) {
                db.update("rooms", newRoomId, mapOf("status" to OCCUPIED, "updated_at" to Clock.nowIso()))
                db.update("rooms", oldRoomId, mapOf("status" to VACANT_DIRTY, "updated_at" to Clock.nowIso()))
            }
        }
    }

    // ── Money ────────────────────────────────────────────────────────────────
    fun postPayment(bookingId: String, amount: Double, method: String, note: String = "", userId: String = "u-owner") {
        require(amount > 0) { "Amount must be more than zero" }
        db.tx { postPaymentInternal(bookingId, amount, method, note, userId) }
    }

    private fun postPaymentInternal(bookingId: String, amount: Double, method: String, note: String, userId: String) {
        val b = booking(bookingId) ?: error("Booking not found")
        val value = Fmt.r2(amount)
        db.insert("hotel_payments", mapOf(
            "id" to UUID.randomUUID().toString(), "booking_id" to bookingId, "amount" to value,
            "method" to method.uppercase(), "reference" to b.str("ref_no"), "note" to note,
            "paid_at" to Clock.nowIso(), "user_id" to userId, "created_at" to Clock.nowIso()
        ))
        acc.post(
            particulars = "Guest payment — ${b.str("guest_name")} (${b.str("ref_no")})",
            source = "HOTEL",
            debits = listOf(Accounts.accountForMethod(method) to value),
            credits = listOf(Accounts.GUEST_DEPOSITS to value)
        )
        refreshTotals(bookingId)
    }

    private fun refreshTotals(bookingId: String) {
        val f = folio(bookingId)
        db.update("bookings", bookingId, mapOf(
            "invoice_total" to f.gross, "paid_total" to f.paid, "updated_at" to Clock.nowIso()
        ))
    }

    /**
     * Close the folio: recognise revenue, clear the held deposit, and either
     * collect the balance or post it to debtors (a company account).
     */
    fun checkOut(
        bookingId: String, settleToDebtor: Boolean = false,
        extraPayment: Double = 0.0, method: String = "CASH", userId: String = "u-owner"
    ): Folio {
        val b = booking(bookingId) ?: error("Booking not found")
        require(b.str("status") == CHECKED_IN) { "Only an in-house guest can check out" }
        return db.tx {
            if (extraPayment > 0) postPaymentInternal(bookingId, extraPayment, method, "Balance on check-out", userId)
            val f = folio(bookingId)
            require(f.gross > 0) { "This folio has nothing on it" }
            if (f.balance > 0.5 && !settleToDebtor) throw IllegalStateException(
                "Collect ${Fmt.money(f.balance)} first, or put it on a company account"
            )
            val discounts = Fmt.r2(charges(bookingId).filter { it.str("kind") == "DISCOUNT" }.sumOf { -it.dbl("amount") })
            val debits = mutableListOf<Pair<String, Double>>()
            debits.add(Accounts.GUEST_DEPOSITS to Fmt.r2(f.paid))
            if (f.balance > 0) debits.add(Accounts.DEBTORS to Fmt.r2(f.balance))
            if (discounts > 0) debits.add(Accounts.SALES_DISCOUNT to discounts)

            val credits = mutableListOf<Pair<String, Double>>()
            f.byKind.forEach { (kind, gross) ->
                val taxPart = Fmt.r2(charges(bookingId).filter { it.str("kind") == kind }.sumOf { it.dbl("tax_amount") })
                val net = Fmt.r2(gross - taxPart)
                if (net > 0) credits.add(accountFor(kind) to net)
            }
            if (f.tax > 0) credits.add(Accounts.VAT_OUT to f.tax)
            if (f.balance < 0) credits.add(Accounts.CASH to Fmt.r2(-f.balance)) // refund owed to the guest

            acc.post(
                particulars = "Hotel folio ${b.str("ref_no")} — ${b.str("guest_name")} (room ${b.str("room_number")})",
                source = "HOTEL", debits = debits.filter { it.second > 0 }, credits = credits.filter { it.second > 0 }
            )
            val now = Clock.nowIso()
            db.update("bookings", bookingId, mapOf(
                "status" to CHECKED_OUT, "checked_out_at" to now,
                "invoice_total" to f.gross, "paid_total" to f.paid, "updated_at" to now
            ))
            db.update("rooms", b.str("room_id"), mapOf("status" to VACANT_DIRTY, "updated_at" to now))
            f
        }
    }

    fun cancelBooking(bookingId: String, refundDeposit: Boolean = true) {
        val b = booking(bookingId) ?: error("Booking not found")
        require(b.str("status") == BOOKED) { "Only an un-arrived booking can be cancelled" }
        db.tx {
            val paid = Fmt.r2(payments(bookingId).sumOf { it.dbl("amount") })
            if (paid > 0 && refundDeposit) {
                acc.post(
                    particulars = "Deposit refunded — ${b.str("guest_name")} (${b.str("ref_no")})",
                    source = "HOTEL",
                    debits = listOf(Accounts.GUEST_DEPOSITS to paid),
                    credits = listOf(Accounts.CASH to paid)
                )
            } else if (paid > 0) {
                acc.post(
                    particulars = "Deposit forfeited — ${b.str("guest_name")} (${b.str("ref_no")})",
                    source = "HOTEL",
                    debits = listOf(Accounts.GUEST_DEPOSITS to paid),
                    credits = listOf(Accounts.ROOM_REVENUE to paid)
                )
            }
            db.update("bookings", bookingId, mapOf("status" to CANCELLED, "updated_at" to Clock.nowIso()))
        }
    }

    fun markNoShow(bookingId: String) {
        val b = booking(bookingId) ?: error("Booking not found")
        require(b.str("status") == BOOKED) { "Only an un-arrived booking can be marked no-show" }
        db.tx {
            val paid = Fmt.r2(payments(bookingId).sumOf { it.dbl("amount") })
            if (paid > 0) acc.post(
                particulars = "No-show, deposit kept — ${b.str("guest_name")} (${b.str("ref_no")})",
                source = "HOTEL",
                debits = listOf(Accounts.GUEST_DEPOSITS to paid),
                credits = listOf(Accounts.ROOM_REVENUE to paid)
            )
            db.update("bookings", bookingId, mapOf("status" to NO_SHOW, "updated_at" to Clock.nowIso()))
        }
    }

    // ── Housekeeping ─────────────────────────────────────────────────────────
    fun housekeepingTasks(openOnly: Boolean = true): List<Row> = db.q(
        """SELECT h.*, r.number AS room_number FROM housekeeping_tasks h
           LEFT JOIN rooms r ON r.id=h.room_id
           ${if (openOnly) "WHERE h.status='OPEN'" else ""}
           ORDER BY h.created_at DESC"""
    )

    fun completeTask(taskId: String) {
        val t = db.one("SELECT * FROM housekeeping_tasks WHERE id=?", taskId) ?: return
        db.tx {
            db.update("housekeeping_tasks", taskId, mapOf("status" to "DONE", "done_at" to Clock.nowIso()))
            val room = room(t.str("room_id"))
            if (room != null && room.str("status") == OUT_OF_ORDER) setRoomStatus(room.str("id"), VACANT_CLEAN)
            else if (room != null && room.str("status") == VACANT_DIRTY && t.str("kind") != "MAINTENANCE")
                setRoomStatus(room.str("id"), VACANT_CLEAN)
        }
    }

    // ── Front desk lists ─────────────────────────────────────────────────────
    fun arrivalsToday(): List<Row> = db.q(
        """SELECT b.*, r.number AS room_number, t.name AS type_name FROM bookings b
           LEFT JOIN rooms r ON r.id=b.room_id LEFT JOIN room_types t ON t.id=b.room_type_id
           WHERE b.status='BOOKED' AND b.check_in_date<=? ORDER BY r.number""", Clock.today()
    )

    fun departuresOn(date: String): List<Row> = db.q(
        """SELECT b.*, r.number AS room_number FROM bookings b LEFT JOIN rooms r ON r.id=b.room_id
           WHERE b.check_out_date=? AND b.status IN ('BOOKED','CHECKED_IN') ORDER BY r.number""", date.take(10)
    )

    fun departuresToday(): List<Row> = db.q(
        """SELECT b.*, r.number AS room_number, t.name AS type_name,
                  (SELECT COALESCE(SUM(amount),0) FROM folio_charges c WHERE c.booking_id=b.id) AS charges,
                  (SELECT COALESCE(SUM(amount),0) FROM hotel_payments p WHERE p.booking_id=b.id) AS paid
           FROM bookings b LEFT JOIN rooms r ON r.id=b.room_id LEFT JOIN room_types t ON t.id=b.room_type_id
           WHERE b.status='CHECKED_IN' AND b.check_out_date<=? ORDER BY r.number""", Clock.today()
    )

    fun inHouse(): List<Row> = db.q(
        """SELECT b.*, r.number AS room_number, t.name AS type_name,
                  (SELECT COALESCE(SUM(amount),0) FROM folio_charges c WHERE c.booking_id=b.id) AS charges,
                  (SELECT COALESCE(SUM(amount),0) FROM hotel_payments p WHERE p.booking_id=b.id) AS paid
           FROM bookings b LEFT JOIN rooms r ON r.id=b.room_id LEFT JOIN room_types t ON t.id=b.room_type_id
           WHERE b.status='CHECKED_IN' ORDER BY r.number"""
    )

    // ── Reports ──────────────────────────────────────────────────────────────
    data class Occupancy(
        val roomNightsSold: Long, val roomNightsAvailable: Long,
        val occupancyPct: Double, val roomRevenue: Double, val adr: Double, val revpar: Double,
        val byDay: List<DayLine>
    )

    data class DayLine(val date: String, val occupied: Int, val available: Int, val revenue: Double) {
        val occupancyPct: Double get() = if (available == 0) 0.0 else occupied * 100.0 / available
    }

    fun occupancy(from: String, to: String): Occupancy {
        val start = LocalDate.parse(from.take(10))
        val end = LocalDate.parse(to.take(10))
        val days = ChronoUnit.DAYS.between(start, end).toInt().coerceAtLeast(1)
        val roomCount = rooms().size
        val revenueByDay = revenueByDay(from, to)
        val lines = (0 until days).map { offset ->
            val day = start.plusDays(offset.toLong()).toString()
            val occupied = db.count(
                "bookings",
                "status IN ('BOOKED','CHECKED_IN','CHECKED_OUT') AND check_in_date <= ? AND check_out_date > ?",
                day, day
            )
            DayLine(day, occupied, roomCount, revenueByDay[day] ?: 0.0)
        }
        val sold = db.q(
            """SELECT check_in_date, check_out_date FROM bookings
               WHERE status IN ('CHECKED_IN','CHECKED_OUT') AND check_in_date < ? AND check_out_date > ?""",
            to.take(10), from.take(10)
        ).sumOf { b ->
            val s = LocalDate.parse(from.take(10))
            val e = LocalDate.parse(to.take(10))
            val bs = maxOf(s, LocalDate.parse(b.str("check_in_date").take(10)))
            val be = minOf(e, LocalDate.parse(b.str("check_out_date").take(10)))
            if (be.isAfter(bs)) ChronoUnit.DAYS.between(bs, be) else 0L
        }
        val roomRevenue = Fmt.r2(revenueByDay.values.sum())
        val available = (roomCount.toLong() * days)
        return Occupancy(
            roomNightsSold = sold,
            roomNightsAvailable = available,
            occupancyPct = if (available == 0L) 0.0 else sold * 100.0 / available,
            roomRevenue = roomRevenue,
            adr = if (sold == 0L) 0.0 else Fmt.r2(roomRevenue / sold),
            revpar = if (available == 0L) 0.0 else Fmt.r2(roomRevenue / available),
            byDay = lines
        )
    }

    /** Room revenue recognised in the ledger, per day. */
    fun revenueByDay(from: String, to: String): Map<String, Double> {
        val rows = db.q(
            """SELECT substr(e.entry_date,1,10) AS d, SUM(l.credit) - SUM(l.debit) AS v
               FROM journal_lines l JOIN journal_entries e ON e.id = l.entry_id
               WHERE l.account_id=? AND substr(e.entry_date,1,10) BETWEEN ? AND ?
               GROUP BY d""",
            Accounts.ROOM_REVENUE, from.take(10), to.take(10)
        )
        return rows.associate { it.str("d") to Fmt.r2(it.dbl("v")) }
    }

    data class RevenueLine(val label: String, val amount: Double)

    /** What the hotel earned in the period, by account, straight from the ledger. */
    fun revenueBreakdown(from: String, to: String): List<RevenueLine> {
        val accounts = listOf(
            Accounts.ROOM_REVENUE to "Accommodation",
            Accounts.FB_REVENUE to "Restaurant & bar",
            Accounts.GUEST_REVENUE to "Other guest services"
        )
        return accounts.map { (account, label) ->
            val v = db.scalar(
                """SELECT COALESCE(SUM(l.credit) - SUM(l.debit),0) FROM journal_lines l
                   JOIN journal_entries e ON e.id=l.entry_id
                   WHERE l.account_id=? AND substr(e.entry_date,1,10) BETWEEN ? AND ?""",
                account, from.take(10), to.take(10)
            )
            RevenueLine(label, Fmt.r2(v))
        }
    }

    /** Deposits we are still holding for guests, straight from the ledger. */
    fun depositsHeld(): Double = db.scalar(
        """SELECT COALESCE(SUM(l.credit) - SUM(l.debit),0) FROM journal_lines l
           WHERE l.account_id=?""", Accounts.GUEST_DEPOSITS
    )

    fun outstandingFolios(): Double = db.scalar(
        """SELECT COALESCE(SUM(balance),0) FROM (
              SELECT b.id,
                (SELECT COALESCE(SUM(amount),0) FROM folio_charges c WHERE c.booking_id=b.id)
                - (SELECT COALESCE(SUM(amount),0) FROM hotel_payments p WHERE p.booking_id=b.id) AS balance
              FROM bookings b WHERE b.status='CHECKED_IN')"""
    )

    fun todayOccupancy(): Double {
        val today = Clock.today()
        val total = rooms().size
        if (total == 0) return 0.0
        val inHouseNow = db.count("bookings", "status='CHECKED_IN'")
        return inHouseNow * 100.0 / total
    }

    fun roomStatusCounts(): Map<String, Int> = rooms().groupBy { it.str("status") }.mapValues { it.value.size }

    data class HotelKpis(
        val rooms: Int, val occupied: Int, val arrivals: Int, val departures: Int,
        val vacantClean: Int, val dirty: Int, val outOfOrder: Int,
        val occupancyPct: Double, val inHouseGuests: Int,
        val revenueThisMonth: Double, val depositsHeld: Double, val outstanding: Double
    )

    fun kpis(): HotelKpis {
        val counts = roomStatusCounts()
        val inHouseRows = inHouse()
        val monthFrom = Clock.monthStart()
        val monthTo = Clock.today()
        val revenue = revenueBreakdown(monthFrom, monthTo).sumOf { it.amount }
        return HotelKpis(
            rooms = rooms().size,
            occupied = counts[OCCUPIED] ?: 0,
            arrivals = arrivalsToday().size,
            departures = departuresToday().size,
            vacantClean = counts[VACANT_CLEAN] ?: 0,
            dirty = counts[VACANT_DIRTY] ?: 0,
            outOfOrder = counts[OUT_OF_ORDER] ?: 0,
            occupancyPct = todayOccupancy(),
            inHouseGuests = inHouseRows.sumOf { it.int("adults") + it.int("children") },
            revenueThisMonth = Fmt.r2(revenue),
            depositsHeld = Fmt.r2(depositsHeld()),
            outstanding = Fmt.r2(outstandingFolios())
        )
    }

    fun hasRooms(): Boolean = db.count("rooms", "is_active=1") > 0

    /** Live bookings touching a window, for the availability grid. */
    fun bookingsBetween(from: String, to: String): List<Row> = db.q(
        """SELECT b.*, r.number AS room_number FROM bookings b
           LEFT JOIN rooms r ON r.id = b.room_id
           WHERE b.status IN (?, ?) AND b.check_in_date < ? AND b.check_out_date > ?
           ORDER BY b.check_in_date""", BOOKED, CHECKED_IN, to, from
    )

    // ══ Yanolja-parity additions ══════════════════════════════════════════════
    // Rates, agents and city ledger, night audit, housekeeping board and
    // maintenance — all offline, on the same SQLite file.

    private fun isWeekend(date: String): Boolean = runCatching {
        val d = java.time.LocalDate.parse(date.take(10)).dayOfWeek
        d == java.time.DayOfWeek.SATURDAY || d == java.time.DayOfWeek.SUNDAY
    }.getOrDefault(false)

    /** The price a room type actually sells for on one night. */
    fun rateFor(roomTypeId: String, date: String): Double {
        val override = db.one(
            "SELECT rate FROM rate_overrides WHERE room_type_id=? AND rate_date=? LIMIT 1",
            roomTypeId, date.take(10)
        )?.dbl("rate")
        if (override != null && override > 0) return Fmt.r2(override)

        val base = roomType(roomTypeId)?.dbl("base_rate") ?: 0.0
        val plans = db.q(
            """SELECT * FROM rate_plans
               WHERE is_active=1 AND (room_type_id=? OR room_type_id IS NULL OR room_type_id='')
                 AND (valid_from IS NULL OR valid_from='' OR valid_from<=?)
                 AND (valid_to   IS NULL OR valid_to=''   OR valid_to>=?)
               ORDER BY price_delta DESC""",
            roomTypeId, date.take(10), date.take(10)
        )
        if (plans.isEmpty()) return Fmt.r2(base)
        // The highest-priced active plan wins (a package beats the bare room).
        val best = plans.maxByOrNull { it.dbl("price_delta") }!!
        val weekend = if (isWeekend(date)) best.dbl("weekend_delta") else 0.0
        return Fmt.r2(base + best.dbl("price_delta") + weekend)
    }

    fun ratePlans(activeOnly: Boolean = false): List<Row> = db.q(
        if (activeOnly) "SELECT * FROM rate_plans WHERE is_active=1 ORDER BY name"
        else "SELECT * FROM rate_plans ORDER BY is_active DESC, name"
    )

    fun saveRatePlan(
        id: String?, name: String, roomTypeId: String?, kind: String = "EP",
        priceDelta: Double = 0.0, validFrom: String = "", validTo: String = "",
        weekendDelta: Double = 0.0, minNights: Int = 1, active: Boolean = true
    ): String {
        require(name.isNotBlank()) { "Give the rate plan a name" }
        val now = Clock.nowIso()
        val values = mapOf(
            "name" to name.trim(), "room_type_id" to (roomTypeId ?: ""), "kind" to kind,
            "price_delta" to Fmt.r2(priceDelta), "valid_from" to validFrom.take(10),
            "valid_to" to validTo.take(10), "weekend_delta" to Fmt.r2(weekendDelta),
            "min_nights" to minNights.coerceAtLeast(1), "is_active" to if (active) 1 else 0,
            "updated_at" to now
        )
        return db.tx {
            if (id == null) db.insert("rate_plans", values + mapOf("id" to UUID.randomUUID().toString(), "created_at" to now))
            else { db.update("rate_plans", id, values); id }
        }
    }

    fun setRatePlanActive(id: String, active: Boolean) =
        db.update("rate_plans", id, mapOf("is_active" to if (active) 1 else 0, "updated_at" to Clock.nowIso()))

    fun deleteRatePlan(id: String) = db.delete("rate_plans", id)

    fun setRateOverride(roomTypeId: String, date: String, rate: Double?) {
        val d = date.take(10)
        db.exec("DELETE FROM rate_overrides WHERE room_type_id=? AND rate_date=?", roomTypeId, d)
        if (rate != null && rate > 0) {
            db.insert("rate_overrides", mapOf(
                "id" to UUID.randomUUID().toString(), "room_type_id" to roomTypeId,
                "rate_date" to d, "rate" to Fmt.r2(rate), "created_at" to Clock.nowIso()
            ))
        }
    }

    /** A day-by-day price board for a room type. */
    fun rateCalendar(roomTypeId: String, days: Int = 14): List<Pair<String, Double>> =
        (0 until days).map { i -> addDays(Clock.today(), i.toLong()) }.map { d -> d to rateFor(roomTypeId, d) }

    // ── Agents, corporate accounts and the city ledger ───────────────────────

    fun agents(activeOnly: Boolean = false): List<Row> = db.q(
        if (activeOnly) "SELECT * FROM agents WHERE is_active=1 ORDER BY name"
        else "SELECT * FROM agents ORDER BY is_active DESC, name"
    )

    fun agent(id: String): Row? = db.one("SELECT * FROM agents WHERE id=?", id)

    fun saveAgent(
        id: String?, name: String, kind: String = "TRAVEL_AGENT", contact: String = "",
        phone: String = "", email: String = "", commissionPct: Double = 0.0,
        creditLimit: Double = 0.0, notes: String = "", active: Boolean = true
    ): String {
        require(name.isNotBlank()) { "Agent name is required" }
        val now = Clock.nowIso()
        val values = mapOf(
            "name" to name.trim(), "kind" to kind, "contact" to contact.trim(),
            "phone" to phone.trim(), "email" to email.trim(),
            "commission_pct" to commissionPct.coerceIn(0.0, 100.0),
            "credit_limit" to Fmt.r2(creditLimit), "notes" to notes.trim(),
            "is_active" to if (active) 1 else 0, "updated_at" to now
        )
        return db.tx {
            if (id == null) db.insert("agents", values + mapOf("id" to UUID.randomUUID().toString(), "created_at" to now))
            else { db.update("agents", id, values); id }
        }
    }

    fun agentBookings(agentId: String, limit: Int = 200): List<Row> = db.q(
        """SELECT b.*, r.number AS room_number FROM bookings b LEFT JOIN rooms r ON r.id=b.room_id
           WHERE b.agent_id=? ORDER BY b.check_in_date DESC LIMIT ?""",
        agentId, limit
    )

    /** What an agent owes us, booking by booking, with its commission. */
    data class AgentLine(val ref: String, val guest: String, val inDate: String, val outDate: String,
                         val gross: Double, val paid: Double, val commission: Double, val due: Double)

    fun agentStatement(agentId: String): List<AgentLine> {
        val pct = agent(agentId)?.dbl("commission_pct") ?: 0.0
        return agentBookings(agentId).filter { it.str("status") != CANCELLED }.map { b ->
            val gross = b.dbl("invoice_total")
            val paid = b.dbl("paid_total")
            val commission = Fmt.r2(gross * pct / 100.0)
            AgentLine(b.str("ref_no"), b.str("guest_name"), b.str("check_in_date"),
                b.str("check_out_date"), gross, paid, commission, Fmt.r2(gross - commission - paid))
        }
    }

    fun agentBalance(agentId: String): Double = agentStatement(agentId).sumOf { it.due }

    fun agentInvoices(agentId: String? = null): List<Row> = if (agentId == null)
        db.q("""SELECT i.*, a.name AS agent_name FROM agent_invoices i
               LEFT JOIN agents a ON a.id=i.agent_id ORDER BY i.issued_at DESC""")
    else db.q("""SELECT i.*, a.name AS agent_name FROM agent_invoices i
                 LEFT JOIN agents a ON a.id=i.agent_id WHERE i.agent_id=? ORDER BY i.issued_at DESC""", agentId)

    /** Bills an agent for everything unbilled between two dates. */
    fun createAgentInvoice(agentId: String, from: String, to: String, dueDays: Int = 14): String {
        val a = agent(agentId) ?: error("Agent not found")
        val lines = agentStatement(agentId).filter { it.due > 0.5 }
        require(lines.isNotEmpty()) { "Nothing owing to ${a.str("name")} right now" }
        val total = Fmt.r2(lines.sumOf { it.due })
        val no = "AG-" + (db.count("agent_invoices") + 1).toString().padStart(4, '0')
        val id = UUID.randomUUID().toString()
        db.insert("agent_invoices", mapOf(
            "id" to id, "agent_id" to agentId, "invoice_no" to no,
            "period_from" to from.take(10), "period_to" to to.take(10),
            "amount" to total, "paid" to 0.0, "status" to "OPEN",
            "issued_at" to Clock.nowIso(), "due_date" to addDays(Clock.today(), dueDays.toLong()),
            "note" to "${lines.size} stay(s)", "created_at" to Clock.nowIso()
        ))
        return id
    }

    fun payAgentInvoice(invoiceId: String, amount: Double, note: String = "") {
        require(amount > 0) { "Amount must be more than zero" }
        val inv = db.one("SELECT * FROM agent_invoices WHERE id=?", invoiceId) ?: error("Invoice not found")
        val paid = Fmt.r2(inv.dbl("paid") + amount)
        val status = if (paid >= inv.dbl("amount") - 0.5) "PAID" else "PART"
        db.update("agent_invoices", invoiceId, mapOf("paid" to paid, "status" to status, "note" to note))
    }

    /** Ageing of what agents owe, in the buckets a hotel actually chases. */
    data class Ageing(val current: Double, val d30: Double, val d60: Double, val d90: Double) {
        val total: Double get() = current + d30 + d60 + d90
    }

    fun agentAgeing(): Ageing {
        var current = 0.0; var d30 = 0.0; var d60 = 0.0; var d90 = 0.0
        agentInvoices().filter { it.str("status") != "PAID" }.forEach { inv ->
            val outstanding = Fmt.r2(inv.dbl("amount") - inv.dbl("paid"))
            if (outstanding <= 0) return@forEach
            val days = runCatching {
                java.time.temporal.ChronoUnit.DAYS.between(
                    java.time.LocalDate.parse(inv.str("due_date").take(10)), java.time.LocalDate.now()
                ).toInt()
            }.getOrDefault(0)
            when {
                days <= 0 -> current += outstanding
                days <= 30 -> d30 += outstanding
                days <= 60 -> d60 += outstanding
                else -> d90 += outstanding
            }
        }
        return Ageing(Fmt.r2(current), Fmt.r2(d30), Fmt.r2(d60), Fmt.r2(d90))
    }

    // ── Night audit ──────────────────────────────────────────────────────────

    fun lastAudit(): Row? = db.one("SELECT * FROM night_audits ORDER BY business_date DESC LIMIT 1")

    /** The date the hotel is working on: the day after the last closed audit. */
    fun businessDate(): String {
        val last = lastAudit() ?: return Clock.today()
        // The night audit usually runs after midnight, so closing today's day
        // legitimately moves the hotel onto tomorrow's date.
        return addDays(last.str("business_date"), 1)
    }

    fun audits(limit: Int = 60): List<Row> =
        db.q("SELECT * FROM night_audits ORDER BY business_date DESC LIMIT ?", limit)

    data class AuditSummary(
        val businessDate: String, val roomsAvailable: Int, val roomsSold: Int, val occupancy: Double,
        val revenue: Double, val payments: Double, val deposits: Double, val noShows: Int,
        val arrivals: Int, val inHouse: Int, val departures: Int,
        val byKind: List<RevenueLine>, val byMethod: List<RevenueLine>
    )

    /** A blank day, so a screen never has to deal with a null summary. */
    fun emptySummary(date: String): AuditSummary = AuditSummary(
        businessDate = date, roomsAvailable = 0, roomsSold = 0, occupancy = 0.0,
        revenue = 0.0, payments = 0.0, deposits = 0.0, noShows = 0,
        arrivals = 0, inHouse = 0, departures = 0, byKind = emptyList(), byMethod = emptyList()
    )

    fun auditSummary(date: String = businessDate()): AuditSummary {
        val d = date.take(10)
        val available = db.count("rooms", "is_active=1")
        val sold = db.q(
            """SELECT COUNT(DISTINCT room_id) AS n FROM bookings
               WHERE status IN ('CHECKED_IN','CHECKED_OUT')
                 AND check_in_date<=? AND check_out_date>?""", d, d
        ).firstOrNull()?.dbl("n")?.toInt() ?: 0
        val revenue = db.scalar(
            "SELECT COALESCE(SUM(amount),0) FROM folio_charges WHERE charge_date=?", d
        )
        val payments = db.scalar(
            "SELECT COALESCE(SUM(amount),0) FROM hotel_payments WHERE substr(paid_at,1,10)=?", d
        )
        val deposits = depositsHeld()
        val noShows = db.count("bookings", "status='BOOKED' AND check_in_date<=?", d)
        val arrivals = db.count("bookings", "check_in_date=? AND status IN ('BOOKED','CHECKED_IN')", d)
        val inHouse = db.count("bookings", "status='CHECKED_IN'")
        val departures = db.count("bookings", "check_out_date=? AND status IN ('BOOKED','CHECKED_IN')", d)
        val byKind = db.q(
            """SELECT kind AS label, COALESCE(SUM(amount),0) AS amount FROM folio_charges
               WHERE charge_date=? GROUP BY kind ORDER BY amount DESC""", d
        ).map { RevenueLine(kindLabel(it.str("kind")), Fmt.r2(it.dbl("amount"))) }
        val byMethod = db.q(
            """SELECT method AS label, COALESCE(SUM(amount),0) AS amount FROM hotel_payments
               WHERE substr(paid_at,1,10)=? GROUP BY method ORDER BY amount DESC""", d
        ).map { RevenueLine(it.str("label").replace("_", " "), Fmt.r2(it.dbl("amount"))) }
        return AuditSummary(
            d, available, sold, if (available > 0) sold * 100.0 / available else 0.0,
            Fmt.r2(revenue), Fmt.r2(payments), Fmt.r2(deposits), noShows, arrivals, inHouse, departures,
            byKind, byMethod
        )
    }

    /** Closes the business day: flags no-shows, snapshots the day, moves the date on. */
    fun closeNightAudit(date: String = businessDate(), userId: String = "u-owner", markNoShows: Boolean = true): String {
        val d = date.take(10)
        check(audits().none { it.str("business_date") == d }) { "$d is already closed" }
        val summary = auditSummary(d)
        var noShows = 0
        if (markNoShows) {
            db.q("SELECT * FROM bookings WHERE status='BOOKED' AND check_in_date=?", d).forEach { b ->
                db.update("bookings", b.str("id"), mapOf(
                    "status" to NO_SHOW, "updated_at" to Clock.nowIso(),
                    "cancel_reason" to "No show on $d"
                ))
                noShows++
            }
        }
        val id = UUID.randomUUID().toString()
        db.insert("night_audits", mapOf(
            "id" to id, "business_date" to d, "closed_at" to Clock.nowIso(), "closed_by" to userId,
            "rooms_sold" to summary.roomsSold, "rooms_available" to summary.roomsAvailable,
            "revenue" to summary.revenue, "payments" to summary.payments, "no_shows" to noShows,
            "note" to "Occupancy ${Fmt.pct(summary.occupancy / 100.0)}"
        ))
        return id
    }

    // ── Housekeeping board and maintenance ───────────────────────────────────

    data class Board(val room: Row, val status: String, val guest: String, val openTasks: Int, val priority: String)

    fun housekeepingBoard(): List<Board> = rooms().map { r ->
        val rid = r.str("id")
        val tasks = db.q(
            "SELECT * FROM housekeeping_tasks WHERE room_id=? AND status='OPEN'", rid
        )
        val occupant = db.one(
            """SELECT guest_name FROM bookings WHERE room_id=? AND status='CHECKED_IN' LIMIT 1""", rid
        )?.str("guest_name") ?: ""
        Board(
            r, r.str("status"), occupant, tasks.size,
            if (tasks.any { it.str("priority") == "URGENT" }) "URGENT"
            else if (tasks.any { it.str("priority") == "HIGH" }) "HIGH" else "NORMAL"
        )
    }

    fun raiseTask(roomId: String, kind: String, note: String, assignedTo: String = "",
                  priority: String = "NORMAL") {
        db.insert("housekeeping_tasks", mapOf(
            "id" to UUID.randomUUID().toString(), "room_id" to roomId, "kind" to kind.uppercase(),
            "note" to note.trim(), "status" to "OPEN", "priority" to priority,
            "assigned_to" to assignedTo.trim(), "created_at" to Clock.nowIso(),
            "updated_at" to Clock.nowIso()
        ))
        if (kind.uppercase() == "MAINTENANCE") setRoomStatus(roomId, OUT_OF_ORDER)
    }

    fun setTaskPriority(taskId: String, priority: String) =
        db.update("housekeeping_tasks", taskId, mapOf("priority" to priority, "updated_at" to Clock.nowIso()))

    /** One task per departure, so the board knows what today needs. */
    fun buildHousekeepingList(date: String = Clock.today()): Int {
        var made = 0
        departuresOn(date).forEach { b ->
            val roomId = b.str("room_id")
            val exists = db.one(
                """SELECT id FROM housekeeping_tasks WHERE room_id=? AND status='OPEN' AND kind='CHECKOUT CLEAN'""",
                roomId
            )
            if (exists == null) {
                raiseTask(roomId, "CHECKOUT CLEAN", "Departure ${b.str("guest_name")}", priority = "HIGH")
                made++
            }
        }
        rooms().filter { it.str("status") == VACANT_DIRTY }.forEach { r ->
            val exists = db.one(
                "SELECT id FROM housekeeping_tasks WHERE room_id=? AND status='OPEN' AND kind='CLEAN'",
                r.str("id")
            )
            if (exists == null) { raiseTask(r.str("id"), "CLEAN", "Room left dirty", priority = "NORMAL"); made++ }
        }
        return made
    }

    fun maintenanceTickets(openOnly: Boolean = true): List<Row> = db.q(
        """SELECT m.*, r.number AS room_number FROM maintenance_tickets m
           LEFT JOIN rooms r ON r.id=m.room_id
           ${if (openOnly) "WHERE m.status<>'DONE'" else ""}
           ORDER BY CASE m.priority WHEN 'URGENT' THEN 0 WHEN 'HIGH' THEN 1 ELSE 2 END, m.created_at DESC"""
    )

    fun raiseMaintenance(roomId: String, issue: String, priority: String = "NORMAL", reportedBy: String = "") {
        require(issue.isNotBlank()) { "What is the problem?" }
        db.insert("maintenance_tickets", mapOf(
            "id" to UUID.randomUUID().toString(), "room_id" to roomId, "issue" to issue.trim(),
            "priority" to priority, "status" to "OPEN", "reported_by" to reportedBy,
            "cost" to 0.0, "created_at" to Clock.nowIso()
        ))
        setRoomStatus(roomId, OUT_OF_ORDER)
    }

    fun closeMaintenance(ticketId: String, cost: Double = 0.0, note: String = "") {
        val ticket = db.one("SELECT * FROM maintenance_tickets WHERE id=?", ticketId) ?: return
        db.update("maintenance_tickets", ticketId, mapOf(
            "status" to "DONE", "cost" to Fmt.r2(cost), "done_at" to Clock.nowIso()
        ))
        // Fixed, but it still has to be cleaned before the next guest.
        val roomId = ticket.str("room_id")
        if (roomId.isNotBlank()) setRoomStatus(roomId, VACANT_DIRTY)
    }

    // ── Upsells: the extras a front desk sells every day ─────────────────────

    data class Upsell(val label: String, val kind: String, val price: Double)

    val UPSELLS = listOf(
        Upsell("Late checkout", "EXTRA", 20_000.0),
        Upsell("Early check-in", "EXTRA", 20_000.0),
        Upsell("Breakfast", "RESTAURANT", 15_000.0),
        Upsell("Airport transfer", "EXTRA", 80_000.0),
        Upsell("Laundry", "LAUNDRY", 10_000.0),
        Upsell("Extra bed", "EXTRA", 25_000.0),
        Upsell("Room upgrade", "EXTRA", 50_000.0),
        Upsell("Minibar", "BAR", 12_000.0)
    )

    fun addUpsell(bookingId: String, upsell: Upsell, qty: Int = 1, userId: String = "u-owner") =
        addCharge(bookingId, upsell.kind, upsell.label, qty.toDouble(), upsell.price, userId = userId)
}
