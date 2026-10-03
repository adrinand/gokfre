package com.adrinand.gokfre.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.adrinand.gokfre.core.hotkey.DISPLAY_NAME_TO_KEY_CODE
import com.adrinand.gokfre.core.hotkey.Hotkey
import com.adrinand.gokfre.core.hotkey.ModifierKey
import com.adrinand.gokfre.core.hotkey.getDisplayCharFromKeyEvent
import com.adrinand.gokfre.core.hotkey.toDisplayString
import com.adrinand.gokfre.core.hotkey.toModifierKey

@Composable
fun HotkeyCaptureInput(
    hotkey: Hotkey,
    onHotkeyCaptured: (Hotkey) -> Unit,
    onCapturingChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var isCapturing by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val pressedModifiers = remember { mutableStateListOf<ModifierKey>() }

    LaunchedEffect(isCapturing) {
        if (isCapturing) {
            focusRequester.requestFocus()
        } else {
            pressedModifiers.clear()
        }
        onCapturingChanged(isCapturing)
    }

    Box(
        modifier =
            modifier
                .testTag("hotkey-capture-input")
                .focusRequester(focusRequester)
                .onKeyEvent { event ->
                    if (!isCapturing) {
                        return@onKeyEvent false
                    }

                    if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                        isCapturing = false
                        return@onKeyEvent true
                    }

                    val modifierKey = event.key.toModifierKey()
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            if (modifierKey != null) {
                                pressedModifiers.add(modifierKey)
                                return@onKeyEvent true
                            }

                            val modifiers = pressedModifiers.toMutableSet()
                            if (event.isCtrlPressed) modifiers.add(ModifierKey.CTRL)
                            if (event.isShiftPressed) modifiers.add(ModifierKey.SHIFT)
                            if (event.isAltPressed) modifiers.add(ModifierKey.ALT)
                            if (event.isMetaPressed) modifiers.add(ModifierKey.SUPER)

                            val captured = captureHotkeyFromKeyEvent(event, modifiers)
                            if (captured != null) {
                                onHotkeyCaptured(captured)
                                isCapturing = false
                                return@onKeyEvent true
                            }
                        }

                        KeyEventType.KeyUp -> {
                            if (modifierKey != null) {
                                pressedModifiers.remove(modifierKey)
                                return@onKeyEvent true
                            }
                        }

                        else -> Unit
                    }

                    false
                }
                .focusable()
                .clickable { isCapturing = true }
                .border(
                    width = 1.dp,
                    color = if (isCapturing) MaterialTheme.colors.primary else Color.Gray,
                    shape = RoundedCornerShape(4.dp),
                )
                .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text =
                if (isCapturing) {
                    "Press modifier + key..."
                } else {
                    hotkey.toDisplayString()
                },
        )
    }
}

internal fun captureHotkeyFromKeyEvent(
    event: KeyEvent,
    modifiers: Set<ModifierKey>,
): Hotkey? {
    val displayName = event.getDisplayCharFromKeyEvent()
    val keyCode = displayName?.let { DISPLAY_NAME_TO_KEY_CODE[it] }
    val isValidKeyDown = event.type == KeyEventType.KeyDown && keyCode != null

    return if (isValidKeyDown && modifiers.isNotEmpty()) {
        Hotkey(keyCode, modifiers)
    } else {
        null
    }
}
