package com.derycode.deryaccount.accounting

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Money — every arithmetic rule the books depend on, in one pure place.
 *
 * Kept free of Android imports on purpose: this is the only code allowed to
 * decide how a shilling is rounded, taxed and balanced, and it is covered by
 * JVM unit tests (app/src/test). If a screen needs a total, it asks here.
 *
 * Conventions:
 *  - All amounts are 2-decimal and rounded half-up on the cent.
 *  - Retail prices in Uganda are quoted VAT-INCLUSIVE. A product with taxRate
 *    of 18 therefore contains 18/118 of VAT, it does not add 18% on top of the
 *    shelf price. Exempt goods carry taxRate = 0 and produce no tax.
 *  - A sale balances by construction: total = subtotal + tax - discount.
 */
object Money {

    /** Half a cent: differences smaller than this are rounding noise, not errors. */
    const val EPSILON = 0.005

    fun round2(value: Double): Double = (value * 100.0).roundToInt() / 100.0

    /**
     * VAT contained inside a tax-inclusive line total.
     * 118,000 at 18% -> 18,000. A rate of 0 (exempt) -> 0.
     */
    fun vatInclusive(gross: Double, ratePercent: Double): Double {
        if (gross <= 0.0 || ratePercent <= 0.0) return 0.0
        return round2(gross - gross / (1.0 + ratePercent / 100.0))
    }

    /** The revenue part of a tax-inclusive amount. */
    fun netOfTax(gross: Double, tax: Double): Double = round2(gross - tax)

    data class Totals(
        val subtotal: Double,
        val taxTotal: Double,
        val discount: Double,
        val total: Double
    )

    /**
     * Totals for a basket. Each line is (tax-inclusive line total, VAT %).
     * Tax is computed per line and then summed, so a mixed basket of exempt and
     * standard-rated goods is exact to the cent and the receipt adds up.
     *
     * Because prices are inclusive, the customer pays subtotal - discount. The
     * tax is *inside* that figure, never added to it:
     *   total = netRevenue + tax - discount.
     */
    fun totals(lines: List<Pair<Double, Double>>, discount: Double): Totals {
        val subtotal = round2(lines.sumOf { it.first })
        val tax = round2(lines.sumOf { vatInclusive(it.first, it.second) })
        val disc = round2(discount.coerceAtLeast(0.0)).coerceAtMost(subtotal)
        val total = round2(subtotal - disc)
        return Totals(subtotal = subtotal, taxTotal = tax, discount = disc, total = total)
    }

    /** Revenue excluding VAT for a basket: subtotal - tax. */
    fun netRevenue(subtotal: Double, taxTotal: Double): Double = round2(subtotal - taxTotal)

    fun balanced(debits: List<Double>, credits: List<Double>): Boolean {
        val d = debits.sumOf { it }
        val c = credits.sumOf { it }
        return d > EPSILON && abs(d - c) < EPSILON
    }

    /** Thrown instead of writing an unbalanced entry. */
    fun requireBalanced(particulars: String, debits: List<Double>, credits: List<Double>) {
        val d = round2(debits.sumOf { it })
        val c = round2(credits.sumOf { it })
        require(d > 0.0) { "Entry '$particulars' has no amount (Dr $d / Cr $c)" }
        require(abs(d - c) < EPSILON) { "Entry '$particulars' does not balance: Dr $d vs Cr $c" }
    }

    /** BRANCHCODE-YYYYMMDD-#### — the receipt format the shop already uses. */
    fun receiptNo(branchCode: String, yyyymmdd: String, sequence: Int): String {
        val code = branchCode.uppercase().take(3).padEnd(3, 'X')
        return "%s-%s-%04d".format(code, yyyymmdd, sequence)
    }

    /** Cash still owed on a sale. Never negative, never silently rounded away. */
    fun outstanding(total: Double, paid: Double): Double =
        round2((total - paid).coerceAtLeast(0.0))

    /** Cash returned to the customer: only when they overpaid. */
    fun changeDue(total: Double, paid: Double): Double =
        round2((paid - total).coerceAtLeast(0.0))
}
