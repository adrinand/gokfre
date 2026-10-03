package com.adrinand.gokfre.ui.tray

import com.adrinand.gokfre.ui.createTrayImage
import dorkbox.systemTray.MenuItem
import dorkbox.systemTray.Separator
import dorkbox.systemTray.SystemTray
import java.awt.GraphicsEnvironment
import java.io.File
import java.nio.file.Files
import java.util.logging.Logger
import javax.swing.SwingUtilities

private val logger = Logger.getLogger("com.adrinand.gokfre.ui.tray.LinuxTrayManager")

private const val APP_NAME = "Gokfre"
private const val SETTINGS_LABEL = "Settings"
private const val QUIT_LABEL = "Quit"
private const val JNA_LIBRARY_PATH = "jna.library.path"

private val SYSTEM_LIB_DIRS =
    listOf(
        "/usr/lib",
        "/usr/lib64",
        "/usr/local/lib",
        "/usr/lib/x86_64-linux-gnu",
        "/lib/x86_64-linux-gnu",
        "/usr/lib/aarch64-linux-gnu",
        "/lib/aarch64-linux-gnu",
    )

private val LEGACY_APPINDICATOR_NAMES =
    listOf(
        "libappindicator3.so.1",
        "libappindicator3.so",
    )

private val AYATANA_APPINDICATOR_NAMES =
    listOf(
        "libayatana-appindicator3.so.1",
        "libayatana-appindicator3.so",
    )

private val SHIM_NAMES =
    listOf(
        "libappindicator3.so",
        "libappindicator3.so.1",
        "libappindicator3-1.so",
        "libappindicator-gtk3.so",
        "libappindicator-gtk3-1.so",
    )

/**
 * Linux tray implementation backed by dorkbox/SystemTray.
 *
 * This uses AppIndicator / StatusNotifierItem on modern desktops, so the icon
 * shows up on GNOME (with the AppIndicator extension or libappindicator-gtk3),
 * KDE Plasma, and other freedesktop-compliant panels.
 *
 * dorkbox 4.4 only probes the legacy libappindicator3 sonames. Modern distros
 * ship libayatana-appindicator3 instead, so we create a private shim directory
 * with symlinks under the legacy names and prepend it to jna.library.path.
 */
class LinuxTrayManager(private val controller: TrayController) {
    private var tray: SystemTray? = null

    fun install() {
        if (GraphicsEnvironment.isHeadless()) {
            logger.info { "Headless environment, skipping system tray" }
            return
        }

        ensureAppIndicatorLibrary()
        SystemTray.PREFER_GTK3 = true
        SystemTray.AUTO_SIZE = true
        configureTrayType()

        val instance = runCatching { SystemTray.get(APP_NAME) }.getOrNull()
        if (instance == null) {
            logger.warning { "System tray is not available on this desktop" }
            return
        }
        tray = instance

        instance.setImage(createTrayImage())
        instance.setTooltip(APP_NAME)

        val menu = instance.menu
        menu.add(MenuItem(SETTINGS_LABEL) { dispatch { controller.onSettings() } })
        menu.add(Separator())
        menu.add(MenuItem(QUIT_LABEL) { dispatch { controller.onQuit() } })
    }

    fun dispose() {
        tray?.shutdown()
        tray = null
    }

    private fun dispatch(action: () -> Unit) {
        SwingUtilities.invokeLater(action)
    }

    private fun configureTrayType() {
        val envType = System.getenv("GOKFRE_TRAY_TYPE")
        if (envType != null) {
            runCatching { SystemTray.TrayType.valueOf(envType) }
                .onSuccess {
                    SystemTray.FORCE_TRAY_TYPE = it
                    logger.info { "Forcing tray type: $it" }
                }
                .onFailure {
                    logger.warning { "Ignoring invalid GOKFRE_TRAY_TYPE value: $envType" }
                }
            return
        }

        val desktop = System.getenv("XDG_CURRENT_DESKTOP")?.lowercase() ?: ""
        if (desktop.contains("gnome")) {
            SystemTray.FORCE_TRAY_TYPE = SystemTray.TrayType.AppIndicator
            logger.info { "Forcing AppIndicator tray on GNOME" }
        }
    }

    private fun ensureAppIndicatorLibrary() {
        if (legacyAppIndicatorExists()) {
            logger.info { "Legacy libappindicator3 found" }
            return
        }

        val ayatana = findAyatanaAppIndicator()
        if (ayatana == null) {
            logger.info { "No libayatana-appindicator3 found, letting dorkbox fall back" }
            return
        }

        val shimDir = createAppIndicatorShim(ayatana)
        if (shimDir == null) {
            logger.warning { "Failed to create appindicator shim" }
            return
        }

        prependJnaLibraryPath(shimDir)
        logger.info { "Using ayatana appindicator shim at ${shimDir.absolutePath}" }
    }

    private fun legacyAppIndicatorExists(): Boolean =
        SYSTEM_LIB_DIRS.any { dir ->
            LEGACY_APPINDICATOR_NAMES.any { File(dir, it).exists() }
        }

    private fun findAyatanaAppIndicator(): File? =
        SYSTEM_LIB_DIRS
            .asSequence()
            .flatMap { dir ->
                AYATANA_APPINDICATOR_NAMES.asSequence().map { File(dir, it) }
            }
            .firstOrNull { it.exists() }

    private fun createAppIndicatorShim(ayatana: File): File? =
        runCatching {
            val shimDir = File(System.getProperty("java.io.tmpdir"), "gokfre-appindicator-shim").apply { mkdirs() }
            val target = ayatana.toPath().toRealPath()
            SHIM_NAMES.forEach { name ->
                val link = File(shimDir, name).toPath()
                Files.deleteIfExists(link)
                Files.createSymbolicLink(link, target)
            }
            shimDir
        }.onFailure {
            logger.warning { "Failed to create appindicator shim: ${it.message}" }
        }.getOrNull()

    private fun prependJnaLibraryPath(dir: File) {
        val existing = System.getProperty(JNA_LIBRARY_PATH)
        System.setProperty(
            JNA_LIBRARY_PATH,
            if (existing.isNullOrBlank()) {
                dir.absolutePath
            } else {
                "${dir.absolutePath}${File.pathSeparator}$existing"
            },
        )
    }
}
