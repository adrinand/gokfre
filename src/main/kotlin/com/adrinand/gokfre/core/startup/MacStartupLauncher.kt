package com.adrinand.gokfre.core.startup

import com.adrinand.gokfre.utils.PackagedAppPathFinder
import com.adrinand.gokfre.utils.writeAtomically
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Logger

private const val LAUNCH_AGENT_LABEL = "com.adrinand.gokfre"
private const val LAUNCH_AGENT_FILE_NAME = "$LAUNCH_AGENT_LABEL.plist"

private val logger = Logger.getLogger("com.adrinand.gokfre.core.startup.MacStartupLauncher")

class MacStartupLauncher(
    private val launchAgentsDir: Path = defaultLaunchAgentsDir(),
    private val bundleResolver: () -> Path? = { PackagedAppPathFinder.macAppBundlePath() },
) : StartupLauncher {
    private val launchAgentFile = launchAgentsDir.resolve(LAUNCH_AGENT_FILE_NAME)

    override fun isEnabled(): Boolean = Files.isRegularFile(launchAgentFile)

    override fun enable(): Result<Unit> =
        runCatching {
            val bundle =
                bundleResolver()
                    ?.requirePermanentMacLocation()
                    ?.requireNoControlCharacters("Gokfre.app bundle")
                    ?: error("Launch at startup requires the packaged Gokfre.app.")
            writeAtomically(launchAgentFile, renderLaunchAgent(bundle))
            logger.info { "Enabled launch at startup via $launchAgentFile" }
        }.onFailure {
            logger.warning("Failed to enable launch at startup: ${it.message}")
        }

    override fun disable(): Result<Unit> =
        runCatching {
            Files.deleteIfExists(launchAgentFile)
            logger.info { "Disabled launch at startup" }
        }.onFailure {
            logger.warning("Failed to disable launch at startup: ${it.message}")
        }

    private fun renderLaunchAgent(bundle: Path): String =
        listOf(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
            "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" " +
                "\"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">",
            "<plist version=\"1.0\">",
            "<dict>",
            "    <key>Label</key>",
            "    <string>$LAUNCH_AGENT_LABEL</string>",
            "    <key>ProgramArguments</key>",
            "    <array>",
            "        <string>/usr/bin/open</string>",
            "        <string>${escapeXml(bundle.toAbsolutePath().toString())}</string>",
            "    </array>",
            "    <key>RunAtLoad</key>",
            "    <true/>",
            "</dict>",
            "</plist>",
            "",
        ).joinToString("\n")

    private fun escapeXml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private companion object {
        fun defaultLaunchAgentsDir(): Path = Path.of(System.getProperty("user.home"), "Library", "LaunchAgents")
    }
}
