package org.skypulse.app.export

import com.flightaware.android.flightfeeder.analyzers.dump1090.ModeSMessage
import org.skypulse.app.diagnostics.DiagnosticLog
import org.skypulse.app.health.HealthState
import java.io.BufferedOutputStream
import java.net.InetAddress
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class BeastTcpServer(private val port: Int) {
    private val running = AtomicBoolean(false)
    private val activeClient = AtomicReference<ClientSession?>()
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var acceptThread: Thread? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        acceptThread = Thread(::acceptLoop, "BeastAccept").also { it.start() }
    }

    fun publish(message: ModeSMessage) {
        val session = activeClient.get() ?: return
        val bytes = BeastEncoder.encode(message) ?: return
        if (!session.offer(bytes)) {
            DiagnosticLog.warn(TAG, "Slow Beast client exceeded bounded queue; disconnecting")
            session.close()
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        runCatching { serverSocket?.close() }
        activeClient.getAndSet(null)?.close()
        acceptThread?.interrupt()
        acceptThread = null
        HealthState.beastClients.set(0)
    }

    private fun acceptLoop() {
        try {
            ServerSocket().use { listener ->
                listener.reuseAddress = true
                listener.bind(InetSocketAddress(LOOPBACK_ADDRESS, port), 2)
                serverSocket = listener
                DiagnosticLog.info(TAG, "Listening on 127.0.0.1:$port")
                while (running.get()) {
                    val socket = listener.accept()
                    socket.tcpNoDelay = true
                    socket.keepAlive = true
                    val replacement = ClientSession(socket) { closed ->
                        if (activeClient.compareAndSet(closed, null)) HealthState.beastClients.set(0)
                    }
                    activeClient.getAndSet(replacement)?.close()
                    HealthState.beastClients.set(1)
                    DiagnosticLog.info(TAG, "Beast client connected from ${socket.inetAddress.hostAddress}")
                    replacement.start()
                }
            }
        } catch (error: Exception) {
            if (running.get()) DiagnosticLog.error(TAG, "Beast listener failed", error)
        } finally {
            serverSocket = null
        }
    }

    private class ClientSession(
        private val socket: Socket,
        private val onClosed: (ClientSession) -> Unit,
    ) {
        private val open = AtomicBoolean(true)
        private val queue = ArrayBlockingQueue<ByteArray>(QUEUE_CAPACITY)

        fun start() = Thread(::writeLoop, "BeastWriter").start()

        fun offer(frame: ByteArray): Boolean = open.get() && queue.offer(frame)

        fun close() {
            if (!open.compareAndSet(true, false)) return
            runCatching { socket.close() }
            onClosed(this)
        }

        private fun writeLoop() {
            try {
                BufferedOutputStream(socket.getOutputStream(), 64 * 1024).use { output ->
                    while (open.get()) {
                        val frame = queue.take()
                        output.write(frame)
                        if (queue.isEmpty()) output.flush()
                    }
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (error: Exception) {
                DiagnosticLog.warn(TAG, "Beast client disconnected", error)
            } finally {
                close()
            }
        }

        companion object {
            private const val QUEUE_CAPACITY = 2_048
        }
    }

    companion object {
        internal val LOOPBACK_ADDRESS: InetAddress = InetAddress.getByAddress(
            byteArrayOf(127, 0, 0, 1),
        ).also { check(it is Inet4Address) }
        private const val TAG = "ADSB.Beast"
    }
}
