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
    var restoreMsg by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<java.io.File?>(null) }
    val pickBackup = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val staged = withContext(Dispatchers.IO) {
                runCatching {
                    val tmp = java.io.File(context.cacheDir, "picked_backup.zip")
                    context.contentResolver.openInputStream(uri)!!.use { input ->
                        tmp.outputStream().buffered().use { out -> input.copyTo(out) }
                    }
                    tmp
                }.getOrNull()
            }
            if (staged == null) {
                restoreMsg = "Could not open that file."
            } else {
                pendingRestore = staged
                restoreMsg = null
            }
        }
    }
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

        // Restoring is the one action that can replace the shop's books, so it is
        // verified first, staged second, and only swapped in when the app restarts.
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Icon(Icons.Default.Restore, null, tint = DaGreen, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(8.dp))
                Text("Restore from a backup", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Pick a DeryAccount backup ZIP from this phone, Drive, or a chat. "
                    + "It is checked before anything is replaced.", fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { pickBackup.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                    modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Default.FolderOpen, null); Text("  Choose backup file")
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Tip: prepare a backup at the end of every day and keep the latest copy "
            + "in Drive or TeraBox. Your data lives only on this phone — a backup is "
            + "your only protection if the phone is lost.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    pendingRestore?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore this backup?") },
            text = { Text("Your current records are kept as a safety copy first. "
                + "After restoring, close and reopen DeryAccount to load the backup.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            com.derycode.deryaccount.data.local.BackupRestore.stageFromZip(context, file)
                        }
                        restoreMsg = when (result) {
                            is com.derycode.deryaccount.data.local.BackupRestore.Result.Ready ->
                                "Backup checked and ready. Close and reopen DeryAccount now to finish restoring."
                            is com.derycode.deryaccount.data.local.BackupRestore.Result.Refused -> result.reason
                        }
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } }
        )
    }

    restoreMsg?.let { msg ->
        AlertDialog(
            onDismissRequest = { restoreMsg = null },
            title = { Text("Restore") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { restoreMsg = null }) { Text("OK") } }
        )
    }
}

/** Zip everything the business owns into DeryAccount/backups/. Returns (zip, labels). */
private fun buildBackup(context: Context): Pair<File, List<String>> {
    val base = DeviceStore.baseDir(context)
    val backupDir = File(base, "backups").apply { mkdirs() }
    val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US).format(java.util.Date())
    val zip = File(backupDir, "deryaccount_backup_$stamp.zip")
    // Write to a .part file and rename only once the zip is complete, so an
    // interrupted backup can never leave a truncated file that looks real.
    val part = File(backupDir, "deryaccount_backup_$stamp.zip.part")
    val labels = mutableListOf<String>()

    // Fold the write-ahead log back into the main file first. Without this the
    // zip can miss the day's last sales, which is exactly what a shop needs back.
    runCatching {
        val live = context.getDatabasePath("deryaccount.db")
        if (live.exists()) {
            android.database.sqlite.SQLiteDatabase.openDatabase(
                live.absolutePath, null,
                android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
            ).use { db -> db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() } }
        }
    }

    ZipOutputStream(part.outputStream().buffered()).use { out ->
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

        // Manifest last: a restore can then prove it has the same database it
        // was told about, instead of trusting the file size.
        if (dbPath.exists()) {
            val manifest = com.derycode.deryaccount.data.local.BackupIntegrity.Manifest(
                version = 1,
                createdAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                    .format(java.util.Date()),
                databaseSha256 = com.derycode.deryaccount.data.local.BackupIntegrity.sha256Hex(dbPath),
                databaseBytes = dbPath.length()
            )
            out.putNextEntry(ZipEntry("DeryAccount/manifest.txt"))
            out.write(manifest.encode().toByteArray())
            out.closeEntry()
            labels.add("manifest.txt (checksum of the database)")
        }
    }
    // Only now does the backup get its real name.
    if (zip.exists()) zip.delete()
    if (!part.renameTo(zip)) { part.copyTo(zip, overwrite = true); part.delete() }
    return zip to labels
}
