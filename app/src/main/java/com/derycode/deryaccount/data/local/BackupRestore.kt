package com.derycode.deryaccount.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.util.zip.ZipFile

/**
 * BackupRestore — turns a backup ZIP back into a working shop.
 *
 * Audit finding 10: a shop could create a backup but had no way to restore one
 * from inside the app; the only restore path needed the file staged by hand.
 * This verifies the zip, extracts the database, checks it with SQLite itself,
 * and only then stages it. Nothing touches the live database here: the swap
 * happens at next startup in AppDatabase.applyPendingRestore, which keeps its
 * own pre-restore safety copy.
 */
object BackupRestore {

    const val DB_ENTRY = "DeryAccount/database/deryaccount.db"
    const val MANIFEST_ENTRY = "DeryAccount/manifest.txt"
    const val PENDING_NAME = "restore_pending.db"

    sealed class Result {
        data class Ready(val databaseBytes: Long, val from: String) : Result()
        data class Refused(val reason: String) : Result()
    }

    /** Extracts the database from [zip] and proves it is usable. */
    fun stageFromZip(context: Context, zip: File): Result {
        if (!zip.isFile || zip.length() < 512) return Result.Refused("That file is too small to be a backup.")

        val temp = File(context.cacheDir, "restore_extract_${System.currentTimeMillis()}.db")
        try {
            ZipFile(zip).use { zf ->
                val entry = zf.getEntry(DB_ENTRY) ?: zf.entries().asSequence()
                    .firstOrNull { it.name.endsWith("deryaccount.db") }
                    ?: return Result.Refused("This zip has no DeryAccount database inside it.")

                entry.let { e ->
                    zf.getInputStream(e).use { input ->
                        temp.outputStream().buffered().use { out -> input.copyTo(out) }
                    }
                }

                if (!BackupIntegrity.looksLikeSqlite(temp)) {
                    return Result.Refused("The database inside this backup is damaged, so nothing was changed.")
                }

                // If the backup carries a manifest, the bytes must match it exactly.
                val manifestEntry = zf.getEntry(MANIFEST_ENTRY)
                if (manifestEntry != null) {
                    val text = zf.getInputStream(manifestEntry).use { it.readBytes().toString(Charsets.UTF_8) }
                    val manifest = BackupIntegrity.parseManifest(text)
                    if (manifest != null && manifest.databaseSha256.isNotBlank()) {
                        val actual = BackupIntegrity.sha256Hex(temp)
                        if (!actual.equals(manifest.databaseSha256, ignoreCase = true)) {
                            return Result.Refused("This backup is incomplete (checksum does not match). Nothing was changed.")
                        }
                    }
                }
            }

            // SQLite's own opinion matters more than ours.
            if (!integrityOk(temp)) {
                return Result.Refused("SQLite could not read this database cleanly, so nothing was changed.")
            }

            val dbDir = context.getDatabasePath("deryaccount.db").parentFile
                ?: return Result.Refused("This device has no database folder.")
            val pending = File(dbDir, PENDING_NAME)
            temp.copyTo(pending, overwrite = true)
            return Result.Ready(pending.length(), zip.name)
        } catch (e: Exception) {
            return Result.Refused("Could not read that backup: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            runCatching { temp.delete() }
        }
    }

    /** Read-only open + PRAGMA integrity_check, so a corrupt file is refused. */
    fun integrityOk(dbFile: File): Boolean = try {
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("PRAGMA integrity_check", null).use { c ->
                c.moveToFirst() && c.getString(0).equals("ok", ignoreCase = true)
            }
        }
    } catch (_: Exception) {
        false
    }

    fun hasPendingRestore(context: Context): Boolean {
        val dir = context.getDatabasePath("deryaccount.db").parentFile ?: return false
        return File(dir, PENDING_NAME).isFile
    }
}
