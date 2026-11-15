package com.derycode.deryaccount.desktop.data

data class AccountDef(
    val id: String,
    val code: String,
    val name: String,
    val type: String,       // ASSET | LIABILITY | EQUITY | INCOME | EXPENSE
    val isCash: Boolean = false,
    val sortOrder: Int = 0
)

/** The chart of accounts, identical to the phone app so both books agree. */
object Accounts {
    val COA = listOf(
        AccountDef("acc-cash", "1000", "Cash on Hand", "ASSET", isCash = true, sortOrder = 1),
        AccountDef("acc-petty", "1001", "Petty Cash", "ASSET", isCash = true, sortOrder = 2),
        AccountDef("acc-bank", "1010", "Bank / Mobile Money", "ASSET", isCash = true, sortOrder = 3),
        AccountDef("acc-debtors", "1100", "Debtors (Receivables)", "ASSET", sortOrder = 4),
        AccountDef("acc-stock", "1200", "Stock on Hand", "ASSET", sortOrder = 5),
        AccountDef("acc-assets", "1300", "Fixed Assets", "ASSET", sortOrder = 6),
        AccountDef("acc-accdepr", "1350", "Accumulated Depreciation", "ASSET", sortOrder = 7),
        AccountDef("acc-creditors", "2000", "Creditors (Payables)", "LIABILITY", sortOrder = 8),
        AccountDef("acc-vatout", "2500", "VAT Payable", "LIABILITY", sortOrder = 9),
        AccountDef("acc-deposits", "2100", "Guest Deposits (Held)", "LIABILITY", sortOrder = 9),
        AccountDef("acc-capital", "3000", "Capital", "EQUITY", sortOrder = 10),
        AccountDef("acc-drawings", "3100", "Drawings", "EQUITY", sortOrder = 11),
        AccountDef("acc-sales", "4000", "Sales / Revenue", "INCOME", sortOrder = 12),
        AccountDef("acc-rooms", "4010", "Room Revenue (Accommodation)", "INCOME", sortOrder = 12),
        AccountDef("acc-fb", "4020", "Restaurant & Bar Revenue", "INCOME", sortOrder = 12),
        AccountDef("acc-guestrev", "4030", "Other Guest Services", "INCOME", sortOrder = 12),
        AccountDef("acc-disc", "4100", "Sales Discounts", "INCOME", sortOrder = 13),
        AccountDef("acc-returns", "4150", "Sales Returns", "INCOME", sortOrder = 14),
        AccountDef("acc-reval", "4900", "Stock Revaluation Gain", "INCOME", sortOrder = 15),
        AccountDef("acc-cogs", "5000", "Cost of Sales", "EXPENSE", sortOrder = 16),
        AccountDef("acc-purchases", "5100", "Purchases", "EXPENSE", sortOrder = 17),
        AccountDef("acc-rent", "5200", "Rent", "EXPENSE", sortOrder = 18),
        AccountDef("acc-salaries", "5210", "Salaries & Wages", "EXPENSE", sortOrder = 19),
        AccountDef("acc-transport", "5220", "Transport & Fuel", "EXPENSE", sortOrder = 20),
        AccountDef("acc-utilities", "5230", "Water & Electricity (UEL)", "EXPENSE", sortOrder = 21),
        AccountDef("acc-airtime", "5240", "Airtime & Data", "EXPENSE", sortOrder = 22),
        AccountDef("acc-depexp", "5310", "Depreciation", "EXPENSE", sortOrder = 23),
        AccountDef("acc-sundry", "5900", "Sundry / Other Expenses", "EXPENSE", sortOrder = 24),
        AccountDef("acc-writeoff", "5910", "Stock Losses (Damage & Shrinkage)", "EXPENSE", sortOrder = 25)
    )

    const val CASH = "acc-cash"
    const val PETTY = "acc-petty"
    const val BANK = "acc-bank"
    const val DEBTORS = "acc-debtors"
    const val STOCK = "acc-stock"
    const val FIXED_ASSETS = "acc-assets"
    const val ACC_DEPR = "acc-accdepr"
    const val CREDITORS = "acc-creditors"
    const val VAT_OUT = "acc-vatout"
    const val GUEST_DEPOSITS = "acc-deposits"
    const val ROOM_REVENUE = "acc-rooms"
    const val FB_REVENUE = "acc-fb"
    const val GUEST_REVENUE = "acc-guestrev"
    const val CAPITAL = "acc-capital"
    const val SALES = "acc-sales"
    const val SALES_DISCOUNT = "acc-disc"
    const val SALES_RETURNS = "acc-returns"
    const val REVALUATION = "acc-reval"
    const val COGS = "acc-cogs"
    const val PURCHASES = "acc-purchases"
    const val SALARY = "acc-salaries"
    const val DEPRECIATION = "acc-depexp"
    const val WRITEOFF = "acc-writeoff"
    const val SUNDRY = "acc-sundry"

    /** Expense categories map onto real ledger accounts, so the books stay honest. */
    val EXPENSE_ACCOUNTS = mapOf(
        "RENT" to "acc-rent",
        "SALARIES" to "acc-salaries",
        "TRANSPORT" to "acc-transport",
        "UTILITIES" to "acc-utilities",
        "AIRTIME" to "acc-airtime",
        "OTHER" to "acc-sundry"
    )

    fun accountForExpense(category: String): String =
        EXPENSE_ACCOUNTS[category.uppercase()] ?: SUNDRY

    /** Payment method → the account money lands in. */
    fun accountForMethod(method: String): String = when (method.uppercase()) {
        "MTN_MOMO", "AIRTEL_MONEY", "BANK", "MOMO" -> BANK
        "CREDIT" -> DEBTORS
        "PETTY" -> PETTY
        else -> CASH
    }
}
