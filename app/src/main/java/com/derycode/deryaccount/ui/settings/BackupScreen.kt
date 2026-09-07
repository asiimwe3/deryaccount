package com.derycode.deryaccount.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.ui.theme.*
import com.derycode.deryaccount.util.DeviceStore
import com.derycode.deryaccount.util.Share
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * BackupScreen — prepares ONE folder with everything in the business:
 * all receipts (PDF), all reports, all saved books/invoices, and the full
 * database file — zips it, then shares it to Google Drive, TeraBox, or
 * email in one tap. 100% offline: the app never uploads anywhere itself;
 * the user chooses the destination from the Android share sheet.
 */
@Composable
fun BackupScreen(session: com.derycode.deryaccount.util.SessionManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preparing by remember { mutableStateOf(false) }
    var zipFile by remember { mutableStateOf<File?>(null) }
    var contents by remember { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun prepare() {
        preparing = true; error = null
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { buildBackup(context) }
                zipFile = result.first; contents = result.second
            } catch (e: Exception) {
                error = e.message ?: "Backup failed"
            } finally { preparing = false }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Backup & Export", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(6.dp))
        Text("Everything in one folder: all receipts, reports, books, invoices and the "
            + "complete database — zipped and ready for Google Drive, TeraBox or email.",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Icon(Icons.Default.CloudUpload, null, tint = DaGreen, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(8.dp))
                Text("Prepare backup folder", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Creates a single ZIP with all your business files.", fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Button(onClick = { prepare() }, enabled = !preparing, modifier = Modifier.fillMaxWidth()) {
                    if (preparing) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = DaBlack)
                        Text("  Preparing…")
                    } else Text(if (zipFile == null) "Prepare Backup" else "Prepare Again")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        }

        zipFile?.let { zip ->
            Spacer(Modifier.height(14.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Icon(Icons.Default.FolderZip, null, tint = DaAmber)
                    Spacer(Modifier.height(6.dp))
                    Text(zip.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Size: ${"%,.1f".format(zip.length() / 1024.0 / 1024.0)} MB · ${contents.size} files",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text("Includes:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    contents.distinct().take(8).forEach { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { Share.file(context, zip, "Backup DeryAccount — save to Drive / TeraBox", "application/zip") },
                        modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Icon(Icons.Default.Cloud, null); Text("  Share to Drive / TeraBox / Any App")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { Share.byEmail(context, zip,
                        "DeryAccount Backup ${zip.name}",
                        "Attached: full DeryAccount backup (receipts, reports, books, database).",
                        "application/zip") }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Icon(Icons.Default.Email, null); Text("  Send by Email")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { Share.toWhatsApp(context, zip, "application/zip",
                        "DeryAccount backup ${zip.name}") }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Icon(Icons.Default.Chat, null); Text("  Send on WhatsApp")
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Tip: prepare a backup at the end of every day and keep the latest copy "
            + "in Drive or TeraBox. Your data lives only on this phone — a backup is "
            + "your only protection if the phone is lost.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Zip everything the business owns into DeryAccount/backups/. Returns (zip, labels). */
private fun buildBackup(context: Context): Pair<File, List<String>> {
    val base = DeviceStore.baseDir(context)
    val backupDir = File(base, "backups").apply { mkdirs() }
    val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US).format(java.util.Date())
    val zip = File(backupDir, "deryaccount_backup_$stamp.zip")
    val labels = mutableListOf<String>()

    ZipOutputStream(zip.outputStream().buffered()).use { out ->
        fun add(f: File, inZip: String, label: String) {
            if (!f.exists()) return
            out.putNextEntry(ZipEntry("DeryAccount/$inZip"))
            f.inputStream().use { it.copyTo(out) }
            out.closeEntry()
            labels.add(label)
        }
        // all receipts & documents
        listOf("receipts", "reports", "documents").forEach { dirName ->
            val dir = File(base, dirName)
            if (dir.exists()) dir.listFiles()?.sortedBy { it.name }?.forEach { f ->
                if (f.isFile) add(f, "$dirName/${f.name}", "$dirName/${f.name}")
            }
        }
        // the complete database (products, sales, books, customers…)
        val dbPath = context.getDatabasePath("deryaccount.db")
        if (dbPath.exists()) add(dbPath, "database/deryaccount.db", "database/deryaccount.db (all records)")
        val wal = File(dbPath.parentFile, "deryaccount.db-wal")
        if (wal.exists()) add(wal, "database/deryaccount.db-wal", "database/deryaccount.db-wal")
    }
    return zip to labels
}
