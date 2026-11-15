package com.derycode.deryaccount.desktop.data

import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlin.math.min
import kotlin.random.Random

/** Fixed assets, payroll, budgets, bank reconciliation and the import/export tools. */
class AdminOps(private val repo: Repo) {
    private val db = repo.db
    private val acc = repo.accounting

    // ── Fixed assets ─────────────────────────────────────────────────────────
    fun assets(): List<Row> = db.q("SELECT * FROM fixed_assets ORDER BY purchase_date DESC")

    fun addAsset(
        name: String, category: String, cost: Double, salvage: Double,
        purchaseDate: String, lifeMonths: Int, paidFrom: String = "CASH"
    ): String {
        require(cost > 0 && lifeMonths > 0) { "Cost and useful life must be greater than zero" }
        val id = UUID.randomUUID().toString()
        db.tx {
            db.insert("fixed_assets", mapOf(
                "id" to id, "name" to name, "category" to category, "cost" to Fmt.r2(cost),
                "salvage" to Fmt.r2(salvage), "purchase_date" to purchaseDate,
                "life_months" to lifeMonths, "accumulated_depreciation" to 0.0,
                "created_at" to Clock.nowIso()
            ))
            acc.post(
                particulars = "Fixed asset — $name", source = "ASSET",
                debits = listOf(Accounts.FIXED_ASSETS to Fmt.r2(cost)),
                credits = listOf(Accounts.accountForMethod(paidFrom) to Fmt.r2(cost))
            )
        }
        return id
    }

    /**
     * Straight-line depreciation. Posts one month at a time and never depreciates
     * an asset below its salvage value; safe to press twice in the same month.
     */
    fun runDepreciation(months: Int = 1): Double {
        var total = 0.0
        val now = Clock.nowIso()
        db.tx {
            assets().filter { it.str("sold_at").isBlank() }.forEach { a ->
                val cost = a.dbl("cost")
                val salvage = a.dbl("salvage")
                val life = a.int("life_months").coerceAtLeast(1)
                val monthly = (cost - salvage) / life
                val depreciable = cost - salvage
                var accumulated = a.dbl("accumulated_depreciation")
                var posted = 0.0
                repeat(months) {
                    val remaining = depreciable - accumulated
                    if (remaining > 0.01) {
                        val amount = Fmt.r2(min(monthly, remaining))
                        accumulated += amount
                        posted += amount
                    }
                }
                if (posted > 0) {
                    db.update("fixed_assets", a.str("id"), mapOf("accumulated_depreciation" to Fmt.r2(accumulated)))
                    acc.postDepreciation(Fmt.r2(posted), a.str("name"))
                    total += posted
                }
            }
        }
        return Fmt.r2(total)
    }

    // ── Payroll ──────────────────────────────────────────────────────────────
    fun employees(): List<Row> = db.q("SELECT * FROM employees ORDER BY name")
    fun payslips(month: String = Clock.thisMonth()): List<Row> =
        db.q("SELECT * FROM payslips WHERE month=? ORDER BY employee_name", month)

    fun runPayroll(month: String, deductionPct: Double, method: String, employeeIds: List<String>): Double {
        require(employeeIds.isNotEmpty()) { "Select at least one employee" }
        var grossTotal = 0.0
        db.tx {
            employeeIds.forEach { id ->
                val e = db.one("SELECT * FROM employees WHERE id=?", id) ?: return@forEach
                val gross = Fmt.r2(e.dbl("monthly_salary"))
                val deductions = Fmt.r2(gross * (deductionPct / 100.0))
                val net = Fmt.r2(gross - deductions)
                db.insert("payslips", mapOf(
                    "id" to UUID.randomUUID().toString(), "employee_id" to id,
                    "employee_name" to e.str("name"), "month" to month, "gross" to gross,
                    "deductions" to deductions, "net" to net, "method" to method,
                    "note" to "Payroll ${Fmt.month(month)}", "paid_at" to Clock.nowIso(),
                    "created_at" to Clock.nowIso()
                ))
                grossTotal += gross
                acc.postPayroll(gross, net, deductions, month)
            }
        }
        return Fmt.r2(grossTotal)
    }

    // ── Budgets ──────────────────────────────────────────────────────────────
    fun budgets(month: String): List<Row> = db.q("SELECT * FROM budgets WHERE month=?", month)
    fun saveBudget(month: String, kind: String, category: String, amount: Double) {
        val existing = db.one("SELECT * FROM budgets WHERE month=? AND kind=? AND category=?", month, kind, category)
        if (existing == null) db.insert("budgets", mapOf(
            "id" to UUID.randomUUID().toString(), "month" to month, "kind" to kind,
            "category" to category, "amount" to Fmt.r2(amount), "created_at" to Clock.nowIso()
        )) else db.update("budgets", existing.str("id"), mapOf("amount" to Fmt.r2(amount)))
    }

