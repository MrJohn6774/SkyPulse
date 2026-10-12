package io.github.mrjohn6774.skypulse.sdr

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import io.github.mrjohn6774.skypulse.R
import io.github.mrjohn6774.skypulse.diagnostics.DiagnosticLog
import io.github.mrjohn6774.skypulse.ui.DriverStartActivity
import java.util.concurrent.atomic.AtomicBoolean

/** Asks the user to start the SDR driver when Android blocks starting it from the background. */
object DriverStartNotification {
    private const val TAG = "ADSB.Driver"
    private const val CHANNEL_ID = "sdr_driver_start"
    private const val NOTIFICATION_ID = 1091
    private val shown = AtomicBoolean(false)

    fun show(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.driver_start_channel_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { description = context.getString(R.string.driver_start_channel_description) },
            )
        }
        val tapIntent = PendingIntent.getActivity(
            context,
            3,
            Intent(context, DriverStartActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.driver_start_title))
            .setContentText(context.getString(R.string.driver_start_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.driver_start_text)))
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
            if (!shown.getAndSet(true)) {
                DiagnosticLog.warn(TAG, "Android blocks background driver starts; waiting for the user to tap the notification")
            }
        } catch (error: SecurityException) {
            DiagnosticLog.warn(TAG, "Cannot post the driver start notification", error)
        }
    }

    fun cancel(context: Context) {
        if (!shown.getAndSet(false)) return
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }
}
