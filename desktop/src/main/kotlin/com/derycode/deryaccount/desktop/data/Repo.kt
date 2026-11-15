package com.derycode.deryaccount.desktop.data

import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToLong

class Repo(val db: DatabaseShop) {

    val accounting = Accounting(db)

    data class CartLine(
        val productId: String,
        val name: String,
        val qty: Double,
        val unitPrice: Double,
        val costPrice: Double,
        val taxRate: Double = 0.0
    ) {
        val lineTotal: Double get() = (qty * unitPrice * 100).roundToLong() / 100.0
    }

    data class CheckoutResult(val saleId: String, val receiptNo: String, val total: Double, val change: Double)

    // ── Reference data ───────────────────────────────────────────────────────
    fun branchId(): String = db.one("SELECT id FROM branches ORDER BY created_at LIMIT 1")?.str("id") ?: "br-main"
    fun branches(): List<Row> = db.q("SELECT * FROM branches ORDER BY name")
    fun users(): List<Row> = db.q("SELECT * FROM users ORDER BY full_name")
    fun profile(): Row = db.one("SELECT * FROM profile LIMIT 1") ?: Row(emptyMap())
    fun businessName(): String = profile().str("name").ifBlank { "My Business" }

    fun user(id: String): Row? = db.one("SELECT * FROM users WHERE id=?", id)

    fun findUser(username: String, pin: String): Row? = authenticate(username, pin)

    fun touchLastLogin(userId: String) {
        runCatching { db.meta("last_login_$userId", Clock.nowIso()) }
    }

    fun setting(key: String, def: String): String = db.meta(key) ?: def

    fun branchName(id: String): String =
        db.one("SELECT name FROM branches WHERE id=?", id)?.str("name") ?: "Main Branch"

    fun authenticate(username: String, pin: String): Row? {
        val u = db.one("SELECT * FROM users WHERE lower(username)=lower(?) AND is_active=1", username.trim()) ?: return null
        return if (u.str("pin_hash") == DatabaseShop.sha256(pin)) u else null
    }

    fun saveProfile(values: Map<String, Any?>) {
        val v = LinkedHashMap(values)
        v["updated_at"] = Clock.nowIso()
        if (db.count("profile") == 0) db.insert("profile", v + ("id" to "profile"))
        else db.update("profile", "profile", v)
    }

    // ── Products ─────────────────────────────────────────────────────────────
    fun products(branchId: String = branchId(), search: String = ""): List<Row> {
        val like = "%${search.trim()}%"
        return if (search.isBlank())
            db.q("SELECT * FROM products ORDER BY is_favourite DESC, name")
        else db.q(
            """SELECT * FROM products WHERE name LIKE ? OR barcode LIKE ? OR category LIKE ?
               OR subcategory LIKE ? ORDER BY is_favourite DESC, name""",
            like, like, like, like
        )
    }

    fun product(id: String): Row? = db.one("SELECT * FROM products WHERE id=?", id)

    fun categories(): List<String> =
        db.q("SELECT DISTINCT category FROM products WHERE category IS NOT NULL AND category <> '' ORDER BY category")
            .map { it.str("category") }

    fun units(): List<String> = listOf("pcs", "kg", "g", "l", "ml", "pkt", "box", "crate", "bag", "roll", "pair", "set")

    fun priceFor(p: Row, qty: Double, saleType: String): Double {
        val wholesale = p["wholesale_price"]?.toString()?.toDoubleOrNull()
        val minQty = p.int("wholesale_min_qty")
        return if (saleType == "WHOLESALE" && wholesale != null && qty >= minQty && wholesale > 0)
            wholesale else p.dbl("retail_price")
    }

