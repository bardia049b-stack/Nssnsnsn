package app.nebulabox.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import app.nebulabox.R
import app.nebulabox.data.SettingsStore
import app.nebulabox.engine.Engines
import app.nebulabox.engine.TunnelState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.N)
class NebulaTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { refresh() }
    }

    private suspend fun refresh() {
        val tile = qsTile ?: return
        val connected = Engines.active.value?.status?.value?.state == TunnelState.STARTED
        tile.state = if (connected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val connected = Engines.active.value?.status?.value?.state == TunnelState.STARTED
        if (connected) {
            Actions.disconnect(this)
        } else {
            scope.launch {
                val profileId = SettingsStore(this@NebulaTileService).current().selectedProfileId
                if (profileId == null) {
                    unlockAndRun {
                        startActivityAndCollapseCompat()
                    }
                } else {
                    Actions.connect(this@NebulaTileService, profileId)
                }
                refresh()
            }
        }
    }

    @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
    private fun startActivityAndCollapseCompat() {
        val intent = android.content.Intent(this, app.nebulabox.MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                android.app.PendingIntent.getActivity(
                    this, 0, intent,
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        } else {
            startActivityAndCollapse(intent)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
