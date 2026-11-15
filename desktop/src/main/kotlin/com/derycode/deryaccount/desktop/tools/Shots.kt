package com.derycode.deryaccount.desktop.tools

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.derycode.deryaccount.desktop.AppShell
import com.derycode.deryaccount.desktop.data.AdminOps
import com.derycode.deryaccount.desktop.data.Repo
import com.derycode.deryaccount.desktop.data.Reports
import com.derycode.deryaccount.desktop.ui.AppState
import com.derycode.deryaccount.desktop.ui.DeryTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/**
 * Renders the real screens off-screen to PNG files. Used for the screenshots in
 * the repo README and to eyeball a build without opening a window.
 * Run with: gradle shots -PshotsDir=/tmp/shots
 */
fun main(args: Array<String>) {
    val outDir = File(args.firstOrNull() ?: "/tmp/deryaccount-shots")
    outDir.mkdirs()

    val dbFile = File(outDir, "shots.db")
    dbFile.delete()
    val repo = Repo.open(dbFile)
    AdminOps(repo).loadSampleData()
    val owner = repo.users().firstOrNull { it.str("username") == "owner" } ?: repo.users().first()
    val app = AppState(repo, Reports(repo), AdminOps(repo))
    app.user = owner

    val screens = listOf(
        "dashboard", "frontdesk", "rooms", "bookings", "guests", "hotelreports",
        "pos", "sales", "stock", "purchases", "customers",
        "books", "reports", "admin", "data"
    )

    screens.forEach { screen ->
        app.screen = screen
        val scene = ImageComposeScene(width = 1440, height = 900, density = Density(1f)) {
            DeryTheme { AppShell(app) }
        }
        // Two frames: the first mounts, the second lets LaunchedEffect data land.
        scene.render(0)
        val image = scene.render(400_000_000)
        val data = image.encodeToData(EncodedImageFormat.PNG) ?: error("encode failed for $screen")
        File(outDir, "$screen.png").writeBytes(data.bytes)
        println("rendered $screen -> ${outDir.resolve("$screen.png").path}")
        scene.close()
    }
    repo.db.conn.close()
    println("done: ${screens.size} screens")
}
