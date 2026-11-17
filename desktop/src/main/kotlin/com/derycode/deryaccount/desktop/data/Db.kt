package com.derycode.deryaccount.desktop.data

import java.io.File
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.util.Locale
import java.util.UUID

/** A read row with forgiving accessors — keeps report code short and null-safe. */
class Row(private val m: Map<String, Any?>) {
    operator fun get(key: String): Any? = m[key]
    fun str(key: String, def: String = ""): String = when (val v = m[key]) {
        null -> def
        is String -> v
        is ByteArray -> String(v)
        else -> v.toString()
    }
    fun dbl(key: String, def: Double = 0.0): Double = when (val v = m[key]) {
        null -> def
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull() ?: def
        else -> def
    }
    fun int(key: String, def: Int = 0): Int = when (val v = m[key]) {
        null -> def
        is Number -> v.toInt()
        is String -> v.toIntOrNull() ?: def
        else -> def
    }
    fun bool(key: String): Boolean = when (val v = m[key]) {
        null -> false
        is Boolean -> v
        is Number -> v.toInt() != 0
        is String -> v == "1" || v.equals("true", true)
        else -> false
    }
    fun has(key: String): Boolean = m.containsKey(key)
    fun keys(): Set<String> = m.keys
}

/**
 * The whole desktop app talks to one local SQLite file. Offline by design:
 * no server, no login server, no internet requirement.
 */
class DatabaseShop(val file: File) : AutoCloseable {

