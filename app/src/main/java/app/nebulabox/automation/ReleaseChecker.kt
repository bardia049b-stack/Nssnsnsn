package app.nebulabox.automation

import app.nebulabox.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ReleaseChecker {

    data class UpdateInfo(
        val tag: String,
        val buildNumber: Int,
        val releaseUrl: String,
        val apkUrl: String,
        val notes: String,
    ) {
        val shortNotes: String
            get() = notes.lineSequence()
                .map { it.trim().removePrefix("- ").removePrefix("* ") }
                .filter { it.isNotEmpty() }
                .take(6)
                .joinToString("\n")
    }

    suspend fun latestRelease(): UpdateInfo? = runCatching {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "JavidTun/${BuildConfig.VERSION_NAME}")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            }
            if (connection.responseCode !in 200..299) return null
            val payload = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val tag = payload.optString("tag_name").trim()
            if (tag.isBlank()) return null
            val assets = payload.optJSONArray("assets")
            val apk = (0 until (assets?.length() ?: 0))
                .mapNotNull { assets?.optJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                ?.optString("browser_download_url")
                .orEmpty()
            UpdateInfo(
                tag = tag,
                buildNumber = parseBuildNumber(tag),
                releaseUrl = payload.optString("html_url").trim(),
                apkUrl = apk,
                notes = payload.optString("body").trim(),
            )
        } finally {
            runCatching { connection?.disconnect() }
        }
    }.getOrNull()

    fun isNewer(info: UpdateInfo): Boolean {
        val installed = parseBuildNumber(BuildConfig.VERSION_NAME)
        return info.buildNumber > 0 && info.buildNumber > installed
    }

    fun parseBuildNumber(version: String): Int {
        val match = VERSION_PATTERN.find(version.trim()) ?: return 0
        return match.groupValues[4].toIntOrNull() ?: 0
    }

    private const val RELEASES_API = "https://api.github.com/repos/bardia049b-stack/Nssnsnsn/releases/latest"
    private val VERSION_PATTERN = Regex("^[vV]?(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+](\\d+))?")
}
