package com.adrinand.gokfre

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.TimeUnit

class AppStartupTest {
    @RegisterExtension
    val e2e = E2EExtension()

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `app launches and becomes ready`() {
        // E2EExtension already launched the app and waited for ready.
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `app toggles preview window`() {
        e2e.app.triggerToggle()
        val preview = waitForPreviewWindow()
        check(preview != null) { "Preview window did not appear after toggle" }

        e2e.app.triggerToggle()
        val closed = waitForPreviewClosed()
        check(closed) { "Preview window did not close after second toggle" }
    }
}
