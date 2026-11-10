package com.adrinand.gokfre

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.awt.Rectangle
import java.awt.event.KeyEvent
import java.util.concurrent.TimeUnit
import kotlin.math.abs

private const val ARRANGEMENT_TIMEOUT_MS = 5_000L
private const val PREVIEW_SETTLE_MS = 500L
private const val ESCAPE_CLOSE_TIMEOUT_MS = 2_000L
private const val MAX_ATTEMPTS = 3
private const val GEOMETRY_TOLERANCE_PX = 8

private data class ArrangementCase(
    val keys: List<Char>,
    val description: String,
)

private val ARRANGEMENT_CASES =
    listOf(
        ArrangementCase(listOf('Q', 'Q'), "single top-left cell"),
        ArrangementCase(listOf('Q', 'W'), "top-left two cells"),
        ArrangementCase(listOf('A', 'D'), "middle row"),
        ArrangementCase(listOf('R', 'V'), "right column"),
        ArrangementCase(listOf('Q', 'V'), "full screen"),
    )

class ArrangementTest {
    @RegisterExtension
    val e2e = E2EExtension()

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    fun `repeated arrangements resize the active window across five layouts`() {
        requireArrangementEnvironment()

        val target = TestTargetWindow()

        try {
            val initialBounds = target.bounds
            check(initialBounds != Rectangle(0, 0, 0, 0)) { "Target window has invalid initial bounds" }
            val results = arrangeAll(target)
            assertDistinct(results)
            assertRelativeGeometry(results)
            assertCoversWorkArea(results.last(), target.workArea)
        } finally {
            target.close()
        }
    }

    private fun arrangeAll(target: TestTargetWindow): List<Rectangle> =
        ARRANGEMENT_CASES.map { case ->
            val before = target.bounds
            val arranged = arrangeWithRetry(target, case, before)
            check(arranged != before) {
                "Case '${case.description}' did not resize the target window (bounds stayed $before)"
            }
            arranged
        }

    private fun arrangeWithRetry(
        target: TestTargetWindow,
        case: ArrangementCase,
        before: Rectangle,
    ): Rectangle {
        var lastFailure: String? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            target.focus()
            e2e.app.triggerToggle()
            val preview = waitForPreviewWindow()
            if (preview == null) {
                lastFailure = "preview window did not appear"
            } else {
                Thread.sleep(PREVIEW_SETTLE_MS)
                case.keys.forEach { sendKeyToWindow(preview, keyCodeForChar(it)) }
                if (waitForPreviewClosed(ARRANGEMENT_TIMEOUT_MS)) {
                    val after = target.bounds
                    if (after != before) {
                        return after
                    }
                    lastFailure = "target window bounds did not change after '${case.description}'"
                } else {
                    sendKeyToWindow(preview, KeyEvent.VK_ESCAPE)
                    waitForPreviewClosed(ESCAPE_CLOSE_TIMEOUT_MS)
                    lastFailure = "preview did not close after selecting keys"
                }
            }
            if (attempt < MAX_ATTEMPTS - 1) {
                target.focus()
            }
        }
        error("$lastFailure after $MAX_ATTEMPTS attempts")
    }

    private fun assertDistinct(results: List<Rectangle>) {
        results.forEachIndexed { index, bounds ->
            for (other in index + 1 until results.size) {
                check(bounds != results[other]) {
                    "Cases '${ARRANGEMENT_CASES[index].description}' and " +
                        "'${ARRANGEMENT_CASES[other].description}' produced identical bounds $bounds"
                }
            }
        }
    }

    private fun assertRelativeGeometry(results: List<Rectangle>) {
        val (cell, twoCells, middleRow, rightColumn, fullScreen) = results

        assertApprox(cell.x, twoCells.x, "left edge of single cell vs two cells")
        assertApprox(cell.y, twoCells.y, "top edge of single cell vs two cells")
        assertApprox(cell.height, twoCells.height, "height of single cell vs two cells")
        check(twoCells.width > cell.width + GEOMETRY_TOLERANCE_PX) {
            "Two top-row cells should be wider than a single cell: $twoCells vs $cell"
        }

        assertApprox(cell.x, middleRow.x, "left edge of top row vs middle row")
        assertApprox(cell.height, middleRow.height, "height of top row vs middle row")
        check(middleRow.y > cell.y + GEOMETRY_TOLERANCE_PX) {
            "Middle row should start below the top row: $middleRow vs $cell"
        }
        check(middleRow.width > cell.width + GEOMETRY_TOLERANCE_PX) {
            "Middle row should be wider than a single cell: $middleRow vs $cell"
        }

        assertApprox(cell.y, rightColumn.y, "top edge of left cell vs right column")
        assertApprox(cell.width, rightColumn.width, "column width of left vs right column")
        check(rightColumn.x > cell.x + GEOMETRY_TOLERANCE_PX) {
            "Right column should start right of the left cell: $rightColumn vs $cell"
        }
        assertApprox(fullScreen.height, rightColumn.height, "height of right column vs full screen")
        assertApprox(
            fullScreen.x + fullScreen.width,
            rightColumn.x + rightColumn.width,
            "right edge of right column vs full screen",
        )

        assertApprox(fullScreen.x, cell.x, "left edge of full screen vs single cell")
        assertApprox(fullScreen.y, cell.y, "top edge of full screen vs single cell")
        check(fullScreen.width > twoCells.width + GEOMETRY_TOLERANCE_PX) {
            "Full screen should be wider than two top-row cells: $fullScreen vs $twoCells"
        }
        check(fullScreen.height > middleRow.height + GEOMETRY_TOLERANCE_PX) {
            "Full screen should be taller than one row: $fullScreen vs $middleRow"
        }
        check(fullScreen.width > rightColumn.width + GEOMETRY_TOLERANCE_PX) {
            "Full screen should be wider than the right column: $fullScreen vs $rightColumn"
        }
    }

    private fun assertCoversWorkArea(
        fullScreen: Rectangle,
        workArea: Rectangle,
    ) {
        check(fullScreen.x <= workArea.x + GEOMETRY_TOLERANCE_PX) {
            "Full screen layout starts left of the work area: $fullScreen vs $workArea"
        }
        check(fullScreen.y <= workArea.y + GEOMETRY_TOLERANCE_PX) {
            "Full screen layout starts above the work area: $fullScreen vs $workArea"
        }
        check(fullScreen.x + fullScreen.width >= workArea.x + workArea.width - GEOMETRY_TOLERANCE_PX) {
            "Full screen layout does not reach the right edge of the work area: $fullScreen vs $workArea"
        }
        check(fullScreen.y + fullScreen.height >= workArea.y + workArea.height - GEOMETRY_TOLERANCE_PX) {
            "Full screen layout does not reach the bottom edge of the work area: $fullScreen vs $workArea"
        }
    }

    private fun assertApprox(
        expected: Int,
        actual: Int,
        label: String,
    ) {
        check(abs(expected - actual) <= GEOMETRY_TOLERANCE_PX) {
            "$label: expected $expected ± $GEOMETRY_TOLERANCE_PX but was $actual"
        }
    }
}
