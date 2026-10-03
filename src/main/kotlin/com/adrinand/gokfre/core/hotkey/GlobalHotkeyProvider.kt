package com.adrinand.gokfre.core.hotkey

interface GlobalHotkeyProvider : AutoCloseable {
    fun register(
        hotkey: Hotkey,
        callback: () -> Unit,
    )

    fun unregister(hotkey: Hotkey)

    fun dispose()

    override fun close() = dispose()
}
