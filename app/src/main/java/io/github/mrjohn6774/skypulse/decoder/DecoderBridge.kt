package io.github.mrjohn6774.skypulse.decoder

import android.util.Log
import com.flightaware.android.flightfeeder.analyzers.Aircraft
import com.flightaware.android.flightfeeder.analyzers.IAnalyzerExportDispatcher
import com.flightaware.android.flightfeeder.analyzers.IAnalyzerLogger
import com.flightaware.android.flightfeeder.analyzers.IAnalyzerStatusNotifier
import com.flightaware.android.flightfeeder.analyzers.ILocationProvider
import com.flightaware.android.flightfeeder.analyzers.dump1090.ModeSMessage
import io.github.mrjohn6774.skypulse.aircraft.AircraftRepository
import io.github.mrjohn6774.skypulse.diagnostics.DiagnosticLog
import io.github.mrjohn6774.skypulse.export.BeastTcpServer
import io.github.mrjohn6774.skypulse.health.HealthState
import io.github.mrjohn6774.skypulse.model.GeoPoint
import io.github.mrjohn6774.skypulse.settings.StationSettings

class DecoderBridge(
    private val settings: StationSettings,
    private val beastServer: BeastTcpServer,
) : ILocationProvider, IAnalyzerLogger, IAnalyzerStatusNotifier, IAnalyzerExportDispatcher {

    override fun getLocation(): GeoPoint? = settings.receiverPoint()

    override fun d(msg: String) {
        Log.d(TAG, msg)
    }

    override fun i(msg: String) = DiagnosticLog.info(TAG, msg)

    override fun e(msg: String) = DiagnosticLog.error(TAG, msg)

    override fun e(msg: String, t: Throwable?) = DiagnosticLog.error(TAG, msg, t)

    override fun onNewAircraftDecoded() = Unit

    override fun onRawFrameDetected(message: ModeSMessage) {
        HealthState.onMessage()
        beastServer.publish(message)
    }

    override fun onAircraftUpdated(message: ModeSMessage, aircraft: Aircraft) {
        AircraftRepository.update(aircraft)
        HealthState.aircraftActive.set(AircraftRepository.mappable().size)
    }

    override fun onReadyAircraftUpdated(message: ModeSMessage, aircraft: Aircraft, uptimeMs: Long) = Unit

    companion object {
        private const val TAG = "ADSB.Decoder"
    }
}
