package com.adrinand.gokfre.ui

enum class WarningLineStyle { Body, Emphasis, Code }

data class WarningLine(
    val text: String,
    val style: WarningLineStyle = WarningLineStyle.Body,
)

data class WarningSection(
    val title: String,
    val lines: List<WarningLine>,
)

data class WarningContent(
    val windowTitle: String,
    val sections: List<WarningSection>,
    val actionLabel: String? = null,
    val dismissLabel: String = "OK",
)
