package com.adrinand.gokfre.ui

import com.adrinand.gokfre.core.screen.LinuxRequirement
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.Test

class LinuxWarningContentTest {
    @Test
    fun `every requirement maps to a single section`() {
        LinuxRequirement.entries.forEach { requirement ->
            val content = linuxWarningContent(listOf(requirement))

            content.sections.size shouldBe 1
            content.sections.single().title.isNotBlank() shouldBe true
            content.sections.single().lines.isNotEmpty() shouldBe true
        }
    }

    @Test
    fun `sections keep the requirement order`() {
        val content =
            linuxWarningContent(
                listOf(
                    LinuxRequirement.INPUT_DEVICE_ACCESS,
                    LinuxRequirement.GNOME_APPINDICATOR_EXTENSION,
                ),
            )

        content.sections.map { it.title } shouldContainExactly
            listOf("Input device access required", "AppIndicator extension required")
    }

    @Test
    fun `hotkey failure adds a listener warning`() {
        displayedRequirements(
            detected = emptyList(),
            hotkeyUnavailable = true,
        ) shouldContainExactly listOf(LinuxRequirement.HOTKEY_LISTENER)
    }

    @Test
    fun `hotkey failure does not duplicate the input warning`() {
        displayedRequirements(
            detected = listOf(LinuxRequirement.INPUT_DEVICE_ACCESS),
            hotkeyUnavailable = true,
        ) shouldContainExactly listOf(LinuxRequirement.INPUT_DEVICE_ACCESS)
    }

    @Test
    fun `input device section explains groups and command`() {
        val section = linuxWarningContent(listOf(LinuxRequirement.INPUT_DEVICE_ACCESS)).sections.single()
        val lines = section.lines.map { it.text }

        lines shouldContain "sudo usermod -aG input,uinput \$USER"
        lines shouldContain "sudo modprobe uinput"
        section.lines.any { it.style == WarningLineStyle.Code } shouldBe true
    }

    @Test
    fun `GNOME extension section names the extension`() {
        val section = linuxWarningContent(listOf(LinuxRequirement.GNOME_SHELL_EXTENSION)).sections.single()
        val text = section.lines.joinToString(" ") { it.text }

        text shouldContain "gokfre@adrinand"
        text shouldContain "gnome-extensions enable"
    }

    @Test
    fun `AppIndicator section mentions the tray extension`() {
        val section = linuxWarningContent(listOf(LinuxRequirement.GNOME_APPINDICATOR_EXTENSION)).sections.single()
        val text = section.lines.joinToString(" ") { it.text }

        text shouldContain "AppIndicator"
        text shouldContain "tray"
    }

    @Test
    fun `D-Bus section mentions gdbus`() {
        val section = linuxWarningContent(listOf(LinuxRequirement.DBUS_CLIENT)).sections.single()
        val text = section.lines.joinToString(" ") { it.text }

        text shouldContain "gdbus"
    }

    @Test
    fun `unsupported compositor section mentions XWayland`() {
        val section = linuxWarningContent(listOf(LinuxRequirement.UNSUPPORTED_WAYLAND_COMPOSITOR)).sections.single()
        val text = section.lines.joinToString(" ") { it.text }

        text shouldContain "XWayland"
    }
}
