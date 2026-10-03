package com.adrinand.gokfre.ui

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import com.adrinand.gokfre.viewmodel.SettingsViewModel

@Composable
fun App(settingsViewModel: SettingsViewModel) {
    MaterialTheme {
        LayoutPreviewScreen(settingsViewModel)
    }
}
