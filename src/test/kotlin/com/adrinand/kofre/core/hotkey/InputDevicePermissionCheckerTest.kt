package com.adrinand.kofre.core.hotkey

import io.kotest.matchers.shouldBe
import org.junit.Assume
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class InputDevicePermissionCheckerTest {
    @Test
    fun `non Linux OS denies access`() {
        withTempDir { uinput, input ->
            InputDevicePermissionChecker.hasAccess(
                osName = "Windows 11",
                userGroupNames = { GROUPS_WITH_INPUT },
                uinputPath = uinput,
                inputDir = input,
            ) shouldBe false
        }
    }

    @Test
    fun `Linux with input group membership and writable uinput allows access`() {
        withTempDir { uinput, input ->
            InputDevicePermissionChecker.hasAccess(
                osName = LINUX_OS,
                userGroupNames = { GROUPS_WITH_INPUT },
                uinputPath = uinput,
                inputDir = input,
            ) shouldBe true
        }
    }

    @Test
    fun `Linux with readable event device and writable uinput allows access`() {
        withTempDir { uinput, input ->
            Files.createFile(input.resolve("event0"))
            InputDevicePermissionChecker.hasAccess(
                osName = LINUX_OS,
                userGroupNames = { "user" },
                uinputPath = uinput,
                inputDir = input,
            ) shouldBe true
        }
    }

    @Test
    fun `Linux without input group nor readable event device denies access`() {
        withTempDir { uinput, input ->
            InputDevicePermissionChecker.hasAccess(
                osName = LINUX_OS,
                userGroupNames = { "user" },
                uinputPath = uinput,
                inputDir = input,
            ) shouldBe false
        }
    }

    @Test
    fun `Linux with missing uinput denies access`() {
        withTempDir { _, input ->
            InputDevicePermissionChecker.hasAccess(
                osName = LINUX_OS,
                userGroupNames = { GROUPS_WITH_INPUT },
                uinputPath = input.resolve("missing-uinput"),
                inputDir = input,
            ) shouldBe false
        }
    }

    @Test
    fun `Linux with unwritable uinput denies access`() {
        withTempDir { uinput, input ->
            uinput.toFile().setWritable(false)
            Assume.assumeFalse(Files.isWritable(uinput))
            InputDevicePermissionChecker.hasAccess(
                osName = LINUX_OS,
                userGroupNames = { GROUPS_WITH_INPUT },
                uinputPath = uinput,
                inputDir = input,
            ) shouldBe false
        }
    }

    private fun withTempDir(block: (uinput: Path, input: Path) -> Unit) {
        val dir = Files.createTempDirectory("kofre-input-permission-checker")
        try {
            val uinput = dir.resolve("uinput").also { Files.createFile(it) }
            val input = dir.resolve("input").also { Files.createDirectory(it) }
            block(uinput, input)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    companion object {
        private const val LINUX_OS = "Linux"
        private const val GROUPS_WITH_INPUT = "user input uinput"
    }
}
