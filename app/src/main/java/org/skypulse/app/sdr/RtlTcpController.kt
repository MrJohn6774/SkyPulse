package org.skypulse.app.sdr

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.SystemClock
import org.skypulse.app.diagnostics.DiagnosticLog
import org.skypulse.app.health.HealthState
import org.skypulse.app.settings.StationSettings
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class RtlTcpController(
    private val context: Context,
    private val settings: StationSettings,
) {
    private val running = AtomicBoolean(false)
    private val lifecycleLock = Any()
    private val wakeSignal = Object()
    private val sessionGeneration = AtomicLong(GLOBAL_GENERATION.incrementAndGet())
    @Volatile private var worker: Thread? = null
    @Volatile private var client: RtlTcpClient? = null
    @Volatile private var usbStableAfterMs = 0L
    @Volatile var state: RtlTcpState = RtlTcpState.STOPPED
        private set

    fun start() {
        synchronized(lifecycleLock) {
            if (!running.compareAndSet(false, true)) return
            worker = Thread(::recoveryLoop, "RtlTcpSupervisor").also { it.start() }
        }
    }

    fun stop() {
        val activeClient: RtlTcpClient?
        val activeWorker: Thread?
        synchronized(lifecycleLock) {
            if (!running.compareAndSet(true, false)) return
            advanceGeneration("controller stopped")
            activeClient = client
            activeWorker = worker
            worker = null
        }
        if (activeClient?.wasConnected == true) RtlTcpDriver.markTeardownPending()
        activeClient?.requestCleanStop()
        signalWorker()
        activeWorker?.interrupt()
        if (activeWorker !== Thread.currentThread()) runCatching { activeWorker?.join(STOP_JOIN_MS) }
        activeClient?.stop()
        transition(RtlTcpState.STOPPED)
        HealthState.rtlTcpConnected.set(false)
    }

    /** Invalidates the active socket immediately when Android reports detach/re-enumeration. */
    fun onUsbChanged(reason: String) {
        val activeClient: RtlTcpClient?
        synchronized(lifecycleLock) {
            if (!running.get()) return
            advanceGeneration("USB event $reason")
            activeClient = client
        }
        val hadConnectedSession = activeClient?.wasConnected == true
        if (hadConnectedSession) RtlTcpDriver.markTeardownPending()
        activeClient?.requestCleanStop()
        // Even if the write loses a race with USB removal, do not launch again until a
        // refused connection proves the obsolete listener has actually disappeared.
        activeClient?.stop()
        signalWorker()
    }

    private fun recoveryLoop() {
        var delayIndex = 0
        var observedUsb: String? = null
        while (running.get()) {
            var sessionClient: RtlTcpClient? = null
            var generationAtStart: Long? = null
            val connectedAt = SystemClock.elapsedRealtime()
            try {
                val usb = currentSdrDevice()
                if (usb == null) {
                    if (observedUsb != null) {
                        observedUsb = null
                        advanceGeneration("RTL-SDR disappeared")
                    }
                    transition(RtlTcpState.WAITING_FOR_DEVICE)
                    waitForSignal(DEVICE_POLL_MS)
                    continue
                }

                val fingerprint = usbFingerprint(usb)
                if (fingerprint != observedUsb) {
                    observedUsb = fingerprint
                    advanceGeneration("RTL-SDR enumerated as $fingerprint")
                }

                val stabilityWaitMs = usbStableAfterMs - SystemClock.elapsedRealtime()
                if (stabilityWaitMs > 0) {
                    transition(RtlTcpState.WAITING_FOR_USB_STABILITY)
                    waitForSignal(stabilityWaitMs)
                    continue
                }

                val generation = sessionGeneration.get()
                generationAtStart = generation
                transition(RtlTcpState.CONNECTING_TCP)
                sessionClient = RtlTcpClient(settings.rtlTcpPort) {
                    if (generation != sessionGeneration.get() || !running.get()) {
                        sessionClient?.stop()
                    } else {
                        transition(RtlTcpState.STREAMING)
                        DiagnosticLog.info(
                            TAG,
                            "Session $generation streaming valid I/Q on 127.0.0.1:${settings.rtlTcpPort}",
                        )
                    }
                }
                client = sessionClient
                sessionClient.stream()
                if (SystemClock.elapsedRealtime() - connectedAt >= STABLE_STREAM_MS) delayIndex = 0
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (error: Exception) {
                HealthState.rtlTcpConnected.set(false)
                if (!running.get()) break

                if (generationAtStart != null && generationAtStart != sessionGeneration.get()) {
                    DiagnosticLog.info(TAG, "Ignoring failure from stale session $generationAtStart")
                    continue
                }

                HealthState.recoveries.incrementAndGet()
                if (sessionClient?.wasConnected == true) {
                    transition(RtlTcpState.STOPPING_OLD_SESSION)
                    RtlTcpDriver.markTeardownPending()
                    sessionClient.stop()
                    DiagnosticLog.warn(
                        TAG,
                        "RTL-TCP session failed after connect (${error.message}); waiting for driver teardown",
                    )
                    waitForSignal(DRIVER_TEARDOWN_GRACE_MS)
                    delayIndex = 0
                } else {
                    if (RtlTcpDriver.confirmTeardownIfPending()) {
                        // Connection refusal is proof that the old listener is gone. Only now may
                        // a previous launch reservation be released.
                        DiagnosticLog.info(TAG, "Old RTL-TCP listener teardown confirmed")
                    }

                    val launched = synchronized(lifecycleLock) {
                        if (!running.get()) false
                        else RtlTcpDriver.requestStartFromBackground(context, settings)
                    }
                    transition(if (launched) RtlTcpState.STARTING_DRIVER else RtlTcpState.WAITING_FOR_LISTENER)
                    val retryMs = if (launched) {
                        // A newly requested activity normally opens its listener quickly. Reset
                        // accumulated outage backoff so we probe it promptly without relaunching.
                        delayIndex = 1
                        RETRY_DELAYS_MS[0]
                    } else {
                        RETRY_DELAYS_MS[delayIndex.coerceAtMost(RETRY_DELAYS_MS.lastIndex)].also {
                            delayIndex = (delayIndex + 1).coerceAtMost(RETRY_DELAYS_MS.lastIndex)
                        }
                    }
                    DiagnosticLog.warn(
                        TAG,
                        "RTL-TCP listener unavailable (${error.message}); retrying in ${retryMs / 1_000}s",
                    )
                    waitForSignal(retryMs)
                }
            } finally {
                sessionClient?.stop()
                if (client === sessionClient) client = null
                HealthState.rtlTcpConnected.set(false)
            }
        }
    }

    private fun currentSdrDevice(): UsbDevice? {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return manager.deviceList.values.firstOrNull { it.vendorId in SUPPORTED_USB_VENDOR_IDS }
    }

    private fun usbFingerprint(device: UsbDevice): String =
        "${device.vendorId.toString(16)}:${device.productId.toString(16)}:${device.deviceId}:${device.deviceName}"

    private fun advanceGeneration(reason: String): Long {
        val generation = GLOBAL_GENERATION.incrementAndGet()
        sessionGeneration.set(generation)
        usbStableAfterMs = SystemClock.elapsedRealtime() + USB_STABILITY_MS
        DiagnosticLog.info(TAG, "Session generation $generation: $reason")
        return generation
    }

    @Throws(InterruptedException::class)
    private fun waitForSignal(delayMs: Long) {
        if (delayMs <= 0 || !running.get()) return
        synchronized(wakeSignal) {
            if (running.get()) wakeSignal.wait(delayMs)
        }
    }

    private fun signalWorker() = synchronized(wakeSignal) { wakeSignal.notifyAll() }

    private fun transition(next: RtlTcpState) {
        if (state == next) return
        state = next
        HealthState.driverState.set(next.name.lowercase())
        DiagnosticLog.info(TAG, "State: $next")
    }

    companion object {
        private const val TAG = "ADSB.RtlTcp"
        private const val STABLE_STREAM_MS = 30_000L
        private const val DEVICE_POLL_MS = 2_000L
        private const val USB_STABILITY_MS = 3_000L
        private const val DRIVER_TEARDOWN_GRACE_MS = 3_000L
        private const val STOP_JOIN_MS = 1_500L
        private val RETRY_DELAYS_MS = longArrayOf(1_000, 2_000, 5_000, 10_000, 30_000)
        private val SUPPORTED_USB_VENDOR_IDS = setOf(3034, 3011)
        private val GLOBAL_GENERATION = AtomicLong(0L)
    }
}
