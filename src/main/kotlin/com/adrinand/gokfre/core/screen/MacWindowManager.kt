@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.adrinand.gokfre.core.screen

import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.awt.Rectangle
import java.awt.Window
import java.lang.reflect.Field
import java.util.logging.Logger
import javax.swing.SwingUtilities

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

    private data class CapturedMacWindow(
        val pid: Long,
        val workArea: Rectangle?,
    )

    @Volatile
    private var capturedWindow: CapturedMacWindow? = null

    constructor() : this(NativeMacWindowSource)

    override fun castHandle(window: WindowHandle): WindowHandle.Mac? = window as? WindowHandle.Mac

    override fun retrieveActiveWindow(): WindowHandle.Mac? = buildMacHandle()

    override fun retrieveBounds(handle: WindowHandle.Mac): Rectangle? = source.windowBounds(handle.pid)

    override fun getWorkArea(
        window: WindowHandle,
        bounds: Rectangle?,
    ): Rectangle? {
        val handle = window as? WindowHandle.Mac ?: return super.getWorkArea(window, bounds)
        val cachedWorkArea =
            if (bounds == null) {
                capturedWindow?.takeIf { it.pid == handle.pid }?.workArea
            } else {
                null
            }
        return cachedWorkArea ?: run {
            val effectiveBounds = bounds ?: source.windowBounds(handle.pid) ?: return null
            awtWorkAreaForWindowBounds(effectiveBounds)
        }
    }

    override fun applyBounds(
        handle: WindowHandle.Mac,
        bounds: Rectangle,
    ) {
        source.setBounds(handle.pid, bounds)
    }

    override fun focusHandle(handle: WindowHandle.Mac) {
        source.raise(handle.pid)
    }

    override suspend fun showAndFocusPreview(
        window: Window,
        targetWorkArea: Rectangle?,
        scope: CoroutineScope,
    ) {
        placePreviewForShow(window, targetWorkArea, scope)
        runCatching {
            focusAppWindow()
            super.focusPreview(window)
        }.onFailure {
            if (it is CancellationException) {
                currentCoroutineContext().ensureActive()
            }
        }.onFailure { logger.warning { "Preview focus failed: ${it.message}" } }
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
            capturedWindow = null
            return null
        }
        if (focusedAppPid != pid) {
            logger.info { "Using fallback window pid=$pid (focused app pid=$rawFocusedAppPid)" }
        }
        capturedWindow =
            CapturedMacWindow(
                pid = pid,
                workArea = source.windowBounds(pid)?.let(::awtWorkAreaForWindowBounds),
            )
        val title = if (focusedAppPid == pid) source.windowTitle(pid) ?: "" else ""
        return WindowHandle.Mac(pid = pid, title = title)
    }

    companion object {
        /**
         * Activates Gokfre and raises [window] on the active Space after macOS
         * `LSUIElement` creation. Used by settings and warning windows.
         */
        fun configureUtilityWindow(window: Window) = configureMacUtilityWindow(window)
    }
}

private const val NS_WINDOW_COLLECTION_BEHAVIOR_MOVE_TO_ACTIVE_SPACE = 1L shl 1
private const val NS_WINDOW_COLLECTION_BEHAVIOR_TRANSIENT = 1L shl 3
private const val SELECTOR_SET_COLLECTION_BEHAVIOR = "setCollectionBehavior:"

private val utilityLogger = Logger.getLogger("com.adrinand.gokfre.core.screen.MacWindowManager")

private val objcMsgSend: Function? by lazy {
    runCatching { NativeLibrary.getInstance("objc").getFunction("objc_msgSend") }
        .onFailure { utilityLogger.warning { "Could not load the macOS Objective-C runtime: ${it.message}" } }
        .getOrNull()
}

private fun configureMacUtilityWindow(window: Window) {
    if (!isMac() || !window.isShowing) {
        return
    }
    if (!SwingUtilities.isEventDispatchThread()) {
        utilityLogger.warning { "macOS window activation called off the AWT thread; skipping" }
        return
    }
    runCatching {
        activateMacApplication()
        window.toFront()
        window.requestFocus()
    }.onFailure {
        utilityLogger.warning { "Failed to activate macOS utility window: ${it.message}" }
    }
    runCatching {
        window.setCollectionBehavior(
            NS_WINDOW_COLLECTION_BEHAVIOR_MOVE_TO_ACTIVE_SPACE or NS_WINDOW_COLLECTION_BEHAVIOR_TRANSIENT,
        )
    }.onFailure {
        utilityLogger.warning { "Failed to set macOS collection behavior: ${it.message}" }
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
        utilityLogger.warning { "Could not resolve the macOS NSWindow pointer" }
        return
    }
    if (!windowPointer.respondsTo(SELECTOR_SET_COLLECTION_BEHAVIOR)) {
        utilityLogger.warning { "The macOS window does not respond to $SELECTOR_SET_COLLECTION_BEHAVIOR" }
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
    }.onFailure { utilityLogger.warning { "Could not initialize the macOS Objective-C runtime: ${it.message}" } }
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

private interface ObjectiveCRuntime : Library {
    fun objc_getClass(name: String): Pointer?

    fun sel_registerName(name: String): Pointer?

    companion object {
        val INSTANCE: ObjectiveCRuntime = Native.load("objc", ObjectiveCRuntime::class.java)
    }
}
