package com.adrinand.gokfre.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.adrinand.gokfre.core.screen.configureMacUtilityWindow
import com.adrinand.gokfre.core.screen.isMac
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

/** Raises a newly opened utility window on macOS `LSUIElement` apps. */
@Composable
internal fun MacUtilityWindowActivation(window: Window) {
    if (isMac()) {
        DisposableEffect(window) {
            val listener =
                object : WindowAdapter() {
                    override fun windowOpened(e: WindowEvent) {
                        configureMacUtilityWindow(window)
                    }
                }
            if (window.isShowing) {
                configureMacUtilityWindow(window)
            } else {
                window.addWindowListener(listener)
            }
            onDispose { window.removeWindowListener(listener) }
        }
    }
}
