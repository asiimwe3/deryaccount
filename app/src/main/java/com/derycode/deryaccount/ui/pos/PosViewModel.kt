package com.derycode.deryaccount.ui.pos

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.derycode.deryaccount.data.local.AppDatabase
import com.derycode.deryaccount.data.local.entity.Sale
import com.derycode.deryaccount.data.local.entity.SaleItem
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
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Customers for credit sales (pick who is buying on credit). */
    val customers = db.customerDao().observeAll()

    /** Full product catalog for the tap-to-sell tiles. */
    val catalog: Flow<List<com.derycode.deryaccount.data.local.entity.Product>> =
        db.productDao().observeBranchProducts(branchId)

    /** Quick-add: create product from an unknown barcode scan, then sell it immediately. */
    fun quickAddProduct(name: String, price: Double, cost: Double, qty: Double, barcode: String?) {
        viewModelScope.launch {
            val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                .format(java.util.Date())
            val id = java.util.UUID.randomUUID().toString()
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
            // Books: Dr Stock, Cr Cash for the stock just created
            try {
                com.derycode.deryaccount.accounting.AccountingRepo(db).apply {
                    ensureSeeded()
                    postPurchase(price * qty, "CASH", "quick add $name")
                }
            } catch (_: Exception) { }
            db.productDao().get(id)?.let { addProductInternal(it) }
        }
    }

    private val repo = PosRepository(appContext, db)

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
            val cart = if (existing != null) {
                val newQty = existing.qty + 1
                val price = repo.priceFor(p, newQty, saleType)
                st.cart.map { if (it.product.id == p.id) it.copy(qty = newQty, unitPrice = price) else it }
            } else {
                val price = repo.priceFor(p, 1.0, saleType)
                st.cart + CartLineUi(p, 1.0, price)
            }
            recompute(st.copy(cart = cart))
        }
    }

    fun setQty(productId: String, qty: Double) {
        _uiState.update { st ->
            if (qty <= 0) st.copy(cart = st.cart.filter { it.product.id != productId }).let(::recompute)
            else {
                val price = repo.priceFor(
                    st.cart.find { it.product.id == productId }?.product
                        ?: return@update st, qty, st.saleType)
                recompute(st.copy(cart = st.cart.map {
                    if (it.product.id == productId) it.copy(qty = qty, unitPrice = price) else it
                }))
            }
        }
    }

    fun removeLine(productId: String) {
        _uiState.update { recompute(it.copy(cart = it.cart.filter { l -> l.product.id != productId })) }
    }

    fun toggleSaleType() {
        _uiState.update { st ->
            val newType = if (st.saleType == "RETAIL") "WHOLESALE" else "RETAIL"
            val cart = st.cart.map { l -> l.copy(unitPrice = repo.priceFor(l.product, l.qty, newType)) }
            recompute(st.copy(saleType = newType, cart = cart))
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
        viewModelScope.launch {
            try {
                val lines = st.cart.map { PosRepository.CartLine(it.product, it.qty, it.unitPrice) }
                val result = repo.checkout(
                    branchId = branchId, userId = userId, lines = lines,
                    customerId = customerId, saleType = st.saleType,
                    amountPaid = amountPaid, paymentMethod = method,
                    discount = st.discount
                )
                // Post to the books of account: Dr Cash/MoMo/Debtors, Cr Sales
                try {
                    com.derycode.deryaccount.accounting.AccountingRepo(db).apply {
                        ensureSeeded()
                        postSale(result.sale.total, result.sale.paymentMethod,
                            result.sale.receiptNo, result.items.size,
                            st.cart.sumOf { it.qty * it.product.costPrice })
                    }
                } catch (_: Exception) { /* sale already saved; ledger retryable later */ }
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
