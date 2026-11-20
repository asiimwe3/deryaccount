package com.derycode.deryaccount.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessPolicyTest {

    @Test
    fun `owner can do everything including the licence`() {
        Capability.values().forEach { assertTrue("owner lacks $it", AccessPolicy.can("OWNER", it)) }
    }

    @Test
    fun `a cashier cannot touch the books, discounts or the licence`() {
        assertTrue(AccessPolicy.can("CASHIER", Capability.SELL))
        assertFalse(AccessPolicy.can("CASHIER", Capability.VIEW_BOOKS))
        assertFalse(AccessPolicy.can("CASHIER", Capability.APPLY_DISCOUNT))
        assertFalse(AccessPolicy.can("CASHIER", Capability.VOID_SALE))
        assertFalse(AccessPolicy.can("CASHIER", Capability.ADJUST_STOCK))
        assertFalse(AccessPolicy.can("CASHIER", Capability.MANAGE_USERS))
        assertFalse(AccessPolicy.can("CASHIER", Capability.MANAGE_LICENCE))
    }

    @Test
    fun `an accountant keeps the books but cannot sell`() {
        assertTrue(AccessPolicy.can("ACCOUNTANT", Capability.VIEW_BOOKS))
        assertTrue(AccessPolicy.can("ACCOUNTANT", Capability.POST_EXPENSE))
        assertTrue(AccessPolicy.can("ACCOUNTANT", Capability.VIEW_REPORTS))
        assertFalse("an accountant must not ring up sales", AccessPolicy.can("ACCOUNTANT", Capability.SELL))
        assertFalse(AccessPolicy.can("ACCOUNTANT", Capability.ADJUST_STOCK))
        assertFalse(AccessPolicy.can("ACCOUNTANT", Capability.MANAGE_USERS))
    }

    @Test
    fun `a manager runs the shop but cannot move the licence`() {
        assertTrue(AccessPolicy.can("MANAGER", Capability.MANAGE_USERS))
        assertTrue(AccessPolicy.can("MANAGER", Capability.BACKUP_RESTORE))
        assertTrue(AccessPolicy.can("MANAGER", Capability.VOID_SALE))
        assertFalse(AccessPolicy.can("MANAGER", Capability.MANAGE_LICENCE))
    }

    @Test
    fun `an unknown or blank role gets cashier rights, never more`() {
        listOf("", "  ", "SUPERUSER", "admin", null).forEach { role ->
            assertEquals(AccessPolicy.CASHIER, AccessPolicy.normalize(role))
            assertFalse("$role must not reach the books", AccessPolicy.can(role, Capability.VIEW_BOOKS))
            assertFalse("$role must not manage users", AccessPolicy.can(role, Capability.MANAGE_USERS))
        }
    }

    @Test
    fun `roles are matched regardless of case or padding`() {
        assertTrue(AccessPolicy.can(" owner ", Capability.MANAGE_LICENCE))
        assertTrue(AccessPolicy.can("Cashier", Capability.SELL))
        assertTrue(AccessPolicy.can("accountant", Capability.VIEW_BOOKS))
    }

    @Test
    fun `a cashier is confined to the branch they signed in to`() {
        assertFalse(AccessPolicy.seesAllBranches("CASHIER"))
        assertEquals("BR-KYENJOJO", AccessPolicy.branchScope("CASHIER", "BR-KYENJOJO"))
        assertNull(AccessPolicy.branchScope("OWNER", "BR-KYENJOJO"))
        assertNull(AccessPolicy.branchScope("ACCOUNTANT", "BR-KYENJOJO"))
    }

    @Test
    fun `a branch scope of null means no branch was chosen, not all branches`() {
        // Guard against silently widening a query when the session has no branch.
        assertNull(AccessPolicy.branchScope("CASHIER", null))
    }
}