    /** Add stock from a supplier purchase or a manual delivery, posting the books. */
    fun addStock(
        productId: String, qty: Double, unitCost: Double?, note: String,
        supplierId: String? = null, paidHow: String = "CASH", userId: String = "u-owner"
    ) {
        require(qty > 0) { "Quantity must be greater than zero" }
        val p = product(productId) ?: error("Product not found")
        val cost = unitCost ?: p.dbl("cost_price")
        val now = Clock.nowIso()
        db.tx {
            if (unitCost != null && unitCost > 0) {
                // New landed cost keeps margins and the stock ledger honest.
                db.exec(
                    "UPDATE products SET stock_qty = stock_qty + ?, cost_price = ?, updated_at=? WHERE id=?",
                    qty, cost, now, productId
                )
            } else {
                db.exec("UPDATE products SET stock_qty = stock_qty + ?, updated_at=? WHERE id=?", qty, now, productId)
            }
            db.insert("stock_movements", mapOf(
                "product_id" to productId, "branch_id" to p.str("branch_id", branchId()),
                "type" to "PURCHASE", "qty" to qty, "reference" to null,
                "note" to note.ifBlank { "Stock added" }, "moved_at" to now, "created_at" to now
            ))
            val value = Fmt.r2(qty * cost)
            if (value > 0) {
                db.insert("purchases", mapOf(
                    "id" to UUID.randomUUID().toString(), "supplier_id" to supplierId,
                    "branch_id" to p.str("branch_id", branchId()), "total" to value,
                    "paid_amount" to if (paidHow == "CREDIT") 0.0 else value,
                    "received_at" to now, "note" to note.ifBlank { "Stock delivery" }, "created_at" to now
                ))
                accounting.postPurchase(value, paidHow, "delivery ${p.str("name")}")
                if (paidHow == "CREDIT" && supplierId != null) {
                    db.exec("UPDATE suppliers SET balance = balance + ? WHERE id=?", value, supplierId)
                }
            }
        }
    }

    /** Stock count / adjustment: sets the real quantity, books the variance. */
    fun adjustStock(productId: String, newQty: Double, note: String) {
        val p = product(productId) ?: error("Product not found")
        val oldQty = p.dbl("stock_qty")
        val delta = newQty - oldQty
        if (delta == 0.0) return
        val now = Clock.nowIso()
        db.tx {
            db.exec("UPDATE products SET stock_qty=?, updated_at=? WHERE id=?", newQty, now, productId)
            db.insert("stock_movements", mapOf(
                "product_id" to productId, "branch_id" to p.str("branch_id", branchId()),
                "type" to if (delta > 0) "ADJUSTMENT" else "COUNT", "qty" to delta,
                "reference" to null, "note" to note.ifBlank { "Stock count" },
                "moved_at" to now, "created_at" to now
            ))
            accounting.postCountAdjustment(delta, p.dbl("cost_price"), p.str("name"))
        }
    }

    fun writeOffStock(productId: String, qty: Double, note: String) {
        if (qty <= 0) return
        val p = product(productId) ?: return
        val now = Clock.nowIso()
        db.tx {
            db.exec("UPDATE products SET stock_qty = MAX(0, stock_qty - ?), updated_at=? WHERE id=?", qty, now, productId)
            db.insert("stock_movements", mapOf(
                "product_id" to productId, "branch_id" to p.str("branch_id", branchId()),
                "type" to "DAMAGE", "qty" to -qty, "note" to note.ifBlank { "Damage / expiry" },
                "moved_at" to now, "created_at" to now
            ))
            accounting.postStockEdit(p.dbl("stock_qty"), p.dbl("cost_price"),
                p.dbl("stock_qty") - qty, p.dbl("cost_price"), "write-off ${p.str("name")}")
        }
    }

    fun transferStock(productId: String, toBranchId: String, qty: Double, note: String) {
        val p = product(productId) ?: return
        if (qty <= 0 || qty > p.dbl("stock_qty")) return
        val now = Clock.nowIso()
        db.tx {
            db.exec("UPDATE products SET stock_qty = stock_qty - ?, updated_at=? WHERE id=?", qty, now, productId)
            db.insert("stock_transfers", mapOf(
                "from_branch_id" to p.str("branch_id", branchId()), "to_branch_id" to toBranchId,
                "product_id" to productId, "qty" to qty, "status" to "SENT",
                "note" to note, "transferred_at" to now, "created_at" to now
            ))
            db.insert("stock_movements", mapOf(
                "product_id" to productId, "branch_id" to p.str("branch_id", branchId()),
                "type" to "TRANSFER_OUT", "qty" to -qty, "note" to note,
                "moved_at" to now, "created_at" to now
            ))
        }
    }

