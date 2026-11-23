package com.adrinand.gokfre

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.application
import com.adrinand.gokfre.core.comms.AppSocketComms
import com.adrinand.gokfre.core.hotkey.GlobalHotkeyProvider
import com.adrinand.gokfre.core.logging.AppLogging
import com.adrinand.gokfre.core.persistence.repo.SettingsRepository
import com.adrinand.gokfre.core.screen.ArrangementController
import com.adrinand.gokfre.core.screen.LinuxRequirement
import com.adrinand.gokfre.core.screen.LinuxRequirementChecker
import com.adrinand.gokfre.core.screen.WindowManager
import com.adrinand.gokfre.core.screen.createWindowManager
import com.adrinand.gokfre.core.screen.isLinux
import com.adrinand.gokfre.core.startup.StartupLauncher
import com.adrinand.gokfre.core.startup.createStartupLauncher
import com.adrinand.gokfre.ui.GokfreTray
import com.adrinand.gokfre.ui.GokfreWindow
import com.adrinand.gokfre.ui.LinuxWarningWindow
import com.adrinand.gokfre.ui.MacAccessibilityWarningWindow
import com.adrinand.gokfre.ui.SettingsWindow
import com.adrinand.gokfre.ui.createTrayIcon
import com.adrinand.gokfre.ui.globalHotkeyRegistration
import com.adrinand.gokfre.ui.openAccessibilitySettings
import com.adrinand.gokfre.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.logging.Level
import java.util.logging.Logger
import javax.swing.SwingUtilities

private val logger = Logger.getLogger("com.adrinand.gokfre.Main")

fun main() {
    AppLogging.initialize()
    val settingsRequestChannel = Channel<Unit>(Channel.CONFLATED)
    val acquired =
        AppSocketComms.tryAcquireServer { command ->
            if (command == AppSocketComms.COMMAND_SHOW_SETTINGS) {
                settingsRequestChannel.trySend(Unit)
            }
        }

    if (!acquired) {
        logger.info("Another gokfre instance is already running, exiting")
        return
    }

    runGokfreApplication(settingsRequestChannel)
}

@Suppress("LongMethod", "CognitiveComplexMethod", "CyclomaticComplexMethod", "LongParameterList")
fun runGokfreApplication(
    settingsRequestChannel: Channel<Unit>,
    exitProcessOnExit: Boolean = true,
    onReady: () -> Unit = {},
    onViewModelCreated: (SettingsViewModel) -> Unit = {},
    shutdownChannel: Channel<Unit> = Channel(),
    settingsRepository: SettingsRepository? = null,
    startupLauncher: StartupLauncher? = null,
    globalHotkeyProvider: GlobalHotkeyProvider? = null,
    toggleDispatcher: (() -> Unit) -> Unit = { SwingUtilities.invokeLater(it) },
    e2eMode: Boolean = false,
) {
    val effectiveSettingsRepository =
        settingsRepository
            ?: runCatching { SettingsRepository() }
                .onFailure { logger.warning("Failed to initialize settings repository: ${it.message}") }
                .getOrNull()

    val effectiveStartupLauncher =
        startupLauncher
            ?: runCatching { createStartupLauncher() }
                .onFailure { logger.warning("Failed to initialize startup launcher: ${it.message}") }
                .getOrNull()

    application(exitProcessOnExit = exitProcessOnExit) {
        val settingsCoroutineScope = rememberCoroutineScope()
        val settingsViewModel =
            remember {
                SettingsViewModel(settingsCoroutineScope, effectiveSettingsRepository, effectiveStartupLauncher)
            }
        LaunchedEffect(settingsViewModel) {
            onViewModelCreated(settingsViewModel)
        }
        var windowManager by remember { mutableStateOf<WindowManager?>(null) }
        var arrangementController by remember { mutableStateOf<ArrangementController?>(null) }
        var isWindowVisible by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }
        var showMacAccessibilityWarning by remember { mutableStateOf(false) }
        var linuxRequirements by remember { mutableStateOf<List<LinuxRequirement>?>(null) }
        var linuxHotkeyUnavailable by remember { mutableStateOf(false) }
        val trayIcon = remember { createTrayIcon() }

        LaunchedEffect(Unit) {
            if (isLinux() && !e2eMode) {
                linuxRequirements =
                    withContext(Dispatchers.IO) {
                        LinuxRequirementChecker.checkMissingRequirementsWithRetry()
                    }
            }
        }

        val warningRequirements by remember {
            derivedStateOf {
                linuxRequirements?.let {
                    LinuxRequirementChecker.displayedRequirements(
                        detected = it,
                        hotkeyListenerFailed = linuxHotkeyUnavailable,
                    )
                } ?: emptyList()
            }
        }

        LaunchedEffect(Unit) {
            launch {
                shutdownChannel.receive()
                exitApplication()
            }
            launch(Dispatchers.IO) {
                runCatching {
                    val manager = createWindowManager()
                    windowManager = manager
                    arrangementController = ArrangementController(manager)
                    onReady()
                }.onFailure {
                    logger.log(Level.SEVERE, "Failed to initialize window manager", it)
                }
            }
            settingsRequestChannel.consumeEach { showSettings = true }
        }

        fun isSetupWarningVisible(): Boolean = warningRequirements.isNotEmpty() || showMacAccessibilityWarning

        val toggleWindow = {
            if (isSetupWarningVisible()) {
                logger.info { "Ignoring toggle while a setup warning is visible" }
            } else {
                val controller = arrangementController
                if (controller != null) {
                    if (!isWindowVisible) {
                        controller.captureTargetWindow()
                    }
                    isWindowVisible = !isWindowVisible
                    logger.info("Preview window visible=$isWindowVisible")
                } else {
                    logger.info { "Window manager not ready yet, ignoring toggle" }
                }
            }
        }

        globalHotkeyRegistration(
            settingsViewModel = settingsViewModel,
            onPermissionMissing = {
                if (isLinux()) {
                    linuxHotkeyUnavailable = true
                } else {
                    showMacAccessibilityWarning = true
                }
            },
            onToggle = toggleWindow,
            globalHotkeyProvider = globalHotkeyProvider,
            toggleDispatcher = toggleDispatcher,
        )

        arrangementController?.let { controller ->
            GokfreWindow(
                visible = isWindowVisible,
                onClose = { isWindowVisible = false },
                viewModel = settingsViewModel,
                arrangementController = controller,
            )
        }

        GokfreTray(
            icon = trayIcon,
            onToggle = toggleWindow,
            onSettings = { showSettings = true },
        )

        if (showSettings) {
            SettingsWindow(
                isVisible = showSettings,
                onClose = { showSettings = false },
                viewModel = settingsViewModel,
            )
        }

        if (isLinux()) {
            LinuxWarningWindow(
                requirements = warningRequirements,
                onDismiss = {
                    linuxRequirements = emptyList()
                    linuxHotkeyUnavailable = false
                },
            )
        } else if (showMacAccessibilityWarning) {
            MacAccessibilityWarningWindow(
                onDismiss = { showMacAccessibilityWarning = false },
                onOpenSystemSettings = ::openAccessibilitySettings,
            )
        }
    }
}
