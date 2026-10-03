package com.adrinand.gokfre.core.screen

import com.adrinand.gokfre.ui.PREVIEW_WINDOW_TITLE
import com.sun.jna.NativeLong
import com.sun.jna.platform.unix.X11
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.NativeLongByReference
import com.sun.jna.ptr.PointerByReference
import kotlinx.coroutines.delay
import java.awt.Window
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

private data class X11Data(
    val x11: X11,
    val display: X11.Display,
    val root: X11.Window,
    val netWmState: X11.Atom,
    val maxVertAtom: X11.Atom,
    val maxHorzAtom: X11.Atom,
)

/**
 * X11 fullscreen helper that requests `_NET_WM_STATE_MAXIMIZED_VERT | HORZ`
 * for the Gokfre preview window.
 */
object X11Fullscreen {
    private const val NET_WM_STATE_REMOVE = 0L
    private const val NET_WM_STATE_ADD = 1L
    private const val NO_DATA = 0L
    private const val CLIENT_MESSAGE_FORMAT = 32
    private const val MAX_ATTEMPTS = 3
    private const val REMOVE_SETTLE_DELAY_MS = 50L
    private const val ATTEMPT_POLL_INTERVAL_MS = 80L
    private const val FULLSCREEN_POLL_INTERVAL_MS = 50L
    private const val DATA_SLOT_FIRST_UNUSED = 3
    private const val MAX_STATE_ATOMS = 1024L

    private fun extractX11Data(
        x11: X11,
        display: X11.Display,
    ): X11Data =
        X11Data(
            x11 = x11,
            display = display,
            root = x11.XDefaultRootWindow(display),
            netWmState = x11.XInternAtom(display, "_NET_WM_STATE", false),
            maxVertAtom = x11.XInternAtom(display, "_NET_WM_STATE_MAXIMIZED_VERT", false),
            maxHorzAtom = x11.XInternAtom(display, "_NET_WM_STATE_MAXIMIZED_HORZ", false),
        )

    suspend fun setFullscreen(window: Window) {
        val x11 = X11.INSTANCE
        val display = x11.XOpenDisplay(null)
        if (display == null) {
            AwtFullscreen.setFullscreen(window)
            return
        }
        try {
            val atoms = extractX11Data(x11, display)
            val windowId = resolveWindowId(x11, display, atoms, window)
            if (windowId == null) {
                AwtFullscreen.setFullscreen(window)
                return
            }
            requestMaximizeState(atoms, windowId, add = false)
            x11.XFlush(display)
            delay(REMOVE_SETTLE_DELAY_MS.milliseconds)

            for (attempt in 1..MAX_ATTEMPTS) {
                requestMaximizeState(atoms, windowId, add = true)
                x11.XFlush(display)
                delay(ATTEMPT_POLL_INTERVAL_MS.milliseconds)
                if (isMaximized(atoms, windowId)) break
            }
        } finally {
            x11.XCloseDisplay(display)
        }
    }

    suspend fun waitForFullscreen(
        window: Window,
        timeoutMs: Long,
    ): Boolean {
        val x11 = X11.INSTANCE
        val display = x11.XOpenDisplay(null) ?: return false
        try {
            val atoms = extractX11Data(x11, display)
            val windowId = resolveWindowId(x11, display, atoms, window)
            val deadline = TimeSource.Monotonic.markNow() + timeoutMs.milliseconds
            var applied = false
            while (!applied && deadline.hasNotPassedNow()) {
                if (windowId != null && isMaximized(atoms, windowId)) {
                    applied = true
                } else {
                    delay(FULLSCREEN_POLL_INTERVAL_MS.milliseconds)
                }
            }
            return applied
        } finally {
            x11.XCloseDisplay(display)
        }
    }

    private fun resolveWindowId(
        x11: X11,
        display: X11.Display,
        atoms: X11Data,
        window: Window,
    ): Long? = x11WindowId(window) ?: findWindowByTitle(x11, display, atoms.root)

