package io.github.mrjohn6774.skypulse.sdr

import com.flightaware.android.flightfeeder.analyzers.dump1090.RtlSdrDataQueue
import io.github.mrjohn6774.skypulse.diagnostics.DiagnosticLog
import io.github.mrjohn6774.skypulse.health.HealthState
import java.io.EOFException
import java.io.InputStream
import java.net.InetAddress
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean

class RtlTcpClient(
    private val port: Int,
    private val staleThresholdMs: Long = 5_000L,
    private val onStreaming: () -> Unit,
) {
    private val running = AtomicBoolean(true)
    private val cleanExitRequested = AtomicBoolean(false)
    @Volatile private var socket: Socket? = null
    @Volatile var wasConnected: Boolean = false
        private set
    @Volatile var hasReceivedIq: Boolean = false
        private set

    fun stream() {
        Socket().use { client ->
            socket = client
            client.tcpNoDelay = true
            client.receiveBufferSize = BUFFER_SIZE * 2
            client.soTimeout = READ_TIMEOUT_MS
            client.connect(InetSocketAddress(LOOPBACK_ADDRESS, port), CONNECT_TIMEOUT_MS)
            wasConnected = true
            val input = client.getInputStream()
            consumeHeader(input)
            readIq(input)
        }
    }

    private fun requestDriverExit(): Boolean {
        val client = socket ?: return false
        if (!wasConnected || client.isClosed) return false
        return try {
            RtlTcpControl.requestDriverExit(client.getOutputStream())
            DiagnosticLog.info("ADSB.RtlTcp", "Requested remote driver exit")
            true
        } catch (error: Exception) {
            DiagnosticLog.warn("ADSB.RtlTcp", "Could not send remote driver exit", error)
            false
        }
    }

    /** Lets the stream thread send the network command without violating main-thread policy. */
    fun requestCleanStop() {
        cleanExitRequested.set(true)
        running.set(false)
    }

    fun stop() {
        running.set(false)
        runCatching { socket?.close() }
    }

    private fun consumeHeader(input: InputStream) {
        val header = ByteArray(RTL_TCP_HEADER_SIZE)
        var count = 0
        while (count < header.size) {
            val read = input.read(header, count, header.size - count)
            if (read < 0) throw EOFException("RTL-TCP closed before header")
            count += read
        }
        val isHeader = header[0] == 'R'.code.toByte() && header[1] == 'T'.code.toByte() &&
            header[2] == 'L'.code.toByte() && header[3] == '0'.code.toByte()
        if (!isHeader) offerEvenIq(header, header.size, null)
    }

    private fun readIq(input: InputStream) {
        val buffer = ByteArray(BUFFER_SIZE)
        var pending: Byte? = null
        var lastReadAt = android.os.SystemClock.elapsedRealtime()
        try {
            while (running.get()) {
                try {
                    val offset = if (pending == null) 0 else {
                        buffer[0] = pending
                        pending = null
                        1
                    }
                    val count = input.read(buffer, offset, buffer.size - offset)
                    if (count < 0) throw EOFException("RTL-TCP stream closed")
                    if (count == 0) continue
                    lastReadAt = android.os.SystemClock.elapsedRealtime()
                    if (!hasReceivedIq) {
                        hasReceivedIq = true
                        HealthState.rtlTcpConnected.set(true)
                        onStreaming()
                    }
                    val total = offset + count
                    pending = offerEvenIq(buffer, total, pending)
                    HealthState.onIqBytes(count)
                } catch (timeout: SocketTimeoutException) {
                    if (android.os.SystemClock.elapsedRealtime() - lastReadAt >= staleThresholdMs) {
                        requestDriverExit()
                        throw SocketTimeoutException("No RTL-TCP I/Q bytes for $staleThresholdMs ms")
                    }
                }
            }
        } finally {
            if (cleanExitRequested.get()) requestDriverExit()
        }
    }

    private fun offerEvenIq(buffer: ByteArray, count: Int, unused: Byte?): Byte? {
        val evenCount = count and 1.inv()
        if (evenCount > 0) RtlSdrDataQueue.offer(buffer.copyOf(evenCount))
        return if (count != evenCount) buffer[count - 1] else null
    }

    companion object {
        // The Android rtl_tcp driver is explicitly started with "-a 127.0.0.1". Do not use
        // InetAddress.getLoopbackAddress() here: on some devices it resolves to IPv6 ::1,
        // which cannot connect to the driver's IPv4-only listener.
        internal val LOOPBACK_ADDRESS: InetAddress = InetAddress.getByAddress(
            byteArrayOf(127, 0, 0, 1),
        ).also { check(it is Inet4Address) }

        private const val BUFFER_SIZE = 256 * 1024
        private const val RTL_TCP_HEADER_SIZE = 12
        private const val CONNECT_TIMEOUT_MS = 1_500
        private const val READ_TIMEOUT_MS = 1_000
    }
}
