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
class WarningDialogTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `dialog renders every line and the action`() {
        composeTestRule.setContent {
            WarningDialog(content = contentWithAction(), onDismiss = {}, onAction = {})
        }

        composeTestRule.onNodeWithText("Section title").assertIsDisplayed()
        composeTestRule.onNodeWithText("Body line").assertIsDisplayed()
        composeTestRule.onNodeWithText("Emphasis line").assertIsDisplayed()
        composeTestRule.onNodeWithText("code line").assertIsDisplayed()
        composeTestRule.onNodeWithText(FIX_LABEL).assertIsDisplayed()
        composeTestRule.onNodeWithText(OK_LABEL).assertIsDisplayed()
    }

    @Test
    fun `action and dismiss buttons trigger callbacks`() {
        var actionTriggered = false
        var dismissed = false
        composeTestRule.setContent {
            WarningDialog(
                content = contentWithAction(),
                onDismiss = { dismissed = true },
                onAction = { actionTriggered = true },
            )
        }

        composeTestRule.onNodeWithText(FIX_LABEL).performClick()
        composeTestRule.onNodeWithText(OK_LABEL).performClick()
        composeTestRule.waitForIdle()

        actionTriggered shouldBe true
        dismissed shouldBe true
    }

    @Test
    fun `action button is hidden without a callback`() {
        composeTestRule.setContent {
            WarningDialog(content = contentWithAction(), onDismiss = {}, onAction = null)
        }

        composeTestRule.onNodeWithText(FIX_LABEL).assertDoesNotExist()
        composeTestRule.onNodeWithText(OK_LABEL).assertIsDisplayed()
    }

    private fun contentWithAction(): WarningContent =
        WarningContent(
            windowTitle = "Test",
            sections =
                listOf(
                    WarningSection(
                        title = "Section title",
                        lines =
                            listOf(
                                WarningLine("Body line"),
                                WarningLine("Emphasis line", WarningLineStyle.Emphasis),
                                WarningLine("code line", WarningLineStyle.Code),
                            ),
                    ),
                ),
            actionLabel = FIX_LABEL,
        )

    private companion object {
        const val FIX_LABEL = "Fix it"
        const val OK_LABEL = "OK"
    }
}
