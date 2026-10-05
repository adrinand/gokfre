package com.adrinand.gokfre.core.screen

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.PointerType
import com.sun.jna.platform.mac.CoreFoundation.CFStringRef
import com.sun.jna.platform.mac.CoreFoundation.CFTypeRef
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.util.logging.Logger

private val logger = Logger.getLogger("com.adrinand.gokfre.core.screen.MacAccessibility")

internal const val AX_ERROR_SUCCESS = 0
internal const val AX_VALUE_CGPOINT_TYPE = 1
internal const val AX_VALUE_CGSIZE_TYPE = 2
internal const val POSITION_MEMORY_SIZE = 16L
internal const val SIZE_MEMORY_SIZE = 16L
internal const val COORDINATE_X_OFFSET = 0L
internal const val COORDINATE_Y_OFFSET = 8L
internal val kAXFocusedApplicationAttribute = CFStringRef.createCFString("AXFocusedApplication")
internal val kAXFocusedWindowAttribute = CFStringRef.createCFString("AXFocusedWindow")
internal val kAXPositionAttribute = CFStringRef.createCFString("AXPosition")
internal val kAXSizeAttribute = CFStringRef.createCFString("AXSize")
internal val kAXRaiseAction = CFStringRef.createCFString("AXRaise")
internal val kAXTitleAttribute = CFStringRef.createCFString("AXTitle")
internal val kAXFrontmostAttribute = CFStringRef.createCFString("AXFrontmost")

/**
 * Low-level mapping for macOS Accessibility/API (HIServices/ApplicationServices).
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming", "ComplexInterface")
internal interface ApplicationServices : Library {
    fun AXIsProcessTrusted(): Byte

    fun AXUIElementCreateSystemWide(): AXUIElementRef

    fun AXUIElementCreateApplication(pid: Int): AXUIElementRef

    fun AXUIElementGetPid(
        element: AXUIElementRef,
        pid: IntByReference,
    ): Int

    fun AXUIElementCopyAttributeValue(
        element: AXUIElementRef,
        attribute: CFStringRef,
        value: PointerByReference,
    ): Int

    fun AXUIElementSetAttributeValue(
        element: AXUIElementRef,
        attribute: CFStringRef,
        value: Pointer,
    ): Int

    fun AXUIElementPerformAction(
        element: AXUIElementRef,
        action: CFStringRef,
    ): Int

    fun AXValueCreate(
        type: Int,
        value: Pointer,
    ): AXValueRef

    fun AXValueGetValue(
        value: AXValueRef,
        type: Int,
        valuePtr: Pointer,
    ): Boolean

    companion object {
        val INSTANCE: ApplicationServices = Native.load("ApplicationServices", ApplicationServices::class.java)
    }
}

internal class AXUIElementRef : PointerType {
    constructor() : super()
    constructor(p: Pointer?) : super(p)
}

internal class AXValueRef : PointerType {
    constructor() : super()
    constructor(p: Pointer?) : super(p)
}

internal fun release(reference: PointerType) {
    if (reference.pointer != null) {
        CFTypeRef(reference.pointer).release()
    }
}

internal fun isAccessibilityTrusted(): Boolean =
    runCatching { ApplicationServices.INSTANCE.AXIsProcessTrusted() != 0.toByte() }
        .onFailure { logger.warning("Failed to check accessibility permission: ${it.message}") }
        .getOrDefault(false)

internal fun focusAppWindow(): Boolean =
    runCatching {
        val app = ApplicationServices.INSTANCE
        val appElement = app.AXUIElementCreateApplication(ProcessHandle.current().pid().toInt())
        if (appElement.pointer == null) {
            logger.warning { "Could not create AX application element for this process" }
            false
        } else {
            try {
                val error = app.AXUIElementSetAttributeValue(appElement, kAXFrontmostAttribute, cfBooleanTrue())
                if (error == AX_ERROR_SUCCESS) {
                    logger.info { "Brought the application forward for preview focus" }
                    true
                } else {
                    logger.warning { "Setting AXFrontmost failed with error $error" }
                    false
                }
            } finally {
                release(appElement)
            }
        }
    }.onFailure { logger.warning { "Failed to activate this application: ${it.message}" } }.getOrDefault(false)

internal fun copyAttribute(
    app: ApplicationServices,
    element: AXUIElementRef,
    attribute: CFStringRef,
): CFTypeRef? {
    val reference = PointerByReference()
    val error = app.AXUIElementCopyAttributeValue(element, attribute, reference)
    return if (error == AX_ERROR_SUCCESS && reference.value != null) {
        CFTypeRef(reference.value)
    } else {
        null
    }
}

internal fun readPid(
    app: ApplicationServices,
    element: AXUIElementRef,
): Long? {
    val pidRef = IntByReference()
    val error = app.AXUIElementGetPid(element, pidRef)
    return if (error == AX_ERROR_SUCCESS) {
        pidRef.value.toLong()
    } else {
        logger.warning { "AXUIElementGetPid failed with error $error" }
        null
    }
}

internal fun focusedWindow(
    app: ApplicationServices,
    appElement: AXUIElementRef,
): AXUIElementRef? {
    val windowPointer = copyAttribute(app, appElement, kAXFocusedWindowAttribute) ?: return null
    return AXUIElementRef(windowPointer.pointer)
}

internal fun readWindowTitle(
    app: ApplicationServices,
    appElement: AXUIElementRef,
): String? {
    val targetWindow = focusedWindow(app, appElement) ?: return null
    try {
        val titlePointer = copyAttribute(app, targetWindow, kAXTitleAttribute) ?: return null
        try {
            return CFStringRef(titlePointer.pointer).stringValue()
        } finally {
            release(titlePointer)
        }
    } finally {
        release(targetWindow)
    }
}

internal fun setPosition(
    app: ApplicationServices,
    window: AXUIElementRef,
    x: Double,
    y: Double,
) {
    val memory = Memory(POSITION_MEMORY_SIZE)
    memory.setDouble(COORDINATE_X_OFFSET, x)
    memory.setDouble(COORDINATE_Y_OFFSET, y)
    val value = app.AXValueCreate(AX_VALUE_CGPOINT_TYPE, memory)
    if (value.pointer == null) {
        return
    }
    try {
        app.AXUIElementSetAttributeValue(window, kAXPositionAttribute, value.pointer)
    } finally {
        release(value)
    }
}

internal fun setSize(
    app: ApplicationServices,
    window: AXUIElementRef,
    width: Double,
    height: Double,
) {
    val memory = Memory(SIZE_MEMORY_SIZE)
    memory.setDouble(COORDINATE_X_OFFSET, width)
    memory.setDouble(COORDINATE_Y_OFFSET, height)
    val value = app.AXValueCreate(AX_VALUE_CGSIZE_TYPE, memory)
    if (value.pointer == null) {
        return
    }
    try {
        app.AXUIElementSetAttributeValue(window, kAXSizeAttribute, value.pointer)
    } finally {
        release(value)
    }
}

private fun cfBooleanTrue(): Pointer =
    NativeLibrary.getInstance("CoreFoundation")
        .getGlobalVariableAddress("kCFBooleanTrue")
        .getPointer(0)
