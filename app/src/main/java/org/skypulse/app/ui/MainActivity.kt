package org.skypulse.app.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import org.skypulse.app.diagnostics.DiagnosticLog
import org.skypulse.app.health.HealthState
import org.skypulse.app.map.MapActivity
import org.skypulse.app.sdr.RtlTcpDriver
import org.skypulse.app.service.AdsbForegroundService
import org.skypulse.app.settings.StationSettings
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var settings: StationSettings
    private lateinit var status: TextView

    private val driverLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            DiagnosticLog.info("ADSB.Driver", "SDR driver reported successful startup")
            Toast.makeText(this, "SDR driver started", Toast.LENGTH_SHORT).show()
        } else {
            val reason = result.data?.getStringExtra("detailed_exception_message")
                ?: "Driver did not open the SDR"
            DiagnosticLog.warn("ADSB.Driver", reason)
            Toast.makeText(this, reason, Toast.LENGTH_LONG).show()
        }
    }

    private val refresh = object : Runnable {
        override fun run() {
            renderStatus()
            handler.postDelayed(this, 1_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = StationSettings(this)
        setContentView(buildContent())
        requestNotificationPermission()
    }

    override fun onStart() {
        super.onStart()
        handler.post(refresh)
    }

    override fun onStop() {
        handler.removeCallbacks(refresh)
        super.onStop()
    }

    private fun buildContent(): ScrollView {
        val content = verticalLayout()
        content.addMatchWidth(heading("SkyPulse receiver"))
        status = TextView(this).apply {
            textSize = 16f
            setTextIsSelectable(true)
        }
        content.addMatchWidth(status)

        content.addMatchWidth(section("Receiver control"))
        content.addMatchWidth(MaterialButton(this).apply {
            text = "Start"
            setOnClickListener { startReceiverAndDriver() }
        })
        content.addMatchWidth(MaterialButton(this).apply {
            text = "Restart receiver"
            setOnClickListener {
                settings.receiverEnabled = true
                AdsbForegroundService.start(this@MainActivity, AdsbForegroundService.ACTION_RESTART)
            }
        })
        content.addMatchWidth(MaterialButton(this).apply {
            text = "Stop"
            setOnClickListener { AdsbForegroundService.stop(this@MainActivity) }
        })

        content.addMatchWidth(section("Station"))
        content.addMatchWidth(MaterialButton(this).apply {
            text = "Aircraft map"
            setOnClickListener { startActivity(Intent(this@MainActivity, MapActivity::class.java)) }
        })
        content.addMatchWidth(MaterialButton(this).apply {
            text = "Settings"
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        })
        content.addMatchWidth(MaterialButton(this).apply {
            text = "Diagnostics"
            setOnClickListener { startActivity(Intent(this@MainActivity, DiagnosticsActivity::class.java)) }
        })
        return ScrollView(this).apply { addView(content) }
    }

    private fun startReceiverAndDriver() {
        settings.receiverEnabled = true
        val driverIntent = RtlTcpDriver.createIntent(this, settings)
        if (driverIntent == null) {
            AdsbForegroundService.start(this)
            Toast.makeText(this, "Install an iqsrc-compatible rtl_tcp_andro driver", Toast.LENGTH_LONG).show()
        } else if (!RtlTcpDriver.reserveForegroundLaunch()) {
            AdsbForegroundService.start(this)
            Toast.makeText(this, "SDR driver startup is already in progress", Toast.LENGTH_SHORT).show()
        } else {
            // Reserve the single-flight launch before the service supervisor starts. Otherwise
            // its first refused TCP connection can race this foreground activity launch.
            AdsbForegroundService.start(this)
            driverLauncher.launch(driverIntent)
        }
    }

    private fun renderStatus() {
        val value = HealthState.snapshot()
        val overall = when {
            !value.service.equals("running", true) -> "STOPPED"
            value.rtlTcpConnected && value.iqBytesPerSec > 0 -> "RUNNING"
            else -> "DEGRADED"
        }
        status.text = buildString {
            append(overall).append("\n\n")
            append("SDR state: ").append(value.driver).append('\n')
            append("RTL-TCP: ").append(if (value.rtlTcpConnected) "connected" else "disconnected").append('\n')
            append("Frequency: ").append(settings.frequency / 1_000_000.0).append(" MHz\n")
            append("Sample rate: ").append(settings.sampleRate).append(" samples/s\n")
            append("Gain: ").append(if (settings.automaticGain) "AGC" else "${settings.gainTenthsDb / 10.0} dB").append('\n')
            append("I/Q rate: ").append(value.iqBytesPerSec).append(" bytes/s\n")
            append("Messages: ").append(String.format(Locale.US, "%.1f/s", value.messagesPerSec)).append('\n')
            append("Aircraft: ").append(value.aircraftActive).append('\n')
            append("Beast 127.0.0.1:").append(settings.beastPort).append(": ")
                .append(if (value.beastClients > 0) "client connected" else "waiting").append('\n')
            append("Uptime: ").append(value.uptimeSeconds).append(" s\n")
            append("Recoveries: ").append(value.recoveries)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 33)
    }
}
