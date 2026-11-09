package com.adrinand.gokfre

import com.adrinand.gokfre.core.hotkey.GlobalHotkeyProvider
import com.adrinand.gokfre.core.hotkey.Hotkey
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val REGISTRATION_TIMEOUT_MS = 5_000L

/**
 * A [GlobalHotkeyProvider] that lets tests trigger the toggle callback
 * programmatically instead of relying on OS-level global hotkeys.
 */
class TestGlobalHotkeyProvider : GlobalHotkeyProvider {
    private var registeredCallback: (() -> Unit)? = null
    private val registrationLatch = CountDownLatch(1)

    override fun register(
        hotkey: Hotkey,
        callback: () -> Unit,
    ) {
        registeredCallback = callback
        registrationLatch.countDown()
    }

    override fun unregister(hotkey: Hotkey) {
        registeredCallback = null
    }

    override fun dispose() {
        registeredCallback = null
    }

    fun awaitRegistered(timeoutMs: Long = REGISTRATION_TIMEOUT_MS) {
        registrationLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
    }

    fun triggerToggle() {
        registeredCallback?.invoke()
    }
}
