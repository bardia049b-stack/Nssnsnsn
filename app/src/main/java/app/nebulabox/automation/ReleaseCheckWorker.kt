package app.nebulabox.automation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.nebulabox.Application
import app.nebulabox.BuildConfig
import app.nebulabox.data.SettingsStore

class ReleaseCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val settings = SettingsStore(applicationContext).current()
        val isManual = inputData.getBoolean(KEY_MANUAL, false)
        if (!isManual && !settings.autoCheckAppUpdates) return Result.success()

        val release = ReleaseChecker.latestRelease()
            ?: return if (runAttemptCount < 3) Result.retry() else Result.success()

        val isNewer = ReleaseChecker.isNewer(release)

        if (isNewer && settings.notifyAppUpdates) {
            val preferences = applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            if (preferences.getString(KEY_LAST_NOTIFIED_TAG, null) != release.tag) {
                AutomationNotifications.show(
                    context = applicationContext,
                    notificationId = Application.NOTIFICATION_RELEASE_UPDATE,
                    title = "JavidTun ${release.tag} is available",
                    message = "You are on ${BuildConfig.VERSION_NAME}. Tap to open the release page.",
                    openUrl = release.releaseUrl,
                )
                preferences.edit().putString(KEY_LAST_NOTIFIED_TAG, release.tag).apply()
            }
        } else if (isManual && !isNewer && settings.notifyAppUpdates) {
            AutomationNotifications.show(
                context = applicationContext,
                notificationId = Application.NOTIFICATION_RELEASE_UPDATE,
                title = "JavidTun is up to date",
                message = "Installed: ${BuildConfig.VERSION_NAME} · Latest: ${release.tag}",
                openUrl = release.releaseUrl,
            )
        }

        return Result.success()
    }

    companion object {
        const val KEY_MANUAL = "manual_release_check"
        private const val PREFERENCES_NAME = "release_check"
        private const val KEY_LAST_NOTIFIED_TAG = "last_notified_tag"
    }
}