    // ── Bank reconciliation ──────────────────────────────────────────────────
    fun bankLines(): List<Row> = db.q("SELECT * FROM bank_lines ORDER BY statement_date DESC")

    fun addBankLine(date: String, description: String, direction: String, amount: Double, reference: String) {
        db.insert("bank_lines", mapOf(
            "id" to UUID.randomUUID().toString(), "branch_id" to repo.branchId(),
            "statement_date" to date, "description" to description, "direction" to direction,
            "amount" to Fmt.r2(amount), "is_matched" to 0, "reference" to reference,
            "note" to "", "created_at" to Clock.nowIso()
        ))
    }

    /** Matches statement lines to a sale or expense of the same amount on the same day. */
    fun autoMatch(): Int {
        var matched = 0
        db.tx {
            bankLines().filter { !it.bool("is_matched") }.forEach { line ->
                val day = line.str("statement_date").take(10)
                val amount = line.dbl("amount")
                val ref: String? = if (line.str("direction") == "IN") {
                    db.one(
                        "SELECT receipt_no FROM sales WHERE sold_at LIKE ? AND ABS(total - ?) < 1 ORDER BY sold_at LIMIT 1",
                        "$day%", amount
                    )?.str("receipt_no")
                } else {
                    db.one(
                        "SELECT category FROM expenses WHERE spent_at LIKE ? AND ABS(amount - ?) < 1 ORDER BY spent_at LIMIT 1",
                        "$day%", amount
                    )?.str("category")
                }
                if (ref != null) {
                    db.update("bank_lines", line.str("id"), mapOf("is_matched" to 1, "reference" to ref))
                    matched++
                }
            }
        }
        return matched
    }

    // ── CSV ──────────────────────────────────────────────────────────────────
    fun exportTable(table: String, target: File): Int {
        val rows = db.q("SELECT * FROM $table")
        val headers = rows.firstOrNull()?.keys()?.toList() ?: return 0
        val csv = Csv.write(headers, rows.map { r -> headers.map { r[it] } })
        target.writeText(csv)
        return rows.size
    }

    /** Products import: matches on barcode or name, updates stock and prices. */
    fun importProducts(file: File): Pair<Int, Int> {
        val rows = Csv.parse(file.readText())
        if (rows.size < 2) return 0 to 0
        val head = rows.first().map { it.trim().lowercase() }
        fun idx(vararg names: String): Int = names.map { head.indexOf(it) }.firstOrNull { it >= 0 } ?: -1
        val iName = idx("name", "product", "product name")
        if (iName < 0) error("CSV needs a 'name' column")
        val iBarcode = idx("barcode")
        val iCat = idx("category")
        val iUnit = idx("unit")
        val iCost = idx("cost_price", "cost")
        val iRetail = idx("retail_price", "price", "selling_price")
        val iStock = idx("stock_qty", "stock", "quantity")
        var created = 0
        var updated = 0
        db.tx {
            rows.drop(1).forEach { r ->
                fun cell(i: Int) = if (i >= 0 && i < r.size) r[i].trim() else ""
                val name = cell(iName)
                if (name.isBlank()) return@forEach
                val barcode = cell(iBarcode)
                val existing = if (barcode.isNotBlank())
                    db.one("SELECT * FROM products WHERE barcode=?", barcode)
                else db.one("SELECT * FROM products WHERE lower(name)=lower(?)", name)
                val values = mapOf(
                    "name" to name,
                    "barcode" to barcode.ifBlank { null },
                    "category" to cell(iCat).ifBlank { "General" },
                    "unit" to cell(iUnit).ifBlank { "pcs" },
                    "cost_price" to (cell(iCost).toDoubleOrNull() ?: 0.0),
                    "retail_price" to (cell(iRetail).toDoubleOrNull() ?: 0.0),
                    "stock_qty" to (cell(iStock).toDoubleOrNull() ?: 0.0)
                )
                if (existing == null) {
                    db.insert("products", values + mapOf(
                        "id" to UUID.randomUUID().toString(), "branch_id" to repo.branchId(),
                        "low_stock_alert" to 5.0, "is_favourite" to 0,
                        "created_at" to Clock.nowIso(), "updated_at" to Clock.nowIso()
                    ))
                    created++
                } else {
                    db.update("products", existing.str("id"), values + mapOf("updated_at" to Clock.nowIso()))
                    updated++
                }
            }
        }
        return created to updated
    }

