package com.adrinand.gokfre.ui

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import com.adrinand.gokfre.viewmodel.SettingsViewModel
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class LayoutSettingsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContentWithLayoutSettings() {
        composeTestRule.setContent {
            val coroutineScope = rememberCoroutineScope()
            val viewModel = remember { SettingsViewModel(coroutineScope) }
            LayoutSettingsScreen(viewModel)
        }
    }

    @Test
    fun clickingColumnIncrementButtonIncreasesWeightNumber() {
        setContentWithLayoutSettings()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("2").assertDoesNotExist()

        composeTestRule.onNodeWithTag("col-0-plus").performClick()
        composeTestRule.onNodeWithText("2").assertExists()

        composeTestRule.onNodeWithTag("col-0-minus").performClick()
        composeTestRule.onNodeWithText("2").assertDoesNotExist()
    }

    @Test
    fun decrementRowWeightToZeroRemovesRow() {
        setContentWithLayoutSettings()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("row-0-minus").performClick()
        composeTestRule.onNodeWithText("Q").assertDoesNotExist()
        composeTestRule.onNodeWithText("A").assertExists()
        composeTestRule.onNodeWithText("Z").assertExists()
    }

    @Test
    fun pressingKeyUpdatesSelectedTile() {
        setContentWithLayoutSettings()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("layout-screen").performClick()

        composeTestRule.onNodeWithText("Q").performClick()
        composeTestRule.onRoot().performKeyInput { keyDown(Key.P) }

        composeTestRule.onNodeWithText("P").assertExists()
        composeTestRule.onNodeWithText("Q").assertDoesNotExist()
    }

    @Test
    fun duplicateKeyShowsDialog() {
        setContentWithLayoutSettings()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("layout-screen").performClick()

        composeTestRule.onNodeWithText("Q").performClick()
        composeTestRule.onRoot().performKeyInput { keyDown(Key.A) }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Error").assertExists()
        composeTestRule
            .onNodeWithText(
                "Selected key is already added to the layout.\n" +
                    "Please, replace current position with another key and try again.",
            ).assertExists()

        composeTestRule.onNodeWithText("OK").performClick()
        composeTestRule.onNodeWithText("Error").assertDoesNotExist()
        composeTestRule.onNodeWithText("Q").assertExists()
    }

    @Test
    fun addingRowWorks() {
        setContentWithLayoutSettings()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Add Row").performClick()
        composeTestRule.onAllNodesWithText("?").assertCountEquals(4)
    }
}
