package io.github.mrjohn6774.skypulse.settings

import android.content.Context
import android.os.Build
import io.github.mrjohn6774.skypulse.model.GeoPoint

class StationSettings(context: Context) {
    private val storageContext = if (Build.VERSION.SDK_INT >= 24) {
        context.createDeviceProtectedStorageContext()
    } else {
        context
    }
    private val prefs = storageContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var receiverLatitude: Double
        get() = prefs.getString(KEY_LATITUDE, null)?.toDoubleOrNull() ?: 0.0
        set(value) = prefs.edit().putString(KEY_LATITUDE, value.toString()).apply()

    var receiverLongitude: Double
        get() = prefs.getString(KEY_LONGITUDE, null)?.toDoubleOrNull() ?: 0.0
        set(value) = prefs.edit().putString(KEY_LONGITUDE, value.toString()).apply()

    var receiverAltitudeMetres: Double?
        get() = prefs.getString(KEY_ALTITUDE, null)?.toDoubleOrNull()
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_ALTITUDE) else putString(KEY_ALTITUDE, value.toString())
        }.apply()

    var rtlTcpPort: Int
        get() = prefs.getInt(KEY_RTL_PORT, DEFAULT_RTL_PORT)
        set(value) = prefs.edit().putInt(KEY_RTL_PORT, value.coerceIn(1024, 65535)).apply()

    var sampleRate: Long
        get() = prefs.getLong(KEY_SAMPLE_RATE, DEFAULT_SAMPLE_RATE)
        set(value) = prefs.edit().putLong(KEY_SAMPLE_RATE, value).apply()

    var frequency: Long
        get() = prefs.getLong(KEY_FREQUENCY, DEFAULT_FREQUENCY)
        set(value) = prefs.edit().putLong(KEY_FREQUENCY, value).apply()

    var automaticGain: Boolean
        get() = prefs.getBoolean(KEY_AUTO_GAIN, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_GAIN, value).apply()

    var gainTenthsDb: Int
        get() = prefs.getInt(KEY_GAIN, 240)
        set(value) = prefs.edit().putInt(KEY_GAIN, value.coerceIn(0, 500)).apply()

    var beastPort: Int
        get() = prefs.getInt(KEY_BEAST_PORT, DEFAULT_BEAST_PORT)
        set(value) = prefs.edit().putInt(KEY_BEAST_PORT, value.coerceIn(1024, 65535)).apply()

    var beastTcpExportEnabled: Boolean
        get() = prefs.getBoolean(KEY_BEAST_TCP_EXPORT, false)
        set(value) = prefs.edit().putBoolean(KEY_BEAST_TCP_EXPORT, value).apply()

    var startAtBoot: Boolean
        get() = prefs.getBoolean(KEY_START_BOOT, true)
        set(value) = prefs.edit().putBoolean(KEY_START_BOOT, value).apply()

    var receiverEnabled: Boolean
        get() = prefs.getBoolean(KEY_RECEIVER_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_RECEIVER_ENABLED, value).apply()

    var firEnabled: Boolean
        get() = prefs.getBoolean(KEY_FIR, false)
        set(value) = prefs.edit().putBoolean(KEY_FIR, value).apply()

    var traconEnabled: Boolean
        get() = prefs.getBoolean(KEY_TRACON, false)
        set(value) = prefs.edit().putBoolean(KEY_TRACON, value).apply()

    var labelsEnabled: Boolean
        get() = prefs.getBoolean(KEY_LABELS, true)
        set(value) = prefs.edit().putBoolean(KEY_LABELS, value).apply()

    var mapStyleUrl: String
        get() = prefs.getString(KEY_STYLE, DEFAULT_STYLE_URL) ?: DEFAULT_STYLE_URL
        set(value) = prefs.edit().putString(KEY_STYLE, value.trim()).apply()

    var boundaryAutoUpdate: Boolean
        get() = prefs.getBoolean(KEY_BOUNDARY_UPDATE, false)
        set(value) = prefs.edit().putBoolean(KEY_BOUNDARY_UPDATE, value).apply()

    var lastBoundaryCheckMs: Long
        get() = prefs.getLong(KEY_LAST_BOUNDARY_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_BOUNDARY_CHECK, value).apply()

    fun receiverPoint(): GeoPoint? {
        val latitude = receiverLatitude
        val longitude = receiverLongitude
        return if (latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
            !(latitude == 0.0 && longitude == 0.0)
        ) GeoPoint(latitude, longitude) else null
    }

    fun driverArguments(): String {
        val gain = if (automaticGain) 0 else gainTenthsDb
        return "-a 127.0.0.1 -p $rtlTcpPort -f $frequency -s $sampleRate -g $gain"
    }

    companion object {
        const val DEFAULT_RTL_PORT = 1234
        const val DEFAULT_BEAST_PORT = 30005
        const val DEFAULT_SAMPLE_RATE = 2_400_000L
        const val DEFAULT_FREQUENCY = 1_090_000_000L
        const val DEFAULT_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

        private const val FILE_NAME = "station_settings"
        private const val KEY_LATITUDE = "receiver_latitude"
        private const val KEY_LONGITUDE = "receiver_longitude"
        private const val KEY_ALTITUDE = "receiver_altitude"
        private const val KEY_RTL_PORT = "rtl_tcp_port"
        private const val KEY_SAMPLE_RATE = "sample_rate"
        private const val KEY_FREQUENCY = "frequency"
        private const val KEY_AUTO_GAIN = "auto_gain"
        private const val KEY_GAIN = "gain_tenths_db"
        private const val KEY_BEAST_PORT = "beast_port"
        private const val KEY_BEAST_TCP_EXPORT = "beast_tcp_export_enabled"
        private const val KEY_START_BOOT = "start_at_boot"
        private const val KEY_RECEIVER_ENABLED = "receiver_enabled"
        private const val KEY_FIR = "fir_enabled"
        private const val KEY_TRACON = "tracon_enabled"
        private const val KEY_LABELS = "labels_enabled"
        private const val KEY_STYLE = "map_style"
        private const val KEY_BOUNDARY_UPDATE = "boundary_auto_update"
        private const val KEY_LAST_BOUNDARY_CHECK = "last_boundary_check"
    }
}
