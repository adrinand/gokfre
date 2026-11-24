package com.adrinand.gokfre.core.screen

import kotlinx.coroutines.CoroutineScope
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

    var activeWindowBounds: Rectangle? = null

    var activeWindowWorkArea: Rectangle? = null

    override fun getActiveWindowId(): WindowHandle {
        val handle = WindowHandle.X11(DEFAULT_HEADLESS_WINDOW_ID)
        lastActiveWindowHandle = handle
        return handle
    }

    override fun getWindowBounds(window: WindowHandle): Rectangle? = activeWindowBounds

    override fun getWorkArea(
        window: WindowHandle,
        bounds: Rectangle?,
    ): Rectangle? = activeWindowWorkArea ?: super.getWorkArea(window, bounds)

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

    override suspend fun exitFullscreen(
        window: Window,
        timeoutMs: Long,
    ): Boolean = true

    override suspend fun showPreview(
        window: Window,
        targetWorkArea: Rectangle?,
        scope: CoroutineScope,
    ) {
        // No-op: tests drive placement directly.
    }

    companion object {
        const val DEFAULT_HEADLESS_WINDOW_ID = 42L
    }
}
