package app.nebulabox.util

import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

object Formatters {

    /** Bytes per second into a readable rate. */
    fun speed(bytesPerSecond: Long): String {
        if (bytesPerSecond <= 0) return "0 B/s"
        return size(bytesPerSecond) + "/s"
    }

    fun size(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val exponent = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.size - 1)
        val value = bytes / 1024.0.pow(exponent.toDouble())
        val format = if (exponent == 0) "%.0f" else "%.1f"
        return String.format(Locale.US, format, value) + " " + units[exponent]
    }

    /** Elapsed time since [fromMillis] as h:mm:ss. */
    fun duration(fromMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        if (fromMillis <= 0) return "--:--"
        val seconds = ((nowMillis - fromMillis) / 1000).coerceAtLeast(0)
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    fun delay(millis: Int): String = if (millis < 0) "—" else "${millis} ms"

    /** Masks a secret so it can be shown in a list without leaking it. */
    fun secret(value: String): String = when {
        value.isEmpty() -> ""
        value.length <= 4 -> "•".repeat(value.length)
        else -> value.take(2) + "•".repeat((value.length - 4).coerceAtMost(12)) + value.takeLast(2)
    }
}
