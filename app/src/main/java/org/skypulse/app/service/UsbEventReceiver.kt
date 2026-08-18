package org.skypulse.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.skypulse.app.diagnostics.DiagnosticLog

class UsbEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DiagnosticLog.info("ADSB.Driver", "USB/driver event: ${intent.action}")
        if (intent.action != android.hardware.usb.UsbManager.ACTION_USB_DEVICE_DETACHED) {
            runCatching { AdsbForegroundService.start(context) }
                .onFailure { DiagnosticLog.error("ADSB.Service", "USB-triggered start rejected", it) }
        }
    }
}
