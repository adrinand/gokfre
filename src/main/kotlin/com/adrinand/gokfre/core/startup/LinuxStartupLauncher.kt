package com.adrinand.gokfre.core.startup

import com.adrinand.gokfre.utils.PackagedAppLocator
import com.adrinand.gokfre.utils.writeAtomically
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Logger

private const val DESKTOP_FILE_NAME = "com.adrinand.gokfre.desktop"

private val logger = Logger.getLogger("com.adrinand.gokfre.core.startup.LinuxStartupLauncher")

class LinuxStartupLauncher(
    private val autostartDir: Path = defaultAutostartDir(),
    private val executableResolver: () -> Path? = { PackagedAppLocator.linuxExecutable() },
) : StartupLauncher {
    private val desktopFile = autostartDir.resolve(DESKTOP_FILE_NAME)

    override fun isEnabled(): Boolean = Files.isRegularFile(desktopFile) && !isDisabled(desktopFile)

    override fun enable(): Result<Unit> =
        runCatching {
            val executable =
                executableResolver()
                    ?.requireNoControlCharacters("Gokfre executable")
                    ?: error("Launch at startup requires the packaged Gokfre application.")
            writeAtomically(desktopFile, renderDesktopEntry(executable))
            logger.info { "Enabled launch at startup via $desktopFile" }
        }.onFailure {
            logger.warning("Failed to enable launch at startup: ${it.message}")
        }

    override fun disable(): Result<Unit> =
        runCatching {
            Files.deleteIfExists(desktopFile)
            logger.info { "Disabled launch at startup" }
        }.onFailure {
            logger.warning("Failed to disable launch at startup: ${it.message}")
        }

    private fun isDisabled(file: Path): Boolean =
        runCatching {
            Files.readAllLines(file, StandardCharsets.UTF_8).any { line ->
                val key = line.substringBefore('=').trim()
                val value = line.substringAfter('=', "").trim()
                val hidden = key.equals("Hidden", ignoreCase = true) && value.equals("true", ignoreCase = true)
                val gnomeDisabled =
                    key.equals("X-GNOME-Autostart-enabled", ignoreCase = true) &&
                        value.equals("false", ignoreCase = true)
                hidden || gnomeDisabled
            }
        }.getOrDefault(false)

    private fun renderDesktopEntry(executable: Path): String =
        listOf(
            "[Desktop Entry]",
            "Type=Application",
            "Name=Gokfre",
            "Comment=Keyboard-driven tiling window manager",
            "Exec=${quoteExecArgument(executable.toAbsolutePath().toString())}",
            "Terminal=false",
            "StartupNotify=false",
            "",
        ).joinToString("\n")

    private fun quoteExecArgument(value: String): String {
        val escaped =
            value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("`", "\\`")
                .replace("$", "\\$")
                .replace("%", "%%")
        return "\"$escaped\""
    }

    private companion object {
        fun defaultAutostartDir(): Path {
            val configHome =
                System.getenv("XDG_CONFIG_HOME")
                    ?.takeIf { it.isNotBlank() }
                    ?.let(Path::of)
                    ?.takeIf { it.isAbsolute }
                    ?: Path.of(System.getProperty("user.home"), ".config")
            return configHome.resolve("autostart")
        }
    }
}
