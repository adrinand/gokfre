package com.adrinand.gokfre

import java.awt.Rectangle
import java.awt.Toolkit
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.math.roundToInt

private const val DEFAULT_WIDTH = 800
private const val DEFAULT_HEIGHT = 600
private const val DEFAULT_X = 100
private const val DEFAULT_Y = 100
private const val COMMAND_TIMEOUT_SECONDS = 5L
private val X11_WINDOW_ID = Regex("""\d+""")

/**
 * A real Swing window used as the target for arrangement E2E tests.
 *
 * The window is brought to front and focused so that gokfre's window manager
 * sees it as the active window when the preview is toggled.
 */
class TestTargetWindow(
    width: Int = DEFAULT_WIDTH,
    height: Int = DEFAULT_HEIGHT,
    x: Int = DEFAULT_X,
    y: Int = DEFAULT_Y,
) {
    private val frame = JFrame("Gokfre E2E Target")
    private var x11WindowId: String? = null
    private var useX11Geometry = false
    private var lastX11Bounds: Rectangle? = null

    private val awtScale: Double by lazy {
        val holder = AtomicReference(1.0)
        SwingUtilities.invokeAndWait {
            holder.set(frame.graphicsConfiguration.defaultTransform.scaleX)
        }
        holder.get()
    }

    init {
        SwingUtilities.invokeAndWait {
            frame.setSize(width, height)
            frame.setLocation(x, y)
            frame.isVisible = true
            frame.toFront()
            frame.requestFocus()
            frame.requestFocusInWindow()
        }
    }

    /**
     * Frame bounds of the window.
     *
     * On X11, AWT can lag behind compositor-initiated resizes (KWin may not
     * deliver the configure event to the JVM at all), so read the geometry from
     * the server and add [_NET_FRAME_EXTENTS]. Other platforms use AWT.
     */
    val bounds: Rectangle
        get() {
            val x11 = x11FrameBounds()
            if (x11 != null) {
                useX11Geometry = true
                lastX11Bounds = x11
                return x11
            }
            // Once the X11 path works, never mix in AWT client bounds: they are
            // a different coordinate space and would look like a geometry change.
            return if (useX11Geometry) lastX11Bounds ?: awtBounds else awtBounds
        }

    private val awtBounds: Rectangle
        get() {
            val holder = AtomicReference<Rectangle>()
            SwingUtilities.invokeAndWait { holder.set(frame.bounds) }
            return holder.get() ?: error("Failed to read target window bounds")
        }

    val workArea: Rectangle
        get() {
            val holder = AtomicReference<Rectangle>()
            SwingUtilities.invokeAndWait {
                val config = frame.graphicsConfiguration
                val insets = Toolkit.getDefaultToolkit().getScreenInsets(config)
                val screen = config.bounds
                holder.set(
                    Rectangle(
                        screen.x + insets.left,
                        screen.y + insets.top,
                        screen.width - insets.left - insets.right,
                        screen.height - insets.top - insets.bottom,
                    ),
                )
            }
            return holder.get() ?: error("Failed to read target work area")
        }

    fun focus(settleMs: Long = 300L) {
        if (activateWindowByTitle(frame.title)) {
            Thread.sleep(settleMs)
            return
        }

        SwingUtilities.invokeAndWait {
            frame.isAlwaysOnTop = true
            frame.toFront()
            frame.requestFocus()
            frame.requestFocusInWindow()
        }
        Thread.sleep(settleMs)
        SwingUtilities.invokeAndWait { frame.isAlwaysOnTop = false }
    }

    /**
     * Reports the AWT and X11 views of the window so E2E failures can tell
     * whether the compositor or the AWT event queue is out of sync.
     */
    fun describeGeometry(): String = "awt=$awtBounds x11=${x11FrameBounds() ?: "unavailable"}"

    fun close() {
        SwingUtilities.invokeAndWait { frame.dispose() }
    }

    private fun x11FrameBounds(): Rectangle? =
        runCatching {
            val id = x11WindowId ?: findX11WindowId() ?: return@runCatching null
            val info = runCommand("xwininfo", "-id", id)

            fun value(label: String): Int? =
                info.lineSequence()
                    .firstOrNull { it.contains(label) }
                    ?.substringAfter(':')
                    ?.trim()
                    ?.toIntOrNull()

            val x = value("Absolute upper-left X") ?: return@runCatching null
            val y = value("Absolute upper-left Y") ?: return@runCatching null
            val width = value("Width") ?: return@runCatching null
            val height = value("Height") ?: return@runCatching null
            val (left, right, top, bottom) = frameExtents(id) ?: return@runCatching null
            val native =
                Rectangle(x - left, y - top, width + left + right, height + top + bottom)
            if (awtScale == 1.0) {
                native
            } else {
                Rectangle(
                    (native.x / awtScale).roundToInt(),
                    (native.y / awtScale).roundToInt(),
                    (native.width / awtScale).roundToInt(),
                    (native.height / awtScale).roundToInt(),
                )
            }
        }.getOrNull()

    private fun findX11WindowId(): String? {
        val ids =
            runCommand("xdotool", "search", "--name", frame.title)
                .lineSequence()
                .filter { X11_WINDOW_ID.matches(it.trim()) }
                .map { it.trim() }
                .toList()
        // Window-manager frame windows can share the title but do not carry
        // frame extents; the client window does.
        val client = ids.firstOrNull { frameExtents(it) != null } ?: return null
        x11WindowId = client
        return client
    }

    private fun frameExtents(id: String): List<Int>? {
        val values =
            runCommand("xprop", "-id", id, "_NET_FRAME_EXTENTS")
                .substringAfter('=', "")
                .split(',')
                .mapNotNull { it.trim().toIntOrNull() }
        return values.takeIf { it.size == 4 }
    }

    private fun runCommand(vararg command: String): String {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        try {
            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                error("Command timed out: ${command.joinToString(" ")}")
            }
            return process.inputStream.bufferedReader().use { it.readText().trim() }
        } finally {
            if (process.isAlive) {
                process.destroyForcibly()
            }
        }
    }
}
