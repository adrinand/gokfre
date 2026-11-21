package com.adrinand.gokfre.core.hotkey

import io.kotest.matchers.shouldBe
import org.junit.Test

class KeyCodeTest {
    @Test
    fun `key codes match the historical jnativehook values`() {
        listOf(
            KeyCode.VC_A to 0x1E,
            KeyCode.VC_B to 0x30,
            KeyCode.VC_C to 0x2E,
            KeyCode.VC_D to 0x20,
            KeyCode.VC_E to 0x12,
            KeyCode.VC_F to 0x21,
            KeyCode.VC_G to 0x22,
            KeyCode.VC_H to 0x23,
            KeyCode.VC_I to 0x17,
            KeyCode.VC_J to 0x24,
            KeyCode.VC_K to 0x25,
            KeyCode.VC_L to 0x26,
            KeyCode.VC_M to 0x32,
            KeyCode.VC_N to 0x31,
            KeyCode.VC_O to 0x18,
            KeyCode.VC_P to 0x19,
            KeyCode.VC_Q to 0x10,
            KeyCode.VC_R to 0x13,
            KeyCode.VC_S to 0x1F,
            KeyCode.VC_T to 0x14,
            KeyCode.VC_U to 0x16,
            KeyCode.VC_V to 0x2F,
            KeyCode.VC_W to 0x11,
            KeyCode.VC_X to 0x2D,
            KeyCode.VC_Y to 0x15,
            KeyCode.VC_Z to 0x2C,
            KeyCode.VC_1 to 0x02,
            KeyCode.VC_2 to 0x03,
            KeyCode.VC_3 to 0x04,
            KeyCode.VC_4 to 0x05,
            KeyCode.VC_5 to 0x06,
            KeyCode.VC_6 to 0x07,
            KeyCode.VC_7 to 0x08,
            KeyCode.VC_8 to 0x09,
            KeyCode.VC_9 to 0x0A,
            KeyCode.VC_0 to 0x0B,
            KeyCode.VC_F1 to 0x3B,
            KeyCode.VC_F2 to 0x3C,
            KeyCode.VC_F3 to 0x3D,
            KeyCode.VC_F4 to 0x3E,
            KeyCode.VC_F5 to 0x3F,
            KeyCode.VC_F6 to 0x40,
            KeyCode.VC_F7 to 0x41,
            KeyCode.VC_F8 to 0x42,
            KeyCode.VC_F9 to 0x43,
            KeyCode.VC_F10 to 0x44,
            KeyCode.VC_F11 to 0x57,
            KeyCode.VC_F12 to 0x58,
            KeyCode.VC_SPACE to 0x39,
            KeyCode.VC_ENTER to 0x1C,
            KeyCode.VC_BACKSPACE to 0x0E,
            KeyCode.VC_TAB to 0x0F,
            KeyCode.VC_SLASH to 0x35,
        ).forEach { (actual, expected) ->
            actual shouldBe expected
        }
    }
}
