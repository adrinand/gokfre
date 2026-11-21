package com.adrinand.gokfre.viewmodel

import com.adrinand.gokfre.core.startup.StartupLauncher
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Test

class SettingsViewModelStartupTest {
    @Test
    fun `view model syncs launch at startup from the launcher`() {
        val launcher = FakeStartupLauncher(enabled = true)

        val viewModel = createViewModel(launcher)

        viewModel.launchAtStartup shouldBe true
    }

    @Test
    fun `enabling launch at startup invokes the launcher`() {
        val launcher = FakeStartupLauncher()
        val viewModel = createViewModel(launcher)

        viewModel.updateLaunchAtStartup(true)

        launcher.enableCalls shouldBe 1
        launcher.disableCalls shouldBe 0
        viewModel.launchAtStartup shouldBe true
        viewModel.startupError shouldBe null
    }

    @Test
    fun `disabling launch at startup invokes the launcher`() {
        val launcher = FakeStartupLauncher(enabled = true)
        val viewModel = createViewModel(launcher)

        viewModel.updateLaunchAtStartup(false)

        launcher.enableCalls shouldBe 0
        launcher.disableCalls shouldBe 1
        viewModel.launchAtStartup shouldBe false
        viewModel.startupError shouldBe null
    }

    @Test
    fun `startup failure keeps the previous state and exposes an error`() {
        val launcher =
            FakeStartupLauncher(
                enableResult = Result.failure(IllegalStateException("Launcher unavailable")),
            )
        val viewModel = createViewModel(launcher)

        viewModel.updateLaunchAtStartup(true)

        viewModel.launchAtStartup shouldBe false
        viewModel.startupError shouldBe "Launcher unavailable"
    }

    private fun createViewModel(launcher: StartupLauncher): SettingsViewModel =
        SettingsViewModel(
            CoroutineScope(Dispatchers.Unconfined),
            startupLauncher = launcher,
            ioDispatcher = Dispatchers.Unconfined,
        )

    private class FakeStartupLauncher(
        var enabled: Boolean = false,
        private val enableResult: Result<Unit> = Result.success(Unit),
        private val disableResult: Result<Unit> = Result.success(Unit),
    ) : StartupLauncher {
        var enableCalls = 0
        var disableCalls = 0

        override fun isEnabled(): Boolean = enabled

        override fun enable(): Result<Unit> {
            enableCalls++
            return enableResult
        }

        override fun disable(): Result<Unit> {
            disableCalls++
            return disableResult
        }
    }
}
