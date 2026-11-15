package com.derycode.deryaccount.desktop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.desktop.data.*
import com.derycode.deryaccount.desktop.ui.*

@Composable
fun StockScreen(app: AppState) {
    val repo = app.repo
    var tab by remember { mutableStateOf(0) }
    var search by remember { mutableStateOf("") }
    var products by remember { mutableStateOf(repo.products(search = "")) }
    var editing by remember { mutableStateOf<Row?>(null) }
    var creating by remember { mutableStateOf(false) }
    var showAddStock by remember { mutableStateOf<Row?>(null) }
    var showCount by remember { mutableStateOf<Row?>(null) }
    var showWriteOff by remember { mutableStateOf<Row?>(null) }
    var showTransfer by remember { mutableStateOf<Row?>(null) }
    var onlyLow by remember { mutableStateOf(false) }

    LaunchedEffect(app.tick, search) { products = repo.products(search = search) }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        ScreenHeader("Stock", "Products, deliveries, counts and tracked items") {
            Btn("Add product", { creating = true }, Icons.Default.Add)
        }
        TabBar(listOf("Products", "Add stock", "Stock count", "Write-off", "Transfer", "Batches", "Serial numbers"), tab, { tab = it })
        Spacer(Modifier.height(14.dp))
        when (tab) {
            0 -> Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SearchBar(search, { search = it }, "Search name, barcode or category") }
                    Btn(if (onlyLow) "Showing low stock" else "Low stock only", { onlyLow = !onlyLow },
                        Icons.Default.FilterAlt, if (onlyLow) BtnKind.SUBTLE else BtnKind.GHOST)
                }
                Spacer(Modifier.height(10.dp))
                val list = if (onlyLow) products.filter { it.dbl("stock_qty") <= it.dbl("low_stock_alert") } else products
                CardBox(Modifier.fillMaxSize(), padding = 0.dp) {
                    Table(
                        listOf("Product", "Category", "Stock", "Cost", "Retail", "Value at cost", "Status"),
                        listOf(2.2f, 1.2f, 0.8f, 0.9f, 0.9f, 1f, 0.9f),
                        list.map { p ->
                            val stock = p.dbl("stock_qty")
                            val status = when {
                                stock <= 0 -> Cell("OUT", Da.Red, true)
                                stock <= p.dbl("low_stock_alert") -> Cell("LOW", Da.Amber, true)
                                else -> Cell("OK", Da.Green)
                            }
                            listOf(
                                Cell(p.str("name"), bold = true),
                                Cell(p.str("category"), Da.Muted),
                                Cell("${Fmt.qty(stock)} ${p.str("unit")}", Da.Text, align = TextAlign.End),
                                Cell(Fmt.money(p.dbl("cost_price")), Da.Muted, align = TextAlign.End),
                                Cell(Fmt.money(p.dbl("retail_price")), Da.Muted, align = TextAlign.End),
                                Cell(Fmt.money(stock * p.dbl("cost_price")), Da.Blue, align = TextAlign.End),
                                status
                            )
                        },
                        onRowClick = { i -> editing = list[i] }
                    )
                }
            }
            1 -> QuickStockForm(app, "Add stock (delivery)", products) { p, qty, cost, supplier, paid ->
                repo.addStock(p.str("id"), qty, cost, "Delivery of ${p.str("name")}", supplier, paid)
                app.refresh(); app.say("Stock added to ${p.str("name")}")
            }
            2 -> QuickStockForm(app, "Stock count", products, showCost = false) { p, qty, _, _, _ ->
                repo.adjustStock(p.str("id"), qty, "Physical count")
                app.refresh(); app.say("Count saved for ${p.str("name")}")
            }
            3 -> QuickStockForm(app, "Write off stock", products, showCost = false, noteLabel = "Reason") { p, qty, _, _, _ ->
                repo.writeOffStock(p.str("id"), qty, "Damage / expiry")
                app.refresh(); app.say("Written off ${p.str("name")}")
            }
            4 -> TransferForm(app, products)
            5 -> BatchesTab(app)
            6 -> SerialsTab(app)
        }
    }

    if (creating || editing != null) {
        ProductDialog(app, editing, onClose = { creating = false; editing = null })
    }
    showAddStock?.let { showAddStock = null }
    showCount?.let { showCount = null }
    showWriteOff?.let { showWriteOff = null }
    showTransfer?.let { showTransfer = null }
}

