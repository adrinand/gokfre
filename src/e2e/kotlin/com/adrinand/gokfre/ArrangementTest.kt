package com.adrinand.gokfre

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.awt.Rectangle
import java.awt.event.KeyEvent
import java.util.concurrent.TimeUnit

private const val ARRANGEMENT_TIMEOUT_MS = 5_000L
private const val PREVIEW_SETTLE_MS = 500L
private const val ESCAPE_CLOSE_TIMEOUT_MS = 2_000L
private const val MAX_ATTEMPTS = 3

class ArrangementTest {
    @RegisterExtension
    val e2e = E2EExtension()

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    fun `selecting two layout keys arranges the active window`() {
        requireArrangementEnvironment()

        val target = TestTargetWindow()

        try {
            val initialBounds = target.bounds
            check(initialBounds != Rectangle(0, 0, 0, 0)) { "Target window has invalid initial bounds" }
            target.focus()
            arrangeWithRetry(target, initialBounds)
        } finally {
            target.close()
        }
    }

    private fun arrangeWithRetry(
        target: TestTargetWindow,
        initialBounds: Rectangle,
    ) {
        var lastFailure: String? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            e2e.app.triggerToggle()
            val preview = waitForPreviewWindow()
            if (preview == null) {
                lastFailure = "preview window did not appear"
            } else {
                Thread.sleep(PREVIEW_SETTLE_MS)
                sendKeyToWindow(preview, keyCodeForChar('Q'))
                sendKeyToWindow(preview, keyCodeForChar('V'))
                if (waitForPreviewClosed(ARRANGEMENT_TIMEOUT_MS)) {
                    if (target.bounds != initialBounds) {
                        return
                    }
                    lastFailure = "target window bounds did not change after arrangement"
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
        check(false) { "$lastFailure after $MAX_ATTEMPTS attempts" }
    }
}
