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
        guestId: String? = null, userId: String = "u-owner"
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
                "invoice_total" to Fmt.r2(nightRate * n), "paid_total" to 0.0,
                "user_id" to userId, "branch_id" to repo.branchId(),
                "created_at" to now, "updated_at" to now
            ))
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

    fun raiseTask(roomId: String, kind: String, note: String, assignedTo: String = "") {
        db.insert("housekeeping_tasks", mapOf(
            "id" to UUID.randomUUID().toString(), "room_id" to roomId, "kind" to kind.uppercase(),
            "note" to note, "status" to "OPEN", "assigned_to" to assignedTo, "created_at" to Clock.nowIso()
        ))
        if (kind.uppercase() == "MAINTENANCE") setRoomStatus(roomId, OUT_OF_ORDER)
    }

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
}
