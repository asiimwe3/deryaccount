package com.derycode.deryaccount.config

/**
 * Features — module switches for the shop build.
 *
 * The hotel desk was pulled from DeryAccount on 21 Nov 2026 at the owner's
 * request because it was destabilising the shop build. This is a switch, not a
 * deletion: no hotel code, table, booking or guest record is removed, so a shop
 * that already used the desk keeps every record and it can be switched back on
 * by returning this to true and shipping an update.
 */
object Features {
    const val HOTEL_DESK: Boolean = false
}
