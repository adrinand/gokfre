package com.adrinand.gokfre.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import java.util.logging.Logger

private const val WINDOW_WIDTH_DP = 560
private const val WINDOW_HEIGHT_DP = 280
private const val WINDOW_TITLE = "Gokfre Permissions"
private const val ACCESSIBILITY_SETTINGS_URL =
    "x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility"
private const val SETTINGS_BUTTON_LABEL = "Open System Settings"
private const val DISMISS_BUTTON_LABEL = "OK"
private const val TITLE = "Accessibility permission required"
private const val BODY_TEXT =
    "macOS requires Gokfre to have Accessibility permission to arrange windows " +
        "and Input Monitoring permission for the global hotkey."
private const val INSTRUCTION_TEXT =
    "Grant both permissions in System Settings > Privacy & Security, " +
        "then restart Gokfre."

private val logger = Logger.getLogger("com.adrinand.gokfre.ui.MacAccessibilityWarningWindow")

@Composable
fun MacAccessibilityWarningWindow(
    onDismiss: () -> Unit,
    onOpenSystemSettings: () -> Unit,
) {
    Window(
        onCloseRequest = onDismiss,
        title = WINDOW_TITLE,
        state = rememberWindowState(width = WINDOW_WIDTH_DP.dp, height = WINDOW_HEIGHT_DP.dp),
    ) {
        MacAccessibilityWarningDialog(
            onDismiss = onDismiss,
            onOpenSystemSettings = onOpenSystemSettings,
        )
    }
}

@Composable
fun MacAccessibilityWarningDialog(
    onDismiss: () -> Unit,
    onOpenSystemSettings: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(TITLE, style = MaterialTheme.typography.h6)
        Text(
            BODY_TEXT,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            INSTRUCTION_TEXT,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp),
        )
        Row(modifier = Modifier.padding(top = 16.dp)) {
            Button(
                onClick = onOpenSystemSettings,
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Text(SETTINGS_BUTTON_LABEL)
            }
            Button(onClick = onDismiss) {
                Text(DISMISS_BUTTON_LABEL)
            }
        }
    }
}

fun openAccessibilitySettings() {
    runCatching {
        ProcessBuilder("open", ACCESSIBILITY_SETTINGS_URL)
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
    }.onFailure {
        logger.warning("Failed to open System Settings: ${it.message}")
    }
}
