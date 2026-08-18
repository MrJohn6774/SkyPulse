package org.skypulse.app.map

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.skypulse.app.aircraft.AircraftRepository
import org.skypulse.app.aircraft.AircraftSnapshot
import org.skypulse.app.settings.StationSettings
import org.skypulse.app.ui.dp
import java.net.URI
import java.util.Locale

class MapActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var settings: StationSettings
    private lateinit var boundaries: BoundaryRepository
    private lateinit var mapView: MapView
    private var map: MapLibreMap? = null
    private var selectedIcao: String? = null

    private val refresh = object : Runnable {
        override fun run() {
            refreshAircraft()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = StationSettings(this)
        boundaries = BoundaryRepository(this, settings)
        MapLibre.getInstance(this)
        mapView = MapView(this)
        setContentView(buildContent())
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { value ->
            map = value
            value.setStyle(Style.Builder().fromUri(settings.mapStyleUrl)) { style ->
                configureSourcesAndLayers(style)
                recenter()
            }
            value.addOnMapClickListener { point -> onMapClick(value, point) }
        }
    }

    private fun buildContent(): View {
        val root = FrameLayout(this)
        root.addView(mapView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(0xCC172033.toInt())
        }
        controls.addView(MaterialButton(this).apply {
            text = "Receiver"
            setOnClickListener { recenter() }
        })
        controls.addView(CheckBox(this).apply {
            text = "FIR"
            setTextColor(Color.WHITE)
            isChecked = settings.firEnabled
            setOnCheckedChangeListener { _, checked ->
                settings.firEnabled = checked
                setLayerVisible(FIR_LAYER, checked)
            }
        })
        controls.addView(CheckBox(this).apply {
            text = "TRACON"
            setTextColor(Color.WHITE)
            isChecked = settings.traconEnabled
            setOnCheckedChangeListener { _, checked ->
                settings.traconEnabled = checked
                setLayerVisible(TRACON_LAYER, checked)
            }
        })
        controls.addView(CheckBox(this).apply {
            text = "Labels"
            setTextColor(Color.WHITE)
            isChecked = settings.labelsEnabled
            setOnCheckedChangeListener { _, checked ->
                settings.labelsEnabled = checked
                setLayerVisible(AIRCRAFT_LABEL_LAYER, checked)
            }
        })
        root.addView(controls, FrameLayout.LayoutParams(dp(150), FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = dp(12)
            marginEnd = dp(12)
        })
        return root
    }

    private fun configureSourcesAndLayers(style: Style) {
        style.addSource(GeoJsonSource(FIR_SOURCE, boundaries.sourceUri(BoundaryRepository.FIR_FILE)))
        style.addLayer(LineLayer(FIR_LAYER, FIR_SOURCE).withProperties(
            PropertyFactory.lineColor("#4FA3FF"),
            PropertyFactory.lineWidth(1.5f),
            PropertyFactory.lineOpacity(0.7f),
            PropertyFactory.visibility(if (settings.firEnabled) Property.VISIBLE else Property.NONE),
        ))
        style.addSource(GeoJsonSource(TRACON_SOURCE, boundaries.sourceUri(BoundaryRepository.TRACON_FILE)))
        style.addLayer(LineLayer(TRACON_LAYER, TRACON_SOURCE).withProperties(
            PropertyFactory.lineColor("#F8C24E"),
            PropertyFactory.lineWidth(1.2f),
            PropertyFactory.lineOpacity(0.75f),
            PropertyFactory.visibility(if (settings.traconEnabled) Property.VISIBLE else Property.NONE),
        ))

        style.addSource(GeoJsonSource(RECEIVER_SOURCE, receiverGeoJson()))
        style.addLayer(SymbolLayer(RECEIVER_LAYER, RECEIVER_SOURCE).withProperties(
            PropertyFactory.textField("◆"),
            PropertyFactory.textSize(22f),
            PropertyFactory.textColor("#65D6AD"),
            PropertyFactory.textHaloColor("#172033"),
            PropertyFactory.textHaloWidth(2f),
        ))

        style.addSource(GeoJsonSource(AIRCRAFT_SOURCE, emptyFeatureCollection()))
        style.addLayer(SymbolLayer(AIRCRAFT_LAYER, AIRCRAFT_SOURCE).withProperties(
            PropertyFactory.textField("▲"),
            PropertyFactory.textSize(18f),
            PropertyFactory.textColor("#FFFFFF"),
            PropertyFactory.textHaloColor("#172033"),
            PropertyFactory.textHaloWidth(2f),
            PropertyFactory.textRotate(Expression.get("track")),
            PropertyFactory.textAllowOverlap(true),
        ))
        style.addLayer(SymbolLayer(AIRCRAFT_LABEL_LAYER, AIRCRAFT_SOURCE).withProperties(
            PropertyFactory.textField(Expression.get("label")),
            PropertyFactory.textSize(11f),
            PropertyFactory.textColor("#FFFFFF"),
            PropertyFactory.textHaloColor("#172033"),
            PropertyFactory.textHaloWidth(1.5f),
            PropertyFactory.textOffset(arrayOf(0f, 1.8f)),
            PropertyFactory.textAllowOverlap(false),
            PropertyFactory.visibility(if (settings.labelsEnabled) Property.VISIBLE else Property.NONE),
        ))
    }

    private fun refreshAircraft() {
        val style = map?.style ?: return
        val active = AircraftRepository.active()
        style.getSourceAs<GeoJsonSource>(AIRCRAFT_SOURCE)?.setGeoJson(aircraftGeoJson(active))
        selectedIcao?.let { icao ->
            active.firstOrNull { it.icao == icao && it.latitude != null && it.longitude != null }?.let {
                map?.animateCamera(CameraUpdateFactory.newLatLng(LatLng(it.latitude!!, it.longitude!!)))
            }
        }
    }

    private fun onMapClick(map: MapLibreMap, point: LatLng): Boolean {
        val screen = map.projection.toScreenLocation(point)
        val feature = map.queryRenderedFeatures(screen, AIRCRAFT_LAYER).firstOrNull() ?: return false
        val icao = feature.getStringProperty("icao") ?: return false
        selectedIcao = icao
        AircraftRepository.active().firstOrNull { it.icao == icao }?.let(::showAircraft)
        return true
    }

    private fun showAircraft(value: AircraftSnapshot) {
        AlertDialog.Builder(this)
            .setTitle(value.callsign ?: value.icao)
            .setMessage(buildString {
                append("ICAO: ").append(value.icao).append('\n')
                append("Altitude: ").append(value.altitudeFeet ?: "—").append(" ft\n")
                append("Speed: ").append(value.groundSpeedKnots ?: "—").append(" kt\n")
                append("Track: ").append(value.trackDegrees ?: "—").append("°\n")
                append("Vertical rate: ").append(value.verticalRateFeetPerMinute ?: "—").append(" ft/min\n")
                append("Squawk: ").append(value.squawk ?: "—").append('\n')
                append("Signal: ").append(String.format(Locale.US, "%.1f dB", value.signalDb)).append('\n')
                append("Age: ").append((System.currentTimeMillis() - value.lastSeenEpochMs).coerceAtLeast(0) / 1_000).append(" s")
            })
            .setPositiveButton("Follow") { _, _ -> selectedIcao = value.icao }
            .setNegativeButton("Close") { _, _ -> selectedIcao = null }
            .show()
    }

    private fun recenter() {
        selectedIcao = null
        settings.receiverPoint()?.let {
            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 8.0))
        }
    }

    private fun setLayerVisible(id: String, visible: Boolean) {
        map?.style?.getLayer(id)?.setProperties(PropertyFactory.visibility(if (visible) Property.VISIBLE else Property.NONE))
    }

    private fun receiverGeoJson(): String {
        val point = settings.receiverPoint() ?: return emptyFeatureCollection()
        return JSONObject().apply {
            put("type", "FeatureCollection")
            put("features", JSONArray().put(JSONObject().apply {
                put("type", "Feature")
                put("properties", JSONObject().put("name", "Receiver"))
                put("geometry", JSONObject().put("type", "Point").put("coordinates", JSONArray().put(point.longitude).put(point.latitude)))
            }))
        }.toString()
    }

    private fun aircraftGeoJson(values: List<AircraftSnapshot>): String {
        val features = JSONArray()
        values.filter { it.latitude != null && it.longitude != null }.forEach { value ->
            features.put(JSONObject().apply {
                put("type", "Feature")
                put("properties", JSONObject().apply {
                    put("icao", value.icao)
                    put("track", value.trackDegrees ?: 0)
                    put("label", "${value.callsign ?: value.icao} ${value.altitudeFeet ?: ""}")
                })
                put("geometry", JSONObject().put("type", "Point").put("coordinates", JSONArray().put(value.longitude).put(value.latitude)))
            })
        }
        return JSONObject().put("type", "FeatureCollection").put("features", features).toString()
    }

    private fun emptyFeatureCollection() = "{\"type\":\"FeatureCollection\",\"features\":[]}"

    override fun onStart() { super.onStart(); mapView.onStart(); handler.post(refresh) }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { handler.removeCallbacks(refresh); mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { mapView.onDestroy(); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }

    companion object {
        private const val FIR_SOURCE = "fir-source"
        private const val FIR_LAYER = "fir-layer"
        private const val TRACON_SOURCE = "tracon-source"
        private const val TRACON_LAYER = "tracon-layer"
        private const val RECEIVER_SOURCE = "receiver-source"
        private const val RECEIVER_LAYER = "receiver-layer"
        private const val AIRCRAFT_SOURCE = "aircraft-source"
        private const val AIRCRAFT_LAYER = "aircraft-layer"
        private const val AIRCRAFT_LABEL_LAYER = "aircraft-label-layer"
    }
}
