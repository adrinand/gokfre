package com.adrinand.gokfre.core.screen

import com.adrinand.gokfre.core.hotkey.InputDevicePermissionChecker
import kotlinx.coroutines.delay
import java.awt.GraphicsEnvironment
import kotlin.time.Duration.Companion.milliseconds

private const val WAYLAND_SESSION = "wayland"
private const val GNOME_DESKTOP = "gnome"
private const val KDE_DESKTOP = "kde"
private const val PLASMA_DESKTOP = "plasma"
private const val GNOME_EXTENSION_SERVICE = "org.gnome.Shell.Extensions.Gokfre"
private const val GNOME_EXTENSION_OBJECT_PATH = "/org/gnome/Shell/Extensions/Gokfre"
private const val GNOME_SHELL_EXTENSIONS_SERVICE = "org.gnome.Shell"
private const val GNOME_SHELL_EXTENSIONS_OBJECT_PATH = "/org/gnome/Shell"
private const val GNOME_SHELL_EXTENSIONS_INTERFACE = "org.gnome.Shell.Extensions"
private const val GNOME_EXTENSION_ENABLED_STATE = 1
private val GNOME_APPINDICATOR_UUIDS =
    listOf(
        "ubuntu-appindicators@ubuntu.com",
        "appindicatorsupport@rgcjonas.gmail.com",
    )
private val EXTENSION_STATE_REGEX = Regex("""'state':\s*<(?:\w+\s+)?(\d+)(?:\.\d+)?>""")
private const val RECHECK_DELAY_MS = 1_500L

enum class LinuxRequirement {
    INPUT_DEVICE_ACCESS,
    HOTKEY_LISTENER,
    GNOME_SHELL_EXTENSION,
    GNOME_APPINDICATOR_EXTENSION,
    DBUS_CLIENT,
    UNSUPPORTED_WAYLAND_COMPOSITOR,
}

internal data class LinuxSession(
    val osName: String?,
    val sessionType: String,
    val desktop: String,
) {
    val isLinux: Boolean get() = osName?.lowercase()?.contains("linux") == true
    val isWayland: Boolean get() = sessionType.lowercase() == WAYLAND_SESSION
    val isGnome: Boolean get() = desktop.lowercase().contains(GNOME_DESKTOP)
    val isKde: Boolean
        get() = desktop.lowercase().let { it.contains(KDE_DESKTOP) || it.contains(PLASMA_DESKTOP) }
    val isSupportedDesktop: Boolean get() = isGnome || isKde
    val isUnsupportedWayland: Boolean get() = isWayland && !isSupportedDesktop
}

internal data class LinuxProbe(
    val hasInputAccess: Boolean,
    val hasGdbus: Boolean,
    val hasGnomeExtension: Boolean,
    val hasAppIndicator: Boolean,
)

/** Detects missing Linux requirements. Spawns `id` and `gdbus`, so call off the UI thread. */
object LinuxRequirementChecker {
    fun missingRequirements(): List<LinuxRequirement> {
        if (GraphicsEnvironment.isHeadless()) {
            return emptyList()
        }
        val session = currentSession()
        if (!session.isLinux) {
            return emptyList()
        }

        val probe = detectProbe(session)
        val requirements = mutableListOf<LinuxRequirement>()

        if (!probe.hasInputAccess) {
            requirements += LinuxRequirement.INPUT_DEVICE_ACCESS
        }
        if (session.isUnsupportedWayland) {
            requirements += LinuxRequirement.UNSUPPORTED_WAYLAND_COMPOSITOR
        }
        if (session.isGnome) {
            collectGnomeRequirements(session, probe, requirements)
        }
        return requirements
    }

    internal suspend fun checkMissingRequirementsWithRetry(delayMs: Long = RECHECK_DELAY_MS): List<LinuxRequirement> {
        val first = missingRequirements()
        if (first.isEmpty()) {
            return emptyList()
        }
        delay(delayMs.milliseconds)
        return missingRequirements()
    }

    internal fun displayedRequirements(
        detected: List<LinuxRequirement>,
        hotkeyListenerFailed: Boolean,
    ): List<LinuxRequirement> =
        if (hotkeyListenerFailed && LinuxRequirement.INPUT_DEVICE_ACCESS !in detected) {
            detected + LinuxRequirement.HOTKEY_LISTENER
        } else {
            detected
        }

    private fun collectGnomeRequirements(
        session: LinuxSession,
        probe: LinuxProbe,
        requirements: MutableList<LinuxRequirement>,
    ) {
        if (!probe.hasGdbus) {
            requirements += LinuxRequirement.DBUS_CLIENT
            return
        }
        if (session.isWayland && !probe.hasGnomeExtension) {
            requirements += LinuxRequirement.GNOME_SHELL_EXTENSION
        }
        if (!probe.hasAppIndicator) {
            requirements += LinuxRequirement.GNOME_APPINDICATOR_EXTENSION
        }
    }

    private fun currentSession(): LinuxSession {
        val sessionType =
            System.getenv("XDG_SESSION_TYPE")?.lowercase()?.takeIf { it.isNotEmpty() }
                ?: if (System.getenv("WAYLAND_DISPLAY").isNullOrEmpty()) "x11" else WAYLAND_SESSION
        val desktop =
            System.getenv("XDG_CURRENT_DESKTOP")?.takeIf { it.isNotEmpty() }
                ?: System.getenv("DESKTOP_SESSION").orEmpty()
        return LinuxSession(
            osName = System.getProperty("os.name"),
            sessionType = sessionType,
            desktop = desktop,
        )
    }

    private fun detectProbe(session: LinuxSession): LinuxProbe {
        val gdbus = Dbus.findGdbus()
        return LinuxProbe(
            hasInputAccess = runCatching { InputDevicePermissionChecker.hasInputDeviceAccess() }.getOrDefault(false),
            hasGdbus = gdbus != null,
            hasGnomeExtension = gdbus != null && session.isGnome && isGnomeExtensionRunning(gdbus),
            hasAppIndicator = gdbus != null && session.isGnome && isAppIndicatorEnabled(gdbus),
        )
    }

    private fun isGnomeExtensionRunning(gdbus: String): Boolean =
        Dbus.runClient(
            gdbus,
            listOf(
                "introspect",
                "--session",
                "--dest",
                GNOME_EXTENSION_SERVICE,
                "--object-path",
                GNOME_EXTENSION_OBJECT_PATH,
            ),
        ) != null

    private fun isAppIndicatorEnabled(gdbus: String): Boolean =
        GNOME_APPINDICATOR_UUIDS.any { uuid -> extensionState(gdbus, uuid) == GNOME_EXTENSION_ENABLED_STATE }

    private fun extensionState(
        gdbus: String,
        uuid: String,
    ): Int? {
        val output =
            Dbus.runClient(
                gdbus,
                listOf(
                    "call",
                    "--session",
                    "--dest",
                    GNOME_SHELL_EXTENSIONS_SERVICE,
                    "--object-path",
                    GNOME_SHELL_EXTENSIONS_OBJECT_PATH,
                    "--method",
                    "$GNOME_SHELL_EXTENSIONS_INTERFACE.GetExtensionInfo",
                    uuid,
                ),
            ) ?: return null
        return parseExtensionState(output)
    }
}

internal fun parseExtensionState(output: String): Int? {
    val state = EXTENSION_STATE_REGEX.find(output)?.groupValues?.get(1) ?: return null
    return state.toIntOrNull()
}
