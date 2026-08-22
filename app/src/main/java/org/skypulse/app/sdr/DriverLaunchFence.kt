package org.skypulse.app.sdr

internal class DriverLaunchFence(
    private val singleFlightMs: Long,
) {
    private var blockedUntilMs = 0L

    @Synchronized
    fun tryAcquire(nowMs: Long): Boolean {
        if (nowMs < blockedUntilMs) return false
        blockedUntilMs = nowMs + singleFlightMs
        return true
    }

    @Synchronized
    fun remainingMs(nowMs: Long): Long = (blockedUntilMs - nowMs).coerceAtLeast(0L)

    @Synchronized
    fun releaseAfterConfirmedTeardown() {
        blockedUntilMs = 0L
    }
}
