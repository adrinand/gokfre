package com.adrinand.gokfre.core.hotkey

object KeyCode {
    const val VC_A = 0x1E
    const val VC_B = 0x30
    const val VC_C = 0x2E
    const val VC_D = 0x20
    const val VC_E = 0x12
    const val VC_F = 0x21
    const val VC_G = 0x22
    const val VC_H = 0x23
    const val VC_I = 0x17
    const val VC_J = 0x24
    const val VC_K = 0x25
    const val VC_L = 0x26
    const val VC_M = 0x32
    const val VC_N = 0x31
    const val VC_O = 0x18
    const val VC_P = 0x19
    const val VC_Q = 0x10
    const val VC_R = 0x13
    const val VC_S = 0x1F
    const val VC_T = 0x14
    const val VC_U = 0x16
    const val VC_V = 0x2F
    const val VC_W = 0x11
    const val VC_X = 0x2D
    const val VC_Y = 0x15
    const val VC_Z = 0x2C

    const val VC_1 = 0x02
    const val VC_2 = 0x03
    const val VC_3 = 0x04
    const val VC_4 = 0x05
    const val VC_5 = 0x06
    const val VC_6 = 0x07
    const val VC_7 = 0x08
    const val VC_8 = 0x09
    const val VC_9 = 0x0A
    const val VC_0 = 0x0B

    const val VC_F1 = 0x3B
    const val VC_F2 = 0x3C
    const val VC_F3 = 0x3D
    const val VC_F4 = 0x3E
    const val VC_F5 = 0x3F
    const val VC_F6 = 0x40
    const val VC_F7 = 0x41
    const val VC_F8 = 0x42
    const val VC_F9 = 0x43
    const val VC_F10 = 0x44
    const val VC_F11 = 0x57
    const val VC_F12 = 0x58

    const val VC_SPACE = 0x39
    const val VC_ENTER = 0x1C
    const val VC_BACKSPACE = 0x0E
    const val VC_TAB = 0x0F
    const val VC_SLASH = 0x35
}

val KEY_CODE_TO_DISPLAY_NAME: Map<Int, String> =
    mapOf(
        KeyCode.VC_A to "A",
        KeyCode.VC_B to "B",
        KeyCode.VC_C to "C",
        KeyCode.VC_D to "D",
        KeyCode.VC_E to "E",
        KeyCode.VC_F to "F",
        KeyCode.VC_G to "G",
        KeyCode.VC_H to "H",
        KeyCode.VC_I to "I",
        KeyCode.VC_J to "J",
        KeyCode.VC_K to "K",
        KeyCode.VC_L to "L",
        KeyCode.VC_M to "M",
        KeyCode.VC_N to "N",
        KeyCode.VC_O to "O",
        KeyCode.VC_P to "P",
        KeyCode.VC_Q to "Q",
        KeyCode.VC_R to "R",
        KeyCode.VC_S to "S",
        KeyCode.VC_T to "T",
        KeyCode.VC_U to "U",
        KeyCode.VC_V to "V",
        KeyCode.VC_W to "W",
        KeyCode.VC_X to "X",
        KeyCode.VC_Y to "Y",
        KeyCode.VC_Z to "Z",
        KeyCode.VC_1 to "1",
        KeyCode.VC_2 to "2",
        KeyCode.VC_3 to "3",
        KeyCode.VC_4 to "4",
        KeyCode.VC_5 to "5",
        KeyCode.VC_6 to "6",
        KeyCode.VC_7 to "7",
        KeyCode.VC_8 to "8",
        KeyCode.VC_9 to "9",
        KeyCode.VC_0 to "0",
        KeyCode.VC_F1 to "F1",
        KeyCode.VC_F2 to "F2",
        KeyCode.VC_F3 to "F3",
        KeyCode.VC_F4 to "F4",
        KeyCode.VC_F5 to "F5",
        KeyCode.VC_F6 to "F6",
        KeyCode.VC_F7 to "F7",
        KeyCode.VC_F8 to "F8",
        KeyCode.VC_F9 to "F9",
        KeyCode.VC_F10 to "F10",
        KeyCode.VC_F11 to "F11",
        KeyCode.VC_F12 to "F12",
        KeyCode.VC_SPACE to "Space",
        KeyCode.VC_ENTER to "Enter",
        KeyCode.VC_BACKSPACE to "Backspace",
        KeyCode.VC_TAB to "Tab",
        KeyCode.VC_SLASH to "Slash",
    )

val DISPLAY_NAME_TO_KEY_CODE: Map<String, Int> =
    KEY_CODE_TO_DISPLAY_NAME.entries.associate { it.value to it.key }
