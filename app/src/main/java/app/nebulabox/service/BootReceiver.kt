package app.nebulabox.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.nebulabox.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Restores the tunnel after a reboot when the user asked for auto connect. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            return
        }
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val settings = SettingsStore(appContext).current()
            val profileId = settings.selectedProfileId
            if (settings.autoConnect && profileId != null) {
                Actions.connect(appContext, profileId)
            }
        }
    }
}
