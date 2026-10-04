package app.nebulabox.automation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.nebulabox.Application
import app.nebulabox.BuildConfig
import app.nebulabox.data.SettingsStore
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ReleaseCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val settings = SettingsStore(applicationContext).current()
        val isManual = inputData.getBoolean(KEY_MANUAL, false)
        if (!isManual && !settings.autoCheckAppUpdates) return Result.success()

        val release = fetchLatestRelease() ?: return if (runAttemptCount < 3) Result.retry() else Result.success()
        val tag = release.first
        val releaseUrl = release.second
        val latestVersion = parseVersion(tag) ?: return Result.success()
        val currentVersion = parseVersion(BuildConfig.VERSION_NAME) ?: return Result.success()
        val isNewer = latestVersion > currentVersion

        if (isNewer && settings.notifyAppUpdates) {
            val preferences = applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            val previouslyNotified = preferences.getString(KEY_LAST_NOTIFIED_TAG, null)
            if (previouslyNotified != tag) {
                AutomationNotifications.show(
                    context = applicationContext,
                    notificationId = Application.NOTIFICATION_RELEASE_UPDATE,
                    title = "JavidTun update available",
                    message = "Version $tag is available. Tap to view the release and download it.",
                    openUrl = releaseUrl,
                )
                preferences.edit().putString(KEY_LAST_NOTIFIED_TAG, tag).apply()
            }
        } else if (isManual && !isNewer && settings.notifyAppUpdates) {
            AutomationNotifications.show(
                context = applicationContext,
                notificationId = Application.NOTIFICATION_RELEASE_UPDATE,
                title = "JavidTun is up to date",
                message = "Installed: ${BuildConfig.VERSION_NAME} · Latest: $tag",
            )
        }

        return Result.success()
    }

    private fun fetchLatestRelease(): Pair<String, String>? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 15000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "JavidTun/${BuildConfig.VERSION_NAME}")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            }
            if (connection.responseCode !in 200..299) return null
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            val payload = JSONObject(json)
            val tag = payload.optString("tag_name").trim()
            val url = payload.optString("html_url").trim()
            if (tag.isBlank() || url.isBlank()) null else tag to url
        } catch (_: Exception) {
            null
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    private fun parseVersion(value: String): ReleaseVersion? {
        val match = VERSION_PATTERN.find(value.trim()) ?: return null
        return ReleaseVersion(
            major = match.groupValues[1].toIntOrNull() ?: return null,
            minor = match.groupValues[2].toIntOrNull() ?: return null,
            patch = match.groupValues[3].toIntOrNull() ?: return null,
            build = match.groupValues[4].toIntOrNull() ?: 0,
        )
    }

    private data class ReleaseVersion(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val build: Int,
    ) : Comparable<ReleaseVersion> {
        override fun compareTo(other: ReleaseVersion): Int =
            compareValuesBy(this, other, ReleaseVersion::major, ReleaseVersion::minor, ReleaseVersion::patch, ReleaseVersion::build)
    }

    companion object {
        const val KEY_MANUAL = "manual_release_check"
        private const val PREFERENCES_NAME = "release_check"
        private const val KEY_LAST_NOTIFIED_TAG = "last_notified_tag"
        private const val RELEASES_API = "https://api.github.com/repos/bardia049b-stack/Nssnsnsn/releases/latest"
        private val VERSION_PATTERN = Regex("^[vV]?(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+](\\d+))?")
    }
}
