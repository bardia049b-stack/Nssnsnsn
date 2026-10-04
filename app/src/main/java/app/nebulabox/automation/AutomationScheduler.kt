package app.nebulabox.automation

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object AutomationScheduler {
    private const val SUBSCRIPTION_WORK = "subscription_auto_update"
    private const val RELEASE_CHECK_WORK = "release_auto_check"
    private const val RELEASE_CHECK_NOW_WORK = "release_check_now"

    fun sync(context: Context, settings: app.nebulabox.data.AppSettings) {
        val manager = WorkManager.getInstance(context.applicationContext)
        val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        if (settings.autoUpdateSubscriptions) {
            val intervalHours = when (settings.subscriptionUpdateIntervalHours) {
                6, 12, 24 -> settings.subscriptionUpdateIntervalHours
                else -> 12
            }
            val request = PeriodicWorkRequestBuilder<SubscriptionRefreshWorker>(
                intervalHours.toLong(),
                TimeUnit.HOURS,
            )
                .setConstraints(networkConstraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            manager.enqueueUniquePeriodicWork(
                SUBSCRIPTION_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        } else {
            manager.cancelUniqueWork(SUBSCRIPTION_WORK)
        }

        if (settings.autoCheckAppUpdates) {
            val request = PeriodicWorkRequestBuilder<ReleaseCheckWorker>(24, TimeUnit.HOURS)
                .setConstraints(networkConstraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            manager.enqueueUniquePeriodicWork(
                RELEASE_CHECK_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        } else {
            manager.cancelUniqueWork(RELEASE_CHECK_WORK)
        }
    }

    fun checkForReleaseNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<ReleaseCheckWorker>()
            .setInputData(workDataOf(ReleaseCheckWorker.KEY_MANUAL to true))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            RELEASE_CHECK_NOW_WORK,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
