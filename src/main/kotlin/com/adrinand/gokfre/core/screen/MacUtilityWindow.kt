@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.adrinand.gokfre.core.screen

import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import java.awt.Window
import java.lang.reflect.Field
import java.util.logging.Logger
import javax.swing.SwingUtilities

private val logger = Logger.getLogger("com.adrinand.gokfre.core.screen.MacUtilityWindow")

private const val NS_WINDOW_COLLECTION_BEHAVIOR_MOVE_TO_ACTIVE_SPACE = 1L shl 1
private const val NS_WINDOW_COLLECTION_BEHAVIOR_TRANSIENT = 1L shl 3
private const val SELECTOR_SET_COLLECTION_BEHAVIOR = "setCollectionBehavior:"

private val objcMsgSend: Function? by lazy {
    runCatching { NativeLibrary.getInstance("objc").getFunction("objc_msgSend") }
        .onFailure { logger.warning { "Could not load the macOS Objective-C runtime: ${it.message}" } }
        .getOrNull()
}

/** Activates Gokfre and raises [window] on the active Space after macOS `LSUIElement` creation. */
internal fun configureMacUtilityWindow(window: Window) {
    if (!isMac() || !window.isShowing) {
        return
    }
    check(SwingUtilities.isEventDispatchThread()) { "macOS window activation must run on the AWT thread" }
    runCatching {
        activateMacApplication()
        window.toFront()
        window.requestFocus()
    }.onFailure {
        logger.warning { "Failed to activate macOS utility window: ${it.message}" }
    }
    runCatching {
        window.setCollectionBehavior(
            NS_WINDOW_COLLECTION_BEHAVIOR_MOVE_TO_ACTIVE_SPACE or NS_WINDOW_COLLECTION_BEHAVIOR_TRANSIENT,
        )
    }.onFailure {
        logger.warning { "Failed to set macOS collection behavior: ${it.message}" }
    }
}

private fun activateMacApplication() {
    val (runtime, msgSend) = objcContext() ?: return
    val appClass = runtime.objc_getClass("NSApplication") ?: return
    val app =
        runtime.sel_registerName("sharedApplication")?.let {
            msgSend.invokePointer(arrayOf(appClass, it))
        } ?: return
    if (app.respondsTo("activate")) {
        runtime.sel_registerName("activate")?.let { msgSend.invokeVoid(arrayOf(app, it)) }
    } else {
        runtime.sel_registerName("activateIgnoringOtherApps:")?.let {
            msgSend.invokeVoid(arrayOf(app, it, 1.toByte()))
        }
    }
}

private fun Window.setCollectionBehavior(behavior: Long) {
    val windowPointer = nsWindowPointer()
    if (windowPointer == null) {
        logger.warning { "Could not resolve the macOS NSWindow pointer" }
        return
    }
    if (!windowPointer.respondsTo(SELECTOR_SET_COLLECTION_BEHAVIOR)) {
        logger.warning { "The macOS window does not respond to $SELECTOR_SET_COLLECTION_BEHAVIOR" }
        return
    }
    applyCollectionBehavior(windowPointer, behavior)
}

private fun applyCollectionBehavior(
    windowPointer: Pointer,
    behavior: Long,
) {
    val (runtime, msgSend) = objcContext() ?: return
    val getBehavior = runtime.sel_registerName("collectionBehavior") ?: return
    val currentBehavior = msgSend.invokeLong(arrayOf(windowPointer, getBehavior))
    runtime.sel_registerName(SELECTOR_SET_COLLECTION_BEHAVIOR)?.let {
        msgSend.invokeVoid(arrayOf(windowPointer, it, currentBehavior or behavior))
    }
}

private fun Pointer.respondsTo(selectorName: String): Boolean =
    runCatching {
        val (runtime, msgSend) = objcContext() ?: return@runCatching false
        val respondsSelector = runtime.sel_registerName("respondsToSelector:") ?: return@runCatching false
        val targetSelector = runtime.sel_registerName(selectorName) ?: return@runCatching false
        val result = msgSend.invoke(java.lang.Byte.TYPE, arrayOf(this, respondsSelector, targetSelector)) as? Byte
        val value = result?.toInt() ?: 0
        value != 0
    }.getOrDefault(false)

private fun objcContext(): Pair<ObjectiveCRuntime, Function>? =
    runCatching {
        val runtime = ObjectiveCRuntime.INSTANCE
        val msgSend = objcMsgSend ?: return@runCatching null
        runtime to msgSend
    }.onFailure { logger.warning { "Could not initialize the macOS Objective-C runtime: ${it.message}" } }
        .getOrNull()

private fun Window.nsWindowPointer(): Pointer? =
    runCatching {
        val platformWindow =
            java.awt.Component::class.java
                .getDeclaredField("peer")
                .apply { isAccessible = true }
                .get(this)
                ?.let(::platformWindowOf) ?: return@runCatching null
        val pointer = pointerField(platformWindow.javaClass)?.getLong(platformWindow) ?: return@runCatching null
        pointer.takeIf { it != 0L }?.let(::Pointer)
    }.getOrNull()

private fun platformWindowOf(peer: Any): Any? {
    val method = peer.javaClass.getMethod("getPlatformWindow")
    method.isAccessible = true
    return method.invoke(peer)
}

private fun pointerField(type: Class<*>): Field? =
    generateSequence(type) { it.superclass }
        .mapNotNull { runCatching { it.getDeclaredField("ptr") }.getOrNull() }
        .firstOrNull()
        ?.apply { isAccessible = true }

internal interface ObjectiveCRuntime : Library {
    fun objc_getClass(name: String): Pointer?

    fun sel_registerName(name: String): Pointer?

    companion object {
        val INSTANCE: ObjectiveCRuntime = Native.load("objc", ObjectiveCRuntime::class.java)
    }
}
