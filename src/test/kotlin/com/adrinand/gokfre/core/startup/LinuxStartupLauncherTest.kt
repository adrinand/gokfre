package com.adrinand.gokfre.core.startup

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class LinuxStartupLauncherTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val autostartDir: Path
        get() = tempFolder.root.toPath().resolve("autostart")

    private val desktopFile: Path
        get() = autostartDir.resolve("com.adrinand.gokfre.desktop")

    private fun createLauncher(executable: Path?): LinuxStartupLauncher =
        LinuxStartupLauncher(
            autostartDir = autostartDir,
            executableResolver = { executable },
        )

    @Test
    fun `enable writes an xdg autostart entry`() {
        val launcher = createLauncher(Path.of("/opt/gokfre/bin/gokfre"))

        launcher.enable().isSuccess shouldBe true

        Files.isRegularFile(desktopFile) shouldBe true
        val content = Files.readString(desktopFile)
        content shouldContain "Type=Application"
        content shouldContain "Name=Gokfre"
        content shouldContain "Exec=\"/opt/gokfre/bin/gokfre\""
        content shouldContain "StartupNotify=false"
        content shouldNotContain "Hidden=true"
        launcher.isEnabled() shouldBe true
    }

    @Test
    fun `disable removes the autostart entry`() {
        val launcher = createLauncher(Path.of("/opt/gokfre/bin/gokfre"))
        launcher.enable().isSuccess shouldBe true

        launcher.disable().isSuccess shouldBe true

        Files.exists(desktopFile) shouldBe false
        launcher.isEnabled() shouldBe false
    }

    @Test
    fun `enable fails when the packaged executable cannot be resolved`() {
        val launcher = createLauncher(null)

        launcher.enable().isFailure shouldBe true

        Files.exists(desktopFile) shouldBe false
        launcher.isEnabled() shouldBe false
    }

    @Test
    fun `isEnabled is false when the entry is hidden`() {
        Files.createDirectories(autostartDir)
        Files.writeString(desktopFile, "[Desktop Entry]\nHidden=true\n")

        createLauncher(null).isEnabled() shouldBe false
    }

    @Test
    fun `isEnabled is false when gnome autostart is disabled`() {
        Files.createDirectories(autostartDir)
        Files.writeString(desktopFile, "[Desktop Entry]\nX-GNOME-Autostart-enabled=false\n")

        createLauncher(null).isEnabled() shouldBe false
    }

    @Test
    fun `exec paths with spaces are quoted`() {
        val launcher = createLauncher(Path.of("/opt/gokfre dir/bin/gokfre"))

        launcher.enable().isSuccess shouldBe true

        Files.readString(desktopFile) shouldContain "Exec=\"/opt/gokfre dir/bin/gokfre\""
    }

    @Test
    fun `exec paths escape percent characters`() {
        val launcher = createLauncher(Path.of("/opt/100%/bin/gokfre"))

        launcher.enable().isSuccess shouldBe true

        Files.readString(desktopFile) shouldContain "Exec=\"/opt/100%%/bin/gokfre\""
    }

    @Test
    fun `enable rejects executable paths with control characters`() {
        val launcher = createLauncher(Path.of("/opt/gokfre\nExec=/bin/sh/bin/gokfre"))

        launcher.enable().isFailure shouldBe true

        Files.exists(desktopFile) shouldBe false
    }
}
