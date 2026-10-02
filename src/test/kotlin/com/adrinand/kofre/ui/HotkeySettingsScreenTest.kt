package com.adrinand.kofre.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import com.adrinand.kofre.core.hotkey.Hotkey
import com.adrinand.kofre.core.hotkey.ModifierKey
import com.adrinand.kofre.core.hotkey.toDisplayString
import com.adrinand.kofre.viewmodel.SettingsViewModel
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class HotkeySettingsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel: SettingsViewModel
        get() = SettingsViewModel(CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun `hotkey settings screen renders its subcomponents`() {
        val model = viewModel
        composeTestRule.setContent {
            HotkeySettingsScreen(viewModel = model)
        }

        composeTestRule.onNodeWithTag(SCREEN_TAG).assertExists()
        composeTestRule.onNodeWithTag(INPUT_TAG).assertExists()
        composeTestRule
            .onNodeWithText(model.toggleHotkey.toDisplayString())
            .assertIsDisplayed()
    }

    @Test
    fun `clicking capture input and entering a valid combo updates the toggle hotkey`() {
        val model = viewModel
        composeTestRule.setContent {
            HotkeySettingsScreen(viewModel = model)
        }

        composeTestRule.onNodeWithTag(INPUT_TAG).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onRoot().performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.ShiftLeft)
            keyDown(Key.A)
        }
        composeTestRule.waitForIdle()

        val expected = Hotkey(NativeKeyEvent.VC_A, setOf(ModifierKey.CTRL, ModifierKey.SHIFT))
        model.toggleHotkey shouldBe expected
        composeTestRule
            .onNodeWithText(expected.toDisplayString())
            .assertIsDisplayed()
    }

    @Test
    fun `clicking capture input and entering invalid keys does not update the toggle hotkey`() {
        val model = viewModel
        val originalHotkey = model.toggleHotkey

        composeTestRule.setContent {
            HotkeySettingsScreen(viewModel = model)
        }

        composeTestRule.onNodeWithTag(INPUT_TAG).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onRoot().performKeyInput {
            keyDown(Key.Escape)
        }
        composeTestRule.waitForIdle()

        model.toggleHotkey shouldBe originalHotkey
        composeTestRule
            .onNodeWithText(originalHotkey.toDisplayString())
            .assertIsDisplayed()
    }

    private companion object {
        const val SCREEN_TAG = "hotkey-settings-screen"
        const val INPUT_TAG = "hotkey-capture-input"
    }
}
