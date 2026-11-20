package com.adrinand.gokfre.core.hotkey

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test

class MacKeyCodeMappingTest {
    @Test
    fun `maps letter to carbon key code`() {
        Hotkey(KeyCode.VC_K, setOf(ModifierKey.SUPER)).toCarbonHotkey() shouldBe
            CarbonHotkey(40, 0x0100)
    }

    @Test
    fun `maps every displayable key`() {
        KEY_CODE_TO_DISPLAY_NAME.keys.forEach { keyCode ->
            Hotkey(keyCode).toCarbonHotkey() shouldNotBe null
        }
    }

    @Test
    fun `maps digits and function keys`() {
        Hotkey(KeyCode.VC_1).toCarbonHotkey() shouldBe CarbonHotkey(18, 0)
        Hotkey(KeyCode.VC_F5).toCarbonHotkey() shouldBe CarbonHotkey(96, 0)
        Hotkey(KeyCode.VC_ENTER).toCarbonHotkey() shouldBe CarbonHotkey(36, 0)
    }

    @Test
    fun `maps each modifier to its own mask`() {
        Hotkey(KeyCode.VC_A, setOf(ModifierKey.CTRL)).toCarbonHotkey() shouldBe CarbonHotkey(0, 0x1000)
        Hotkey(KeyCode.VC_A, setOf(ModifierKey.ALT)).toCarbonHotkey() shouldBe CarbonHotkey(0, 0x0800)
        Hotkey(KeyCode.VC_A, setOf(ModifierKey.SHIFT)).toCarbonHotkey() shouldBe CarbonHotkey(0, 0x0200)
        Hotkey(KeyCode.VC_A, setOf(ModifierKey.SUPER)).toCarbonHotkey() shouldBe CarbonHotkey(0, 0x0100)
    }

    @Test
    fun `maps modifiers to carbon masks`() {
        Hotkey(
            KeyCode.VC_A,
            setOf(ModifierKey.SHIFT, ModifierKey.CTRL, ModifierKey.ALT, ModifierKey.SUPER),
        ).toCarbonHotkey() shouldBe
            CarbonHotkey(
                0,
                0x0100 or 0x0200 or 0x0800 or 0x1000,
            )
    }

    @Test
    fun `unknown key is not mappable`() {
        Hotkey(0x9999).toCarbonHotkey() shouldBe null
    }
}
