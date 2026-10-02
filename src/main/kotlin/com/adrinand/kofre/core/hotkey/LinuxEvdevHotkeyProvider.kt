package com.adrinand.kofre.core.hotkey

import com.adrinand.kofre.core.hotkey.KofreHotkeyNative.KofreHotkeyCallback
import com.sun.jna.Pointer
import javax.swing.SwingUtilities

/**
 * [GlobalHotkeyProvider] backed by the Rust `kbd-global` / evdev library.
 *
 * Works on Linux regardless of the display server (Wayland, X11, TTY).
 * Requires the user to have access to `/dev/input/event*` devices.
 */
class LinuxEvdevHotkeyProvider : GlobalHotkeyProvider {
    private var manager: Pointer? = null
    private var callback: KofreHotkeyCallback? = null

    init {
        val handle = KofreHotkeyNative.INSTANCE.kofre_hotkey_init()
        check(handle != Pointer.NULL) { "Failed to initialize kofre_hotkey native library" }
        manager = handle
    }

    override fun register(
        hotkey: Hotkey,
        callback: () -> Unit,
    ) {
        val currentManager = manager ?: error("Hotkey provider has been shut down")
        val nativeCallback =
            object : KofreHotkeyCallback {
                override fun invoke() {
                    System.err.println("LinuxEvdevHotkeyProvider: native callback fired")
                    SwingUtilities.invokeLater { callback.invoke() }
                }
            }
        this.callback = nativeCallback

        val combo = hotkey.toKbdGlobalString()
        val result = KofreHotkeyNative.INSTANCE.kofre_hotkey_register(currentManager, combo, nativeCallback)
        check(result == 0) { "Failed to register hotkey '$combo' (error $result)" }
    }

    override fun unregister(hotkey: Hotkey) {
        shutdown()
    }

    override fun dispose() {
        shutdown()
    }

    private fun shutdown() {
        manager?.let { KofreHotkeyNative.INSTANCE.kofre_hotkey_shutdown(it) }
        manager = null
        callback = null
    }
}

internal fun Hotkey.toKbdGlobalString(): String {
    val modifierNames =
        modifiers
            .map { modifier ->
                when (modifier) {
                    ModifierKey.SHIFT -> "Shift"
                    ModifierKey.CTRL -> "Ctrl"
                    ModifierKey.ALT -> "Alt"
                    ModifierKey.SUPER -> "Super"
                }
            }.sorted()
    val keyName = keyCode.toKbdGlobalKeyName()
    return if (modifierNames.isEmpty()) keyName else (modifierNames + keyName).joinToString("+")
}

private fun Int.toKbdGlobalKeyName(): String = KEY_CODE_TO_DISPLAY_NAME[this] ?: "Key$this"