    // ── Checkout: the money path ─────────────────────────────────────────────
    fun checkout(
        lines: List<CartLine>,
        customerId: String?,
        saleType: String,
        amountPaid: Double,
        method: String,
        discount: Double = 0.0,
        userId: String = "u-owner",
        note: String = ""
    ): CheckoutResult {
        require(lines.isNotEmpty()) { "Add at least one item" }
        // A shop cannot hand over goods it does not have: refuse before writing anything.
        lines.forEach { l ->
            val p = db.one("SELECT stock_qty FROM products WHERE id=?", l.productId)
            if (p != null && l.qty > p.dbl("stock_qty") + 0.0001) {
                throw IllegalStateException("Not enough stock for ${l.name}: only ${Fmt.qty(p.dbl("stock_qty"))} left")
            }
        }
        val now = Clock.nowIso()
        val branch = branchId()
        val subtotal = Fmt.r2(lines.sumOf { it.lineTotal })
        // Prices are VAT-inclusive, so the tax is the slice already sitting inside the price.
        val taxTotal = Fmt.r2(lines.sumOf { l ->
            if (l.taxRate > 0) l.lineTotal * l.taxRate / (100.0 + l.taxRate) else 0.0
        })
        val total = Fmt.r2(subtotal - discount)   // VAT-inclusive prices: tax is inside, not added
        require(total >= 0) { "Discount is larger than the sale" }
        val paid = if (method == "CREDIT") amountPaid else amountPaid
        val change = if (method == "CREDIT") 0.0 else Fmt.r2((amountPaid - total).coerceAtLeast(0.0))

        val saleId = UUID.randomUUID().toString()
        val receiptNo = nextReceiptNo(branch)

        db.tx {
            db.insert("sales", mapOf(
                "id" to saleId, "receipt_no" to receiptNo, "branch_id" to branch,
                "user_id" to userId, "customer_id" to customerId, "sale_type" to saleType,
                "subtotal" to subtotal, "tax_total" to taxTotal, "discount" to discount,
                "total" to total, "amount_paid" to paid, "change_given" to change,
                "payment_method" to method, "sold_at" to now, "shift_id" to null,
                "note" to note, "created_at" to now, "updated_at" to now
            ))
            lines.forEach { l ->
                db.insert("sale_items", mapOf(
                    "id" to UUID.randomUUID().toString(), "sale_id" to saleId,
                    "product_id" to l.productId, "name" to l.name, "qty" to l.qty,
                    "unit_price" to l.unitPrice, "cost_price" to l.costPrice,
                    "line_total" to l.lineTotal, "tax_rate" to l.taxRate
                ))
                db.exec("UPDATE products SET stock_qty = stock_qty - ?, updated_at=? WHERE id=?",
                    l.qty, now, l.productId)
                db.insert("stock_movements", mapOf(
                    "product_id" to l.productId, "branch_id" to branch, "type" to "SALE",
                    "qty" to -l.qty, "reference" to saleId, "note" to receiptNo,
                    "moved_at" to now, "created_at" to now
                ))
            }
            if (customerId != null) {
                val c = db.one("SELECT * FROM customers WHERE id=?", customerId)
                if (c != null) {
                    val unpaid = if (method == "CREDIT") Fmt.r2((total - amountPaid).coerceAtLeast(0.0)) else 0.0
                    val receivedNow = if (method == "CREDIT") amountPaid.coerceAtLeast(0.0) else total
                    db.update("customers", customerId, mapOf(
                        "balance" to Fmt.r2(c.dbl("balance") + unpaid),
                        "total_purchases" to Fmt.r2(c.dbl("total_purchases") + total),
                        "total_paid" to Fmt.r2(c.dbl("total_paid") + receivedNow),
                        "updated_at" to now
                    ))
                }
            }
            accounting.postSale(
                amount = total, method = method, receiptNo = receiptNo, itemCount = lines.size,
                costTotal = Fmt.r2(lines.sumOf { it.qty * it.costPrice }),
                gross = subtotal, discount = discount,
                collected = if (method == "CREDIT") Fmt.r2(amountPaid) else 0.0,
                vatAmount = taxTotal, branchId = branch
            )
        }
        return CheckoutResult(saleId, receiptNo, total, change)
    }

    fun nextReceiptNo(branchId: String = branchId()): String {
        val b = db.one("SELECT * FROM branches WHERE id=?", branchId)
        val code = (b?.str("name") ?: "Main").take(3).uppercase(Locale.US).padEnd(3, 'X')
        val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val iso = LocalDate.now().toString()
        val n = db.count("sales", "branch_id=? AND sold_at>=? AND sold_at<=?", branchId, "${iso}T00:00:00", "${iso}T23:59:59")
        return "$code-$date-%04d".format(n + 1)
    }

