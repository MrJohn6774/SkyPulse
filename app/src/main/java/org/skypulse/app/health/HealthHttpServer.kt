package org.skypulse.app.health

import org.skypulse.app.diagnostics.DiagnosticLog
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class HealthHttpServer(private val port: Int = 8090) {
    private val running = AtomicBoolean(false)
    @Volatile private var listener: ServerSocket? = null
    @Volatile private var thread: Thread? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        thread = Thread(::acceptLoop, "HealthHttp").also { it.start() }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        runCatching { listener?.close() }
        thread?.interrupt()
        thread = null
    }

    private fun acceptLoop() {
        try {
            ServerSocket().use { server ->
                listener = server
                server.reuseAddress = true
                server.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 2)
                DiagnosticLog.info(TAG, "Health endpoint listening on 127.0.0.1:$port/status")
                while (running.get()) handle(server.accept())
            }
        } catch (error: Exception) {
            if (running.get()) DiagnosticLog.error(TAG, "Health listener failed", error)
        } finally {
            listener = null
        }
    }

    private fun handle(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 2_000
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII))
            val request = reader.readLine().orEmpty()
            while (!reader.readLine().isNullOrEmpty()) Unit
            val isStatus = request.startsWith("GET /status ")
            val body = if (isStatus) toJson(HealthState.snapshot()) else "{\"error\":\"not_found\"}"
            val status = if (isStatus) "200 OK" else "404 Not Found"
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            val headers = "HTTP/1.1 $status\r\nContent-Type: application/json\r\n" +
                "Content-Length: ${bytes.size}\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n"
            client.getOutputStream().apply {
                write(headers.toByteArray(StandardCharsets.US_ASCII))
                write(bytes)
                flush()
            }
        }
    }

    private fun toJson(value: HealthSnapshot): String = buildString(320) {
        append('{')
        append("\"service\":\"").append(value.service).append("\",")
        append("\"driver\":\"").append(value.driver).append("\",")
        append("\"rtl_tcp_connected\":").append(value.rtlTcpConnected).append(',')
        append("\"iq_bytes_per_sec\":").append(value.iqBytesPerSec).append(',')
        append("\"messages_per_sec\":").append(String.format(Locale.US, "%.1f", value.messagesPerSec)).append(',')
        append("\"aircraft_active\":").append(value.aircraftActive).append(',')
        append("\"beast_clients\":").append(value.beastClients).append(',')
        append("\"last_iq_ms_ago\":").append(value.lastIqMsAgo ?: "null").append(',')
        append("\"last_message_ms_ago\":").append(value.lastMessageMsAgo ?: "null").append(',')
        append("\"uptime_seconds\":").append(value.uptimeSeconds).append(',')
        append("\"recoveries\":").append(value.recoveries)
        append('}')
    }

    companion object {
        private const val TAG = "ADSB.Service"
    }
}
