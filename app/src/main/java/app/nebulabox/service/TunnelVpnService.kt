package app.nebulabox.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.PowerManager
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
import app.nebulabox.engine.TunnelState
import app.nebulabox.engine.TunnelStatus
import app.nebulabox.util.AppLogger
import app.nebulabox.util.Formatters
import com.v2ray.ang.service.TProxyService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android [VpnService] aligned with `v2rayNG 2.3.10`'s `CoreVpnService` & `CoreServiceManager`:
 *  - Supports both `hev-socks5-tunnel` (`TProxyService` + Xray SOCKS5/HTTP inbound)
 *    and Xray-core Native TUN (`"protocol": "tun"` / gVisor)
 *  - Tracks upstream physical network via [ConnectivityManager.NetworkCallback] and updates
 *    [setUnderlyingNetworks] so cellular/Wi-Fi handovers work seamlessly
 *  - Performs non-blocking teardown on [Dispatchers.IO]
 */
class TunnelVpnService : VpnService(), TunProvider {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var profileStore: ProfileStore
    private lateinit var settingsStore: SettingsStore
    @Volatile private var interfaceFd: ParcelFileDescriptor? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var statsNotificationJob: Job? = null
    private var connectJob: Job? = null
    private var activeProfileName = ""
    private val isStopping = AtomicBoolean(false)
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        isServiceAlive = true
        Engines.tunProvider = this
        profileStore = ProfileStore(this)
        settingsStore = SettingsStore(this)
        AppLogger.i(TAG, "TunnelVpnService.onCreate (hevTunLoaded=${TProxyService.isLoaded})")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Actions.ACTION_CONNECT -> {
                isStopping.set(false)
                val profileId = intent.getStringExtra(Actions.EXTRA_PROFILE_ID)
                connectJob?.cancel()
                connectJob = scope.launch { connect(profileId) }
            }

