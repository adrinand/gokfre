package com.adrinand.kofre.core.persistence.repo

import com.adrinand.kofre.core.hotkey.Hotkey
import com.adrinand.kofre.core.hotkey.ModifierKey
import com.adrinand.kofre.core.persistence.entity.PersistedLayoutSettings
import com.adrinand.kofre.core.persistence.entity.PersistedSettings
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class SettingsRepositoryTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createRepository() = SettingsRepository(tempFolder.root.toPath().resolve("settings.json"))

    @Test
    fun `load returns null when settings file does not exist`() {
        val repository = createRepository()

        repository.load().shouldBeNull()
    }

    @Test
    fun `save and load round-trips settings`() {
        val repository = createRepository()
        val settings =
            PersistedSettings(
                layout =
                    PersistedLayoutSettings(
                        columnWeights = listOf(2, 1),
                        rowWeights = listOf(1, 2, 3),
                        keyLabels = listOf(listOf("A", "B"), listOf("C", "D"), listOf("E", "F")),
                    ),
                toggleHotkey = Hotkey(42, setOf(ModifierKey.CTRL, ModifierKey.SHIFT)),
            )

        repository.save(settings)
        val loaded = repository.load()

        loaded shouldBe settings
    }

    @Test
    fun `load returns null for corrupt json`() {
        val repository = createRepository()
        Files.writeString(repository.settingsPath(), "not valid json")

        repository.load().shouldBeNull()
    }

    @Test
    fun `load ignores unknown keys`() {
        val repository = createRepository()
        val json =
            "{ \"version\": 1, \"unknownField\": \"ignored\", " +
                "\"layout\": { \"columnWeights\": [1, 2], \"rowWeights\": [3, 4], " +
                "\"keyLabels\": [[\"X\"], [\"Y\"]], \"anotherUnknown\": 123 }, " +
                "\"toggleHotkey\": { \"keyCode\": 10, \"modifiers\": [\"ALT\"] } }"
        Files.writeString(repository.settingsPath(), json)

        val loaded = repository.load()

        loaded shouldBe
            PersistedSettings(
                layout =
                    PersistedLayoutSettings(
                        columnWeights = listOf(1, 2),
                        rowWeights = listOf(3, 4),
                        keyLabels = listOf(listOf("X"), listOf("Y")),
                    ),
                toggleHotkey = Hotkey(10, setOf(ModifierKey.ALT)),
            )
    }

    @Test
    fun `load uses defaults for missing fields`() {
        val repository = createRepository()
        val json = "{ \"version\": 1 }"
        Files.writeString(repository.settingsPath(), json)

        val loaded = repository.load()

        loaded shouldBe PersistedSettings()
    }

    @Test
    fun `save creates parent directories`() {
        val nestedPath = tempFolder.root.toPath().resolve("a/b/c/settings.json")
        val repository = SettingsRepository(nestedPath)

        repository.save(PersistedSettings())

        Files.exists(nestedPath) shouldBe true
    }

    private fun SettingsRepository.settingsPath() = tempFolder.root.toPath().resolve("settings.json")
}
