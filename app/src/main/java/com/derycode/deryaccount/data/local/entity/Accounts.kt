package com.derycode.deryaccount.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * BOOKS OF ACCOUNT — full double-entry core, 100% offline.
 * Cash book, petty cash book, ledgers, trial balance and income
 * statement are all derived from these three tables.
 */

/** Chart of accounts — the bookkeeper's account list. */
@Entity(tableName = "accounts", indices = [Index("code")])
data class Account(
    @PrimaryKey val id: String,
    val code: String,          // e.g. "1000", "4000"
    val name: String,          // e.g. "Cash on Hand", "Sales"
    val type: String,          // ASSET | LIABILITY | EQUITY | INCOME | EXPENSE
    val isCash: Boolean = false, // cash book / petty cash accounts
    val sortOrder: Int = 0
)

/** One voucher/transaction — must always balance (debits == credits). */
@Entity(tableName = "journal_entries", indices = [Index("entryDate")])
data class JournalEntry(
    @PrimaryKey val id: String,
    val entryDate: String,     // ISO datetime
    val voucherNo: String,      // e.g. CB-0001
    val particulars: String,    // description
    val source: String = "MANUAL", // MANUAL | POS | EXPENSE | CAPITAL
    val branchId: String? = null
)

/** The Dr/Cr lines of an entry. */
@Entity(tableName = "journal_lines", indices = [Index("entryId"), Index("accountId")])
data class JournalLine(
    @PrimaryKey val id: String,
    val entryId: String,
    val accountId: String,
    val debit: Double = 0.0,
    val credit: Double = 0.0
)
