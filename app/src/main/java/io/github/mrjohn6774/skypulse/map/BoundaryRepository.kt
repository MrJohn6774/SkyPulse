package io.github.mrjohn6774.skypulse.map

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import io.github.mrjohn6774.skypulse.diagnostics.DiagnosticLog
import io.github.mrjohn6774.skypulse.settings.StationSettings
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream

class BoundaryRepository(
    context: Context,
    private val settings: StationSettings,
) {
    private val storageContext = if (Build.VERSION.SDK_INT >= 24) context.createDeviceProtectedStorageContext() else context
    private val directory = File(storageContext.filesDir, "boundaries").apply { mkdirs() }

    fun sourceUri(name: String): URI {
        val cached = File(directory, name)
        return if (cached.isFile && runCatching { validateCollection(cached.readText()) }.isSuccess) {
            localFileUri(cached.absolutePath)
        } else {
            URI("asset://$name")
        }
    }

    fun maybeUpdate() {
        if (!settings.boundaryAutoUpdate || !updating.compareAndSet(false, true)) return
        val now = System.currentTimeMillis()
        if (now - settings.lastBoundaryCheckMs < CHECK_INTERVAL_MS) {
            updating.set(false)
            return
        }
        settings.lastBoundaryCheckMs = now
        Thread({
            try {
                updateFir()
                updateTracon()
            } finally {
                updating.set(false)
            }
        }, "BoundaryUpdate").start()
    }

    private fun updateFir() {
        runCatching {
            val text = downloadText(FIR_URL, MAX_GEOJSON_BYTES)
            validateCollection(text)
            atomicReplace(File(directory, FIR_FILE), text.toByteArray(Charsets.UTF_8))
            DiagnosticLog.info(TAG, "VATSpy FIR boundaries updated")
        }.onFailure { DiagnosticLog.warn(TAG, "FIR update failed; keeping current snapshot", it) }
    }

    private fun updateTracon() {
        val archive = File(directory, "tracon-update.zip.tmp")
        runCatching {
            downloadToFile(TRACON_ARCHIVE_URL, archive, MAX_ARCHIVE_BYTES)
            val features = JSONArray()
            ZipInputStream(BufferedInputStream(archive.inputStream())).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val normalizedName = entry.name.replace('\\', '/')
                    if (!entry.isDirectory && normalizedName.contains("/Boundaries/") && normalizedName.endsWith(".json")) {
                        if (features.length() >= MAX_FEATURES) error("TRACON archive contains too many features")
                        val text = zip.readBytesLimited(MAX_ENTRY_BYTES).toString(Charsets.UTF_8)
                        val feature = JSONObject(text)
                        validateFeature(feature)
                        features.put(feature)
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            if (features.length() == 0) error("TRACON archive contained no boundary features")
            val collection = JSONObject()
                .put("type", "FeatureCollection")
                .put("name", "SimAware TRACON boundaries")
                .put("features", features)
                .toString()
            atomicReplace(File(directory, TRACON_FILE), collection.toByteArray(Charsets.UTF_8))
            DiagnosticLog.info(TAG, "SimAware TRACON boundaries updated (${features.length()} features)")
        }.onFailure { DiagnosticLog.warn(TAG, "TRACON update failed; keeping current snapshot", it) }
        archive.delete()
    }

    private fun validateCollection(text: String) {
        val document = JSONObject(text)
        require(document.optString("type") == "FeatureCollection") { "Not a GeoJSON FeatureCollection" }
        val features = document.optJSONArray("features") ?: error("FeatureCollection has no features array")
        require(features.length() in 1..MAX_FEATURES) { "Invalid boundary feature count ${features.length()}" }
        for (index in 0 until features.length()) validateFeature(features.getJSONObject(index))
    }

    private fun validateFeature(feature: JSONObject) {
        require(feature.optString("type") == "Feature") { "Boundary item is not a Feature" }
        require(feature.optJSONObject("properties")?.optString("id").orEmpty().isNotBlank()) { "Boundary feature has no id" }
        val geometry = feature.optJSONObject("geometry") ?: error("Boundary feature has no geometry")
        require(geometry.optString("type") in ALLOWED_GEOMETRIES) { "Unsupported boundary geometry" }
        require(geometry.optJSONArray("coordinates")?.length() ?: 0 > 0) { "Boundary geometry has no coordinates" }
    }

    private fun downloadText(url: String, limit: Long): String {
        val connection = open(url)
        return connection.inputStream.use { input -> input.readBytesLimited(limit).toString(Charsets.UTF_8) }
            .also { connection.disconnect() }
    }

    private fun downloadToFile(url: String, target: File, limit: Long) {
        val connection = open(url)
        connection.inputStream.use { input ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > limit) error("Boundary download exceeds size limit")
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
        }
        connection.disconnect()
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "SkyPulse/${io.github.mrjohn6774.skypulse.BuildConfig.VERSION_NAME}")
        connect()
        if (responseCode !in 200..299) error("HTTP $responseCode from $url")
    }

    private fun java.io.InputStream.readBytesLimited(limit: Long): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            if (total > limit) error("Boundary data exceeds size limit")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun atomicReplace(target: File, bytes: ByteArray) {
        val temporary = File(target.parentFile, "${target.name}.tmp")
        FileOutputStream(temporary).use { output -> output.write(bytes); output.fd.sync() }
        val backup = File(target.parentFile, "${target.name}.bak")
        if (backup.exists()) backup.delete()
        if (target.exists() && !target.renameTo(backup)) error("Could not stage previous boundary file")
        if (!temporary.renameTo(target)) {
            backup.renameTo(target)
            error("Could not activate downloaded boundary file")
        }
        backup.delete()
    }

    companion object {
        const val FIR_FILE = "fir_boundaries.geojson"
        const val TRACON_FILE = "tracon_boundaries.geojson"
        private const val TAG = "ADSB.DataUpdate"
        private const val CHECK_INTERVAL_MS = 7L * 24 * 60 * 60 * 1_000
        private const val FIR_URL = "https://raw.githubusercontent.com/vatsimnetwork/vatspy-data-project/master/Boundaries.geojson"
        private const val TRACON_ARCHIVE_URL = "https://github.com/vatsimnetwork/simaware-tracon-project/archive/refs/heads/main.zip"
        private const val MAX_GEOJSON_BYTES = 12L * 1024 * 1024
        private const val MAX_ARCHIVE_BYTES = 30L * 1024 * 1024
        private const val MAX_ENTRY_BYTES = 2L * 1024 * 1024
        private const val MAX_FEATURES = 5_000
        private val ALLOWED_GEOMETRIES = setOf("Polygon", "MultiPolygon", "LineString", "MultiLineString")
        private val updating = AtomicBoolean(false)

        internal fun localFileUri(absolutePath: String): URI {
            require(absolutePath.startsWith('/')) { "Boundary path must be absolute" }
            return URI("file", "", absolutePath, null)
        }
    }
}
