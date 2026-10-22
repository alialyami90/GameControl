package com.gamecontroller.hid

import kotlin.math.roundToInt

/**
 * The phone's Bluetooth HID identity, "GameCtl Pad": a composite keyboard (report id 1)
 * and gamepad (report id 2: 64 buttons, 8 signed 16-bit axes).
 */
object HidDescriptor {
    const val NAME = "GameCtl Pad"
    const val DESCRIPTION = "Game Controller touch surface"
    const val PROVIDER = "GameController"
    const val KEYBOARD_REPORT_ID = 1
    const val GAMEPAD_REPORT_ID = 2
    const val BUTTONS = 64
    const val AXES = 8
    val AXIS_NAMES = listOf("X", "Y", "Z", "RX", "RY", "RZ", "SL0", "SL1")

    val REPORT_MAP: ByteArray = bytes(
        // Keyboard
        0x05, 0x01, 0x09, 0x06, 0xA1, 0x01,
        0x85, KEYBOARD_REPORT_ID,
        0x05, 0x07, 0x19, 0xE0, 0x29, 0xE7, 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, 0x08, 0x81, 0x02, // modifiers
        0x95, 0x01, 0x75, 0x08, 0x81, 0x01, // reserved
        0x95, 0x05, 0x75, 0x01, 0x05, 0x08, 0x19, 0x01, 0x29, 0x05, 0x91, 0x02, // LEDs
        0x95, 0x01, 0x75, 0x03, 0x91, 0x01, // LED padding
        0x95, 0x06, 0x75, 0x08, 0x15, 0x00, 0x26, 0xFF, 0x00, 0x05, 0x07, 0x19, 0x00, 0x2A, 0xFF, 0x00, 0x81, 0x00, // keys
        0xC0,
        // Gamepad
        0x05, 0x01, 0x09, 0x05, 0xA1, 0x01,
        0x85, GAMEPAD_REPORT_ID,
        0x05, 0x09, 0x19, 0x01, 0x29, BUTTONS, 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, BUTTONS, 0x81, 0x02,
        0x05, 0x01,
        0x09, 0x30, 0x09, 0x31, 0x09, 0x32, 0x09, 0x33, 0x09, 0x34, 0x09, 0x35, 0x09, 0x36, 0x09, 0x37,
        0x16, 0x01, 0x80, 0x26, 0xFF, 0x7F, 0x75, 0x10, 0x95, AXES, 0x81, 0x02,
        0xC0,
    )

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
}

/** Key names from the protocol's key table mapped to HID keyboard usage ids. */
object HidKeys {
    /** Modifier key name to bit in the modifier byte. */
    val MODIFIERS = mapOf(
        "LCTRL" to 0, "LSHIFT" to 1, "LALT" to 2, "LWIN" to 3,
        "RCTRL" to 4, "RSHIFT" to 5, "RALT" to 6, "RWIN" to 7,
    )

    val USAGES: Map<String, Int> = buildMap {
        ('A'..'Z').forEachIndexed { i, c -> put(c.toString(), 0x04 + i) }
        (1..9).forEach { put("D$it", 0x1D + it) }
        put("D0", 0x27)
        put("ENTER", 0x28); put("ESC", 0x29); put("BACKSPACE", 0x2A); put("TAB", 0x2B); put("SPACE", 0x2C)
        put("MINUS", 0x2D); put("EQUALS", 0x2E); put("LBRACKET", 0x2F); put("RBRACKET", 0x30); put("BACKSLASH", 0x31)
        put("SEMICOLON", 0x33); put("APOSTROPHE", 0x34); put("GRAVE", 0x35); put("COMMA", 0x36); put("PERIOD", 0x37)
        put("SLASH", 0x38); put("CAPSLOCK", 0x39)
        (1..12).forEach { put("F$it", 0x39 + it) }
        put("PRINTSCREEN", 0x46); put("SCROLLLOCK", 0x47); put("PAUSE", 0x48); put("INSERT", 0x49); put("HOME", 0x4A)
        put("PAGEUP", 0x4B); put("DELETE", 0x4C); put("END", 0x4D); put("PAGEDOWN", 0x4E)
        put("RIGHT", 0x4F); put("LEFT", 0x50); put("DOWN", 0x51); put("UP", 0x52)
        put("NUMLOCK", 0x53); put("NUMPAD_DIVIDE", 0x54); put("NUMPAD_MULTIPLY", 0x55); put("NUMPAD_SUBTRACT", 0x56)
        put("NUMPAD_ADD", 0x57); put("NUMPAD_ENTER", 0x58)
        (1..9).forEach { put("NUMPAD$it", 0x58 + it) }
        put("NUMPAD0", 0x62); put("NUMPAD_DECIMAL", 0x63); put("APPS", 0x65)
        (13..24).forEach { put("F$it", 0x68 + it - 13) }
    }