    val conn: Connection = run {
        // The folder must exist before SQLite opens the file — first run on a fresh PC.
        file.parentFile?.mkdirs()
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}")
        }
        .also { c ->
            c.createStatement().use { st ->
                st.execute("PRAGMA journal_mode=WAL")
                st.execute("PRAGMA foreign_keys=ON")
                st.execute("PRAGMA busy_timeout=5000")
            }
        }

    init {
        createSchema()
        seed()
    }

    // ── Schema ────────────────────────────────────────────────────────────────

    private fun createSchema() {
        conn.autoCommit = true
        conn.createStatement().use { st -> DDL.forEach { st.execute(it) } }
        // Columns added after the first release are applied in place — an existing
        // hotel's database keeps every booking it already has.
        addColumns(
            "bookings" to listOf(
                "agent_id" to "TEXT", "agent_name" to "TEXT", "commission_pct" to "REAL DEFAULT 0",
                "group_name" to "TEXT", "company" to "TEXT", "market" to "TEXT", "cancel_reason" to "TEXT"
            ),
            "guests" to listOf(
                "vip" to "INTEGER DEFAULT 0", "loyalty" to "INTEGER DEFAULT 0",
                "id_type" to "TEXT", "preferences" to "TEXT", "blacklist" to "INTEGER DEFAULT 0"
            ),
            "housekeeping_tasks" to listOf("priority" to "TEXT DEFAULT 'NORMAL'", "updated_at" to "TEXT"),
            "rooms" to listOf("held_until" to "TEXT")
        )
        meta("schema_version", "2")
    }

    /** Adds any missing column to an existing table (SQLite has no IF NOT EXISTS for columns). */
    private fun addColumns(vararg tables: Pair<String, List<Pair<String, String>>>) {
        tables.forEach { (table, columns) ->
            val existing = q("PRAGMA table_info(`" + table + "`)").map { it.str("name") }.toSet()
            columns.forEach { (name, type) ->
                if (name !in existing) {
                    exec("ALTER TABLE `" + table + "` ADD COLUMN `" + name + "` " + type)
                }
            }
        }
    }

    private fun seed() {
        // Chart of accounts — the same one the phone app uses, so books match.
        val accounts = Accounts.COA
        val existing = q("SELECT code FROM accounts").map { it.str("code") }.toSet()
        accounts.filter { it.code !in existing }.forEach { a ->
            insert("accounts", mapOf(
                "id" to a.id, "code" to a.code, "name" to a.name, "type" to a.type,
                "is_cash" to if (a.isCash) 1 else 0, "sort_order" to a.sortOrder
            ))
        }
        if (q("SELECT id FROM branches LIMIT 1").isEmpty()) {
            insert("branches", mapOf(
                "id" to "br-main", "name" to "Main Branch", "location" to "",
                "is_active" to 1, "created_at" to Clock.nowIso()
            ))
        }
        if (q("SELECT id FROM users LIMIT 1").isEmpty()) {
            insert("users", mapOf(
                "id" to "u-owner", "username" to "owner",
                "pin_hash" to sha256("1234"), "full_name" to "Owner",
                "role" to "OWNER", "branch_id" to "br-main",
                "is_active" to 1, "created_at" to Clock.nowIso()
            ))
        }
        if (q("SELECT id FROM profile LIMIT 1").isEmpty()) {
            insert("profile", mapOf(
                "id" to "profile", "name" to "", "phone" to "", "email" to "",
                "address" to "", "tin" to "", "motto" to "", "logo_path" to "",
                "updated_at" to Clock.nowIso()
            ))
        }
    }

    // ── Tiny query helpers ────────────────────────────────────────────────────
    private fun bind(ps: PreparedStatement, params: Array<out Any?>) {
        params.forEachIndexed { i, v ->
            when (v) {
                null -> ps.setObject(i + 1, null)
                is Int -> ps.setInt(i + 1, v)
                is Long -> ps.setLong(i + 1, v)
                is Double -> ps.setDouble(i + 1, v)
                is Float -> ps.setDouble(i + 1, v.toDouble())
                is Boolean -> ps.setInt(i + 1, if (v) 1 else 0)
                else -> ps.setString(i + 1, v.toString())
            }
        }
    }

    fun q(sql: String, vararg params: Any?): List<Row> {
        synchronized(conn) {
            conn.prepareStatement(sql).use { ps ->
                bind(ps, params)
                ps.executeQuery().use { rs ->
                    val md = rs.metaData
                    val cols = (1..md.columnCount).map { md.getColumnLabel(it) }
                    val out = mutableListOf<Row>()
                    while (rs.next()) {
                        val m = LinkedHashMap<String, Any?>(cols.size)
                        cols.forEachIndexed { idx, name -> m[name] = rs.getObject(idx + 1) }
                        out.add(Row(m))
                    }
                    return out
                }
            }
        }
    }

    fun one(sql: String, vararg params: Any?): Row? = q(sql, *params).firstOrNull()

    fun scalar(sql: String, vararg params: Any?): Double = one(sql, *params)?.let { r ->
        r.keys().firstOrNull()?.let { k -> r.dbl(k) } ?: 0.0
    } ?: 0.0

    fun exec(sql: String, vararg params: Any?): Int {
        synchronized(conn) {
            conn.prepareStatement(sql).use { ps ->
                bind(ps, params)
                return ps.executeUpdate()
            }
        }
    }

    fun insert(table: String, values: Map<String, Any?>): String {
        val id = (values["id"] as? String) ?: UUID.randomUUID().toString()
        val v = LinkedHashMap(values)
        v["id"] = id
        val cols = v.keys.toList()
        exec(
            "INSERT INTO $table (${cols.joinToString(",")}) VALUES (${cols.joinToString(",") { "?" }})",
            *v.values.toTypedArray()
        )
        return id
    }

    fun update(table: String, id: String, values: Map<String, Any?>) {
        if (values.isEmpty()) return
        val keys = values.keys.toList()
        val args = values.values.toMutableList<Any?>()
        args.add(id)
        exec("UPDATE $table SET ${keys.joinToString(",") { "$it=?" }} WHERE id=?", *args.toTypedArray())
    }

    fun delete(table: String, id: String) = exec("DELETE FROM $table WHERE id=?", id)

    fun count(table: String, where: String = "", vararg params: Any?): Int =
        one("SELECT COUNT(*) AS n FROM $table ${if (where.isBlank()) "" else "WHERE $where"}", *params)?.int("n") ?: 0

    fun <T> tx(block: () -> T): T {
        synchronized(conn) {
            if (!conn.autoCommit) return block()
            conn.autoCommit = false
            try {
                val r = block()
                conn.commit()
                return r
            } catch (e: Throwable) {
                try { conn.rollback() } catch (_: Throwable) {}
                throw e
            } finally {
                conn.autoCommit = true
            }
        }
    }

    fun meta(key: String, value: String? = null): String? {
        return if (value == null) {
            one("SELECT value FROM meta WHERE key=?", key)?.str("value")
        } else {
            exec("INSERT INTO meta(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value", key, value)
            value
        }
    }

    override fun close() {
        runCatching { conn.close() }
    }

    companion object {
        fun sha256(s: String): String {
            val d = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            return d.joinToString("") { String.format(Locale.US, "%02x", it) }
        }
    }
}

