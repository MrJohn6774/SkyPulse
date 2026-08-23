package io.github.mrjohn6774.skypulse.sdr

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class RtlTcpControlTest {
    @Test
    fun `driver exit is encoded as one command byte and a zero network parameter`() {
        val output = ByteArrayOutputStream()

        RtlTcpControl.requestDriverExit(output)

        assertArrayEquals(byteArrayOf(0x7E, 0, 0, 0, 0), output.toByteArray())
    }
}
