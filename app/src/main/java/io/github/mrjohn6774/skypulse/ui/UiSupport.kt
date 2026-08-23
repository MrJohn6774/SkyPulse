package io.github.mrjohn6774.skypulse.ui

import android.content.Context
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

internal fun Context.verticalLayout(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(20), dp(20), dp(20), dp(28))
}

internal fun Context.heading(text: String): TextView = TextView(this).apply {
    this.text = text
    textSize = 24f
    setTypeface(typeface, Typeface.BOLD)
    setPadding(0, dp(8), 0, dp(12))
}

internal fun Context.section(text: String): TextView = TextView(this).apply {
    this.text = text
    textSize = 17f
    setTypeface(typeface, Typeface.BOLD)
    setPadding(0, dp(18), 0, dp(6))
}

internal fun ViewGroup.addMatchWidth(view: android.view.View) {
    addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
}
