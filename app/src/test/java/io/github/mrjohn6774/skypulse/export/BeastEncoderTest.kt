package io.github.mrjohn6774.skypulse.export

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BeastEncoderTest {
    @Test
    fun `encodes short mode s frame with standard header timestamp and rssi`() {
        val frame = byteArrayOf(0x5D, 0x48, 0x40, 0xD6.toByte(), 0xF9.toByte(), 0x4B, 0xC6.toByte())

        val encoded = BeastEncoder.encode(frame, 0x010203040506L, 1.0)

        assertArrayEquals(
            byteArrayOf(
                0x1A, '2'.code.toByte(),
                0x01, 0x02, 0x03, 0x04, 0x05, 0x06,
                0xFF.toByte(),
                *frame,
            ),
            encoded,
        )
    }

    @Test
    fun `encodes long frame and escapes every data 1a byte`() {
        val frame = byteArrayOf(
            0x8D.toByte(), 0x1A, 0x40, 0x62, 0x58, 0x1A, 0x38, 0x26,
            0xA0.toByte(), 0x57, 0x60, 0x98.toByte(), 0xB8.toByte(), 0x7E,
        )
        val signalThatRoundsTo1a = (26.0 / 255.0) * (26.0 / 255.0)

        val encoded = BeastEncoder.encode(frame, 0x001A02030405L, signalThatRoundsTo1a)

        assertArrayEquals(
            byteArrayOf(
                0x1A, '3'.code.toByte(),
                0x00, 0x1A, 0x1A, 0x02, 0x03, 0x04, 0x05,
                0x1A, 0x1A,
                0x8D.toByte(), 0x1A, 0x1A, 0x40, 0x62, 0x58, 0x1A, 0x1A, 0x38, 0x26,
                0xA0.toByte(), 0x57, 0x60, 0x98.toByte(), 0xB8.toByte(), 0x7E,
            ),
            encoded,
        )
    }

    @Test
    fun `rejects unsupported frame length`() {
        assertNull(BeastEncoder.encode(byteArrayOf(1, 2, 3), 0L, 0.0))
    }
}
