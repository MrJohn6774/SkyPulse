package org.skypulse.app.sdr

import java.net.Inet4Address
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RtlTcpClientTest {
    @Test
    fun `client uses the same IPv4 loopback address passed to the driver`() {
        val address = RtlTcpClient.LOOPBACK_ADDRESS

        assertTrue(address is Inet4Address)
        assertEquals("127.0.0.1", address.hostAddress)
    }
}
