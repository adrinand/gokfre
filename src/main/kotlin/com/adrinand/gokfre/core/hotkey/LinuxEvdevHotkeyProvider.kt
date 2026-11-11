package com.adrinand.gokfre.core.hotkey

import com.adrinand.gokfre.core.hotkey.GokfreHotkeyNative.GokfreHotkeyCallback
import com.sun.jna.Pointer
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * [GlobalHotkeyProvider] backed by the Rust `kbd-global` / evdev library.
 *
 * Works on Linux regardless of the display server (Wayland, X11, TTY).
 * Requires the user to have access to `/dev/input/event*` devices.
 */
class LinuxEvdevHotkeyProvider(
    private val debounceMs: Int = DEFAULT_DEBOUNCE_MS,
) : GlobalHotkeyProvider {
    private var manager: Pointer? = null
    private var nativeCallback: GokfreHotkeyCallback? = null
    private var currentCallback: (() -> Unit)? = null
    private var debounceTimer: Timer? = null
    private val pending = AtomicBoolean(false)

    init {
        reinitialize()
    }

    override fun register(
        hotkey: Hotkey,
        callback: () -> Unit,
    ) {
        val currentManager = manager ?: reinitialize()
        currentCallback = callback

        val callbackToRegister =
            nativeCallback ?: object : GokfreHotkeyCallback {
                override fun invoke() {
                    System.err.println("LinuxEvdevHotkeyProvider: native callback fired")
                    // The evdev grab+forward path can deliver the same physical event twice
                    // (physical device + forwarded virtual device). Coalesce rapid callbacks
                    // so a single key press produces exactly one toggle.
                    if (!pending.compareAndSet(false, true)) {
                        return
                    }
                    debounceTimer?.stop()
                    debounceTimer =
                        Timer(debounceMs) {
                            pending.set(false)
                            SwingUtilities.invokeLater { currentCallback?.invoke() }
                        }.apply {
                            isRepeats = false
                            start()
                        }
                }
            }.also { nativeCallback = it }

        val combo = hotkey.toKbdGlobalString()
        val result = GokfreHotkeyNative.INSTANCE.gokfre_hotkey_register(currentManager, combo, callbackToRegister)
        check(result == 0) { "Failed to register hotkey '$combo' (error $result)" }
    }

    override fun unregister(hotkey: Hotkey) {
        shutdown()
    }

    override fun dispose() {
        shutdown()
    }

    private fun reinitialize(): Pointer {
        val handle = GokfreHotkeyNative.INSTANCE.gokfre_hotkey_init()
        check(handle != Pointer.NULL) { "Failed to initialize gokfre_hotkey native library" }
        manager = handle
        return handle
    }

    private fun shutdown() {
        debounceTimer?.stop()
        debounceTimer = null
        pending.set(false)
        manager?.let { GokfreHotkeyNative.INSTANCE.gokfre_hotkey_shutdown(it) }
        manager = null
        nativeCallback = null
        currentCallback = null
    }

    companion object {
        private const val DEFAULT_DEBOUNCE_MS = 100
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
