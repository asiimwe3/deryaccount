package com.derycode.deryaccount.security

/**
 * AccessPolicy — the single place that decides what a role may do.
 *
 * Audit finding 8: permissions were enforced only by `role == "CASHIER"`
 * checks scattered across screens, so an ACCOUNTANT could open the POS and
 * sell, and a MANAGER could do everything an OWNER could. Screens now ask
 * this object instead of comparing role strings.
 */
enum class Capability {
    SELL,
    VOID_SALE,
    APPLY_DISCOUNT,
    EDIT_PRICE,
    ADJUST_STOCK,
    VIEW_BOOKS,
    POST_EXPENSE,
    VIEW_REPORTS,
    VIEW_ALL_BRANCHES,
    MANAGE_USERS,
    HOTEL_CHECKIN,
    HOTEL_CHECKOUT,
    NIGHT_AUDIT,
    MANAGE_SETTINGS,
    BACKUP_RESTORE,
    MANAGE_LICENCE
}

object AccessPolicy {

    const val OWNER = "OWNER"
    const val MANAGER = "MANAGER"
    const val ACCOUNTANT = "ACCOUNTANT"
    const val CASHIER = "CASHIER"

    /** Unknown roles fall back to CASHIER: the least privilege, never more. */
    fun normalize(role: String?): String = when (role?.trim()?.uppercase()) {
        OWNER -> OWNER
        MANAGER -> MANAGER
        ACCOUNTANT -> ACCOUNTANT
        else -> CASHIER
    }

    private val CASHIER_CAPS = setOf(
        Capability.SELL,
        Capability.HOTEL_CHECKIN,
        Capability.HOTEL_CHECKOUT
    )

    /**
     * Accountants keep every right they already have in the shipped build: the
     * books, expenses, reports AND the till. Shops are live, so taking a working
     * screen away has to be the owner's decision, never a side effect of a
     * refactor. Deleting Capability.SELL from this set is the one line that
     * removes the POS from accountants.
     */
    private val ACCOUNTANT_CAPS = CASHIER_CAPS + setOf(
        Capability.VIEW_BOOKS,
        Capability.POST_EXPENSE,
        Capability.VIEW_REPORTS,
        Capability.VIEW_ALL_BRANCHES
    )

    /** A manager runs the shop: the books AND the till. */
    private val MANAGER_CAPS = ACCOUNTANT_CAPS + CASHIER_CAPS + setOf(
        Capability.VOID_SALE,
        Capability.APPLY_DISCOUNT,
        Capability.EDIT_PRICE,
        Capability.ADJUST_STOCK,
        Capability.MANAGE_USERS,
        Capability.NIGHT_AUDIT,
        Capability.MANAGE_SETTINGS,
        Capability.BACKUP_RESTORE
    )

    /** Only the owner can mint, move or clear a licence. */
    private val OWNER_CAPS = MANAGER_CAPS + Capability.MANAGE_LICENCE

    fun capabilitiesFor(role: String?): Set<Capability> = when (normalize(role)) {
        OWNER -> OWNER_CAPS
        MANAGER -> MANAGER_CAPS
        ACCOUNTANT -> ACCOUNTANT_CAPS
        else -> CASHIER_CAPS
    }

    fun can(role: String?, capability: Capability): Boolean =
        capability in capabilitiesFor(role)

    /**
     * Whether this role may read figures across every branch. Everyone else is
     * confined to the branch they signed in to, which is what keeps one shop's
     * takings out of another shop's reports.
     */
    fun seesAllBranches(role: String?): Boolean = can(role, Capability.VIEW_ALL_BRANCHES)

    /** The branch a query must be scoped to, or null when all are allowed. */
    fun branchScope(role: String?, sessionBranchId: String?): String? =
        if (seesAllBranches(role)) null else sessionBranchId
}
