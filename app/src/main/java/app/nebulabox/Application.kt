package app.nebulabox

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import app.nebulabox.automation.AutomationScheduler
import app.nebulabox.automation.NetworkReconnectMonitor
import app.nebulabox.data.SettingsStore
import app.nebulabox.locale.LocaleManager
import app.nebulabox.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class Application : android.app.Application() {

    lateinit var prefs: SharedPreferences
        private set
    private var networkReconnectMonitor: NetworkReconnectMonitor? = null

    override fun onCreate() {
        instance = this
        super.onCreate()
        AppLogger.init(this)
        prefs = getSharedPreferences("nebula", Context.MODE_PRIVATE)
        LocaleManager.applyStoredLocale(this)
        createNotificationChannels()
        networkReconnectMonitor = NetworkReconnectMonitor(this).also { it.start() }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            AutomationScheduler.sync(this@Application, SettingsStore(this@Application).current())
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val tunnel = NotificationChannel(
            CHANNEL_TUNNEL,
            getString(R.string.channel_tunnel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
            setSound(null, null)
        }
        val updates = NotificationChannel(
            CHANNEL_UPDATES,
            getString(R.string.channel_updates),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            setShowBadge(true)
        }
        manager.createNotificationChannels(listOf(tunnel, updates))
    }

    companion object {
        const val CHANNEL_TUNNEL = "tunnel"
        const val CHANNEL_UPDATES = "updates"
        const val NOTIFICATION_SUBSCRIPTION_UPDATE = 2201
        const val NOTIFICATION_RELEASE_UPDATE = 2202

        lateinit var instance: Application
            private set
    }
}
