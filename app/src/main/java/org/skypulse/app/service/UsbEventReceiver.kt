package org.skypulse.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import org.skypulse.app.diagnostics.DiagnosticLog
import org.skypulse.app.health.HealthState
import org.skypulse.app.settings.StationSettings

class UsbEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DiagnosticLog.info("ADSB.Driver", "USB/driver event: ${intent.action}")
        val event = intent.action.orEmpty()
        // USB re-enumeration may be caused by shutting the driver down. Never let that
        // resurrect a station which the user explicitly stopped.
        if (!StationSettings(context).receiverEnabled) return
        if (event == UsbManager.ACTION_USB_DEVICE_DETACHED && !HealthState.serviceRunning.get()) return
        runCatching { AdsbForegroundService.notifyUsbChanged(context, event) }
            .onFailure { DiagnosticLog.error("ADSB.Service", "USB-triggered start rejected", it) }
    }
}
