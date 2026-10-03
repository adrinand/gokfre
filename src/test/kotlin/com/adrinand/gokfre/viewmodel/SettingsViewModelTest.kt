package com.adrinand.gokfre.viewmodel

import com.adrinand.gokfre.core.hotkey.Hotkey
import com.adrinand.gokfre.core.hotkey.ModifierKey
import com.adrinand.gokfre.core.hotkey.toDisplayString
import com.adrinand.gokfre.core.persistence.entity.PersistedLayoutSettings
import com.adrinand.gokfre.core.persistence.entity.PersistedSettings
import com.adrinand.gokfre.core.persistence.repo.SettingsRepository
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val AUTO_SAVE_WAIT_MILLIS = 700L

class SettingsViewModelTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `default layout settings hold default bindings`() {
        val viewModel = SettingsViewModel(CoroutineScope(Dispatchers.Unconfined))

        val settings = viewModel.layoutSettings
        settings.columnWeights.toList() shouldBe listOf(1, 1, 1, 1)
        settings.rowWeights.toList() shouldBe listOf(1, 1, 1)
        settings.keyLabels.map { it.toList() } shouldBe
            listOf(
                listOf("Q", "W", "E", "R"),
                listOf("A", "S", "D", "F"),
                listOf("Z", "X", "C", "V"),
            )
    }

    @Test
    fun `default layout settings expose default helper`() {
        val settings = AppLayoutSettings.default()

        settings.columnWeights.toList() shouldBe listOf(1, 1, 1, 1)
        settings.rowWeights.toList() shouldBe listOf(1, 1, 1)
        settings.keyLabels.map { it.toList() } shouldBe
            listOf(
                listOf("Q", "W", "E", "R"),
                listOf("A", "S", "D", "F"),
                listOf("Z", "X", "C", "V"),
            )
    }

    @Test
    fun `default toggle hotkey is Super plus K`() {
        val viewModel = SettingsViewModel(CoroutineScope(Dispatchers.Unconfined))

        viewModel.toggleHotkey shouldBe Hotkey.DEFAULT_TOGGLE
        viewModel.toggleHotkey.toDisplayString() shouldBe "Super+K"
    }

    @Test
    fun `toggle hotkey can be updated`() {
        val viewModel = SettingsViewModel(CoroutineScope(Dispatchers.Unconfined))

        val newHotkey = Hotkey(10, setOf(ModifierKey.CTRL, ModifierKey.SHIFT))
        viewModel.toggleHotkey = newHotkey

        viewModel.toggleHotkey shouldBe newHotkey
    }

    @Test
    fun `view model loads persisted settings`() {
        val repository = createRepository()
        val persisted =
            PersistedSettings(
                layout =
                    PersistedLayoutSettings(
                        columnWeights = listOf(2, 1),
                        rowWeights = listOf(3),
                        keyLabels = listOf(listOf("M", "N")),
                    ),
                toggleHotkey = Hotkey(99, setOf(ModifierKey.ALT)),
            )
        repository.save(persisted)

        val viewModel = SettingsViewModel(CoroutineScope(Dispatchers.Unconfined), repository)

        viewModel.layoutSettings.columnWeights.toList() shouldBe listOf(2, 1)
        viewModel.layoutSettings.rowWeights.toList() shouldBe listOf(3)
        viewModel.layoutSettings.keyLabels.map { it.toList() } shouldBe listOf(listOf("M", "N"))
        viewModel.toggleHotkey shouldBe Hotkey(99, setOf(ModifierKey.ALT))
    }

    @Test
    fun `view model auto-saves changes after debounce`() {
        val repository = createRepository()
        val viewModel = SettingsViewModel(CoroutineScope(Dispatchers.Unconfined), repository)

        viewModel.layoutSettings.columnWeights[0] = 5
        viewModel.toggleHotkey = Hotkey(20, setOf(ModifierKey.SUPER))
        Thread.sleep(AUTO_SAVE_WAIT_MILLIS)

        val loaded = repository.load().shouldNotBeNull()
        loaded.layout.columnWeights shouldBe listOf(5, 1, 1, 1)
        loaded.toggleHotkey shouldBe Hotkey(20, setOf(ModifierKey.SUPER))
    }

    private fun createRepository(): SettingsRepository =
        SettingsRepository(
            tempFolder.root.toPath().resolve("settings.json"),
        )
}
