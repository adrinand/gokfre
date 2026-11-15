package com.adrinand.gokfre

import java.awt.Rectangle
import java.awt.Toolkit
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JFrame
import javax.swing.SwingUtilities

private const val DEFAULT_WIDTH = 800
private const val DEFAULT_HEIGHT = 600
private const val DEFAULT_X = 100
private const val DEFAULT_Y = 100

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

    val bounds: Rectangle
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
    fun describeGeometry(): String {
        val awt = bounds
        fun run(vararg command: String): String =
            runCatching {
                val process = ProcessBuilder(*command).redirectErrorStream(true).start()
                process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
                process.inputStream.bufferedReader().readText().trim()
            }.getOrDefault("failed")
        val ids = run("xdotool", "search", "--name", frame.title).lineSequence().filter { it.isNotBlank() }
        val geometries =
            ids.joinToString("; ") { id ->
                val geometry = run("xdotool", "getwindowgeometry", "--shell", id)
                "id=$id ${geometry.replace('\n', ',')}"
            }
        val tree = run("xwininfo", "-root", "-tree").lineSequence()
            .filter { it.contains(frame.title) || it.contains("Gokfre") }
            .joinToString("; ")
        return "awt=$awt toolkit=${Toolkit.getDefaultToolkit().javaClass.name} " +
            "x11=[$geometries] tree=[$tree]"
    }

    fun close() {
        SwingUtilities.invokeAndWait { frame.dispose() }
    }
}
