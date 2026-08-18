package org.skypulse.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.skypulse.app.diagnostics.DiagnosticLog
import org.skypulse.app.settings.StationSettings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!StationSettings(context).startAtBoot) return
        DiagnosticLog.info("ADSB.Service", "Boot event ${intent.action}; starting receiver")
        runCatching { AdsbForegroundService.start(context) }
            .onFailure { DiagnosticLog.error("ADSB.Service", "Boot service start rejected", it) }
    }
}
