package com.adrinand.gokfre.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.adrinand.gokfre.core.screen.LinuxRequirement
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.GraphicsEnvironment

@OptIn(ExperimentalTestApi::class)
class LinuxWarningWindowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun requireDisplay() {
        assumeFalse("Skipping: headless environment", GraphicsEnvironment.isHeadless())
    }

    @Test
    fun `window shows the warning for a missing requirement`() {
        composeTestRule.setContent {
            LinuxWarningWindow(
                requirements = listOf(LinuxRequirement.UNSUPPORTED_WAYLAND_COMPOSITOR),
                onDismiss = {},
                utilityWindowActivator = null,
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Limited Wayland support").assertIsDisplayed()
        composeTestRule.onNodeWithText("OK").assertIsDisplayed()
    }

    @Test
    fun `nothing is rendered without requirements`() {
        composeTestRule.setContent {
            LinuxWarningWindow(
                requirements = emptyList(),
                onDismiss = {},
                utilityWindowActivator = null,
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Limited Wayland support").assertDoesNotExist()
    }
}
