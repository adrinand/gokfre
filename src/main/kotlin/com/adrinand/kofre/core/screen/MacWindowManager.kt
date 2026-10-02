package com.adrinand.kofre.core.screen

import java.awt.Rectangle
import java.awt.Window
import java.util.logging.Logger

/**
 * [WindowManager] implementation for macOS using the Accessibility API.
 *
 * The implementation captures the frontmost application's process ID and
 * operates on that application's focused window. macOS requires the user to
 * grant Accessibility permissions to Kofre before these calls succeed.
 */
class MacWindowManager : SingleBackendWindowManager<WindowHandle.Mac>() {
    override val logger: Logger = Logger.getLogger("com.adrinand.kofre.core.screen.MacWindowManager")

    private val app = ApplicationServices.INSTANCE

    override fun castHandle(window: WindowHandle): WindowHandle.Mac? = window as? WindowHandle.Mac

    override fun retrieveActiveWindow(): WindowHandle.Mac? {
        val systemWide = app.AXUIElementCreateSystemWide()
        return try {
            if (systemWide.pointer == null) {
                logger.warning { "Failed to create system-wide accessibility element" }
                null
            } else {
                buildMacHandle(systemWide)
            }
        } finally {
            release(systemWide)
        }
    }

    override fun applyBounds(
        handle: WindowHandle.Mac,
        bounds: Rectangle,
    ) {
        val appElement = app.AXUIElementCreateApplication(handle.pid.toInt())
        if (appElement.pointer == null) {
            logger.warning { "Failed to create accessibility element for pid ${handle.pid}" }
            return
        }
        try {
            val targetWindow = focusedWindow(app, appElement) ?: return
            try {
                setPosition(app, targetWindow, bounds.x.toDouble(), bounds.y.toDouble())
                setSize(app, targetWindow, bounds.width.toDouble(), bounds.height.toDouble())
            } finally {
                release(targetWindow)
            }
        } finally {
            release(appElement)
        }
    }

    override fun focusHandle(handle: WindowHandle.Mac) {
        val appElement = app.AXUIElementCreateApplication(handle.pid.toInt())
        if (appElement.pointer == null) {
            return
        }
        try {
            val targetWindow = focusedWindow(app, appElement) ?: return
            try {
                app.AXUIElementPerformAction(targetWindow, kAXRaiseAction)
            } finally {
                release(targetWindow)
            }
        } finally {
            release(appElement)
        }
    }

    override suspend fun enterFullscreen(
        window: Window,
        timeoutMs: Long,
    ): Boolean {
        AwtFullscreen.setFullscreen(window)
        return true
    }

    private fun buildMacHandle(systemWide: AXUIElementRef): WindowHandle.Mac? {
        val focusedAppPointer = copyAttribute(app, systemWide, kAXFocusedApplicationAttribute) ?: return null
        val focusedApp = AXUIElementRef(focusedAppPointer.pointer)
        try {
            val pid = readPid(app, focusedApp) ?: return null
            val title = readWindowTitle(app, focusedApp)
            return WindowHandle.Mac(pid = pid, title = title ?: "")
        } finally {
            release(focusedApp)
        }
    }
}
