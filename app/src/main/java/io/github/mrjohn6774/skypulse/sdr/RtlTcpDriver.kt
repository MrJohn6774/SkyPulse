package io.github.mrjohn6774.skypulse.sdr

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.annotation.RequiresApi
import io.github.mrjohn6774.skypulse.diagnostics.DiagnosticLog
import io.github.mrjohn6774.skypulse.settings.StationSettings
import java.util.concurrent.atomic.AtomicBoolean

enum class DriverLaunchResult {
    LAUNCHED,
    SUPPRESSED,
    NEEDS_USER,
    UNAVAILABLE,
}

object RtlTcpDriver {
    private const val TAG = "ADSB.Driver"
    private const val PREFERRED_PACKAGE = "marto.rtl_tcp_andro"
    private const val DRIVER_START_SINGLE_FLIGHT_MS = 45_000L
    private const val OVERLAY_SETTLE_MS = 300L
    private const val OVERLAY_HOLD_MS = 5_000L
    private const val RESULT_DETAIL_EXTRA = "detailed_exception_message"
    private val launchFence = DriverLaunchFence(DRIVER_START_SINGLE_FLIGHT_MS)
    private val teardownPending = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

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

    /** True when the driver can be restarted without anyone touching the phone. */
    fun canStartUnattended(context: Context): Boolean =
        DriverLaunchPolicy.route(Build.VERSION.SDK_INT, appVisible = false, canDrawOverlays(context)) !=
            DriverLaunchRoute.NOTIFY

    fun requestStartFromBackground(context: Context, settings: StationSettings): DriverLaunchResult {
        val intent = createIntent(context, settings) ?: run {
            DiagnosticLog.warn(TAG, "No iqsrc-compatible SDR driver is installed")
            return DriverLaunchResult.UNAVAILABLE
        }
        // Checked before routing so an in-flight start never re-posts the tap-to-start prompt.
        if (launchFence.remainingMs(SystemClock.elapsedRealtime()) > 0) {
            DiagnosticLog.info(TAG, "Driver launch suppressed; a start is already in flight")
            return DriverLaunchResult.SUPPRESSED
        }
        val route = DriverLaunchPolicy.route(Build.VERSION.SDK_INT, isAppVisible(), canDrawOverlays(context))
        if (route == DriverLaunchRoute.NOTIFY) {
            DriverStartNotification.show(context)
            return DriverLaunchResult.NEEDS_USER
        }
        if (!reserveLaunch()) {
            DiagnosticLog.info(TAG, "Driver launch suppressed; a start is already in flight")
            return DriverLaunchResult.SUPPRESSED
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
        // The OVERLAY route is only chosen on Android 10+, where TYPE_APPLICATION_OVERLAY exists.
        if (route == DriverLaunchRoute.DIRECT || Build.VERSION.SDK_INT < 26) {
            return if (startDriver(context, intent, settings)) DriverLaunchResult.LAUNCHED else DriverLaunchResult.UNAVAILABLE
        }
        startDriverBehindOverlay(context.applicationContext, intent, settings)
        return DriverLaunchResult.LAUNCHED
    }

    fun reserveForegroundLaunch(): Boolean = reserveLaunch()

    /** Explains a non-OK result from the driver activity, or null when it opened the SDR. */
    fun describeFailure(resultCode: Int, data: Intent?): String? =
        if (resultCode == android.app.Activity.RESULT_OK) null
        else data?.getStringExtra(RESULT_DETAIL_EXTRA) ?: "Driver did not open the SDR"

    internal fun markTeardownPending() {
        teardownPending.set(true)
    }

    internal fun confirmTeardownIfPending(): Boolean {
        if (!teardownPending.compareAndSet(true, false)) return false
        launchFence.releaseAfterConfirmedTeardown()
        return true
    }

    private fun startDriver(context: Context, intent: Intent, settings: StationSettings): Boolean = try {
        context.startActivity(intent)
        DiagnosticLog.info(TAG, "Requested driver start: ${settings.driverArguments()}")
        true
    } catch (error: Exception) {
        launchFence.releaseAfterConfirmedTeardown()
        DiagnosticLog.warn(TAG, "Android rejected the driver activity start", error)
        false
    }

    /**
     * Android 15+ lets an overlay-permitted app start activities from the background only while
     * one of its overlay windows is visible, so hold a 1x1 window across the start.
     */
    @RequiresApi(26)
    private fun startDriverBehindOverlay(context: Context, intent: Intent, settings: StationSettings) {
        mainHandler.post {
            val windowManager = context.getSystemService(WindowManager::class.java)
            val overlay = View(context)
            val shown = runCatching {
                windowManager.addView(
                    overlay,
                    WindowManager.LayoutParams(
                        1,
                        1,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT,
                    ),
                )
            }.onFailure { DiagnosticLog.warn(TAG, "Could not show launch overlay", it) }.isSuccess
            mainHandler.postDelayed({
                DiagnosticLog.info(TAG, "Starting driver behind overlay (overlay shown: $shown)")
                startDriver(context, intent, settings)
                mainHandler.postDelayed({
                    if (shown) runCatching { windowManager.removeView(overlay) }
                }, OVERLAY_HOLD_MS)
            }, OVERLAY_SETTLE_MS)
        }
    }

    private fun isAppVisible(): Boolean {
        val state = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(state)
        return state.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    private fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    private fun reserveLaunch(): Boolean = launchFence.tryAcquire(SystemClock.elapsedRealtime())
}
