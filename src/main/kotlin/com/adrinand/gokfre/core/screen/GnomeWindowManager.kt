package com.adrinand.gokfre.core.screen

import com.adrinand.gokfre.core.screen.Dbus.findGdbus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.awt.Rectangle
import java.awt.Window
import java.util.logging.Logger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import com.adrinand.gokfre.core.screen.Dbus.runClient as runDbusClient

private const val BACKEND_NAME = "gnome-shell"
private const val WAYLAND_TOKEN = "captured"
private const val SERVICE_NAME = "org.gnome.Shell.Extensions.Gokfre"
private const val OBJECT_PATH = "/org/gnome/Shell/Extensions/Gokfre"
private const val INTERFACE_NAME = "org.gnome.Shell.Extensions.Gokfre"
private const val POLL_INTERVAL_MS = 50L
private const val MAXIMIZE_SETTLE_MS = 50L
private const val CALL_TIMEOUT_MS = 300L
private val LEADING_BOOLEAN_REGEX = Regex("""\(\s*(true|false)""")

/**
 * [WindowManager] implementation for native Wayland windows on GNOME.
 *
 * This backend requires the companion GNOME Shell extension
 * (`gokfre@adrinand`) to be installed and enabled. The extension stores a
 * reference to the captured active window and can move/resize it on demand.
 */
class GnomeWindowManager : SingleBackendWindowManager<WindowHandle.Wayland>() {
    override val logger: Logger = Logger.getLogger("com.adrinand.gokfre.core.screen.GnomeWindowManager")

    /**
     * Returns `true` if the companion GNOME Shell extension is available on
     * the session bus and a D-Bus client is installed.
     */
    fun isAvailable(): Boolean {
        val client = findGdbus() ?: return false
        val output =
            runDbusClient(
                client,
                listOf("introspect", "--session", "--dest", SERVICE_NAME, "--object-path", OBJECT_PATH),
            )
        return output != null
    }

    override fun castHandle(window: WindowHandle): WindowHandle.Wayland? =
        (window as? WindowHandle.Wayland)?.takeIf { it.backend == BACKEND_NAME }

    override fun retrieveActiveWindow(): WindowHandle.Wayland? {
        val output = callMethod("CaptureActiveWindow") ?: return null
        val success = parseLeadingBoolean(output.trim()) ?: return null
        return if (success) WindowHandle.Wayland(BACKEND_NAME, WAYLAND_TOKEN) else null
    }

    override fun applyBounds(
        handle: WindowHandle.Wayland,
        bounds: Rectangle,
    ) {
        callMethod(
            "MoveResizeCapturedWindow",
            listOf(
                bounds.x.toString(),
                bounds.y.toString(),
                bounds.width.toString(),
                bounds.height.toString(),
            ),
        )
    }

    override fun focusHandle(handle: WindowHandle.Wayland) {
        callMethod("FocusCapturedWindow")
    }

    override suspend fun enterFullscreen(
        window: Window,
        timeoutMs: Long,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val output = callMethod("MaximizePreview", timeoutMs = CALL_TIMEOUT_MS) ?: return@withContext false
            if (parseLeadingBoolean(output.trim()) != true) {
                logger.warning { "GNOME extension did not maximize the preview" }
                return@withContext false
            }
            waitForMaximized(timeoutMs)
        }

    private suspend fun waitForMaximized(timeoutMs: Long): Boolean {
        delay(MAXIMIZE_SETTLE_MS.milliseconds)
        val deadline = TimeSource.Monotonic.markNow() + timeoutMs.milliseconds
        while (deadline.hasNotPassedNow()) {
            val output = callMethod("IsPreviewMaximized", timeoutMs = CALL_TIMEOUT_MS) ?: return false
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
        val client = findGdbus() ?: return null
        val command =
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
        return runDbusClient(client, command, timeoutMs)
    }

    private fun parseLeadingBoolean(output: String): Boolean? {
        val match = LEADING_BOOLEAN_REGEX.find(output) ?: return null
        return match.groupValues[1].toBooleanStrictOrNull()
    }
}
