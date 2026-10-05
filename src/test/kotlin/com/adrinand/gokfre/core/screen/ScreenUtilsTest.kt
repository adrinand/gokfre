package com.adrinand.gokfre.core.screen

import io.kotest.matchers.shouldBe
import org.junit.Assume.assumeFalse
import org.junit.Test
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Toolkit

class ScreenUtilsTest {
    @Test
    fun `isLinux matches the running OS`() {
        val expected = System.getProperty("os.name").lowercase().contains("linux")

        isLinux() shouldBe expected
    }

    @Test
    fun `workAreaBounds excludes status bar and dock insets`() {
        assumeFalse("Skipping: headless environment", GraphicsEnvironment.isHeadless())
        val frame = Frame()
        frame.addNotify()
        try {
            val screen = frame.graphicsConfiguration.bounds
            val insets = Toolkit.getDefaultToolkit().getScreenInsets(frame.graphicsConfiguration)

            val workArea = frame.workAreaBounds()

            workArea.x shouldBe screen.x + insets.left
            workArea.y shouldBe screen.y + insets.top
            workArea.width shouldBe screen.width - insets.left - insets.right
            workArea.height shouldBe screen.height - insets.top - insets.bottom
        } finally {
            frame.dispose()
        }
    }
}
