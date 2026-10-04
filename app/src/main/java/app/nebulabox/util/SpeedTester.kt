package app.nebulabox.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

object SpeedTester {

    data class Result(
        val downloadMbps: Double,
        val bytes: Long,
        val elapsedMs: Long,
    ) {
        val display: String get() = String.format(java.util.Locale.US, "%.1f Mbps", downloadMbps)
        val sizeDisplay: String get() = "${bytes / 1024L / 1024L} MB"
    }

    private const val CLOUDFLARE_URL = "https://speed.cloudflare.com/__down?bytes="
    private const val FALLBACK_URL = "https://speedtest.ftp.otenet.gr/files/test10Mb.db"

    suspend fun measure(
        proxyPort: Int,
        payloadBytes: Long = 15L * 1024L * 1024L,
        timeoutMs: Int = 25_000,
    ): Result? = withContext(Dispatchers.IO) {
        download("$CLOUDFLARE_URL$payloadBytes", proxyPort, timeoutMs)
            ?: download(FALLBACK_URL, proxyPort, timeoutMs)
    }

    private fun download(urlStr: String, proxyPort: Int, timeoutMs: Int): Result? {
        var connection: HttpURLConnection? = null
        return try {
            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", proxyPort))
            val conn = URL(urlStr).openConnection(proxy) as HttpURLConnection
            connection = conn
            conn.requestMethod = "GET"
            conn.connectTimeout = 8_000
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "JavidTun")
            conn.setRequestProperty("Cache-Control", "no-cache")
            conn.setRequestProperty("Connection", "close")

            if (conn.responseCode !in 200..299) return null

            val buffer = ByteArray(64 * 1024)
            var total = 0L
            val start = System.currentTimeMillis()
            conn.inputStream.use { stream ->
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (System.currentTimeMillis() - start > timeoutMs) break
                }
            }
            val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
            if (total <= 0L) return null

            Result(
                downloadMbps = (total * 8.0) / (elapsed / 1000.0) / 1_000_000.0,
                bytes = total,
                elapsedMs = elapsed,
            )
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { connection?.disconnect() }
        }
    }
}
