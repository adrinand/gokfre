package com.adrinand.gokfre.core.screen

import kotlinx.coroutines.CoroutineScope
import java.awt.Rectangle
import java.awt.Window

/**
 * Abstraction over the OS windowing layer used to read the active/focused window
 * and change its bounds.
 */
@Suppress("ComplexInterface", "TooManyFunctions")
interface WindowManager {
    /**
     * Whether a hidden preview keeps its maximized state, so re-asserting it on
     * every open only costs a backend round-trip.
     */
    val keepsPreviewMaximizedWhenHidden: Boolean
        get() = true

    /**
     * Applies one-time platform-specific configuration to the preview window.
     */
    fun configurePreviewWindow(window: Window) {}

    /**
     * Whether the preview should be always-on-top for the given visibility.
     * Linux ties it to visibility to avoid a hidden-window quirk.
     */
    fun previewAlwaysOnTop(visible: Boolean): Boolean = !isLinux() || visible

    /**
     * Returns a platform-specific handle for the currently active window,
     * or `null` if it cannot be determined.
     */
    fun getActiveWindowId(): WindowHandle?

    /**
     * Returns the current screen bounds of [window], or `null` if they cannot
     * be determined.
     *
     * Used to find the monitor that hosts the target window so the preview and
     * the arrangement both operate on that monitor.
     */
    fun getWindowBounds(window: WindowHandle): Rectangle?

    /**
     * Returns the work area of the monitor hosting [window] in the backend's
     * coordinate space, or `null` when it cannot be resolved.
     */
    fun getWorkArea(
        window: WindowHandle,
        bounds: Rectangle?,
    ): Rectangle? = bounds?.let(::awtWorkAreaForWindowBounds)

    /**
     * Moves and resizes [window] to [bounds] in screen coordinates.
     */
    fun setWindowBounds(
        window: WindowHandle,
        bounds: Rectangle,
    )

    /**
     * Requests that [window] become the active/focused window.
     */
    fun focusWindow(window: WindowHandle)

    /**
     * Makes the Gokfre preview [window] fullscreen and waits for the state to
     * be applied, returning `true` if fullscreen was confirmed.
     */
    suspend fun enterFullscreen(
        window: Window,
        timeoutMs: Long,
    ): Boolean

    /**
     * Un-maximizes the Gokfre preview [window] and waits for the state to be
     * applied, returning `true` if the window is no longer maximized.
     *
     * The preview must be restored before it can be moved to another monitor.
     */
    suspend fun exitFullscreen(
        window: Window,
        timeoutMs: Long,
    ): Boolean

    /**
     * Moves the Gokfre preview [window] to [bounds] and makes it fullscreen.
     *
     * Backends may override this to perform the restore/move/maximize sequence
     * atomically (the GNOME extension does). The default keeps the historical
     * client-side sequence used by X11, KDE, and macOS.
     *
     * Returns `true` when placement was applied.
     */
    suspend fun placePreview(
        window: Window,
        bounds: Rectangle,
        timeoutMs: Long,
    ): Boolean {
        if (!exitFullscreen(window, timeoutMs)) {
            return false
        }
        window.bounds = bounds
        return enterFullscreen(window, timeoutMs)
    }

    /**
     * Prepares [window] for display on [targetWorkArea] and requests focus.
     * Placement (restore, move, maximize) finishes before this returns so the
     * caller can reveal the window without intermediate states. Focus normally
     * runs in the background; backends that must activate before the reveal
     * focus synchronously. Any post-reveal maximize re-assert is scheduled on
     * [scope].
     */
    suspend fun showAndFocusPreview(
        window: Window,
        targetWorkArea: Rectangle?,
        scope: CoroutineScope,
    )

    /**
     * Requests that the Gokfre preview [window] become the active/focused
     * window. Defaults to AWT window activation.
     */
    suspend fun focusPreview(window: Window) {
        window.toFront()
        window.requestFocus()
    }
}
