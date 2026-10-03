package com.adrinand.gokfre.core.screen

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import java.awt.Frame
import java.awt.GraphicsEnvironment

class AwtFullscreenTest {
    @Before
    fun requireDisplay() {
        assumeFalse("Skipping: headless environment", GraphicsEnvironment.isHeadless())
    }

    @Test
    fun setFullscreenMakesWindowFullscreen() {
        runBlocking {
            val frame = Frame()
            frame.addNotify()
            try {
                AwtFullscreen.setFullscreen(frame)

                val device = frame.graphicsConfiguration.device
                device.fullScreenWindow shouldBe frame
            } finally {
                frame.dispose()
            }
        }
    }

    @Test
    fun setFullscreenHandlesNonDisplayableWindowWithoutCrashing() {
        runBlocking {
            val frame = Frame()

            AwtFullscreen.setFullscreen(frame)

            frame.dispose()
        }
    }
}
