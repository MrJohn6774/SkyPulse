package org.skypulse.app.decoder

import android.util.Log
import com.flightaware.android.flightfeeder.analyzers.Aircraft
import com.flightaware.android.flightfeeder.analyzers.IAnalyzerExportDispatcher
import com.flightaware.android.flightfeeder.analyzers.IAnalyzerLogger
import com.flightaware.android.flightfeeder.analyzers.IAnalyzerStatusNotifier
import com.flightaware.android.flightfeeder.analyzers.ILocationProvider
import com.flightaware.android.flightfeeder.analyzers.dump1090.ModeSMessage
import org.skypulse.app.aircraft.AircraftRepository
import org.skypulse.app.diagnostics.DiagnosticLog
import org.skypulse.app.export.BeastTcpServer
import org.skypulse.app.health.HealthState
import org.skypulse.app.model.GeoPoint
import org.skypulse.app.settings.StationSettings

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
        HealthState.aircraftActive.set(AircraftRepository.active().size)
    }

    override fun onReadyAircraftUpdated(message: ModeSMessage, aircraft: Aircraft, uptimeMs: Long) = Unit

    companion object {
        private const val TAG = "ADSB.Decoder"
    }
}
