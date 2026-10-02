package com.adrinand.kofre.ui

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import com.adrinand.kofre.viewmodel.SettingsViewModel

@Composable
fun App(settingsViewModel: SettingsViewModel) {
    MaterialTheme {
        LayoutPreviewScreen(settingsViewModel)
    }
}
