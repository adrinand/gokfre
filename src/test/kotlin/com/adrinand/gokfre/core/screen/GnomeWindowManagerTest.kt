package com.adrinand.gokfre.core.screen

import io.kotest.matchers.shouldBe
import org.junit.Test
import java.awt.Rectangle

class GnomeWindowManagerTest {
    @Test
    fun `parses the logical work area reported by the extension`() {
        parseCapturedWorkArea("(true, 0, 32, 1920, 1048)") shouldBe
            Rectangle(0, 32, 1920, 1048)
    }

    @Test
    fun `returns null when an older extension reports no work area`() {
        parseCapturedWorkArea("(true)") shouldBe null
    }

    @Test
    fun `returns null for a failed capture`() {
        parseCapturedWorkArea("(false, 0, 0, 0, 0)") shouldBe null
    }

    @Test
    fun `returns null for empty work areas`() {
        parseCapturedWorkArea("(true, 0, 0, 0, 0)") shouldBe null
    }

    @Test
    fun `returns null for negative work area dimensions`() {
        parseCapturedWorkArea("(true, 0, 0, -1920, 1048)") shouldBe null
    }

    @Test
    fun `parses the atomic show preview reply`() {
        parseShowPreviewResult("(true, true)") shouldBe (true to true)
        parseShowPreviewResult("(true, false)") shouldBe (true to false)
        parseShowPreviewResult("(false, false)") shouldBe (false to false)
    }

    @Test
    fun `returns null for an unknown show preview reply`() {
        parseShowPreviewResult("(true)") shouldBe null
        parseShowPreviewResult("") shouldBe null
    }

    @Test
    fun `ignores merged stderr before the show preview reply`() {
        parseShowPreviewResult("(process:1): GLib-WARNING: something\n(true, false)\n") shouldBe (true to false)
    }

    @Test
    fun `detects the pid-aware show preview method`() {
        val xml = """<method name="ShowPreview"><arg type="i" name="pid" direction="in"/></method>"""

        parseSupportsShowPreview(xml) shouldBe true
    }

    @Test
    fun `does not detect an older show preview shape`() {
        parseSupportsShowPreview("""<method name="PlacePreview"><arg type="i" name="x"/></method>""") shouldBe false
        parseSupportsShowPreview("""<method name="ShowPreview"><arg type="i" name="x"/></method>""") shouldBe false
    }
}
