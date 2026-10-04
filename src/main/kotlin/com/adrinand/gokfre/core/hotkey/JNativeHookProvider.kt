package com.adrinand.gokfre.core.hotkey

import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.NativeHookException
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

private val logger = Logger.getLogger("com.adrinand.gokfre.core.hotkey.JNativeHookProvider")

class JNativeHookProvider : GlobalHotkeyProvider {
    private val registrations = ConcurrentHashMap<Hotkey, () -> Unit>()
    private val listener = ToggleListener()

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
        logger.fine { "Native key event: keyCode=${event.keyCode} modifiers=${event.modifiers}" }
        for ((hotkey, callback) in registrations) {
            if (hotkey.matches(event)) {
                logger.fine { "Hotkey matched: $hotkey" }
                callback.invoke()
                return
            }
        }
    }

    private fun Hotkey.matches(event: NativeKeyEvent): Boolean {
        if (event.keyCode != keyCode) {
            return false
        }
        return event.presentModifiers() == modifiers
    }

    private fun NativeKeyEvent.presentModifiers(): Set<ModifierKey> =
        buildSet {
            if (modifiers and NativeKeyEvent.SHIFT_MASK != 0) add(ModifierKey.SHIFT)
            if (modifiers and NativeKeyEvent.CTRL_MASK != 0) add(ModifierKey.CTRL)
            if (modifiers and NativeKeyEvent.ALT_MASK != 0) add(ModifierKey.ALT)
            if (modifiers and NativeKeyEvent.META_MASK != 0) add(ModifierKey.SUPER)
        }

    private inner class ToggleListener : NativeKeyListener {
        override fun nativeKeyPressed(event: NativeKeyEvent) {
            dispatch(event)
        }
    }
}
