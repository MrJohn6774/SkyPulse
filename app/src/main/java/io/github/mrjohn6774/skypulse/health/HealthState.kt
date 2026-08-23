package io.github.mrjohn6774.skypulse.health

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

data class HealthSnapshot(
    val service: String,
    val driver: String,
    val rtlTcpConnected: Boolean,
    val iqBytesPerSec: Long,
    val messagesPerSec: Double,
    val aircraftActive: Int,
    val beastClients: Int,
    val beastPipeline: String,
    val beastTcpExportEnabled: Boolean,
    val lastIqMsAgo: Long?,
    val lastMessageMsAgo: Long?,
    val uptimeSeconds: Long,
    val recoveries: Long,
)

object HealthState {
    val serviceRunning = AtomicBoolean(false)
    val driverState = AtomicReference("stopped")
    val rtlTcpConnected = AtomicBoolean(false)
    val aircraftActive = AtomicInteger(0)
    val beastClients = AtomicInteger(0)
    val beastPipelineRunning = AtomicBoolean(false)
    val beastTcpExportEnabled = AtomicBoolean(false)
    val recoveries = AtomicLong(0)
    val totalIqBytes = AtomicLong(0)
    val totalMessages = AtomicLong(0)
    val lastIqElapsedMs = AtomicLong(0)
    val lastMessageElapsedMs = AtomicLong(0)

    @Volatile private var startedElapsedMs = 0L
    private var rateSampleElapsedMs = 0L
    private var rateIqBytes = 0L
    private var rateMessages = 0L
    private var cachedIqRate = 0L
    private var cachedMessageRate = 0.0

    @Synchronized
    fun markStarted() {
        val now = SystemClock.elapsedRealtime()
        startedElapsedMs = now
        rateSampleElapsedMs = now
        rateIqBytes = totalIqBytes.get()
        rateMessages = totalMessages.get()
        serviceRunning.set(true)
    }

    fun onIqBytes(count: Int) {
        totalIqBytes.addAndGet(count.toLong())
        lastIqElapsedMs.set(SystemClock.elapsedRealtime())
    }

    fun onMessage() {
        totalMessages.incrementAndGet()
        lastMessageElapsedMs.set(SystemClock.elapsedRealtime())
    }

    @Synchronized
    fun snapshot(): HealthSnapshot {
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - rateSampleElapsedMs
        if (elapsed >= 1_000L) {
            val iq = totalIqBytes.get()
            val messages = totalMessages.get()
            cachedIqRate = ((iq - rateIqBytes) * 1_000L / elapsed.coerceAtLeast(1L))
            cachedMessageRate = (messages - rateMessages) * 1_000.0 / elapsed.coerceAtLeast(1L)
            rateIqBytes = iq
            rateMessages = messages
            rateSampleElapsedMs = now
        }
        return HealthSnapshot(
            service = if (serviceRunning.get()) "running" else "stopped",
            driver = driverState.get(),
            rtlTcpConnected = rtlTcpConnected.get(),
            iqBytesPerSec = cachedIqRate,
            messagesPerSec = cachedMessageRate,
            aircraftActive = aircraftActive.get(),
            beastClients = beastClients.get(),
            beastPipeline = if (beastPipelineRunning.get()) "running" else "stopped",
            beastTcpExportEnabled = beastTcpExportEnabled.get(),
            lastIqMsAgo = age(now, lastIqElapsedMs.get()),
            lastMessageMsAgo = age(now, lastMessageElapsedMs.get()),
            uptimeSeconds = if (serviceRunning.get()) (now - startedElapsedMs).coerceAtLeast(0) / 1_000 else 0,
            recoveries = recoveries.get(),
        )
    }

    private fun age(now: Long, event: Long): Long? = if (event == 0L) null else (now - event).coerceAtLeast(0)
}