    // ── Sales ────────────────────────────────────────────────────────────────
    fun sales(limit: Int = 500, search: String = ""): List<Row> {
        val base = """
            SELECT s.*, COALESCE(c.name,'Walk-in') AS customer_name,
                   COALESCE(u.full_name, s.user_id) AS cashier
            FROM sales s
            LEFT JOIN customers c ON c.id = s.customer_id
            LEFT JOIN users u ON u.id = s.user_id
        """
        return if (search.isBlank()) db.q("$base ORDER BY s.sold_at DESC LIMIT ?", limit)
        else db.q("$base WHERE s.receipt_no LIKE ? OR c.name LIKE ? ORDER BY s.sold_at DESC LIMIT ?",
            "%$search%", "%$search%", limit)
    }

    fun saleItems(saleId: String): List<Row> = db.q("SELECT * FROM sale_items WHERE sale_id=? ORDER BY name", saleId)
    fun sale(id: String): Row? = db.one(
        """SELECT s.*, COALESCE(c.name,'Walk-in') AS customer_name, COALESCE(u.full_name, s.user_id) AS cashier
           FROM sales s LEFT JOIN customers c ON c.id=s.customer_id LEFT JOIN users u ON u.id=s.user_id
           WHERE s.id=?""", id
    )

    fun returnSale(saleId: String, amount: Double, method: String, note: String, userId: String = "u-owner") {
        val s = sale(saleId) ?: error("Sale not found")
        val now = Clock.nowIso()
        val costValue = Fmt.r2(saleItems(saleId).sumOf { it.dbl("qty") * it.dbl("cost_price") })
        db.tx {
            db.insert("sales_returns", mapOf(
                "id" to UUID.randomUUID().toString(), "sale_id" to saleId,
                "receipt_no" to s.str("receipt_no"), "branch_id" to s.str("branch_id"),
                "user_id" to userId, "amount" to Fmt.r2(amount), "cost_value" to costValue,
                "method" to method, "note" to note, "returned_at" to now, "created_at" to now
            ))
            // Goods come back onto the shelf.
            saleItems(saleId).forEach { it2 ->
                db.exec("UPDATE products SET stock_qty = stock_qty + ? WHERE id=?", it2.dbl("qty"), it2.str("product_id"))
                db.insert("stock_movements", mapOf(
                    "product_id" to it2.str("product_id"), "branch_id" to s.str("branch_id"),
                    "type" to "RETURN", "qty" to it2.dbl("qty"), "reference" to saleId,
                    "note" to "Sale return ${s.str("receipt_no")}", "moved_at" to now, "created_at" to now
                ))
            }
            accounting.postSaleReturn(Fmt.r2(amount), costValue, method, s.str("receipt_no"))
            val cust = s.str("customer_id")
            if (cust.isNotBlank()) {
                db.exec("UPDATE customers SET total_purchases = MAX(0, total_purchases - ?), " +
                    "balance = MAX(0, balance - ?) WHERE id=?",
                    Fmt.r2(amount), if (method == "CREDIT") Fmt.r2(amount) else 0.0, cust)
            }
        }
    }

    // ── Purchases, POs & returns ─────────────────────────────────────────────
    fun purchases(limit: Int = 400): List<Row> = db.q(
        """SELECT p.*, COALESCE(s.name,'Cash purchase') AS supplier_name
           FROM purchases p LEFT JOIN suppliers s ON s.id = p.supplier_id
           ORDER BY p.received_at DESC LIMIT ?""", limit
    )

    fun purchaseItems(purchaseId: String): List<Row> =
        db.q("SELECT * FROM purchase_items WHERE purchase_id=?", purchaseId)