    fun importCustomers(file: File): Pair<Int, Int> {
        val rows = Csv.parse(file.readText())
        if (rows.size < 2) return 0 to 0
        val head = rows.first().map { it.trim().lowercase() }
        val iName = head.indexOf("name")
        if (iName < 0) error("CSV needs a 'name' column")
        fun idx(n: String) = head.indexOf(n)
        var created = 0
        var updated = 0
        db.tx {
            rows.drop(1).forEach { r ->
                fun cell(n: String): String = idx(n).let { if (it >= 0 && it < r.size) r[it].trim() else "" }
                val name = r.getOrNull(iName)?.trim() ?: return@forEach
                if (name.isBlank()) return@forEach
                val phone = cell("phone")
                val existing = if (phone.isNotBlank()) db.one("SELECT * FROM customers WHERE phone=?", phone)
                else db.one("SELECT * FROM customers WHERE lower(name)=lower(?)", name)
                val values = mapOf(
                    "name" to name, "phone" to phone.ifBlank { null },
                    "address" to cell("address").ifBlank { null },
                    "type" to cell("type").ifBlank { "RETAIL" },
                    "credit_limit" to (cell("credit_limit").toDoubleOrNull() ?: 0.0)
                )
                if (existing == null) {
                    db.insert("customers", values + mapOf(
                        "id" to UUID.randomUUID().toString(), "balance" to 0.0,
                        "total_purchases" to 0.0, "total_paid" to 0.0,
                        "created_at" to Clock.nowIso(), "updated_at" to Clock.nowIso()
                    ))
                    created++
                } else {
                    db.update("customers", existing.str("id"), values + mapOf("updated_at" to Clock.nowIso()))
                    updated++
                }
            }
        }
        return created to updated
    }

    // ── Sample data ──────────────────────────────────────────────────────────
    /** Optional demo shop. Runs through the real posting engine, so the books stay true. */
    /**
     * A working guest house on top of the shop: room types, rooms, guests and a
     * few stays (closed, in house and arriving) so every hotel screen has life.
     */
    fun loadHotelSample(quiet: Boolean = false) {
        if (db.count("rooms") > 0) {
            if (quiet) return
            error("This business already has rooms — the hotel demo is only for an empty room list")
        }
        val hotel = Hotel(repo)
        val today = Clock.today()
        val types = listOf(
            Triple("Single", 80_000.0, 1) to "1 single",
            Triple("Double", 120_000.0, 2) to "1 double",
            Triple("Twin", 130_000.0, 2) to "2 singles",
            Triple("Suite", 220_000.0, 3) to "1 king + sofa"
        ).associate { (spec, beds) ->
            spec.first to hotel.saveRoomType(null, spec.first, spec.second, spec.third, beds, taxRate = 18.0)
        }

        val numbers = listOf("101" to "Single", "102" to "Double", "103" to "Double", "104" to "Twin",
                             "201" to "Suite", "202" to "Double", "203" to "Twin", "204" to "Single")
        val roomIds = numbers.map { (number, typeName) ->
            number to hotel.saveRoom(null, number, types[typeName], number.take(1), Hotel.VACANT_CLEAN, "")
        }.toMap()

        data class DemoGuest(val id: String, val name: String, val phone: String)
        val guestList = listOf(
            Triple("Aisha Kabahenda", "0772 100 200", "1"),
            Triple("John Mugisa", "0703 400 500", "0"),
            Triple("Ministry of Health", "0414 000 000", "X"),
            Triple("Grace Atuhaire", "0752 700 800", "1"),
            Triple("Peter Baguma", "0779 900 100", "0")
        )
        val guests = guestList.map { (n, phone, idNo) ->
            DemoGuest(hotel.saveGuest(null, n, phone, idNumber = idNo), n, phone)
        }
        fun guestByName(name: String): DemoGuest = guests.first { it.name == name }

        // Closed stays over the last few days: revenue already recognised.
        listOf("101", "104", "203", "102").forEachIndexed { i, roomNo ->
            val inDate = Hotel.addDays(today, -(6 - i).toLong())
            val outDate = Hotel.addDays(inDate, 2)
            val g = guests[i]
            runCatching {
                val id = hotel.createBooking(
                    g.name, g.phone, roomIds[roomNo]!!, inDate, outDate,
                    adults = 2, source = "PHONE", guestId = g.id,
                    deposit = 60_000.0, depositMethod = if (i % 2 == 0) "CASH" else "MTN_MOMO"
                )
                hotel.checkIn(id)
                val bill = hotel.folio(id)
                hotel.checkOut(id, extraPayment = bill.balance, method = "CASH")
            }
        }

        // Guests in house right now, one with a restaurant bill on the folio.
        listOf("102" to "John Mugisa", "201" to "Ministry of Health").forEachIndexed { i, (roomNo, name) ->
            val g = guestByName(name)
            val id = hotel.createBooking(
                g.name, g.phone, roomIds[roomNo]!!,
                Hotel.addDays(today, -1), Hotel.addDays(today, 2),
                adults = 2, source = if (i == 1) "CORPORATE" else "WALK_IN",
                guestId = g.id, deposit = 100_000.0, depositMethod = "CASH"
            )
            hotel.checkIn(id)
            hotel.addCharge(id, "RESTAURANT", "Dinner for two", 2.0, 18_000.0, taxRate = 18.0)
            hotel.postPayment(id, 40_000.0, "AIRTEL_MONEY")
        }

        // Arrivals for later today and tomorrow, so the front desk has work to do.
        listOf("103" to "Grace Atuhaire", "202" to "Peter Baguma").forEachIndexed { i, (roomNo, name) ->
            val g = guestByName(name)
            hotel.createBooking(
                g.name, g.phone, roomIds[roomNo]!!,
                Hotel.addDays(today, i.toLong()), Hotel.addDays(today, i + 2L),
                adults = 2, source = "PHONE", guestId = g.id,
                deposit = 50_000.0, depositMethod = "MTN_MOMO"
            )
        }
        hotel.raiseTask(roomIds["204"]!!, "MAINTENANCE", "Air conditioner leaking")
    }

