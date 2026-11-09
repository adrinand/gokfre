package com.adrinand.gokfre

import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

class SingleInstanceTest {
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `second app instance refuses to start while another is running`() {
        assumeDesktopEnvironment()

        val app1 = E2EApplication.launch()
        try {
            val secondLaunch = runCatching { E2EApplication.launch() }
            secondLaunch.exceptionOrNull() shouldNotBe null
        } finally {
            app1.stop()
        }
    }
}
