package com.adrinand.gokfre.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class InputPermissionWarningDialogTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `dialog shows warning title and instructions`() {
        composeTestRule.setContent {
            InputPermissionWarningDialog(onDismiss = {})
        }

        composeTestRule.onNodeWithText("Input device access required").assertIsDisplayed()
        composeTestRule.onNodeWithText("OK").assertIsDisplayed()
    }

    @Test
    fun `OK button triggers onDismiss`() {
        var dismissed = false
        composeTestRule.setContent {
            InputPermissionWarningDialog(onDismiss = { dismissed = true })
        }

        composeTestRule.onNodeWithText("OK").performClick()
        composeTestRule.waitForIdle()

        dismissed shouldBe true
    }
}
