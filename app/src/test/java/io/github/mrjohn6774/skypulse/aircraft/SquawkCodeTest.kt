package io.github.mrjohn6774.skypulse.aircraft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SquawkCodeTest {
    @Test
    fun `packed octal squawk is formatted as exactly four digits`() {
        assertEquals("7700", SquawkCode.fromPackedOctal(0x7700))
        assertEquals("1203", SquawkCode.fromPackedOctal(0x1203))
        assertEquals("0007", SquawkCode.fromPackedOctal(0x0007))
    }

    @Test
    fun `invalid packed squawk is rejected`() {
        assertNull(SquawkCode.fromPackedOctal(0x8000))
        assertNull(SquawkCode.fromPackedOctal(null))
    }
}
