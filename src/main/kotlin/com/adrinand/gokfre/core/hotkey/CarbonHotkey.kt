package com.adrinand.gokfre.core.hotkey

internal data class CarbonHotkey(
    val keyCode: Int,
    val modifiers: Int,
)

private const val CARBON_CMD_KEY = 0x0100
private const val CARBON_SHIFT_KEY = 0x0200
private const val CARBON_OPTION_KEY = 0x0800
private const val CARBON_CONTROL_KEY = 0x1000

private object CarbonKeyCode {
    const val A = 0x00
    const val S = 0x01
    const val D = 0x02
    const val F = 0x03
    const val H = 0x04
    const val G = 0x05
    const val Z = 0x06
    const val X = 0x07
    const val C = 0x08
    const val V = 0x09
    const val B = 0x0B
    const val Q = 0x0C
    const val W = 0x0D
    const val E = 0x0E
    const val R = 0x0F
    const val Y = 0x10
    const val T = 0x11
    const val ONE = 0x12
    const val TWO = 0x13
    const val THREE = 0x14
    const val FOUR = 0x15
    const val SIX = 0x16
    const val FIVE = 0x17
    const val NINE = 0x19
    const val SEVEN = 0x1A
    const val EIGHT = 0x1C
    const val ZERO = 0x1D
    const val O = 0x1F
    const val U = 0x20
    const val I = 0x22
    const val P = 0x23
    const val RETURN = 0x24
    const val L = 0x25
    const val J = 0x26
    const val K = 0x28
    const val N = 0x2D
    const val M = 0x2E
    const val SLASH = 0x2C
    const val TAB = 0x30
    const val SPACE = 0x31
    const val DELETE = 0x33
    const val F1 = 0x7A
    const val F2 = 0x78
    const val F3 = 0x63
    const val F4 = 0x76
    const val F5 = 0x60
    const val F6 = 0x61
    const val F7 = 0x62
    const val F8 = 0x64
    const val F9 = 0x65
    const val F10 = 0x6D
    const val F11 = 0x67
    const val F12 = 0x6F
}

private val KEY_CODE_TO_CARBON_KEY_CODE: Map<Int, Int> =
    mapOf(
        KeyCode.VC_A to CarbonKeyCode.A,
        KeyCode.VC_B to CarbonKeyCode.B,
        KeyCode.VC_C to CarbonKeyCode.C,
        KeyCode.VC_D to CarbonKeyCode.D,
        KeyCode.VC_E to CarbonKeyCode.E,
        KeyCode.VC_F to CarbonKeyCode.F,
        KeyCode.VC_G to CarbonKeyCode.G,
        KeyCode.VC_H to CarbonKeyCode.H,
        KeyCode.VC_I to CarbonKeyCode.I,
        KeyCode.VC_J to CarbonKeyCode.J,
        KeyCode.VC_K to CarbonKeyCode.K,
        KeyCode.VC_L to CarbonKeyCode.L,
        KeyCode.VC_M to CarbonKeyCode.M,
        KeyCode.VC_N to CarbonKeyCode.N,
        KeyCode.VC_O to CarbonKeyCode.O,
        KeyCode.VC_P to CarbonKeyCode.P,
        KeyCode.VC_Q to CarbonKeyCode.Q,
        KeyCode.VC_R to CarbonKeyCode.R,
        KeyCode.VC_S to CarbonKeyCode.S,
        KeyCode.VC_T to CarbonKeyCode.T,
        KeyCode.VC_U to CarbonKeyCode.U,
        KeyCode.VC_V to CarbonKeyCode.V,
        KeyCode.VC_W to CarbonKeyCode.W,
        KeyCode.VC_X to CarbonKeyCode.X,
        KeyCode.VC_Y to CarbonKeyCode.Y,
        KeyCode.VC_Z to CarbonKeyCode.Z,
        KeyCode.VC_1 to CarbonKeyCode.ONE,
        KeyCode.VC_2 to CarbonKeyCode.TWO,
        KeyCode.VC_3 to CarbonKeyCode.THREE,
        KeyCode.VC_4 to CarbonKeyCode.FOUR,
        KeyCode.VC_5 to CarbonKeyCode.FIVE,
        KeyCode.VC_6 to CarbonKeyCode.SIX,
        KeyCode.VC_7 to CarbonKeyCode.SEVEN,
        KeyCode.VC_8 to CarbonKeyCode.EIGHT,
        KeyCode.VC_9 to CarbonKeyCode.NINE,
        KeyCode.VC_0 to CarbonKeyCode.ZERO,
        KeyCode.VC_F1 to CarbonKeyCode.F1,
        KeyCode.VC_F2 to CarbonKeyCode.F2,
        KeyCode.VC_F3 to CarbonKeyCode.F3,
        KeyCode.VC_F4 to CarbonKeyCode.F4,
        KeyCode.VC_F5 to CarbonKeyCode.F5,
        KeyCode.VC_F6 to CarbonKeyCode.F6,
        KeyCode.VC_F7 to CarbonKeyCode.F7,
        KeyCode.VC_F8 to CarbonKeyCode.F8,
        KeyCode.VC_F9 to CarbonKeyCode.F9,
        KeyCode.VC_F10 to CarbonKeyCode.F10,
        KeyCode.VC_F11 to CarbonKeyCode.F11,
        KeyCode.VC_F12 to CarbonKeyCode.F12,
        KeyCode.VC_SPACE to CarbonKeyCode.SPACE,
        KeyCode.VC_ENTER to CarbonKeyCode.RETURN,
        KeyCode.VC_BACKSPACE to CarbonKeyCode.DELETE,
        KeyCode.VC_TAB to CarbonKeyCode.TAB,
        KeyCode.VC_SLASH to CarbonKeyCode.SLASH,
    )

internal fun Hotkey.toCarbonHotkey(): CarbonHotkey? {
    val carbonKeyCode = KEY_CODE_TO_CARBON_KEY_CODE[keyCode] ?: return null
    return CarbonHotkey(carbonKeyCode, modifiers.toCarbonModifiers())
}

private fun Set<ModifierKey>.toCarbonModifiers(): Int =
    fold(0) { modifiers, modifier ->
        modifiers or
            when (modifier) {
                ModifierKey.SHIFT -> CARBON_SHIFT_KEY
                ModifierKey.CTRL -> CARBON_CONTROL_KEY
                ModifierKey.ALT -> CARBON_OPTION_KEY
                ModifierKey.SUPER -> CARBON_CMD_KEY
            }
    }
