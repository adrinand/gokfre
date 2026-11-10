package com.adrinand.gokfre.core.screen

import com.sun.jna.platform.mac.CoreFoundation.CFStringRef
import java.awt.Rectangle
import java.util.logging.Logger

internal object NativeMacWindowSource : MacWindowSource {
    private val logger = Logger.getLogger("com.adrinand.gokfre.core.screen.NativeMacWindowSource")
    private val app = ApplicationServices.INSTANCE

    override fun focusedApplicationPid(): Long? = readPidFromSystemWideAttribute(kAXFocusedApplicationAttribute)

    override fun focusedElementPid(): Long? = readPidFromSystemWideAttribute(kAXFocusedUIElementAttribute)

    override fun hasFocusedWindow(pid: Long): Boolean {
        val appElement = createAppElement(pid) ?: return false
        return try {
            val window = focusedWindow(app, appElement) ?: return false
            release(window)
            true
        } finally {
            release(appElement)
        }
    }

    override fun frontmostWindowPid(selfPid: Long): Long? = frontmostWindowOwnerPid(selfPid)

    override fun windowTitle(pid: Long): String? {
        val appElement = createAppElement(pid) ?: return null
        return try {
            readWindowTitle(app, appElement)
        } finally {
            release(appElement)
        }
    }

    override fun setBounds(
        pid: Long,
        bounds: Rectangle,
    ) {
        val appElement = createAppElement(pid) ?: return
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

    override fun raise(pid: Long) {
        val appElement = createAppElement(pid) ?: return
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

    private fun readPidFromSystemWideAttribute(attribute: CFStringRef): Long? {
        val systemWide = app.AXUIElementCreateSystemWide()
        if (systemWide.pointer == null) {
            logger.warning { "Failed to create system-wide accessibility element" }
            return null
        }
        return try {
            val value = copyAttribute(app, systemWide, attribute) ?: return null
            val element = AXUIElementRef(value.pointer)
            try {
                readPid(app, element)
            } finally {
                release(element)
            }
        } finally {
            release(systemWide)
        }
    }

    private fun createAppElement(pid: Long): AXUIElementRef? {
        val element = app.AXUIElementCreateApplication(pid.toInt())
        if (element.pointer == null) {
            logger.warning { "Failed to create accessibility element for pid $pid" }
            return null
        }
        return element
    }
}
