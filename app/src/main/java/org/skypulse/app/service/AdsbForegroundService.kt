package org.skypulse.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.flightaware.android.flightfeeder.analyzers.AnalyzerBridge
import com.flightaware.android.flightfeeder.analyzers.dump1090.Dump1090
import org.skypulse.app.R
import org.skypulse.app.aircraft.AircraftRepository
import org.skypulse.app.decoder.DecoderBridge
import org.skypulse.app.diagnostics.DiagnosticLog
import org.skypulse.app.export.BeastTcpServer
import org.skypulse.app.health.HealthHttpServer
import org.skypulse.app.health.HealthState
import org.skypulse.app.map.BoundaryRepository
import org.skypulse.app.sdr.RtlTcpController
import org.skypulse.app.settings.StationSettings
import org.skypulse.app.ui.MainActivity

class AdsbForegroundService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var settings: StationSettings
    private var wakeLock: PowerManager.WakeLock? = null
    private var decoder: Dump1090? = null
    private var rtlController: RtlTcpController? = null
    private var beastServer: BeastTcpServer? = null
    private var healthServer: HealthHttpServer? = null

    private val monitor = object : Runnable {
        override fun run() {
            if (HealthState.serviceRunning.get()) {
                ensureWakeLock()
                if (!Dump1090.isRunning()) {
                    DiagnosticLog.warn(TAG_SERVICE, "Decoder worker stopped; restarting pipeline")
                    HealthState.recoveries.incrementAndGet()
                    decoder?.stopEbc()
                    decoder?.startEbc()
                }
                HealthState.aircraftActive.set(AircraftRepository.mappable().size)
                updateNotification()
                mainHandler.postDelayed(this, MONITOR_INTERVAL_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings = StationSettings(this)
        createNotificationChannel()
        enterForeground()
        acquireWakeLock()
        startCore()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                settings.receiverEnabled = false
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RESTART -> restartCore()
            ACTION_USB_CHANGED -> rtlController?.onUsbChanged(
                intent.getStringExtra(EXTRA_USB_EVENT).orEmpty(),
            )
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopCore()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startCore() {
        HealthState.markStarted()
        beastServer = BeastTcpServer(settings.beastPort).also { it.start() }
        healthServer = HealthHttpServer().also { it.start() }
        val bridge = DecoderBridge(settings, requireNotNull(beastServer))
        AnalyzerBridge.registerAll(bridge)
        AnalyzerBridge.setStallTimeoutSec(60)
        decoder = Dump1090().also { it.startEbc() }
        rtlController = RtlTcpController(this, settings).also { it.start() }
        BoundaryRepository(this, settings).maybeUpdate()
        DiagnosticLog.info(TAG_SERVICE, "Receiver core started")
        mainHandler.removeCallbacks(monitor)
        mainHandler.postDelayed(monitor, MONITOR_INTERVAL_MS)
    }

    private fun stopCore() {
        HealthState.serviceRunning.set(false)
        mainHandler.removeCallbacks(monitor)
        rtlController?.stop()
        rtlController = null
        decoder?.stopEbc()
        decoder = null
        beastServer?.stop()
        beastServer = null
        healthServer?.stop()
        healthServer = null
        HealthState.driverState.set("stopped")
        HealthState.rtlTcpConnected.set(false)
        HealthState.beastClients.set(0)
        DiagnosticLog.info(TAG_SERVICE, "Receiver core stopped")
    }

    private fun restartCore() {
        DiagnosticLog.info(TAG_SERVICE, "Manual receiver restart")
        stopCore()
        startCore()
    }

    private fun acquireWakeLock() {
        val manager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:adsb-receiver").apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun ensureWakeLock() {
        wakeLock?.let { if (!it.isHeld) it.acquire(WAKE_LOCK_TIMEOUT_MS) }
    }

    private fun enterForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val snapshot = HealthState.snapshot()
        val openIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, AdsbForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val detail = "${snapshot.driver} · ${snapshot.messagesPerSec.format1()} msg/s · ${snapshot.aircraftActive} aircraft"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notification_running))
            .setContentText(detail)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.notification_channel_description) }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    private fun Double.format1(): String = String.format(java.util.Locale.US, "%.1f", this)

    companion object {
        const val ACTION_STOP = "org.skypulse.app.action.STOP"
        const val ACTION_RESTART = "org.skypulse.app.action.RESTART"
        const val ACTION_USB_CHANGED = "org.skypulse.app.action.USB_CHANGED"
        const val EXTRA_USB_EVENT = "usb_event"
        private const val TAG_SERVICE = "ADSB.Service"
        private const val CHANNEL_ID = "adsb_receiver"
        private const val NOTIFICATION_ID = 1090
        private const val MONITOR_INTERVAL_MS = 5_000L
        private const val WAKE_LOCK_TIMEOUT_MS = 10L * 60 * 1_000

        fun start(context: Context, action: String? = null) {
            val intent = Intent(context, AdsbForegroundService::class.java).apply { this.action = action }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AdsbForegroundService::class.java).setAction(ACTION_STOP)
            ContextCompat.startForegroundService(context, intent)
        }

        fun notifyUsbChanged(context: Context, event: String) {
            val intent = Intent(context, AdsbForegroundService::class.java)
                .setAction(ACTION_USB_CHANGED)
                .putExtra(EXTRA_USB_EVENT, event)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
