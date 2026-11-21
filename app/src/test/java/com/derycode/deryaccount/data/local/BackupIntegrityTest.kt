package com.derycode.deryaccount.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BackupIntegrityTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun sqliteFile(name: String, filler: String = "payload"): File {
        val f = tmp.newFile(name)
        // 16-byte magic + padding so length is realistic
        f.outputStream().use { out ->
            out.write("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII))
            out.write(filler.toByteArray())
            out.write(ByteArray(700))
        }
        return f
    }

    @Test fun `a real database file is accepted`() {
        assertTrue(BackupIntegrity.looksLikeSqlite(sqliteFile("good.db")))
    }

    @Test fun `a truncated or foreign file is rejected`() {
        val tiny = tmp.newFile("tiny.db").apply { writeText("SQLite") }
        assertFalse(BackupIntegrity.looksLikeSqlite(tiny))
        val text = tmp.newFile("notes.db").apply { writeText("x".repeat(2000)) }
        assertFalse(BackupIntegrity.looksLikeSqlite(text))
    }

    @Test fun `manifest round trips`() {
        val m = BackupIntegrity.Manifest(1, "2026-11-20T09:00:00Z", "a".repeat(64), 4096)
        val parsed = BackupIntegrity.parseManifest(m.encode())
        assertEquals(m, parsed)
    }

    @Test fun `a malformed manifest is refused instead of guessed`() {
        assertNull(BackupIntegrity.parseManifest("garbage"))
        assertNull(BackupIntegrity.parseManifest("version=1\ncreatedAt=x\ndatabaseSha256=short\ndatabaseBytes=1"))
        assertNotNull(BackupIntegrity.parseManifest(BackupIntegrity.Manifest(1, "t", "b".repeat(64), 10).encode()))
    }

    @Test fun `a restore file that does not match its manifest is refused`() {
        val db = sqliteFile("backup.db")
        val manifest = BackupIntegrity.Manifest(1, "now", BackupIntegrity.sha256Hex(db), db.length())
        assertTrue(BackupIntegrity.matchesManifest(db, manifest))

        // same size, different bytes: the case a length check alone would wave through
        val tampered = sqliteFile("tampered.db", filler = "PAYLOAD")
        val sameSize = BackupIntegrity.Manifest(1, "now", BackupIntegrity.sha256Hex(db), tampered.length())
        assertFalse(BackupIntegrity.matchesManifest(tampered, sameSize))
    }

    /** A fake-but-valid SQLite file: correct magic, then filler. */
    private fun fakeDb(bytes: Int = 2048): ByteArray {
        val head = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        val out = ByteArray(bytes)
        head.copyInto(out, 0)
        for (i in head.size until bytes) out[i] = (i % 251).toByte()
        return out
    }

    @Test
    fun `a backup whose bytes match its manifest is accepted`() {
        val db = tmp.newFile("match.db").apply { writeBytes(fakeDb()) }
        val manifest = BackupIntegrity.Manifest(1, "2026-11-21T00:00:00Z",
            BackupIntegrity.sha256Hex(db), db.length())
        val roundTripped = BackupIntegrity.parseManifest(manifest.encode())
        assertNotNull(roundTripped)
        assertTrue(BackupIntegrity.matchesManifest(db, roundTripped!!))
    }

    @Test
    fun `one changed byte in the database is refused`() {
        val db = tmp.newFile("tampered.db").apply { writeBytes(fakeDb()) }
        val manifest = BackupIntegrity.Manifest(1, "2026-11-21T00:00:00Z",
            BackupIntegrity.sha256Hex(db), db.length())
        val bytes = db.readBytes()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1] + 1).toByte()
        db.writeBytes(bytes)
        assertFalse(BackupIntegrity.matchesManifest(db, manifest))
    }

    @Test
    fun `a truncated database is refused`() {
        val db = tmp.newFile("short.db").apply { writeBytes(fakeDb()) }
        val manifest = BackupIntegrity.Manifest(1, "2026-11-21T00:00:00Z",
            BackupIntegrity.sha256Hex(db), db.length())
        db.writeBytes(db.readBytes().copyOf(db.length().toInt() / 2))
        assertFalse(BackupIntegrity.matchesManifest(db, manifest))
    }

    @Test
    fun `a manifest missing its checksum is treated as unreadable`() {
        assertNull(BackupIntegrity.parseManifest("version=1\ncreatedAt=x\ndatabaseBytes=10\n"))
        assertNull(BackupIntegrity.parseManifest("version=1\ncreatedAt=x\ndatabaseSha256=abc\ndatabaseBytes=10\n"))
    }
}
