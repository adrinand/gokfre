package com.adrinand.gokfre.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.adrinand.gokfre.core.hotkey.Hotkey
import com.adrinand.gokfre.core.persistence.entity.PersistedLayoutSettings
import com.adrinand.gokfre.core.persistence.entity.PersistedSettings
import com.adrinand.gokfre.core.persistence.entity.toAppLayoutSettings
import com.adrinand.gokfre.core.persistence.repo.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private const val SAVE_DEBOUNCE_MILLIS = 500L

class SettingsViewModel(
    private val coroutineScope: CoroutineScope,
    private val repository: SettingsRepository? = null,
) {
    var layoutSettings: AppLayoutSettings by mutableStateOf(AppLayoutSettings.default())
    var toggleHotkey: Hotkey by mutableStateOf(Hotkey.DEFAULT_TOGGLE)
    var registrationError: String? by mutableStateOf(null)
    var isHotkeyCaptureActive: Boolean by mutableStateOf(false)

    init {
        repository?.load()?.let { load(it) }
        startAutoSave()
    }

    private fun load(persisted: PersistedSettings) {
        layoutSettings = persisted.layout.toAppLayoutSettings()
        toggleHotkey = persisted.toggleHotkey
    }

    @OptIn(FlowPreview::class)
    private fun startAutoSave() {
        val repo = repository ?: return

        coroutineScope.launch {
            snapshotFlow {
                PersistedSettings(
                    layout = layoutSettings.toPersisted(),
                    toggleHotkey = toggleHotkey,
                )
            }
                .distinctUntilChanged()
                .debounce(SAVE_DEBOUNCE_MILLIS.milliseconds)
                .collect { repo.save(it) }
        }
    }
}

data class AppLayoutSettings(
    val columnWeights: SnapshotStateList<Int>,
    val rowWeights: SnapshotStateList<Int>,
    val keyLabels: SnapshotStateList<SnapshotStateList<String>>,
) {
    companion object {
        fun default() =
            AppLayoutSettings(
                columnWeights = mutableStateListOf(1, 1, 1, 1),
                rowWeights = mutableStateListOf(1, 1, 1),
                keyLabels =
                    mutableStateListOf(
                        mutableStateListOf("Q", "W", "E", "R"),
                        mutableStateListOf("A", "S", "D", "F"),
                        mutableStateListOf("Z", "X", "C", "V"),
                    ),
            )
    }
}

fun AppLayoutSettings.toPersisted(): PersistedLayoutSettings =
    PersistedLayoutSettings(
        columnWeights = columnWeights.toList(),
        rowWeights = rowWeights.toList(),
        keyLabels = keyLabels.map { it.toList() },
    )
