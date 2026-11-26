package com.adrinand.gokfre

import androidx.compose.ui.window.application
import com.adrinand.gokfre.core.comms.AppSocketComms
import com.adrinand.gokfre.core.hotkey.GlobalHotkeyProvider
import com.adrinand.gokfre.core.logging.AppLogging
import com.adrinand.gokfre.core.persistence.repo.SettingsRepository
import com.adrinand.gokfre.core.startup.StartupLauncher
import com.adrinand.gokfre.core.startup.createStartupLauncher
import com.adrinand.gokfre.ui.ApplicationDependencies
import com.adrinand.gokfre.ui.GokfreApplicationContent
import com.adrinand.gokfre.ui.rememberApplicationState
import com.adrinand.gokfre.viewmodel.SettingsViewModel
import kotlinx.coroutines.channels.Channel
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

@Suppress("LongParameterList")
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
    val dependencies = createApplicationDependencies(settingsRepository, startupLauncher)
    application(exitProcessOnExit = exitProcessOnExit) {
        val state =
            rememberApplicationState(
                dependencies = dependencies,
                settingsRequestChannel = settingsRequestChannel,
                shutdownChannel = shutdownChannel,
                onReady = onReady,
                onViewModelCreated = onViewModelCreated,
                e2eMode = e2eMode,
            )
        GokfreApplicationContent(
            state = state,
            globalHotkeyProvider = globalHotkeyProvider,
            toggleDispatcher = toggleDispatcher,
        )
    }
}

private fun createApplicationDependencies(
    settingsRepository: SettingsRepository?,
    startupLauncher: StartupLauncher?,
): ApplicationDependencies =
    ApplicationDependencies(
        settingsRepository =
            settingsRepository
                ?: runCatching { SettingsRepository() }
                    .onFailure { logger.warning("Failed to initialize settings repository: ${it.message}") }
                    .getOrNull(),
        startupLauncher =
            startupLauncher
                ?: runCatching { createStartupLauncher() }
                    .onFailure { logger.warning("Failed to initialize startup launcher: ${it.message}") }
                    .getOrNull(),
    )
