package com.adrinand.gokfre.core.hotkey

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

/**
 * Checks whether the current process is likely able to read Linux input devices.
 *
 * This is a heuristic: it checks group membership and the readability of
 * `/dev/input/event*`. The actual check happens when the native library tries to open evdev.
 */
object InputDevicePermissionChecker {
    fun hasInputDeviceAccess(): Boolean =
        hasAccess(
            osName = System.getProperty("os.name"),
            userGroupNames = ::runIdGn,
            uinputPath = Paths.get("/dev/uinput"),
            inputDir = Paths.get("/dev/input"),
        )

    internal fun hasAccess(
        osName: String?,
        userGroupNames: () -> String?,
        uinputPath: Path,
        inputDir: Path,
    ): Boolean {
        if (osName?.lowercase()?.contains("linux") != true) {
            return false
        }
        val canReadInputDevice = isInInputGroup(userGroupNames) || canReadAnyInputDevice(inputDir)
        val canWriteUinput = Files.exists(uinputPath) && Files.isWritable(uinputPath)
        return canReadInputDevice && canWriteUinput
    }

    private fun isInInputGroup(runIdGn: () -> String?): Boolean {
        val groupNames = runIdGn() ?: return false
        return groupNames.splitToSequence(" ").any { it.trim() == "input" }
    }

    private fun runIdGn(): String? =
        try {
            ProcessBuilder("id", "-Gn")
                .redirectErrorStream(true)
                .start()
                .run {
                    waitFor(2, TimeUnit.SECONDS)
                    if (exitValue() == 0) inputStream.bufferedReader().readText().trim() else null
                }
        } catch (_: Exception) {
            null
        }

    private fun canReadAnyInputDevice(inputDir: Path): Boolean =
        Files.newDirectoryStream(inputDir, "event*").use { stream ->
            stream.any { Files.isReadable(it) }
        }
}
