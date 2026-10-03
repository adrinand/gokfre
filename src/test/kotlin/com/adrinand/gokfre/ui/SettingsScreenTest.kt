package com.adrinand.gokfre.ui

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.adrinand.gokfre.viewmodel.SettingsViewModel
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SettingsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContentWithSettings() {
        composeTestRule.setContent {
            val coroutineScope = rememberCoroutineScope()
            val viewModel = remember { SettingsViewModel(coroutineScope) }
            SettingsScreen(viewModel)
        }
    }

    @Test
    fun `settings screen renders layout settings by default`() {
        setContentWithSettings()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(LAYOUT_SCREEN_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(HOTKEY_SCREEN_TAG).assertDoesNotExist()
    }

    @Test
    fun `settings screen renders hotkey settings when hotkey tab is selected`() {
        setContentWithSettings()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(HOTKEY_TAB_TAG).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(HOTKEY_SCREEN_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(LAYOUT_SCREEN_TAG).assertDoesNotExist()
    }

    @Test
    fun `settings screen returns to layout settings when layout tab is selected`() {
        setContentWithSettings()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(HOTKEY_TAB_TAG).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(LAYOUT_TAB_TAG).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(LAYOUT_SCREEN_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(HOTKEY_SCREEN_TAG).assertDoesNotExist()
    }

    private companion object {
        const val LAYOUT_SCREEN_TAG = "layout-screen"
        const val HOTKEY_SCREEN_TAG = "hotkey-settings-screen"
        const val LAYOUT_TAB_TAG = "layout-tab"
        const val HOTKEY_TAB_TAG = "hotkey-tab"
    }
}
