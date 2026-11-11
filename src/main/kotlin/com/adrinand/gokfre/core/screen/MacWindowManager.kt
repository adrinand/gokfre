package com.adrinand.gokfre.core.screen

import java.awt.Rectangle
import java.awt.Window
import java.util.logging.Logger

/**
 * [WindowManager] implementation for macOS using the Accessibility API.
 *
 * The implementation captures the frontmost application's process ID and
 * operates on that application's focused window. macOS requires the user to
 * grant Accessibility permissions to Gokfre before these calls succeed.
 */
class MacWindowManager internal constructor(
    private val source: MacWindowSource,
) : SingleBackendWindowManager<WindowHandle.Mac>() {
    override val logger: Logger = Logger.getLogger("com.adrinand.gokfre.core.screen.MacWindowManager")

    constructor() : this(NativeMacWindowSource)

    override fun castHandle(window: WindowHandle): WindowHandle.Mac? = window as? WindowHandle.Mac

    override fun retrieveActiveWindow(): WindowHandle.Mac? = buildMacHandle()

    override fun retrieveBounds(handle: WindowHandle.Mac): Rectangle? = source.windowBounds(handle.pid)

    override fun applyBounds(
        handle: WindowHandle.Mac,
        bounds: Rectangle,
    ) {
        source.setBounds(handle.pid, bounds)
    }

    override fun focusHandle(handle: WindowHandle.Mac) {
        source.raise(handle.pid)
    }

    override suspend fun enterFullscreen(
        window: Window,
        timeoutMs: Long,
    ): Boolean = true

    override suspend fun exitFullscreen(
        window: Window,
        timeoutMs: Long,
    ): Boolean = true

    private fun buildMacHandle(): WindowHandle.Mac? {
        val selfPid = ProcessHandle.current().pid()
        val rawFocusedAppPid = source.focusedApplicationPid()
        val focusedAppPid = rawFocusedAppPid?.takeIf { it != selfPid }
        val focusedElementPid =
            if (focusedAppPid == null) {
                source.focusedElementPid()?.takeIf { it != selfPid && source.hasFocusedWindow(it) }
            } else {
                null
            }
        val pid =
            (focusedAppPid ?: focusedElementPid ?: source.frontmostWindowPid(selfPid))
                ?.takeIf { it != selfPid }
        if (pid == null) {
            logger.warning { "Could not determine the frontmost application or window" }
            return null
        }
        if (focusedAppPid != pid) {
            logger.info { "Using fallback window pid=$pid (focused app pid=$rawFocusedAppPid)" }
        }
        val title = if (focusedAppPid == pid) source.windowTitle(pid) ?: "" else ""
        return WindowHandle.Mac(pid = pid, title = title)
    }
}
