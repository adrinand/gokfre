package com.adrinand.kofre.ui.tray

/**
 * Callbacks exposed by the tray menu.
 *
 * Implementations are read from the controller when a menu item is activated,
 * so the tray manager does not need to be recreated when the callbacks change.
 */
interface TrayController {
    val onToggle: () -> Unit
    val onSettings: () -> Unit
    val onQuit: () -> Unit
}

/**
 * Creates a [TrayController] that delegates to the provided callbacks.
 */
fun TrayController(
    onToggle: () -> Unit,
    onSettings: () -> Unit,
    onQuit: () -> Unit,
): TrayController =
    object : TrayController {
        override val onToggle: () -> Unit = onToggle
        override val onSettings: () -> Unit = onSettings
        override val onQuit: () -> Unit = onQuit
    }
