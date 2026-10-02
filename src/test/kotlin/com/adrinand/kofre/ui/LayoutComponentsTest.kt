package com.adrinand.kofre.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class LayoutComponentsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `weight control renders value with increment and decrement buttons`() {
        composeTestRule.setContent {
            WeightControl(
                value = 2,
                onIncrement = {},
                onDecrement = {},
                modifier = Modifier,
                testTag = WEIGHT_TAG,
            )
        }

        composeTestRule.onNodeWithText("2").assertIsDisplayed()
        composeTestRule.onNodeWithTag(WEIGHT_PLUS_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(WEIGHT_MINUS_TAG).assertIsDisplayed()
    }

    @Test
    fun `weight control increment button calls onIncrement`() {
        var incremented = false
        composeTestRule.setContent {
            WeightControl(
                value = 1,
                onIncrement = { incremented = true },
                onDecrement = {},
                modifier = Modifier,
                testTag = WEIGHT_TAG,
            )
        }

        composeTestRule.onNodeWithTag(WEIGHT_PLUS_TAG).performClick()
        composeTestRule.waitForIdle()

        incremented shouldBe true
    }

    @Test
    fun `weight control decrement button calls onDecrement`() {
        var decremented = false
        composeTestRule.setContent {
            WeightControl(
                value = 1,
                onIncrement = {},
                onDecrement = { decremented = true },
                modifier = Modifier,
                testTag = WEIGHT_TAG,
            )
        }

        composeTestRule.onNodeWithTag(WEIGHT_MINUS_TAG).performClick()
        composeTestRule.waitForIdle()

        decremented shouldBe true
    }

    @Test
    fun `weight control decrement is disabled at zero`() {
        var decremented = false
        composeTestRule.setContent {
            WeightControl(
                value = 0,
                onIncrement = {},
                onDecrement = { decremented = true },
                modifier = Modifier,
                testTag = WEIGHT_TAG,
            )
        }

        composeTestRule.onNodeWithTag(WEIGHT_MINUS_TAG).performClick()
        composeTestRule.waitForIdle()

        decremented shouldBe false
    }

    @Test
    fun `key tile renders its label`() {
        composeTestRule.setContent {
            KeyTile(label = KEY_LABEL, isSelected = false, onClick = {})
        }

        composeTestRule.onNodeWithText(KEY_LABEL).assertIsDisplayed()
    }

    @Test
    fun `key tile click calls onClick`() {
        var clicked = false
        composeTestRule.setContent {
            KeyTile(label = KEY_LABEL, isSelected = false, onClick = { clicked = true })
        }

        composeTestRule.onNodeWithText(KEY_LABEL).performClick()
        composeTestRule.waitForIdle()

        clicked shouldBe true
    }

    private companion object {
        const val WEIGHT_TAG = "weight"
        const val WEIGHT_PLUS_TAG = "$WEIGHT_TAG-plus"
        const val WEIGHT_MINUS_TAG = "$WEIGHT_TAG-minus"
        const val KEY_LABEL = "K"
    }
}
