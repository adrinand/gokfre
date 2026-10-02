package com.adrinand.kofre.core.screen

import io.kotest.matchers.shouldBe
import org.junit.Test

class ScreenUtilsTest {
    @Test
    fun `isLinux matches the running OS`() {
        val expected = System.getProperty("os.name").lowercase().contains("linux")

        isLinux() shouldBe expected
    }
}
