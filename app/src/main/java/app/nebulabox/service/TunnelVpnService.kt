package app.nebulabox.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import app.nebulabox.Application
import app.nebulabox.MainActivity
import app.nebulabox.R
import app.nebulabox.config.ConfigBuilder
import app.nebulabox.data.AppSettings
import app.nebulabox.data.Profile
import app.nebulabox.data.ProfileStore
import app.nebulabox.data.SettingsStore
import app.nebulabox.engine.Engines
import app.nebulabox.engine.TunProvider
import app.nebulabox.engine.TunnelStatus
import app.nebulabox.engine.TunnelState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Owns the Android VPN session and hands the resulting tun descriptor to the
 * tunnel engine. Everything protocol specific lives in the engine; this class
 * only deals with the Android side of the contract.
 */
class TunnelVpnService : VpnService(), TunProvider {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var profileStore: ProfileStore
    private lateinit var settingsStore: SettingsStore
    private var interfaceFd: ParcelFileDescriptor? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var logJob: Job? = null
    private var activeProfileName = ""

    override fun onCreate() {
        super.onCreate()
        isServiceAlive = true
        Engines.tunProvider = this
        profileStore = ProfileStore(this)
        settingsStore = SettingsStore(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Actions.ACTION_CONNECT -> {
                val profileId = intent.getStringExtra(Actions.EXTRA_PROFILE_ID)
                scope.launch { connect(profileId) }
            }

            Actions.ACTION_DISCONNECT -> stopTunnel()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    private suspend fun connect(profileId: String?) {
        val settings: AppSettings = settingsStore.current()
        val known = profileStore.all()
        val profile: Profile? = if (profileId != null) {
            known.firstOrNull { it.id == profileId }
        } else {
            settings.selectedProfileId?.let { id -> known.firstOrNull { it.id == id } }
        }

        if (profile == null) {
            stopWithMessage(getString(R.string.error_no_profile))
            return
        }

        val config = runCatching { ConfigBuilder.build(profile, settings) }.getOrElse {
            stopWithMessage(getString(R.string.error_config, it.message ?: "?"))
            return
        }

        val problem = ConfigBuilder.validate(config)
        if (problem != null) {
            stopWithMessage(getString(R.string.error_config, problem))
            return
        }

        activeProfileName = profile.displayName
        showNotification(getString(R.string.status_starting))

        val engine = Engines.obtain()
        try {
            engine.start(activeProfileName, config, settings.mtu) { openTun(settings) }
            logJob?.cancel()
            logJob = scope.launch {
                engine.logs.collect { line ->
                    Log.println(priorityOf(line.level), "NebulaBox", line.message)
                }
            }
            acquireWakeLock()
        } catch (e: Exception) {
            stopWithMessage(e.message ?: getString(R.string.error_start))
        }
    }

    /**
     * Builds the Android VPN session. Returns true once a tun descriptor exists
     * for the engine to take ownership of via [tunFileDescriptor].
     */
    private fun openTun(settings: AppSettings): Boolean {
        val builder = Builder()
            .setSession(activeProfileName.ifBlank { getString(R.string.app_name) })
            .setMtu(settings.mtu)
            .setBlocking(false)

        if (settings.routeMode != "direct") {
            builder.addAddress("172.19.0.1", 30)
            builder.addRoute("0.0.0.0", 0)
        }
        if (settings.ipv6) {
            builder.addAddress("fdfe:dcba:9876::1", 126)
            builder.addRoute("::", 0)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        // Route our own traffic outside the tunnel so outbound sockets cannot loop.
        runCatching { builder.addDisallowedApplication(packageName) }

        if (settings.perAppEnabled) {
            runCatching {
                for (pkg in settings.perAppPackages) {
                    if (pkg == packageName) continue
                    if (settings.perAppMode == "include") builder.addAllowedApplication(pkg)
                    else builder.addDisallowedApplication(pkg)
                }
            }
        }

        return try {
            interfaceFd?.close()
            val fd = builder.establish()
            if (fd == null) {
                stopWithMessage(getString(R.string.error_tun))
                return false
            }
            interfaceFd = fd
            sendBroadcast(Intent(Actions.ACTION_STATE_CHANGED).setPackage(packageName))
            true
        } catch (e: Exception) {
            stopWithMessage(getString(R.string.error_tun) + ": " + e.message)
            false
        }
    }

    /**
     * Detaches the tun descriptor for the engine. Ownership passes to the
     * caller, which is why the field is cleared.
     */
    override fun tunFileDescriptor(): Int {
        val fd = interfaceFd?.detachFd() ?: -1
        interfaceFd = null
        return fd
    }

    override fun protectSocket(fd: Int): Boolean = protect(fd)

    private fun stopTunnel() {
        runCatching { Engines.active.value?.stop() }
        logJob?.cancel()
        logJob = null
        interfaceFd?.close()
        interfaceFd = null
        releaseWakeLock()
        isServiceAlive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        sendBroadcast(Intent(Actions.ACTION_STATE_CHANGED).setPackage(packageName))
        stopSelf()
    }

    private fun stopWithMessage(message: String) {
        Log.e(TAG, "tunnel failed: $message")
        val engine = Engines.active.value
        if (engine != null) {
            engine.status.value = TunnelStatus(
                state = TunnelState.STOPPED,
                profileName = activeProfileName,
                message = message,
            )
        }
        showNotification(message, error = true)
        interfaceFd?.close()
        interfaceFd = null
        releaseWakeLock()
        sendBroadcast(Intent(Actions.ACTION_STATE_CHANGED).setPackage(packageName))
        stopSelf()
    }

    override fun onRevoke() = stopTunnel()

    override fun onDestroy() {
        isServiceAlive = false
        if (Engines.tunProvider === this) Engines.tunProvider = null
        stopTunnel()
        scope.cancel()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NebulaBox:tunnel").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    private fun showNotification(text: String, error: Boolean = false) {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, TunnelVpnService::class.java).setAction(Actions.ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification: Notification =
            NotificationCompat.Builder(this, Application.CHANNEL_TUNNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(openIntent)
                .setOnlyAlertOnce(true)
                .setOngoing(!error)
                .addAction(0, getString(R.string.action_disconnect), stopIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun priorityOf(level: Int): Int = when (level) {
        0, 1 -> Log.VERBOSE
        2 -> Log.DEBUG
        3 -> Log.INFO
        4 -> Log.WARN
        5 -> Log.ERROR
        else -> Log.ASSERT
    }

    companion object {
        private const val TAG = "NebulaBox"
        private const val NOTIFICATION_ID = 1

        @Volatile
        var isServiceAlive: Boolean = false
            private set
    }
}
