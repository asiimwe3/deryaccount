package com.derycode.deryaccount.ui.settings

import android.content.Context
import java.io.File
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.data.local.AppDatabase
import com.derycode.deryaccount.data.remote.SupabaseClient
import com.derycode.deryaccount.sync.SyncEngine
import com.derycode.deryaccount.util.DeviceStore
import com.derycode.deryaccount.util.SessionManager
import kotlinx.coroutines.launch

/**
 * MoreScreen — sync setup & status, Supabase configuration, on-device
 * safe storage (DeryAccount folder), logout.
 */
@Composable
fun MoreScreen(
    db: AppDatabase,
    syncEngine: SyncEngine,
    session: SessionManager,
    context: Context,
    userRole: String = "OWNER",
    onNavigate: (String) -> Unit = {},
    onLogout: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var showProfile by remember { mutableStateOf(false) }
    if (showProfile) BusinessProfileDialog(session, onDone = { showProfile = false })
    var syncMsg by remember { mutableStateOf<String?>(null) }
    var sbUrl by remember { mutableStateOf("") }
    var sbKey by remember { mutableStateOf("") }
    val licenseState = remember { com.derycode.deryaccount.billing.LicenseManager.state(context) }
    var backupMsg by remember { mutableStateOf<String?>(null) }
    var restoreMsg by remember { mutableStateOf<String?>(null) }
    var restoreConfirm by remember { mutableStateOf(false) }
    val restorePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        restoreMsg = try {
            if (uri == null) null
            else {
                val dbDir = context.getDatabasePath("deryaccount.db").parentFile!!
                val pending = java.io.File(dbDir, "restore_pending.db")
                context.contentResolver.openInputStream(uri)!!.use { input ->
                    pending.outputStream().use { input.copyTo(it) }
                }
                if (pending.length() < 1024) {
                    pending.delete()
                    "That file doesn't look like a DeryAccount backup."
                } else {
                    "Restore staged ✓ — close and reopen the app to finish restoring."
                }
            }
        } catch (e: Exception) { "Restore failed: ${e.message}" }
    }
    if (restoreConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { restoreConfirm = false },
            title = { Text("Restore from backup?", fontWeight = FontWeight.Bold) },
            text = { Text("Pick a deryaccount_backup .db file (e.g. from Downloads). " +
                "It will REPLACE the current data when you next open the app. " +
                "Make a fresh backup first if unsure.") },
            confirmButton = { Button(onClick = {
                restoreConfirm = false
                restorePicker.launch(arrayOf("*/*"))
            }) { Text("Choose file") } },
            dismissButton = { TextButton(onClick = { restoreConfirm = false }) { Text("Cancel") } })
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("More", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { onNavigate("expenses") }, modifier = Modifier.weight(1f).height(52.dp)) {
                Text("Expenses", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = { onNavigate("customers") }, modifier = Modifier.weight(1f).height(52.dp)) {
                Text("Customers", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = { onNavigate("shift") }, modifier = Modifier.weight(1f).height(52.dp)) {
                Text("Shift", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(16.dp))

        val isCashier = userRole == "CASHIER"
        val isOwner = userRole == "OWNER" || userRole == "MANAGER" || userRole == "ACCOUNTANT"
        if (!isCashier) {
            Text("Business Tools", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val moneyTools = listOf(
                "custpayments" to "Customer Payments", "suppayments" to "Supplier Payments",
                "purchreturns" to "Purchase Returns", "purchorders" to "Purchase Orders")
            moneyTools.chunked(2).forEach { rowTools ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowTools.forEach { (route, label) ->
                        OutlinedButton(onClick = { onNavigate(route) },
                            modifier = Modifier.weight(1f).height(48.dp)) {
                            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (rowTools.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
            }
            val analyticsTools = listOf(
                "salescharts" to "Sales Charts", "bestsellers" to "Best Sellers",
                "deadstock" to "Dead Stock", "valuation" to "Stock Valuation",
                "profitproduct" to "Profit / Product", "profitcategory" to "Profit / Category",
                "custaging" to "Customer Aging", "supaging" to "Supplier Aging",
                "branchcompare" to "Branch Compare")
            analyticsTools.chunked(3).forEach { rowTools ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowTools.forEach { (route, label) ->
                        OutlinedButton(onClick = { onNavigate(route) },
                            modifier = Modifier.weight(1f).height(44.dp)) {
                            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (rowTools.size < 3) Spacer(Modifier.weight(3 - rowTools.size))
                }
                Spacer(Modifier.height(6.dp))
            }
            val accountingTools = listOf(
                "journal" to "Journal", "vat" to "VAT Report", "bankrec" to "Bank Rec.",
                "assets" to "Fixed Assets", "budgets" to "Budgets", "payroll" to "Payroll")
            accountingTools.chunked(3).forEach { rowTools ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowTools.forEach { (route, label) ->
                        OutlinedButton(onClick = { onNavigate(route) },
                            modifier = Modifier.weight(1f).height(44.dp)) {
                            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (rowTools.size < 3) Spacer(Modifier.weight(3 - rowTools.size))
                }
                Spacer(Modifier.height(6.dp))
            }
            val dataTools = listOf(
                "csvimport" to "Import CSV", "csvexport" to "Export CSV",
                "batches" to "Batches", "serials" to "Serials", "syncstatus" to "Sync Status")
            dataTools.chunked(3).forEach { rowTools ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowTools.forEach { (route, label) ->
                        OutlinedButton(onClick = { onNavigate(route) },
                            modifier = Modifier.weight(1f).height(44.dp)) {
                            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (rowTools.size < 3) Spacer(Modifier.weight(3 - rowTools.size))
                }
                Spacer(Modifier.height(6.dp))
            }
            if (userRole == "OWNER" || userRole == "MANAGER") {
                OutlinedButton(onClick = { onNavigate("permissions") },
                    modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text("Users & Permissions", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
            }
        }

        Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        // ---- Business profile: the shop's identity on receipts ----
        val profileName = session.businessProfile
            .collectAsState(initial = null).value?.name
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Business profile", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    (if (profileName.isNullOrBlank())
                        "Add your business name, phone and location — printed on every receipt."
                    else "Receipts are printed as $profileName")
                        + " with your own receipt numbers.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { showProfile = true }) {
                    Text(if (profileName.isNullOrBlank()) "Create business profile" else "Edit business profile")
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // ---- On-device safe storage ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Device Storage", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Every receipt is saved automatically to the DeryAccount folder on this device, " +
                        "so records stay safe even if the app or tablet has a problem.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    scope.launch {
                        val dbPath = context.getDatabasePath("deryaccount.db").absolutePath
                        val f = DeviceStore.backupDatabase(context, dbPath)
                        // Second copy into public Downloads — survives uninstall,
                        // so the books can be restored on a new install or new device.
                        val downloads = DeviceStore.backupToDownloads(context, dbPath)
                        backupMsg = when {
                            downloads != null -> "Backup saved in DeryAccount/backups and in Downloads (safe from uninstall) ✓"
                            f != null -> "Backup saved in DeryAccount/backups ✓"
                            else -> "Backup failed — try again"
                        }
                    }
                }) { Text("Backup all data to device") }
                backupMsg?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { restoreConfirm = true }) {
                    Text("Restore from backup file")
                }
                restoreMsg?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { context.shareErrorLogs() }) {
                    Text("Send error logs (WhatsApp)")
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---- Subscription & Pricing ----
        Card(Modifier.fillMaxWidth().clickable { onNavigate("subscription") }) {
            Row(Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(Icons.Default.Verified, null,
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Subscription & Pricing", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (licenseState.isLicensed) "Plan: ${licenseState.plan.displayName} — licensed"
                        else if (licenseState.isTrial) "Free trial — ${licenseState.trialDaysLeft} day(s) left"
                        else "Trial ended — running on Starter. Tap to see plans",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Default.KeyboardArrowRight, null)
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---- Sync ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Cloud Sync", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Sales sync automatically whenever internet is available. Offline sales are kept safe on this device.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    scope.launch {
                        val r = syncEngine.syncAll()
                        syncMsg = if (!r.wasOnline) "Device is offline — sales kept safe, will sync later"
                        else "Synced: pushed ${r.pushed}, pulled ${r.pulled} records"
                    }
                }) { Text("Sync now") }
                syncMsg?.let { Spacer(Modifier.height(6.dp)); Text(it, fontSize = 13.sp) }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---- Supabase config (multi-branch link) ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Connect to Cloud (multi-branch)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Enter the DeryAccount Supabase URL & key to link branches. Not required for single-branch offline use.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(sbUrl, { sbUrl = it }, label = { Text("Supabase URL") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(sbKey, { sbKey = it }, label = { Text("Anon key") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    if (sbUrl.isNotBlank() && sbKey.isNotBlank()) {
                        scope.launch {
                            session.saveSupabase(sbUrl.trim(), sbKey.trim())
                            SupabaseClient.init(sbUrl.trim(), sbKey.trim())
                            val r = syncEngine.syncAll()
                            syncMsg = "Connected & synced: pushed ${r.pushed}, pulled ${r.pulled}"
                        }
                    }
                }) { Text("Connect") }
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = {
                scope.launch {
                    syncEngine.syncAll()
                    session.logout()
                    onLogout()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Log out", color = MaterialTheme.colorScheme.error) }
    }
}

/**
 * Packages the newest crash logs from DeryAccount/crashes into a single
 * text file and opens WhatsApp with it attached — one tap, no file
 * manager needed.
 */
private fun Context.shareErrorLogs() {
    try {
        val crashDir = File(DeviceStore.baseDir(this), "crashes")
        val logs = crashDir.listFiles { f -> f.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            ?.take(5)
            .orEmpty()
        if (logs.isEmpty()) {
            android.widget.Toast.makeText(this,
                "No error logs found — nothing to send", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val text = logs.joinToString("\n\n================================\n\n") { file ->
            "FILE: ${file.name}\n\n" + file.readText()
        }
        val outFile = File(DeviceStore.baseDir(this), "error_logs.txt")
        outFile.writeText(text)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            this, "$packageName.fileprovider", outFile)
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage("com.whatsapp")
        }
        try {
            startActivity(send)
        } catch (_: android.content.ActivityNotFoundException) {
            // No WhatsApp — open the general share sheet instead
            val generic = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(generic, "Send error logs"))
        }
    } catch (e: Exception) {
        try {
            com.derycode.deryaccount.util.DbSafety.log(this, "Share error logs", e)
        } catch (_: Exception) { }
    }
}
