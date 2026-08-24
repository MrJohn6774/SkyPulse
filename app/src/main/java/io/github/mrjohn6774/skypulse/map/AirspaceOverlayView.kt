package io.github.mrjohn6774.skypulse.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import io.github.mrjohn6774.skypulse.aircraft.AircraftSnapshot
import io.github.mrjohn6774.skypulse.model.GeoPoint
import io.github.mrjohn6774.skypulse.ui.dp
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView

/**
 * Renders airspace and receiver data above osmdroid's raster tiles.
 *
 * Drawing the ADS-B layer here keeps aircraft labels and selection independent from the map's
 * tile renderer, while avoiding thousands of individual marker Views.
 */
internal class AirspaceOverlayView(
    context: Context,
    private val mapView: MapView,
) : View(context) {
    private fun dp(value: Int): Int = context.dp(value)

    private val aircraftIcon = aircraftIconBitmap()
    private val receiverIcon = receiverIconBitmap()
    private val boundaryPath = Path()
    private val firPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2).toFloat()
        strokeJoin = Paint.Join.ROUND
    }
    private val traconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2).toFloat()
        strokeJoin = Paint.Join.ROUND
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(11).toFloat()
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val labelBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelBounds = RectF()

    private var firRings: List<BoundaryRing> = emptyList()
    private var traconRings: List<BoundaryRing> = emptyList()
    private var aircraft: List<AircraftSnapshot> = emptyList()
    private var receiver: GeoPoint? = null
    private var firVisible = false
    private var traconVisible = false
    private var labelsVisible = true
    private var darkTheme = false

    init {
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        applyTheme(false)
    }

    fun setBoundaryRings(
        fir: List<List<GeoPoint>>,
        tracon: List<List<GeoPoint>>,
    ) {
        // Build level-of-detail copies once, off the UI thread. These files contain over 115k
        // coordinate pairs, which is needlessly expensive to reproject on every pan frame.
        val preparedFir = fir.mapNotNull(BoundaryRing::from)
        val preparedTracon = tracon.mapNotNull(BoundaryRing::from)
        post {
            firRings = preparedFir
            traconRings = preparedTracon
            invalidate()
        }
    }

    fun setVisibility(
        fir: Boolean = firVisible,
        tracon: Boolean = traconVisible,
        labels: Boolean = labelsVisible,
    ) {
        firVisible = fir
        traconVisible = tracon
        labelsVisible = labels
        invalidate()
    }

    fun setTheme(dark: Boolean) {
        if (darkTheme == dark) return
        applyTheme(dark)
        invalidate()
    }

    fun updateTraffic(
        aircraft: List<AircraftSnapshot>,
        receiver: GeoPoint?,
    ) {
        this.aircraft = aircraft
        this.receiver = receiver
        invalidate()
    }

    fun findAircraftAt(location: org.osmdroid.util.GeoPoint): AircraftSnapshot? {
        if (width == 0 || height == 0) return null
        val projection = mapView.getProjection()
        val target = projection.toPixels(location, null)
        val hitRadiusSquared = dp(AIRCRAFT_HIT_RADIUS_DP).toFloat().let { it * it }
        return aircraft
            .asSequence()
            .map { value ->
                val point = projection.toPixels(org.osmdroid.util.GeoPoint(value.latitude!!, value.longitude!!), null)
                val dx = (point.x - target.x).toFloat()
                val dy = (point.y - target.y).toFloat()
                value to dx * dx + dy * dy
            }
            .filter { (_, distanceSquared) -> distanceSquared <= hitRadiusSquared }
            .minByOrNull { (_, distanceSquared) -> distanceSquared }
            ?.first
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        val projection = mapView.projection

        if (firVisible) drawRings(canvas, projection, firRings, firPaint)
        if (traconVisible) drawRings(canvas, projection, traconRings, traconPaint)
        receiver?.let { drawReceiver(canvas, projection, it) }
        aircraft.forEach { drawAircraft(canvas, projection, it) }
    }

    private fun drawRings(
        canvas: Canvas,
        projection: org.osmdroid.views.Projection,
        rings: List<BoundaryRing>,
        paint: Paint,
    ) {
        val visibleBounds = mapView.boundingBox
        val zoom = mapView.zoomLevelDouble
        rings.forEach { ring ->
            if (!ring.intersects(visibleBounds)) return@forEach
            boundaryPath.reset()
            ring.pointsFor(zoom).forEachIndexed { index, point ->
                val screen = projection.toPixels(org.osmdroid.util.GeoPoint(point.latitude, point.longitude), null)
                if (index == 0) boundaryPath.moveTo(screen.x.toFloat(), screen.y.toFloat())
                else boundaryPath.lineTo(screen.x.toFloat(), screen.y.toFloat())
            }
            canvas.drawPath(boundaryPath, paint)
        }
    }

    private fun drawReceiver(
        canvas: Canvas,
        projection: org.osmdroid.views.Projection,
        point: GeoPoint,
    ) {
        val screen = projection.toPixels(org.osmdroid.util.GeoPoint(point.latitude, point.longitude), null)
        canvas.drawBitmap(
            receiverIcon,
            screen.x - receiverIcon.width / 2f,
            screen.y - receiverIcon.height / 2f,
            null,
        )
    }

    private fun drawAircraft(
        canvas: Canvas,
        projection: org.osmdroid.views.Projection,
        value: AircraftSnapshot,
    ) {
        val screen = projection.toPixels(org.osmdroid.util.GeoPoint(value.latitude!!, value.longitude!!), null)
        val x = screen.x.toFloat()
        val y = screen.y.toFloat()
        canvas.save()
        canvas.rotate((value.trackDegrees ?: 0).toFloat(), x, y)
        canvas.drawBitmap(aircraftIcon, x - aircraftIcon.width / 2f, y - aircraftIcon.height / 2f, null)
        canvas.restore()

        if (!labelsVisible) return
        val label = buildString {
            append(value.callsign ?: value.icao)
            value.altitudeFeet?.let { append(' ').append(it) }
        }
        val labelX = x - labelPaint.measureText(label) / 2f
        val labelY = y + aircraftIcon.height / 2f + dp(14)
        labelBounds.set(
            labelX - dp(4),
            labelY - labelPaint.textSize - dp(3),
            labelX + labelPaint.measureText(label) + dp(4),
            labelY + dp(4),
        )
        canvas.drawRoundRect(labelBounds, dp(4).toFloat(), dp(4).toFloat(), labelBackgroundPaint)
        canvas.drawText(label, labelX, labelY, labelPaint)
    }

    private fun applyTheme(dark: Boolean) {
        darkTheme = dark
        if (dark) {
            firPaint.color = Color.rgb(125, 211, 252)
            traconPaint.color = Color.rgb(251, 191, 36)
            labelPaint.color = Color.WHITE
            labelBackgroundPaint.color = 0xD8172033.toInt()
        } else {
            firPaint.color = Color.rgb(0, 92, 153)
            traconPaint.color = Color.rgb(176, 74, 0)
            labelPaint.color = Color.rgb(18, 30, 45)
            labelBackgroundPaint.color = 0xEAF7F9FC.toInt()
        }
    }

    private fun aircraftIconBitmap(): Bitmap {
        val size = dp(30)
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
            val path = Path().apply {
                moveTo(size * 0.50f, size * 0.04f)
                lineTo(size * 0.63f, size * 0.42f)
                lineTo(size * 0.92f, size * 0.68f)
                lineTo(size * 0.60f, size * 0.61f)
                lineTo(size * 0.55f, size * 0.87f)
                lineTo(size * 0.70f, size * 0.96f)
                lineTo(size * 0.50f, size * 0.91f)
                lineTo(size * 0.30f, size * 0.96f)
                lineTo(size * 0.45f, size * 0.87f)
                lineTo(size * 0.40f, size * 0.61f)
                lineTo(size * 0.08f, size * 0.68f)
                lineTo(size * 0.37f, size * 0.42f)
                close()
            }
            Canvas(bitmap).apply {
                drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(23, 32, 51)
                    style = Paint.Style.STROKE
                    strokeWidth = dp(4).toFloat()
                    strokeJoin = Paint.Join.ROUND
                })
                drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            }
        }
    }

    private fun receiverIconBitmap(): Bitmap {
        val size = dp(24)
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
            val path = Path().apply {
                moveTo(size * 0.50f, size * 0.08f)
                lineTo(size * 0.92f, size * 0.50f)
                lineTo(size * 0.50f, size * 0.92f)
                lineTo(size * 0.08f, size * 0.50f)
                close()
            }
            Canvas(bitmap).apply {
                drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(23, 32, 51)
                    style = Paint.Style.STROKE
                    strokeWidth = dp(4).toFloat()
                    strokeJoin = Paint.Join.ROUND
                })
                drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(101, 214, 173) })
            }
        }
    }

    /** A boundary ring with an extent and zoom-specific point sets for efficient panning. */
    private class BoundaryRing private constructor(
        private val original: List<GeoPoint>,
        private val minLatitude: Double,
        private val maxLatitude: Double,
        private val minLongitude: Double,
        private val maxLongitude: Double,
    ) {
        private val veryLowDetail = simplify(original, 0.20)
        private val lowDetail = simplify(original, 0.05)
        private val mediumDetail = simplify(original, 0.01)
        private val highDetail = simplify(original, 0.0025)

        fun pointsFor(zoom: Double): List<GeoPoint> = when {
            zoom < 5f -> veryLowDetail
            zoom < 7f -> lowDetail
            zoom < 9f -> mediumDetail
            zoom < 11f -> highDetail
            else -> original
        }

        fun intersects(visible: BoundingBox): Boolean {
            val south = visible.latSouth
            val north = visible.latNorth
            if (maxLatitude < south || minLatitude > north) return false

            val west = visible.lonWest
            val east = visible.lonEast
            // A viewport or a boundary spanning the antimeridian is conservatively retained.
            return west > east || minLongitude > maxLongitude || maxLongitude >= west && minLongitude <= east
        }

        companion object {
            fun from(points: List<GeoPoint>): BoundaryRing? {
                if (points.size < 2) return null
                return BoundaryRing(
                    original = points,
                    minLatitude = points.minOf { it.latitude },
                    maxLatitude = points.maxOf { it.latitude },
                    minLongitude = points.minOf { it.longitude },
                    maxLongitude = points.maxOf { it.longitude },
                )
            }

            private fun simplify(points: List<GeoPoint>, minimumDistanceDegrees: Double): List<GeoPoint> {
                if (points.size <= 2) return points
                val minimumDistanceSquared = minimumDistanceDegrees * minimumDistanceDegrees
                return buildList {
                    var previous = points.first()
                    add(previous)
                    for (index in 1 until points.lastIndex) {
                        val point = points[index]
                        val latitudeDelta = point.latitude - previous.latitude
                        val longitudeDelta = point.longitude - previous.longitude
                        if (latitudeDelta * latitudeDelta + longitudeDelta * longitudeDelta >= minimumDistanceSquared) {
                            add(point)
                            previous = point
                        }
                    }
                    if (last() !== points.last()) add(points.last())
                }
            }
        }
    }

    private companion object {
        const val AIRCRAFT_HIT_RADIUS_DP = 28
    }
}
