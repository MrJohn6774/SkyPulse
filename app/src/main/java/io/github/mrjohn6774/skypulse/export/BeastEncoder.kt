package io.github.mrjohn6774.skypulse.export

import com.flightaware.android.flightfeeder.analyzers.dump1090.ModeSMessage
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt
import kotlin.math.sqrt

object BeastEncoder {
    private const val ESCAPE = 0x1A

    fun encode(message: ModeSMessage): ByteArray? = encode(
        modeSBytes = message.bytes.map { it.toByte() }.toByteArray(),
        clockCount = message.clockCount,
        signalLevel = message.signalLevel,
    )

    fun encode(modeSBytes: ByteArray, clockCount: Long, signalLevel: Double): ByteArray? {
        val type = when (modeSBytes.size) {
            7 -> '2'.code
            14 -> '3'.code
            else -> return null
        }
        val output = ByteArrayOutputStream(2 + 7 + modeSBytes.size + 8)
        output.write(ESCAPE)
        output.write(type)
        for (shift in 40 downTo 0 step 8) appendEscaped(output, (clockCount ushr shift).toInt())
        val signal = (sqrt(signalLevel.coerceIn(0.0, 1.0)) * 255.0).roundToInt().coerceIn(0, 255)
        appendEscaped(output, signal)
        modeSBytes.forEach { appendEscaped(output, it.toInt()) }
        return output.toByteArray()
    }

    private fun appendEscaped(output: ByteArrayOutputStream, value: Int) {
        val byteValue = value and 0xFF
        output.write(byteValue)
        if (byteValue == ESCAPE) output.write(byteValue)
    }
}
