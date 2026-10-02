package com.adrinand.kofre.core.hotkey

import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import com.adrinand.kofre.common.NUMBER_FIFTY_SEVEN
import com.adrinand.kofre.common.NUMBER_FORTY_EIGHT
import com.adrinand.kofre.common.NUMBER_NINTY
import com.adrinand.kofre.common.NUMBER_SIXTY_FIVE
import java.awt.event.KeyEvent as AwtKeyEvent

fun KeyEvent.getDisplayCharFromKeyEvent(): String? {
    awtEventOrNull?.let { awtEvent ->
        return AwtKeyEvent
            .getKeyText(awtEvent.keyCode)
            .takeIf { it.length == 1 && it[0].isLetterOrDigit() }
            ?.uppercase()
    }

    return when (val code = key.nativeKeyCode) {
        in NUMBER_SIXTY_FIVE..NUMBER_NINTY -> code.toChar().toString().uppercase()
        in NUMBER_FORTY_EIGHT..NUMBER_FIFTY_SEVEN -> code.toChar().toString()
        else -> null
    }
}

fun Key.toModifierKey(): ModifierKey? =
    when {
        this == Key.CtrlLeft || this == Key.CtrlRight -> ModifierKey.CTRL
        this == Key.ShiftLeft || this == Key.ShiftRight -> ModifierKey.SHIFT
        this == Key.AltLeft || this == Key.AltRight -> ModifierKey.ALT
        this == Key.MetaLeft || this == Key.MetaRight -> ModifierKey.SUPER
        nativeKeyCode == AwtKeyEvent.VK_WINDOWS -> ModifierKey.SUPER
        else -> null
    }
