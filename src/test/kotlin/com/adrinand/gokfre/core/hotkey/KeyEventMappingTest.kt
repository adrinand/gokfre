package com.adrinand.gokfre.core.hotkey

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class KeyEventMappingTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `letter key event maps to uppercase letter`() {
        val captured = captureKeyEvent { keyDown(Key.P) }
        captured.getDisplayCharFromKeyEvent() shouldBe "P"
    }

    @Test
    fun `digit key event maps to digit`() {
        val captured = captureKeyEvent { keyDown(Key.Eight) }
        captured.getDisplayCharFromKeyEvent() shouldBe "8"
    }

    @Test
    fun `Ctrl keys map to Ctrl modifier`() {
        Key.CtrlLeft.toModifierKey() shouldBe ModifierKey.CTRL
        Key.CtrlRight.toModifierKey() shouldBe ModifierKey.CTRL
    }

    @Test
    fun `Shift keys map to Shift modifier`() {
        Key.ShiftLeft.toModifierKey() shouldBe ModifierKey.SHIFT
        Key.ShiftRight.toModifierKey() shouldBe ModifierKey.SHIFT
    }

    @Test
    fun `Alt keys map to Alt modifier`() {
        Key.AltLeft.toModifierKey() shouldBe ModifierKey.ALT
        Key.AltRight.toModifierKey() shouldBe ModifierKey.ALT
    }

    @Test
    fun `Meta keys map to Super modifier`() {
        Key.MetaLeft.toModifierKey() shouldBe ModifierKey.SUPER
        Key.MetaRight.toModifierKey() shouldBe ModifierKey.SUPER
    }

    @Test
    fun `regular key does not map to modifier`() {
        Key.A.toModifierKey() shouldBe null
    }

    private fun captureKeyEvent(dispatch: androidx.compose.ui.test.KeyInjectionScope.() -> Unit): KeyEvent {
        var captured: KeyEvent? = null
        composeTestRule.setContent {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .focusRequester(focusRequester)
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            captured = event
                            true
                        },
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onRoot().performKeyInput(dispatch)
        composeTestRule.waitForIdle()

        return captured ?: error("No key event captured")
    }
}
