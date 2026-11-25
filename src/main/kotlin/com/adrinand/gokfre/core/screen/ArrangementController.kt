package com.adrinand.gokfre.core.screen

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import java.awt.Rectangle
import java.util.logging.Logger
import kotlin.time.Duration.Companion.milliseconds

private val logger = Logger.getLogger("com.adrinand.gokfre.core.screen.ArrangementController")
private const val FOCUS_SETTLE_DELAY_MS = 100L

/**
 * Orchestrates capturing the target window and applying a tiled bounds to it.
 *
 * The monitor that hosts the captured window is recorded so the preview and the
 * arrangement use that monitor's work area.
 */
class ArrangementController(
    val windowManager: WindowManager,
) {
    private data class CapturedTarget(
        val window: WindowHandle?,
        val workArea: Rectangle?,
    )

    @Volatile
    private var capturedTarget = CapturedTarget(window = null, workArea = null)

    /**
     * Work area of the monitor that hosted the captured target window.
     */
    val targetWorkArea: Rectangle?
        get() = capturedTarget.workArea

    /**
     * Captures the currently active window so subsequent [arrange] calls apply
     * to it.
     */
    fun captureTargetWindow() {
        val window = windowManager.getActiveWindowId()
        val workArea =
            window?.let { handle ->
                // Backends that track the work area (GNOME) answer without a
                // bounds lookup, which keeps the open path cheap.
                windowManager.getWorkArea(handle, null)
                    ?: windowManager.getWindowBounds(handle)?.let { bounds ->
                        windowManager.getWorkArea(handle, bounds)
                    }
            }
        capturedTarget = CapturedTarget(window = window, workArea = workArea)
        logger.info { "Captured target window: ${window?.format()}, workArea=$workArea" }
    }

    /**
     * Captures the active window on [Dispatchers.IO] so the hotkey handler does
     * not block the UI thread.
     */
    suspend fun captureTargetWindowOnIo() {
        runInterruptible(Dispatchers.IO) {
            captureTargetWindow()
        }
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
        val target = capturedTarget
        val window = target.window
        val effectiveWorkArea = target.workArea ?: workArea
        val positions = if (window != null) findKeyPositions(keyLabels, selectedKeys) else emptyList()
        val bounds = computeSelectedBounds(rowWeights, columnWeights, positions, effectiveWorkArea)

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
