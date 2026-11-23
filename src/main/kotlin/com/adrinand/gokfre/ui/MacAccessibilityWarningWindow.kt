package com.adrinand.gokfre.ui

import androidx.compose.runtime.Composable
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
        "and register the global hotkey."
private const val INSTRUCTION_TEXT =
    "Grant Accessibility permission in System Settings > Privacy & Security, " +
        "then restart Gokfre."

private val logger = Logger.getLogger("com.adrinand.gokfre.ui.MacAccessibilityWarningWindow")

private val MAC_WARNING_CONTENT =
    WarningContent(
        windowTitle = WINDOW_TITLE,
        sections =
            listOf(
                WarningSection(
                    title = TITLE,
                    lines =
                        listOf(
                            WarningLine(BODY_TEXT),
                            WarningLine(INSTRUCTION_TEXT, WarningLineStyle.Emphasis),
                        ),
                ),
            ),
        actionLabel = SETTINGS_BUTTON_LABEL,
        dismissLabel = DISMISS_BUTTON_LABEL,
    )

@Composable
fun MacAccessibilityWarningWindow(
    onDismiss: () -> Unit,
    onOpenSystemSettings: () -> Unit,
) {
    WarningWindow(
        content = MAC_WARNING_CONTENT,
        onDismiss = onDismiss,
        onAction = onOpenSystemSettings,
        widthDp = WINDOW_WIDTH_DP,
        heightDp = WINDOW_HEIGHT_DP,
    )
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
