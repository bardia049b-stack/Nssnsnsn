package app.nebulabox

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import app.nebulabox.locale.LocaleManager
import app.nebulabox.util.AppLogger

class Application : android.app.Application() {

    lateinit var prefs: SharedPreferences
        private set

    override fun onCreate() {
        instance = this
        super.onCreate()
        AppLogger.init(this)
        prefs = getSharedPreferences("nebula", Context.MODE_PRIVATE)
        LocaleManager.applyStoredLocale(this)
        createNotificationChannels()
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
        manager.createNotificationChannel(tunnel)
    }

    companion object {
        const val CHANNEL_TUNNEL = "tunnel"

        lateinit var instance: Application
            private set
    }
}
