package com.adrinand.gokfre.core.screen

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

private const val PROCESS_TIMEOUT_MILLISECONDS = 2_000L
private const val OUTPUT_READER_JOIN_MILLISECONDS = 500L
private const val OUTPUT_BUFFER_CHARS = 1024
private const val MAX_OUTPUT_CHARS = 64 * 1024
private const val GDBUS_NAME = "gdbus"
private const val QDBUS_NAME = "qdbus"
private const val GDBUS_PATH = "/usr/bin/gdbus"
private const val GDBUS_PATH_ALT = "/bin/gdbus"
private const val QDBUS_PATH = "/usr/bin/qdbus"
private const val QDBUS_PATH_ALT = "/bin/qdbus"
private const val WHICH_COMMAND = "which"

private val logger = Logger.getLogger("com.adrinand.gokfre.core.screen.Dbus")

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
     * The output is read on a separate thread so [timeoutMs] also bounds a
     * child that never closes its stdout.
     */
    private fun runCommand(
        command: List<String>,
        timeoutMs: Long = PROCESS_TIMEOUT_MILLISECONDS,
    ): String? {
        val process = startProcess(command) ?: return null
        val output = StringBuilder()
        val reader = startOutputReader(process, command, output)
        val finished = waitForProcess(process, command, timeoutMs) ?: return null
        if (!finished) {
            process.destroyForcibly()
        }
        val readerFinished = joinReader(reader)
        val processSucceeded = finished && readerFinished
        val success = processSucceeded && runCatching { process.exitValue() }.getOrDefault(-1) == 0
        return if (success) {
            output.toString()
        } else {
            if (readerFinished) {
                logger.fine { "${command.joinToString(" ")} failed: $output" }
            } else {
                logger.fine { "${command.joinToString(" ")} failed: output reader still running" }
            }
            null
        }
    }

    private fun startProcess(command: List<String>): Process? =
        try {
            ProcessBuilder(command).redirectErrorStream(true).start()
        } catch (e: IOException) {
            logger.fine { "${command.joinToString(" ")} threw: ${e.message}" }
            null
        }

    private fun startOutputReader(
        process: Process,
        command: List<String>,
        output: StringBuilder,
    ): Thread =
        Thread {
            runCatching {
                process.inputStream.bufferedReader().use { stream ->
                    val buffer = CharArray(OUTPUT_BUFFER_CHARS)
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        val remaining = MAX_OUTPUT_CHARS - output.length
                        if (remaining > 0) {
                            output.append(buffer, 0, minOf(read, remaining))
                        }
                    }
                }
            }.onFailure { logger.fine { "Reading ${command.joinToString(" ")} output failed: ${it.message}" } }
        }.apply {
            isDaemon = true
            start()
        }

    private fun waitForProcess(
        process: Process,
        command: List<String>,
        timeoutMs: Long,
    ): Boolean? =
        try {
            process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            process.destroyForcibly()
            logger.fine { "${command.joinToString(" ")} interrupted" }
            null
        }

    private fun joinReader(reader: Thread): Boolean =
        try {
            reader.join(OUTPUT_READER_JOIN_MILLISECONDS)
            !reader.isAlive
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
}
