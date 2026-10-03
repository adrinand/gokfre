package com.adrinand.gokfre.core.screen

import java.awt.Window

/**
 * Fallback fullscreen helper using AWT's fullscreen window API.
 */
object AwtFullscreen {
    fun setFullscreen(window: Window) {
        try {
            window.graphicsConfiguration.device.fullScreenWindow = window
        } catch (_: Exception) {
            println("FullscreenHelper: AWT fullscreen failed")
        }
    }
}
