package io.github.mrjohn6774.skypulse.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import io.github.mrjohn6774.skypulse.diagnostics.DiagnosticLog
import io.github.mrjohn6774.skypulse.health.HealthState
import io.github.mrjohn6774.skypulse.settings.StationSettings

class UsbEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DiagnosticLog.info("ADSB.Driver", "USB/driver event: ${intent.action}")
        val event = intent.action.orEmpty()
        if (event !in ALLOWED_ACTIONS) return
        // USB re-enumeration may be caused by shutting the driver down. Never let that
        // resurrect a station which the user explicitly stopped.
        if (!StationSettings(context).receiverEnabled) return
        if (event == UsbManager.ACTION_USB_DEVICE_DETACHED && !HealthState.serviceRunning.get()) return
        runCatching { AdsbForegroundService.notifyUsbChanged(context, event) }
            .onFailure { DiagnosticLog.error("ADSB.Service", "USB-triggered start rejected", it) }
    }

    companion object {
        internal val ALLOWED_ACTIONS = setOf(
            "com.sdrtouch.rtlsdr.SDR_DEVICE_ATTACHED",
            UsbManager.ACTION_USB_DEVICE_ATTACHED,
            UsbManager.ACTION_USB_DEVICE_DETACHED,
        )
    }
}
