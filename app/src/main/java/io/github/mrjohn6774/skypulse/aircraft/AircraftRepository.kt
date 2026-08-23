package io.github.mrjohn6774.skypulse.aircraft

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
            altitudeSource = source.altitudeSource,
            baroSetting = source.baroSetting,
            category = source.category,
            selectedAltitudeFeet = source.selectedAltitude,
            selectedHeadingDegrees = source.selectedHeading,
            altitudeHoldEnabled = source.isAltitudeHoldEnabled,
            autopilotEngaged = source.isAutoPilotEngaged,
            onApproach = source.isOnApproach,
            onGround = source.isOnGround,
            ready = source.isReady(System.currentTimeMillis()),
            tcasEnabled = source.isTcasEnabled,
            uat = source.isUat,
            verticalNavEnabled = source.isVerticalNavEnabled,
            lastPositionEpochMs = source.seenLatLon,
            headingDeltaDegrees = source.headingDelta,
            evenMessage = ModeSFrameSnapshot.from(source.evenMessage),
            evenPosition = RawPositionSnapshot.from(source.evenPosition),
            oddMessage = ModeSFrameSnapshot.from(source.oddMessage),
            oddPosition = RawPositionSnapshot.from(source.oddPosition),
            fileExportTimestampMs = source.timeStampFileExport,
        )
        if (aircraft.size > MAX_AIRCRAFT) expire(System.currentTimeMillis(), force = true)
    }

    fun active(nowMs: Long = System.currentTimeMillis()): List<AircraftSnapshot> {
        expire(nowMs, force = false)
        return aircraft.values.sortedBy { it.icao }
    }

    /** Active aircraft which can be represented by a marker on the map. */
    fun mappable(nowMs: Long = System.currentTimeMillis()): List<AircraftSnapshot> =
        active(nowMs).filter { it.hasValidPosition }

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