    /** A proper supplier purchase with line items: stock in, creditor up, books posted. */
    fun recordPurchase(
        supplierId: String?, lines: List<CartLine>, paid: Double, note: String,
        userId: String = "u-owner"
    ): String {
        require(lines.isNotEmpty()) { "Add at least one line" }
        val now = Clock.nowIso()
        val branch = branchId()
        val total = Fmt.r2(lines.sumOf { it.qty * it.unitPrice })
        val purchaseId = UUID.randomUUID().toString()
        db.tx {
            db.insert("purchases", mapOf(
                "id" to purchaseId, "supplier_id" to supplierId, "branch_id" to branch,
                "total" to total, "paid_amount" to Fmt.r2(paid), "received_at" to now,
                "note" to note, "created_at" to now
            ))
            lines.forEach { l ->
                db.insert("purchase_items", mapOf(
                    "id" to UUID.randomUUID().toString(), "purchase_id" to purchaseId,
                    "product_id" to l.productId, "name" to l.name, "qty" to l.qty,
                    "unit_cost" to l.unitPrice, "line_total" to Fmt.r2(l.qty * l.unitPrice)
                ))
                db.exec("UPDATE products SET stock_qty = stock_qty + ?, cost_price = ?, updated_at=? WHERE id=?",
                    l.qty, l.unitPrice, now, l.productId)
                db.insert("stock_movements", mapOf(
                    "product_id" to l.productId, "branch_id" to branch, "type" to "PURCHASE",
                    "qty" to l.qty, "reference" to purchaseId, "note" to "Purchase",
                    "moved_at" to now, "created_at" to now
                ))
            }
            val owed = Fmt.r2(total - paid)
            if (owed > 0 && supplierId != null) {
                db.exec("UPDATE suppliers SET balance = balance + ?, updated_at=? WHERE id=?", owed, now, supplierId)
            }
            if (paid > 0) accounting.postPurchase(paid, "CASH", "purchase paid")
            if (owed > 0) accounting.postPurchase(owed, "CREDIT", "purchase on credit")
        }
        return purchaseId
    }

    fun purchaseOrders(): List<Row> = db.q(
        """SELECT o.*, COALESCE(s.name,'No supplier') AS supplier_name,
             (SELECT COUNT(*) FROM purchase_order_items i WHERE i.order_id = o.id) AS lines,
             (SELECT COALESCE(SUM(i.line_total),0) FROM purchase_order_items i WHERE i.order_id=o.id) AS value
           FROM purchase_orders o LEFT JOIN suppliers s ON s.id=o.supplier_id
           ORDER BY o.ordered_at DESC"""
    )

    fun orderItems(orderId: String): List<Row> =
        db.q("SELECT * FROM purchase_order_items WHERE order_id=?", orderId)

    fun createPurchaseOrder(supplierId: String?, lines: List<CartLine>, expectedAt: String?, note: String): String {
        require(lines.isNotEmpty()) { "Add at least one line" }
        val now = Clock.nowIso()
        val id = UUID.randomUUID().toString()
        val poNo = "PO-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" +
            "%04d".format(db.count("purchase_orders") + 1)
        db.tx {
            db.insert("purchase_orders", mapOf(
                "id" to id, "po_no" to poNo, "supplier_id" to supplierId, "branch_id" to branchId(),
                "user_id" to "u-owner", "status" to "OPEN", "expected_at" to expectedAt,
                "note" to note, "ordered_at" to now, "created_at" to now, "updated_at" to now
            ))
            lines.forEach { l ->
                db.insert("purchase_order_items", mapOf(
                    "id" to UUID.randomUUID().toString(), "order_id" to id,
                    "product_id" to l.productId, "name" to l.name, "qty" to l.qty,
                    "unit_cost" to l.unitPrice, "line_total" to Fmt.r2(l.qty * l.unitPrice)
                ))
            }
        }
        return id
    }

    /** Receiving an order turns it into a real purchase: stock and books follow. */
    fun receiveOrder(orderId: String) {
        val o = db.one("SELECT * FROM purchase_orders WHERE id=?", orderId) ?: return
        if (o.str("status") != "OPEN") return
        val lines = orderItems(orderId).map {
            CartLine(it.str("product_id"), it.str("name"), it.dbl("qty"), it.dbl("unit_cost"), it.dbl("unit_cost"))
        }
        db.tx {
            recordPurchase(o.str("supplier_id").ifBlank { null }, lines, 0.0, "Received ${o.str("po_no")}")
            db.update("purchase_orders", orderId, mapOf("status" to "RECEIVED", "updated_at" to Clock.nowIso()))
        }
    }

