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
}
