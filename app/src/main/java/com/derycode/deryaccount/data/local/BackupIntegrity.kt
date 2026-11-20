package com.derycode.deryaccount.data.local

import java.io.File
import java.security.MessageDigest

/**
 * BackupIntegrity — the checks that stand between a shop and an empty database.
 *
 * Audit finding 10: restore used to delete the live database and then rename a
 * staged file over it, with only a "bigger than 1 KB" test on the staged file.
 * A truncated download, a half-copied WhatsApp file or a zip that never
 * extracted would therefore wipe the books with no way back.
 *
 * Everything here is pure Kotlin so it can be unit tested on the JVM.
 */
object BackupIntegrity {

    /** Every real SQLite database starts with this 16-byte string. */
    private val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    /** A file we are willing to treat as a database at all. */
    fun looksLikeSqlite(file: File): Boolean {
        if (!file.isFile || file.length() < 512) return false
        return try {
            file.inputStream().use { input ->
                val head = ByteArray(SQLITE_MAGIC.size)
                var read = 0
                while (read < head.size) {
                    val n = input.read(head, read, head.size - read)
                    if (n <= 0) return false
                    read += n
                }
                head.contentEquals(SQLITE_MAGIC)
            }
        } catch (_: Exception) {
            false
        }
    }

    fun sha256Hex(file: File): String =
        file.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                digest.update(buffer, 0, n)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }

    /** One line in the backup manifest, kept deliberately simple to parse. */
    data class Manifest(
        val version: Int,
        val createdAt: String,
        val databaseSha256: String,
        val databaseBytes: Long
    ) {
        fun encode(): String =
            "version=$version\ncreatedAt=$createdAt\ndatabaseSha256=$databaseSha256\ndatabaseBytes=$databaseBytes\n"
    }

    fun parseManifest(text: String): Manifest? {
        val map = text.lineSequence()
            .mapNotNull { line ->
                val i = line.indexOf('=')
                if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
            }
            .toMap()
        val version = map["version"]?.toIntOrNull() ?: return null
        val created = map["createdAt"] ?: return null
        val sha = map["databaseSha256"] ?: return null
        val bytes = map["databaseBytes"]?.toLongOrNull() ?: return null
        if (sha.length != 64) return null
        return Manifest(version, created, sha, bytes)
    }

    /**
     * True when the staged database matches the manifest that travelled with it.
     * Callers must treat a false result as "do not touch the live database".
     */
    fun matchesManifest(file: File, manifest: Manifest): Boolean {
        if (!looksLikeSqlite(file)) return false
        if (file.length() != manifest.databaseBytes) return false
        return sha256Hex(file) == manifest.databaseSha256
    }
}
