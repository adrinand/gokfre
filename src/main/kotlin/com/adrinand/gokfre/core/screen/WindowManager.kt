package com.adrinand.gokfre.core.screen

import java.awt.Rectangle
import java.awt.Window

/**
 * Abstraction over the OS windowing layer used to read the active/focused window
 * and change its bounds.
 */
interface WindowManager {
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
     */
    suspend fun placePreview(
        window: Window,
        bounds: Rectangle,
        timeoutMs: Long,
    ) {
        exitFullscreen(window, timeoutMs)
        window.bounds = bounds
        enterFullscreen(window, timeoutMs)
    }

    /**
     * Requests that the Gokfre preview [window] become the active/focused
     * window. Defaults to AWT window activation.
     */
    suspend fun focusPreview(window: Window) {
        window.toFront()
        window.requestFocus()
    }
}
