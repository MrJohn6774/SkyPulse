package io.github.mrjohn6774.skypulse.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.mrjohn6774.skypulse.diagnostics.DiagnosticLog
import io.github.mrjohn6774.skypulse.settings.StationSettings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ALLOWED_ACTIONS) return
        if (!StationSettings(context).startAtBoot) return
        DiagnosticLog.info("ADSB.Service", "Boot event ${intent.action}; starting receiver")
        runCatching { AdsbForegroundService.start(context) }
            .onFailure { DiagnosticLog.error("ADSB.Service", "Boot service start rejected", it) }
    }

    companion object {
        internal val ALLOWED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
