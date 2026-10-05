package com.adrinand.gokfre.ui

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.adrinand.gokfre.core.screen.ArrangementController
import com.adrinand.gokfre.core.screen.HeadlessWindowManager
import com.adrinand.gokfre.viewmodel.SettingsViewModel
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.IllegalComponentStateException

class GokfreWindowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun requireDisplay() {
        assumeFalse("Skipping: headless environment", GraphicsEnvironment.isHeadless())
    }

    @Test
    fun `gokfre window renders layout preview`() {
        composeTestRule.setContent {
            val coroutineScope = rememberCoroutineScope()
            val viewModel = remember { SettingsViewModel(coroutineScope) }
            val windowManager = remember { HeadlessWindowManager() }
            val controller = remember { ArrangementController(windowManager) }
            GokfreWindow(
                visible = true,
                onClose = {},
                viewModel = viewModel,
                arrangementController = controller,
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("layout-preview").assertExists()
    }

    @Test
    fun `setOpacitySafely swallows full-screen opacity failures`() {
        val frame =
            object : Frame() {
                override fun setOpacity(opacity: Float) {
                    throw IllegalComponentStateException("Setting opacity for full-screen window is not supported.")
                }
            }

        frame.setOpacitySafely(1f)
    }
}
