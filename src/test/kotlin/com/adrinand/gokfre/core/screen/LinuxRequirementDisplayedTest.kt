package com.adrinand.gokfre.core.screen

import io.kotest.matchers.collections.shouldContainExactly
import org.junit.Test

class LinuxRequirementDisplayedTest {
    @Test
    fun `hotkey failure adds a listener warning`() {
        LinuxRequirementChecker.displayedMissingRequirements(
            detected = emptyList(),
            hotkeyListenerFailed = true,
        ) shouldContainExactly listOf(LinuxRequirement.HOTKEY_LISTENER)
    }

    @Test
    fun `hotkey failure does not duplicate the input warning`() {
        LinuxRequirementChecker.displayedMissingRequirements(
            detected = listOf(LinuxRequirement.INPUT_DEVICE_ACCESS),
            hotkeyListenerFailed = true,
        ) shouldContainExactly listOf(LinuxRequirement.INPUT_DEVICE_ACCESS)
    }

    @Test
    fun `hotkey available keeps the detected requirements`() {
        LinuxRequirementChecker.displayedMissingRequirements(
            detected = listOf(LinuxRequirement.GNOME_SHELL_EXTENSION),
            hotkeyListenerFailed = false,
        ) shouldContainExactly listOf(LinuxRequirement.GNOME_SHELL_EXTENSION)
    }
}
