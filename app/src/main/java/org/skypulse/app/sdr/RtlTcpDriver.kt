package org.skypulse.app.sdr

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import org.skypulse.app.diagnostics.DiagnosticLog
import org.skypulse.app.settings.StationSettings

object RtlTcpDriver {
    private const val TAG = "ADSB.Driver"
    private const val PREFERRED_PACKAGE = "marto.rtl_tcp_andro"

    fun createIntent(context: Context, settings: StationSettings): Intent? {
        val base = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("iqsrc://${settings.driverArguments()}")
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        val activities = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.queryIntentActivities(
                base,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(base, PackageManager.MATCH_DEFAULT_ONLY)
        }
        val selected = activities.minByOrNull {
            if (it.activityInfo.packageName == PREFERRED_PACKAGE) 0 else 1
        } ?: return null
        return base.apply {
            component = ComponentName(selected.activityInfo.packageName, selected.activityInfo.name)
        }
    }

    fun requestStartFromBackground(context: Context, settings: StationSettings): Boolean {
        val intent = createIntent(context, settings) ?: run {
            DiagnosticLog.warn(TAG, "No iqsrc-compatible SDR driver is installed")
            return false
        }
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
            context.startActivity(intent)
            DiagnosticLog.info(TAG, "Requested driver start: ${settings.driverArguments()}")
            true
        } catch (error: Exception) {
            DiagnosticLog.warn(TAG, "Android blocked background driver activity start", error)
            false
        }
    }
}
