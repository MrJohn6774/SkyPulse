package com.flightaware.android.flightfeeder.analyzers.dump1090

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DecoderPositionPolicyTest {
    @Test
    fun `airborne global CPR remains usable without receiver coordinates`() {
        assertTrue(Decoder.isGlobalPositionUsable(false, null, 31.2, 121.5))
    }

    @Test
    fun `surface global CPR still requires a location reference`() {
        assertFalse(Decoder.isGlobalPositionUsable(true, null, 31.2, 121.5))
    }
}
