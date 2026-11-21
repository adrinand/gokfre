package com.adrinand.gokfre.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import com.adrinand.gokfre.core.hotkey.Hotkey
import com.adrinand.gokfre.core.hotkey.KeyCode
import com.adrinand.gokfre.core.hotkey.ModifierKey
import com.adrinand.gokfre.core.hotkey.toDisplayString
import com.adrinand.gokfre.core.startup.StartupLauncher
import com.adrinand.gokfre.viewmodel.SettingsViewModel
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class GeneralSettingsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel: SettingsViewModel
        get() = SettingsViewModel(CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun `general settings screen renders its subcomponents`() {
        val model = viewModel
        composeTestRule.setContent {
            GeneralSettingsScreen(viewModel = model)
        }

        composeTestRule.onNodeWithTag(SCREEN_TAG).assertExists()
        composeTestRule.onNodeWithText("General Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Launch at startup").assertIsDisplayed()
        composeTestRule.onNodeWithTag(STARTUP_SWITCH_TAG).assertExists()
        composeTestRule.onNodeWithTag(INPUT_TAG).assertExists()
        composeTestRule
            .onNodeWithText(model.toggleHotkey.toDisplayString())
            .assertIsDisplayed()
    }

    @Test
    fun `startup switch reflects and updates the view model`() {
        val model = viewModel
        composeTestRule.setContent {
            GeneralSettingsScreen(viewModel = model)
        }

        composeTestRule.onNodeWithTag(STARTUP_SWITCH_TAG).assertIsOff()

        composeTestRule.onNodeWithTag(STARTUP_SWITCH_TAG).performClick()
        composeTestRule.waitForIdle()

        model.launchAtStartup shouldBe true
        composeTestRule.onNodeWithTag(STARTUP_SWITCH_TAG).assertIsOn()
    }

    @Test
    fun `startup failure shows an error dialog`() {
        val model =
            SettingsViewModel(
                CoroutineScope(Dispatchers.Unconfined),
                startupLauncher = FailingStartupLauncher,
                ioDispatcher = Dispatchers.Unconfined,
            )
        composeTestRule.setContent {
            GeneralSettingsScreen(viewModel = model)
        }

        composeTestRule.onNodeWithTag(STARTUP_SWITCH_TAG).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(STARTUP_ERROR_DIALOG_TAG).assertExists()
        composeTestRule.onNodeWithText(FAILURE_MESSAGE).assertIsDisplayed()
    }

    @Test
    fun `clicking capture input and entering a valid combo updates the toggle hotkey`() {
        val model = viewModel
        composeTestRule.setContent {
            GeneralSettingsScreen(viewModel = model)
        }

        composeTestRule.onNodeWithTag(INPUT_TAG).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onRoot().performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.ShiftLeft)
            keyDown(Key.A)
        }
        composeTestRule.waitForIdle()

        val expected = Hotkey(KeyCode.VC_A, setOf(ModifierKey.CTRL, ModifierKey.SHIFT))
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
            GeneralSettingsScreen(viewModel = model)
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

    private object FailingStartupLauncher : StartupLauncher {
        override fun isEnabled(): Boolean = false

        override fun enable(): Result<Unit> = Result.failure(IllegalStateException(FAILURE_MESSAGE))

        override fun disable(): Result<Unit> = Result.success(Unit)
    }

    private companion object {
        const val SCREEN_TAG = "general-settings-screen"
        const val INPUT_TAG = "hotkey-capture-input"
        const val STARTUP_SWITCH_TAG = "launch-at-startup-switch"
        const val STARTUP_ERROR_DIALOG_TAG = "startup-error-dialog"
        const val FAILURE_MESSAGE = "Launcher unavailable"
    }
}
