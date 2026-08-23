package io.github.mrjohn6774.skypulse.export

import com.flightaware.android.flightfeeder.analyzers.dump1090.ModeSMessage
import io.github.mrjohn6774.skypulse.diagnostics.DiagnosticLog
import io.github.mrjohn6774.skypulse.health.HealthState
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
    private val pipelineRunning = AtomicBoolean(false)
    private val listenerRunning = AtomicBoolean(false)
    private val activeClient = AtomicReference<ClientSession?>()
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var acceptThread: Thread? = null

    fun start() {
        pipelineRunning.set(true)
        HealthState.beastPipelineRunning.set(true)
    }

    fun setTcpExportEnabled(enabled: Boolean) {
        if (enabled) {
            if (!listenerRunning.compareAndSet(false, true)) return
            HealthState.beastTcpExportEnabled.set(true)
        } else {
            if (!listenerRunning.compareAndSet(true, false)) return
            runCatching { serverSocket?.close() }
            activeClient.getAndSet(null)?.close()
            HealthState.beastClients.set(0)
            HealthState.beastTcpExportEnabled.set(false)
            return
        }
        acceptThread = Thread(::acceptLoop, "BeastAccept").also { it.start() }
    }

    fun publish(message: ModeSMessage) {
        val bytes = BeastEncoder.encode(message) ?: return
        if (!pipelineRunning.get()) return
        val session = activeClient.get() ?: return
        if (!session.offer(bytes)) {
            DiagnosticLog.warn(TAG, "Slow Beast client exceeded bounded queue; disconnecting")
            session.close()
        }
    }

    fun stop() {
        pipelineRunning.set(false)
        HealthState.beastPipelineRunning.set(false)
        setTcpExportEnabled(false)
    }

    private fun acceptLoop() {
        try {
            ServerSocket().use { listener ->
                listener.reuseAddress = true
                listener.bind(InetSocketAddress(LOOPBACK_ADDRESS, port), 2)
                serverSocket = listener
                DiagnosticLog.info(TAG, "Listening on 127.0.0.1:$port")
                while (listenerRunning.get()) {
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
            if (listenerRunning.get()) DiagnosticLog.error(TAG, "Beast listener failed", error)
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
