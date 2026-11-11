package com.adrinand.gokfre.core.screen

import java.awt.Rectangle

internal interface MacWindowSource {
    fun focusedApplicationPid(): Long?

    fun focusedElementPid(): Long?

    fun hasFocusedWindow(pid: Long): Boolean

    fun frontmostWindowPid(selfPid: Long): Long?

    fun windowTitle(pid: Long): String?

    fun windowBounds(pid: Long): Rectangle?

    fun setBounds(
        pid: Long,
        bounds: Rectangle,
    )

    fun raise(pid: Long)
}
