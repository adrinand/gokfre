package com.adrinand.gokfre.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adrinand.gokfre.viewmodel.SettingsViewModel

private const val SCREEN_FONT_DIVISOR = 30f
private const val CELL_FIT_FACTOR = 0.30f

@Composable
fun LayoutPreviewScreen(
    viewModel: SettingsViewModel,
    selectedKeys: Set<String> = emptySet(),
) {
    val layout = viewModel.layoutSettings

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().testTag("layout-preview"),
    ) {
        val baseFontSize = (maxHeight.value / SCREEN_FONT_DIVISOR).sp

        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            layout.rowWeights.forEachIndexed { rowIndex, rowWeight ->
                if (rowWeight > 0) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(rowWeight.toFloat()),
                    ) {
                        layout.columnWeights.forEachIndexed { colIndex, colWeight ->
                            if (colWeight > 0) {
                                val label = layout.keyLabels[rowIndex].getOrElse(colIndex) { "?" }
                                PreviewCell(
                                    label = label,
                                    isSelected = label.uppercase() in selectedKeys,
                                    baseFontSize = baseFontSize,
                                    modifier =
                                        Modifier
                                            .weight(colWeight.toFloat())
                                            .fillMaxHeight(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewCell(
    label: String,
    isSelected: Boolean,
    baseFontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    val purple = Color(PURPLE_COLOR)
    val backgroundColor =
        if (isSelected) {
            purple.copy(alpha = 0.5f)
        } else {
            Color.Black.copy(alpha = 0.3f)
        }

    BoxWithConstraints(
        modifier =
            modifier
                .background(backgroundColor)
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = purple,
                ).padding(vertical = 4.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        val cellFitLimit = (minOf(maxWidth, maxHeight).value * CELL_FIT_FACTOR).sp
        val fontSize = minOf(baseFontSize.value, cellFitLimit.value).sp
        Text(
            text = label,
            color = purple,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
        )
    }
}
