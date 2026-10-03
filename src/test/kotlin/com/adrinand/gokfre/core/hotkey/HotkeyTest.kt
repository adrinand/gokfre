package com.adrinand.gokfre.core.hotkey

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import io.kotest.matchers.shouldBe
import org.junit.Test

class HotkeyTest {
    @Test
    fun `default toggle should be Super plus K`() {
        Hotkey.DEFAULT_TOGGLE.keyCode shouldBe NativeKeyEvent.VC_K
        Hotkey.DEFAULT_TOGGLE.modifiers shouldBe setOf(ModifierKey.SUPER)
    }
}
