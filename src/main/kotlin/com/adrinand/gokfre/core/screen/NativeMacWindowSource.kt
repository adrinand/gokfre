package com.adrinand.gokfre.core.screen

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.platform.mac.CoreFoundation.CFArrayRef
import com.sun.jna.platform.mac.CoreFoundation.CFDictionaryRef
import com.sun.jna.platform.mac.CoreFoundation.CFNumberRef
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

    override fun frontmostWindowPid(selfPid: Long): Long? = CoreGraphicsWindowList.frontmostOwnerPid(selfPid)

    override fun windowTitle(pid: Long): String? {
        val appElement = createAppElement(pid) ?: return null
        return try {
            readWindowTitle(app, appElement)
        } finally {
            release(appElement)
        }
    }

    override fun windowBounds(pid: Long): Rectangle? {
        val appElement = createAppElement(pid) ?: return null
        return try {
            readWindowBounds(app, appElement)
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

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
internal interface CoreGraphicsServices : Library {
    fun CGWindowListCopyWindowInfo(
        option: Int,
        relativeToWindow: Int,
    ): CFArrayRef?

    companion object {
        val INSTANCE: CoreGraphicsServices = Native.load("CoreGraphics", CoreGraphicsServices::class.java)
    }
}

private object CoreGraphicsWindowList {
    private val logger = Logger.getLogger("com.adrinand.gokfre.core.screen.MacWindowList")
    private const val CG_WINDOW_LIST_ON_SCREEN_ONLY = 1
    private const val CG_WINDOW_LIST_EXCLUDE_DESKTOP_ELEMENTS = 1 shl 4
    private const val CG_NULL_WINDOW_ID = 0
    private const val CG_NORMAL_WINDOW_LAYER = 0
    private val kOwnerPidAttribute: CFStringRef by lazy { CFStringRef.createCFString("kCGWindowOwnerPID") }
    private val kLayerAttribute: CFStringRef by lazy { CFStringRef.createCFString("kCGWindowLayer") }
    private val kAlphaAttribute: CFStringRef by lazy { CFStringRef.createCFString("kCGWindowAlpha") }

    fun frontmostOwnerPid(selfPid: Long): Long? =
        runCatching {
            val windows =
                CoreGraphicsServices.INSTANCE.CGWindowListCopyWindowInfo(
                    CG_WINDOW_LIST_ON_SCREEN_ONLY or CG_WINDOW_LIST_EXCLUDE_DESKTOP_ELEMENTS,
                    CG_NULL_WINDOW_ID,
                )
            if (windows?.pointer == null) {
                logger.warning { "CGWindowListCopyWindowInfo returned no windows" }
                return@runCatching null
            }
            try {
                (0 until windows.count).firstNotNullOfOrNull { index ->
                    runCatching { normalWindowOwnerPid(windows, index, selfPid) }
                        .onFailure { logger.fine { "Skipping window entry $index: ${it.message}" } }
                        .getOrNull()
                }
            } finally {
                windows.release()
            }
        }.onFailure { logger.warning { "Failed to read the frontmost window list: ${it.message}" } }.getOrNull()

    private fun normalWindowOwnerPid(
        windows: CFArrayRef,
        index: Int,
        selfPid: Long,
    ): Long? =
        windows.getValueAtIndex(index)
            ?.let { CFDictionaryRef(it) }
            ?.takeIf { it.layer() == CG_NORMAL_WINDOW_LAYER }
            ?.takeIf { it.alpha() ?: 1.0 > 0.0 }
            ?.pid()
            ?.takeIf { it > 0 && it != selfPid }

    private fun CFDictionaryRef.layer(): Int? = getValue(kLayerAttribute)?.let { CFNumberRef(it).intValue() }

    private fun CFDictionaryRef.alpha(): Double? = getValue(kAlphaAttribute)?.let { CFNumberRef(it).doubleValue() }

    private fun CFDictionaryRef.pid(): Long? = getValue(kOwnerPidAttribute)?.let { CFNumberRef(it).longValue() }
}
