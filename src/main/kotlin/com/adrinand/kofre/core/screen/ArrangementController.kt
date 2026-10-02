package com.adrinand.kofre.core.screen

import kotlinx.coroutines.delay
import java.awt.Rectangle
import java.util.logging.Logger
import kotlin.time.Duration.Companion.milliseconds

private val logger = Logger.getLogger("com.adrinand.kofre.core.screen.ArrangementController")

private const val FOCUS_SETTLE_DELAY_MS = 100L

private fun WindowHandle?.format(): String =
    when (this) {
        null -> "null"
        is WindowHandle.X11 -> "0x${id.toString(HEX_RADIX)}"
        is WindowHandle.Wayland -> "wayland:$backend:$token"
        is WindowHandle.Mac -> "mac:pid=$pid"
    }

private const val HEX_RADIX = 16

/**
 * Orchestrates capturing the target window and applying a tiled bounds to it.
 */
class ArrangementController(
    val windowManager: WindowManager,
) {
    private var targetWindow: WindowHandle? = null

    /**
     * Captures the currently active window so subsequent [arrange] calls apply
     * to it.
     */
    fun captureTargetWindow() {
        targetWindow = windowManager.getActiveWindowId()
        logger.info { "Captured target window: ${targetWindow.format()}" }
    }

    /**
     * Computes the bounds for [selectedKeys] inside the given weighted layout
     * and applies them to the captured target window.
     *
     * Returns the computed bounds, or `null` if the selection could not be
     * resolved or no target window was captured.
     */
    suspend fun arrange(
        rowWeights: List<Int>,
        columnWeights: List<Int>,
        keyLabels: List<List<String>>,
        selectedKeys: List<String>,
        workArea: Rectangle,
    ): Rectangle? {
        val window = targetWindow
        val positions = if (window != null) findKeyPositions(keyLabels, selectedKeys) else emptyList()
        val bounds = computeSelectedBounds(rowWeights, columnWeights, positions, workArea)

        return when {
            window == null -> {
                logger.warning { "arrange() called but no target window was captured" }
                null
            }

            positions.isEmpty() -> {
                logger.warning { "arrange() could not resolve positions for keys: $selectedKeys" }
                null
            }

            bounds == null -> {
                logger.warning { "arrange() could not compute bounds for positions: $positions" }
                null
            }

            else -> {
                logger.info { "Arranging window ${window.format()} to $bounds" }
                windowManager.focusWindow(window)
                delay(FOCUS_SETTLE_DELAY_MS.milliseconds)
                windowManager.setWindowBounds(window, bounds)
                delay(FOCUS_SETTLE_DELAY_MS.milliseconds)
                bounds
            }
        }
    }
}
