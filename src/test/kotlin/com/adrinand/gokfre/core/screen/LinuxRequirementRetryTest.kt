package com.adrinand.gokfre.core.screen

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

class LinuxRequirementRetryTest {
    @Before
    fun setUp() {
        mockkObject(LinuxRequirementChecker)
        coEvery { LinuxRequirementChecker.checkMissingRequirementsWithRetry(any<Long>()) } coAnswers {
            callOriginal()
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `retry returns empty after a single probe`() {
        every { LinuxRequirementChecker.missingRequirements() } returns emptyList()

        val result = runBlocking { LinuxRequirementChecker.checkMissingRequirementsWithRetry(delayMs = 0) }

        result.shouldBeEmpty()
        verify(exactly = 1) { LinuxRequirementChecker.missingRequirements() }
    }

    @Test
    fun `retry probes again when the first pass finds a requirement`() {
        every { LinuxRequirementChecker.missingRequirements() } returnsMany
            listOf(listOf(LinuxRequirement.GNOME_SHELL_EXTENSION), emptyList())

        val result = runBlocking { LinuxRequirementChecker.checkMissingRequirementsWithRetry(delayMs = 0) }

        result.shouldBeEmpty()
        verify(exactly = 2) { LinuxRequirementChecker.missingRequirements() }
    }

    @Test
    fun `retry returns the second probe result`() {
        every { LinuxRequirementChecker.missingRequirements() } returnsMany
            listOf(
                listOf(LinuxRequirement.GNOME_SHELL_EXTENSION),
                listOf(LinuxRequirement.GNOME_APPINDICATOR_EXTENSION),
            )

        val result = runBlocking { LinuxRequirementChecker.checkMissingRequirementsWithRetry(delayMs = 0) }

        result shouldContainExactly listOf(LinuxRequirement.GNOME_APPINDICATOR_EXTENSION)
    }
}
