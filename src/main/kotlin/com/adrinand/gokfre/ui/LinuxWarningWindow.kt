package com.adrinand.gokfre.ui

import androidx.compose.runtime.Composable
import com.adrinand.gokfre.core.screen.LinuxRequirement
import com.adrinand.gokfre.core.screen.MacUtilityWindowActivator

private const val WINDOW_TITLE = "Gokfre Setup"
private const val BASE_WINDOW_HEIGHT_DP = 180
private const val SECTION_HEIGHT_DP = 140
private const val MAX_WINDOW_HEIGHT_DP = 640

@Composable
fun LinuxWarningWindow(
    requirements: List<LinuxRequirement>,
    onDismiss: () -> Unit,
    utilityWindowActivator: MacUtilityWindowActivator?,
) {
    val content = linuxWarningContent(requirements)
    WarningWindow(
        content = content,
        onDismiss = onDismiss,
        utilityWindowActivator = utilityWindowActivator,
        heightDp = windowHeightDp(content),
    )
}

internal fun linuxWarningContent(requirements: List<LinuxRequirement>): WarningContent =
    WarningContent(
        windowTitle = WINDOW_TITLE,
        sections = requirements.map(::warningSection),
    )

private fun windowHeightDp(content: WarningContent): Int =
    (BASE_WINDOW_HEIGHT_DP + content.sections.size * SECTION_HEIGHT_DP).coerceAtMost(MAX_WINDOW_HEIGHT_DP)
