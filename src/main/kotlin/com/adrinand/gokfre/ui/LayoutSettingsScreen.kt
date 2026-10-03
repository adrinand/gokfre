package com.adrinand.gokfre.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adrinand.gokfre.core.hotkey.getDisplayCharFromKeyEvent
import com.adrinand.gokfre.viewmodel.SettingsViewModel

@Suppress("CyclomaticComplexMethod", "LongMethod", "CognitiveComplexMethod", "ComplexCondition")
@Composable
fun LayoutSettingsScreen(viewModel: SettingsViewModel) {
    val layoutSettings = viewModel.layoutSettings
    val columnWeights = layoutSettings.columnWeights
    val rowWeights = layoutSettings.rowWeights
    val keyLabels = layoutSettings.keyLabels
    val focusRequester = remember { FocusRequester() }
    val selectedPosition = remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val usedKeys = remember { mutableStateOf(keyLabels.flatten().toSet()) }
    val showDialog = remember { mutableStateOf(false) }
    val dialogMessage = remember { mutableStateOf("") }

    fun addNewRow() {
        val newRowKeys = List(columnWeights.size) { "?" }
        rowWeights.add(1)
        keyLabels.add(mutableStateListOf<String>().apply { addAll(newRowKeys) })
    }

    fun removeRow(index: Int) {
        val removedKeys = keyLabels[index].toSet()
        keyLabels.removeAt(index)
        rowWeights.removeAt(index)
        usedKeys.value -= removedKeys
        selectedPosition.value?.let { (row, _) -> if (row == index) selectedPosition.value = null }
    }

    fun clearSelectionIfHidden() {
        selectedPosition.value?.let { (row, col) ->
            if (row >= rowWeights.size || col >= columnWeights.size || columnWeights[col] == 0) {
                selectedPosition.value = null
            }
        }
    }

    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(16.dp)
                .focusRequester(focusRequester)
                .testTag("layout-screen")
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        val displayChar = keyEvent.getDisplayCharFromKeyEvent()
                        if (displayChar != null &&
                            displayChar.length == 1 &&
                            displayChar[0].isLetterOrDigit()
                        ) {
                            selectedPosition.value?.let { (row, col) ->
                                if (row in keyLabels.indices &&
                                    col in keyLabels[row].indices
                                ) {
                                    val oldKey = keyLabels[row][col]
                                    val newKey = displayChar.uppercase()

                                    if (usedKeys.value.contains(newKey)) {
                                        showDialog.value = true
                                        dialogMessage.value =
                                            "Selected key is already added to the layout.\n" +
                                            "Please, replace current position with " +
                                            "another key and try again."
                                    } else {
                                        keyLabels[row][col] = newKey
                                        usedKeys.value =
                                            usedKeys.value - oldKey + newKey
                                        selectedPosition.value = null
                                    }
                                    return@onKeyEvent true
                                }
                            }
                        }
                    }
                    false
                }
                .focusable(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        Text(
            text = "Column and row weights",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 20.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(modifier = Modifier.width(80.dp))
            columnWeights.forEachIndexed { colIndex, weight ->
                WeightControl(
                    value = weight,
                    onIncrement = {
                        columnWeights[colIndex] = (columnWeights[colIndex] + 1).coerceAtLeast(0)
                    },
                    onDecrement = {
                        val newVal = (columnWeights[colIndex] - 1).coerceAtLeast(0)
                        columnWeights[colIndex] = newVal
                        clearSelectionIfHidden()
                    },
                    modifier = Modifier.weight(1f),
                    testTag = "col-$colIndex",
                )
            }
        }

        rowWeights.forEachIndexed { rowIndex, rowWeight ->
            if (rowWeight > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth().weight(rowWeight.toFloat()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WeightControl(
                        value = rowWeight,
                        onIncrement = {
                            rowWeights[rowIndex] = (rowWeights[rowIndex] + 1).coerceAtLeast(0)
                        },
                        onDecrement = {
                            val newVal = (rowWeights[rowIndex] - 1).coerceAtLeast(0)
                            if (newVal == 0) {
                                removeRow(rowIndex)
                            } else {
                                rowWeights[rowIndex] = newVal
                            }
                            clearSelectionIfHidden()
                        },
                        modifier = Modifier.width(80.dp),
                        testTag = "row-$rowIndex",
                    )

                    Row(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val rowLabels = keyLabels[rowIndex]
                        columnWeights.forEachIndexed { colIndex, colWeight ->
                            if (colWeight > 0) {
                                val label = rowLabels.getOrElse(colIndex) { "?" }
                                KeyTile(
                                    label = label,
                                    isSelected = selectedPosition.value == rowIndex to colIndex,
                                    onClick = {
                                        selectedPosition.value =
                                            if (selectedPosition.value ==
                                                rowIndex to colIndex
                                            ) {
                                                null
                                            } else {
                                                rowIndex to colIndex
                                            }
                                    },
                                    modifier =
                                        Modifier.weight(colWeight.toFloat())
                                            .fillMaxHeight(),
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            TextButton(onClick = { addNewRow() }, enabled = rowWeights.size < MAX_ROWS) {
                Text("Add Row")
            }
        }

        if (showDialog.value) {
            AlertDialog(
                onDismissRequest = { showDialog.value = false },
                title = { Text("Error") },
                text = { Text(dialogMessage.value) },
                confirmButton = {
                    TextButton(onClick = { showDialog.value = false }) { Text("OK") }
                },
                dismissButton = null,
            )
        }
    }
}

@Composable
fun WeightControl(
    value: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = value.toString(),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(24.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text(
            text = "−",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier =
                Modifier.size(28.dp).testTag(testTag?.let { "$it-minus" } ?: "").clickable(
                    enabled = enabled && value > 0,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDecrement() },
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text(
            text = "+",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier =
                Modifier.size(28.dp).testTag(testTag?.let { "$it-plus" } ?: "").clickable(
                    enabled = enabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onIncrement() },
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
fun KeyTile(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val purple = Color(PURPLE_COLOR)

    Surface(
        modifier =
            modifier
                .clickable { onClick() }
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = 2.dp,
                            color = MaterialTheme.colors.primary,
                            shape = RoundedCornerShape(4.dp),
                        )
                    } else {
                        Modifier
                    },
                ),
        color =
            if (isSelected) {
                purple.copy(alpha = 0.2f)
            } else {
                MaterialTheme.colors.surface.copy(alpha = 0.5f)
            },
        shape = RoundedCornerShape(4.dp),
        elevation = 2.dp,
    ) {
        Box(
            modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
