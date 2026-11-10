package com.adrinand.gokfre.core.screen

import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusMemberName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.exceptions.DBusException
import org.freedesktop.dbus.interfaces.DBusInterface
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.logging.Logger

internal const val KDE_BRIDGE_NAME = "org.kde.gokfre.Bridge"
internal const val KDE_BRIDGE_PATH = "/org/kde/gokfre/Bridge"

private val logger = Logger.getLogger("com.adrinand.gokfre.core.screen.KdeBridge")

/**
 * D-Bus interface KWin scripts call back through.
 */
@DBusInterfaceName(KDE_BRIDGE_NAME)
internal interface KdeBridgeInterface : DBusInterface {
    @DBusMemberName("Report")
    fun report(
        token: String,
        payload: String,
    )

    override fun getObjectPath(): String
}

/**
 * App-hosted D-Bus service that collects results from KWin scripts.
 */
internal class KdeBridge private constructor(
    private val connection: DBusConnection,
) : KdeBridgeInterface {
    private val pending = ConcurrentHashMap<String, CompletableFuture<String>>()

    override fun report(
        token: String,
        payload: String,
    ) {
        pending.remove(token)?.complete(payload)
    }

    override fun getObjectPath(): String = KDE_BRIDGE_PATH

    fun register(token: String): CompletableFuture<String> = pending.computeIfAbsent(token) { CompletableFuture() }

    fun await(
        token: String,
        future: CompletableFuture<String>,
        timeoutMs: Long,
    ): String? =
        try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            logger.fine { "Timed out waiting for KWin script result ($token): ${e.message}" }
            null
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.fine { "Interrupted waiting for KWin script result ($token): ${e.message}" }
            null
        } catch (e: ExecutionException) {
            logger.fine { "KWin script result failed ($token): ${e.message}" }
            null
        } finally {
            pending.remove(token)
        }

    fun unregister(token: String) {
        pending.remove(token)
    }

    fun connection(): DBusConnection = connection

    companion object {
        @Volatile
        private var instance: KdeBridge? = null

        @Synchronized
        fun getInstance(): KdeBridge? {
            instance?.let { return it }
            val created = connect() ?: return null
            instance = created
            return created
        }

        fun connect(): KdeBridge? {
            val connection = openConnection() ?: return null
            return try {
                val bridge = KdeBridge(connection)
                connection.requestBusName(KDE_BRIDGE_NAME)
                connection.exportObject(KDE_BRIDGE_PATH, bridge)
                Runtime.getRuntime().addShutdownHook(Thread { closeQuietly(connection) })
                bridge
            } catch (e: DBusException) {
                logger.warning { "Could not start KDE D-Bus bridge: ${e.message}" }
                closeQuietly(connection)
                null
            } catch (e: IllegalArgumentException) {
                logger.warning { "Could not start KDE D-Bus bridge: ${e.message}" }
                closeQuietly(connection)
                null
            }
        }

        private fun openConnection(): DBusConnection? =
            try {
                DBusConnectionBuilder.forSessionBus().build()
            } catch (e: DBusException) {
                logger.warning { "Could not connect KDE D-Bus bridge: ${e.message}" }
                null
            } catch (e: IOException) {
                logger.warning { "Could not connect KDE D-Bus bridge: ${e.message}" }
                null
            } catch (e: IllegalArgumentException) {
                logger.warning { "Invalid KDE D-Bus bridge configuration: ${e.message}" }
                null
            }

        private fun closeQuietly(connection: DBusConnection) {
            try {
                connection.close()
            } catch (e: IOException) {
                logger.fine { "Closing KDE D-Bus bridge failed: ${e.message}" }
            }
        }
    }
}
