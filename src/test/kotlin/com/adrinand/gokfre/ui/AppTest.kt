package com.adrinand.gokfre.ui

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.adrinand.gokfre.viewmodel.SettingsViewModel
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class AppTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `app mounts and renders layout preview`() {
        composeTestRule.setContent {
            val coroutineScope = rememberCoroutineScope()
            App(remember { SettingsViewModel(coroutineScope) })
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("layout-preview").assertIsDisplayed()
        composeTestRule.onNodeWithText("Q").assertIsDisplayed()
        composeTestRule.onNodeWithText("Z").assertIsDisplayed()
    }
}