/** All desktop tables, created on first run. Additive only — never drops a table. */
private val DDL: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT)",
        "CREATE TABLE IF NOT EXISTS profile (id TEXT PRIMARY KEY, name TEXT, phone TEXT, email TEXT, " +
            "address TEXT, tin TEXT, motto TEXT, logo_path TEXT, updated_at TEXT)",
        "CREATE TABLE IF NOT EXISTS branches (id TEXT PRIMARY KEY, name TEXT, location TEXT, " +
            "is_active INTEGER DEFAULT 1, created_at TEXT)",
        "CREATE TABLE IF NOT EXISTS users (id TEXT PRIMARY KEY, username TEXT, pin_hash TEXT, " +
            "full_name TEXT, role TEXT, branch_id TEXT, is_active INTEGER DEFAULT 1, created_at TEXT)",
        "CREATE TABLE IF NOT EXISTS products (id TEXT PRIMARY KEY, name TEXT, barcode TEXT, category TEXT, " +
            "subcategory TEXT, image_path TEXT, unit TEXT, cost_price REAL DEFAULT 0, retail_price REAL DEFAULT 0, " +
            "wholesale_price REAL, wholesale_min_qty INTEGER DEFAULT 0, tax_rate REAL DEFAULT 0, " +
            "stock_qty REAL DEFAULT 0, low_stock_alert REAL DEFAULT 5, reorder_level REAL DEFAULT 0, " +
            "expiry_date TEXT, branch_id TEXT, is_favourite INTEGER DEFAULT 0, created_at TEXT, updated_at TEXT)",
        "CREATE TABLE IF NOT EXISTS customers (id TEXT PRIMARY KEY, name TEXT, phone TEXT, address TEXT, " +
            "type TEXT DEFAULT 'RETAIL', credit_limit REAL DEFAULT 0, balance REAL DEFAULT 0, " +
            "total_purchases REAL DEFAULT 0, total_paid REAL DEFAULT 0, created_at TEXT, updated_at TEXT)",
        "CREATE TABLE IF NOT EXISTS suppliers (id TEXT PRIMARY KEY, name TEXT, phone TEXT, address TEXT, " +
            "balance REAL DEFAULT 0, created_at TEXT, updated_at TEXT)",
        "CREATE TABLE IF NOT EXISTS sales (id TEXT PRIMARY KEY, receipt_no TEXT, branch_id TEXT, user_id TEXT, " +
            "customer_id TEXT, sale_type TEXT, subtotal REAL, tax_total REAL, discount REAL, total REAL, " +
            "amount_paid REAL, change_given REAL, payment_method TEXT, sold_at TEXT, shift_id TEXT, " +
            "note TEXT, created_at TEXT, updated_at TEXT)",
        "CREATE TABLE IF NOT EXISTS sale_items (id TEXT PRIMARY KEY, sale_id TEXT, product_id TEXT, " +
            "name TEXT, qty REAL, unit_price REAL, cost_price REAL, line_total REAL, tax_rate REAL DEFAULT 0)",
        "CREATE TABLE IF NOT EXISTS sales_returns (id TEXT PRIMARY KEY, sale_id TEXT, receipt_no TEXT, " +
            "branch_id TEXT, user_id TEXT, amount REAL, cost_value REAL, method TEXT, note TEXT, " +
            "returned_at TEXT, created_at TEXT)",
        "CREATE TABLE IF NOT EXISTS stock_movements (id TEXT PRIMARY KEY, product_id TEXT, branch_id TEXT, " +
            "type TEXT, qty REAL, reference TEXT, note TEXT, moved_at TEXT, created_at TEXT)",
        "CREATE TABLE IF NOT EXISTS purchases (id TEXT PRIMARY KEY, supplier_id TEXT, branch_id TEXT, " +
            "total REAL, paid_amount REAL, received_at TEXT, note TEXT, created_at TEXT)",
        "CREATE TABLE IF NOT EXISTS purchase_items (id TEXT PRIMARY KEY, purchase_id TEXT, product_id TEXT, " +
            "name TEXT, qty REAL, unit_cost REAL, line_total REAL)",
        "CREATE TABLE IF NOT EXISTS purchase_orders (id TEXT PRIMARY KEY, po_no TEXT, supplier_id TEXT, " +
            "branch_id TEXT, user_id TEXT, status TEXT DEFAULT 'OPEN', expected_at TEXT, note TEXT, " +
            "ordered_at TEXT, created_at TEXT, updated_at TEXT)",
        "CREATE TABLE IF NOT EXISTS purchase_order_items (id TEXT PRIMARY KEY, order_id TEXT, product_id TEXT, " +
            "name TEXT, qty REAL, unit_cost REAL, line_total REAL)",
        "CREATE TABLE IF NOT EXISTS purchase_returns (id TEXT PRIMARY KEY, pr_no TEXT, supplier_id TEXT, " +
            "branch_id TEXT, user_id TEXT, total REAL, refund_method TEXT, note TEXT, returned_at TEXT, created_at TEXT)",
        "CREATE TABLE IF NOT EXISTS purchase_return_items (id TEXT PRIMARY KEY, purchase_return_id TEXT, " +
            "product_id TEXT, name TEXT, qty REAL, unit_cost REAL, line_total REAL)",
        "CREATE TABLE IF NOT EXISTS expenses (id TEXT PRIMARY KEY, branch_id TEXT, user_id TEXT, category TEXT, " +
            "amount REAL, note TEXT, paid_from TEXT, spent_at TEXT, created_at TEXT)",
        "CREATE TABLE IF NOT EXISTS customer_payments (id TEXT PRIMARY KEY, customer_id TEXT, branch_id TEXT, " +
            "user_id TEXT, amount REAL, method TEXT, reference TEXT, note TEXT, paid_at TEXT, created_at TEXT)",
        "CREATE TABLE IF NOT EXISTS supplier_payments (id TEXT PRIMARY KEY, supplier_id TEXT, branch_id TEXT, " +
            "user_id TEXT, amount REAL, method TEXT, reference TEXT, note TEXT, paid_at TEXT, created_at TEXT)",
        "CREATE TABLE IF NOT EXISTS accounts (id TEXT PRIMARY KEY, code TEXT, name TEXT, type TEXT, " +
            "is_cash INTEGER DEFAULT 0, sort_order INTEGER DEFAULT 0)",
        "CREATE TABLE IF NOT EXISTS journal_entries (id TEXT PRIMARY KEY, entry_date TEXT, voucher_no TEXT, " +
            "particulars TEXT, source TEXT, branch_id TEXT, created_at TEXT)",
        "CREATE TABLE IF NOT EXISTS journal_lines (id TEXT PRIMARY KEY, entry_id TEXT, account_id TEXT, " +
            "debit REAL DEFAULT 0, credit REAL DEFAULT 0)",
        "CREATE TABLE IF NOT EXISTS shifts (id TEXT PRIMARY KEY, branch_id TEXT, user_id TEXT, opened_at TEXT, " +
            "closed_at TEXT, opening_cash REAL, closing_cash REAL, expected_cash REAL, variance REAL, note TEXT)",
        "CREATE TABLE IF NOT EXISTS cash_movements (id TEXT PRIMARY KEY, branch_id TEXT, user_id TEXT, " +
            "shift_id TEXT, type TEXT, amount REAL, note TEXT, moved_at TEXT, created_at TEXT)",
        "CREATE TABLE IF NOT EXISTS stock_transfers (id TEXT PRIMARY KEY, from_branch_id TEXT, to_branch_id TEXT, " +
            "product_id TEXT, qty REAL, status TEXT, note TEXT, transferred_at TEXT, created_at TEXT)",
        "CREATE TABLE IF NOT EXISTS held_sales (id TEXT PRIMARY KEY, branch_id TEXT, user_id TEXT, " +
            "lines_json TEXT, note TEXT, discount REAL, created_at TEXT)",
        "CREATE TABLE IF NOT EXISTS fixed_assets (id TEXT PRIMARY KEY, name TEXT, category TEXT, cost REAL, " +
            "salvage REAL DEFAULT 0, purchase_date TEXT, life_months INTEGER, " +
            "accumulated_depreciation REAL DEFAULT 0, sold_at TEXT, created_at TEXT)",
        "CREATE TABLE IF NOT EXISTS employees (id TEXT PRIMARY KEY, name TEXT, role TEXT, phone TEXT, " +
            "monthly_salary REAL, is_active INTEGER DEFAULT 1, created_at TEXT)",
        "CREATE TABLE IF NOT EXISTS payslips (id TEXT PRIMARY KEY, employee_id TEXT, employee_name TEXT, " +
            "month TEXT, gross REAL, deductions REAL, net REAL, method TEXT, note TEXT, paid_at TEXT, created_at TEXT)",
        "CREATE TABLE IF NOT EXISTS budgets (id TEXT PRIMARY KEY, month TEXT, kind TEXT, category TEXT, " +
            "amount REAL, created_at TEXT)",
        "CREATE TABLE IF NOT EXISTS bank_lines (id TEXT PRIMARY KEY, branch_id TEXT, statement_date TEXT, " +
            "description TEXT, direction TEXT, amount REAL, is_matched INTEGER DEFAULT 0, reference TEXT, " +
            "note TEXT, created_at TEXT)",
        "CREATE TABLE IF NOT EXISTS batches (id TEXT PRIMARY KEY, product_id TEXT, product_name TEXT, " +
            "batch_no TEXT, expiry_date TEXT, qty REAL, branch_id TEXT, note TEXT, created_at TEXT)",
        "CREATE TABLE IF NOT EXISTS serials (id TEXT PRIMARY KEY, product_id TEXT, product_name TEXT, " +
            "serial TEXT, status TEXT DEFAULT 'IN_STOCK', sold_at TEXT, branch_id TEXT, created_at TEXT)",
        // ── Hotel & accommodation ────────────────────────────────────────────
        "CREATE TABLE IF NOT EXISTS room_types (id TEXT PRIMARY KEY, name TEXT, base_rate REAL DEFAULT 0, " +
            "capacity INTEGER DEFAULT 2, beds TEXT, description TEXT, tax_rate REAL DEFAULT 0, created_at TEXT)",
        "CREATE TABLE IF NOT EXISTS rooms (id TEXT PRIMARY KEY, number TEXT, room_type_id TEXT, floor TEXT, " +
            "status TEXT DEFAULT 'VACANT_CLEAN', rate_override REAL DEFAULT 0, note TEXT, " +
            "branch_id TEXT, is_active INTEGER DEFAULT 1, created_at TEXT, updated_at TEXT)",
        "CREATE TABLE IF NOT EXISTS guests (id TEXT PRIMARY KEY, name TEXT, phone TEXT, email TEXT, " +
            "id_number TEXT, nationality TEXT, address TEXT, notes TEXT, created_at TEXT, updated_at TEXT)",
        "CREATE TABLE IF NOT EXISTS bookings (id TEXT PRIMARY KEY, ref_no TEXT, guest_id TEXT, guest_name TEXT, " +
            "guest_phone TEXT, room_id TEXT, room_type_id TEXT, check_in_date TEXT, check_out_date TEXT, " +
            "adults INTEGER DEFAULT 1, children INTEGER DEFAULT 0, nights INTEGER DEFAULT 1, rate REAL DEFAULT 0, " +
            "status TEXT DEFAULT 'BOOKED', source TEXT DEFAULT 'WALK_IN', notes TEXT, " +
            "invoice_total REAL DEFAULT 0, paid_total REAL DEFAULT 0, checked_in_at TEXT, checked_out_at TEXT, " +
            "user_id TEXT, branch_id TEXT, created_at TEXT, updated_at TEXT)",
        "CREATE TABLE IF NOT EXISTS folio_charges (id TEXT PRIMARY KEY, booking_id TEXT, kind TEXT, " +
            "description TEXT, qty REAL DEFAULT 1, unit_price REAL DEFAULT 0, amount REAL DEFAULT 0, " +
            "tax_amount REAL DEFAULT 0, charge_date TEXT, posted_at TEXT, posted_by TEXT, created_at TEXT)",
        "CREATE TABLE IF NOT EXISTS hotel_payments (id TEXT PRIMARY KEY, booking_id TEXT, amount REAL, " +
            "method TEXT, reference TEXT, note TEXT, paid_at TEXT, user_id TEXT, created_at TEXT)",
        "CREATE TABLE IF NOT EXISTS housekeeping_tasks (id TEXT PRIMARY KEY, room_id TEXT, kind TEXT, " +
            "note TEXT, status TEXT DEFAULT 'OPEN', assigned_to TEXT, created_at TEXT, done_at TEXT)",
        // ── Hotel parity: rates, agents, night audit ─────────────────────────
        "CREATE TABLE IF NOT EXISTS rate_plans (id TEXT PRIMARY KEY, name TEXT, room_type_id TEXT, " +
            "kind TEXT DEFAULT 'EP', price_delta REAL DEFAULT 0, valid_from TEXT, valid_to TEXT, " +
            "weekend_delta REAL DEFAULT 0, min_nights INTEGER DEFAULT 1, is_active INTEGER DEFAULT 1, " +
            "created_at TEXT, updated_at TEXT)",
        "CREATE TABLE IF NOT EXISTS rate_overrides (id TEXT PRIMARY KEY, room_type_id TEXT, " +
            "rate_date TEXT, rate REAL, note TEXT, created_at TEXT)",
        "CREATE INDEX IF NOT EXISTS idx_rate_override ON rate_overrides(room_type_id, rate_date)",
        "CREATE TABLE IF NOT EXISTS agents (id TEXT PRIMARY KEY, name TEXT, kind TEXT DEFAULT 'TRAVEL_AGENT', " +
            "contact TEXT, phone TEXT, email TEXT, commission_pct REAL DEFAULT 0, credit_limit REAL DEFAULT 0, " +
            "notes TEXT, is_active INTEGER DEFAULT 1, created_at TEXT, updated_at TEXT)",
        "CREATE TABLE IF NOT EXISTS agent_invoices (id TEXT PRIMARY KEY, agent_id TEXT, invoice_no TEXT, " +
            "period_from TEXT, period_to TEXT, amount REAL DEFAULT 0, paid REAL DEFAULT 0, " +
            "status TEXT DEFAULT 'OPEN', issued_at TEXT, due_date TEXT, note TEXT, created_at TEXT)",
        "CREATE TABLE IF NOT EXISTS night_audits (id TEXT PRIMARY KEY, business_date TEXT, closed_at TEXT, " +
            "closed_by TEXT, rooms_sold INTEGER DEFAULT 0, rooms_available INTEGER DEFAULT 0, " +
            "revenue REAL DEFAULT 0, payments REAL DEFAULT 0, no_shows INTEGER DEFAULT 0, note TEXT)",
        "CREATE TABLE IF NOT EXISTS maintenance_tickets (id TEXT PRIMARY KEY, room_id TEXT, issue TEXT, " +
            "priority TEXT DEFAULT 'NORMAL', status TEXT DEFAULT 'OPEN', reported_by TEXT, " +
            "assigned_to TEXT, cost REAL DEFAULT 0, created_at TEXT, done_at TEXT)",
        "CREATE INDEX IF NOT EXISTS idx_bookings_dates ON bookings(check_in_date, check_out_date)",
        "CREATE INDEX IF NOT EXISTS idx_bookings_room ON bookings(room_id)",
        "CREATE INDEX IF NOT EXISTS idx_charges_booking ON folio_charges(booking_id)",
        "CREATE INDEX IF NOT EXISTS idx_sales_sold ON sales(sold_at)",
        "CREATE INDEX IF NOT EXISTS idx_items_sale ON sale_items(sale_id)",
        "CREATE INDEX IF NOT EXISTS idx_lines_entry ON journal_lines(entry_id)",
        "CREATE INDEX IF NOT EXISTS idx_lines_account ON journal_lines(account_id)",
        "CREATE INDEX IF NOT EXISTS idx_moves_product ON stock_movements(product_id)",
        "CREATE INDEX IF NOT EXISTS idx_products_branch ON products(branch_id)"
        )
