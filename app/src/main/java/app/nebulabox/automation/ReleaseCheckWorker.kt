package app.nebulabox.automation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.nebulabox.R
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
                    title = applicationContext.getString(R.string.release_available_title, release.tag),
                    message = applicationContext.getString(R.string.release_available_body, BuildConfig.VERSION_NAME),
                    openUrl = release.releaseUrl,
                )
                preferences.edit().putString(KEY_LAST_NOTIFIED_TAG, release.tag).apply()
            }
        } else if (isManual && !isNewer && settings.notifyAppUpdates) {
            AutomationNotifications.show(
                context = applicationContext,
                notificationId = Application.NOTIFICATION_RELEASE_UPDATE,
                title = applicationContext.getString(R.string.release_up_to_date),
                message = applicationContext.getString(
                    R.string.release_up_to_date_body,
                    BuildConfig.VERSION_NAME,
                    release.tag,
                ),
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
