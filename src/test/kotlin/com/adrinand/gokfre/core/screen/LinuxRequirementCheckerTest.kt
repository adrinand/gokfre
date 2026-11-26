package com.adrinand.gokfre.core.screen

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.awt.GraphicsEnvironment

private const val LINUX = "Linux"
private const val MAC_OS = "Mac OS X"
private const val WAYLAND = "wayland"
private const val X11 = "x11"
private const val GNOME = "GNOME"
private const val KDE = "KDE"
private const val SWAY = "sway"
private const val CURRENT_SESSION = "currentSession"
private const val DETECT_PROBE = "detectProbe"
private val defaultProbe = LinuxRequirementsProbe(true, true, true, true)

private fun linuxSession(
    osName: String = LINUX,
    sessionType: String = WAYLAND,
    desktop: String = GNOME,
): LinuxSessionData = LinuxSessionData(osName = osName, sessionType = sessionType, desktop = desktop)

class LinuxRequirementCheckerTest {
    @Before
    fun setUp() {
        mockkStatic(GraphicsEnvironment::class)
        every { GraphicsEnvironment.isHeadless() } returns false

        mockkObject(LinuxRequirementChecker, recordPrivateCalls = true)
        every { LinuxRequirementChecker.missingRequirements() } answers { callOriginal() }
        every { LinuxRequirementChecker[CURRENT_SESSION]() } returns linuxSession()
        every { LinuxRequirementChecker[DETECT_PROBE](any<LinuxSessionData>()) } returns defaultProbe
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `non Linux sessions have no requirements`() {
        every { LinuxRequirementChecker[CURRENT_SESSION]() } returns linuxSession(osName = MAC_OS)

        LinuxRequirementChecker.missingRequirements().shouldBeEmpty()
    }

    @Test
    fun `X11 GNOME session with input access has no requirements`() {
        every { LinuxRequirementChecker[CURRENT_SESSION]() } returns linuxSession(sessionType = X11, desktop = GNOME)

        LinuxRequirementChecker.missingRequirements().shouldBeEmpty()
    }

    @Test
    fun `missing input access is reported`() {
        every { LinuxRequirementChecker[CURRENT_SESSION]() } returns linuxSession(sessionType = X11, desktop = GNOME)
        every { LinuxRequirementChecker[DETECT_PROBE](any<LinuxSessionData>()) } returns
            defaultProbe.copy(hasInputAccess = false)

        LinuxRequirementChecker.missingRequirements() shouldContainExactly listOf(LinuxRequirement.INPUT_DEVICE_ACCESS)
    }

    @Test
    fun `missing GNOME Shell extension on Wayland is reported`() {
        every { LinuxRequirementChecker[DETECT_PROBE](any<LinuxSessionData>()) } returns
            defaultProbe.copy(hasGnomeExtension = false)

        LinuxRequirementChecker.missingRequirements() shouldContainExactly
            listOf(LinuxRequirement.GNOME_SHELL_EXTENSION)
    }

    @Test
    fun `missing AppIndicator extension on GNOME is reported`() {
        every { LinuxRequirementChecker[DETECT_PROBE](any<LinuxSessionData>()) } returns
            defaultProbe.copy(hasAppIndicator = false)

        LinuxRequirementChecker.missingRequirements() shouldContainExactly
            listOf(LinuxRequirement.GNOME_APPINDICATOR_EXTENSION)
    }

    @Test
    fun `missing gdbus on GNOME reports only the D-Bus client`() {
        every { LinuxRequirementChecker[DETECT_PROBE](any<LinuxSessionData>()) } returns
            defaultProbe.copy(hasGdbus = false, hasGnomeExtension = false, hasAppIndicator = false)

        LinuxRequirementChecker.missingRequirements() shouldContainExactly listOf(LinuxRequirement.DBUS_CLIENT)
    }

    @Test
    fun `KDE Wayland needs nothing beyond input access`() {
        every { LinuxRequirementChecker[CURRENT_SESSION]() } returns linuxSession(desktop = KDE)
        every { LinuxRequirementChecker[DETECT_PROBE](any<LinuxSessionData>()) } returns
            defaultProbe.copy(hasGnomeExtension = false, hasAppIndicator = false)

        LinuxRequirementChecker.missingRequirements().shouldBeEmpty()
    }

    @Test
    fun `unsupported Wayland compositor is reported`() {
        every { LinuxRequirementChecker[CURRENT_SESSION]() } returns linuxSession(desktop = SWAY)
        every { LinuxRequirementChecker[DETECT_PROBE](any<LinuxSessionData>()) } returns
            defaultProbe.copy(hasGnomeExtension = false, hasAppIndicator = false)

        LinuxRequirementChecker.missingRequirements() shouldContainExactly
            listOf(LinuxRequirement.UNSUPPORTED_WAYLAND_COMPOSITOR)
    }
}
