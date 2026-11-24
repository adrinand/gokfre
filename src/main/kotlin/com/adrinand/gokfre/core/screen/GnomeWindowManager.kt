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
private const val FOCUS_CALL_TIMEOUT_MS = 1_000L
private const val FOCUS_RETRY_TIMEOUT_MS = 500L
private const val BACKGROUND_CAPTURE_INTERVAL_MS = 500L
private const val PLACE_PREVIEW_ATTEMPTS = 5
private const val BOUNDS_VALUE_COUNT = 4
private const val X_INDEX = 0
private const val Y_INDEX = 1
private const val WIDTH_INDEX = 2
private const val HEIGHT_INDEX = 3
private val LEADING_BOOLEAN_REGEX = Regex("""\(\s*(true|false)""")
private val CAPTURED_BOUNDS_REGEX =
    Regex(
        """\(\s*true\s*,.*?,\s*(-?\d+)\s*,\s*(-?\d+)\s*,""" +
            """\s*(-?\d+)\s*,\s*(-?\d+)\s*\)\s*$""",
        RegexOption.DOT_MATCHES_ALL,
    )
private val CAPTURED_WORK_AREA_REGEX =
    Regex(
        """\(\s*true\s*,\s*(-?\d+)\s*,\s*(-?\d+)\s*,""" +
            """\s*(-?\d+)\s*,\s*(-?\d+)\s*\)\s*$""",
    )

internal fun parseCapturedWorkArea(output: String): Rectangle? {
    val values =
        CAPTURED_WORK_AREA_REGEX.find(output.trim())
            ?.groupValues
            ?.drop(1)
            ?.mapNotNull { it.toIntOrNull() }
            ?: return null
    if (values.size != BOUNDS_VALUE_COUNT) {
        return null
    }
    return values
        .takeIf { it[WIDTH_INDEX] > 0 }
        ?.takeIf { it[HEIGHT_INDEX] > 0 }
        ?.let { Rectangle(it[X_INDEX], it[Y_INDEX], it[WIDTH_INDEX], it[HEIGHT_INDEX]) }
}

/**
 * [WindowManager] implementation for native Wayland windows on GNOME.
 *
 * This backend requires the companion GNOME Shell extension
 * (`gokfre@adrinand`) to be installed and enabled. The extension stores a
 * reference to the captured active window and can move/resize it on demand.
 */
@Suppress("TooManyFunctions")
class GnomeWindowManager : SingleBackendWindowManager<WindowHandle.Wayland>() {
    override val logger: Logger = Logger.getLogger("com.adrinand.gokfre.core.screen.GnomeWindowManager")

    override val backgroundCaptureIntervalMs: Long = BACKGROUND_CAPTURE_INTERVAL_MS

    @Volatile
    private var capturedWorkArea: Rectangle? = null

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
        val output = callMethod("CaptureActiveWindow")
        val success = output?.trim()?.let(::parseLeadingBoolean) == true
        capturedWorkArea = if (success) output.let(::parseCapturedWorkArea) else null
        return if (success) WindowHandle.Wayland(BACKEND_NAME, WAYLAND_TOKEN) else null
    }

    @Suppress("ReturnCount")
    override fun retrieveBounds(handle: WindowHandle.Wayland): Rectangle? {
        val output = callMethod("GetCapturedWindow") ?: return null
        val match = CAPTURED_BOUNDS_REGEX.find(output.trim()) ?: return null
        val values = match.groupValues.drop(1).mapNotNull { it.toIntOrNull() }
        if (values.size != BOUNDS_VALUE_COUNT) {
            return null
        }
        return Rectangle(values[X_INDEX], values[Y_INDEX], values[WIDTH_INDEX], values[HEIGHT_INDEX])
    }

    /**
     * Returns the work area in the extension's logical coordinate space.
     */
    override fun getWorkArea(
        window: WindowHandle,
        bounds: Rectangle?,
    ): Rectangle? = castHandle(window)?.let { capturedWorkArea } ?: super.getWorkArea(window, bounds)

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
            waitForMaximized(timeoutMs, expected = true)
        }

    override suspend fun exitFullscreen(
        window: Window,
        timeoutMs: Long,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val output = callMethod("RestorePreview", timeoutMs = CALL_TIMEOUT_MS)
            val restoredByExtension =
                output != null &&
                    parseLeadingBoolean(output.trim()) == true &&
                    waitForMaximized(timeoutMs, expected = false)
            restoredByExtension || X11Fullscreen.unsetFullscreen(window, timeoutMs)
        }

    override suspend fun placePreview(
        window: Window,
        bounds: Rectangle,
        timeoutMs: Long,
    ): Boolean {
        if (capturedWorkArea == null) {
            return super.placePreview(window, bounds, timeoutMs)
        }
        val placed =
            withContext(Dispatchers.IO) {
                repeat(PLACE_PREVIEW_ATTEMPTS) { attempt ->
                    val output =
                        callMethod(
                            "PlacePreview",
                            listOf(
                                bounds.x.toString(),
                                bounds.y.toString(),
                                bounds.width.toString(),
                                bounds.height.toString(),
                            ),
                            timeoutMs = CALL_TIMEOUT_MS,
                        )
                    if (output != null && parseLeadingBoolean(output.trim()) == true) {
                        return@withContext true
                    }
                    if (attempt < PLACE_PREVIEW_ATTEMPTS - 1) {
                        delay(POLL_INTERVAL_MS.milliseconds)
                    }
                }
                false
            }
        if (!placed) {
            // [bounds] is logical while AWT is in XWayland pixels; writing it
            // client-side would move the preview to the wrong place.
            logger.warning { "GNOME extension could not place the preview; keeping its current bounds" }
        }
        return placed
    }

    override suspend fun focusPreview(window: Window) {
        val focused =
            withContext(Dispatchers.IO) {
                val deadline = TimeSource.Monotonic.markNow() + FOCUS_RETRY_TIMEOUT_MS.milliseconds
                while (deadline.hasNotPassedNow()) {
                    val output = callMethod("FocusPreview", timeoutMs = FOCUS_CALL_TIMEOUT_MS)
                    if (output != null && parseLeadingBoolean(output.trim()) == true) {
                        return@withContext true
                    }
                    delay(POLL_INTERVAL_MS.milliseconds)
                }
                false
            }
        if (!focused) {
            logger.info { "GNOME extension did not confirm preview focus; using AWT activation" }
            super.focusPreview(window)
        }
    }

    private suspend fun waitForMaximized(
        timeoutMs: Long,
        expected: Boolean,
    ): Boolean {
        delay(MAXIMIZE_SETTLE_MS.milliseconds)
        val deadline = TimeSource.Monotonic.markNow() + timeoutMs.milliseconds
        while (deadline.hasNotPassedNow()) {
            val output = callMethod("IsPreviewMaximized", timeoutMs = CALL_TIMEOUT_MS)
            if (output != null && parseLeadingBoolean(output.trim()) == expected) {
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
