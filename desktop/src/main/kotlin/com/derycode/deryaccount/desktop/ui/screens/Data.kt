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
import java.io.File

@Composable
fun DataScreen(app: AppState) {
    val repo = app.repo
    val admin = app.admin
    var table by remember { mutableStateOf("products") }
    var message by remember { mutableStateOf<String?>(null) }
    var backupPath by remember { mutableStateOf("") }
    var restorePath by remember { mutableStateOf("") }
    var importKind by remember { mutableStateOf("Products") }
    var importPath by remember { mutableStateOf("") }
    var counts by remember { mutableStateOf(rowCounts(repo)) }

    val tables = listOf(
        "products", "customers", "suppliers", "sales", "sale_items", "purchases", "purchase_items",
        "expenses", "customer_payments", "supplier_payments", "journal_entries", "journal_lines",
        "accounts", "stock_movements", "shifts", "fixed_assets", "employees", "payslips", "budgets",
        "batches", "serials", "bank_lines", "purchase_orders", "purchase_returns", "sales_returns"
    )

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        ScreenHeader("Data, backup & exports", "Your books live in one file on this computer — keep a copy somewhere safe")

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            CardBox(Modifier.weight(1f)) {
                Text("Export to CSV / Excel", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                Text("Opens in Excel, Google Sheets or anywhere else.", fontSize = 11.sp, color = Da.Muted)
                Spacer(Modifier.height(10.dp))
                Drop("Table", tables, table, { table = it })
                Spacer(Modifier.height(10.dp))
                Btn("Export $table", {
                    runCatching {
                        val dir = File(System.getProperty("user.home"), "Documents/DeryAccount/exports")
                        dir.mkdirs()
                        val f = File(dir, "$table-${Clock.today()}.csv")
                        val n = admin.exportTable(table, f)
                        message = "$n rows exported to ${f.absolutePath}"
                    }.onFailure { message = it.message }
                }, Icons.Default.FileDownload, BtnKind.PRIMARY)
                Spacer(Modifier.height(10.dp))
                Text("Every table can be exported: sales, stock, the journal, debtors, everything.",
                    fontSize = 11.sp, color = Da.Dim)
            }

            CardBox(Modifier.weight(1f)) {
                Text("Import from CSV", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                Text("Bulk-load a product list or customer book from a spreadsheet.", fontSize = 11.sp, color = Da.Muted)
                Spacer(Modifier.height(10.dp))
                Drop("What to import", listOf("Products", "Customers"), importKind, { importKind = it })
                Spacer(Modifier.height(10.dp))
                Field("CSV file path", importPath, { importPath = it }, placeholder = "C:\\Users\\you\\products.csv")
                Spacer(Modifier.height(10.dp))
                Btn("Import now", {
                    runCatching {
                        val f = File(importPath)
                        if (!f.exists()) error("File not found: ${f.path}")
                        val (created, updated) = if (importKind == "Products") admin.importProducts(f) else admin.importCustomers(f)
                        message = "$created new, $updated updated"
                        app.refresh()
                    }.onFailure { message = it.message ?: "Import failed" }
                }, Icons.Default.FileUpload, BtnKind.PRIMARY)
                Spacer(Modifier.height(10.dp))
                Text("Products CSV needs a 'name' column; barcode, category, unit, cost_price, retail_price and stock_qty are used when present. Customers CSV needs 'name', plus phone, address, type and credit_limit when present.",
                    fontSize = 10.5.sp, color = Da.Dim)
            }

            CardBox(Modifier.weight(1f)) {
                Text("Backup & restore", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                Text("The whole business is one database file. Copy it to a flash disk every evening.",
                    fontSize = 11.sp, color = Da.Muted)
                Spacer(Modifier.height(10.dp))
                Field("Backup folder", backupPath, { backupPath = it },
                    placeholder = File(System.getProperty("user.home"), "Documents/DeryAccount").absolutePath)
                Spacer(Modifier.height(8.dp))
                Btn("Back up now", {
                    runCatching {
                        val dir = File(backupPath.ifBlank { File(System.getProperty("user.home"), "Documents/DeryAccount").absolutePath })
                        dir.mkdirs()
                        val f = File(dir, "deryaccount-backup-${Clock.today()}.db")
                        repo.backupTo(f)
                        message = "Backup saved to ${f.absolutePath}"
                    }.onFailure { message = it.message ?: "Backup failed" }
                }, Icons.Default.Backup, BtnKind.PRIMARY)
                Spacer(Modifier.height(12.dp))
                Field("Restore from file", restorePath, { restorePath = it }, placeholder = "C:\\path\\deryaccount-backup.db")
                Spacer(Modifier.height(8.dp))
                Btn("Restore (replaces this data)", {
                    runCatching {
                        val f = File(restorePath)
                        if (!f.exists()) error("File not found")
                        repo.db.conn.createStatement().use { it.execute("PRAGMA wal_checkpoint(FULL)") }
                        java.nio.file.Files.copy(f.toPath(), repo.dbFile().toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                        message = "Restored. Close and reopen DeryAccount to load the restored books."
                        app.refresh()
                    }.onFailure { message = it.message ?: "Restore failed" }
                }, Icons.Default.Restore, BtnKind.DANGER)
                Spacer(Modifier.height(10.dp))
                Text("Database: ${repo.dbFile().absolutePath}", fontSize = 10.sp, color = Da.Dim)
            }
        }

        message?.let {
            Spacer(Modifier.height(12.dp))
            CardBox(Modifier.fillMaxWidth(), background = Da.Surface2) { Text(it, fontSize = 12.sp, color = Da.Text) }
        }

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            CardBox(Modifier.weight(1.6f), padding = 0.dp) {
                Column {
                    Text("What is inside the file", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Da.Text,
                        modifier = Modifier.padding(12.dp))
                    Table(
                        listOf("Table", "Rows"),
                        listOf(3f, 1f),
                        counts.map { listOf(Cell(it.first), Cell(it.second.toString(), Da.Muted, align = TextAlign.End)) },
                        scrollable = false
                    )
                }
            }
            CardBox(Modifier.weight(1f)) {
                Text("Demo data", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                Text("Load a full sample shop — 12 products, suppliers, customers, sales history, expenses and a delivery motorcycle — to explore every report.",
                    fontSize = 11.5.sp, color = Da.Muted, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
                Btn("Load sample shop", {
                    runCatching {
                        admin.loadSampleData(); app.refresh(); counts = rowCounts(repo)
                        message = "Sample shop loaded"
                    }.onFailure { message = it.message ?: "Could not load sample data" }
                }, Icons.Default.Storefront, BtnKind.GHOST)
                Spacer(Modifier.height(12.dp))
                Text("Only runs on an empty file, so sample sales can never mix into real books.",
                    fontSize = 11.sp, color = Da.Dim)
            }
        }
    }
}

private fun rowCounts(repo: Repo): List<Pair<String, Int>> = listOf(
    "Products" to repo.db.count("products"),
    "Customers" to repo.db.count("customers"),
    "Suppliers" to repo.db.count("suppliers"),
    "Sales" to repo.db.count("sales"),
    "Sale lines" to repo.db.count("sale_items"),
    "Purchases" to repo.db.count("purchases"),
    "Expenses" to repo.db.count("expenses"),
    "Journal entries" to repo.db.count("journal_entries"),
    "Journal lines" to repo.db.count("journal_lines"),
    "Stock movements" to repo.db.count("stock_movements"),
    "Employees" to repo.db.count("employees"),
    "Fixed assets" to repo.db.count("fixed_assets")
)
