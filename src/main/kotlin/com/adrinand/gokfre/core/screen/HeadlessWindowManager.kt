package com.adrinand.gokfre.core.screen

import java.awt.Rectangle
import java.awt.Window

/**
 * No-op [WindowManager] for headless environments and tests.
 *
 * It records the last requested operation so tests can verify the integration
 * without a real display server.
 */
class HeadlessWindowManager : WindowManager {
    var lastActiveWindowHandle: WindowHandle? = null
        private set

    var lastBounds: Rectangle? = null
        private set

    override fun getActiveWindowId(): WindowHandle {
        val handle = WindowHandle.X11(DEFAULT_HEADLESS_WINDOW_ID)
        lastActiveWindowHandle = handle
        return handle
    }

    override fun setWindowBounds(
        window: WindowHandle,
        bounds: Rectangle,
    ) {
        lastActiveWindowHandle = window
        lastBounds = bounds
    }

    override fun focusWindow(window: WindowHandle) {
        lastActiveWindowHandle = window
    }

    override suspend fun enterFullscreen(
        window: Window,
        timeoutMs: Long,
    ): Boolean = true

    companion object {
        const val DEFAULT_HEADLESS_WINDOW_ID = 42L
    }
}
