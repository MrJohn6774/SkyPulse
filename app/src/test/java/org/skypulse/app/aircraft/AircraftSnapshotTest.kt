package org.skypulse.app.aircraft

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AircraftSnapshotTest {
    @Test
    fun `only finite in-range coordinates are mappable`() {
        assertTrue(snapshot(22.30, 114.17).hasValidPosition)
        assertFalse(snapshot(null, 114.17).hasValidPosition)
        assertFalse(snapshot(91.0, 114.17).hasValidPosition)
        assertFalse(snapshot(22.30, Double.NaN).hasValidPosition)
    }

    private fun snapshot(latitude: Double?, longitude: Double?) = AircraftSnapshot(
        icao = "ABC123",
        callsign = null,
        latitude = latitude,
        longitude = longitude,
        altitudeFeet = null,
        groundSpeedKnots = null,
        headingDegrees = null,
        trackDegrees = null,
        verticalRateFeetPerMinute = null,
        squawk = null,
        emergencyState = null,
        signalDb = 0.0,
        messageCount = 1,
        lastSeenEpochMs = 0L,
    )
}
