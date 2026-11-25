package com.adrinand.gokfre.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.adrinand.gokfre.core.persistence.repo.SettingsRepository
import com.adrinand.gokfre.core.screen.ArrangementController
import com.adrinand.gokfre.core.screen.createWindowManager
import com.adrinand.gokfre.core.startup.StartupLauncher
import com.adrinand.gokfre.viewmodel.SettingsViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.time.Duration.Companion.milliseconds

private val logger = Logger.getLogger("com.adrinand.gokfre.ui.ApplicationState")

private const val OPEN_CAPTURE_TIMEOUT_MS = 250L

internal data class ApplicationDependencies(
    val settingsRepository: SettingsRepository?,
    val startupLauncher: StartupLauncher?,
)

/**
 * Owns the mutable state that drives the application shell and the actions that
 * mutate it.
 */
@Stable
internal class ApplicationState(
    val settingsViewModel: SettingsViewModel,
    private val compositionScope: CoroutineScope,
    private val settingsRequestChannel: Channel<Unit>,
    private val shutdownChannel: Channel<Unit>,
    private val onReady: () -> Unit,
) {
    var arrangementController: ArrangementController? by mutableStateOf(null)
        private set

    var isPreviewVisible: Boolean by mutableStateOf(false)
        private set

    var isSettingsVisible: Boolean by mutableStateOf(false)
        private set

    var isPermissionWarningVisible: Boolean by mutableStateOf(false)
        private set

    private val previewVisible = AtomicBoolean(false)
    private val opening = AtomicBoolean(false)

    fun updatePreviewVisibility(visible: Boolean) {
        previewVisible.set(visible)
        isPreviewVisible = visible
    }

    fun requestSettings() {
        isSettingsVisible = true
    }

    fun dismissSettings() {
        isSettingsVisible = false
    }

    fun dismissPermissionWarning() {
        isPermissionWarningVisible = false
    }

    fun reportPermissionMissing() {
        isPermissionWarningVisible = true
    }

    @Suppress("TooGenericExceptionCaught")
    fun toggleWindow() {
        val controller = arrangementController
        if (controller == null) {
            logger.info { "Window manager not ready yet, ignoring toggle" }
            return
        }
        if (previewVisible.get()) {
            updatePreviewVisibility(false)
            logger.info("Preview window visible=false")
            return
        }
        if (!opening.compareAndSet(false, true)) {
            return
        }
        compositionScope.launch {
            try {
                try {
                    withTimeoutOrNull(OPEN_CAPTURE_TIMEOUT_MS.milliseconds) {
                        controller.captureTargetWindowOnIo()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    logger.log(Level.WARNING, "Target capture failed, showing preview anyway", e)
                }
                updatePreviewVisibility(true)
                logger.info("Preview window visible=true")
            } finally {
                opening.set(false)
            }
        }
    }

    fun initializeWindowManager() {
        runCatching {
            arrangementController = ArrangementController(createWindowManager())
            onReady()
        }.onFailure {
            logger.log(Level.SEVERE, "Failed to initialize window manager", it)
        }
    }

    suspend fun awaitShutdown() {
        shutdownChannel.receive()
    }

    suspend fun consumeSettingsRequests() {
        settingsRequestChannel.consumeEach { requestSettings() }
    }
}

@Composable
internal fun rememberApplicationState(
    dependencies: ApplicationDependencies,
    settingsRequestChannel: Channel<Unit>,
    shutdownChannel: Channel<Unit>,
    onReady: () -> Unit,
    onViewModelCreated: (SettingsViewModel) -> Unit,
): ApplicationState {
    val compositionScope = rememberCoroutineScope()
    val settingsViewModel =
        remember {
            SettingsViewModel(
                compositionScope,
                dependencies.settingsRepository,
                dependencies.startupLauncher,
            )
        }
    LaunchedEffect(settingsViewModel) {
        onViewModelCreated(settingsViewModel)
    }
    return remember(settingsViewModel) {
        ApplicationState(
            settingsViewModel = settingsViewModel,
            compositionScope = compositionScope,
            settingsRequestChannel = settingsRequestChannel,
            shutdownChannel = shutdownChannel,
            onReady = onReady,
        )
    }
}
