package com.derycode.deryaccount.desktop.data

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Money and date formatting shared by every screen. UGX has no cents in daily use. */
object Fmt {
    private val plain = DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US))
    private val cents = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))

    fun r2(v: Double): Double = (v * 100.0).roundToLong() / 100.0

    fun money(v: Double): String = "UGX " + plain.format(v)

    fun moneySigned(v: Double): String = if (v < 0) "-UGX " + plain.format(abs(v)) else money(v)

    /** Quantities: 12 not 12.0, but 2.75 keeps its decimals. */
    fun qty(v: Double): String =
        if (abs(v - v.roundToLong()) < 0.0001) plain.format(v.roundToLong()) else cents.format(v)

    fun pct(v: Double): String = String.format(Locale.US, "%.1f%%", v)

    fun compact(v: Double): String = when {
        abs(v) >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", v / 1_000_000_000)
        abs(v) >= 1_000_000 -> String.format(Locale.US, "%.1fM", v / 1_000_000)
        abs(v) >= 10_000 -> String.format(Locale.US, "%.0fK", v / 1_000)
        else -> plain.format(v)
    }

    private val dmy = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US)
    private val dmyShort = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.US)
    private val hm = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    fun date(iso: String): String = runCatching {
        LocalDate.parse(iso.take(10)).format(dmy)
    }.getOrDefault(iso.take(10))

    fun dateShort(iso: String): String = runCatching {
        LocalDate.parse(iso.take(10)).format(dmyShort)
    }.getOrDefault(iso.take(10))

    fun dateTime(iso: String): String = runCatching {
        val ldt = LocalDateTime.ofInstant(Instant.parse(iso), ZoneId.systemDefault())
        ldt.format(dmy) + " " + ldt.format(hm)
    }.getOrElse { date(iso) }

    fun month(ym: String): String = runCatching {
        val d = LocalDate.parse("$ym-01")
        d.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US))
    }.getOrDefault(ym)
}

object Clock {
    fun nowIso(): String = Instant.now().toString()

    fun today(): String = LocalDate.now().toString()

    fun thisMonth(): String = LocalDate.now().toString().take(7)

    fun monthStart(): String = LocalDate.now().withDayOfMonth(1).toString()

    fun daysAgoIso(days: Long): String =
        LocalDate.now().minusDays(days).toString() + "T00:00:00Z"

    fun monthName(ym: String): String = Fmt.month(ym)

    fun shiftMonth(ym: String, delta: Long): String = runCatching {
        LocalDate.parse("$ym-01").plusMonths(delta).toString().take(7)
    }.getOrDefault(ym)
}

/** Minimal ISO-8601 CSV writer/reader — no dependency, RFC 4180 quoting. */
object Csv {
    fun write(headers: List<String>, rows: List<List<Any?>>): String {
        val sb = StringBuilder()
        sb.append(headers.joinToString(",") { esc(it) }).append("\r\n")
        rows.forEach { r ->
            sb.append(r.joinToString(",") { esc(it?.toString() ?: "") }).append("\r\n")
        }
        return sb.toString()
    }

    private fun esc(s: String): String =
        if (s.contains(',') || s.contains('"') || s.contains('\n') || s.contains('\r'))
            "\"" + s.replace("\"", "\"\"") + "\"" else s

    /** Parses RFC 4180 text into rows. Tolerates CRLF and stray blank lines. */
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var field = StringBuilder()
        var row = mutableListOf<String>()
        var inQuotes = false
        var i = 0
        val t = text.removePrefix("\uFEFF")
        while (i < t.length) {
            val c = t[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < t.length && t[i + 1] == '"' -> { field.append('"'); i++ }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> { row.add(field.toString()); field = StringBuilder() }
                c == '\r' -> { /* swallow, handled by \n */ }
                c == '\n' -> {
                    row.add(field.toString()); field = StringBuilder()
                    if (row.any { it.isNotBlank() }) rows.add(row)
                    row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            if (row.any { it.isNotBlank() }) rows.add(row)
        }
        return rows
    }
}

fun Double.orZero(): Double = if (isFinite()) this else 0.0

/** Stable ids and PIN hashing — the same shapes the phone app stores. */
object Uuid {
    fun v4(): String = java.util.UUID.randomUUID().toString()

    fun hash(value: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
