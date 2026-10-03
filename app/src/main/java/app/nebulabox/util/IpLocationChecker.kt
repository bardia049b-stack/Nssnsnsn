package app.nebulabox.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.Locale

/**
 * Queries the active tunnel's exit IP address, country, flag emoji, and HTTP delay
 * through the local sing-box mixed proxy port (`127.0.0.1:10808`), matching v2rayNG's
 * `SpeedtestManager.getRemoteIPInfo()`.
 */
object IpLocationChecker {

    const val LOCAL_MIXED_PORT = 10808

    data class EndpointLocation(
        val ip: String,
        val countryCode: String,
        val countryName: String,
        val city: String = "",
        val isp: String = "",
        val flagEmoji: String,
        val delayMs: Long,
    ) {
        val displaySummary: String
            get() = buildString {
                if (flagEmoji.isNotBlank()) append("$flagEmoji ")
                append(countryName.ifBlank { countryCode.ifBlank { "Unknown" } })
                if (city.isNotBlank() && !city.equals(countryName, ignoreCase = true)) {
                    append(" · $city")
                }
            }
    }

    suspend fun fetchLocation(proxyPort: Int = LOCAL_MIXED_PORT): EndpointLocation? =
        withContext(Dispatchers.IO) {
            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", proxyPort))

            // 1. Try ip-api.com (fast JSON with country, countryCode, city, isp, query)
            fetchFromIpApi(proxy)?.let { return@withContext it }

            // 2. Try api.ip.sb/geoip (v2rayNG's default IP_API_URL)
            fetchFromIpSb(proxy)?.let { return@withContext it }

            // 3. Try ipwho.is
            fetchFromIpWhois(proxy)?.let { return@withContext it }

            // 4. Try cloudflare cdn-cgi/trace (always works on Cloudflare Workers *.workers.dev)
            fetchFromCloudflareTrace(proxy)?.let { return@withContext it }

            null
        }

    private fun fetchFromIpApi(proxy: Proxy): EndpointLocation? = runCatching {
        val start = System.currentTimeMillis()
        val body = httpGet("http://ip-api.com/json/?fields=status,country,countryCode,city,isp,query", proxy, 5000)
            ?: return null
        val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
        val json = JSONObject(body)
        if (json.optString("status") != "success") return null
        val ip = json.optString("query").trim()
        val code = json.optString("countryCode").trim().uppercase(Locale.US)
        val country = json.optString("country").trim()
        val city = json.optString("city").trim()
        val isp = json.optString("isp").trim()
        if (ip.isEmpty()) return null
        EndpointLocation(
            ip = ip,
            countryCode = code,
            countryName = country.ifBlank { countryNameFromCode(code) },
            city = city,
            isp = isp,
            flagEmoji = countryCodeToFlag(code),
            delayMs = elapsed,
        )
    }.getOrNull()

    private fun fetchFromIpSb(proxy: Proxy): EndpointLocation? = runCatching {
        val start = System.currentTimeMillis()
        val body = httpGet("https://api.ip.sb/geoip", proxy, 5000) ?: return null
        val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
        val json = JSONObject(body)
        val ip = json.optString("ip").trim()
        val code = json.optString("country_code").trim().uppercase(Locale.US)
        val country = json.optString("country").trim()
        val city = json.optString("city").trim()
        val isp = json.optString("organization").ifBlank { json.optString("isp") }.trim()
        if (ip.isEmpty()) return null
        EndpointLocation(
            ip = ip,
            countryCode = code,
            countryName = country.ifBlank { countryNameFromCode(code) },
            city = city,
            isp = isp,
            flagEmoji = countryCodeToFlag(code),
            delayMs = elapsed,
        )
    }.getOrNull()

    private fun fetchFromIpWhois(proxy: Proxy): EndpointLocation? = runCatching {
        val start = System.currentTimeMillis()
        val body = httpGet("https://ipwho.is/", proxy, 5000) ?: return null
        val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
        val json = JSONObject(body)
        if (!json.optBoolean("success", true)) return null
        val ip = json.optString("ip").trim()
        val code = json.optString("country_code").trim().uppercase(Locale.US)
        val country = json.optString("country").trim()
        val city = json.optString("city").trim()
        val isp = json.optJSONObject("connection")?.optString("isp").orEmpty().trim()
        if (ip.isEmpty()) return null
        EndpointLocation(
            ip = ip,
            countryCode = code,
            countryName = country.ifBlank { countryNameFromCode(code) },
            city = city,
            isp = isp,
            flagEmoji = countryCodeToFlag(code),
            delayMs = elapsed,
        )
    }.getOrNull()

    private fun fetchFromCloudflareTrace(proxy: Proxy): EndpointLocation? = runCatching {
        val start = System.currentTimeMillis()
        val body = httpGet("https://www.cloudflare.com/cdn-cgi/trace", proxy, 5000)
            ?: httpGet("http://cp.cloudflare.com/cdn-cgi/trace", proxy, 5000)
            ?: return null
        val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
        val map = body.lines()
            .mapNotNull { line ->
                val idx = line.indexOf('=')
                if (idx <= 0) null else line.substring(0, idx).trim() to line.substring(idx + 1).trim()
            }
            .toMap()
        val ip = map["ip"].orEmpty()
        val code = map["loc"].orEmpty().uppercase(Locale.US)
        val colo = map["colo"].orEmpty()
        if (ip.isEmpty()) return null
        EndpointLocation(
            ip = ip,
            countryCode = code,
            countryName = countryNameFromCode(code),
            city = colo,
            isp = "Cloudflare",
            flagEmoji = countryCodeToFlag(code),
            delayMs = elapsed,
        )
    }.getOrNull()

    private fun httpGet(urlStr: String, proxy: Proxy, timeoutMs: Int): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection(proxy) as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; NebulaBox)")
                setRequestProperty("Accept", "application/json, text/plain, */*")
                setRequestProperty("Connection", "close")
            }
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    fun countryCodeToFlag(code: String): String {
        val clean = code.trim().uppercase(Locale.US)
        if (clean.length != 2 || !clean.all { it in 'A'..'Z' }) return "🌐"
        val first = Character.toChars(0x1F1E6 + (clean[0] - 'A'))
        val second = Character.toChars(0x1F1E6 + (clean[1] - 'A'))
        return String(first) + String(second)
    }

    private fun countryNameFromCode(code: String): String {
        if (code.length != 2) return code
        return runCatching {
            Locale("", code).getDisplayCountry(Locale.ENGLISH).ifBlank { code }
        }.getOrDefault(code)
    }
}
