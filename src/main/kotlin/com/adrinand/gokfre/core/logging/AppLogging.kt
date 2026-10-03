package com.adrinand.gokfre.core.logging

import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.logging.FileHandler
import java.util.logging.Formatter
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import kotlin.io.path.createDirectories

object AppLogging {
    private const val OS_NAME_PROPERTY = "os.name"
    private const val OS_ARCH_PROPERTY = "os.arch"
    private const val JAVA_VERSION_PROPERTY = "java.version"
    private const val USER_HOME_PROPERTY = "user.home"
    private const val XDG_STATE_HOME_ENV = "XDG_STATE_HOME"
    private const val OS_MAC = "mac"
    private const val OS_DARWIN = "darwin"
    private const val LOG_DIR_NAME = "gokfre"
    private const val LOG_FILE_NAME_PREFIX = "gokfre"
    private const val LOG_FILE_SUFFIX_PATTERN = "-%g.log"
    private const val MAX_LOG_BYTES = 1_000_000
    private const val MAX_LOG_FILES = 3
    private const val DEFAULT_VERSION = "dev"

    private val logger = Logger.getLogger("com.adrinand.gokfre.core.logging.AppLogging")
    private var fileHandler: Handler? = null
    private var logDir: Path? = null
    private var isInitialized = false
    private var previousExceptionHandler: Thread.UncaughtExceptionHandler? = null

    fun initialize() = initialize(defaultLogDir())

    internal fun initialize(baseDir: Path) {
        if (isInitialized) {
            return
        }
        isInitialized = true
        runCatching {
            baseDir.createDirectories()
            val pattern = baseDir.resolve("$LOG_FILE_NAME_PREFIX$LOG_FILE_SUFFIX_PATTERN").toString()
            val handler =
                FileHandler(pattern, MAX_LOG_BYTES, MAX_LOG_FILES, true).apply {
                    formatter = SingleLineFormatter()
                    level = Level.FINE
                }
            val rootLogger = Logger.getLogger("")
            rootLogger.level = Level.FINE
            rootLogger.addHandler(handler)
            fileHandler = handler
            logDir = baseDir
        }.onFailure {
            logger.warning("Failed to initialize file logging: ${it.message}")
        }
        installUncaughtExceptionHandler()
        logStartupBanner()
    }

    internal fun reset() {
        fileHandler?.let { handler ->
            Logger.getLogger("").removeHandler(handler)
            runCatching { handler.close() }
        }
        fileHandler = null
        logDir = null
        Logger.getLogger("").level = Level.INFO
        Thread.setDefaultUncaughtExceptionHandler(previousExceptionHandler)
        previousExceptionHandler = null
        isInitialized = false
    }

    internal fun defaultLogDir(
        osName: String = System.getProperty(OS_NAME_PROPERTY).lowercase(),
        xdgStateHome: String? = System.getenv(XDG_STATE_HOME_ENV),
        userHome: String = System.getProperty(USER_HOME_PROPERTY),
    ): Path {
        val baseDir =
            when {
                osName.contains(OS_MAC) || osName.contains(OS_DARWIN) ->
                    Path.of(userHome, "Library", "Logs")

                xdgStateHome.isNullOrBlank() -> Path.of(userHome, ".local", "state")
                else -> Path.of(xdgStateHome)
            }
        return baseDir.resolve(LOG_DIR_NAME)
    }

    private fun installUncaughtExceptionHandler() {
        previousExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            logger.log(Level.SEVERE, "Uncaught exception in thread '${thread.name}'", throwable)
        }
    }

    private fun logStartupBanner() {
        logger.info(
            "Starting gokfre version=${appVersion()} " +
                "os=${System.getProperty(OS_NAME_PROPERTY)} " +
                "arch=${System.getProperty(OS_ARCH_PROPERTY)} " +
                "java=${System.getProperty(JAVA_VERSION_PROPERTY)} " +
                "logDir=$logDir",
        )
    }

    private fun appVersion(): String = AppLogging::class.java.`package`?.implementationVersion ?: DEFAULT_VERSION
}

internal class SingleLineFormatter : Formatter() {
    override fun format(record: LogRecord): String {
        val builder =
            StringBuilder()
                .append(TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(record.millis)))
                .append(' ')
                .append(record.level.name)
                .append(" [")
                .append(Thread.currentThread().name)
                .append("] ")
                .append(record.loggerName)
                .append(" - ")
                .append(formatMessage(record))
                .append(System.lineSeparator())
        record.thrown?.let { throwable -> builder.append(throwable.stackTraceToString()) }
        return builder.toString()
    }
}

private val TIMESTAMP_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
