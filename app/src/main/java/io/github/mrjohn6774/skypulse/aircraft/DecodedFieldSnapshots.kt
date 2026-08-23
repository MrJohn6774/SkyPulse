package io.github.mrjohn6774.skypulse.aircraft

import com.flightaware.android.flightfeeder.analyzers.RawPosition
import com.flightaware.android.flightfeeder.analyzers.dump1090.ModeSMessage
import java.util.Locale

data class ModeSFrameSnapshot(
    val rawHex: String,
    val clockCount: Long,
    val format: Int,
    val extendedSquitterType: Int,
    val extendedSquitterSubtype: Int,
    val icao: String,
    val correctedBits: Int,
    val signalLevel: Double,
    val valid: Boolean,
) {
    companion object {
        fun from(source: ModeSMessage?): ModeSFrameSnapshot? = source?.let {
            ModeSFrameSnapshot(
                rawHex = it.bytesAsString,
                clockCount = it.clockCount,
                format = it.format,
                extendedSquitterType = it.metype,
                extendedSquitterSubtype = it.meSub,
                icao = String.format(Locale.US, "%06X", it.icao and 0xFFFFFF),
                correctedBits = it.numCorrectedBits.toInt(),
                signalLevel = it.signalLevel,
                valid = it.isValid,
            )
        }
    }
}

data class RawPositionSnapshot(
    val rawLatitude: Int,
    val rawLongitude: Int,
    val nucp: Int,
    val timestampUptimeMs: Long,
) {
    companion object {
        fun from(source: RawPosition?): RawPositionSnapshot? = source?.let {
            RawPositionSnapshot(
                rawLatitude = it.rawLatitude,
                rawLongitude = it.rawLongitude,
                nucp = it.nucp,
                timestampUptimeMs = it.timestamp,
            )
        }
    }
}
