package com.adrinand.kofre.core.persistence.repo

import com.adrinand.kofre.core.persistence.entity.PersistedSettings
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.logging.Logger
import kotlin.io.path.createParentDirectories
import kotlin.io.path.notExists
import kotlin.io.path.readText
import kotlin.io.path.writeText

private const val OS_NAME_PROPERTY = "os.name"
private const val USER_HOME_PROPERTY = "user.home"
private const val JAVA_IO_TMPDIR_PROPERTY = "java.io.tmpdir"
private const val XDG_CONFIG_HOME_ENV = "XDG_CONFIG_HOME"
private const val APP_CONFIG_DIR = "kofre"
private const val SETTINGS_FILE_NAME = "settings.json"
private val logger = Logger.getLogger("com.adrinand.kofre.core.persistence.repo.SettingsRepository")
private val json =
    Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

class SettingsRepository internal constructor(
    private val settingsPath: Path,
) {
    constructor() : this(resolveDefaultPath())

    fun load(): PersistedSettings? {
        if (settingsPath.notExists()) {
            return null
        }

        return runCatching { json.decodeFromString<PersistedSettings>(settingsPath.readText()) }
            .onFailure {
                logger.warning("Failed to load settings from $settingsPath: ${it.message}")
            }
            .getOrNull()
    }

    fun save(settings: PersistedSettings) {
        runCatching {
            settingsPath.createParentDirectories()
            val serialized = json.encodeToString(settings)
            val parent = settingsPath.parent ?: Path.of(System.getProperty(JAVA_IO_TMPDIR_PROPERTY))
            val tempPath = Files.createTempFile(parent, "kofre-settings", ".tmp")
            tempPath.writeText(serialized)

            try {
                Files.move(
                    tempPath,
                    settingsPath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tempPath, settingsPath, StandardCopyOption.REPLACE_EXISTING)
            }
        }
            .onFailure {
                logger.warning("Failed to save settings to $settingsPath: ${it.message}")
            }
    }

    companion object {
        private fun resolveDefaultPath(): Path {
            val osName = System.getProperty(OS_NAME_PROPERTY).lowercase()
            val baseDir =
                when {
                    osName.contains("mac") || osName.contains("darwin") -> {
                        Path.of(
                            System.getProperty(USER_HOME_PROPERTY),
                            "Library",
                            "Application Support",
                        )
                    }

                    osName.contains("linux") -> {
                        val xdgConfigHome = System.getenv(XDG_CONFIG_HOME_ENV)
                        if (xdgConfigHome.isNullOrBlank()) {
                            Path.of(System.getProperty(USER_HOME_PROPERTY), ".config")
                        } else {
                            Path.of(xdgConfigHome)
                        }
                    }

                    else -> {
                        Path.of(System.getProperty(USER_HOME_PROPERTY))
                    }
                }

            return baseDir.resolve(APP_CONFIG_DIR).resolve(SETTINGS_FILE_NAME)
        }
    }
}
