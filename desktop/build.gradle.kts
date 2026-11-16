import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJvmToolOperationTask

// ── Packaging JDK pin ─────────────────────────────────────────────────────────
// jpackage ships the app image inside the MSI. Windows CI used to cache a Temurin
// re-spin whose jpackage produced ballooning installers, so the packager runs on
// the Amazon Corretto 17 build. The foojay resolver fetches it automatically.
val packagingJdkLauncher = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(17))
    vendor.set(JvmVendorSpec.AMAZON)
}
tasks.withType<AbstractJvmToolOperationTask>().configureEach {
    javaHome.set(packagingJdkLauncher.map { it.metadata.installationPath.toString() })
}
// ──────────────────────────────────────────────────────────────────────────────

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.compose") version "1.6.11"
}

kotlin { jvmToolchain(17) }

val desktopVersion = "1.1.0"

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
    implementation("org.apache.pdfbox:pdfbox:2.0.31")
    testImplementation(kotlin("test"))
}

// `-PsoftwareRender` forces Skia's CPU backend (headless CI, VMs without GL).
tasks.matching { it.name == "run" }.configureEach {
    if (project.hasProperty("softwareRender")) {
        (this as JavaExec).systemProperty("skiko.renderApi", "SOFTWARE")
    }
}

// Off-screen screenshots of the real screens: gradle shots -PshotsDir=/tmp/shots
tasks.register<JavaExec>("shots") {
    mainClass.set("com.derycode.deryaccount.desktop.tools.ShotsKt")
    classpath = sourceSets["main"].runtimeClasspath
    systemProperty("skiko.renderApi", "SOFTWARE")
    systemProperty("java.awt.headless", "true")
    args = listOf((project.findProperty("shotsDir") as String?) ?: "/tmp/deryaccount-shots")
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed", "skipped") }
}

compose.desktop {
    application {
        mainClass = "com.derycode.deryaccount.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "DeryAccount"
            packageVersion = desktopVersion
            description = "DeryAccount — offline books of account, stock and POS for Ugandan businesses"
            vendor = "DeryCode"
            copyright = "© 2026 DeryCode Technologies"
            windows {
                // The exact DeryCode mark in the Start menu, taskbar and installer.
                iconFile.set(project.file("src/main/resources/brand/icon.ico"))
                menuGroup = "DeryCode"
                // A stable UUID means each new installer upgrades the installed copy
                // instead of sitting beside it.
                upgradeUuid = "b6c1f0d4-7a2e-4c58-9f31-2d84a5e7c913"
                shortcut = true
                dirChooser = true
                perUserInstall = true
            }
        }
    }
}
