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
import com.adrinand.gokfre.core.screen.LinuxRequirement
import com.adrinand.gokfre.core.screen.LinuxRequirementChecker
import com.adrinand.gokfre.core.screen.createWindowManager
import com.adrinand.gokfre.core.screen.isLinux
import com.adrinand.gokfre.core.startup.StartupLauncher
import com.adrinand.gokfre.viewmodel.SettingsViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
@Suppress("TooManyFunctions")
@Stable
internal class ApplicationState(
    val settingsViewModel: SettingsViewModel,
    private val compositionScope: CoroutineScope,
    private val settingsRequestChannel: Channel<Unit>,
    private val shutdownChannel: Channel<Unit>,
    private val onReady: () -> Unit,
    private val e2eMode: Boolean = false,
) {
    var arrangementController: ArrangementController? by mutableStateOf(null)
        private set

    var isPreviewVisible: Boolean by mutableStateOf(false)
        private set

    var isSettingsVisible: Boolean by mutableStateOf(false)
        private set

    var linuxRequirements: List<LinuxRequirement>? by mutableStateOf(null)
        private set

    var linuxHotkeyUnavailable: Boolean by mutableStateOf(false)
        private set

    var isMacAccessibilityWarningVisible: Boolean by mutableStateOf(false)
        private set

    val linuxMissingRequirements: List<LinuxRequirement>
        get() =
            linuxRequirements?.let {
                LinuxRequirementChecker.displayedMissingRequirements(it, linuxHotkeyUnavailable)
            } ?: emptyList()

    val isSetupWarningVisible: Boolean
        get() = linuxMissingRequirements.isNotEmpty() || isMacAccessibilityWarningVisible

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

    fun dismissLinuxWarning() {
        linuxRequirements = emptyList()
        linuxHotkeyUnavailable = false
    }

    fun dismissMacWarning() {
        isMacAccessibilityWarningVisible = false
    }

    fun reportPermissionMissing() {
        if (isLinux()) {
            linuxHotkeyUnavailable = true
        } else {
            isMacAccessibilityWarningVisible = true
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun toggleWindow() {
        when {
            isSetupWarningVisible -> {
                logger.info { "Ignoring toggle while a setup warning is visible" }
            }
            arrangementController == null -> {
                logger.info { "Window manager not ready yet, ignoring toggle" }
            }
            previewVisible.get() -> {
                updatePreviewVisibility(false)
                logger.info("Preview window visible=false")
            }
            opening.compareAndSet(false, true) -> {
                compositionScope.launch { openPreviewWithCapture() }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun openPreviewWithCapture() {
        try {
            try {
                withTimeoutOrNull(OPEN_CAPTURE_TIMEOUT_MS.milliseconds) {
                    arrangementController?.captureTargetWindowOnIo()
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

    fun initializeWindowManager() {
        runCatching {
            arrangementController = ArrangementController(createWindowManager())
            onReady()
        }.onFailure {
            logger.log(Level.SEVERE, "Failed to initialize window manager", it)
        }
    }

    suspend fun checkLinuxRequirements() {
        if (!isLinux() || e2eMode) {
            linuxRequirements = emptyList()
            return
        }
        linuxRequirements =
            withContext(Dispatchers.IO) {
                LinuxRequirementChecker.checkMissingRequirementsWithRetry()
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
    e2eMode: Boolean = false,
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
            e2eMode = e2eMode,
        )
    }
}
