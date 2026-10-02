package com.adrinand.kofre.core.screen

import com.sun.jna.NativeLong
import com.sun.jna.platform.unix.X11
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.NativeLongByReference
import com.sun.jna.ptr.PointerByReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Rectangle
import java.awt.Window
import java.util.logging.Logger

/**
 * [WindowManager] implementation backed by X11.
 *
 * Reads the active window from the `_NET_ACTIVE_WINDOW` root property and
 * applies bounds with [X11Extra.XMoveResizeWindow].
 */
object X11WindowManager : SingleBackendWindowManager<WindowHandle.X11>() {
    override val logger = Logger.getLogger("com.adrinand.kofre.core.screen.X11WindowManager")

    private const val CLIENT_MESSAGE_FORMAT = 32
    private const val NET_WM_STATE_REMOVE = 0L
    private const val NO_DATA = 0L
    private const val MAX_PROPERTY_ITEMS = 1L
    private const val MIN_WINDOW_DIMENSION = 1
    private const val FIRST_UNUSED_DATA_SLOT = 3
    private const val UNSIGNED_INT_MASK = 0xFFFFFFFFL
    private const val ACTIVE_WINDOW_SOURCE_APPLICATION = 1L

    private data class X11Atoms(
        val x11: X11,
        val display: X11.Display,
        val root: X11.Window,
        val netWmState: X11.Atom,
        val maxVert: X11.Atom,
        val maxHorz: X11.Atom,
    )

    override fun castHandle(window: WindowHandle): WindowHandle.X11? = window as? WindowHandle.X11

    override fun retrieveActiveWindow(): WindowHandle.X11? {
        val x11 = X11.INSTANCE
        val display = x11.XOpenDisplay(null) ?: return null
        return try {
            val root = x11.XDefaultRootWindow(display)
            val netActiveWindow = x11.XInternAtom(display, "_NET_ACTIVE_WINDOW", false)
            readWindowProperty(x11, display, root, netActiveWindow)?.let { WindowHandle.X11(it) }
        } finally {
            x11.XCloseDisplay(display)
        }
    }

    override fun applyBounds(
        handle: WindowHandle.X11,
        bounds: Rectangle,
    ) {
        val x11 = X11.INSTANCE
        val display = x11.XOpenDisplay(null) ?: return
        try {
            val atoms =
                X11Atoms(
                    x11 = x11,
                    display = display,
                    root = x11.XDefaultRootWindow(display),
                    netWmState = x11.XInternAtom(display, "_NET_WM_STATE", false),
                    maxVert = x11.XInternAtom(display, "_NET_WM_STATE_MAXIMIZED_VERT", false),
                    maxHorz = x11.XInternAtom(display, "_NET_WM_STATE_MAXIMIZED_HORZ", false),
                )

            removeMaximizedState(atoms, handle.id)

            X11Extra.INSTANCE.XMoveResizeWindow(
                display,
                X11.Window(handle.id),
                bounds.x,
                bounds.y,
                bounds.width.coerceAtLeast(MIN_WINDOW_DIMENSION),
                bounds.height.coerceAtLeast(MIN_WINDOW_DIMENSION),
            )
            x11.XFlush(display)
        } finally {
            x11.XCloseDisplay(display)
        }
    }

    override fun focusHandle(handle: WindowHandle.X11) {
        val x11 = X11.INSTANCE
        val display = x11.XOpenDisplay(null) ?: return
        try {
            val root = x11.XDefaultRootWindow(display)
            val netActiveWindow = x11.XInternAtom(display, "_NET_ACTIVE_WINDOW", false)
            val event = X11.XEvent()
            event.setType(X11.XClientMessageEvent::class.java)
            event.xclient.type = X11.ClientMessage
            event.xclient.window = X11.Window(handle.id)
            event.xclient.message_type = netActiveWindow
            event.xclient.format = CLIENT_MESSAGE_FORMAT
            event.xclient.data.setType(Array<NativeLong>::class.java)
            val data = event.xclient.data.l
            data[0] = NativeLong(ACTIVE_WINDOW_SOURCE_APPLICATION)
            data[1] = NativeLong(X11.CurrentTime.toLong())
            for (i in FIRST_UNUSED_DATA_SLOT until data.size) {
                data[i] = NativeLong(NO_DATA)
            }
            val mask = NativeLong((X11.SubstructureRedirectMask or X11.SubstructureNotifyMask).toLong())
            x11.XSendEvent(display, root, 0, mask, event)
            x11.XFlush(display)
        } finally {
            x11.XCloseDisplay(display)
        }
    }

    override suspend fun enterFullscreen(
        window: Window,
        timeoutMs: Long,
    ): Boolean =
        withContext(Dispatchers.IO) {
            X11Fullscreen.setFullscreen(window)
            X11Fullscreen.waitForFullscreen(window, timeoutMs)
        }

    private fun removeMaximizedState(
        atoms: X11Atoms,
        windowId: Long,
    ) {
        val event = X11.XEvent()
        event.setType(X11.XClientMessageEvent::class.java)
        event.xclient.type = X11.ClientMessage
        event.xclient.window = X11.Window(windowId)
        event.xclient.message_type = atoms.netWmState
        event.xclient.format = CLIENT_MESSAGE_FORMAT
        event.xclient.data.setType(Array<NativeLong>::class.java)
        val data = event.xclient.data.l
        data[0] = NativeLong(NET_WM_STATE_REMOVE)
        data[1] = NativeLong(atoms.maxVert.toLong())
        data[2] = NativeLong(atoms.maxHorz.toLong())
        for (i in FIRST_UNUSED_DATA_SLOT until data.size) {
            data[i] = NativeLong(NO_DATA)
        }
        val mask = NativeLong((X11.SubstructureRedirectMask or X11.SubstructureNotifyMask).toLong())
        atoms.x11.XSendEvent(atoms.display, atoms.root, 0, mask, event)
        atoms.x11.XFlush(atoms.display)
    }

    private fun readWindowProperty(
        x11: X11,
        display: X11.Display,
        window: X11.Window,
        property: X11.Atom,
    ): Long? {
        val actualType = X11.AtomByReference()
        val actualFormat = IntByReference()
        val itemCount = NativeLongByReference()
        val bytesAfter = NativeLongByReference()
        val propertyValue = PointerByReference()
        x11.XGetWindowProperty(
            display,
            window,
            property,
            NativeLong(0),
            NativeLong(MAX_PROPERTY_ITEMS),
            false,
            X11.XA_WINDOW,
            actualType,
            actualFormat,
            itemCount,
            bytesAfter,
            propertyValue,
        )
        val value = propertyValue.value ?: return null
        return try {
            if (itemCount.value.toInt() > 0) value.getInt(0).toLong() and UNSIGNED_INT_MASK else null
        } finally {
            x11.XFree(value)
        }
    }
}