/** Shared one-product form for deliveries, counts and write-offs. */
@Composable
private fun QuickStockForm(
    app: AppState,
    title: String,
    products: List<Row>,
    showCost: Boolean = true,
    noteLabel: String = "Note",
    onSave: (Row, Double, Double?, String?, String) -> Unit
) {
    var productName by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var supplier by remember { mutableStateOf("") }
    var paid by remember { mutableStateOf("CASH") }
    var note by remember { mutableStateOf("") }
    val suppliers = remember { app.repo.suppliers() }
    val selected = products.firstOrNull { it.str("name") == productName }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        CardBox(Modifier.weight(1.4f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Da.Text)
            Text(
                when (title) {
                    "Stock count" -> "Type what is actually on the shelf. The difference is booked as found or shrinkage stock."
                    "Write off stock" -> "Damaged, expired or missing stock leaves the shelf and hits stock losses."
                    else -> "A delivery from a supplier: stock in, cost updated, supplier balance and books posted together."
                },
                fontSize = 11.5.sp, color = Da.Muted, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
            )
            Drop("Product", products.map { it.str("name") }, productName, { productName = it })
            Spacer(Modifier.height(8.dp))
            Row {
                Box(Modifier.weight(1f)) { MoneyField(if (title == "Stock count") "Quantity counted" else "Quantity", qty, { qty = it.filter { c -> c.isDigit() || c == '.' } }) }
                if (showCost) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) { MoneyField("Unit cost (blank = current)", cost, { cost = it.filter { c -> c.isDigit() || c == '.' } }) }
                }
            }
            if (showCost) {
                Spacer(Modifier.height(8.dp))
                Row {
                    Box(Modifier.weight(1f)) {
                        Drop("Supplier", listOf("Cash purchase") + suppliers.map { it.str("name") }, supplier, { supplier = it })
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        Drop("Payment", listOf("CASH", "MTN_MOMO", "AIRTEL_MONEY", "CREDIT"), paid, { paid = it })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Field(noteLabel, note, { note = it }, placeholder = "optional")
            Spacer(Modifier.height(12.dp))
            selected?.let {
                Text(
                    "On hand now: ${Fmt.qty(it.dbl("stock_qty"))} ${it.str("unit")} · cost ${Fmt.money(it.dbl("cost_price"))}",
                    fontSize = 11.5.sp, color = Da.Blue
                )
                Spacer(Modifier.height(8.dp))
            }
            Btn("Save", {
                val p = selected ?: return@Btn
                val q = qty.toDoubleOrNull() ?: return@Btn
                val supplierId = suppliers.firstOrNull { it.str("name") == supplier }?.str("id")
                onSave(p, q, cost.toDoubleOrNull(), supplierId, paid)
                qty = ""; cost = ""; note = ""
            }, Icons.Default.Save, BtnKind.PRIMARY, enabled = selected != null && qty.toDoubleOrNull() != null)
        }
        CardBox(Modifier.weight(1f)) {
            Text("How this hits the books", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Da.Text)
            Spacer(Modifier.height(8.dp))
            listOf(
                "Stock value is always quantity × cost, so the trial balance matches the shelf.",
                "A delivery debits Stock and credits Cash (or the supplier if on credit).",
                "A count never touches cash: found stock is a gain, missing stock is a loss.",
                "Write-offs post to Stock Losses so theft and damage are visible in the income statement."
            ).forEach {
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text("•", color = Da.Green, fontSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(it, fontSize = 11.5.sp, color = Da.Muted)
                }
            }
        }
    }
}

