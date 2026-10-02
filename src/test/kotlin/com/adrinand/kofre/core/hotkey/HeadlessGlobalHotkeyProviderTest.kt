package com.adrinand.kofre.core.hotkey

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import io.kotest.matchers.shouldBe
import org.junit.Test

class HeadlessGlobalHotkeyProviderTest {
    @Test
    fun `register unregister and dispose should not throw`() {
        val provider = HeadlessGlobalHotkeyProvider
        val hotkey = Hotkey(NativeKeyEvent.VC_K, setOf(ModifierKey.SUPER))
        var invoked = false

        provider.register(hotkey) { invoked = true }
        provider.unregister(hotkey)
        provider.close()

        invoked shouldBe false
    }
}
