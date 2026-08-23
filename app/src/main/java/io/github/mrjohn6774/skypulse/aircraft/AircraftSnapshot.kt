package io.github.mrjohn6774.skypulse.aircraft

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
    val altitudeSource: String? = null,
    val baroSetting: Float? = null,
    val category: Int? = null,
    val selectedAltitudeFeet: Int? = null,
    val selectedHeadingDegrees: Int? = null,
    val altitudeHoldEnabled: Boolean = false,
    val autopilotEngaged: Boolean = false,
    val onApproach: Boolean = false,
    val onGround: Boolean = false,
    val ready: Boolean = false,
    val tcasEnabled: Boolean = false,
    val uat: Boolean = false,
    val verticalNavEnabled: Boolean = false,
    val lastPositionEpochMs: Long = 0L,
    val headingDeltaDegrees: Int = 0,
    val evenMessage: ModeSFrameSnapshot? = null,
    val evenPosition: RawPositionSnapshot? = null,
    val oddMessage: ModeSFrameSnapshot? = null,
    val oddPosition: RawPositionSnapshot? = null,
    val fileExportTimestampMs: Long = 0L,
) {
    val hasValidPosition: Boolean
        get() {
            val latitude = latitude ?: return false
            val longitude = longitude ?: return false
            return latitude.isFinite() && longitude.isFinite() &&
                latitude in -90.0..90.0 && longitude in -180.0..180.0
        }
}
