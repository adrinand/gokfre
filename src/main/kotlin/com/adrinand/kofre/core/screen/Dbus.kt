package com.adrinand.kofre.core.screen

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

private const val PROCESS_TIMEOUT_MILLISECONDS = 2_000L
private const val GDBUS_NAME = "gdbus"
private const val QDBUS_NAME = "qdbus"
private const val GDBUS_PATH = "/usr/bin/gdbus"
private const val GDBUS_PATH_ALT = "/bin/gdbus"
private const val QDBUS_PATH = "/usr/bin/qdbus"
private const val QDBUS_PATH_ALT = "/bin/qdbus"
private const val WHICH_COMMAND = "which"

private val logger = Logger.getLogger("com.adrinand.kofre.core.screen.Dbus")

/**
 * Stateless helpers for invoking D-Bus client binaries.
 */
object Dbus {
    /**
     * Runs a D-Bus client command and returns its stdout, or `null` on failure.
     */
    internal fun runClient(
        client: String,
        args: List<String>,
        timeoutMs: Long = PROCESS_TIMEOUT_MILLISECONDS,
    ): String? = runCommand(listOf(client) + args, timeoutMs)

    /**
     * Returns the absolute path to `gdbus`, or `null` if it is not available.
     */
    fun findGdbus(): String? {
        val candidates = listOf(GDBUS_PATH, GDBUS_PATH_ALT)
        val existing = candidates.firstOrNull { File(it).exists() }
        if (existing != null) {
            return existing
        }
        return runCommand(listOf(WHICH_COMMAND, GDBUS_NAME))?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Returns the absolute path to `qdbus`, or `null` if it is not available.
     * Falls back to `gdbus` if `qdbus` is missing.
     */
    fun findQdbusOrGdbus(): String? {
        val qdbusCandidates = listOf(QDBUS_PATH, QDBUS_PATH_ALT)
        val qdbusExisting = qdbusCandidates.firstOrNull { File(it).exists() }
        if (qdbusExisting != null) {
            return qdbusExisting
        }
        val gdbusCandidates = listOf(GDBUS_PATH, GDBUS_PATH_ALT)
        val gdbusExisting = gdbusCandidates.firstOrNull { File(it).exists() }
        if (gdbusExisting != null) {
            return gdbusExisting
        }
        return runCommand(listOf(WHICH_COMMAND, QDBUS_NAME))?.trim()?.takeIf { it.isNotEmpty() }
            ?: runCommand(listOf(WHICH_COMMAND, GDBUS_NAME))?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Runs [command] and returns its stdout, or `null` on failure.
     *
     * The output is returned as-is; callers can trim it if needed.
     */
    private fun runCommand(
        command: List<String>,
        timeoutMs: Long = PROCESS_TIMEOUT_MILLISECONDS,
    ): String? {
        return try {
            val process =
                ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start()
            val output = process.inputStream.bufferedReader().readText()
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished || process.exitValue() != 0) {
                logger.fine { "${command.joinToString(" ")} failed: $output" }
                null
            } else {
                output
            }
        } catch (e: IOException) {
            logger.fine { "${command.joinToString(" ")} threw: ${e.message}" }
            null
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.fine { "${command.joinToString(" ")} interrupted" }
            null
        }
    }
}