@Composable
private fun TransferForm(app: AppState, products: List<Row>) {
    var productName by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val branches = remember { app.repo.branches() }
    val selected = products.firstOrNull { it.str("name") == productName }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        CardBox(Modifier.weight(1.4f)) {
            Text("Transfer stock between branches", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Da.Text)
            Spacer(Modifier.height(10.dp))
            Drop("Product", products.map { it.str("name") }, productName, { productName = it })
            Spacer(Modifier.height(8.dp))
            Row {
                Box(Modifier.weight(1f)) { MoneyField("Quantity", qty, { qty = it.filter { c -> c.isDigit() || c == '.' } }) }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) { Drop("To branch", branches.map { it.str("name") }, branch, { branch = it }) }
            }
            Spacer(Modifier.height(8.dp))
            Field("Note", note, { note = it })
            Spacer(Modifier.height(12.dp))
            Btn("Transfer", {
                val p = selected ?: return@Btn
                val b = branches.firstOrNull { it.str("name") == branch } ?: return@Btn
                app.repo.transferStock(p.str("id"), b.str("id"), qty.toDoubleOrNull() ?: 0.0, note)
                app.refresh(); app.say("Transfer recorded"); qty = ""
            }, Icons.Default.LocalShipping, BtnKind.PRIMARY, enabled = selected != null)
        }
        CardBox(Modifier.weight(1f)) {
            Text("Branch transfers", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Da.Text)
            Spacer(Modifier.height(8.dp))
            Table(
                listOf("Product", "To", "Qty", "Status"),
                listOf(2f, 1.2f, 0.7f, 1f),
                app.repo.db.q(
                    """SELECT t.*, p.name AS product_name, b.name AS to_name FROM stock_transfers t
                       LEFT JOIN products p ON p.id=t.product_id LEFT JOIN branches b ON b.id=t.to_branch_id
                       ORDER BY t.transferred_at DESC LIMIT 25"""
                ).map {
                    listOf(Cell(it.str("product_name")), Cell(it.str("to_name"), Da.Muted),
                        Cell(Fmt.qty(it.dbl("qty")), Da.Text, align = TextAlign.End), Cell(it.str("status"), Da.Blue))
                }
            )
        }
    }
}

@Composable
private fun BatchesTab(app: AppState) {
    var productName by remember { mutableStateOf("") }
    var batchNo by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    val products = remember(app.tick) { app.repo.products() }
    val rows = remember(app.tick) { app.repo.db.q("SELECT * FROM batches ORDER BY expiry_date") }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        CardBox(Modifier.weight(1f)) {
            Text("Add a batch / lot", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Da.Text)
            Spacer(Modifier.height(10.dp))
            Drop("Product", products.map { it.str("name") }, productName, { productName = it })
            Spacer(Modifier.height(8.dp))
            Row {
                Box(Modifier.weight(1f)) { Field("Batch number", batchNo, { batchNo = it }) }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) { Field("Expiry (yyyy-mm-dd)", expiry, { expiry = it }) }
            }
            Spacer(Modifier.height(8.dp))
            MoneyField("Quantity", qty, { qty = it.filter { c -> c.isDigit() || c == '.' } })
            Spacer(Modifier.height(12.dp))
            Btn("Save batch", {
                val p = products.firstOrNull { it.str("name") == productName } ?: return@Btn
                app.repo.db.insert("batches", mapOf(
                    "id" to java.util.UUID.randomUUID().toString(), "product_id" to p.str("id"),
                    "product_name" to p.str("name"), "batch_no" to batchNo.ifBlank { "B-${System.currentTimeMillis() % 10000}" },
                    "expiry_date" to expiry.ifBlank { null }, "qty" to (qty.toDoubleOrNull() ?: 0.0),
                    "branch_id" to app.branchId, "note" to "", "created_at" to Clock.nowIso()
                ))
                app.refresh(); app.say("Batch saved"); batchNo = ""; qty = ""; expiry = ""
            }, Icons.Default.Inventory, BtnKind.PRIMARY, enabled = productName.isNotBlank())
            Spacer(Modifier.height(10.dp))
            Text("Batches track expiry dates without changing the stock quantity: perfect for milk, bread and medicine.",
                fontSize = 11.sp, color = Da.Dim)
        }
        CardBox(Modifier.weight(1.5f)) {
            Text("Batches on file", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Da.Text)
            Spacer(Modifier.height(8.dp))
            Table(
                listOf("Product", "Batch", "Expiry", "Qty", ""),
                listOf(2f, 1.2f, 1f, 0.7f, 0.4f),
                rows.map { b ->
                    val expired = b.str("expiry_date").isNotBlank() && b.str("expiry_date") < Clock.today()
                    listOf(
                        Cell(b.str("product_name")), Cell(b.str("batch_no"), Da.Muted),
                        Cell(if (b.str("expiry_date").isBlank()) "—" else Fmt.date(b.str("expiry_date")),
                            if (expired) Da.Red else Da.Muted),
                        Cell(Fmt.qty(b.dbl("qty")), align = TextAlign.End),
                        Cell(if (expired) "EXPIRED" else "", Da.Red)
                    )
                }
            )
        }
    }
}

