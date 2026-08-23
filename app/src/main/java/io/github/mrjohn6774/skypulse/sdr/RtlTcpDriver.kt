package io.github.mrjohn6774.skypulse.sdr

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import io.github.mrjohn6774.skypulse.diagnostics.DiagnosticLog
import io.github.mrjohn6774.skypulse.settings.StationSettings
import java.util.concurrent.atomic.AtomicBoolean

object RtlTcpDriver {
    private const val TAG = "ADSB.Driver"
    private const val PREFERRED_PACKAGE = "marto.rtl_tcp_andro"
    private const val DRIVER_START_SINGLE_FLIGHT_MS = 45_000L
    private val launchFence = DriverLaunchFence(DRIVER_START_SINGLE_FLIGHT_MS)
    private val teardownPending = AtomicBoolean(false)

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
        if (!reserveLaunch()) {
            DiagnosticLog.info(TAG, "Driver launch suppressed; a start is already in flight")
            return false
        }
        val intent = createIntent(context, settings) ?: run {
            launchFence.releaseAfterConfirmedTeardown()
            DiagnosticLog.warn(TAG, "No iqsrc-compatible SDR driver is installed")
            return false
        }
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
            context.startActivity(intent)
            DiagnosticLog.info(TAG, "Requested driver start: ${settings.driverArguments()}")
            true
        } catch (error: Exception) {
            launchFence.releaseAfterConfirmedTeardown()
            DiagnosticLog.warn(TAG, "Android blocked background driver activity start", error)
            false
        }
    }

    fun reserveForegroundLaunch(): Boolean = reserveLaunch()

    internal fun markTeardownPending() {
        teardownPending.set(true)
    }

    internal fun confirmTeardownIfPending(): Boolean {
        if (!teardownPending.compareAndSet(true, false)) return false
        launchFence.releaseAfterConfirmedTeardown()
        return true
    }

    private fun reserveLaunch(): Boolean = launchFence.tryAcquire(SystemClock.elapsedRealtime())
}
