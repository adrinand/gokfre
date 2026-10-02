package com.adrinand.kofre.core.screen

import com.sun.jna.Native
import com.sun.jna.platform.unix.X11

/**
 * Extension of the JNA X11 mapping that adds functions not declared in
 * [com.sun.jna.platform.unix.X11].
 */
interface X11Extra : X11 {
    /**
     * Changes the size and location of the specified window.
     *
     * See `XMoveResizeWindow(3)`.
     */
    @Suppress("FunctionNaming", "ktlint:standard:function-naming")
    fun XMoveResizeWindow(
        display: X11.Display,
        window: X11.Window,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    )

    companion object {
        val INSTANCE: X11Extra by lazy {
            Native.load("X11", X11Extra::class.java)
        }
    }
}
