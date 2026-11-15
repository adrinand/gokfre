package com.adrinand.gokfre.core.screen

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.Test
import java.awt.Rectangle

class KdeScriptRendererTest {
    private val tokenPlaceholder = "__GOKFRE_TOKEN__"
    private val windowId = "window-id"

    @Test
    fun `injectToken replaces the placeholder with a quoted token`() {
        val script = KdeScriptRenderer.injectToken("report($tokenPlaceholder);", "abc123")

        script shouldContain "report(\"abc123\");"
    }

    @Test
    fun `injectToken escapes quotes and backslashes`() {
        val script = KdeScriptRenderer.injectToken("report($tokenPlaceholder);", "a\"b\\c")

        script shouldContain "\"a\\\"b\\\\c\""
    }

    @Test
    fun `capture script supports Plasma 5 and 6 window APIs`() {
        val script = KdeScriptRenderer.capture()

        script shouldContain "workspace.activeWindow"
        script shouldContain "workspace.activeClient"
        script shouldContain "workspace.windowList"
        script shouldContain "workspace.clientList"
        script shouldContain "frameGeometry"
    }

    @Test
    fun `moveResize embeds the requested bounds`() {
        val script = KdeScriptRenderer.moveResize(windowId, Rectangle(10, 20, 300, 400))

        script shouldContain "x: 10"
        script shouldContain "y: 20"
        script shouldContain "width: 300"
        script shouldContain "height: 400"
        script shouldContain "\"$windowId\""
    }

    @Test
    fun `scripts report through the gokfre bridge`() {
        val script = KdeScriptRenderer.capture()

        script shouldContain KDE_BRIDGE_NAME
        script shouldContain KDE_BRIDGE_PATH
        script shouldContain "'Report'"
    }

    @Test
    fun `preview scripts target the gokfre preview window`() {
        val maximize = KdeScriptRenderer.maximizePreview()
        val restore = KdeScriptRenderer.restorePreview()

        maximize shouldContain "Gokfre Preview"
        maximize shouldContain "previewWindow()"
        restore shouldContain "Gokfre Preview"
        restore shouldContain "unmaximize"
    }

    @Test
    fun `bounds script queries the captured window geometry`() {
        val script = KdeScriptRenderer.bounds(windowId)

        script shouldContain "\"bounds\""
        script shouldContain "\"$windowId\""
        script shouldContain "frameGeometry"
    }

    @Test
    fun `rendered scripts leave no placeholders`() {
        val operations =
            listOf(
                KdeScriptRenderer.capture(),
                KdeScriptRenderer.focus(windowId),
                KdeScriptRenderer.bounds(windowId),
                KdeScriptRenderer.moveResize(windowId, Rectangle(1, 2, 3, 4)),
                KdeScriptRenderer.maximizePreview(),
                KdeScriptRenderer.restorePreview(),
                KdeScriptRenderer.isPreviewMaximized(),
                KdeScriptRenderer.probe(),
            )

        operations.forEach { script ->
            val rendered = KdeScriptRenderer.injectToken(script, "token")
            rendered shouldNotContain "__GOKFRE_"
        }
    }
}
