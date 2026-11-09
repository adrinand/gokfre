package com.adrinand.gokfre

import com.adrinand.gokfre.core.comms.AppSocketComms
import com.adrinand.gokfre.core.persistence.repo.SettingsRepository
import com.adrinand.gokfre.viewmodel.SettingsViewModel
import kotlinx.coroutines.channels.Channel
import java.awt.Window
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.io.path.createTempDirectory

private const val READY_TIMEOUT_SECONDS = 30L

/**
 * Handle to a running gokfre E2E instance.
 */
class E2EAppHandle(
    private val appThread: Thread,
    private val hotkeyProvider: TestGlobalHotkeyProvider,
    private val shutdownChannel: Channel<Unit>,
    val settingsPath: Path,
) {
    var viewModel: SettingsViewModel? = null
        private set

    fun triggerToggle() {
        hotkeyProvider.triggerToggle()
    }

    fun stop(timeoutMs: Long = 10_000) {
        runCatching { shutdownChannel.trySend(Unit) }

        // Give Compose a moment to exit cleanly before forcing windows closed.
        appThread.join(2_000)
        if (appThread.isAlive) {
            runCatching {
                Window.getWindows()
                    .filter { it.isDisplayable }
                    .forEach { it.dispose() }
            }
            appThread.join(timeoutMs - 2_000)
        }
        if (appThread.isAlive) {
            appThread.interrupt()
        }
        AppSocketComms.stopServer()
    }

    internal fun attachViewModel(viewModel: SettingsViewModel) {
        this.viewModel = viewModel
    }
}

/**
 * Launcher for end-to-end gokfre instances.
 *
 * Each instance runs in its own thread with an isolated settings directory and
 * a test hotkey provider. The real window manager backend is used so the tests
 * exercise actual OS window-management behavior.
 */
object E2EApplication {

    fun launch(
        settingsPath: Path =
            createTempDirectory("gokfre-e2e-settings")
                .resolve("settings.json"),
    ): E2EAppHandle {
        assumeDesktopEnvironment()

        val settingsRequestChannel = Channel<Unit>(Channel.CONFLATED)
        val acquired =
            AppSocketComms.tryAcquireServer { command ->
                if (command == AppSocketComms.COMMAND_SHOW_SETTINGS) {
                    settingsRequestChannel.trySend(Unit)
                }
            }

        check(acquired) { "Failed to acquire single-instance socket for E2E test" }

        val readyLatch = CountDownLatch(2)
        val viewModelRef = AtomicReference<SettingsViewModel?>(null)
        val shutdownChannel = Channel<Unit>()
        val hotkeyProvider = TestGlobalHotkeyProvider()

        val appThread =
            thread(name = "gokfre-e2e-app") {
                runGokfreApplication(
                    settingsRequestChannel = settingsRequestChannel,
                    exitProcessOnExit = false,
                    onViewModelCreated = { viewModel ->
                        viewModelRef.set(viewModel)
                        readyLatch.countDown()
                    },
                    onReady = { readyLatch.countDown() },
                    shutdownChannel = shutdownChannel,
                    settingsRepository = SettingsRepository(settingsPath),
                    globalHotkeyProvider = hotkeyProvider,
                    toggleDispatcher = { it() },
                )
            }

        val ready = readyLatch.await(READY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        check(ready) { "Gokfre E2E app did not become ready within $READY_TIMEOUT_SECONDS seconds" }

        hotkeyProvider.awaitRegistered()

        return E2EAppHandle(
            appThread = appThread,
            hotkeyProvider = hotkeyProvider,
            shutdownChannel = shutdownChannel,
            settingsPath = settingsPath,
        ).apply {
            viewModelRef.get()?.let { attachViewModel(it) }
        }
    }
}
