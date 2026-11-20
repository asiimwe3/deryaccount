package com.derycode.deryaccount.accounting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the money rules the ledger depends on.
 * These are the audit items "VAT is hardcoded to zero" and "entries must balance".
 */
class MoneyTest {

    // ── VAT ────────────────────────────────────────────────────────────────

    @Test fun `vat is extracted from a tax-inclusive price, not added to it`() {
        // A 118,000 shelf price at 18% contains 18,000 of VAT, it is not 139,240.
        assertEquals(18000.0, Money.vatInclusive(118000.0, 18.0), 0.001)
    }

    @Test fun `exempt goods produce no tax`() {
        assertEquals(0.0, Money.vatInclusive(50000.0, 0.0), 0.001)
    }

    @Test fun `tax rounds to the cent`() {
        assertEquals(0.15, Money.vatInclusive(1.0, 18.0), 0.001)   // 0.1525…
    }

    @Test fun `mixed basket of exempt and rated lines adds up`() {
        val totals = Money.totals(
            listOf(
                50000.0 to 0.0,    // exempt: no tax
                118000.0 to 18.0,  // 18,000 tax
                59000.0 to 18.0    // 9,000 tax
            ),
            discount = 0.0
        )
        assertEquals(227000.0, totals.subtotal, 0.001)
        assertEquals(27000.0, totals.taxTotal, 0.001)
        assertEquals(227000.0, totals.total, 0.001)  // inclusive prices: total == subtotal
    }

    @Test fun `discount reduces the total but never the tax already contained`() {
        val totals = Money.totals(listOf(118000.0 to 18.0), discount = 8000.0)
        assertEquals(18000.0, totals.taxTotal, 0.001)
        assertEquals(110000.0, totals.total, 0.001)
    }

    @Test fun `net revenue plus tax equals the gross`() {
        val gross = 118000.0
        val tax = Money.vatInclusive(gross, 18.0)
        assertEquals(gross, Money.netOfTax(gross, tax) + tax, 0.001)
    }

    // ── Part payment ───────────────────────────────────────────────────────

    @Test fun `part payment leaves exactly the unpaid amount outstanding`() {
        assertEquals(60000.0, Money.outstanding(100000.0, 40000.0), 0.001)
        assertEquals(0.0, Money.outstanding(100000.0, 100000.0), 0.001)
        assertEquals(0.0, Money.outstanding(100000.0, 120000.0), 0.001) // overpaid, not negative
    }

    @Test fun `change is only returned when the customer overpaid`() {
        assertEquals(20000.0, Money.changeDue(100000.0, 120000.0), 0.001)
        assertEquals(0.0, Money.changeDue(100000.0, 40000.0), 0.001)
    }

    // ── Double entry ───────────────────────────────────────────────────────

    @Test fun `balanced entries are accepted`() {
        assertTrue(Money.balanced(listOf(100000.0), listOf(60000.0, 40000.0)))
    }

    @Test fun `an unbalanced entry is rejected`() {
        assertFalse(Money.balanced(listOf(100000.0), listOf(60000.0)))
    }

    @Test fun `a zero-value entry is rejected`() {
        assertFalse(Money.balanced(listOf(0.0), listOf(0.0)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `requireBalanced throws instead of writing a bad journal`() {
        Money.requireBalanced("Sales", listOf(100.0), listOf(99.0))
    }

    @Test fun `a sale with vat and discount still posts a balanced entry`() {
        // Dr cash 110,000 + Dr discount 8,000  ==  Cr sales 100,000 + Cr VAT 18,000
        val gross = 118000.0
        val tax = Money.vatInclusive(gross, 18.0)
        val net = Money.netOfTax(gross, tax)
        Money.requireBalanced(
            "Sales",
            listOf(110000.0, 8000.0),
            listOf(net, tax)
        )
    }

    // ── Receipt numbers ────────────────────────────────────────────────────

    @Test fun `receipt numbers are branch coded, dated and zero padded`() {
        assertEquals("KAM-20261120-0007", Money.receiptNo("Kam", "20261120", 7))
    }

    @Test fun `receipt sequence stays sortable past 9999`() {
        assertEquals("KAM-20261120-10000", Money.receiptNo("KAM", "20261120", 10000))
    }

    @Test fun `adding VAT never moves the customer price`() {
        // Before stage 1 tax was hardcoded to zero, so a sale totalled
        // subtotal - discount. Treating prices as VAT-inclusive must leave that
        // total untouched: only the split between Sales and VAT Payable changes.
        val lines = listOf(45_000.0 to 18.0, 12_500.0 to 0.0, 8_000.0 to 18.0)
        for (discount in listOf(0.0, 3_000.0, 7_500.0)) {
            val legacyTotal = Money.round2(lines.sumOf { it.first } - discount)
            val totals = Money.totals(lines, discount)
            assertEquals(legacyTotal, totals.total, 0.0001)
            assertEquals(Money.round2(totals.subtotal - discount), totals.total, 0.0001)
        }
    }

    @Test fun `vat extracted never exceeds the money collected`() {
        val totals = Money.totals(listOf(118_000.0 to 18.0), 0.0)
        assertEquals(18_000.0, totals.taxTotal, 0.01)
        assertTrue("tax cannot exceed the gross", totals.taxTotal < totals.subtotal)
    }
}
