package com.adrinand.kofre.core.comms

import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.concurrent.thread
import kotlin.io.path.Path

object AppSocketComms {
    const val COMMAND_SHOW_SETTINGS = "SHOW_SETTINGS"
    private const val READ_BUFFER_SIZE = 256
    private val socketPath by lazy {
        Path(System.getProperty("java.io.tmpdir"), "kofre-${System.getProperty("user.name")}.sock")
    }
    private var serverChannel: ServerSocketChannel? = null

    fun tryAcquireServer(onCommand: (String) -> Unit): Boolean {
        if (sendCommand(COMMAND_SHOW_SETTINGS)) {
            return false
        }

        stopServer()
        runCatching { Files.deleteIfExists(socketPath) }

        val server =
            runCatching {
                ServerSocketChannel.open(StandardProtocolFamily.UNIX).apply {
                    bind(UnixDomainSocketAddress.of(socketPath))
                }
            }.getOrNull()

        if (server == null) {
            sendCommand(COMMAND_SHOW_SETTINGS)
            return false
        }

        startServerWithChannel(server, onCommand)
        return true
    }

    fun sendCommand(command: String): Boolean =
        runCatching {
            SocketChannel.open(StandardProtocolFamily.UNIX).use { client ->
                client.connect(UnixDomainSocketAddress.of(socketPath))
                client.write(ByteBuffer.wrap(command.toByteArray(StandardCharsets.UTF_8)))
            }
            true
        }.getOrDefault(false)

    fun stopServer() {
        runCatching { serverChannel?.close() }
        serverChannel = null
        runCatching { Files.deleteIfExists(socketPath) }
    }

    private fun startServerWithChannel(
        server: ServerSocketChannel,
        onCommand: (String) -> Unit,
    ) {
        serverChannel = server

        Runtime.getRuntime().addShutdownHook(
            Thread {
                runCatching { server.close() }
                runCatching { Files.deleteIfExists(socketPath) }
            },
        )

        thread(isDaemon = true, name = "KofreIpcServer") {
            while (true) {
                val client =
                    try {
                        server.accept()
                    } catch (_: Exception) {
                        break
                    }

                thread(isDaemon = true) {
                    runCatching {
                        val buffer = ByteBuffer.allocate(READ_BUFFER_SIZE)
                        client.read(buffer)
                        buffer.flip()
                        val command =
                            StandardCharsets.UTF_8
                                .decode(buffer)
                                .toString()
                                .trim()
                        if (command.isNotEmpty()) {
                            onCommand(command)
                        }
                    }
                    runCatching { client.close() }
                }
            }
        }
    }
}
