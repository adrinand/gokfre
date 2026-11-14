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
}
