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
private const val SHOW_PREVIEW_TIMEOUT_MS = 1_000L
private const val FOCUS_RETRY_TIMEOUT_MS = 200L
private const val SHOW_WAIT_TIMEOUT_MS = 500L
private const val SHOW_WAIT_POLL_MS = 10L
private const val PLACE_PREVIEW_ATTEMPTS = 10
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
private val SHOW_PREVIEW_RESULT_REGEX = Regex("""\(\s*(true|false)\s*,\s*(true|false)\s*\)""")
private val SHOW_PREVIEW_METHOD_REGEX =
    Regex("""<method name="ShowPreview">(.*?)</method>""", RegexOption.DOT_MATCHES_ALL)

internal fun parseSupportsShowPreview(introspection: String): Boolean {
    val method = SHOW_PREVIEW_METHOD_REGEX.find(introspection)?.groupValues?.get(1) ?: return false
    return method.contains("name=\"pid\"") &&
        introspection.contains("<method name=\"PlacePreviewForPid\"") &&
        introspection.contains("<method name=\"FocusPreviewForPid\"")
}

internal fun parseShowPreviewResult(output: String): Pair<Boolean, Boolean>? {
    val match = SHOW_PREVIEW_RESULT_REGEX.findAll(output).lastOrNull()
    val placed = match?.groupValues?.get(1)?.toBooleanStrictOrNull()
    val focused = match?.groupValues?.get(2)?.toBooleanStrictOrNull()
    return if (placed != null && focused != null) placed to focused else null
}

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

    @Volatile
    private var capturedWorkArea: Rectangle? = null

    @Volatile
    private var supportsShowPreview: Boolean? = null

    private val selfPid: Int = ProcessHandle.current().pid().toInt()

    override val previewRequiresMappedWindow: Boolean = true

    /**
     * Returns `true` if the companion GNOME Shell extension is available on
     * the session bus and a D-Bus client is installed.
     */
    fun isAvailable(): Boolean {
        val client = findGdbus() ?: return false
        val output =
            runDbusClient(
                client,
                listOf("introspect", "--session", "--dest", SERVICE_NAME, "--object-path", OBJECT_PATH, "--xml"),
            ) ?: return false
        supportsShowPreview = parseSupportsShowPreview(output)
        logger.info { "GNOME extension atomic ShowPreview supported=$supportsShowPreview" }
        return true
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
     * Uses the work area captured by `CaptureActiveWindow`, avoiding a separate
     * `GetCapturedWindow` D-Bus call.
     */
    override fun getWorkArea(window: WindowHandle): Rectangle? {
        val handle = castHandle(window) ?: return super.getWorkArea(window)
        return capturedWorkArea ?: super.getWorkArea(handle)
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

    override suspend fun showPreview(
        window: Window,
        bounds: Rectangle,
        timeoutMs: Long,
    ): Boolean {
        if (capturedWorkArea == null || supportsShowPreview != true) {
            awaitMapped(window)
            return super.showPreview(window, bounds, timeoutMs)
        }
        if (showAtomically(window, bounds, timeoutMs)) {
            return true
        }
        logger.warning { "ShowPreview failed; retrying with pid-guarded placement" }
        awaitMapped(window)
        val placed = placePreviewForPid(bounds, timeoutMs)
        if (placed) {
            focusPreviewForPid(window)
        }
        return placed
    }

    private suspend fun awaitMapped(window: Window) {
        val deadline = TimeSource.Monotonic.markNow() + SHOW_WAIT_TIMEOUT_MS.milliseconds
        while (!window.isShowing && deadline.hasNotPassedNow()) {
            delay(SHOW_WAIT_POLL_MS.milliseconds)
        }
        if (!window.isShowing) {
            logger.warning { "Preview window did not report isShowing before placement" }
        }
    }

    private suspend fun placePreviewForPid(
        bounds: Rectangle,
        timeoutMs: Long,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val callTimeout = timeoutMs.coerceAtMost(SHOW_PREVIEW_TIMEOUT_MS)
            repeat(PLACE_PREVIEW_ATTEMPTS) { attempt ->
                val output =
                    callMethod(
                        "PlacePreviewForPid",
                        listOf(
                            bounds.x.toString(),
                            bounds.y.toString(),
                            bounds.width.toString(),
                            bounds.height.toString(),
                            selfPid.toString(),
                        ),
                        timeoutMs = callTimeout,
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

    private suspend fun showAtomically(
        window: Window,
        bounds: Rectangle,
        timeoutMs: Long,
    ): Boolean {
        val atomic = withContext(Dispatchers.IO) { callAtomicShowPreview(bounds, timeoutMs) }
        if (atomic == null) {
            logger.warning { "ShowPreview failed" }
            return false
        }
        if (!atomic.first) {
            logger.warning { "GNOME extension did not find the owned preview" }
            return false
        }
        if (!atomic.second) {
            focusPreviewForPid(window)
        }
        return true
    }

    private suspend fun callAtomicShowPreview(
        bounds: Rectangle,
        timeoutMs: Long,
    ): Pair<Boolean, Boolean>? {
        val callTimeout = timeoutMs.coerceAtMost(SHOW_PREVIEW_TIMEOUT_MS)
        var lastResult: Pair<Boolean, Boolean>? = null
        repeat(PLACE_PREVIEW_ATTEMPTS) { attempt ->
            val result =
                callMethod(
                    "ShowPreview",
                    listOf(
                        bounds.x.toString(),
                        bounds.y.toString(),
                        bounds.width.toString(),
                        bounds.height.toString(),
                        selfPid.toString(),
                    ),
                    timeoutMs = callTimeout,
                )?.let(::parseShowPreviewResult) ?: return null
            lastResult = result
            if (result.first) {
                return result
            }
            if (attempt < PLACE_PREVIEW_ATTEMPTS - 1) {
                delay(POLL_INTERVAL_MS.milliseconds)
            }
        }
        return lastResult
    }

    override suspend fun focusPreview(window: Window) {
        if (!awaitFocus("FocusPreview")) {
            logger.info { "GNOME extension did not confirm preview focus; using AWT activation" }
            super.focusPreview(window)
        }
    }

    private suspend fun focusPreviewForPid(window: Window) {
        if (!awaitFocus("FocusPreviewForPid", listOf(selfPid.toString()))) {
            logger.info { "GNOME extension did not confirm preview focus; using AWT activation" }
            super.focusPreview(window)
        }
    }

    private suspend fun awaitFocus(
        method: String,
        args: List<String> = emptyList(),
    ): Boolean =
        withContext(Dispatchers.IO) {
            val deadline = TimeSource.Monotonic.markNow() + FOCUS_RETRY_TIMEOUT_MS.milliseconds
            while (deadline.hasNotPassedNow()) {
                val output = callMethod(method, args, timeoutMs = FOCUS_CALL_TIMEOUT_MS)
                if (output != null && parseLeadingBoolean(output.trim()) == true) {
                    return@withContext true
                }
                delay(POLL_INTERVAL_MS.milliseconds)
            }
            false
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
