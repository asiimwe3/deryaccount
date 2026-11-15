package com.derycode.deryaccount.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * HOTEL & ACCOMMODATION — guests, rooms, reservations and the guest bill.
 *
 * Same shape as every other table: offline first, syncable, and money only moves
 * through the journal. A deposit is a liability (money held for the guest),
 * revenue is recognised when the guest checks out.
 */

/** A category of room: Single, Double, Twin, Suite… with its nightly rate. */
@Entity(tableName = "room_types")
data class RoomType(
    @PrimaryKey val id: String,
    val name: String,
    val baseRate: Double,
    val capacity: Int = 2,
    val beds: String = "",
    val description: String = "",
    val taxRate: Double = 0.0,        // VAT % already inside the rate
    override val createdAt: String,
    override val updatedAt: String,
    override val syncState: String = "pending",
    override val isDeleted: Boolean = false
) : Syncable

/** A physical room the front desk can sell. */
@Entity(tableName = "hotel_rooms", indices = [Index("number"), Index("roomTypeId")])
data class HotelRoom(
    @PrimaryKey val id: String,
    val number: String,
    val roomTypeId: String,
    val floor: String = "",
    val status: String = "VACANT_CLEAN",  // VACANT_CLEAN | VACANT_DIRTY | OCCUPIED | OUT_OF_ORDER
    val rateOverride: Double = 0.0,
    val note: String = "",
    val branchId: String,
    val isActive: Boolean = true,
    override val createdAt: String,
    override val updatedAt: String,
    override val syncState: String = "pending",
    override val isDeleted: Boolean = false
) : Syncable

/** A guest on file, with their ID and contact details. */
@Entity(tableName = "guests", indices = [Index("phone")])
data class Guest(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String = "",
    val email: String = "",
    val idNumber: String = "",
    val nationality: String = "",
    val address: String = "",
    val notes: String = "",
    override val createdAt: String,
    override val updatedAt: String,
    override val syncState: String = "pending",
    override val isDeleted: Boolean = false
) : Syncable

/** A reservation. Also the folio header once the guest is in house. */
@Entity(tableName = "bookings", indices = [Index("checkInDate"), Index("roomId"), Index("status")])
data class Booking(
    @PrimaryKey val id: String,
    val refNo: String,
    val guestId: String? = null,
    val guestName: String,
    val guestPhone: String = "",
    val roomId: String,
    val roomTypeId: String? = null,
    val checkInDate: String,          // yyyy-MM-dd
    val checkOutDate: String,
    val adults: Int = 1,
    val children: Int = 0,
    val nights: Int = 1,
    val rate: Double = 0.0,
    val status: String = "BOOKED",    // BOOKED | CHECKED_IN | CHECKED_OUT | CANCELLED | NO_SHOW
    val source: String = "WALK_IN",
    val notes: String = "",
    val invoiceTotal: Double = 0.0,
    val paidTotal: Double = 0.0,
    val checkedInAt: String? = null,
    val checkedOutAt: String? = null,
    val userId: String,
    val branchId: String,
    override val createdAt: String,
    override val updatedAt: String,
    override val syncState: String = "pending",
    override val isDeleted: Boolean = false
) : Syncable

/** One line on the guest bill: a room night, dinner, laundry, a discount… */
@Entity(tableName = "folio_charges", indices = [Index("bookingId")])
data class FolioCharge(
    @PrimaryKey val id: String,
    val bookingId: String,
    val kind: String,                 // ROOM | RESTAURANT | BAR | LAUNDRY | EXTRA | DISCOUNT
    val description: String,
    val qty: Double = 1.0,
    val unitPrice: Double = 0.0,
    val amount: Double = 0.0,         // negative for a discount
    val taxAmount: Double = 0.0,
    val chargeDate: String,
    val postedAt: String,
    val postedBy: String = "",
    override val createdAt: String,
    override val updatedAt: String,
    override val syncState: String = "pending",
    override val isDeleted: Boolean = false
) : Syncable

/** Money received from a guest (deposit, part payment, balance on check-out). */
@Entity(tableName = "hotel_payments", indices = [Index("bookingId")])
data class HotelPayment(
    @PrimaryKey val id: String,
    val bookingId: String,
    val amount: Double,
    val method: String,               // CASH | MTN_MOMO | AIRTEL_MONEY | BANK
    val reference: String = "",
    val note: String = "",
    val paidAt: String,
    val userId: String,
    override val createdAt: String,
    override val updatedAt: String,
    override val syncState: String = "pending",
    override val isDeleted: Boolean = false
) : Syncable

/** Cleaning and maintenance jobs on the room board. */
@Entity(tableName = "housekeeping_tasks", indices = [Index("roomId")])
data class HousekeepingTask(
    @PrimaryKey val id: String,
    val roomId: String,
    val kind: String,                 // CLEAN | LINEN | MAINTENANCE | INSPECTION
    val note: String = "",
    val status: String = "OPEN",      // OPEN | DONE
    val assignedTo: String = "",
    val doneAt: String? = null,
    override val createdAt: String,
    override val updatedAt: String,
    override val syncState: String = "pending",
    override val isDeleted: Boolean = false
) : Syncable
