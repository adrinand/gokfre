package com.adrinand.kofre.ui

import io.kotest.matchers.shouldNotBe
import org.junit.Test

class TrayIconTest {
    @Test
    fun `createTrayIcon returns a painter with non-zero size`() {
        val painter = createTrayIcon()

        painter shouldNotBe null
        painter.intrinsicSize.width shouldNotBe 0f
        painter.intrinsicSize.height shouldNotBe 0f
    }
}
