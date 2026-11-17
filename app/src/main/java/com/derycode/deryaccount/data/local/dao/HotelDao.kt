package com.derycode.deryaccount.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import com.derycode.deryaccount.data.local.entity.*
import kotlinx.coroutines.flow.Flow

/** Ledger totals used by the hotel reports. */
data class LedgerTotal(val label: String, val value: Double)

/** Hotel & accommodation queries: rooms, guests, bookings, folios, housekeeping. */
@Dao
interface HotelDao {

    // ── Room types ───────────────────────────────────────────────────────────
    @Query("SELECT * FROM room_types WHERE isDeleted = 0 ORDER BY baseRate DESC, name")
    fun observeRoomTypes(): Flow<List<RoomType>>

    @Query("SELECT * FROM room_types WHERE isDeleted = 0 ORDER BY baseRate DESC, name")
    suspend fun roomTypesOnce(): List<RoomType>

    @Query("SELECT * FROM room_types WHERE id = :id")
    suspend fun roomType(id: String): RoomType?

    @Upsert
    suspend fun upsertRoomType(type: RoomType)

    // ── Rooms ────────────────────────────────────────────────────────────────
    @Query("SELECT * FROM hotel_rooms WHERE isDeleted = 0 AND isActive = 1 ORDER BY number")
    fun observeRooms(): Flow<List<HotelRoom>>

    @Query("SELECT * FROM hotel_rooms WHERE isDeleted = 0 AND isActive = 1 ORDER BY number")
    suspend fun roomsOnce(): List<HotelRoom>

    @Query("SELECT * FROM hotel_rooms WHERE id = :id")
    suspend fun room(id: String): HotelRoom?

    @Query("SELECT * FROM hotel_rooms WHERE isDeleted = 0 AND isActive = 1 AND lower(number) = lower(:number) LIMIT 1")
    suspend fun roomByNumber(number: String): HotelRoom?

    @Query("UPDATE hotel_rooms SET status = :status, updatedAt = :now, syncState = 'pending' WHERE id = :id")
    suspend fun setRoomStatus(id: String, status: String, now: String)

    @Upsert
    suspend fun upsertRoom(room: HotelRoom)

    // ── Guests ───────────────────────────────────────────────────────────────
    @Query("SELECT * FROM guests WHERE isDeleted = 0 AND (name LIKE '%' || :q || '%' OR phone LIKE '%' || :q || '%' OR idNumber LIKE '%' || :q || '%') ORDER BY name")
    fun observeGuests(q: String = ""): Flow<List<Guest>>

    @Query("SELECT * FROM guests WHERE id = :id")
    suspend fun guest(id: String): Guest?

    @Upsert
    suspend fun upsertGuest(guest: Guest)

    @Query("SELECT * FROM bookings WHERE guestId = :guestId AND isDeleted = 0 ORDER BY checkInDate DESC")
    suspend fun staysForGuest(guestId: String): List<Booking>

    // ── Bookings ─────────────────────────────────────────────────────────────
    @Query("SELECT * FROM bookings WHERE isDeleted = 0 AND status IN ('BOOKED','CHECKED_IN') ORDER BY status DESC, checkInDate, roomId")
    fun observeOpenBookings(): Flow<List<Booking>>

    @Query("SELECT * FROM bookings WHERE isDeleted = 0 ORDER BY checkInDate DESC LIMIT 200")
    fun observeAllBookings(): Flow<List<Booking>>

    @Query("SELECT * FROM bookings WHERE id = :id")
    suspend fun booking(id: String): Booking?

    @Query("SELECT * FROM bookings WHERE id = :id")
    fun observeBooking(id: String): Flow<Booking?>

    @Query("SELECT * FROM bookings WHERE isDeleted = 0 AND status IN ('BOOKED','CHECKED_IN')")
    suspend fun openBookingsOnce(): List<Booking>

    @Query("SELECT * FROM bookings WHERE isDeleted = 0 AND status = 'BOOKED' AND checkInDate <= :today ORDER BY checkInDate")
    suspend fun arrivalsOnce(today: String): List<Booking>

    @Query("SELECT * FROM bookings WHERE isDeleted = 0 AND status = 'CHECKED_IN' ORDER BY checkOutDate")
    suspend fun inHouseOnce(): List<Booking>

    @Query("SELECT * FROM bookings WHERE isDeleted = 0 AND status = 'CHECKED_IN' AND checkOutDate <= :today ORDER BY checkOutDate")
    suspend fun departuresOnce(today: String): List<Booking>

    /** Overlap check: an open booking that covers any night between the two dates. */
    @Query("""SELECT COUNT(*) FROM bookings
              WHERE isDeleted = 0 AND roomId = :roomId AND status IN ('BOOKED','CHECKED_IN')
                AND id <> :excludeId AND checkInDate < :to AND checkOutDate > :from""")
    suspend fun clashes(roomId: String, from: String, to: String, excludeId: String = ""): Int

    @Query("SELECT COUNT(*) FROM bookings WHERE isDeleted = 0")
    suspend fun bookingCount(): Int

    @Upsert
    suspend fun upsertBooking(booking: Booking)

    // ── Folio ────────────────────────────────────────────────────────────────
    @Query("SELECT * FROM folio_charges WHERE bookingId = :bookingId AND isDeleted = 0 ORDER BY chargeDate, postedAt")
    fun observeCharges(bookingId: String): Flow<List<FolioCharge>>