    private fun isMaximized(
        atoms: X11Data,
        windowId: Long,
    ): Boolean {
        val actualType = X11.AtomByReference()
        val actualFormat = IntByReference()
        val itemsReturn = NativeLongByReference()
        val bytesAfter = NativeLongByReference()
        val propertyValue = PointerByReference()
        atoms.x11.XGetWindowProperty(
            atoms.display,
            X11.Window(windowId),
            atoms.netWmState,
            NativeLong(0),
            NativeLong(MAX_STATE_ATOMS),
            false,
            X11.XA_ATOM,
            actualType,
            actualFormat,
            itemsReturn,
            bytesAfter,
            propertyValue,
        )
        val value = propertyValue.value ?: return false
        val atomCount = itemsReturn.value.toInt()
        var hasVert = false
        var hasHorz = false
        if (atomCount > 0) {
            val stateAtoms = value.getLongArray(0, atomCount)
            hasVert = stateAtoms.any { it == atoms.maxVertAtom.toLong() }
            hasHorz = stateAtoms.any { it == atoms.maxHorzAtom.toLong() }
        }
        atoms.x11.XFree(value)
        return hasVert && hasHorz
    }

    private fun requestMaximizeState(
        atoms: X11Data,
        windowId: Long,
        add: Boolean,
    ) {
        val event = X11.XEvent()
        event.setType(X11.XClientMessageEvent::class.java)
        event.xclient.type = X11.ClientMessage
        event.xclient.window = X11.Window(windowId)
        event.xclient.message_type = atoms.netWmState
        event.xclient.format = CLIENT_MESSAGE_FORMAT
        event.xclient.data.setType(Array<NativeLong>::class.java)
        val dataSlots = event.xclient.data.l
        dataSlots[0] = NativeLong(if (add) NET_WM_STATE_ADD else NET_WM_STATE_REMOVE)
        dataSlots[1] = NativeLong(atoms.maxVertAtom.toLong())
        dataSlots[2] = NativeLong(atoms.maxHorzAtom.toLong())
        for (i in DATA_SLOT_FIRST_UNUSED until dataSlots.size) {
            dataSlots[i] = NativeLong(NO_DATA)
        }

        val mask = NativeLong((X11.SubstructureRedirectMask or X11.SubstructureNotifyMask).toLong())
        atoms.x11.XSendEvent(atoms.display, atoms.root, 0, mask, event)
        atoms.x11.XSendEvent(atoms.display, X11.Window(windowId), 0, NativeLong(0), event)
        atoms.x11.XFlush(atoms.display)
    }

    private fun findWindowByTitle(
        x11: X11,
        display: X11.Display,
        root: X11.Window,
    ): Long? {
        if (matchesTitle(x11, display, root)) {
            return root.toLong()
        }
        return findInChildren(x11, display, root)
    }

    private fun matchesTitle(
        x11: X11,
        display: X11.Display,
        window: X11.Window,
    ): Boolean {
        val namePointer = PointerByReference()
        if (x11.XFetchName(display, window, namePointer) == 0 || namePointer.value == null) {
            return false
        }
        val name = namePointer.value.getString(0)
        x11.XFree(namePointer.value)
        return name == PREVIEW_WINDOW_TITLE
    }

    private fun findInChildren(
        x11: X11,
        display: X11.Display,
        window: X11.Window,
    ): Long? {
        val root = X11.WindowByReference()
        val parent = X11.WindowByReference()
        val childrenPointer = PointerByReference()
        val childrenCount = IntByReference()
        if (x11.XQueryTree(display, window, root, parent, childrenPointer, childrenCount) == 0) {
            return null
        }
        var result: Long? = null
        if (childrenPointer.value != null && childrenCount.value > 0) {
            val children = childrenPointer.value.getLongArray(0, childrenCount.value)
            for (i in 0 until childrenCount.value) {
                val childWindow = X11.Window(children[i])
                result = findWindowByTitle(x11, display, childWindow)
                if (result != null) {
                    break
                }
            }
        }
        return result
    }
}
