package com.adrinand.gokfre.core.screen

import java.awt.Rectangle
import java.util.logging.Logger

/**
 * Base class for [WindowManager] implementations that manage a single backend
 * handle subtype.
 *
 * Subclasses implement the backend-specific logic with their concrete
 * [WindowHandle] type. The base class centralises the cast and the warning
 * logged when an incompatible handle is passed in.
 */
abstract class SingleBackendWindowManager<H : WindowHandle> : WindowManager {
    protected abstract val logger: Logger

    /**
     * Casts [window] to this backend's handle type, or returns `null` when the
     * handle belongs to a different backend.
     */
    protected abstract fun castHandle(window: WindowHandle): H?

    /**
     * Returns the currently active window as this backend's handle type.
     */
    protected abstract fun retrieveActiveWindow(): H?

    /**
     * Moves and resizes [handle] to [bounds].
     */
    protected abstract fun applyBounds(
        handle: H,
        bounds: Rectangle,
    )

    /**
     * Requests that [handle] become the active/focused window.
     */
    protected abstract fun focusHandle(handle: H)

    final override fun getActiveWindowId(): WindowHandle? = retrieveActiveWindow()

    final override fun setWindowBounds(
        window: WindowHandle,
        bounds: Rectangle,
    ) {
        val handle = castHandle(window)
        if (handle == null) {
            logger.warning { "Ignoring setWindowBounds for incompatible handle: $window" }
            return
        }
        applyBounds(handle, bounds)
    }

    final override fun focusWindow(window: WindowHandle) {
        val handle = castHandle(window)
        if (handle == null) {
            logger.warning { "Ignoring focusWindow for incompatible handle: $window" }
            return
        }
        focusHandle(handle)
    }
}
