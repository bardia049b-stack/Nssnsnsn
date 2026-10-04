package app.nebulabox.automation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.nebulabox.Application
import app.nebulabox.data.ProfileStore
import app.nebulabox.data.SettingsStore
import app.nebulabox.util.ShareLinkParser
import java.net.HttpURLConnection
import java.net.URL

class SubscriptionRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val profileStore = ProfileStore(applicationContext)
        val settingsStore = SettingsStore(applicationContext)
        val settings = settingsStore.current()
        if (!settings.autoUpdateSubscriptions) return Result.success()

        val subscriptions = profileStore.allSubscriptions().filter { it.enabled }
        if (subscriptions.isEmpty()) return Result.success()

        var updatedGroups = 0
        var updatedProfiles = 0
        var failedGroups = 0

        for (subscription in subscriptions) {
            if (isStopped) return Result.retry()
            val body = fetch(subscription.url)
            if (body.isNullOrBlank()) {
                failedGroups++
                continue
            }
            val parsed = runCatching { ShareLinkParser.parseMany(body) }
                .getOrDefault(emptyList())
            if (parsed.isEmpty()) {
                failedGroups++
                continue
            }
            profileStore.replaceSubscriptionProfiles(subscription.id, subscription.url, parsed)
            profileStore.upsertSubscription(
                subscription.copy(updatedAt = System.currentTimeMillis()),
            )
            updatedGroups++
            updatedProfiles += parsed.size
        }

        if (updatedGroups == 0 && failedGroups == subscriptions.size && runAttemptCount < 3) {
            return Result.retry()
        }

        if (settings.notifySubscriptionUpdates) {
            val message = when {
                updatedGroups > 0 && failedGroups > 0 ->
                    "Updated $updatedGroups group(s), $updatedProfiles server(s); $failedGroups failed"
                updatedGroups > 0 ->
                    "Updated $updatedGroups group(s) and $updatedProfiles server(s)"
                else ->
                    "No subscription could be updated. Existing servers were kept."
            }
            AutomationNotifications.show(
                context = applicationContext,
                notificationId = Application.NOTIFICATION_SUBSCRIPTION_UPDATE,
                title = "Subscription update finished",
                message = message,
            )
        }

        return Result.success()
    }

    private fun fetch(address: String): String? {
        if (!address.startsWith("https://", true) && !address.startsWith("http://", true)) return null
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(address).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 20000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "JavidTun/${app.nebulabox.BuildConfig.VERSION_NAME}")
                setRequestProperty("Accept", "*/*")
            }
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().use { reader ->
                val result = StringBuilder()
                val buffer = CharArray(8192)
                while (true) {
                    val read = reader.read(buffer)
                    if (read < 0) break
                    if (result.length + read > 5_000_000) return null
                    result.append(buffer, 0, read)
                }
                result.toString()
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { connection?.disconnect() }
        }
    }
}
