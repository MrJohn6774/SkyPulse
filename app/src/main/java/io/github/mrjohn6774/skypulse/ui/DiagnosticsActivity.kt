package io.github.mrjohn6774.skypulse.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.github.mrjohn6774.skypulse.diagnostics.DiagnosticLog
import io.github.mrjohn6774.skypulse.health.HealthState

class DiagnosticsActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var text: TextView
    private val refresh = object : Runnable {
        override fun run() {
            val health = HealthState.snapshot()
            text.text = buildString {
                append("Health\n").append(health).append("\n\nRecent events\n")
                DiagnosticLog.snapshot().asReversed().forEach { append(it).append('\n') }
            }
            handler.postDelayed(this, 2_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        text = TextView(this).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 12f
            setPadding(dp(16), dp(16), dp(16), dp(24))
            setTextIsSelectable(true)
        }
        setContentView(ScrollView(this).apply { addView(text) })
    }

    override fun onStart() { super.onStart(); handler.post(refresh) }
    override fun onStop() { handler.removeCallbacks(refresh); super.onStop() }
}