    fun cancelOrder(orderId: String) = db.update("purchase_orders", orderId,
        mapOf("status" to "CANCELLED", "updated_at" to Clock.nowIso()))

    fun purchaseReturns(): List<Row> = db.q(
        """SELECT r.*, COALESCE(s.name,'No supplier') AS supplier_name
           FROM purchase_returns r LEFT JOIN suppliers s ON s.id=r.supplier_id
           ORDER BY r.returned_at DESC"""
    )

    fun recordPurchaseReturn(
        supplierId: String?, productId: String, qty: Double, unitCost: Double,
        refundMethod: String, note: String
    ): String {
        val p = product(productId) ?: error("Product not found")
        require(qty > 0 && qty <= p.dbl("stock_qty")) { "Quantity must be within stock on hand" }
        val now = Clock.nowIso()
        val id = UUID.randomUUID().toString()
        val value = Fmt.r2(qty * unitCost)
        val prNo = "PR-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" +
            "%04d".format(db.count("purchase_returns") + 1)
        db.tx {
            db.insert("purchase_returns", mapOf(
                "id" to id, "pr_no" to prNo, "supplier_id" to supplierId, "branch_id" to branchId(),
                "user_id" to "u-owner", "total" to value, "refund_method" to refundMethod,
                "note" to note, "returned_at" to now, "created_at" to now
            ))
            db.insert("purchase_return_items", mapOf(
                "id" to UUID.randomUUID().toString(), "purchase_return_id" to id,
                "product_id" to productId, "name" to p.str("name"), "qty" to qty,
                "unit_cost" to unitCost, "line_total" to value
            ))
            db.exec("UPDATE products SET stock_qty = stock_qty - ? WHERE id=?", qty, productId)
            db.insert("stock_movements", mapOf(
                "product_id" to productId, "branch_id" to branchId(), "type" to "RETURN",
                "qty" to -qty, "reference" to id, "note" to prNo, "moved_at" to now, "created_at" to now
            ))
            if (refundMethod == "SUPPLIER_CREDIT" && supplierId != null) {
                db.exec("UPDATE suppliers SET balance = MAX(0, balance - ?) WHERE id=?", value, supplierId)
            }
            accounting.postPurchaseReturn(refundMethod, value, prNo)
        }
        return id
    }

    // ── Expenses ─────────────────────────────────────────────────────────────
    fun expenses(limit: Int = 400): List<Row> = db.q("SELECT * FROM expenses ORDER BY spent_at DESC LIMIT ?", limit)

    fun recordExpense(category: String, amount: Double, paidFrom: String, note: String, userId: String = "u-owner") {
        require(amount > 0) { "Amount must be greater than zero" }
        val now = Clock.nowIso()
        db.tx {
            db.insert("expenses", mapOf(
                "id" to UUID.randomUUID().toString(), "branch_id" to branchId(), "user_id" to userId,
                "category" to category, "amount" to Fmt.r2(amount), "note" to note,
                "paid_from" to paidFrom, "spent_at" to now, "created_at" to now
            ))
            accounting.postExpense(Fmt.r2(amount), category, paidFrom, note)
        }
    }

    // ── Customer / supplier money ────────────────────────────────────────────
    fun customers(): List<Row> = db.q("SELECT * FROM customers ORDER BY name")
    fun suppliers(): List<Row> = db.q("SELECT * FROM suppliers ORDER BY name")

    fun recordCustomerPayment(customerId: String, amount: Double, method: String, reference: String, note: String) {
        val c = db.one("SELECT * FROM customers WHERE id=?", customerId) ?: error("Customer not found")
        require(amount > 0) { "Amount must be greater than zero" }
        val now = Clock.nowIso()
        db.tx {
            db.insert("customer_payments", mapOf(
                "id" to UUID.randomUUID().toString(), "customer_id" to customerId,
                "branch_id" to branchId(), "user_id" to "u-owner", "amount" to Fmt.r2(amount),
                "method" to method, "reference" to reference, "note" to note,
                "paid_at" to now, "created_at" to now
            ))
            db.exec(
                "UPDATE customers SET balance = MAX(0, balance - ?), total_paid = total_paid + ?, updated_at=? WHERE id=?",
                Fmt.r2(amount), Fmt.r2(amount), now, customerId
            )
            accounting.postCustomerPayment(Fmt.r2(amount), method, c.str("name"))
        }
    }

