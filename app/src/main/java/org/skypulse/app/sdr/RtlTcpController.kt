package org.skypulse.app.sdr

import android.content.Context
import android.hardware.usb.UsbManager
import android.os.SystemClock
import org.skypulse.app.diagnostics.DiagnosticLog
import org.skypulse.app.health.HealthState
import org.skypulse.app.settings.StationSettings
import java.util.concurrent.atomic.AtomicBoolean

class RtlTcpController(
    private val context: Context,
    private val settings: StationSettings,
) {
    private val running = AtomicBoolean(false)
    @Volatile private var worker: Thread? = null
    @Volatile private var client: RtlTcpClient? = null
    @Volatile var state: RtlTcpState = RtlTcpState.STOPPED
        private set

    fun start() {
        if (!running.compareAndSet(false, true)) return
        worker = Thread(::recoveryLoop, "RtlTcpSupervisor").also { it.start() }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        client?.stop()
        worker?.interrupt()
        worker = null
        transition(RtlTcpState.STOPPED)
        HealthState.rtlTcpConnected.set(false)
    }

    private fun recoveryLoop() {
        var delayIndex = 0
        var lastDriverRequestAt = 0L
        while (running.get()) {
            val connectedAt: Long
            try {
                if (!hasUsbDevice()) {
                    transition(RtlTcpState.WAITING_FOR_DEVICE)
                    SystemClock.sleep(2_000)
                    continue
                }
                transition(RtlTcpState.CONNECTING_TCP)
                connectedAt = SystemClock.elapsedRealtime()
                client = RtlTcpClient(settings.rtlTcpPort) {
                    transition(RtlTcpState.STREAMING)
                    lastDriverRequestAt = 0L
                    DiagnosticLog.info(TAG, "RTL-TCP streaming on 127.0.0.1:${settings.rtlTcpPort}")
                }
                client?.stream()
                if (SystemClock.elapsedRealtime() - connectedAt >= STABLE_STREAM_MS) delayIndex = 0
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (error: Exception) {
                HealthState.rtlTcpConnected.set(false)
                if (!running.get()) break
                val now = SystemClock.elapsedRealtime()
                if (lastDriverRequestAt == 0L || now - lastDriverRequestAt >= DRIVER_RESTART_INTERVAL_MS) {
                    transition(RtlTcpState.STARTING_DRIVER)
                    RtlTcpDriver.requestStartFromBackground(context, settings)
                    lastDriverRequestAt = now
                }
                transition(RtlTcpState.RECOVERING)
                HealthState.recoveries.incrementAndGet()
                val delayMs = RETRY_DELAYS_MS[delayIndex.coerceAtMost(RETRY_DELAYS_MS.lastIndex)]
                delayIndex = (delayIndex + 1).coerceAtMost(RETRY_DELAYS_MS.lastIndex)
                DiagnosticLog.warn(TAG, "RTL-TCP unavailable (${error.message}); retrying in ${delayMs / 1_000}s")
                SystemClock.sleep(delayMs)
            } finally {
                client?.stop()
                client = null
                HealthState.rtlTcpConnected.set(false)
            }
        }
    }

    private fun hasUsbDevice(): Boolean {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return manager.deviceList.isNotEmpty()
    }

    private fun transition(next: RtlTcpState) {
        if (state == next) return
        state = next
        HealthState.driverState.set(next.name.lowercase())
        DiagnosticLog.info(TAG, "State: $next")
    }

    companion object {
        private const val TAG = "ADSB.RtlTcp"
        private const val STABLE_STREAM_MS = 30_000L
        private const val DRIVER_RESTART_INTERVAL_MS = 30_000L
        private val RETRY_DELAYS_MS = longArrayOf(1_000, 2_000, 5_000, 10_000, 30_000)
    }
}
