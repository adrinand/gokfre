package com.adrinand.gokfre.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ApplicationScope
import com.adrinand.gokfre.core.hotkey.GlobalHotkeyProvider
import com.adrinand.gokfre.core.screen.isLinux
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
internal fun ApplicationScope.GokfreApplicationContent(
    state: ApplicationState,
    globalHotkeyProvider: GlobalHotkeyProvider?,
    toggleDispatcher: (() -> Unit) -> Unit,
) {
    LaunchedEffect(state) {
        launch {
            state.awaitShutdown()
            exitApplication()
        }
        launch(Dispatchers.IO) { state.initializeWindowManager() }
        state.consumeSettingsRequests()
    }

    globalHotkeyRegistration(
        settingsViewModel = state.settingsViewModel,
        onPermissionMissing = state::reportPermissionMissing,
        onToggle = state::toggleWindow,
        globalHotkeyProvider = globalHotkeyProvider,
        toggleDispatcher = toggleDispatcher,
    )

    state.arrangementController?.let { controller ->
        GokfreWindow(
            visible = state.isPreviewVisible,
            onClose = { state.updatePreviewVisibility(false) },
            viewModel = state.settingsViewModel,
            arrangementController = controller,
        )
    }

    val trayIcon = remember { createTrayIcon() }
    GokfreTray(
        icon = trayIcon,
        onToggle = state::toggleWindow,
        onSettings = state::requestSettings,
    )

    if (state.isSettingsVisible) {
        SettingsWindow(
            isVisible = state.isSettingsVisible,
            onClose = state::dismissSettings,
            viewModel = state.settingsViewModel,
        )
    }

    PermissionWarning(
        visible = state.isPermissionWarningVisible,
        onDismiss = state::dismissPermissionWarning,
    )
}

@Composable
private fun PermissionWarning(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    if (!visible) {
        return
    }
    if (isLinux()) {
        InputPermissionWarningDialog(onDismiss = onDismiss)
    } else {
        MacAccessibilityWarningWindow(
            onDismiss = onDismiss,
            onOpenSystemSettings = ::openAccessibilitySettings,
        )
    }
}
