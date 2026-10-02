package com.adrinand.kofre.core.hotkey

object HeadlessGlobalHotkeyProvider : GlobalHotkeyProvider {
    override fun register(
        hotkey: Hotkey,
        callback: () -> Unit,
    ) {
        // no-op
    }

    override fun unregister(hotkey: Hotkey) {
        // no-op
    }

    override fun dispose() {
        // no-op
    }
}
