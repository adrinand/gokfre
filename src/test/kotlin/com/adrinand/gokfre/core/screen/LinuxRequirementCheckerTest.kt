package com.adrinand.gokfre.core.screen

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Test

class LinuxRequirementCheckerTest {
    @Test
    fun `non Linux sessions have no requirements`() {
        LinuxRequirementChecker.missingRequirements(
            session = session(osName = MAC_OS),
            probe = defaultProbe,
        ).shouldBeEmpty()
    }

    @Test
    fun `X11 GNOME session with input access has no requirements`() {
        LinuxRequirementChecker.missingRequirements(
            session = session(sessionType = X11, desktop = GNOME),
            probe = defaultProbe,
        ).shouldBeEmpty()
    }

    @Test
    fun `missing input access is reported`() {
        LinuxRequirementChecker.missingRequirements(
            session = session(sessionType = X11, desktop = GNOME),
            probe = defaultProbe.copy(hasInputAccess = false),
        ) shouldContainExactly listOf(LinuxRequirement.INPUT_DEVICE_ACCESS)
    }

    @Test
    fun `missing GNOME Shell extension on Wayland is reported`() {
        LinuxRequirementChecker.missingRequirements(
            session = session(desktop = GNOME),
            probe = defaultProbe.copy(hasGnomeExtension = false),
        ) shouldContainExactly listOf(LinuxRequirement.GNOME_SHELL_EXTENSION)
    }

    @Test
    fun `missing AppIndicator extension on GNOME is reported`() {
        LinuxRequirementChecker.missingRequirements(
            session = session(desktop = GNOME),
            probe = defaultProbe.copy(hasAppIndicator = false),
        ) shouldContainExactly listOf(LinuxRequirement.GNOME_APPINDICATOR_EXTENSION)
    }

    @Test
    fun `missing gdbus on GNOME reports only the D-Bus client`() {
        LinuxRequirementChecker.missingRequirements(
            session = session(desktop = GNOME),
            probe =
                defaultProbe.copy(
                    hasGdbus = false,
                    hasGnomeExtension = false,
                    hasAppIndicator = false,
                ),
        ) shouldContainExactly listOf(LinuxRequirement.DBUS_CLIENT)
    }

    @Test
    fun `KDE Wayland needs nothing beyond input access`() {
        LinuxRequirementChecker.missingRequirements(
            session = session(desktop = KDE),
            probe = defaultProbe.copy(hasGnomeExtension = false, hasAppIndicator = false),
        ).shouldBeEmpty()
    }

    @Test
    fun `unsupported Wayland compositor is reported`() {
        LinuxRequirementChecker.missingRequirements(
            session = session(desktop = SWAY),
            probe = defaultProbe.copy(hasGnomeExtension = false, hasAppIndicator = false),
        ) shouldContainExactly listOf(LinuxRequirement.UNSUPPORTED_WAYLAND_COMPOSITOR)
    }

    @Test
    fun `extension state is parsed from gdbus output`() {
        parseExtensionState("({'state': <uint32 1>, 'enabled': <true>},)") shouldBe 1
        parseExtensionState("({'state': <1.0>, 'enabled': <true>},)") shouldBe 1
        parseExtensionState("({'state': <uint32 4>},)") shouldBe 4
        parseExtensionState("({'_generated': <'x'>, 'version': <66.0>, 'state': <2.0>},)") shouldBe 2
        parseExtensionState("Error: not found") shouldBe null
    }

    private fun session(
        osName: String = LINUX,
        sessionType: String = WAYLAND,
        desktop: String = GNOME,
    ): LinuxSession = LinuxSession(osName = osName, sessionType = sessionType, desktop = desktop)

    private companion object {
        const val LINUX = "Linux"
        const val MAC_OS = "Mac OS X"
        const val WAYLAND = "wayland"
        const val X11 = "x11"
        const val GNOME = "GNOME"
        const val KDE = "KDE"
        const val SWAY = "sway"
        val defaultProbe = LinuxProbe(true, true, true, true)
    }
}
