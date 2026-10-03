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
class MacAccessibilityWarningDialogTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `dialog shows warning title and buttons`() {
        composeTestRule.setContent {
            MacAccessibilityWarningDialog(onDismiss = {}, onOpenSystemSettings = {})
        }

        composeTestRule.onNodeWithText("Accessibility permission required").assertIsDisplayed()
        composeTestRule.onNodeWithText("Open System Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("OK").assertIsDisplayed()
    }

    @Test
    fun `open system settings button triggers callback`() {
        var opened = false
        composeTestRule.setContent {
            MacAccessibilityWarningDialog(onDismiss = {}, onOpenSystemSettings = { opened = true })
        }

        composeTestRule.onNodeWithText("Open System Settings").performClick()
        composeTestRule.waitForIdle()

        opened shouldBe true
    }

    @Test
    fun `OK button triggers onDismiss`() {
        var dismissed = false
        composeTestRule.setContent {
            MacAccessibilityWarningDialog(onDismiss = { dismissed = true }, onOpenSystemSettings = {})
        }

        composeTestRule.onNodeWithText("OK").performClick()
        composeTestRule.waitForIdle()

        dismissed shouldBe true
    }
}