    fun recordSupplierPayment(supplierId: String, amount: Double, method: String, reference: String, note: String) {
        val s = db.one("SELECT * FROM suppliers WHERE id=?", supplierId) ?: error("Supplier not found")
        require(amount > 0) { "Amount must be greater than zero" }
        val now = Clock.nowIso()
        db.tx {
            db.insert("supplier_payments", mapOf(
                "id" to UUID.randomUUID().toString(), "supplier_id" to supplierId,
                "branch_id" to branchId(), "user_id" to "u-owner", "amount" to Fmt.r2(amount),
                "method" to method, "reference" to reference, "note" to note,
                "paid_at" to now, "created_at" to now
            ))
            db.exec("UPDATE suppliers SET balance = MAX(0, balance - ?), updated_at=? WHERE id=?",
                Fmt.r2(amount), now, supplierId)
            accounting.postSupplierPayment(Fmt.r2(amount), method, s.str("name"))
        }
    }

    // ── Shifts ───────────────────────────────────────────────────────────────
    fun openShift(openingCash: Double, userId: String = "u-owner"): String {
        val id = UUID.randomUUID().toString()
        db.insert("shifts", mapOf(
            "id" to id, "branch_id" to branchId(), "user_id" to userId,
            "opened_at" to Clock.nowIso(), "opening_cash" to Fmt.r2(openingCash)
        ))
        if (openingCash > 0) db.insert("cash_movements", mapOf(
            "id" to UUID.randomUUID().toString(), "branch_id" to branchId(), "user_id" to userId,
            "shift_id" to id, "type" to "OPEN_SHIFT", "amount" to Fmt.r2(openingCash),
            "note" to "Opening float", "moved_at" to Clock.nowIso(), "created_at" to Clock.nowIso()
        ))
        return id
    }

    fun openShiftNow(): Row? =
        db.one("SELECT * FROM shifts WHERE closed_at IS NULL ORDER BY opened_at DESC LIMIT 1")

    fun cashIn(amount: Double, note: String) {
        val s = openShiftNow() ?: error("Open a shift first")
        db.insert("cash_movements", mapOf(
            "id" to UUID.randomUUID().toString(), "branch_id" to branchId(), "user_id" to s.str("user_id"),
            "shift_id" to s.str("id"), "type" to "CASH_IN", "amount" to Fmt.r2(amount),
            "note" to note, "moved_at" to Clock.nowIso(), "created_at" to Clock.nowIso()
        ))
    }

    fun cashOut(amount: Double, note: String) {
        val s = openShiftNow() ?: error("Open a shift first")
        db.insert("cash_movements", mapOf(
            "id" to UUID.randomUUID().toString(), "branch_id" to branchId(), "user_id" to s.str("user_id"),
            "shift_id" to s.str("id"), "type" to "CASH_OUT", "amount" to Fmt.r2(amount),
            "note" to note, "moved_at" to Clock.nowIso(), "created_at" to Clock.nowIso()
        ))
    }

    /** Cash the drawer should hold: float + cash sales + cash in − cash out. */
    fun expectedCash(shift: Row): Double {
        val cashSales = db.scalar(
            """SELECT COALESCE(SUM(total),0) FROM sales
               WHERE sold_at >= ? AND payment_method IN ('CASH')""",
            shift.str("opened_at")
        )
        val inAmt = db.scalar("SELECT COALESCE(SUM(amount),0) FROM cash_movements WHERE shift_id=? AND type='CASH_IN'", shift.str("id"))
        val outAmt = db.scalar("SELECT COALESCE(SUM(amount),0) FROM cash_movements WHERE shift_id=? AND type='CASH_OUT'", shift.str("id"))
        return Fmt.r2(shift.dbl("opening_cash") + cashSales + inAmt - outAmt)
    }

    fun closeShift(shiftId: String, countedCash: Double) {
        val s = db.one("SELECT * FROM shifts WHERE id=?", shiftId) ?: return
        val expected = expectedCash(s)
        db.update("shifts", shiftId, mapOf(
            "closed_at" to Clock.nowIso(), "closing_cash" to Fmt.r2(countedCash),
            "expected_cash" to expected, "variance" to Fmt.r2(countedCash - expected)
        ))
    }

    fun shifts(limit: Int = 100): List<Row> = db.q("SELECT * FROM shifts ORDER BY opened_at DESC LIMIT ?", limit)

