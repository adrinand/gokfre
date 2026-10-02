package com.adrinand.kofre.core.screen

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test
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
