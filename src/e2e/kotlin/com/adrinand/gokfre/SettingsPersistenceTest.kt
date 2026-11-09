package com.adrinand.gokfre

import com.adrinand.gokfre.core.persistence.entity.PersistedLayoutSettings
import com.adrinand.gokfre.core.persistence.entity.PersistedSettings
import com.adrinand.gokfre.core.persistence.repo.SettingsRepository
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory

class SettingsPersistenceTest {
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `settings are loaded from disk on app start`() {
        assumeDesktopEnvironment()

        val settingsPath = createTempDirectory("gokfre-e2e-settings").resolve("settings.json")
        val expectedLayout =
            PersistedLayoutSettings(
                columnWeights = listOf(1, 1),
                rowWeights = listOf(1, 1),
                keyLabels =
                    listOf(
                        listOf("A", "B"),
                        listOf("C", "D"),
                    ),
            )
        SettingsRepository(settingsPath).save(
            PersistedSettings(
                layout = expectedLayout,
            ),
        )

        val app = E2EApplication.launch(settingsPath)
        try {
            val viewModel = app.viewModel ?: error("ViewModel not available")
            val layout = viewModel.layoutSettings

            layout.columnWeights.toList() shouldBe expectedLayout.columnWeights
            layout.rowWeights.toList() shouldBe expectedLayout.rowWeights
            layout.keyLabels[0].toList() shouldBe expectedLayout.keyLabels[0]
            layout.keyLabels[1].toList() shouldBe expectedLayout.keyLabels[1]
        } finally {
            app.stop()
        }
    }
}