            Actions.ACTION_DISCONNECT -> {
                requestStopTunnel()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    private suspend fun connect(profileId: String?) {
        AppLogger.i(TAG, "connect requested (profileId=$profileId)")
        val rawSettings: AppSettings = settingsStore.current().normalized()
        // If hev-socks5-tunnel is requested but native lib isn't loaded, fall back to Xray Native TUN
        val settings = if (rawSettings.useHevTun && !TProxyService.isLoaded) {
            rawSettings.copy(useHevTun = false)
        } else {
            rawSettings
        }

        val known = profileStore.all()
        val profile: Profile? = if (profileId != null) {
            known.firstOrNull { it.id == profileId }
        } else {
            settings.selectedProfileId?.let { id -> known.firstOrNull { it.id == id } }
                ?: known.firstOrNull()
        }

        if (profile == null) {
            stopWithMessage(getString(R.string.error_no_profile))
            return
        }

        val config = runCatching { ConfigBuilder.build(profile, settings) }.getOrElse {
            AppLogger.e(TAG, "ConfigBuilder.build failed", it)
            stopWithMessage(getString(R.string.error_config, it.message ?: "?"))
            return
        }

        AppLogger.recordGeneratedConfig(config)

        val problem = ConfigBuilder.validate(config)
        if (problem != null) {
            AppLogger.e(TAG, "ConfigBuilder.validate failed: $problem")
            stopWithMessage(getString(R.string.error_config, problem))
            return
        }

        activeProfileName = profile.displayName
        withContext(Dispatchers.Main) {
            showNotification(getString(R.string.status_starting))
        }

        val safeMtu = if (settings.mtu in 1280..1500) settings.mtu else 1500
        val engine = Engines.obtain()
        try {
            // Stop any previous hev-socks5-tunnel session before starting
            TProxyService.stop()

            try {
                engine.start(activeProfileName, config, safeMtu) { openTun(settings) }
            } catch (geoErr: Throwable) {
                val msg = geoErr.message.orEmpty()
                if (msg.contains("geodata") || msg.contains("geosite") || msg.contains("geoip")) {
                    AppLogger.w(TAG, "Geodata error detected ($msg), retrying without geo rules...")
                    val fallbackConfig = ConfigBuilder.buildWithoutGeoRules(profile, settings)
                    AppLogger.recordGeneratedConfig(fallbackConfig)
                    engine.start(activeProfileName, fallbackConfig, safeMtu) { openTun(settings) }
                } else {
                    throw geoErr
                }
            }

            // If using hev-socks5-tunnel mode, start TProxyService on the established TUN fd
            val pfd = interfaceFd
            if (settings.useHevTun && pfd != null) {
                val startedHev = TProxyService.start(this, pfd, settings)
                if (!startedHev) {
                    throw IllegalStateException("hev-socks5-tunnel failed to start")
                }
            }

            registerNetworkMonitor()
            acquireWakeLock()

            withContext(Dispatchers.Main) {
                showNotification(getString(R.string.status_started) + " · " + activeProfileName)
            }

            if (settings.showSpeedInNotification) {
                startSpeedNotificationJob(engine)
            }

            AppLogger.i(
                TAG,
                "Tunnel connected: $activeProfileName (mode=${if (settings.useHevTun) "hev-socks5-tunnel" else "xray-tun"})",
            )
        } catch (e: Throwable) {
            AppLogger.e(TAG, "Tunnel start failed: ${e.message}", e)
            stopWithMessage(e.message ?: getString(R.string.error_start))
        }
    }

    /**
     * Builds the Android VPN session matching `v2rayNG 2.3.10`'s `CoreVpnService.configureVpnService`.
     */
    private fun openTun(settings: AppSettings): Boolean {
        if (prepare(this) != null) {
            AppLogger.e(TAG, "VPN permission not granted in openTun")
            return false
        }

        val safeMtu = if (settings.mtu in 1280..1500) settings.mtu else 1500
        val builder = Builder()
            .setSession(activeProfileName.ifBlank { getString(R.string.app_name) })
            .setMtu(safeMtu)
            .setBlocking(false)
            .addAddress("10.10.14.1", 30)

        if (settings.bypassLan) {
            ROUTED_IP_LIST.forEach { cidr ->
                val parts = cidr.split('/')
                if (parts.size == 2) {
                    builder.addRoute(parts[0], parts[1].toInt())
                }
            }
        } else {
            builder.addRoute("0.0.0.0", 0)
        }

        if (settings.ipv6) {
            builder.addAddress("fc00::10:10:14:1", 126)
            if (settings.bypassLan) {
                builder.addRoute("2000::", 3)
            } else {
                builder.addRoute("::", 0)
            }
        }

        // Configure VPN DNS servers (matching v2rayNG SettingsManager.getVpnDnsServers)
        val dnsList = settings.vpnDns.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf("1.1.1.1") }
        for (dns in dnsList) {
            runCatching { builder.addDnsServer(dns) }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(settings.meteredNetwork)
        }

        // Route our own app outside the TUN so Xray's outbound sockets never loop into tun0
        runCatching { builder.addDisallowedApplication(packageName) }

        if (settings.perAppEnabled && settings.perAppPackages.isNotEmpty()) {
            runCatching {
                if (settings.perAppMode == "include") {
                    for (pkg in settings.perAppPackages) {
                        if (pkg == packageName) continue
                        runCatching { builder.addAllowedApplication(pkg) }
                    }
                } else {
                    for (pkg in settings.perAppPackages) {
                        if (pkg == packageName) continue
                        runCatching { builder.addDisallowedApplication(pkg) }
                    }
                }
            }
        }

        return try {
            runCatching { interfaceFd?.close() }
            val fd = builder.establish()
            if (fd == null) {
                stopWithMessage(getString(R.string.error_tun))
                return false
            }
            interfaceFd = fd
            sendBroadcast(Intent(Actions.ACTION_STATE_CHANGED).setPackage(packageName))
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "builder.establish() failed", e)
            stopWithMessage(getString(R.string.error_tun) + ": " + e.message)
            false
        }
    }

    private fun registerNetworkMonitor() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        unregisterNetworkMonitor()
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val req = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runCatching { setUnderlyingNetworks(arrayOf(network)) }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                runCatching { setUnderlyingNetworks(arrayOf(network)) }
            }

            override fun onLost(network: Network) {
                runCatching { setUnderlyingNetworks(null) }
            }
        }
        runCatching {
            cm.requestNetwork(req, cb)
            networkCallback = cb
        }.onFailure { e ->
            AppLogger.w(TAG, "Failed to register network callback: ${e.message}")
        }
    }

    private fun unregisterNetworkMonitor() {
        val cb = networkCallback ?: return
        networkCallback = null
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        runCatching { cm.unregisterNetworkCallback(cb) }
    }

    private fun startSpeedNotificationJob(engine: app.nebulabox.engine.TunnelEngine) {
        statsNotificationJob?.cancel()
        statsNotificationJob = scope.launch {
            engine.status.collect { st ->
                if (st.state == TunnelState.STARTED) {
                    val speedText = "$activeProfileName · ↑ ${Formatters.speed(st.uplink)} ↓ ${Formatters.speed(st.downlink)}"
                    withContext(Dispatchers.Main) {
                        runCatching { showNotification(speedText) }
                    }
                }
            }
        }
    }

    override fun tunFileDescriptor(): Int = interfaceFd?.fd ?: -1

    override fun protectSocket(fd: Int): Boolean = protect(fd)

    private fun requestStopTunnel() {
        if (!isStopping.compareAndSet(false, true)) {
            return
        }
        AppLogger.i(TAG, "requestStopTunnel: transitioning to STOPPING")
        connectJob?.cancel()
        connectJob = null
        statsNotificationJob?.cancel()
        statsNotificationJob = null
        unregisterNetworkMonitor()

        val engine = Engines.active.value
        if (engine != null) {
            engine.status.value = engine.status.value.copy(state = TunnelState.STOPPING)
        }

        scope.launch(Dispatchers.IO) {
            // 1. Stop hev-socks5-tunnel first if running (matching v2rayNG CoreVpnService.stopV2Ray)
            runCatching { TProxyService.stop() }

            // 2. Stop Xray-core loop
            runCatching { engine?.stop() }

            // 3. Close TUN ParcelFileDescriptor
            val pfd = interfaceFd
            interfaceFd = null
            runCatching { pfd?.close() }

            // 4. Release WakeLock & stop service on Main thread
            releaseWakeLock()
            isServiceAlive = false
            withContext(Dispatchers.Main) {
                runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                runCatching {
                    sendBroadcast(Intent(Actions.ACTION_STATE_CHANGED).setPackage(packageName))
                }
                runCatching { stopSelf() }
            }
        }
    }

    private fun stopWithMessage(message: String) {
        AppLogger.e(TAG, "tunnel stopped with error: $message")
        unregisterNetworkMonitor()
        runCatching { TProxyService.stop() }
        runCatching { Engines.active.value?.stop() }
        val pfd = interfaceFd
        interfaceFd = null
        runCatching { pfd?.close() }

        val engine = Engines.active.value
        if (engine != null) {
            engine.status.value = TunnelStatus(
                state = TunnelState.STOPPED,
                profileName = activeProfileName,
                message = message,
            )
        }
        releaseWakeLock()
        isServiceAlive = false
        scope.launch(Dispatchers.Main) {
            runCatching { showNotification(message, error = true) }
            runCatching {
                sendBroadcast(Intent(Actions.ACTION_STATE_CHANGED).setPackage(packageName))
            }
            runCatching { stopSelf() }
        }
    }

    override fun onRevoke() {
        AppLogger.w(TAG, "VPN permission revoked by system")
        requestStopTunnel()
    }

    override fun onDestroy() {
        isServiceAlive = false
        unregisterNetworkMonitor()
        if (Engines.tunProvider === this) Engines.tunProvider = null
        if (!isStopping.get()) {
            val pfd = interfaceFd
            interfaceFd = null
            val engine = Engines.active.value
            Thread {
                runCatching { TProxyService.stop() }
                runCatching { engine?.stop() }
                runCatching { pfd?.close() }
            }.start()
        }
        releaseWakeLock()
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

    companion object {
        private const val TAG = "NebulaBox"
        private const val NOTIFICATION_ID = 1

        @Volatile
        var isServiceAlive: Boolean = false
            private set

        // v2rayNG AppConfig.ROUTED_IP_LIST for bypassLan
        private val ROUTED_IP_LIST = arrayOf(
            "0.0.0.0/5",
            "8.0.0.0/7",
            "11.0.0.0/8",
            "12.0.0.0/6",
            "16.0.0.0/4",
            "32.0.0.0/3",
            "64.0.0.0/2",
            "128.0.0.0/3",
            "160.0.0.0/5",
            "168.0.0.0/6",
            "172.0.0.0/12",
            "172.32.0.0/11",
            "172.64.0.0/10",
            "172.128.0.0/9",
            "173.0.0.0/8",
            "174.0.0.0/7",
            "176.0.0.0/4",
            "192.0.0.0/9",
            "192.128.0.0/11",
            "192.160.0.0/13",
            "192.169.0.0/16",
            "192.170.0.0/15",
            "192.172.0.0/14",
            "192.176.0.0/12",
            "192.192.0.0/10",
            "193.0.0.0/8",
            "194.0.0.0/7",
            "196.0.0.0/6",
            "200.0.0.0/5",
            "208.0.0.0/4",
            "240.0.0.0/4",
        )
    }
}
