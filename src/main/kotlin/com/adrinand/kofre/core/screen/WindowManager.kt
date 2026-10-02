package com.adrinand.kofre.core.screen

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
     * Makes the Kofre preview [window] fullscreen and waits for the state to
     * be applied, returning `true` if fullscreen was confirmed.
     */
    suspend fun enterFullscreen(
        window: Window,
        timeoutMs: Long,
    ): Boolean
}
