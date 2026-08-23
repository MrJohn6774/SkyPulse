package io.github.mrjohn6774.skypulse.ui

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputLayout
import io.github.mrjohn6774.skypulse.service.AdsbForegroundService
import io.github.mrjohn6774.skypulse.map.BoundaryRepository
import io.github.mrjohn6774.skypulse.settings.StationSettings

class SettingsActivity : AppCompatActivity() {
    private lateinit var settings: StationSettings
    private lateinit var latitude: EditText
    private lateinit var longitude: EditText
    private lateinit var altitude: EditText
    private lateinit var rtlPort: EditText
    private lateinit var sampleRate: EditText
    private lateinit var frequency: EditText
    private lateinit var gain: EditText
    private lateinit var beastPort: EditText
    private lateinit var beastExport: MaterialSwitch
    private lateinit var styleUrl: EditText
    private lateinit var autoGain: MaterialSwitch
    private lateinit var startBoot: MaterialSwitch
    private lateinit var fir: MaterialSwitch
    private lateinit var tracon: MaterialSwitch
    private lateinit var labels: MaterialSwitch
    private lateinit var boundaryUpdate: MaterialSwitch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = StationSettings(this)
        setContentView(buildContent())
    }

    private fun buildContent(): ScrollView {
        val content = verticalLayout()
        content.addMatchWidth(heading("Station settings"))
        content.addMatchWidth(section("Receiver location"))
        latitude = content.field("Latitude", settings.receiverLatitude.toString(), decimal = true)
        longitude = content.field("Longitude", settings.receiverLongitude.toString(), decimal = true)
        altitude = content.field("Altitude metres (optional)", settings.receiverAltitudeMetres?.toString().orEmpty(), decimal = true)

        content.addMatchWidth(section("RTL-TCP"))
        rtlPort = content.field("RTL-TCP port", settings.rtlTcpPort.toString())
        sampleRate = content.field("Sample rate", settings.sampleRate.toString())
        frequency = content.field("Frequency Hz", settings.frequency.toString())
        autoGain = content.switch("Tuner AGC", settings.automaticGain)
        gain = content.field("Manual gain (tenths of dB)", settings.gainTenthsDb.toString())
        startBoot = content.switch("Start at boot", settings.startAtBoot)

        content.addMatchWidth(section("Data export"))
        beastExport = content.switch("Beast TCP export", settings.beastTcpExportEnabled)
        beastPort = content.field("Beast TCP port (127.0.0.1 only)", settings.beastPort.toString())
        fun updateBeastPortEnabled() { beastPort.isEnabled = beastExport.isChecked; beastPort.alpha = if (beastExport.isChecked) 1f else 0.55f }
        updateBeastPortEnabled()
        beastExport.setOnCheckedChangeListener { _, _ -> updateBeastPortEnabled() }
        content.addMatchWidth(MaterialButton(this).apply {
            text = "Apply data export"
            setOnClickListener { applyBeastExport() }
        })

        content.addMatchWidth(section("Map and boundary data"))
        styleUrl = content.field("OpenFreeMap style URL", settings.mapStyleUrl, numeric = false)
        fir = content.switch("FIR overlay", settings.firEnabled)
        tracon = content.switch("TRACON overlay", settings.traconEnabled)
        labels = content.switch("Aircraft labels", settings.labelsEnabled)
        boundaryUpdate = content.switch("Weekly boundary update", settings.boundaryAutoUpdate)
        content.addMatchWidth(MaterialButton(this).apply {
            text = "Update boundary data now"
            setOnClickListener {
                BoundaryRepository(this@SettingsActivity, settings).updateNow()
                Toast.makeText(this@SettingsActivity, "Boundary update started", Toast.LENGTH_SHORT).show()
            }
        })

        content.addMatchWidth(MaterialButton(this).apply {
            text = "Save and restart receiver"
            setOnClickListener { save() }
        })
        return ScrollView(this).apply { addView(content) }
    }

    private fun android.widget.LinearLayout.field(
        label: String,
        value: String,
        decimal: Boolean = false,
        numeric: Boolean = true,
    ): EditText {
        val edit = EditText(this@SettingsActivity).apply {
            setText(value)
            inputType = if (!numeric) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            else InputType.TYPE_CLASS_NUMBER or if (decimal) InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED else 0
        }
        addMatchWidth(TextInputLayout(this@SettingsActivity).apply {
            hint = label
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            addView(edit)
        })
        return edit
    }

    private fun android.widget.LinearLayout.switch(label: String, checked: Boolean): MaterialSwitch {
        return MaterialSwitch(this@SettingsActivity).apply {
            text = label
            isChecked = checked
            setPadding(0, dp(4), 0, dp(4))
            this@switch.addMatchWidth(this)
        }
    }

    private fun save() {
        try {
            settings.receiverLatitude = latitude.text.toString().toDouble()
            settings.receiverLongitude = longitude.text.toString().toDouble()
            settings.receiverAltitudeMetres = altitude.text.toString().toDoubleOrNull()
            settings.rtlTcpPort = rtlPort.text.toString().toInt()
            settings.sampleRate = sampleRate.text.toString().toLong()
            settings.frequency = frequency.text.toString().toLong()
            settings.automaticGain = autoGain.isChecked
            settings.gainTenthsDb = gain.text.toString().toInt()
            settings.beastPort = beastPort.text.toString().toInt()
            settings.beastTcpExportEnabled = beastExport.isChecked
            settings.startAtBoot = startBoot.isChecked
            settings.mapStyleUrl = styleUrl.text.toString()
            settings.firEnabled = fir.isChecked
            settings.traconEnabled = tracon.isChecked
            settings.labelsEnabled = labels.isChecked
            settings.boundaryAutoUpdate = boundaryUpdate.isChecked
            AdsbForegroundService.start(this, AdsbForegroundService.ACTION_RESTART)
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            finish()
        } catch (error: NumberFormatException) {
            Toast.makeText(this, "Check the numeric settings", Toast.LENGTH_LONG).show()
        }
    }

    private fun applyBeastExport() {
        try {
            val port = beastPort.text.toString().toInt()
            if (port !in 1024..65535) throw NumberFormatException()
            settings.beastPort = port
            settings.beastTcpExportEnabled = beastExport.isChecked
            AdsbForegroundService.start(this, AdsbForegroundService.ACTION_APPLY_BEAST_EXPORT)
            Toast.makeText(this, "Data export updated", Toast.LENGTH_SHORT).show()
        } catch (error: NumberFormatException) {
            Toast.makeText(this, "Beast TCP port must be 1024–65535", Toast.LENGTH_LONG).show()
        }
    }
}
