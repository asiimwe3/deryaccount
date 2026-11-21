package com.derycode.deryaccount.desktop

/**
 * Features — module switches for the desktop build.
 *
 * Kept in step with the Android app: the hotel desk is off in this build at the
 * owner's request. Nothing is deleted — hotel tables, bookings and guest records
 * stay exactly as they are, and setting this to true brings the desk back.
 */
object Features {
    const val HOTEL_DESK: Boolean = false
}
