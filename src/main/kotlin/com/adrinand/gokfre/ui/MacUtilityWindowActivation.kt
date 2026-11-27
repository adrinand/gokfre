package com.adrinand.gokfre.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.adrinand.gokfre.core.screen.MacWindowManager
import com.adrinand.gokfre.core.screen.isMac
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

/** Raises a newly opened utility window on macOS `LSUIElement` apps. */
@Composable
internal fun MacUtilityWindowActivation(
    window: Window,
    onActivate: (Window) -> Unit = { MacWindowManager.configureUtilityWindow(it) },
) {
    if (isMac()) {
        DisposableEffect(window) {
            val listener =
                object : WindowAdapter() {
                    override fun windowOpened(e: WindowEvent) {
                        onActivate(window)
                    }
                }
            if (window.isShowing) {
                onActivate(window)
            } else {
                window.addWindowListener(listener)
            }
            onDispose { window.removeWindowListener(listener) }
        }
    }
}
