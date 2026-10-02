package com.adrinand.kofre.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.adrinand.kofre.viewmodel.SettingsViewModel

private const val SETTINGS_WINDOW_WIDTH_DP = 800
private const val SETTINGS_WINDOW_HEIGHT_DP = 600

@Composable
fun SettingsWindow(
    isVisible: Boolean,
    onClose: () -> Unit,
    viewModel: SettingsViewModel,
) {
    if (!isVisible) {
        return
    }

    Window(
        onCloseRequest = onClose,
        title = "Kofre Settings",
        state = rememberWindowState(width = SETTINGS_WINDOW_WIDTH_DP.dp, height = SETTINGS_WINDOW_HEIGHT_DP.dp),
    ) {
        SettingsScreen(viewModel)
    }
}
