package org.skypulse.app.aircraft

internal object SquawkCode {
    /** Converts dump1090's four packed octal nibbles (for example 0x7700) to display text. */
    fun fromPackedOctal(value: Int?): String? {
        if (value == null || value !in 0..0x7777 || value and 0x8888 != 0) return null
        return String.format(java.util.Locale.US, "%04X", value)
    }
}
