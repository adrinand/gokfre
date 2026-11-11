package com.adrinand.gokfre.core.screen

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeFalse
import org.junit.Test
import java.awt.GraphicsEnvironment
import java.awt.Rectangle

class ArrangementControllerTest {
    @Test
    fun `arrange applies bounds to captured target window`() {
        runBlocking {
            val windowManager = HeadlessWindowManager()
            val controller = ArrangementController(windowManager)
            controller.captureTargetWindow()

            val bounds =
                controller.arrange(
                    rowWeights = listOf(1, 1),
                    columnWeights = listOf(1, 1, 1, 1),
                    keyLabels =
                        listOf(
                            listOf("Q", "W", "E", "R"),
                            listOf("A", "S", "D", "F"),
                        ),
                    selectedKeys = listOf("Q", "F"),
                    workArea = Rectangle(0, 0, 1000, 800),
                )

            bounds shouldBe Rectangle(0, 0, 1000, 800)
            windowManager.lastBounds shouldBe Rectangle(0, 0, 1000, 800)
            val expectedHandle = WindowHandle.X11(HeadlessWindowManager.DEFAULT_HEADLESS_WINDOW_ID)
            windowManager.lastActiveWindowHandle shouldBe expectedHandle
        }
    }

    @Test
    fun `arrange uses the work area of the captured window monitor`() {
        assumeFalse("Skipping: headless environment", GraphicsEnvironment.isHeadless())
        runBlocking {
            val configuration =
                GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
            val screen = configuration.bounds
            val windowManager =
                HeadlessWindowManager().apply {
                    activeWindowBounds = Rectangle(screen.x + 10, screen.y + 10, 100, 100)
                }
            val controller = ArrangementController(windowManager)
            controller.captureTargetWindow()

            val bounds =
                controller.arrange(
                    rowWeights = listOf(1),
                    columnWeights = listOf(1),
                    keyLabels = listOf(listOf("Q")),
                    selectedKeys = listOf("Q"),
                    workArea = Rectangle(0, 0, 100, 100),
                )

            val expected = configuration.workAreaBounds()
            controller.targetWorkArea shouldBe expected
            bounds shouldBe expected
            windowManager.lastBounds shouldBe expected
        }
    }

    @Test
    fun `arrange returns null when no target window was captured`() {
        runBlocking {
            val controller = ArrangementController(HeadlessWindowManager())

            val bounds =
                controller.arrange(
                    rowWeights = listOf(1),
                    columnWeights = listOf(1),
                    keyLabels = listOf(listOf("Q")),
                    selectedKeys = listOf("Q"),
                    workArea = Rectangle(0, 0, 100, 100),
                )

            bounds shouldBe null
        }
    }

    @Test
    fun `arrange returns null for unknown keys`() {
        runBlocking {
            val windowManager = HeadlessWindowManager()
            val controller = ArrangementController(windowManager)
            controller.captureTargetWindow()

            val bounds =
                controller.arrange(
                    rowWeights = listOf(1),
                    columnWeights = listOf(1),
                    keyLabels = listOf(listOf("Q")),
                    selectedKeys = listOf("Z"),
                    workArea = Rectangle(0, 0, 100, 100),
                )

            bounds shouldBe null
        }
    }
}
