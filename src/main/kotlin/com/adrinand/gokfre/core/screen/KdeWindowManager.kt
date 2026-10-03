package com.adrinand.gokfre.core.screen

import com.adrinand.gokfre.core.screen.Dbus.findQdbusOrGdbus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.awt.Rectangle
import java.awt.Window
import java.util.logging.Logger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import com.adrinand.gokfre.core.screen.Dbus.runClient as runDbusClient

private const val BACKEND_NAME = "kwin"
private const val WAYLAND_TOKEN = "active"
private const val SERVICE_NAME = "org.kde.KWin.Script.Gokfre"
private const val OBJECT_PATH = "/Gokfre"
private const val INTERFACE_NAME = "org.kde.KWin.Script.Gokfre"
private const val POLL_INTERVAL_MS = 50L
private const val MAXIMIZE_SETTLE_MS = 50L
private const val CALL_TIMEOUT_MS = 300L
private val ACTIVE_WINDOW_REGEX =
    Regex(
        "\\(\\s*(true|false)\\s*,\\s*'([^']*)'\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)",
    )
private val LEADING_BOOLEAN_REGEX = Regex("""\(\s*(true|false)""")

/**
 * [WindowManager] implementation for native Wayland windows on KDE Plasma.
 *
 * This backend requires the companion KWin script (`gokfre.kwin`) to be
 * installed and enabled. The script exposes a D-Bus service that can read the
 * active window and move/resize it.
 */
class KdeWindowManager : SingleBackendWindowManager<WindowHandle.Wayland>() {
    override val logger: Logger = Logger.getLogger("com.adrinand.gokfre.core.screen.KdeWindowManager")

    /**
     * Returns `true` if the companion KWin script is available on the session
     * bus and a D-Bus client is installed.
     */
    fun isAvailable(): Boolean {
        val client = findQdbusOrGdbus() ?: return false
        val args = buildKdeIntrospectArgs(client)
        val output = runDbusClient(client, args)
        return output != null
    }

    override fun castHandle(window: WindowHandle): WindowHandle.Wayland? =
        (window as? WindowHandle.Wayland)?.takeIf { it.backend == BACKEND_NAME }

    override fun retrieveActiveWindow(): WindowHandle.Wayland? {
        val output = callMethod("getActiveWindow") ?: return null
        val hasWindow = parseActiveWindowFlag(output.trim()) ?: return null
        return if (hasWindow) WindowHandle.Wayland(BACKEND_NAME, WAYLAND_TOKEN) else null
    }

    override fun applyBounds(
        handle: WindowHandle.Wayland,
        bounds: Rectangle,
    ) {
        callMethod(
            "moveResizeActiveWindow",
            listOf(
                bounds.x.toString(),
                bounds.y.toString(),
                bounds.width.toString(),
                bounds.height.toString(),
            ),
        )
    }

    override fun focusHandle(handle: WindowHandle.Wayland) {
        callMethod("focusActiveWindow")
    }

    override suspend fun enterFullscreen(
        window: Window,
        timeoutMs: Long,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val output = callMethod("maximizePreview", timeoutMs = CALL_TIMEOUT_MS) ?: return@withContext false
            if (parseLeadingBoolean(output.trim()) != true) {
                logger.warning { "KDE script did not maximize the preview" }
                return@withContext false
            }
            waitForMaximized(timeoutMs)
        }

    private suspend fun waitForMaximized(timeoutMs: Long): Boolean {
        delay(MAXIMIZE_SETTLE_MS.milliseconds)
        val deadline = TimeSource.Monotonic.markNow() + timeoutMs.milliseconds
        while (deadline.hasNotPassedNow()) {
            val output = callMethod("isPreviewMaximized", timeoutMs = CALL_TIMEOUT_MS) ?: return false
            if (parseLeadingBoolean(output.trim()) == true) {
                return true
            }
            delay(POLL_INTERVAL_MS.milliseconds)
        }
        return false
    }

    private fun callMethod(
        method: String,
        args: List<String> = emptyList(),
        timeoutMs: Long = 2_000L,
    ): String? {
        val client = findQdbusOrGdbus() ?: return null
        val command = buildKdeMethodArgs(client, method, args)
        return runDbusClient(client, command, timeoutMs)
    }

    private fun parseActiveWindowFlag(output: String): Boolean? {
        val match = ACTIVE_WINDOW_REGEX.matchEntire(output) ?: return null
        return match.groupValues[1].toBooleanStrictOrNull()
    }

    private fun parseLeadingBoolean(output: String): Boolean? {
        val match = LEADING_BOOLEAN_REGEX.find(output) ?: return null
        return match.groupValues[1].toBooleanStrictOrNull()
    }
}

private fun buildKdeIntrospectArgs(client: String): List<String> {
    return if (client.endsWith("qdbus") || client.endsWith("/qdbus")) {
        listOf(SERVICE_NAME, OBJECT_PATH)
    } else {
        listOf("introspect", "--session", "--dest", SERVICE_NAME, "--object-path", OBJECT_PATH)
    }
}

private fun buildKdeMethodArgs(
    client: String,
    method: String,
    args: List<String>,
): List<String> {
    return if (client.endsWith("qdbus") || client.endsWith("/qdbus")) {
        listOf(SERVICE_NAME, OBJECT_PATH, "$INTERFACE_NAME.$method") + args
    } else {
        listOf(
            "call",
            "--session",
            "--dest",
            SERVICE_NAME,
            "--object-path",
            OBJECT_PATH,
            "--method",
            "$INTERFACE_NAME.$method",
        ) + args
    }
}
