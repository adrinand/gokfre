package com.adrinand.kofre.core.hotkey

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import io.kotest.matchers.shouldBe
import org.junit.Assume
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class LinuxEvdevHotkeyProviderTest {
    @Test
    fun `init and register Super plus K`() {
        Assume.assumeTrue(System.getProperty("os.name").lowercase().contains("linux"))
        Assume.assumeTrue(InputDevicePermissionChecker.hasInputDeviceAccess())

        val provider = LinuxEvdevHotkeyProvider()
        val invoked = AtomicBoolean(false)
        provider.register(
            Hotkey(
                NativeKeyEvent.VC_K,
                setOf(ModifierKey.SUPER),
            ),
        ) { invoked.set(true) }

        // We don't simulate the actual key press here; just verify registration does not throw.
        invoked.get() shouldBe false
        provider.dispose()
    }
}
