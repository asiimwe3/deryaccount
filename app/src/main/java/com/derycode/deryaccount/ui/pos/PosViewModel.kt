package com.derycode.deryaccount.ui.pos

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.derycode.deryaccount.data.local.AppDatabase
import com.derycode.deryaccount.data.local.entity.Sale
import com.derycode.deryaccount.data.local.entity.SaleItem
import androidx.room.withTransaction
import com.derycode.deryaccount.data.repository.PosRepository
import com.derycode.deryaccount.util.EscPosPrinter
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class PosViewModel(
    private val db: AppDatabase,
    private val branchId: String,
    private val userId: String,
    private val appContext: Context
) : ViewModel() {

    data class CartLineUi(
        val product: com.derycode.deryaccount.data.local.entity.Product,
        val qty: Double,
        val unitPrice: Double
    ) {
        val lineTotal: Double get() = (qty * unitPrice * 100).roundToInt() / 100.0
    }

    data class ReceiptUi(val sale: Sale, val items: List<SaleItem>, val printed: String?,
                         val pdfPath: String? = null, val shopName: String = "My Shop")

    data class UiState(
        val cart: List<CartLineUi> = emptyList(),
        val unknownBarcode: String? = null,
        val searchResults: List<com.derycode.deryaccount.data.local.entity.Product> = emptyList(),
        val saleType: String = "RETAIL",
        val subtotal: Double = 0.0,
        val discount: Double = 0.0,
        val total: Double = 0.0,
        val pendingMethod: String? = null,
        val showPaymentDialog: Boolean = false,
        val lastReceipt: ReceiptUi? = null,
        val error: String? = null,
        val heldSales: List<com.derycode.deryaccount.data.local.entity.HeldSale> = emptyList(),
        val customerId: String? = null,     // optional customer attached before checkout (any payment method)
        val customerName: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Customers for credit sales (pick who is buying on credit). */
    val customers = db.customerDao().observeAll()

    /** Full product catalog for the tap-to-sell tiles — favourites float to the top. */
    val catalog: Flow<List<com.derycode.deryaccount.data.local.entity.Product>> =
        db.productDao().catalogueForBranch(branchId)

    init {
        // Live list of parked carts (Hold Sale) — updates the badge instantly
        viewModelScope.launch {
            db.heldSaleDao().forBranch(branchId).collect { held ->
                _uiState.update { it.copy(heldSales = held) }
            }
        }
    }

    /** Quick-add: create product from an unknown barcode scan, then sell it immediately. */
    fun quickAddProduct(name: String, price: Double, cost: Double, qty: Double, barcode: String?) {
        viewModelScope.launch {
            val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                .format(java.util.Date())
            val id = java.util.UUID.randomUUID().toString()
            // Atomic: product, movement and purchase entry save together or not at
            // all — and the purchase posts at COST (not retail) so the Stock
            // account always equals the stock list.
            db.withTransaction {
                db.productDao().upsert(com.derycode.deryaccount.data.local.entity.Product(
                    id = id, name = name, barcode = barcode, category = "General", unit = "pcs",
                    costPrice = cost, retailPrice = price, wholesalePrice = null,
                    stockQty = qty, lowStockAlert = 5.0, expiryDate = null,
                    branchId = branchId, createdAt = now, updatedAt = now
                ))
                if (qty > 0) db.stockMovementDao().upsert(
                    com.derycode.deryaccount.data.local.entity.StockMovement(
                        id = java.util.UUID.randomUUID().toString(), productId = id,
                        branchId = branchId, type = "PURCHASE", qty = qty,
                        reference = null, note = "quick add", movedAt = now,
                        createdAt = now, updatedAt = now))
                if (cost * qty > 0) {
                    com.derycode.deryaccount.accounting.AccountingRepo(db).apply {
                        ensureSeeded()
                        postPurchase(cost * qty, "CASH", "quick add $name")
                    }
                }
            }
            db.productDao().get(id)?.let { addProductInternal(it) }
        }
    }

    private val repo = PosRepository(appContext, db)

    /** Best-effort connectivity check for the status pill — the app never depends on this. */
    fun isOnline(): Boolean = try {
        com.derycode.deryaccount.sync.SyncEngine(appContext, db).isOnline()
    } catch (_: Exception) { false }

    /** Manual Sync: one full push+pull cycle. Report what happened, never crash. */
    fun manualSync(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val msg = try {
                val r = com.derycode.deryaccount.sync.SyncEngine(appContext, db).syncAll()
                if (!r.wasOnline) "Offline — saved locally, will sync when back online"
                else "Synced ✓ ${r.pushed} up, ${r.pulled} down"
            } catch (_: Exception) { "Sync failed — data saved locally" }
            onResult(msg)
        }
    }

    // ---- interactions ----

    fun onScan(input: String) {
        viewModelScope.launch {
            val exact = db.productDao().findByBarcode(input, branchId)
            if (exact != null) {
                addProductInternal(exact)
                _uiState.update { it.copy(searchResults = emptyList()) }
            } else {
                val results = db.productDao().search(input, branchId)
                if (results.isEmpty() && input.length >= 6 && input.all { it.isDigit() }) {
                    // Unknown barcode → offer instant product creation (cashier keeps moving)
                    _uiState.update { it.copy(unknownBarcode = input, searchResults = emptyList()) }
                } else {
                    _uiState.update { it.copy(searchResults = results) }
                }
            }
        }
    }

    fun dismissQuickAdd() { _uiState.update { it.copy(unknownBarcode = null) } }

    /** Create a customer on the spot, then hand the id back for the credit sale. */
    fun addCustomer(name: String, phone: String?, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                .format(java.util.Date())
            val id = java.util.UUID.randomUUID().toString()
            db.customerDao().upsert(com.derycode.deryaccount.data.local.entity.Customer(
                id = id, name = name.trim(), phone = phone,
                createdAt = now, updatedAt = now))
            onCreated(id)
        }
    }

    fun addProduct(p: com.derycode.deryaccount.data.local.entity.Product) {
        viewModelScope.launch {
            addProductInternal(p)
            _uiState.update { it.copy(searchResults = emptyList()) }
        }
    }

    private fun addProductInternal(p: com.derycode.deryaccount.data.local.entity.Product) {
        _uiState.update { st ->
            val existing = st.cart.find { it.product.id == p.id }
            val saleType = st.saleType
            if (p.stockQty <= 0) {
                return@update recompute(st.copy(error = "${p.name} is out of stock"))
            }
            val newQty = (existing?.qty ?: 0.0) + 1
            if (newQty > p.stockQty) {
                return@update recompute(st.copy(
                    error = "Only ${fmtNum(p.stockQty)} ${p.name} in stock"))
            }
            val price = repo.priceFor(p, newQty, saleType)
            val cart = if (existing != null)
                st.cart.map { if (it.product.id == p.id) it.copy(qty = newQty, unitPrice = price) else it }
            else st.cart + CartLineUi(p, newQty, price)
            recompute(st.copy(cart = cart))
        }
    }

    /** Add a specific quantity in one tap — used by the card's stepper + cart button. */
    fun addProductQty(p: com.derycode.deryaccount.data.local.entity.Product, qty: Double) {
        if (qty <= 0) return
        _uiState.update { st ->
            if (p.stockQty <= 0) {
                return@update recompute(st.copy(error = "${p.name} is out of stock"))
            }
            val existing = st.cart.find { it.product.id == p.id }
            var newQty = (existing?.qty ?: 0.0) + qty
            var capped = false
            if (newQty > p.stockQty) { newQty = p.stockQty; capped = true }
            val price = repo.priceFor(p, newQty, st.saleType)
            val cart = if (existing != null)
                st.cart.map { if (it.product.id == p.id) it.copy(qty = newQty, unitPrice = price) else it }
            else st.cart + CartLineUi(p, newQty, price)
            recompute(st.copy(cart = cart,
                error = if (capped) "Only ${fmtNum(p.stockQty)} ${p.name} in stock" else st.error))
        }
    }

    /** Clear the transient error line on the Sell screen. */
    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    /** Attach a customer to the cart before checkout — works for cash/MoMo too, not just credit. */
    fun setCustomer(id: String?, name: String?) {
        _uiState.update { it.copy(customerId = id, customerName = name) }
    }

    fun setQty(productId: String, qty: Double) {
        _uiState.update { st ->
            val line = st.cart.find { it.product.id == productId } ?: return@update st
            if (qty <= 0) recompute(st.copy(cart = st.cart.filter { it.product.id != productId }))
            else {
                // Real stock only — the books must never go negative.
                val available = line.product.stockQty
                if (qty > available) {
                    val clampedCart = st.cart.map {
                        if (it.product.id == productId)
                            it.copy(qty = available, unitPrice = repo.priceFor(it.product, available, st.saleType))
                        else it
                    }
                    recompute(st.copy(cart = clampedCart,
                        error = "Only ${fmtNum(available)} ${line.product.name} in stock"))
                } else {
                    val price = repo.priceFor(line.product, qty, st.saleType)
                    recompute(st.copy(cart = st.cart.map {
                        if (it.product.id == productId) it.copy(qty = qty, unitPrice = price) else it
                    }))
                }
            }
        }
    }

    private fun fmtNum(v: Double): String =
        if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

    fun removeLine(productId: String) {
        _uiState.update { recompute(it.copy(cart = it.cart.filter { l -> l.product.id != productId })) }
    }

    /** Empty the cart and drop any attached customer/discount — a fresh start. */
    fun clearCart() {
        _uiState.update { recompute(it.copy(cart = emptyList(), discount = 0.0, customerId = null, customerName = null)) }
    }

    fun toggleSaleType() {
        _uiState.update { st ->
            val newType = if (st.saleType == "RETAIL") "WHOLESALE" else "RETAIL"
            val cart = st.cart.map { l -> l.copy(unitPrice = repo.priceFor(l.product, l.qty, newType)) }
            recompute(st.copy(saleType = newType, cart = cart))
        }
    }

    // ---- Hold Sale: park the cart, serve the next customer, resume later ----

    /** Cashier parks the current cart. Empties the sale screen, keeps everything. */
    fun holdSale(note: String) {
        val st = _uiState.value
        if (st.cart.isEmpty()) return
        viewModelScope.launch {
            // Cart lines as compact JSON: [[productId, qty, unitPrice], ...]
            val arr = org.json.JSONArray()
            st.cart.forEach { l ->
                arr.put(org.json.JSONArray().put(l.product.id).put(l.qty).put(l.unitPrice))
            }
            val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                .format(java.util.Date())
            db.heldSaleDao().insert(com.derycode.deryaccount.data.local.entity.HeldSale(
                id = java.util.UUID.randomUUID().toString(),
                branchId = branchId, userId = userId,
                discount = st.discount, linesJson = arr.toString(),
                note = note.ifBlank { "Held sale" }, createdAt = now))
            _uiState.update { it.copy(cart = emptyList(), discount = 0.0) }
            recompute(_uiState.value)
        }
    }

    /** Resume a parked cart. If a cart is already open, it is auto-held first. */
    fun resumeHeldSale(id: String) {
        viewModelScope.launch {
            val held = db.heldSaleDao().byId(id) ?: return@launch
            val current = _uiState.value
            if (current.cart.isNotEmpty()) holdSale("Auto-held")   // swap, don't destroy
            val arr = org.json.JSONArray(held.linesJson)
            val cart = mutableListOf<CartLineUi>()
            for (i in 0 until arr.length()) {
                val line = arr.getJSONArray(i)
                val product = db.productDao().get(line.getString(0)) ?: continue  // deleted product → skip
                cart.add(CartLineUi(product, line.getDouble(1), line.getDouble(2)))
            }
            _uiState.update { recompute(it.copy(cart = cart.toList(), discount = held.discount)) }
            db.heldSaleDao().delete(id)   // one-shot: resuming clears the hold
        }
    }

    /** Throw a parked cart away entirely. */
    fun discardHeldSale(id: String) {
        viewModelScope.launch { db.heldSaleDao().delete(id) }
    }

    /** How many cart lines a parked sale holds (for the sheet subtitle). */
    fun heldLineCount(held: com.derycode.deryaccount.data.local.entity.HeldSale): Int = try {
        org.json.JSONArray(held.linesJson).length()
    } catch (_: Exception) { 0 }

    // ---- Favourites: star fast sellers, they float to the top of the tiles ----
    fun toggleFavourite(productId: String, fav: Boolean) {
        viewModelScope.launch { db.productDao().setFavourite(productId, fav) }
    }

    // ---- Discount: typed by the cashier, clamped so total never goes negative ----
    fun setDiscount(amount: Double) {
        _uiState.update { st ->
            val max = st.cart.sumOf { it.lineTotal }
            recompute(st.copy(discount = amount.coerceIn(0.0, max)))
        }
    }

    var pendingMethod: String?
        get() = _uiState.value.pendingMethod
        set(v) { _uiState.update { it.copy(pendingMethod = v, showPaymentDialog = v != null) } }

    var showPaymentDialog: Boolean
        get() = _uiState.value.showPaymentDialog
        set(v) { _uiState.update { it.copy(showPaymentDialog = v) } }

    fun checkout(method: String, amountPaid: Double, customerId: String? = null) {
        val st = _uiState.value
        if (st.cart.isEmpty()) { _uiState.update { it.copy(error = "Cart is empty") }; return }
        val effectiveCustomerId = customerId ?: st.customerId
        viewModelScope.launch {
            try {
                val lines = st.cart.map { PosRepository.CartLine(it.product, it.qty, it.unitPrice) }
                val result = repo.checkout(
                    branchId = branchId, userId = userId, lines = lines,
                    customerId = effectiveCustomerId, saleType = st.saleType,
                    amountPaid = amountPaid, paymentMethod = method,
                    discount = st.discount
                )
                // (Books are posted atomically inside repo.checkout — see PosRepository.)
                // Business profile: the shop's own name & details on the receipt
                val profile = try {
                    com.derycode.deryaccount.util.SessionManager(appContext).businessProfileNow()
                } catch (_: Exception) { null }
                val branch = db.branchDao().get(branchId)
                val header = com.derycode.deryaccount.util.EscPosPrinter.BizHeader(
                    name = profile?.name?.ifBlank { null } ?: branch?.name ?: "My Shop",
                    tagline = profile?.tagline ?: "",
                    phone = profile?.phone ?: "",
                    location = profile?.location ?: branch?.location ?: "",
                    tin = profile?.tin ?: "",
                    footer = profile?.footer ?: "Thank you! Karibu tena!")
                val shopName = header.name
                var pdfPath: String? = null
                try {
                    val txt = com.derycode.deryaccount.util.DeviceStore.buildReceiptText(
                        shopName, result.sale.receiptNo,
                        result.items.map { Triple(it.name, it.qty, it.lineTotal) },
                        result.sale.total, result.sale.amountPaid,
                        result.sale.changeGiven, result.sale.paymentMethod)
                    com.derycode.deryaccount.util.DeviceStore.saveReceipt(
                        appContext, result.sale.receiptNo, txt)
                } catch (_: Exception) { /* receipt text is optional */ }
                // Every sale also produces a printable PDF receipt
                try {
                    val pdf = com.derycode.deryaccount.util.PdfExport.receiptPdf(
                        appContext, shopName, result.sale.receiptNo,
                        result.items.map { Triple(it.name, it.qty, it.lineTotal) },
                        result.sale.total, result.sale.amountPaid,
                        result.sale.changeGiven, result.sale.paymentMethod, header)
                    pdfPath = pdf.absolutePath
                } catch (_: Exception) { /* PDF optional; sale already saved */ }
                _uiState.update {
                    UiState(lastReceipt = ReceiptUi(result.sale, result.items, null, pdfPath, shopName))
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Checkout failed: ${e.message}") }
            }
        }
    }

    fun printReceipt() {
        val receipt = _uiState.value.lastReceipt ?: return
        viewModelScope.launch {
            val profile = try {
                com.derycode.deryaccount.util.SessionManager(appContext).businessProfileNow()
            } catch (_: Exception) { null }
            val branch = db.branchDao().get(branchId)
            val header = com.derycode.deryaccount.util.EscPosPrinter.BizHeader(
                name = profile?.name?.ifBlank { null } ?: branch?.name ?: "My Shop",
                tagline = profile?.tagline ?: "",
                phone = profile?.phone ?: "",
                location = profile?.location ?: branch?.location ?: "",
                tin = profile?.tin ?: "",
                footer = profile?.footer ?: "Thank you! Karibu tena!")
            val printer = EscPosPrinter(appContext)
            val ok = printer.printBluetooth(header, receipt.sale, receipt.items)
            _uiState.update {
                it.copy(lastReceipt = receipt.copy(printed =
                    if (ok) "Sent to Bluetooth printer ✓"
                    else "No printer found — receipt saved, print later"))
            }
        }
    }

    fun clearReceipt() { _uiState.update { it.copy(lastReceipt = null) } }
    fun clearSearch() { _uiState.update { it.copy(searchResults = emptyList()) } }

    private fun recompute(st: UiState): UiState {
        val subtotal = (st.cart.sumOf { it.lineTotal } * 100).roundToInt() / 100.0
        val total = ((subtotal - st.discount) * 100).roundToInt() / 100.0
        return st.copy(subtotal = subtotal, total = total)
    }

    class Factory(private val db: AppDatabase, private val branchId: String,
                  private val userId: String, private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PosViewModel(db, branchId, userId, context) as T
    }
}
