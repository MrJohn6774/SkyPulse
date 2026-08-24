package io.github.mrjohn6774.skypulse.map

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import io.github.mrjohn6774.skypulse.aircraft.AircraftRepository
import io.github.mrjohn6774.skypulse.aircraft.AircraftSnapshot
import io.github.mrjohn6774.skypulse.settings.StationSettings
import io.github.mrjohn6774.skypulse.ui.dp
import org.osmdroid.config.Configuration as OsmdroidConfiguration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import java.io.File
import java.util.Locale

class MapActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var settings: StationSettings
    private lateinit var boundaries: BoundaryRepository
    private lateinit var mapView: MapView
    private lateinit var airspaceOverlay: AirspaceOverlayView
    private var selectedIcao: String? = null
    private var initialViewportSet = false
    private lateinit var aircraftTable: AircraftTableView
    private lateinit var aircraftSheetTitle: TextView
    private lateinit var aircraftSheetToggle: TextView
    private lateinit var aircraftSheetBehavior: BottomSheetBehavior<LinearLayout>
    private var lastTableRefreshMs = 0L

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
        configureOsmdroid()
        mapView = MapView(this).apply {
            setTileSource(cartoTileSource(isDarkTheme()))
            setMultiTouchControls(true)
            // CARTO supplies 256px raster tiles; keeping native tile pixels avoids extra bitmap
            // scaling and keeps the on-screen cache footprint small.
            setTilesScaledToDpi(false)
            overlays.add(CopyrightOverlay(this@MapActivity))
            overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(point: GeoPoint): Boolean {
                    onMapClick(point)
                    return false
                }

                override fun longPressHelper(point: GeoPoint): Boolean = false
            }))
            addMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean {
                    airspaceOverlay.invalidate()
                    return false
                }

                override fun onZoom(event: ZoomEvent?): Boolean {
                    airspaceOverlay.invalidate()
                    return false
                }
            })
        }
        setContentView(buildContent())
        loadBoundaries()
        mapView.post {
            recenter()
        }
    }

    private fun buildContent(): View {
        val root = CoordinatorLayout(this)
        root.addView(
            mapView,
            CoordinatorLayout.LayoutParams(
                CoordinatorLayout.LayoutParams.MATCH_PARENT,
                CoordinatorLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        airspaceOverlay = AirspaceOverlayView(this, mapView).apply {
            setVisibility(
                fir = settings.firEnabled,
                tracon = settings.traconEnabled,
                labels = settings.labelsEnabled,
            )
            setTheme(isDarkTheme())
        }
        root.addView(
            airspaceOverlay,
            CoordinatorLayout.LayoutParams(
                CoordinatorLayout.LayoutParams.MATCH_PARENT,
                CoordinatorLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        val mapMenu = buildMapMenu()
        val mapMenuParams = CoordinatorLayout.LayoutParams(dp(160), CoordinatorLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.START
            topMargin = dp(12)
            marginStart = dp(12)
        }
        root.addView(mapMenu, mapMenuParams)

        val sheet = buildAircraftSheet()
        aircraftSheetBehavior = BottomSheetBehavior<LinearLayout>().apply {
            peekHeight = dp(TABLE_PEEK_HEIGHT_DP)
            state = BottomSheetBehavior.STATE_COLLAPSED
            isHideable = false
            isFitToContents = true
            isGestureInsetBottomIgnored = true
            addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    aircraftSheetToggle.text = if (newState == BottomSheetBehavior.STATE_COLLAPSED) "▲" else "▼"
                    if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                        refreshAircraftTable(AircraftRepository.active(), force = true)
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) = Unit
            })
        }
        val sheetParams = CoordinatorLayout.LayoutParams(
            CoordinatorLayout.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.72f).toInt().coerceAtLeast(dp(320)),
        ).apply {
            behavior = aircraftSheetBehavior
        }
        root.addView(sheet, sheetParams)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            mapMenuParams.topMargin = systemBars.top + dp(8)
            mapMenu.layoutParams = mapMenuParams
            sheet.setPadding(0, 0, 0, systemBars.bottom)
            aircraftSheetBehavior.peekHeight = dp(TABLE_PEEK_HEIGHT_DP) + systemBars.bottom
            aircraftSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            sheet.requestLayout()
            insets
        }
        return root
    }

    private fun buildMapMenu(): View {
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xCC172033.toInt())
            elevation = dp(6).toFloat()
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, dp(8), dp(8))
        }
        val toggle = TextView(this).apply {
            text = "−"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(4), 0)
            addView(TextView(this@MapActivity).apply {
                text = "Map"
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { gravity = Gravity.CENTER_VERTICAL })
            addView(toggle, LinearLayout.LayoutParams(dp(40), dp(44)))
            setOnClickListener {
                val minimizing = body.visibility == View.VISIBLE
                body.visibility = if (minimizing) View.GONE else View.VISIBLE
                toggle.text = if (minimizing) "+" else "−"
            }
        }
        controls.addView(header)
        body.addView(MaterialButton(this).apply {
            text = "Receiver"
            setOnClickListener { recenter() }
        })
        body.addView(CheckBox(this).apply {
            text = "FIR"
            setTextColor(Color.WHITE)
            isChecked = settings.firEnabled
            setOnCheckedChangeListener { _, checked ->
                settings.firEnabled = checked
                airspaceOverlay.setVisibility(fir = checked)
            }
        })
        body.addView(CheckBox(this).apply {
            text = "TRACON"
            setTextColor(Color.WHITE)
            isChecked = settings.traconEnabled
            setOnCheckedChangeListener { _, checked ->
                settings.traconEnabled = checked
                airspaceOverlay.setVisibility(tracon = checked)
            }
        })
        body.addView(CheckBox(this).apply {
            text = "Labels"
            setTextColor(Color.WHITE)
            isChecked = settings.labelsEnabled
            setOnCheckedChangeListener { _, checked ->
                settings.labelsEnabled = checked
                airspaceOverlay.setVisibility(labels = checked)
            }
        })
        controls.addView(body)
        return controls
    }

    private fun buildAircraftSheet(): LinearLayout {
        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFA172033.toInt())
            elevation = dp(12).toFloat()
        }
        aircraftSheetToggle = TextView(this).apply {
            text = "▲"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }
        aircraftSheetTitle = TextView(this).apply {
            text = "Aircraft"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(Color.WHITE)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
            addView(aircraftSheetToggle, LinearLayout.LayoutParams(dp(36), dp(TABLE_PEEK_HEIGHT_DP)))
            addView(aircraftSheetTitle, LinearLayout.LayoutParams(0, dp(TABLE_PEEK_HEIGHT_DP), 1f))
            setOnClickListener {
                aircraftSheetBehavior.state = if (aircraftSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
                    BottomSheetBehavior.STATE_EXPANDED
                } else {
                    BottomSheetBehavior.STATE_COLLAPSED
                }
            }
        }
        sheet.addView(header)

        aircraftTable = AircraftTableView(this).apply {
            onAircraftClick = ::showAircraft
        }
        val horizontal = HorizontalScrollView(this).apply {
            isFillViewport = false
            addView(aircraftTable, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }
        val vertical = NestedScrollView(this).apply {
            isFillViewport = true
            addView(horizontal, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }
        sheet.addView(vertical, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ))
        return sheet
    }

    private fun loadBoundaries() {
        Thread({
            val fir = runCatching { BoundaryGeometry.rings(boundaries.collection(BoundaryRepository.FIR_FILE)) }.getOrDefault(emptyList())
            val tracon = runCatching { BoundaryGeometry.rings(boundaries.collection(BoundaryRepository.TRACON_FILE)) }.getOrDefault(emptyList())
            airspaceOverlay.setBoundaryRings(fir, tracon)
        }, "BoundaryMapLoad").start()
    }

    private fun refreshAircraft() {
        val allAircraft = AircraftRepository.active()
        val mappableAircraft = allAircraft.filter { it.hasValidPosition }
        aircraftSheetTitle.text = "Aircraft: ${allAircraft.size} · On map: ${mappableAircraft.size}"
        refreshAircraftTable(allAircraft)

        airspaceOverlay.updateTraffic(mappableAircraft, settings.receiverPoint())
        if (!initialViewportSet) frameAircraft(mappableAircraft)
        selectedIcao?.let { icao ->
            mappableAircraft.firstOrNull { it.icao == icao }?.let {
                mapView.controller.setCenter(GeoPoint(it.latitude!!, it.longitude!!))
            }
        }
    }

    private fun refreshAircraftTable(values: List<AircraftSnapshot>, force: Boolean = false) {
        if (!::aircraftSheetBehavior.isInitialized) return
        if (!force && aircraftSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) return
        val now = System.currentTimeMillis()
        if (!force && now - lastTableRefreshMs < TABLE_REFRESH_MS) return
        lastTableRefreshMs = now

        aircraftTable.update(values, values.map { tableValues(it, now) })
    }

    private fun tableValues(value: AircraftSnapshot, now: Long): List<String> = buildList {
        add(value.icao)
        add(value.callsign.orDash())
        add(value.latitude?.let { String.format(Locale.US, "%.5f", it) }.orDash())
        add(value.longitude?.let { String.format(Locale.US, "%.5f", it) }.orDash())
        add(value.altitudeFeet.asText())
        add(value.altitudeSource.orDash())
        add(String.format(Locale.US, "%.1f", value.signalDb))
        add(value.baroSetting?.let { String.format(Locale.US, "%.1f", it) }.orDash())
        add(value.category?.let { String.format(Locale.US, "0x%02X", it) }.orDash())
        add(value.groundSpeedKnots.asText())
        add(value.headingDegrees.asText())
        add(value.headingDeltaDegrees.toString())
        add(value.trackDegrees.asText())
        add(value.verticalRateFeetPerMinute.asText())
        add(value.squawk.orDash())
        add(value.emergencyState.asText())
        add(value.selectedAltitudeFeet.asText())
        add(value.selectedHeadingDegrees.asText())
        add(value.onGround.yesNo())
        add(value.ready.yesNo())
        add(value.autopilotEngaged.yesNo())
        add(value.verticalNavEnabled.yesNo())
        add(value.altitudeHoldEnabled.yesNo())
        add(value.tcasEnabled.yesNo())
        add(value.onApproach.yesNo())
        add(value.uat.yesNo())
        add(value.messageCount.toString())
        add(value.lastSeenEpochMs.toString())
        add(ageSeconds(value.lastSeenEpochMs, now))
        add(value.lastPositionEpochMs.takeIf { it > 0 }?.toString().orDash())
        add(ageSeconds(value.lastPositionEpochMs, now))
        add(value.fileExportTimestampMs.takeIf { it > 0 }?.toString().orDash())
        addAll(modeSValues(value.evenMessage))
        addAll(rawPositionValues(value.evenPosition))
        addAll(modeSValues(value.oddMessage))
        addAll(rawPositionValues(value.oddPosition))
    }

    private fun modeSValues(value: io.github.mrjohn6774.skypulse.aircraft.ModeSFrameSnapshot?): List<String> = listOf(
        value?.rawHex.orDash(),
        value?.clockCount.asText(),
        value?.format.asText(),
        value?.extendedSquitterType.asText(),
        value?.extendedSquitterSubtype.asText(),
        value?.icao.orDash(),
        value?.correctedBits.asText(),
        value?.signalLevel?.let { String.format(Locale.US, "%.5f", it) }.orDash(),
        value?.valid?.yesNo() ?: "—",
    )

    private fun rawPositionValues(value: io.github.mrjohn6774.skypulse.aircraft.RawPositionSnapshot?): List<String> = listOf(
        value?.rawLatitude.asText(),
        value?.rawLongitude.asText(),
        value?.nucp.asText(),
        value?.timestampUptimeMs.asText(),
    )

    /**
     * A single drawing surface for the wide aircraft table. Only cells intersecting the current
     * horizontal and vertical viewport are drawn, so refreshing decoded data does not construct or
     * remeasure thousands of TextViews while the user is scrolling.
     */
    private inner class AircraftTableView(context: Context) : View(context) {
        private val headerHeight = dp(38)
        private val rowHeight = dp(36)
        private val horizontalPadding = dp(8).toFloat()
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = dp(12).toFloat()
            typeface = Typeface.DEFAULT_BOLD
        }
        private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = dp(11).toFloat()
        }
        private val gridPaint = Paint().apply {
            color = 0x334F6688
            strokeWidth = resources.displayMetrics.density.coerceAtLeast(1f)
        }
        private val headerBackgroundPaint = Paint().apply { color = 0xFF25344E.toInt() }
        private val alternateRowPaint = Paint().apply { color = 0x332E4668 }
        private val headerBaselineOffset =
            (headerHeight - headerPaint.fontMetrics.ascent - headerPaint.fontMetrics.descent) / 2f
        private val cellBaselineOffset =
            (rowHeight - cellPaint.fontMetrics.ascent - cellPaint.fontMetrics.descent) / 2f
        private val columnWidths = IntArray(TABLE_HEADERS.size) { index ->
            dp(tableColumnWidthDp(TABLE_HEADERS[index]))
        }
        private val columnStarts = IntArray(TABLE_HEADERS.size).also { starts ->
            for (index in 1 until starts.size) {
                starts[index] = starts[index - 1] + columnWidths[index - 1]
            }
        }
        private val contentWidth = columnWidths.sum()
        private var aircraft = emptyList<AircraftSnapshot>()
        private var rows = emptyList<List<String>>()
        private var downX = 0f
        private var downY = 0f

        var onAircraftClick: ((AircraftSnapshot) -> Unit)? = null

        init {
            isClickable = true
        }

        fun update(newAircraft: List<AircraftSnapshot>, newRows: List<List<String>>) {
            val heightChanged = rows.size != newRows.size
            aircraft = newAircraft
            rows = newRows
            if (heightChanged) requestLayout()
            invalidate()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val desiredHeight = headerHeight + rows.size * rowHeight + dp(16)
            setMeasuredDimension(
                resolveSize(contentWidth, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec),
            )
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val clip = canvas.clipBounds
            var firstColumn = 0
            while (
                firstColumn < TABLE_HEADERS.size &&
                columnStarts[firstColumn] + columnWidths[firstColumn] <= clip.left
            ) {
                firstColumn++
            }
            var lastColumnExclusive = firstColumn
            while (
                lastColumnExclusive < TABLE_HEADERS.size &&
                columnStarts[lastColumnExclusive] < clip.right
            ) {
                lastColumnExclusive++
            }

            if (clip.top < headerHeight) {
                canvas.drawRect(
                    clip.left.toFloat(),
                    0f,
                    clip.right.toFloat(),
                    headerHeight.toFloat(),
                    headerBackgroundPaint,
                )
                for (index in firstColumn until lastColumnExclusive) {
                    drawCellText(canvas, TABLE_HEADERS[index], index, 0, headerBaselineOffset, headerPaint)
                }
            }

            val firstRow = ((clip.top - headerHeight) / rowHeight).coerceAtLeast(0)
            val lastRowExclusive = ((clip.bottom - headerHeight + rowHeight - 1) / rowHeight)
                .coerceIn(0, rows.size)
            for (rowIndex in firstRow until lastRowExclusive) {
                val top = headerHeight + rowIndex * rowHeight
                if (rowIndex % 2 == 1) {
                    canvas.drawRect(
                        clip.left.toFloat(),
                        top.toFloat(),
                        clip.right.toFloat(),
                        (top + rowHeight).toFloat(),
                        alternateRowPaint,
                    )
                }
                val values = rows[rowIndex]
                for (columnIndex in firstColumn until lastColumnExclusive) {
                    drawCellText(
                        canvas,
                        values.getOrElse(columnIndex) { "" },
                        columnIndex,
                        top,
                        cellBaselineOffset,
                        cellPaint,
                    )
                }
                canvas.drawLine(
                    clip.left.toFloat(),
                    (top + rowHeight).toFloat(),
                    clip.right.toFloat(),
                    (top + rowHeight).toFloat(),
                    gridPaint,
                )
            }

            for (index in firstColumn until lastColumnExclusive) {
                val right = (columnStarts[index] + columnWidths[index]).toFloat()
                canvas.drawLine(right, clip.top.toFloat(), right, clip.bottom.toFloat(), gridPaint)
            }
            canvas.drawLine(
                clip.left.toFloat(),
                headerHeight.toFloat(),
                clip.right.toFloat(),
                headerHeight.toFloat(),
                gridPaint,
            )
        }

        private fun drawCellText(
            canvas: Canvas,
            value: String,
            columnIndex: Int,
            top: Int,
            baselineOffset: Float,
            paint: Paint,
        ) {
            val left = columnStarts[columnIndex]
            val right = left + columnWidths[columnIndex]
            canvas.save()
            canvas.clipRect(left, top, right, top + if (paint === headerPaint) headerHeight else rowHeight)
            canvas.drawText(value, left + horizontalPadding, top + baselineOffset, paint)
            canvas.restore()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    if (
                        kotlin.math.abs(event.x - downX) <= touchSlop &&
                        kotlin.math.abs(event.y - downY) <= touchSlop
                    ) {
                        if (event.y >= headerHeight) {
                            val rowIndex = ((event.y - headerHeight) / rowHeight).toInt()
                            aircraft.getOrNull(rowIndex)?.let { value ->
                                performClick()
                                onAircraftClick?.invoke(value)
                            }
                        }
                    }
                    return true
                }

                MotionEvent.ACTION_CANCEL -> return true
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }

    private fun tableColumnWidthDp(header: String): Int = when {
        header.endsWith(" raw") -> 220
        header.contains("epoch") || header.contains("uptime") || header.contains("clock") ||
            header == "File export ms" -> 136
        header == "Latitude" || header == "Longitude" || header.endsWith(" signal") -> 106
        else -> (header.length * 7 + 24).coerceIn(76, 132)
    }

    private fun String?.orDash(): String = this ?: "—"
    private fun Number?.asText(): String = this?.toString() ?: "—"
    private fun Boolean.yesNo(): String = if (this) "Yes" else "No"
    private fun ageSeconds(timestamp: Long, now: Long): String =
        if (timestamp <= 0) "—" else ((now - timestamp).coerceAtLeast(0L) / 1_000L).toString()

    private fun onMapClick(point: GeoPoint) {
        val aircraft = airspaceOverlay.findAircraftAt(point) ?: return
        selectedIcao = aircraft.icao
        showAircraft(aircraft)
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
        val receiver = settings.receiverPoint()
        if (receiver != null) {
            initialViewportSet = true
            mapView.controller.setZoom(8.0)
            mapView.controller.setCenter(GeoPoint(receiver.latitude, receiver.longitude))
        } else {
            frameAircraft(AircraftRepository.mappable())
        }
    }

    private fun frameAircraft(values: List<AircraftSnapshot>): Boolean {
        val points = values.mapNotNull { value ->
            val latitude = value.latitude ?: return@mapNotNull null
            val longitude = value.longitude ?: return@mapNotNull null
            GeoPoint(latitude, longitude)
        }
        if (points.isEmpty()) return false

        if (points.size == 1) {
            mapView.controller.setZoom(8.0)
            mapView.controller.setCenter(points.first())
        } else {
            mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(points), true, dp(64))
        }
        initialViewportSet = true
        return true
    }

    private fun isDarkTheme(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    private fun configureOsmdroid() {
        OsmdroidConfiguration.getInstance().load(this, getSharedPreferences(OSMDROID_PREFERENCES, MODE_PRIVATE))
        OsmdroidConfiguration.getInstance().apply {
            setOsmdroidBasePath(File(cacheDir, "osmdroid"))
            setOsmdroidTileCache(File(cacheDir, "osmdroid/tiles"))
            userAgentValue = packageName
            setTileDownloadThreads(TILE_DOWNLOAD_THREADS.toShort())
            setTileFileSystemThreads(TILE_FILE_SYSTEM_THREADS.toShort())
            setTileDownloadMaxQueueSize(TILE_DOWNLOAD_QUEUE_SIZE.toShort())
        }
    }

    private fun cartoTileSource(dark: Boolean): XYTileSource {
        val style = if (dark) "dark_all" else "light_all"
        return XYTileSource(
            if (dark) "CartoDark" else "CartoLight",
            0,
            20,
            256,
            ".png",
            arrayOf(
                "https://a.basemaps.cartocdn.com/$style/",
                "https://b.basemaps.cartocdn.com/$style/",
                "https://c.basemaps.cartocdn.com/$style/",
                "https://d.basemaps.cartocdn.com/$style/",
            ),
            "© OpenStreetMap contributors © CARTO",
        )
    }

    override fun onStart() { super.onStart(); handler.post(refresh) }
    override fun onStop() { handler.removeCallbacks(refresh); super.onStop() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onDestroy() { mapView.onDetach(); super.onDestroy() }

    companion object {
        private const val TABLE_PEEK_HEIGHT_DP = 42
        private const val TABLE_REFRESH_MS = 1_000L
        private const val OSMDROID_PREFERENCES = "osmdroid"
        private const val TILE_DOWNLOAD_THREADS = 8
        private const val TILE_FILE_SYSTEM_THREADS = 4
        private const val TILE_DOWNLOAD_QUEUE_SIZE = 96
        private val TABLE_HEADERS = arrayOf(
            "ICAO", "Flight", "Latitude", "Longitude", "Alt ft", "Alt src", "Avg signal dB",
            "Baro", "Category", "GS kt", "Heading", "Heading Δ", "Track", "VRate", "Squawk",
            "Status", "Selected alt", "Selected hdg", "Ground", "Ready", "AP", "VNAV",
            "Alt hold", "TCAS", "Approach", "UAT", "Messages", "Seen epoch ms", "Age s",
            "Position epoch ms", "Pos age s", "File export ms",
            "Even raw", "Even clock", "Even DF", "Even ME type", "Even ME sub", "Even ICAO",
            "Even corrected", "Even signal", "Even valid", "Even CPR lat", "Even CPR lon",
            "Even NUCp", "Even CPR uptime ms",
            "Odd raw", "Odd clock", "Odd DF", "Odd ME type", "Odd ME sub", "Odd ICAO",
            "Odd corrected", "Odd signal", "Odd valid", "Odd CPR lat", "Odd CPR lon", "Odd NUCp",
            "Odd CPR uptime ms",
        )
    }
}
