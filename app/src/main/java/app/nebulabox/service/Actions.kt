package app.nebulabox.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object Actions {
    const val ACTION_CONNECT = "app.nebulabox.CONNECT"
    const val ACTION_DISCONNECT = "app.nebulabox.DISCONNECT"
    const val ACTION_STATE_CHANGED = "app.nebulabox.STATE_CHANGED"

    const val EXTRA_PROFILE_ID = "profile_id"

    fun connect(context: Context, profileId: String) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, TunnelVpnService::class.java)
                .setAction(ACTION_CONNECT)
                .putExtra(EXTRA_PROFILE_ID, profileId),
        )
    }

    fun disconnect(context: Context) {
        context.startService(
            Intent(context, TunnelVpnService::class.java).setAction(ACTION_DISCONNECT),
        )
    }
}
