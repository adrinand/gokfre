@file:Suppress("MagicNumber", "ktlint:standard:filename")

package com.adrinand.gokfre.core.screen

import com.sun.jna.NativeLong
import com.sun.jna.platform.unix.X11
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window

fun isLinux(): Boolean = System.getProperty("os.name").lowercase().contains("linux")

fun isX11Session(): Boolean = System.getenv("XDG_SESSION_TYPE")?.lowercase() == "x11"

fun Window.workAreaBounds(): Rectangle {
    val screenBounds = graphicsConfiguration.bounds
    val insets = Toolkit.getDefaultToolkit().getScreenInsets(graphicsConfiguration)
    return Rectangle(
        screenBounds.x + insets.left,
        screenBounds.y + insets.top,
        screenBounds.width - insets.left - insets.right,
        screenBounds.height - insets.top - insets.bottom,
    )
}

fun x11WindowId(window: Window): Long? {
    return try {
        val getPeer = java.awt.Component::class.java.getDeclaredMethod("getPeer")
        getPeer.isAccessible = true
        val peer = getPeer.invoke(window) ?: return null
        val getWindow = peer.javaClass.getMethod("getWindow")
        (getWindow.invoke(peer) as? Number)?.toLong()
    } catch (_: Exception) {
        null
    }
}

fun Window.skipTaskbarX11() {
    try {
        val x11 = X11.INSTANCE
        val display = x11.XOpenDisplay(null) ?: return
        try {
            val windowId = x11WindowId(this) ?: return
            val root = x11.XDefaultRootWindow(display)

            val netWmWindowType = x11.XInternAtom(display, "_NET_WM_WINDOW_TYPE", false)
            val netWmWindowTypeUtility = x11.XInternAtom(display, "_NET_WM_WINDOW_TYPE_UTILITY", false)
            val changeEvent = X11.XEvent()
            changeEvent.setType(X11.XClientMessageEvent::class.java)
            changeEvent.xclient.type = X11.ClientMessage
            changeEvent.xclient.window = X11.Window(windowId)
            changeEvent.xclient.message_type = netWmWindowType
            changeEvent.xclient.format = 32
            changeEvent.xclient.data.setType(Array<NativeLong>::class.java)
            changeEvent.xclient.data.l[0] = NativeLong(netWmWindowTypeUtility.toLong())
            changeEvent.xclient.data.l[1] = NativeLong(0)
            changeEvent.xclient.data.l[2] = NativeLong(0)
            changeEvent.xclient.data.l[3] = NativeLong(0)
            changeEvent.xclient.data.l[4] = NativeLong(0)
            val changeMask = NativeLong((X11.SubstructureRedirectMask or X11.SubstructureNotifyMask).toLong())
            x11.XSendEvent(display, root, 0, changeMask, changeEvent)

            val netWmState = x11.XInternAtom(display, "_NET_WM_STATE", false)
            val skipTaskbar = x11.XInternAtom(display, "_NET_WM_STATE_SKIP_TASKBAR", false)
            val skipPager = x11.XInternAtom(display, "_NET_WM_STATE_SKIP_PAGER", false)
            val stateEvent = X11.XEvent()
            stateEvent.setType(X11.XClientMessageEvent::class.java)
            stateEvent.xclient.type = X11.ClientMessage
            stateEvent.xclient.window = X11.Window(windowId)
            stateEvent.xclient.message_type = netWmState
            stateEvent.xclient.format = 32
            stateEvent.xclient.data.setType(Array<NativeLong>::class.java)
            stateEvent.xclient.data.l[0] = NativeLong(1)
            stateEvent.xclient.data.l[1] = NativeLong(skipTaskbar.toLong())
            stateEvent.xclient.data.l[2] = NativeLong(skipPager.toLong())
            stateEvent.xclient.data.l[3] = NativeLong(0)
            stateEvent.xclient.data.l[4] = NativeLong(0)
            val stateMask = NativeLong((X11.SubstructureRedirectMask or X11.SubstructureNotifyMask).toLong())
            x11.XSendEvent(display, root, 0, stateMask, stateEvent)

            x11.XFlush(display)
        } finally {
            x11.XCloseDisplay(display)
        }
    } catch (_: Exception) {
    }
}
