package com.adrinand.kofre.ui

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.adrinand.kofre.core.screen.ArrangementController
import com.adrinand.kofre.core.screen.HeadlessWindowManager
import com.adrinand.kofre.viewmodel.SettingsViewModel
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.GraphicsEnvironment

class KofreWindowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun requireDisplay() {
        assumeFalse("Skipping: headless environment", GraphicsEnvironment.isHeadless())
    }

    @Test
    fun `kofre window renders layout preview`() {
        composeTestRule.setContent {
            val coroutineScope = rememberCoroutineScope()
            val viewModel = remember { SettingsViewModel(coroutineScope) }
            val windowManager = remember { HeadlessWindowManager() }
            val controller = remember { ArrangementController(windowManager) }
            KofreWindow(
                visible = true,
                onClose = {},
                viewModel = viewModel,
                arrangementController = controller,
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("layout-preview").assertExists()
    }
}