    fun loadSampleData() {
        if (db.count("products") > 0) error("This business already has products — sample data is only for an empty file")
        val rnd = Random(7)
        db.tx {
            repo.saveProfile(mapOf(
                "name" to "Kyenjojo Hardware & General Store",
                "phone" to "+256 700 000 000", "email" to "shop@example.com",
                "address" to "Main Street, Kyenjojo", "tin" to "1000000000",
                "motto" to "Every sale counted, every shilling accounted for"
            ))
            acc.postCapital(5_000_000.0, "Opening capital")

            val cats = listOf("Groceries", "Household", "Hardware", "Beverages")
            val items = listOf(
                Triple("Sugar 1kg", "Groceries", 3800.0) to 4500.0,
                Triple("Rice 5kg", "Groceries", 14000.0) to 16500.0,
                Triple("Maize Flour 1kg", "Groceries", 2600.0) to 3200.0,
                Triple("Cooking Oil 1L", "Groceries", 6500.0) to 7500.0,
                Triple("Salt 500g", "Groceries", 900.0) to 1200.0,
                Triple("Bar Soap", "Household", 1800.0) to 2300.0,
                Triple("Washing Powder 500g", "Household", 3200.0) to 4000.0,
                Triple("Charcoal Bag", "Household", 18000.0) to 22000.0,
                Triple("Nails 1kg", "Hardware", 5500.0) to 7000.0,
                Triple("Cement 50kg", "Hardware", 32000.0) to 36500.0,
                Triple("Soda Crate", "Beverages", 18000.0) to 22000.0,
                Triple("Bottled Water 500ml", "Beverages", 800.0) to 1200.0)
            var opening = 0.0
            items.forEachIndexed { i, (meta, retail) ->
                val (name, cat, cost) = meta
                val qty = 40.0 + rnd.nextInt(60)
                opening += qty * cost
                db.insert("products", mapOf(
                    "id" to "p-$i", "name" to name, "barcode" to "60010%04d".format(i),
                    "category" to cat, "subcategory" to "", "unit" to if (name.contains("kg")) "pcs" else "pcs",
                    "cost_price" to cost, "retail_price" to retail, "wholesale_price" to Fmt.r2(retail * 0.93),
                    "wholesale_min_qty" to 10, "tax_rate" to 0.0, "stock_qty" to qty,
                    "low_stock_alert" to 12.0, "reorder_level" to 15.0,
                    "branch_id" to "br-main", "is_favourite" to if (i < 4) 1 else 0,
                    "created_at" to Clock.nowIso(), "updated_at" to Clock.nowIso()
                ))
            }
            acc.postOpeningStock(Fmt.r2(opening), "Opening stock")

            listOf(
                "Mukasa General Supplies" to "0772 111 222",
                "Bwambale Wholesalers" to "0702 333 444",
                "Kyenjojo Traders" to "0782 555 666"
            ).forEachIndexed { i, (n, p) ->
                db.insert("suppliers", mapOf(
                    "id" to "s-$i", "name" to n, "phone" to p, "address" to "",
                    "balance" to 0.0, "created_at" to Clock.nowIso(), "updated_at" to Clock.nowIso()
                ))
            }
            listOf(
                "Aunt Grace Kiosk" to "0771 100 100",
                "Hotel Rwenzo" to "0703 200 200",
                "Boda Stage Kyenjojo" to "0752 300 300",
                "Walk-in Customers" to ""
            ).forEachIndexed { i, (n, p) ->
                db.insert("customers", mapOf(
                    "id" to "c-$i", "name" to n, "phone" to p, "address" to "",
                    "type" to if (i == 1) "WHOLESALE" else "RETAIL",
                    "credit_limit" to if (i == 1) 500000.0 else 0.0,
                    "balance" to 0.0, "total_purchases" to 0.0, "total_paid" to 0.0,
                    "created_at" to Clock.nowIso(), "updated_at" to Clock.nowIso()
                ))
            }
            listOf(
                Triple("Sarah Kembabazi", "CASHIER", 350000.0),
                Triple("Joel Mugisa", "SHOP ASSISTANT", 280000.0)
            ).forEachIndexed { i, (n, role, salary) ->
                db.insert("employees", mapOf(
                    "id" to "e-$i", "name" to n, "role" to role, "phone" to "",
                    "monthly_salary" to salary, "is_active" to 1, "created_at" to Clock.nowIso()
                ))
            }
            db.insert("fixed_assets", mapOf(
                "id" to "fa-0", "name" to "Delivery Motorcycle", "category" to "VEHICLE",
                "cost" to 4_200_000.0, "salvage" to 700_000.0,
                "purchase_date" to LocalDate.now().minusMonths(8).toString(),
                "life_months" to 60, "accumulated_depreciation" to 0.0, "created_at" to Clock.nowIso()
            ))
            acc.post(
                particulars = "Fixed asset — Delivery Motorcycle", source = "ASSET",
                debits = listOf(Accounts.FIXED_ASSETS to 4_200_000.0),
                credits = listOf(Accounts.CASH to 4_200_000.0)
            )
        }

        // Trading history: realistic mix of cash, mobile money and credit sales.
        val products = repo.products()
        val methods = listOf("CASH", "CASH", "CASH", "MTN_MOMO", "AIRTEL_MONEY", "CREDIT")
        for (day in 20 downTo 1) {
            val salesToday = 3 + rnd.nextInt(5)
            repeat(salesToday) {
                val lines = mutableListOf<Repo.CartLine>()
                repeat(1 + rnd.nextInt(4)) {
                    val p = products[rnd.nextInt(products.size)]
                    if (lines.any { it.productId == p.str("id") }) return@repeat
                    val qty = if (p.dbl("retail_price") < 5000) (1 + rnd.nextInt(4)).toDouble()
                    else (1 + rnd.nextInt(2)).toDouble()
                    lines.add(Repo.CartLine(p.str("id"), p.str("name"), qty, p.dbl("retail_price"), p.dbl("cost_price")))
                }
                if (lines.isEmpty()) return@repeat
                val method = methods[rnd.nextInt(methods.size)]
                val customer = if (method == "CREDIT") listOf("c-0", "c-1", "c-2")[rnd.nextInt(3)] else null
                val total = Fmt.r2(lines.sumOf { it.lineTotal })
                runCatching {
                    repo.checkout(
                        lines = lines, customerId = customer,
                        saleType = if (customer == "c-1") "WHOLESALE" else "RETAIL",
                        amountPaid = if (method == "CREDIT") total * 0.4 else total,
                        method = method, discount = 0.0
                    )
                }
            }
        }
        repo.recordExpense("RENT", 400_000.0, "CASH", "Monthly shop rent")
        repo.recordExpense("TRANSPORT", 65_000.0, "CASH", "Stock delivery from Kampala")
        repo.recordExpense("UTILITIES", 48_000.0, "MTN_MOMO", "UEL prepaid token")
        repo.recordExpense("AIRTIME", 20_000.0, "MTN_MOMO", "Data and airtime")
        repo.addStock("p-0", 60.0, 3800.0, "Restock sugar", supplierId = "s-0", paidHow = "CREDIT")
        repo.addStock("p-9", 30.0, 32000.0, "Cement delivery", supplierId = "s-1", paidHow = "CASH")
        loadHotelSample(quiet = true)
        db.meta("sample_data", "1")
    }
}
