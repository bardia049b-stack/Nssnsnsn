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
            val apks = (0 until (assets?.length() ?: 0))
                .mapNotNull { assets?.optJSONObject(it) }
                .filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
            val assetCode = apks.mapNotNull { parseBuildNumber(it.optString("name")) }.maxOrNull() ?: 0
            val wanted = preferredAbi()
            val apk = (apks.firstOrNull { it.optString("name").contains(wanted) } ?: apks.firstOrNull())
                ?.optString("browser_download_url")
                .orEmpty()
            UpdateInfo(
                tag = tag,
                buildNumber = maxOf(parseBuildNumber(tag), assetCode),
                releaseUrl = payload.optString("html_url").trim(),
                apkUrl = apk,
                notes = payload.optString("body").trim(),
            )
        } finally {
            runCatching { connection?.disconnect() }
        }
    }.getOrNull()

    /** The release carries one APK per architecture, the phone should only be offered its own. */
    private fun preferredAbi(): String {
        val primary = android.os.Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        return when {
            primary.startsWith("arm64") -> "arm64-v8a"
            primary.startsWith("armeabi") || primary.startsWith("arm") -> "armeabi-v7a"
            primary.startsWith("x86_64") -> "x86_64"
            primary.startsWith("x86") -> "x86"
            else -> primary
        }
    }

    fun isNewer(info: UpdateInfo): Boolean =
        info.buildNumber > 0 && info.buildNumber > BuildConfig.RELEASE_CODE

    fun parseBuildNumber(version: String): Int {
        val match = VERSION_PATTERN.find(version.trim()) ?: return 0
        return match.groupValues[4].toIntOrNull() ?: 0
    }

    private const val RELEASES_API = "https://api.github.com/repos/r4chan842/JavidTun/releases/latest"
    private val VERSION_PATTERN = Regex("(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+](\\d+))?")
}
