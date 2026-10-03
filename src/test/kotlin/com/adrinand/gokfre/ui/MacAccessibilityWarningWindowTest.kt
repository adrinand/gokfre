package com.adrinand.gokfre.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.kotest.matchers.shouldBe
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.GraphicsEnvironment

@OptIn(ExperimentalTestApi::class)
class MacAccessibilityWarningWindowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun requireDisplay() {
        assumeFalse("Skipping: headless environment", GraphicsEnvironment.isHeadless())
    }

    @Test
    fun `window shows warning with title and buttons`() {
        composeTestRule.setContent {
            MacAccessibilityWarningWindow(onDismiss = {}, onOpenSystemSettings = {})
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Accessibility permission required").assertIsDisplayed()
        composeTestRule.onNodeWithText("Open System Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("OK").assertIsDisplayed()
    }

    @Test
    fun `window buttons trigger callbacks`() {
        var dismissed = false
        var opened = false
        composeTestRule.setContent {
            MacAccessibilityWarningWindow(
                onDismiss = { dismissed = true },
                onOpenSystemSettings = { opened = true },
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Open System Settings").performClick()
        composeTestRule.onNodeWithText("OK").performClick()
        composeTestRule.waitForIdle()

        opened shouldBe true
        dismissed shouldBe true
    }
}
