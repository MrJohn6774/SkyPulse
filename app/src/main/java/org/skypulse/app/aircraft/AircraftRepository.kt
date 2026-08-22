package org.skypulse.app.aircraft

import com.flightaware.android.flightfeeder.analyzers.Aircraft
import java.util.concurrent.ConcurrentHashMap

object AircraftRepository {
    private const val EXPIRY_MS = 60_000L
    private const val MAX_AIRCRAFT = 300
    private val aircraft = ConcurrentHashMap<String, AircraftSnapshot>()

    fun update(source: Aircraft) {
        val icao = source.icao ?: return
        aircraft[icao] = AircraftSnapshot(
            icao = icao,
            callsign = source.identity?.trim()?.ifBlank { null },
            latitude = source.latitude,
            longitude = source.longitude,
            altitudeFeet = source.altitude,
            groundSpeedKnots = source.velocity,
            headingDegrees = source.heading,
            trackDegrees = source.trackAngle ?: source.heading,
            verticalRateFeetPerMinute = source.verticalRate,
            squawk = SquawkCode.fromPackedOctal(source.squawk),
            emergencyState = source.status,
            signalDb = source.averageSignalStrength,
            messageCount = source.messageCount,
            lastSeenEpochMs = source.seen,
        )
        if (aircraft.size > MAX_AIRCRAFT) expire(System.currentTimeMillis(), force = true)
    }

    fun active(nowMs: Long = System.currentTimeMillis()): List<AircraftSnapshot> {
        expire(nowMs, force = false)
        return aircraft.values.sortedBy { it.icao }
    }

    fun clear() = aircraft.clear()

    private fun expire(nowMs: Long, force: Boolean) {
        aircraft.entries
            .filter { nowMs - it.value.lastSeenEpochMs > EXPIRY_MS }
            .forEach { aircraft.remove(it.key, it.value) }
        if (force && aircraft.size > MAX_AIRCRAFT) {
            aircraft.values.sortedBy { it.lastSeenEpochMs }
                .take(aircraft.size - MAX_AIRCRAFT)
                .forEach { aircraft.remove(it.icao, it) }
        }
    }
}
