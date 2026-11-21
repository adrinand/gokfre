package com.adrinand.gokfre.core.startup

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class MacStartupLauncherTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val launchAgentsDir: Path
        get() = tempFolder.root.toPath().resolve("LaunchAgents")

    private val launchAgentFile: Path
        get() = launchAgentsDir.resolve("com.adrinand.gokfre.plist")

    private fun createLauncher(bundle: Path?): MacStartupLauncher =
        MacStartupLauncher(
            launchAgentsDir = launchAgentsDir,
            bundleResolver = { bundle },
        )

    @Test
    fun `enable writes a launch agent`() {
        val launcher = createLauncher(Path.of("/Applications/Gokfre.app"))

        launcher.enable().isSuccess shouldBe true

        Files.isRegularFile(launchAgentFile) shouldBe true
        val content = Files.readString(launchAgentFile)
        content shouldContain "<string>com.adrinand.gokfre</string>"
        content shouldContain "<string>/Applications/Gokfre.app</string>"
        content shouldContain "<key>RunAtLoad</key>"
        launcher.isEnabled() shouldBe true
    }

    @Test
    fun `disable removes the launch agent`() {
        val launcher = createLauncher(Path.of("/Applications/Gokfre.app"))
        launcher.enable().isSuccess shouldBe true

        launcher.disable().isSuccess shouldBe true

        Files.exists(launchAgentFile) shouldBe false
        launcher.isEnabled() shouldBe false
    }

    @Test
    fun `enable fails when the app bundle cannot be resolved`() {
        val launcher = createLauncher(null)

        launcher.enable().isFailure shouldBe true

        Files.exists(launchAgentFile) shouldBe false
        launcher.isEnabled() shouldBe false
    }

    @Test
    fun `enable rejects bundle paths with control characters`() {
        val launcher = createLauncher(Path.of("/Applications/Gokfre\n.app"))

        launcher.enable().isFailure shouldBe true

        Files.exists(launchAgentFile) shouldBe false
    }

    @Test
    fun `enable rejects bundles on transient volumes`() {
        val launcher = createLauncher(Path.of("/Volumes/Gokfre/Gokfre.app"))

        launcher.enable().isFailure shouldBe true

        Files.exists(launchAgentFile) shouldBe false
    }

    @Test
    fun `enable rejects translocated bundles`() {
        val launcher =
            createLauncher(Path.of("/private/var/folders/ab/AppTranslocation/123/d/Gokfre.app"))

        launcher.enable().isFailure shouldBe true

        Files.exists(launchAgentFile) shouldBe false
    }
}