    @Query("SELECT * FROM folio_charges WHERE bookingId = :bookingId AND isDeleted = 0 ORDER BY chargeDate, postedAt")
    suspend fun chargesOnce(bookingId: String): List<FolioCharge>

    @Query("SELECT * FROM folio_charges WHERE bookingId = :bookingId AND kind = 'ROOM' AND isDeleted = 0")
    suspend fun roomCharges(bookingId: String): List<FolioCharge>

    @Upsert
    suspend fun upsertCharge(charge: FolioCharge)

    @Query("UPDATE folio_charges SET isDeleted = 1, syncState = 'pending', updatedAt = :now WHERE id = :id")
    suspend fun deleteCharge(id: String, now: String)

    @Query("UPDATE folio_charges SET isDeleted = 1, syncState = 'pending', updatedAt = :now WHERE bookingId = :bookingId AND kind = 'ROOM' AND chargeDate >= :from")
    suspend fun deleteRoomChargesFrom(bookingId: String, from: String, now: String)

    // ── Payments ─────────────────────────────────────────────────────────────
    @Query("SELECT * FROM hotel_payments WHERE bookingId = :bookingId AND isDeleted = 0 ORDER BY paidAt")
    fun observePayments(bookingId: String): Flow<List<HotelPayment>>

    @Query("SELECT * FROM hotel_payments WHERE bookingId = :bookingId AND isDeleted = 0 ORDER BY paidAt")
    suspend fun paymentsOnce(bookingId: String): List<HotelPayment>

    @Upsert
    suspend fun upsertPayment(payment: HotelPayment)

    // ── Housekeeping ─────────────────────────────────────────────────────────
    @Query("SELECT * FROM housekeeping_tasks WHERE status = 'OPEN' AND isDeleted = 0 ORDER BY createdAt DESC")
    fun observeOpenTasks(): Flow<List<HousekeepingTask>>

    @Query("SELECT * FROM housekeeping_tasks WHERE status = 'OPEN' AND isDeleted = 0 ORDER BY createdAt DESC")
    suspend fun openTasksOnce(): List<HousekeepingTask>

    @Query("SELECT * FROM housekeeping_tasks WHERE id = :id")
    suspend fun task(id: String): HousekeepingTask?

    @Upsert
    suspend fun upsertTask(task: HousekeepingTask)

    // ── Ledger readers (hotel reports) ───────────────────────────────────────
    @Query("""SELECT COALESCE(SUM(l.credit) - SUM(l.debit), 0) FROM journal_lines l
              JOIN journal_entries e ON e.id = l.entryId
              WHERE l.accountId = :accountId AND substr(e.entryDate,1,10) BETWEEN :from AND :to""")
    suspend fun revenueForAccount(accountId: String, from: String, to: String): Double

    @Query("SELECT COALESCE(SUM(credit) - SUM(debit), 0) FROM journal_lines WHERE accountId = :accountId")
    suspend fun balanceForAccount(accountId: String): Double

    @Query("""SELECT substr(e.entryDate,1,10) AS label,
                     COALESCE(SUM(l.credit) - SUM(l.debit), 0) AS value
              FROM journal_lines l JOIN journal_entries e ON e.id = l.entryId
              WHERE l.accountId = :accountId AND substr(e.entryDate,1,10) BETWEEN :from AND :to
              GROUP BY label ORDER BY label""")
    suspend fun revenueByDay(accountId: String, from: String, to: String): List<LedgerTotal>

    // ── Night audit ─────────────────────────────────────────────────────────
    @Upsert
    suspend fun upsertAudit(audit: NightAudit)

    @Query("SELECT * FROM night_audits WHERE isDeleted = 0 ORDER BY businessDate DESC LIMIT :limit")
    fun observeAudits(limit: Int = 30): Flow<List<NightAudit>>

    @Query("SELECT * FROM night_audits WHERE isDeleted = 0 ORDER BY businessDate DESC LIMIT 1")
    suspend fun lastAudit(): NightAudit?

    @Query("SELECT * FROM night_audits WHERE businessDate = :date AND isDeleted = 0 LIMIT 1")
    suspend fun auditFor(date: String): NightAudit?

    @Query("SELECT COUNT(DISTINCT roomId) FROM bookings WHERE isDeleted = 0 AND status IN ('CHECKED_IN','CHECKED_OUT') AND checkInDate <= :date AND checkOutDate > :date")
    suspend fun roomsSoldOn(date: String): Int

    @Query("SELECT COUNT(*) FROM hotel_rooms WHERE isDeleted = 0 AND isActive = 1")
    suspend fun roomCount(): Int

    @Query("SELECT COALESCE(SUM(amount), 0) FROM folio_charges WHERE isDeleted = 0 AND chargeDate = :date")
    suspend fun chargesOn(date: String): Double

    @Query("SELECT COALESCE(SUM(amount), 0) FROM hotel_payments WHERE isDeleted = 0 AND substr(paidAt, 1, 10) = :date")
    suspend fun paymentsOn(date: String): Double

    @Query("SELECT * FROM bookings WHERE isDeleted = 0 AND status = 'BOOKED' AND checkInDate = :date")
    suspend fun dueArrivals(date: String): List<Booking>

    @Query("UPDATE bookings SET status = 'NO_SHOW', updatedAt = :now WHERE id IN (:ids)")
    suspend fun markNoShow(ids: List<String>, now: String)
}
