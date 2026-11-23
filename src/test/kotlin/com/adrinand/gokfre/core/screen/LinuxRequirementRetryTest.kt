package com.adrinand.gokfre.core.screen

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test

class LinuxRequirementRetryTest {
    @Test
    fun `retry returns empty after a single probe`() {
        var calls = 0
        val result =
            runBlocking {
                LinuxRequirementChecker.checkMissingRequirementsWithRetry(
                    recheckDelayMs = 0,
                    probe = {
                        calls += 1
                        emptyList()
                    },
                )
            }

        result.shouldBeEmpty()
        calls shouldBe 1
    }

    @Test
    fun `retry probes again when the first pass finds a requirement`() {
        var calls = 0
        val result =
            runBlocking {
                LinuxRequirementChecker.checkMissingRequirementsWithRetry(
                    recheckDelayMs = 0,
                    probe = {
                        calls += 1
                        if (calls == 1) listOf(LinuxRequirement.GNOME_SHELL_EXTENSION) else emptyList()
                    },
                )
            }

        result.shouldBeEmpty()
        calls shouldBe 2
    }

    @Test
    fun `retry returns the second probe result`() {
        var calls = 0
        val result =
            runBlocking {
                LinuxRequirementChecker.checkMissingRequirementsWithRetry(
                    recheckDelayMs = 0,
                    probe = {
                        calls += 1
                        if (calls == 1) {
                            listOf(LinuxRequirement.GNOME_SHELL_EXTENSION)
                        } else {
                            listOf(LinuxRequirement.GNOME_APPINDICATOR_EXTENSION)
                        }
                    },
                )
            }

        result shouldContainExactly listOf(LinuxRequirement.GNOME_APPINDICATOR_EXTENSION)
    }
}
