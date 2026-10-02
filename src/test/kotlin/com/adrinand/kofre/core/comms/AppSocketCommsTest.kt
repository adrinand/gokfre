package com.adrinand.kofre.core.comms

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AppSocketCommsTest {
    @After
    fun tearDown() {
        AppSocketComms.stopServer()
    }

    @Test
    fun `sendCommand delivers command to server listener`() {
        val latch = CountDownLatch(1)
        var receivedCommand: String? = null

        val acquired =
            AppSocketComms.tryAcquireServer { command ->
                receivedCommand = command
                latch.countDown()
            }

        assertTrue(acquired)

        val sent = AppSocketComms.sendCommand(AppSocketComms.COMMAND_SHOW_SETTINGS)

        assertTrue(sent)
        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertEquals(AppSocketComms.COMMAND_SHOW_SETTINGS, receivedCommand)
    }

    @Test
    fun `sendCommand returns false when server is not running`() {
        val sent = AppSocketComms.sendCommand(AppSocketComms.COMMAND_SHOW_SETTINGS)

        assertFalse(sent)
    }

    @Test
    fun `stopServer prevents further commands from being received`() {
        assertTrue(AppSocketComms.tryAcquireServer { })

        AppSocketComms.stopServer()

        val sent = AppSocketComms.sendCommand(AppSocketComms.COMMAND_SHOW_SETTINGS)
        assertFalse(sent)
    }

    @Test
    fun `tryAcquireServer returns true when no other instance is running`() {
        var receivedCommand = false

        val acquired =
            AppSocketComms.tryAcquireServer { command ->
                if (command == AppSocketComms.COMMAND_SHOW_SETTINGS) {
                    receivedCommand = true
                }
            }

        assertTrue(acquired)
        assertFalse(receivedCommand)
    }

    @Test
    fun `tryAcquireServer returns false and notifies an existing server`() {
        val latch = CountDownLatch(1)
        assertTrue(AppSocketComms.tryAcquireServer { latch.countDown() })

        val acquired = AppSocketComms.tryAcquireServer { }

        assertFalse(acquired)
        assertTrue(latch.await(2, TimeUnit.SECONDS))
    }
}
