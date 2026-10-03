package com.adrinand.gokfre.core.persistence.entity

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.adrinand.gokfre.core.hotkey.Hotkey
import com.adrinand.gokfre.viewmodel.AppLayoutSettings
import kotlinx.serialization.Serializable

private const val CURRENT_SETTINGS_VERSION = 1

@Serializable
data class PersistedSettings(
    val version: Int = CURRENT_SETTINGS_VERSION,
    val layout: PersistedLayoutSettings = PersistedLayoutSettings(),
    val toggleHotkey: Hotkey = Hotkey.DEFAULT_TOGGLE,
)

@Serializable
data class PersistedLayoutSettings(
    val columnWeights: List<Int> = listOf(1, 1, 1, 1),
    val rowWeights: List<Int> = listOf(1, 1, 1),
    val keyLabels: List<List<String>> =
        listOf(
            listOf("Q", "W", "E", "R"),
            listOf("A", "S", "D", "F"),
            listOf("Z", "X", "C", "V"),
        ),
)

fun PersistedLayoutSettings.toAppLayoutSettings(): AppLayoutSettings =
    AppLayoutSettings(
        columnWeights = mutableStateListOf<Int>().apply { addAll(columnWeights) },
        rowWeights = mutableStateListOf<Int>().apply { addAll(rowWeights) },
        keyLabels =
            mutableStateListOf<SnapshotStateList<String>>().apply {
                keyLabels.mapTo(this) { row ->
                    mutableStateListOf<String>().apply { addAll(row) }
                }
            },
    )
