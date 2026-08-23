package io.github.mrjohn6774.skypulse.diagnostics

import android.util.Log
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

object DiagnosticLog {
    private const val LIMIT = 200
    private val entries = ArrayDeque<String>(LIMIT)
    private val formatter = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    @Synchronized
    fun info(tag: String, message: String) {
        Log.i(tag, message)
        add("I", tag, message)
    }

    @Synchronized
    fun warn(tag: String, message: String, error: Throwable? = null) {
        Log.w(tag, message, error)
        add("W", tag, if (error == null) message else "$message: ${error.message}")
    }

    @Synchronized
    fun error(tag: String, message: String, error: Throwable? = null) {
        Log.e(tag, message, error)
        add("E", tag, if (error == null) message else "$message: ${error.message}")
    }

    @Synchronized
    fun snapshot(): List<String> = entries.toList()

    private fun add(level: String, tag: String, message: String) {
        if (entries.size == LIMIT) entries.removeFirst()
        entries.addLast("${formatter.format(Date())} $level/$tag $message")
    }
}
