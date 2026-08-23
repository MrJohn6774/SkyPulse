package io.github.mrjohn6774.skypulse.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.mrjohn6774.skypulse.diagnostics.DiagnosticLog
import io.github.mrjohn6774.skypulse.settings.StationSettings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!StationSettings(context).startAtBoot) return
        DiagnosticLog.info("ADSB.Service", "Boot event ${intent.action}; starting receiver")
        runCatching { AdsbForegroundService.start(context) }
            .onFailure { DiagnosticLog.error("ADSB.Service", "Boot service start rejected", it) }
    }
}
