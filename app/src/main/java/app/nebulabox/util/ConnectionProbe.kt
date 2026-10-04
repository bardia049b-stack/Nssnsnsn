package app.nebulabox.util

import java.io.EOFException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Proxy
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

object ConnectionProbe {

    enum class Failure {
        NONE,
        TIMEOUT,
        REFUSED,
        DNS,
        HANDSHAKE,
        SERVER_CLOSED,
        QUOTA,
        UNKNOWN,
    }

    data class Result(
        val reachable: Boolean,
        val delayMs: Long = 0L,
        val failure: Failure = Failure.NONE,
        val detail: String = "",
    )

    private val EOF_MARKERS = listOf(
        "eof",
        "unexpected end of stream",
        "connection reset",
        "broken pipe",
        "connection refused",
        "read timeout",
        "no route to host",
        "network is unreachable",
        "context canceled",
        "io: read/write on closed pipe",
    )

    suspend fun verifyThroughProxy(
        proxyPort: Int,
        testUrls: List<String>,
        attemptsPerUrl: Int = 2,
    ): Result {
        var last = Result(reachable = false, failure = Failure.UNKNOWN)
        for (url in testUrls.filter { it.isNotBlank() }) {
            repeat(attemptsPerUrl) {
                val attempt = probe(url, proxyPort)
                if (attempt.reachable) return attempt
                last = attempt
                if (attempt.failure == Failure.REFUSED || attempt.failure == Failure.HANDSHAKE) return attempt
            }
        }
        return last
    }

    private fun probe(urlStr: String, proxyPort: Int): Result {
        var conn: HttpURLConnection? = null
        val started = System.currentTimeMillis()
        return try {
            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", proxyPort))
            conn = (URL(urlStr).openConnection(proxy) as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 7000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "JavidTun")
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Connection", "close")
            }
            val code = conn.responseCode
            val delay = System.currentTimeMillis() - started
            when {
                code in 200..399 -> Result(true, delay)
                code == 204 -> Result(true, delay)
                code == 407 || code == 403 -> Result(false, delay, Failure.REFUSED, "HTTP $code")
                else -> Result(false, delay, Failure.UNKNOWN, "HTTP $code")
            }
        } catch (e: Throwable) {
            val delay = System.currentTimeMillis() - started
            Result(false, delay, classify(e), e.message ?: e.javaClass.simpleName)
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    fun classify(error: Throwable): Failure = when (error) {
        is SocketTimeoutException -> Failure.TIMEOUT
        is ConnectException -> Failure.REFUSED
        is NoRouteToHostException -> Failure.REFUSED
        is UnknownHostException -> Failure.DNS
        is SSLException -> Failure.HANDSHAKE
        is EOFException -> Failure.SERVER_CLOSED
        is SocketException -> {
            val text = error.message.orEmpty().lowercase()
            if ("closed" in text || "reset" in text || "abort" in text) Failure.SERVER_CLOSED else Failure.UNKNOWN
        }
        else -> classifyText(error.message.orEmpty())
    }

    fun classifyText(raw: String): Failure {
        val text = raw.lowercase()
        return when {
            text.isBlank() -> Failure.UNKNOWN
            "quota" in text || "traffic" in text || "expire" in text || "exhaust" in text || "no data left" in text ->
                Failure.QUOTA
            "eof" in text || "closed" in text || "reset" in text || "broken pipe" in text -> Failure.SERVER_CLOSED
            "handshake" in text || "tls" in text || "reality" in text || "certificate" in text -> Failure.HANDSHAKE
            "refused" in text -> Failure.REFUSED
            "timeout" in text || "timed out" in text -> Failure.TIMEOUT
            "dns" in text || "no such host" in text || "unresolved" in text -> Failure.DNS
            else -> Failure.UNKNOWN
        }
    }

    fun looksLikeCoreEof(line: String): Boolean {
        val text = line.lowercase()
        return EOF_MARKERS.any { it in text }
    }
}
