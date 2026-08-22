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
    val squawk: String?,
    val emergencyState: Int?,
    val signalDb: Double,
    val messageCount: Int,
    val lastSeenEpochMs: Long,
) {
    val hasValidPosition: Boolean
        get() {
            val latitude = latitude ?: return false
            val longitude = longitude ?: return false
            return latitude.isFinite() && longitude.isFinite() &&
                latitude in -90.0..90.0 && longitude in -180.0..180.0
        }
}
