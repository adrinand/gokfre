package com.adrinand.kofre.core.hotkey

import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.NativeHookException
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

class JNativeHookProvider : GlobalHotkeyProvider {
    private val registrations = ConcurrentHashMap<Hotkey, () -> Unit>()
    private val listener = ToggleListener()

    private val Hotkey.modifierMask: Int
        get() = modifiers.fold(0) { mask, modifier -> mask or modifier.toNativeMask() }

    init {
        Logger.getLogger(GlobalScreen::class.java.`package`.name).level = Level.WARNING
        try {
            GlobalScreen.registerNativeHook()
        } catch (e: NativeHookException) {
            throw IllegalStateException("Failed to register global native hook", e)
        }
        GlobalScreen.addNativeKeyListener(listener)
    }

    override fun register(
        hotkey: Hotkey,
        callback: () -> Unit,
    ) {
        registrations[hotkey] = callback
    }

    override fun unregister(hotkey: Hotkey) {
        registrations.remove(hotkey)
    }

    override fun dispose() {
        GlobalScreen.removeNativeKeyListener(listener)
        try {
            GlobalScreen.unregisterNativeHook()
        } catch (e: NativeHookException) {
            println("JNativeHookProvider: failed to unregister native hook: ${e.message}")
        }
    }

    private fun dispatch(event: NativeKeyEvent) {
        for ((hotkey, callback) in registrations) {
            if (event.keyCode == hotkey.keyCode && event.modifiers and hotkey.modifierMask == hotkey.modifierMask) {
                callback.invoke()
                return
            }
        }
    }

    private fun ModifierKey.toNativeMask(): Int =
        when (this) {
            ModifierKey.SHIFT -> NativeKeyEvent.SHIFT_MASK
            ModifierKey.CTRL -> NativeKeyEvent.CTRL_MASK
            ModifierKey.ALT -> NativeKeyEvent.ALT_MASK
            ModifierKey.SUPER -> NativeKeyEvent.META_MASK
        }

    private inner class ToggleListener : NativeKeyListener {
        override fun nativeKeyPressed(event: NativeKeyEvent) {
            dispatch(event)
        }
    }
}