    /** Every key name the app offers in the key picker, modifiers first. */
    val ALL_NAMES: List<String> = MODIFIERS.keys.toList() + USAGES.keys

    fun isKnown(name: String) = name in MODIFIERS || name in USAGES
}

/**
 * Current HID output state. Keys and buttons are reference counted so two controls holding the
 * same key do not release it early. Every mutator returns true when the report changed.
 */
class HidState {
    private val keyCounts = LinkedHashMap<String, Int>()
    private val buttonCounts = IntArray(HidDescriptor.BUTTONS)
    private val axes = IntArray(HidDescriptor.AXES)

    fun keyDown(name: String): Boolean {
        if (!HidKeys.isKnown(name)) return false
        val n = keyCounts[name] ?: 0
        keyCounts[name] = n + 1
        return n == 0
    }

    fun isKeyDown(name: String): Boolean = (keyCounts[name] ?: 0) > 0

    fun keyUp(name: String): Boolean {
        val n = keyCounts[name] ?: return false
        return if (n <= 1) { keyCounts.remove(name); true } else { keyCounts[name] = n - 1; false }
    }

    /** [button] is 1-based. */
    fun buttonDown(button: Int): Boolean {
        if (button !in 1..HidDescriptor.BUTTONS) return false
        return buttonCounts[button - 1]++ == 0
    }

    fun buttonUp(button: Int): Boolean {
        if (button !in 1..HidDescriptor.BUTTONS || buttonCounts[button - 1] == 0) return false
        return --buttonCounts[button - 1] == 0
    }

    /** [value] is -1..1 over the full axis range. */
    fun setAxis(axis: String, value: Float): Boolean {
        val i = HidDescriptor.AXIS_NAMES.indexOf(axis.uppercase())
        if (i < 0) return false
        val raw = (value.coerceIn(-1f, 1f) * 32767f).roundToInt()
        if (axes[i] == raw) return false
        axes[i] = raw
        return true
    }

    fun releaseAll() {
        keyCounts.clear()
        buttonCounts.fill(0)
        axes.fill(0)
    }

    /** Keyboard report payload (without report id): modifiers, reserved, 6 key usages. */
    fun keyboardReport(): ByteArray {
        val out = ByteArray(8)
        var mods = 0
        var slot = 2
        var overflow = false
        for (name in keyCounts.keys) {
            val bit = HidKeys.MODIFIERS[name]
            if (bit != null) {
                mods = mods or (1 shl bit)
            } else {
                val usage = HidKeys.USAGES[name] ?: continue
                if (slot < 8) out[slot++] = usage.toByte() else overflow = true
            }
        }
        // More than 6 keys: the HID boot protocol reports ErrorRollOver in every slot.
        if (overflow) for (i in 2 until 8) out[i] = 0x01
        out[0] = mods.toByte()
        return out
    }

    /** Gamepad report payload (without report id): 8 button bytes then 8 little-endian int16 axes. */
    fun gamepadReport(): ByteArray {
        val out = ByteArray(HidDescriptor.BUTTONS / 8 + HidDescriptor.AXES * 2)
        for (b in 0 until HidDescriptor.BUTTONS) {
            if (buttonCounts[b] > 0) out[b / 8] = (out[b / 8].toInt() or (1 shl (b % 8))).toByte()
        }
        val base = HidDescriptor.BUTTONS / 8
        for (i in 0 until HidDescriptor.AXES) {
            out[base + i * 2] = (axes[i] and 0xFF).toByte()
            out[base + i * 2 + 1] = ((axes[i] shr 8) and 0xFF).toByte()
        }
        return out
    }
}
