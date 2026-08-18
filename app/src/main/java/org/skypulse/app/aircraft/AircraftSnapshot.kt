package org.skypulse.app.aircraft

data class AircraftSnapshot(
    val icao: String,
    val callsign: String?,
    val latitude: Double?,
    val longitude: Double?,
    val altitudeFeet: Int?,
    val groundSpeedKnots: Int?,
    val headingDegrees: Int?,
    val trackDegrees: Int?,
    val verticalRateFeetPerMinute: Int?,
    val squawk: Int?,
    val emergencyState: Int?,
    val signalDb: Double,
    val messageCount: Int,
    val lastSeenEpochMs: Long,
)
