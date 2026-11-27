package com.adrinand.gokfre.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.adrinand.gokfre.core.screen.MacUtilityWindowActivator
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

/** Raises a newly opened utility window on macOS `LSUIElement` apps. */
@Composable
internal fun MacUtilityWindowActivation(
    window: Window,
    activator: MacUtilityWindowActivator?,
) {
    if (activator == null) {
        return
    }
    DisposableEffect(window, activator) {
        val listener =
            object : WindowAdapter() {
                override fun windowOpened(e: WindowEvent) {
                    activator.activate(window)
                }
            }
        if (window.isShowing) {
            activator.activate(window)
        } else {
            window.addWindowListener(listener)
        }
        onDispose { window.removeWindowListener(listener) }
    }
}
