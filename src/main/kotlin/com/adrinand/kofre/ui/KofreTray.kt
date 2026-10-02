package com.adrinand.kofre.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.isTraySupported
import com.adrinand.kofre.core.screen.isLinux
import com.adrinand.kofre.ui.tray.LinuxTrayManager
import com.adrinand.kofre.ui.tray.TrayController

private const val TRAY_TOOLTIP = "Kofre"
private const val SETTINGS_LABEL = "Settings"
private const val QUIT_LABEL = "Quit"

@Composable
fun ApplicationScope.KofreTray(
    icon: Painter,
    onToggle: () -> Unit,
    onSettings: () -> Unit,
) {
    if (isLinux()) {
        LinuxTray(onToggle, onSettings)
    } else {
        ComposeTray(icon, onToggle, onSettings)
    }
}

@Composable
private fun ApplicationScope.LinuxTray(
    onToggle: () -> Unit,
    onSettings: () -> Unit,
) {
    val currentOnToggle by rememberUpdatedState(onToggle)
    val currentOnSettings by rememberUpdatedState(onSettings)
    val controller =
        remember {
            TrayController(
                onToggle = currentOnToggle,
                onSettings = currentOnSettings,
                onQuit = { this@LinuxTray.exitApplication() },
            )
        }

    DisposableEffect(Unit) {
        val manager = LinuxTrayManager(controller)
        manager.install()
        onDispose { manager.dispose() }
    }
}

@Composable
private fun ApplicationScope.ComposeTray(
    icon: Painter,
    onToggle: () -> Unit,
    onSettings: () -> Unit,
) {
    if (!isTraySupported) {
        return
    }

    Tray(
        icon = icon,
        tooltip = TRAY_TOOLTIP,
        onAction = onToggle,
        menu = {
            Item(SETTINGS_LABEL, onClick = onSettings)
            Item(QUIT_LABEL, onClick = ::exitApplication)
        },
    )
}
