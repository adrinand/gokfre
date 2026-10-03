package com.adrinand.gokfre.core.logging

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.FileHandler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

@Suppress("TooManyFunctions")
class AppLoggingTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @After
    fun tearDown() {
        AppLogging.reset()
    }

    @Test
    fun `initialize writes info and fine records to a log file`() {
        val logDir = tempFolder.newFolder("logs").toPath()

        AppLogging.initialize(logDir)
        AppLogging.initialize(logDir)

        Logger.getLogger(TEST_LOGGER_NAME).fine("fine detail record")
        Logger.getLogger(TEST_LOGGER_NAME).info("info record")
        flushRootHandlers()

        val contents = readLogFiles(logDir)
        contents shouldContain "fine detail record"
        contents shouldContain "FINE"
        contents shouldContain "info record"
        contents shouldContain "Starting gokfre"
        Logger.getLogger("").handlers.count { it is FileHandler } shouldBe 1
    }

    @Test
    fun `root logger level is raised so fine records reach file handlers`() {
        AppLogging.initialize(tempFolder.newFolder("logs").toPath())

        Logger.getLogger("").level shouldBe Level.FINE
    }

    @Test
    fun `uncaught exceptions are written to the log file`() {
        val logDir = tempFolder.newFolder("logs").toPath()
        AppLogging.initialize(logDir)

        val handler = Thread.getDefaultUncaughtExceptionHandler()
        handler.shouldNotBeNull().uncaughtException(Thread.currentThread(), IllegalStateException("boom"))

        flushRootHandlers()
        val contents = readLogFiles(logDir)
        contents shouldContain "Uncaught exception in thread"
        contents shouldContain "boom"
    }

    @Test
    fun `initialize fails gracefully when the log directory cannot be created`() {
        val notADirectory = tempFolder.newFile("blocked").toPath()

        AppLogging.initialize(notADirectory)

        Logger.getLogger("").handlers.count { it is FileHandler } shouldBe 0
    }

    @Test
    fun `reset without initialize is a no-op`() {
        AppLogging.reset()

        Thread.getDefaultUncaughtExceptionHandler() shouldBe null
    }

    @Test
    fun `default log dir uses Library Logs on macOS`() {
        val dir = AppLogging.defaultLogDir(osName = MAC, xdgStateHome = null, userHome = MAC_HOME)

        dir.toString() shouldBe MAC_LOG_DIR
    }

    @Test
    fun `default log dir uses darwin name on macOS`() {
        val dir = AppLogging.defaultLogDir(osName = DARWIN, xdgStateHome = null, userHome = MAC_HOME)

        dir.toString() shouldBe MAC_LOG_DIR
    }

    @Test
    fun `default log dir prefers XDG_STATE_HOME on Linux`() {
        val dir = AppLogging.defaultLogDir(osName = LINUX, xdgStateHome = XDG_STATE, userHome = LINUX_HOME)

        dir.toString() shouldBe XDG_LOG_DIR
    }

    @Test
    fun `default log dir falls back to local state on Linux`() {
        val dir = AppLogging.defaultLogDir(osName = LINUX, xdgStateHome = null, userHome = LINUX_HOME)

        dir.toString() shouldBe LINUX_STATE_LOG_DIR
    }

    @Test
    fun `default log dir treats blank XDG_STATE_HOME as unset`() {
        val dir = AppLogging.defaultLogDir(osName = LINUX, xdgStateHome = "", userHome = LINUX_HOME)

        dir.toString() shouldBe LINUX_STATE_LOG_DIR
    }

    @Test
    fun `formatter renders a single line with level and logger name`() {
        val record = LogRecord(Level.WARNING, "formatted message")
        record.loggerName = TEST_WIDGET_LOGGER

        val output = SingleLineFormatter().format(record)

        output shouldContain "WARNING"
        output shouldContain TEST_WIDGET_LOGGER
        output shouldContain "formatted message"
        output.trim().lineSequence().count() shouldBe 1
    }

    @Test
    fun `formatter appends stack traces for thrown records`() {
        val record = LogRecord(Level.SEVERE, "with failure")
        record.loggerName = TEST_WIDGET_LOGGER
        record.thrown = IllegalStateException("kaboom")

        val output = SingleLineFormatter().format(record)

        output shouldContain "with failure"
        output shouldContain "java.lang.IllegalStateException"
        output shouldContain "kaboom"
    }

    private fun flushRootHandlers() {
        Logger.getLogger("").handlers.forEach { handler -> handler.flush() }
    }

    private fun readLogFiles(dir: Path): String =
        Files.list(dir).use { stream ->
            stream
                .filter { path -> path.fileName.toString().endsWith(LOG_EXTENSION) }
                .map { path -> Files.readString(path) }
                .reduce { first, second -> first + second }
                .orElse("")
        }

    companion object {
        private const val TEST_LOGGER_NAME = "com.adrinand.gokfre.test.AppLoggingTest"
        private const val TEST_WIDGET_LOGGER = "com.example.Widget"
        private const val LOG_EXTENSION = ".log"
        private const val LINUX = "linux"
        private const val MAC = "macos"
        private const val DARWIN = "darwin"
        private const val LINUX_HOME = "/home/test"
        private const val MAC_HOME = "/Users/test"
        private const val XDG_STATE = "/var/state"
        private const val LOG_LEAF = "/gokfre"
        private const val MAC_LOG_DIR = "$MAC_HOME/Library/Logs$LOG_LEAF"
        private const val XDG_LOG_DIR = XDG_STATE + LOG_LEAF
        private const val LINUX_STATE_LOG_DIR = "$LINUX_HOME/.local/state$LOG_LEAF"
    }
}
