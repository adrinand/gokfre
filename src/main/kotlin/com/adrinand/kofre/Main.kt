package com.adrinand.kofre

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.application
import com.adrinand.kofre.core.comms.AppSocketComms
import com.adrinand.kofre.core.persistence.repo.SettingsRepository
import com.adrinand.kofre.core.screen.ArrangementController
import com.adrinand.kofre.core.screen.WindowManager
import com.adrinand.kofre.core.screen.createWindowManager
import com.adrinand.kofre.ui.InputPermissionWarningDialog
import com.adrinand.kofre.ui.KofreTray
import com.adrinand.kofre.ui.KofreWindow
import com.adrinand.kofre.ui.SettingsWindow
import com.adrinand.kofre.ui.createTrayIcon
import com.adrinand.kofre.ui.globalHotkeyRegistration
import com.adrinand.kofre.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import java.util.logging.Logger

private val logger = Logger.getLogger("com.adrinand.kofre.Main")

fun main() {
    val settingsRequestChannel = Channel<Unit>(Channel.CONFLATED)
    val acquired =
        AppSocketComms.tryAcquireServer { command ->
            if (command == AppSocketComms.COMMAND_SHOW_SETTINGS) {
                settingsRequestChannel.trySend(Unit)
            }
        }

    if (!acquired) {
        return
    }

    runApplication(settingsRequestChannel)
}

@Suppress("LongMethod", "CognitiveComplexMethod")
private fun runApplication(settingsRequestChannel: Channel<Unit>) {
    val settingsRepository =
        runCatching { SettingsRepository() }
            .onFailure { logger.warning("Failed to initialize settings repository: ${it.message}") }
            .getOrNull()

    application(exitProcessOnExit = false) {
        val settingsCoroutineScope = rememberCoroutineScope()
        val settingsViewModel = remember { SettingsViewModel(settingsCoroutineScope, settingsRepository) }
        var windowManager by remember { mutableStateOf<WindowManager?>(null) }
        var arrangementController by remember { mutableStateOf<ArrangementController?>(null) }
        var isWindowVisible by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }
        var showPermissionWarning by remember { mutableStateOf(false) }
        val trayIcon = remember { createTrayIcon() }

        LaunchedEffect(Unit) {
            launch(Dispatchers.IO) {
                val manager = createWindowManager()
                windowManager = manager
                arrangementController = ArrangementController(manager)
            }
            settingsRequestChannel.consumeEach { showSettings = true }
        }

        val toggleWindow = {
            val controller = arrangementController
            if (controller != null) {
                if (!isWindowVisible) {
                    controller.captureTargetWindow()
                }
                isWindowVisible = !isWindowVisible
            } else {
                logger.info { "Window manager not ready yet, ignoring toggle" }
            }
        }

        globalHotkeyRegistration(
            settingsViewModel = settingsViewModel,
            onPermissionMissing = { showPermissionWarning = true },
            onToggle = toggleWindow,
        )

        arrangementController?.let { controller ->
            KofreWindow(
                visible = isWindowVisible,
                onClose = { isWindowVisible = false },
                viewModel = settingsViewModel,
                arrangementController = controller,
            )
        }

        KofreTray(
            icon = trayIcon,
            onToggle = { isWindowVisible = !isWindowVisible },
            onSettings = { showSettings = true },
        )

        if (showSettings) {
            SettingsWindow(
                isVisible = showSettings,
                onClose = { showSettings = false },
                viewModel = settingsViewModel,
            )
        }

        if (showPermissionWarning) {
            InputPermissionWarningDialog(onDismiss = { showPermissionWarning = false })
        }
    }
}
