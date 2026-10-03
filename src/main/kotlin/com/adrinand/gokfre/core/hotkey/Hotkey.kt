package com.adrinand.gokfre.core.hotkey

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import kotlinx.serialization.Serializable

@Serializable
data class Hotkey(
    val keyCode: Int,
    val modifiers: Set<ModifierKey> = emptySet(),
) {
    companion object {
        val DEFAULT_TOGGLE =
            Hotkey(
                keyCode = NativeKeyEvent.VC_K,
                modifiers = setOf(ModifierKey.SUPER),
            )
    }
}

fun Hotkey.toDisplayString(): String {
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
    val keyName = KEY_CODE_TO_DISPLAY_NAME[keyCode] ?: "Key$keyCode"
    return if (modifierNames.isEmpty()) keyName else (modifierNames + keyName).joinToString("+")
}
