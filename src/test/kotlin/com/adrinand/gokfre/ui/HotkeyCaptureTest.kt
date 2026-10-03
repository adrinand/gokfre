package com.adrinand.gokfre.ui

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
import com.adrinand.gokfre.core.hotkey.Hotkey
import com.adrinand.gokfre.core.hotkey.ModifierKey
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class HotkeyCaptureTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `character key without modifier is ignored`() {
        val event = captureKeyEvent { keyDown(Key.A) }

        event shouldNotBe null
        captureHotkeyFromKeyEvent(event!!, emptySet()) shouldBe null
    }

    @Test
    fun `modifier only key is ignored`() {
        val event = captureKeyEvent { keyDown(Key.ShiftLeft) }

        event shouldNotBe null
        captureHotkeyFromKeyEvent(event!!, emptySet()) shouldBe null
    }

    @Test
    fun `non character or number key is ignored`() {
        val event = captureKeyEvent { keyDown(Key.Escape) }

        event shouldNotBe null
        captureHotkeyFromKeyEvent(event!!, emptySet()) shouldBe null
    }

    @Test
    fun `modifier plus character is captured`() {
        val event =
            captureKeyEvent {
                keyDown(Key.MetaLeft)
                keyDown(Key.K)
            }

        captureHotkeyFromKeyEvent(event!!, setOf(ModifierKey.SUPER)) shouldBe
            Hotkey(NativeKeyEvent.VC_K, setOf(ModifierKey.SUPER))
    }

    @Test
    fun `multiple modifiers plus character is captured`() {
        val event =
            captureKeyEvent {
                keyDown(Key.CtrlLeft)
                keyDown(Key.ShiftLeft)
                keyDown(Key.A)
            }

        captureHotkeyFromKeyEvent(event!!, setOf(ModifierKey.CTRL, ModifierKey.SHIFT)) shouldBe
            Hotkey(NativeKeyEvent.VC_A, setOf(ModifierKey.CTRL, ModifierKey.SHIFT))
    }

    @Test
    fun `key up event is ignored`() {
        val event =
            captureKeyEvent {
                keyDown(Key.A)
                keyUp(Key.A)
            }

        event shouldNotBe null
        captureHotkeyFromKeyEvent(event!!, emptySet()) shouldBe null
    }

    private fun captureKeyEvent(dispatch: androidx.compose.ui.test.KeyInjectionScope.() -> Unit): KeyEvent? {
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
                        .onPreviewKeyEvent { keyEvent ->
                            captured = keyEvent
                            true
                        },
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onRoot().performKeyInput(dispatch)
        composeTestRule.waitForIdle()

        return captured
    }
}
