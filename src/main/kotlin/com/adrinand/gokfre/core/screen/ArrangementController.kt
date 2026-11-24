package com.adrinand.gokfre.core.screen

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.awt.Rectangle
import java.util.logging.Logger
import kotlin.time.Duration.Companion.milliseconds

private val logger = Logger.getLogger("com.adrinand.gokfre.core.screen.ArrangementController")

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

    @Volatile
    private var captureGeneration = 0L

    /**
     * Work area of the monitor that hosted the captured target window.
     */
    val targetWorkArea: Rectangle?
        get() = capturedTarget.workArea

    /**
     * Returns `true` when a target window has been captured.
     */
    val hasCapturedTarget: Boolean
        get() = capturedTarget.window != null

    /**
     * Discards an in-flight background capture.
     *
     * Called when the preview opens so a capture that started before the preview
     * took focus cannot replace the real target with the preview itself.
     */
    @Synchronized
    fun invalidateCaptureInFlight() {
        captureGeneration++
    }

    /**
     * Captures the currently active window so subsequent [arrange] calls apply
     * to it.
     */
    fun captureTargetWindow() {
        captureTarget(shouldApply = { true }, keepPreviousOnNull = false)
    }

    /**
     * Captures the active window on [Dispatchers.IO] for background refreshes.
     *
     * [shouldApply] is checked after the capture finishes so a capture that
     * raced with the preview opening can be discarded. Returns `true` when a
     * non-null target was committed.
     */
    suspend fun captureTargetWindowOnIo(shouldApply: () -> Boolean): Boolean =
        withContext(Dispatchers.IO) {
            captureTarget(shouldApply = shouldApply, keepPreviousOnNull = true)
        }

    private fun captureTarget(
        shouldApply: () -> Boolean,
        keepPreviousOnNull: Boolean,
    ): Boolean {
        val generation = captureGeneration
        val window = windowManager.getActiveWindowId()
        val workArea =
            window?.let { handle ->
                // Backends that track their own work area (GNOME) answer without
                // a bounds lookup, which keeps background capture cheap.
                windowManager.getWorkArea(handle, null)
                    ?: windowManager.getWindowBounds(handle)?.let { bounds ->
                        windowManager.getWorkArea(handle, bounds)
                    }
            }
        return commitCapture(generation, window, workArea, shouldApply, keepPreviousOnNull)
    }

    @Synchronized
    private fun commitCapture(
        generation: Long,
        window: WindowHandle?,
        workArea: Rectangle?,
        shouldApply: () -> Boolean,
        keepPreviousOnNull: Boolean,
    ): Boolean {
        if (generation != captureGeneration || !shouldApply()) {
            logger.fine { "Discarding target capture superseded by the preview" }
            return false
        }
        val previous = capturedTarget
        val keepPrevious = window == null && keepPreviousOnNull && previous.window != null
        if (keepPrevious) {
            logger.fine { "Ignoring transient capture without an active window" }
        } else {
            capturedTarget = CapturedTarget(window = window, workArea = workArea)
            val changed = window != null && (window != previous.window || workArea != previous.workArea)
            if (changed) {
                logger.info { "Captured target window: ${window.format()}, workArea=$workArea" }
            }
        }
        return window != null
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
