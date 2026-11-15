package com.derycode.deryaccount.hotel

import com.derycode.deryaccount.accounting.AccountingRepo
import com.derycode.deryaccount.data.local.AppDatabase
import com.derycode.deryaccount.data.local.entity.*
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToLong

/**
 * Hotel & accommodation engine for the phone: rooms, reservations, the guest
 * folio and the front-desk numbers.
 *
 * The money rules are the same as the desktop app, so a shop and a lodge keep
 * one set of books:
 *   deposit / part payment → Dr cash or mobile money, Cr Guest Deposits (a liability)
 *   check-in               → room charges land on the folio, no revenue yet
 *   check-out              → Dr deposits + debtors + discounts
 *                            Cr room revenue + F&B revenue + other services + VAT
 */
class HotelRepo(
    private val db: AppDatabase,
    private val branchId: String,
    private val userId: String
) {
    private val acc = AccountingRepo(db)

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

        val CHARGE_KINDS = listOf("RESTAURANT", "BAR", "LAUNDRY", "EXTRA", "DISCOUNT")
        val SOURCES = listOf("WALK_IN", "PHONE", "ONLINE", "AGENT", "CORPORATE")

        fun nights(from: String, to: String): Long = runCatching {
            ChronoUnit.DAYS.between(LocalDate.parse(from.take(10)), LocalDate.parse(to.take(10)))
        }.getOrDefault(0L)

        fun addDays(date: String, days: Long): String =
            runCatching { LocalDate.parse(date.take(10)).plusDays(days).toString() }.getOrDefault(date)

        fun today(): String = LocalDate.now().toString()

        fun money(v: Double): String =
            "UGX " + String.format(Locale.US, "%,.0f", v)

        fun pct(v: Double): String = String.format(Locale.US, "%.0f%%", v)

        fun statusLabel(status: String): String = when (status) {
            VACANT_CLEAN -> "Clean"
            VACANT_DIRTY -> "To clean"
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

        fun kindLabel(kind: String): String = when (kind.uppercase()) {
            "ROOM" -> "Room"
            "RESTAURANT" -> "Restaurant"
            "BAR" -> "Bar"
            "LAUNDRY" -> "Laundry"
            "EXTRA" -> "Extra"
            "DISCOUNT" -> "Discount"
            else -> kind.lowercase().replaceFirstChar { it.uppercase() }
        }

        fun accountFor(kind: String): String = when (kind.uppercase()) {
            "ROOM" -> AccountingRepo.ROOM_REVENUE
            "RESTAURANT", "BAR" -> AccountingRepo.FB_REVENUE
            else -> AccountingRepo.GUEST_REVENUE
        }
    }

    private fun now(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())

    private fun r2(v: Double): Double = (v * 100.0).roundToLong() / 100.0

    // ── Rooms ────────────────────────────────────────────────────────────────
    fun roomTypes(): Flow<List<RoomType>> = db.hotelDao().observeRoomTypes()
    suspend fun roomTypesOnce(): List<RoomType> = db.hotelDao().roomTypesOnce()
    fun rooms(): Flow<List<HotelRoom>> = db.hotelDao().observeRooms()
    suspend fun roomsOnce(): List<HotelRoom> = db.hotelDao().roomsOnce()

    fun rateOf(room: HotelRoom, type: RoomType?): Double =
        if (room.rateOverride > 0) room.rateOverride else (type?.baseRate ?: 0.0)

    suspend fun rateOf(room: HotelRoom): Double =
        rateOf(room, db.hotelDao().roomType(room.roomTypeId))

    suspend fun saveRoomType(
        id: String?, name: String, baseRate: Double, capacity: Int, beds: String,
        description: String = "", taxRate: Double = 0.0
    ): String {
        require(name.isNotBlank()) { "Give the room type a name" }
        val ts = now()
        val existing = if (id != null) db.hotelDao().roomType(id) else null
        val row = RoomType(
            id = id ?: UUID.randomUUID().toString(),
            name = name.trim(), baseRate = r2(baseRate), capacity = capacity, beds = beds,
            description = description, taxRate = taxRate,
            createdAt = existing?.createdAt ?: ts, updatedAt = ts
        )
        db.hotelDao().upsertRoomType(row)
        return row.id
    }

    suspend fun saveRoom(
        id: String?, number: String, roomTypeId: String, floor: String,
        status: String, note: String, rateOverride: Double = 0.0
    ): String {
        require(number.isNotBlank()) { "Every room needs a number" }
        val clash = db.hotelDao().roomByNumber(number.trim())
        require(clash == null || clash.id == id) { "Room $number already exists" }
        val ts = now()
        val existing = if (id != null) db.hotelDao().room(id) else null
        val row = HotelRoom(
            id = id ?: UUID.randomUUID().toString(),
            number = number.trim(), roomTypeId = roomTypeId, floor = floor,
            status = status, rateOverride = r2(rateOverride), note = note,
            branchId = branchId, isActive = true,
            createdAt = existing?.createdAt ?: ts, updatedAt = ts
        )
        db.hotelDao().upsertRoom(row)
        return row.id
    }

    suspend fun setRoomStatus(roomId: String, status: String) =
        db.hotelDao().setRoomStatus(roomId, status, now())

    suspend fun retireRoom(roomId: String) {
        val room = db.hotelDao().room(roomId) ?: return
        require(db.hotelDao().clashes(roomId, "0000-01-01", "9999-12-31") == 0) {
            "This room still has an open booking"
        }
        db.hotelDao().upsertRoom(room.copy(isActive = false, updatedAt = now(), syncState = "pending"))
    }

    // ── Guests ───────────────────────────────────────────────────────────────
    fun guests(q: String = ""): Flow<List<Guest>> = db.hotelDao().observeGuests(q)

    suspend fun saveGuest(
        id: String?, name: String, phone: String, email: String = "", idNumber: String = "",
        nationality: String = "", address: String = "", notes: String = ""
    ): String {
        require(name.isNotBlank()) { "Guest name is required" }
        val ts = now()
        val existing = if (id != null) db.hotelDao().guest(id) else null
        val row = Guest(
            id = id ?: UUID.randomUUID().toString(), name = name.trim(), phone = phone.trim(),
            email = email.trim(), idNumber = idNumber.trim(), nationality = nationality.trim(),
            address = address.trim(), notes = notes.trim(),
            createdAt = existing?.createdAt ?: ts, updatedAt = ts
        )
        db.hotelDao().upsertGuest(row)
        return row.id
    }

    suspend fun staysForGuest(guestId: String): List<Booking> = db.hotelDao().staysForGuest(guestId)

    // ── Availability ─────────────────────────────────────────────────────────
    suspend fun isRoomFree(roomId: String, from: String, to: String, excludeBookingId: String? = null): Boolean {
        if (nights(from, to) <= 0) return false
        return db.hotelDao().clashes(roomId, from.take(10), to.take(10), excludeBookingId ?: "") == 0
    }

    suspend fun freeRooms(from: String, to: String, excludeBookingId: String? = null): List<HotelRoom> =
        roomsOnce().filter {
            it.status != OUT_OF_ORDER && isRoomFree(it.id, from, to, excludeBookingId)
        }

    // ── Bookings ─────────────────────────────────────────────────────────────
    fun openBookings(): Flow<List<Booking>> = db.hotelDao().observeOpenBookings()
    fun allBookings(): Flow<List<Booking>> = db.hotelDao().observeAllBookings()
    fun bookingFlow(id: String): Flow<Booking?> = db.hotelDao().observeBooking(id)
    suspend fun booking(id: String): Booking? = db.hotelDao().booking(id)
    suspend fun arrivals() = db.hotelDao().arrivalsOnce(today())
    suspend fun inHouse() = db.hotelDao().inHouseOnce()
    suspend fun departures() = db.hotelDao().departuresOnce(today())

    private suspend fun nextRef(): String {
        val n = db.hotelDao().bookingCount() + 1
        return String.format(Locale.US, "BK-%05d", n)
    }

    suspend fun createBooking(
        guestName: String, guestPhone: String, roomId: String, checkIn: String, checkOut: String,
        adults: Int = 1, children: Int = 0, rate: Double? = null, source: String = "WALK_IN",
        notes: String = "", deposit: Double = 0.0, depositMethod: String = "CASH",
        guestId: String? = null
    ): String {
        require(guestName.isNotBlank()) { "Who is the guest?" }
        val n = nights(checkIn, checkOut)
        require(n > 0) { "Check-out must be after check-in" }
        val room = db.hotelDao().room(roomId) ?: error("Pick a room")
        require(isRoomFree(roomId, checkIn, checkOut)) { "Room ${room.number} is already taken for those dates" }
        val nightRate = r2(rate ?: rateOf(room))
        val ts = now()
        val id = UUID.randomUUID().toString()
        db.hotelDao().upsertBooking(
            Booking(
                id = id, refNo = nextRef(), guestId = guestId, guestName = guestName.trim(),
                guestPhone = guestPhone.trim(), roomId = roomId, roomTypeId = room.roomTypeId,
                checkInDate = checkIn.take(10), checkOutDate = checkOut.take(10),
                adults = adults, children = children, nights = n.toInt(), rate = nightRate,
                status = BOOKED, source = source, notes = notes,
                invoiceTotal = r2(nightRate * n), paidTotal = 0.0,
                userId = userId, branchId = branchId, createdAt = ts, updatedAt = ts
            )
        )
        if (deposit > 0) postPayment(id, deposit, depositMethod, "Deposit at booking")
        return id
    }

    suspend fun walkIn(
        guestName: String, guestPhone: String, roomId: String, checkOut: String,
        adults: Int = 1, children: Int = 0, rate: Double? = null, notes: String = "",
        deposit: Double = 0.0, depositMethod: String = "CASH"
    ): String {
        val id = createBooking(
            guestName, guestPhone, roomId, today(), checkOut, adults, children,
            rate, "WALK_IN", notes, deposit, depositMethod
        )
        checkIn(id)
        return id
    }

    suspend fun checkIn(bookingId: String) {
        val b = db.hotelDao().booking(bookingId) ?: error("Booking not found")
        require(b.status == BOOKED) { "Only a booked reservation can be checked in" }
        require(isRoomFree(b.roomId, b.checkInDate, b.checkOutDate, bookingId)) {
            "That room has another guest for those dates"
        }
        val ts = now()
        db.hotelDao().upsertBooking(
            b.copy(status = CHECKED_IN, checkedInAt = ts, updatedAt = ts, syncState = "pending")
        )
        db.hotelDao().setRoomStatus(b.roomId, OCCUPIED, ts)
        postRoomCharges(bookingId)
    }

    /** One ROOM line per night so the bill reads like a hotel bill. */
    private suspend fun postRoomCharges(bookingId: String) {
        val b = db.hotelDao().booking(bookingId) ?: return
        val type = b.roomTypeId?.let { db.hotelDao().roomType(it) }
        val taxRate = type?.taxRate ?: 0.0
        val existing = db.hotelDao().roomCharges(bookingId).map { it.chargeDate }.toSet()
        val n = nights(b.checkInDate, b.checkOutDate)
        val ts = now()
        for (i in 0 until n) {
            val date = addDays(b.checkInDate, i)
            if (date in existing) continue
            db.hotelDao().upsertCharge(
                FolioCharge(
                    id = UUID.randomUUID().toString(), bookingId = bookingId, kind = "ROOM",
                    description = "Room night ${i + 1}", qty = 1.0, unitPrice = b.rate,
                    amount = b.rate,
                    taxAmount = if (taxRate > 0) r2(b.rate * taxRate / (100.0 + taxRate)) else 0.0,
                    chargeDate = date, postedAt = ts, postedBy = userId,
                    createdAt = ts, updatedAt = ts
                )
            )
        }
        refreshTotals(bookingId)
    }

    suspend fun addCharge(
        bookingId: String, kind: String, description: String, qty: Double, unitPrice: Double,
        taxRate: Double = 0.0
    ) {
        require(qty > 0) { "Quantity must be more than zero" }
        require(unitPrice > 0) { "Amount must be more than zero" }
        val amount = r2(qty * unitPrice)
        val ts = now()
        db.hotelDao().upsertCharge(
            FolioCharge(
                id = UUID.randomUUID().toString(), bookingId = bookingId, kind = kind.uppercase(),
                description = description.ifBlank { kindLabel(kind) }, qty = qty, unitPrice = unitPrice,
                amount = if (kind.uppercase() == "DISCOUNT") -amount else amount,
                taxAmount = if (kind.uppercase() == "DISCOUNT" || taxRate <= 0) 0.0
                else r2(amount * taxRate / (100.0 + taxRate)),
                chargeDate = today(), postedAt = ts, postedBy = userId,
                createdAt = ts, updatedAt = ts
            )
        )
        refreshTotals(bookingId)
    }

    suspend fun removeCharge(chargeId: String) {
        val c = db.hotelDao().chargesOnce("").firstOrNull { it.id == chargeId } ?: return
        val b = db.hotelDao().booking(c.bookingId) ?: return
        require(b.status != CHECKED_OUT) { "This folio is already closed" }
        db.hotelDao().deleteCharge(chargeId, now())
        refreshTotals(c.bookingId)
    }

    /** Shorten a stay: drop the room nights from the new check-out date onwards. */
    suspend fun trimStay(bookingId: String, newCheckOut: String) {
        val b = db.hotelDao().booking(bookingId) ?: error("Booking not found")
        require(b.status == BOOKED || b.status == CHECKED_IN) { "This booking is already closed" }
        val n = nights(b.checkInDate, newCheckOut)
        require(n > 0) { "Check-out must be after check-in" }
        db.hotelDao().deleteRoomChargesFrom(bookingId, newCheckOut.take(10), now())
        db.hotelDao().upsertBooking(
            b.copy(checkOutDate = newCheckOut.take(10), nights = n.toInt(), updatedAt = now(), syncState = "pending")
        )
        refreshTotals(bookingId)
    }

    suspend fun extendStay(bookingId: String, newCheckOut: String) {
        val b = db.hotelDao().booking(bookingId) ?: error("Booking not found")
        require(b.status == BOOKED || b.status == CHECKED_IN) { "This booking is already closed" }
        val n = nights(b.checkInDate, newCheckOut)
        require(n >= b.nights) { "New check-out must be later than the current one" }
        require(isRoomFree(b.roomId, b.checkInDate, newCheckOut, bookingId)) {
            "That room is booked for someone else on those dates"
        }
        db.hotelDao().upsertBooking(
            b.copy(checkOutDate = newCheckOut.take(10), nights = n.toInt(), updatedAt = now(), syncState = "pending")
        )
        postRoomCharges(bookingId)
    }

    // ── Folio ────────────────────────────────────────────────────────────────
    data class Folio(
        val gross: Double, val tax: Double, val paid: Double, val balance: Double,
        val byKind: Map<String, Double>
    )

    fun charges(bookingId: String): Flow<List<FolioCharge>> = db.hotelDao().observeCharges(bookingId)
    fun payments(bookingId: String): Flow<List<HotelPayment>> = db.hotelDao().observePayments(bookingId)

    suspend fun folio(bookingId: String): Folio {
        val rows = db.hotelDao().chargesOnce(bookingId)
        val gross = r2(rows.filter { it.kind != "DISCOUNT" }.sumOf { it.amount })
        val discount = r2(rows.filter { it.kind == "DISCOUNT" }.sumOf { -it.amount })
        val tax = r2(rows.filter { it.kind != "DISCOUNT" }.sumOf { it.taxAmount })
        val paid = r2(db.hotelDao().paymentsOnce(bookingId).sumOf { it.amount })
        val total = r2(gross - discount)
        val byKind = rows.filter { it.kind != "DISCOUNT" }.groupBy { it.kind }
            .mapValues { e -> r2(e.value.sumOf { it.amount }) }
        return Folio(total, tax, paid, r2(total - paid), byKind)
    }

    private suspend fun refreshTotals(bookingId: String) {
        val f = folio(bookingId)
        val b = db.hotelDao().booking(bookingId) ?: return
        db.hotelDao().upsertBooking(
            b.copy(invoiceTotal = f.gross, paidTotal = f.paid, updatedAt = now(), syncState = "pending")
        )
    }

    suspend fun postPayment(bookingId: String, amount: Double, method: String, note: String = "") {
        require(amount > 0) { "Amount must be more than zero" }
        val b = db.hotelDao().booking(bookingId) ?: error("Booking not found")
        val ts = now()
        val value = r2(amount)
        db.hotelDao().upsertPayment(
            HotelPayment(
                id = UUID.randomUUID().toString(), bookingId = bookingId, amount = value,
                method = method.uppercase(), reference = b.refNo, note = note,
                paidAt = ts, userId = userId, createdAt = ts, updatedAt = ts
            )
        )
        acc.post(
            particulars = "Guest payment — ${b.guestName} (${b.refNo})",
            source = "HOTEL",
            debits = listOf(AccountingRepo.accountForMethod(method) to value),
            credits = listOf(AccountingRepo.GUEST_DEPOSITS to value),
            branchId = branchId
        )
        refreshTotals(bookingId)
    }

    /**
     * Close the folio: recognise revenue, clear the held deposit, and either
     * collect the balance or post it to debtors (a company account).
     */
    suspend fun checkOut(
        bookingId: String, settleToDebtor: Boolean = false,
        extraPayment: Double = 0.0, method: String = "CASH"
    ): Folio {
        val b = db.hotelDao().booking(bookingId) ?: error("Booking not found")
        require(b.status == CHECKED_IN) { "Only an in-house guest can check out" }
        if (extraPayment > 0) postPayment(bookingId, extraPayment, method, "Balance on check-out")
        val f = folio(bookingId)
        require(f.gross > 0) { "This folio has nothing on it" }
        require(f.balance <= 0.5 || settleToDebtor) { "Collect ${money(f.balance)} first" }

        val rows = db.hotelDao().chargesOnce(bookingId)
        val discounts = r2(rows.filter { it.kind == "DISCOUNT" }.sumOf { -it.amount })
        val debits = mutableListOf<Pair<String, Double>>()
        if (f.paid > 0) debits.add(AccountingRepo.GUEST_DEPOSITS to f.paid)
        if (f.balance > 0) debits.add(AccountingRepo.DEBTORS to r2(f.balance))
        if (discounts > 0) debits.add(AccountingRepo.SALES_DISCOUNTS to discounts)

        val credits = mutableListOf<Pair<String, Double>>()
        f.byKind.forEach { (kind, gross) ->
            val taxPart = r2(rows.filter { it.kind == kind }.sumOf { it.taxAmount })
            val net = r2(gross - taxPart)
            if (net > 0) credits.add(accountFor(kind) to net)
        }
        if (f.tax > 0) credits.add(AccountingRepo.VAT_OUT to f.tax)
        if (f.balance < 0) credits.add(AccountingRepo.CASH to r2(-f.balance))

        acc.post(
            particulars = "Hotel folio ${b.refNo} — ${b.guestName}",
            source = "HOTEL",
            debits = debits.filter { it.second > 0 },
            credits = credits.filter { it.second > 0 },
            branchId = branchId
        )
        val ts = now()
        db.hotelDao().upsertBooking(
            b.copy(
                status = CHECKED_OUT, checkedOutAt = ts, invoiceTotal = f.gross, paidTotal = f.paid,
                updatedAt = ts, syncState = "pending"
            )
        )
        db.hotelDao().setRoomStatus(b.roomId, VACANT_DIRTY, ts)
        return f
    }

    suspend fun cancelBooking(bookingId: String, refundDeposit: Boolean = true) {
        val b = db.hotelDao().booking(bookingId) ?: error("Booking not found")
        require(b.status == BOOKED) { "Only an un-arrived booking can be cancelled" }
        val paid = r2(db.hotelDao().paymentsOnce(bookingId).sumOf { it.amount })
        if (paid > 0) {
            val keep = !refundDeposit
            acc.post(
                particulars = (if (keep) "Deposit forfeited — " else "Deposit refunded — ") +
                    "${b.guestName} (${b.refNo})",
                source = "HOTEL",
                debits = listOf(AccountingRepo.GUEST_DEPOSITS to paid),
                credits = listOf((if (keep) AccountingRepo.ROOM_REVENUE else AccountingRepo.CASH) to paid),
                branchId = branchId
            )
        }
        db.hotelDao().upsertBooking(b.copy(status = CANCELLED, updatedAt = now(), syncState = "pending"))
    }

    suspend fun markNoShow(bookingId: String) {
        val b = db.hotelDao().booking(bookingId) ?: error("Booking not found")
        require(b.status == BOOKED) { "Only an un-arrived booking can be marked no-show" }
        val paid = r2(db.hotelDao().paymentsOnce(bookingId).sumOf { it.amount })
        if (paid > 0) acc.post(
            particulars = "No-show, deposit kept — ${b.guestName} (${b.refNo})",
            source = "HOTEL",
            debits = listOf(AccountingRepo.GUEST_DEPOSITS to paid),
            credits = listOf(AccountingRepo.ROOM_REVENUE to paid),
            branchId = branchId
        )
        db.hotelDao().upsertBooking(b.copy(status = NO_SHOW, updatedAt = now(), syncState = "pending"))
    }

    // ── Housekeeping ─────────────────────────────────────────────────────────
    fun openTasks(): Flow<List<HousekeepingTask>> = db.hotelDao().observeOpenTasks()

    suspend fun openTasksOnce(): List<HousekeepingTask> = db.hotelDao().openTasksOnce()

    suspend fun raiseTask(roomId: String, kind: String, note: String, assignedTo: String = "") {
        val ts = now()
        db.hotelDao().upsertTask(
            HousekeepingTask(
                id = UUID.randomUUID().toString(), roomId = roomId, kind = kind.uppercase(),
                note = note, status = "OPEN", assignedTo = assignedTo, createdAt = ts, updatedAt = ts
            )
        )
        if (kind.uppercase() == "MAINTENANCE") setRoomStatus(roomId, OUT_OF_ORDER)
    }

    suspend fun completeTask(taskId: String) {
        val t = db.hotelDao().task(taskId) ?: return
        val ts = now()
        db.hotelDao().upsertTask(
            t.copy(status = "DONE", doneAt = ts, updatedAt = ts, syncState = "pending")
        )
        val room = db.hotelDao().room(t.roomId) ?: return
        when {
            room.status == OUT_OF_ORDER -> setRoomStatus(room.id, VACANT_CLEAN)
            room.status == VACANT_DIRTY && t.kind != "MAINTENANCE" -> setRoomStatus(room.id, VACANT_CLEAN)
        }
    }

    // ── Reports ──────────────────────────────────────────────────────────────
    data class DayLine(val date: String, val occupied: Int, val available: Int, val revenue: Double)

    data class Occupancy(
        val sold: Long, val available: Long, val pct: Double,
        val revenue: Double, val adr: Double, val revpar: Double, val byDay: List<DayLine>
    )

    suspend fun occupancy(from: String, to: String): Occupancy {
        val start = LocalDate.parse(from.take(10))
        val end = LocalDate.parse(to.take(10))
        val days = ChronoUnit.DAYS.between(start, end).toInt().coerceAtLeast(1)
        val roomCount = roomsOnce().size
        val perDay = db.hotelDao().revenueByDay(AccountingRepo.ROOM_REVENUE, from, to)
            .associate { it.label to it.value }
        val bookings = db.hotelDao().openBookingsOnce().filter { it.status == CHECKED_IN } +
            db.hotelDao().openBookingsOnce() // safe duplicate-free below
        val allBookings = bookings.distinctBy { it.id }
        val lines = (0 until days).map { offset ->
            val day = start.plusDays(offset.toLong()).toString()
            val occupied = allBookings.count { it.status == CHECKED_IN && it.checkInDate <= day && it.checkOutDate > day }
            DayLine(day, occupied, roomCount, r2(perDay[day] ?: 0.0))
        }
        val sold = allBookings.filter { it.status == CHECKED_IN }.sumOf { b ->
            val bs = maxOf(start, LocalDate.parse(b.checkInDate.take(10)))
            val be = minOf(end, LocalDate.parse(b.checkOutDate.take(10)))
            if (be.isAfter(bs)) ChronoUnit.DAYS.between(bs, be) else 0L
        }
        val revenue = r2(perDay.values.sum())
        val available = roomCount.toLong() * days
        return Occupancy(
            sold = sold, available = available,
            pct = if (available == 0L) 0.0 else sold * 100.0 / available,
            revenue = revenue,
            adr = if (sold == 0L) 0.0 else r2(revenue / sold),
            revpar = if (available == 0L) 0.0 else r2(revenue / available),
            byDay = lines
        )
    }

    data class RevenueLine(val label: String, val amount: Double)

    suspend fun revenueBreakdown(from: String, to: String): List<RevenueLine> = listOf(
        "Accommodation" to AccountingRepo.ROOM_REVENUE,
        "Restaurant & bar" to AccountingRepo.FB_REVENUE,
        "Other services" to AccountingRepo.GUEST_REVENUE
    ).map { (label, account) ->
        RevenueLine(label, r2(db.hotelDao().revenueForAccount(account, from, to)))
    }

    suspend fun depositsHeld(): Double = r2(db.hotelDao().balanceForAccount(AccountingRepo.GUEST_DEPOSITS))

    suspend fun outstandingFolios(): Double {
        val inHouse = inHouse()
        return r2(inHouse.sumOf { b ->
            val f = folio(b.id)
            f.balance.coerceAtLeast(0.0)
        })
    }

    data class Kpis(
        val rooms: Int, val occupied: Int, val arrivals: Int, val departures: Int,
        val vacantClean: Int, val dirty: Int, val outOfOrder: Int,
        val occupancyPct: Double, val guestsInHouse: Int,
        val revenueThisMonth: Double, val outstanding: Double, val deposits: Double
    )

    suspend fun kpis(): Kpis {
        val rooms = roomsOnce()
        val inHouseNow = db.hotelDao().inHouseOnce()
        val monthStart = LocalDate.now().withDayOfMonth(1).toString()
        val revenue = revenueBreakdown(monthStart, today()).sumOf { it.amount }
        val occupied = rooms.count { it.status == OCCUPIED }
        return Kpis(
            rooms = rooms.size, occupied = occupied,
            arrivals = arrivals().size, departures = departures().size,
            vacantClean = rooms.count { it.status == VACANT_CLEAN },
            dirty = rooms.count { it.status == VACANT_DIRTY },
            outOfOrder = rooms.count { it.status == OUT_OF_ORDER },
            occupancyPct = if (rooms.isEmpty()) 0.0 else occupied * 100.0 / rooms.size,
            guestsInHouse = inHouseNow.sumOf { it.adults + it.children },
            revenueThisMonth = r2(revenue),
            outstanding = outstandingFolios(),
            deposits = depositsHeld()
        )
    }

    suspend fun hasRooms(): Boolean = roomsOnce().isNotEmpty()
}