@Composable
private fun SerialsTab(app: AppState) {
    var productName by remember { mutableStateOf("") }
    var serial by remember { mutableStateOf("") }
    val products = remember(app.tick) { app.repo.products() }
    val rows = remember(app.tick) { app.repo.db.q("SELECT * FROM serials ORDER BY created_at DESC") }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        CardBox(Modifier.weight(1f)) {
            Text("Track a serial number", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Da.Text)
            Spacer(Modifier.height(10.dp))
            Drop("Product", products.map { it.str("name") }, productName, { productName = it })
            Spacer(Modifier.height(8.dp))
            Field("Serial number", serial, { serial = it })
            Spacer(Modifier.height(12.dp))
            Btn("Add serial", {
                val p = products.firstOrNull { it.str("name") == productName } ?: return@Btn
                if (serial.isBlank()) return@Btn
                app.repo.db.insert("serials", mapOf(
                    "id" to java.util.UUID.randomUUID().toString(), "product_id" to p.str("id"),
                    "product_name" to p.str("name"), "serial" to serial.trim(), "status" to "IN_STOCK",
                    "branch_id" to app.branchId, "created_at" to Clock.nowIso()
                ))
                app.refresh(); app.say("Serial tracked"); serial = ""
            }, Icons.Default.QrCode, BtnKind.PRIMARY, enabled = productName.isNotBlank() && serial.isNotBlank())
            Spacer(Modifier.height(10.dp))
            Text("For phones, TVs and anything with a warranty: each unit is identifiable and its sale is recorded.",
                fontSize = 11.sp, color = Da.Dim)
        }
        CardBox(Modifier.weight(1.5f)) {
            Text("Serials on file", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Da.Text)
            Spacer(Modifier.height(8.dp))
            Table(
                listOf("Product", "Serial", "Status", "Sold"),
                listOf(1.8f, 1.6f, 1f, 1f),
                rows.map {
                    listOf(
                        Cell(it.str("product_name")), Cell(it.str("serial"), Da.Muted),
                        Cell(it.str("status"), if (it.str("status") == "SOLD") Da.Dim else Da.Green),
                        Cell(if (it.str("sold_at").isBlank()) "—" else Fmt.date(it.str("sold_at")), Da.Dim)
                    )
                }
            )
        }
    }
}

