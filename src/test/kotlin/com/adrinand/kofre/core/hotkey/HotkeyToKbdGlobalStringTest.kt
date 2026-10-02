package com.adrinand.kofre.core.hotkey

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import io.kotest.matchers.shouldBe
import org.junit.Test

class HotkeyToKbdGlobalStringTest {
    @Test
    fun `Super plus K`() {
        val hotkey = Hotkey(NativeKeyEvent.VC_K, setOf(ModifierKey.SUPER))
        hotkey.toKbdGlobalString() shouldBe "Super+K"
    }

    @Test
    fun `Ctrl plus Shift plus A`() {
        val hotkey =
            Hotkey(
                NativeKeyEvent.VC_A,
                setOf(ModifierKey.CTRL, ModifierKey.SHIFT),
            )
        hotkey.toKbdGlobalString() shouldBe "Ctrl+Shift+A"
    }

    @Test
    fun `plain key`() {
        val hotkey = Hotkey(NativeKeyEvent.VC_F1)
        hotkey.toKbdGlobalString() shouldBe "F1"
    }
}
