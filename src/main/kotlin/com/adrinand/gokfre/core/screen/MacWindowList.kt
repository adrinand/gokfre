package com.adrinand.gokfre.core.screen

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.platform.mac.CoreFoundation.CFArrayRef
import com.sun.jna.platform.mac.CoreFoundation.CFDictionaryRef
import com.sun.jna.platform.mac.CoreFoundation.CFNumberRef
import com.sun.jna.platform.mac.CoreFoundation.CFStringRef
import java.util.logging.Logger

private val logger = Logger.getLogger("com.adrinand.gokfre.core.screen.MacWindowList")

private const val CG_WINDOW_LIST_ON_SCREEN_ONLY = 1
private const val CG_WINDOW_LIST_EXCLUDE_DESKTOP_ELEMENTS = 1 shl 4
private const val CG_NULL_WINDOW_ID = 0
private const val CG_NORMAL_WINDOW_LAYER = 0
private val kOwnerPidAttribute: CFStringRef by lazy { CFStringRef.createCFString("kCGWindowOwnerPID") }
private val kLayerAttribute: CFStringRef by lazy { CFStringRef.createCFString("kCGWindowLayer") }
private val kAlphaAttribute: CFStringRef by lazy { CFStringRef.createCFString("kCGWindowAlpha") }

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

/**
 * Returns the process ID owning the frontmost normal window, ignoring Gokfre
 * itself. Used when the system-wide accessibility element cannot report the
 * focused application.
 */
internal fun frontmostWindowPid(selfPid: Long = ProcessHandle.current().pid()): Long? =
    runCatching {
        val windows =
            CoreGraphicsServices.INSTANCE.CGWindowListCopyWindowInfo(
                CG_WINDOW_LIST_ON_SCREEN_ONLY or CG_WINDOW_LIST_EXCLUDE_DESKTOP_ELEMENTS,
                CG_NULL_WINDOW_ID,
            )
        if (windows == null || windows.pointer == null) {
            logger.warning { "CGWindowListCopyWindowInfo returned no windows" }
            return@runCatching null
        }
        try {
            (0 until windows.getCount()).firstNotNullOfOrNull { index ->
                runCatching { normalWindowOwnerPid(windows, index, selfPid) }
                    .onFailure { logger.fine { "Skipping window entry $index: ${it.message}" } }
                    .getOrNull()
            }
        } finally {
            windows.release()
        }
    }.onFailure { logger.warning { "Failed to read the frontmost window list: ${it.message}" } }.getOrNull()

internal fun focusedElementPid(
    systemWide: AXUIElementRef,
    selfPid: Long,
): Long? {
    val elementPointer =
        copyAttribute(
            ApplicationServices.INSTANCE,
            systemWide,
            kAXFocusedUIElementAttribute,
        ) ?: return null
    val element = AXUIElementRef(elementPointer.pointer)
    return try {
        readPid(ApplicationServices.INSTANCE, element)?.takeIf { it != selfPid && hasFocusedWindow(it) }
    } finally {
        release(element)
    }
}

private fun hasFocusedWindow(pid: Long): Boolean {
    val app = ApplicationServices.INSTANCE
    val appElement = app.AXUIElementCreateApplication(pid.toInt())
    if (appElement.pointer == null) return false
    return try {
        val window = focusedWindow(app, appElement) ?: return false
        release(window)
        true
    } finally {
        release(appElement)
    }
}

private fun normalWindowOwnerPid(
    windows: CFArrayRef,
    index: Int,
    selfPid: Long,
): Long? =
    windows.getValueAtIndex(index)
        ?.let { CFDictionaryRef(it) }
        ?.takeIf { it.windowLayer() == CG_NORMAL_WINDOW_LAYER }
        ?.takeIf { it.windowAlpha() ?: 1.0 > 0.0 }
        ?.ownerPid()
        ?.takeIf { it > 0 && it != selfPid }

private fun CFDictionaryRef.windowLayer(): Int? = getValue(kLayerAttribute)?.let { CFNumberRef(it).intValue() }

private fun CFDictionaryRef.windowAlpha(): Double? = getValue(kAlphaAttribute)?.let { CFNumberRef(it).doubleValue() }

private fun CFDictionaryRef.ownerPid(): Long? = getValue(kOwnerPidAttribute)?.let { CFNumberRef(it).longValue() }