@Composable
private fun ProductDialog(app: AppState, existing: Row?, onClose: () -> Unit) {
    val repo = app.repo
    var name by remember { mutableStateOf(existing?.str("name") ?: "") }
    var barcode by remember { mutableStateOf(existing?.str("barcode") ?: "") }
    var category by remember { mutableStateOf(existing?.str("category") ?: "General") }
    var subcategory by remember { mutableStateOf(existing?.str("subcategory") ?: "") }
    var unit by remember { mutableStateOf(existing?.str("unit") ?: "pcs") }
    var cost by remember { mutableStateOf(existing?.dbl("cost_price")?.takeIf { it > 0 }?.toString() ?: "") }
    var retail by remember { mutableStateOf(existing?.dbl("retail_price")?.takeIf { it > 0 }?.toString() ?: "") }
    var wholesale by remember { mutableStateOf(existing?.dbl("wholesale_price")?.takeIf { it > 0 }?.toString() ?: "") }
    var wholesaleMin by remember { mutableStateOf((existing?.int("wholesale_min_qty") ?: 10).toString()) }
    var opening by remember { mutableStateOf("0") }
    var low by remember { mutableStateOf(existing?.dbl("low_stock_alert")?.toString() ?: "5") }
    var reorder by remember { mutableStateOf(existing?.dbl("reorder_level")?.toString() ?: "0") }
    var expiry by remember { mutableStateOf(existing?.str("expiry_date") ?: "") }
    var favourite by remember { mutableStateOf(existing?.bool("is_favourite") ?: false) }
    var taxRate by remember { mutableStateOf(existing?.dbl("tax_rate")?.toString() ?: "0") }

    DialogBox(if (existing == null) "New product" else "Edit ${existing.str("name")}", onClose, 680.dp) {
        Row {
            Box(Modifier.weight(1f)) { Field("Product name", name, { name = it }) }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) { Field("Barcode", barcode, { barcode = it }, placeholder = "optional") }
        }
        Spacer(Modifier.height(10.dp))
        Row {
            Box(Modifier.weight(1f)) { Field("Category", category, { category = it }) }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) { Field("Subcategory", subcategory, { subcategory = it }, placeholder = "optional") }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.width(120.dp)) { Drop("Unit", repo.units(), unit, { unit = it }) }
        }
        Spacer(Modifier.height(10.dp))
        Row {
            Box(Modifier.weight(1f)) { MoneyField("Cost price", cost, { cost = it.filter { c -> c.isDigit() || c == '.' } }) }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) { MoneyField("Retail price", retail, { retail = it.filter { c -> c.isDigit() || c == '.' } }) }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) { MoneyField("Wholesale price", wholesale, { wholesale = it.filter { c -> c.isDigit() || c == '.' } }) }
        }
        Spacer(Modifier.height(10.dp))
        Row {
            Box(Modifier.weight(1f)) { Field("Wholesale min qty", wholesaleMin, { wholesaleMin = it.filter { c -> c.isDigit() } }, numeric = true) }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) { Field("Low stock alert", low, { low = it.filter { c -> c.isDigit() || c == '.' } }, numeric = true) }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) { Field("Reorder level", reorder, { reorder = it.filter { c -> c.isDigit() || c == '.' } }, numeric = true) }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) { Field("VAT %", taxRate, { taxRate = it.filter { c -> c.isDigit() || c == '.' } }, numeric = true) }
        }
        Spacer(Modifier.height(10.dp))
        Row {
            if (existing == null) {
                Box(Modifier.weight(1f)) { MoneyField("Opening stock qty", opening, { opening = it.filter { c -> c.isDigit() || c == '.' } }) }
                Spacer(Modifier.width(10.dp))
            }
            Box(Modifier.weight(1f)) { Field("Expiry date (yyyy-mm-dd)", expiry, { expiry = it }, placeholder = "optional") }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.width(160.dp)) {
                Column {
                    Text("Fast seller", fontSize = 11.sp, color = Da.Muted, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Btn(if (favourite) "★ Pinned to POS" else "Pin to POS", { favourite = !favourite }, Icons.Default.Star,
                        if (favourite) BtnKind.PRIMARY else BtnKind.SUBTLE)
                }
            }
        }
        if (existing != null) {
            Spacer(Modifier.height(8.dp))
            Text("Stock changes after creation go through Add stock, Stock count or Write-off, so the books stay right.",
                fontSize = 11.sp, color = Da.Dim)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn(if (existing == null) "Create product" else "Save changes", {
                if (name.isBlank()) { app.say("Give the product a name"); return@Btn }
                val values = linkedMapOf<String, Any?>(
                    "name" to name.trim(),
                    "barcode" to barcode.trim().ifBlank { null },
                    "category" to category.trim().ifBlank { "General" },
                    "subcategory" to subcategory.trim(),
                    "unit" to unit,
                    "cost_price" to (cost.toDoubleOrNull() ?: 0.0),
                    "retail_price" to (retail.toDoubleOrNull() ?: 0.0),
                    "wholesale_price" to wholesale.toDoubleOrNull(),
                    "wholesale_min_qty" to (wholesaleMin.toIntOrNull() ?: 0),
                    "low_stock_alert" to (low.toDoubleOrNull() ?: 5.0),
                    "reorder_level" to (reorder.toDoubleOrNull() ?: 0.0),
                    "expiry_date" to expiry.trim().ifBlank { null },
                    "tax_rate" to (taxRate.toDoubleOrNull() ?: 0.0),
                    "is_favourite" to if (favourite) 1 else 0
                )
                try {
                    if (existing == null) {
                        val qty = opening.toDoubleOrNull() ?: 0.0
                        val id = repo.db.insert("products", values + mapOf(
                            "stock_qty" to qty, "branch_id" to app.branchId,
                            "created_at" to Clock.nowIso(), "updated_at" to Clock.nowIso()
                        ))
                        if (qty > 0) repo.accounting.postOpeningStock(
                            Fmt.r2(qty * (cost.toDoubleOrNull() ?: 0.0)), "Opening stock $name"
                        )
                        app.say("$name added")
                    } else {
                        repo.db.update("products", existing.str("id"), values + mapOf("updated_at" to Clock.nowIso()))
                        app.say("$name saved")
                    }
                    app.refresh(); onClose()
                } catch (e: Exception) { app.say(e.message ?: "Could not save") }
            }, Icons.Default.Save, BtnKind.PRIMARY)
            if (existing != null) {
                Btn("Delete", {
                    repo.db.delete("products", existing.str("id"))
                    app.refresh(); app.say("Product deleted"); onClose()
                }, Icons.Default.Delete, BtnKind.DANGER)
            }
            Btn("Cancel", onClose, kind = BtnKind.GHOST)
        }
    }
}

private val BtnKind.SUBTLE: BtnKind get() = BtnKind.SUBTLE
