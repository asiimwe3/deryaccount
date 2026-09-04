package com.derycode.deryaccount.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.*
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
    onNavigate: (String) -> Unit = {},
    onLogout: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var showProfile by remember { mutableStateOf(false) }
    if (showProfile) BusinessProfileDialog(session, onDone = { showProfile = false })
    var syncMsg by remember { mutableStateOf<String?>(null) }
    var sbUrl by remember { mutableStateOf("") }
    var sbKey by remember { mutableStateOf("") }
    var backupMsg by remember { mutableStateOf<String?>(null) }

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
        }
        Spacer(Modifier.height(16.dp))

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
                        backupMsg = if (f != null) "Backup saved in DeryAccount/backups ✓"
                        else "Backup failed — try again"
                    }
                }) { Text("Backup all data to device") }
                backupMsg?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontSize = 13.sp)
                }
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
