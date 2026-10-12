package io.github.mrjohn6774.skypulse.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import io.github.mrjohn6774.skypulse.diagnostics.DiagnosticLog
import io.github.mrjohn6774.skypulse.health.HealthState
import io.github.mrjohn6774.skypulse.sdr.DriverStartNotification
import io.github.mrjohn6774.skypulse.sdr.RtlTcpDriver
import io.github.mrjohn6774.skypulse.service.AdsbForegroundService
import io.github.mrjohn6774.skypulse.settings.StationSettings

/**
 * Invisible activity opened from the driver start notification. Being visible is what lets
 * Android accept the driver activity start that the foreground service was not allowed to make.
 */
class DriverStartActivity : ComponentActivity() {
    private val driverLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val failure = RtlTcpDriver.describeFailure(result.resultCode, result.data)
        if (failure == null) {
            DiagnosticLog.info(TAG, "SDR driver reported successful startup")
        } else {
            DiagnosticLog.warn(TAG, failure)
            Toast.makeText(this, failure, Toast.LENGTH_LONG).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        DriverStartNotification.cancel(this)
        val settings = StationSettings(this)
        val intent = RtlTcpDriver.createIntent(this, settings)
        when {
            HealthState.rtlTcpConnected.get() -> finish()
            intent == null -> {
                Toast.makeText(this, "Install an iqsrc-compatible rtl_tcp_andro driver", Toast.LENGTH_LONG).show()
                finish()
            }
            !RtlTcpDriver.reserveForegroundLaunch() -> {
                Toast.makeText(this, "SDR driver startup is already in progress", Toast.LENGTH_SHORT).show()
                finish()
            }
            else -> {
                DiagnosticLog.info(TAG, "Starting driver from notification: ${settings.driverArguments()}")
                AdsbForegroundService.start(this, AdsbForegroundService.ACTION_DRIVER_LAUNCH_REQUESTED)
                driverLauncher.launch(intent)
            }
        }
    }

    private companion object {
        const val TAG = "ADSB.Driver"
    }
}