    /** Z-report for one shift: everything that happened while the drawer was open. */
    fun zReport(shift: Row): Map<String, Double> {
        val from = shift.str("opened_at")
        val to = shift.str("closed_at").ifBlank { Clock.nowIso() }
        val rows = db.q(
            "SELECT payment_method AS m, COUNT(*) AS n, COALESCE(SUM(total),0) AS t FROM sales " +
                "WHERE sold_at >= ? AND sold_at <= ? GROUP BY payment_method", from, to
        )
        val out = LinkedHashMap<String, Double>()
        rows.forEach { out[it.str("m")] = it.dbl("t") }
        out["_count"] = rows.sumOf { it.int("n") }.toDouble()
        out["_total"] = rows.sumOf { it.dbl("t") }
        out["_expenses"] = db.scalar("SELECT COALESCE(SUM(amount),0) FROM expenses WHERE spent_at >= ? AND spent_at <= ?", from, to)
        out["_returns"] = db.scalar("SELECT COALESCE(SUM(amount),0) FROM sales_returns WHERE returned_at >= ? AND returned_at <= ?", from, to)
        out["_expectedCash"] = expectedCash(shift)
        out["_openingFloat"] = shift.dbl("opening_cash")
        return out
    }

    // ── Parked (held) sales ──────────────────────────────────────────────────
    fun holdSale(lines: List<CartLine>, note: String, discount: Double) {
        val json = lines.joinToString(";") { "${it.productId}|${it.qty}|${it.unitPrice}" }
        db.insert("held_sales", mapOf(
            "id" to UUID.randomUUID().toString(), "branch_id" to branchId(),
            "user_id" to "u-owner", "lines_json" to json, "note" to note,
            "discount" to discount, "created_at" to Clock.nowIso()
        ))
    }

    fun heldSales(): List<Row> = db.q("SELECT * FROM held_sales ORDER BY created_at DESC")

    fun heldLines(row: Row): List<CartLine> = row.str("lines_json").split(";").mapNotNull { part ->
        val bits = part.split("|")
        if (bits.size != 3) return@mapNotNull null
        val p = product(bits[0]) ?: return@mapNotNull null
        CartLine(p.str("id"), p.str("name"), bits[1].toDoubleOrNull() ?: 0.0,
            bits[2].toDoubleOrNull() ?: 0.0, p.dbl("cost_price"))
    }

    fun dropHeld(id: String) = db.delete("held_sales", id)

    // ── Generic CRUD used by the metadata-driven screens ─────────────────────
    fun saveRow(table: String, id: String?, values: Map<String, Any?>, timestamps: Boolean = true): String {
        val now = Clock.nowIso()
        val v = LinkedHashMap(values)
        if (timestamps) {
            if (id == null) v["created_at"] = now
            v["updated_at"] = now
        }
        return if (id == null) db.insert(table, v)
        else { db.update(table, id, v); id }
    }

    fun rows(table: String, orderBy: String = "name", search: String = "", searchCols: List<String> = emptyList()): List<Row> {
        return if (search.isBlank() || searchCols.isEmpty()) db.q("SELECT * FROM $table ORDER BY $orderBy")
        else {
            val like = "%$search%"
            val where = searchCols.joinToString(" OR ") { "$it LIKE ?" }
            db.q("SELECT * FROM $table WHERE $where ORDER BY $orderBy", *List(searchCols.size) { like }.toTypedArray())
        }
    }

    fun deleteRow(table: String, id: String) = db.delete(table, id)

    // ── Backup / restore ─────────────────────────────────────────────────────
    fun backupTo(target: File) {
        db.conn.createStatement().use { st ->
            st.execute("PRAGMA wal_checkpoint(FULL)")
        }
        target.parentFile?.mkdirs()
        java.nio.file.Files.copy(
            dbFile().toPath(), target.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING
        )
    }

    fun dbFile(): File = db.file

    companion object {
        var DB_FILE: File = File(System.getProperty("user.home"), ".deryaccount/deryaccount.db")

        /** Opens (or creates) the books. The desktop app keeps everything in this one file. */
        fun open(file: File = DB_FILE): Repo {
            file.parentFile?.mkdirs()
            return Repo(DatabaseShop(file))
        }
    }
}
