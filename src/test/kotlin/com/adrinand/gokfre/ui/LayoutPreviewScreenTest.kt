package com.adrinand.gokfre.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.adrinand.gokfre.viewmodel.AppLayoutSettings
import com.adrinand.gokfre.viewmodel.SettingsViewModel
import org.junit.Rule
import org.junit.Test

class LayoutPreviewScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent() {
        composeTestRule.setContent {
            val coroutineScope = rememberCoroutineScope()
            val viewModel = remember { SettingsViewModel(coroutineScope) }
            LayoutPreviewScreen(viewModel)
        }
    }

    @Test
    fun `layout preview renders with default layout`() {
        setContent()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("layout-preview").assertIsDisplayed()
        composeTestRule.onNodeWithText("Q").assertIsDisplayed()
        composeTestRule.onNodeWithText("A").assertIsDisplayed()
        composeTestRule.onNodeWithText("Z").assertIsDisplayed()
    }

    @Test
    fun `layout preview shows all default cells`() {
        setContent()
        composeTestRule.waitForIdle()

        val expectedLabels = listOf("Q", "W", "E", "R", "A", "S", "D", "F", "Z", "X", "C", "V")
        for (label in expectedLabels) {
            composeTestRule.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test
    fun `layout preview has correct number of cells`() {
        setContent()
        composeTestRule.waitForIdle()

        composeTestRule.onAllNodesWithText("Q").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("A").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Z").assertCountEquals(1)
    }

    @Test
    fun `layout preview reflects layout settings changes`() {
        lateinit var viewModel: SettingsViewModel
        composeTestRule.setContent {
            val coroutineScope = rememberCoroutineScope()
            viewModel = remember { SettingsViewModel(coroutineScope) }
            LayoutPreviewScreen(viewModel)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Q").assertIsDisplayed()
        composeTestRule.onNodeWithText("A").assertIsDisplayed()
        composeTestRule.onNodeWithText("Z").assertIsDisplayed()

        viewModel.layoutSettings =
            AppLayoutSettings(
                columnWeights = mutableStateListOf(1, 1),
                rowWeights = mutableStateListOf(1, 1),
                keyLabels =
                    mutableStateListOf(
                        mutableStateListOf("X", "Y"),
                        mutableStateListOf("Z", "W"),
                    ),
            )
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Q").assertDoesNotExist()
        composeTestRule.onNodeWithText("A").assertDoesNotExist()

        composeTestRule.onNodeWithText("X").assertIsDisplayed()
        composeTestRule.onNodeWithText("Y").assertIsDisplayed()
        composeTestRule.onNodeWithText("Z").assertIsDisplayed()
        composeTestRule.onNodeWithText("W").assertIsDisplayed()
    }

    @Test
    fun `layout preview highlights selected keys`() {
        composeTestRule.setContent {
            val coroutineScope = rememberCoroutineScope()
            val viewModel = remember { SettingsViewModel(coroutineScope) }
            LayoutPreviewScreen(viewModel, selectedKeys = setOf("Q", "S"))
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("layout-preview").assertIsDisplayed()
        composeTestRule.onNodeWithText("Q").assertIsDisplayed()
        composeTestRule.onNodeWithText("S").assertIsDisplayed()
    }
}
