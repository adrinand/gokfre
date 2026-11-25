package com.adrinand.gokfre.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState

private const val DEFAULT_WINDOW_WIDTH_DP = 600
private const val DEFAULT_WINDOW_HEIGHT_DP = 320
private const val SECTION_SPACING_DP = 20
private const val LINE_SPACING_DP = 8
private const val CODE_LINE_SPACING_DP = 4
private const val BUTTON_SPACING_DP = 8

@Composable
fun WarningWindow(
    content: WarningContent,
    onDismiss: () -> Unit,
    onAction: (() -> Unit)? = null,
    widthDp: Int = DEFAULT_WINDOW_WIDTH_DP,
    heightDp: Int = DEFAULT_WINDOW_HEIGHT_DP,
) {
    Window(
        onCloseRequest = onDismiss,
        title = content.windowTitle,
        state = rememberWindowState(width = widthDp.dp, height = heightDp.dp),
    ) {
        WarningDialog(content = content, onDismiss = onDismiss, onAction = onAction)
    }
}

@Composable
fun WarningDialog(
    content: WarningContent,
    onDismiss: () -> Unit,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
    ) {
        content.sections.forEachIndexed { index, section ->
            if (index > 0) {
                Spacer(Modifier.height(SECTION_SPACING_DP.dp))
            }
            Text(section.title, style = MaterialTheme.typography.h6)
            section.lines.forEach { line -> WarningLineText(line) }
        }
        Row(
            modifier = Modifier.padding(top = SECTION_SPACING_DP.dp),
            horizontalArrangement = Arrangement.spacedBy(BUTTON_SPACING_DP.dp),
        ) {
            val actionLabel = content.actionLabel
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
            Button(onClick = onDismiss) {
                Text(content.dismissLabel)
            }
        }
    }
}

@Composable
private fun WarningLineText(line: WarningLine) {
    when (line.style) {
        WarningLineStyle.Body ->
            Text(
                line.text,
                modifier = Modifier.padding(top = LINE_SPACING_DP.dp),
            )
        WarningLineStyle.Emphasis ->
            Text(
                line.text,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = LINE_SPACING_DP.dp),
            )
        WarningLineStyle.Code ->
            Text(
                line.text,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = CODE_LINE_SPACING_DP.dp),
            )
    }
}
