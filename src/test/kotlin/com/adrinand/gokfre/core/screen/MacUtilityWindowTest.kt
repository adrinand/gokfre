package com.adrinand.gokfre.core.screen

import org.junit.Assume.assumeFalse
import org.junit.Test
import java.awt.Frame
import java.awt.GraphicsEnvironment

class MacUtilityWindowTest {
    @Test
    fun `configureMacUtilityWindow is a no-op outside macOS`() {
        assumeFalse("Skipping: macOS behavior", isMac())
        assumeFalse("Skipping: headless environment", GraphicsEnvironment.isHeadless())
        val frame = Frame()
        try {
            configureMacUtilityWindow(frame)
        } finally {
            frame.dispose()
        }
    }
}
