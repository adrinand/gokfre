package com.adrinand.kofre.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.adrinand.kofre.viewmodel.SettingsViewModel

private enum class SettingsTab {
    LAYOUT,
    HOTKEY,
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    var selectedTab by remember { mutableStateOf(SettingsTab.LAYOUT) }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("settings-screen"),
        bottomBar = {
            BottomNavigation {
                BottomNavigationItem(
                    selected = selectedTab == SettingsTab.LAYOUT,
                    onClick = { selectedTab = SettingsTab.LAYOUT },
                    icon = { Text("L") },
                    label = { Text("Layout") },
                    modifier = Modifier.testTag("layout-tab"),
                )
                BottomNavigationItem(
                    selected = selectedTab == SettingsTab.HOTKEY,
                    onClick = { selectedTab = SettingsTab.HOTKEY },
                    icon = { Text("H") },
                    label = { Text("Hotkeys") },
                    modifier = Modifier.testTag("hotkey-tab"),
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            if (selectedTab == SettingsTab.LAYOUT) {
                LayoutSettingsScreen(viewModel)
            } else {
                HotkeySettingsScreen(viewModel)
            }
        }
    }
}
