package app.nebulabox.engine

import android.annotation.SuppressLint
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.provider.Settings
import android.system.OsConstants
import android.util.Log
import app.nebulabox.BuildConfig
import app.nebulabox.util.AppLogger
import io.nekohasekai.libbox.BridgeOptions
import io.nekohasekai.libbox.BridgeSession
import io.nekohasekai.libbox.CommandClient
import io.nekohasekai.libbox.CommandClientHandler
import io.nekohasekai.libbox.CommandClientOptions
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.ConnectionEvents
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.LogIterator
import io.nekohasekai.libbox.NeighborUpdateListener
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.OutboundGroupItemIterator
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.PlatformUser
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.libbox.ShellSession
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.SystemProxyStatus
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.InterfaceAddress
import java.net.NetworkInterface

/**
 * Drives the sing-box native core (`libbox.aar` v1.14.2).
 */
class LibboxEngine : TunnelEngine {

    override val status = MutableStateFlow(TunnelStatus())
    override val groups = MutableStateFlow<List<OutboundGroup>>(emptyList())

    private val logFlow = MutableSharedFlow<TunnelEngine.LogLine>(extraBufferCapacity = 256)
    override val logs: Flow<TunnelEngine.LogLine> = logFlow

    override val implementationName: String
        get() = "sing-box ${runCatching { Libbox.version() }.getOrDefault("unknown")}"

    override val functional: Boolean get() = true

    private var commandServer: CommandServer? = null
    private var commandClient: CommandClient? = null
    private var platform: Platform? = null
    private var openTunCallback: (() -> Boolean)? = null
    private var setupDone: Boolean = false

    private val monitorThread by lazy {
        HandlerThread("NebulaNetworkMonitor").apply { start() }
    }
    private val monitorHandler by lazy {
        Handler(monitorThread.looper)
    }

    private fun ensureSetup() {
        if (setupDone) return
        runCatching {
            val app = app.nebulabox.Application.instance
            val baseDir = app.filesDir.apply { mkdirs() }
            val workingDir = (app.getExternalFilesDir(null) ?: baseDir).apply { mkdirs() }
            val tempDir = app.cacheDir.apply { mkdirs() }
            val options = SetupOptions().apply {
                basePath = baseDir.absolutePath
                workingPath = workingDir.absolutePath
                tempPath = tempDir.absolutePath
                // Workaround for Go runtime stack crash on Android 9+ (golang/go#68760)
                fixAndroidStack = true
                logMaxLines = 3000
                debug = BuildConfig.DEBUG
                crashReportSource = "NebulaBox"
                appVersion = BuildConfig.VERSION_CODE.toString()
                appMarketingVersion = BuildConfig.VERSION_NAME
            }
            Libbox.setup(options)
            runCatching {
                Libbox.setLocale(java.util.Locale.getDefault().toLanguageTag())
            }
            setupDone = true
            AppLogger.i(TAG, "Libbox.setup ok: version=${runCatching { Libbox.version() }.getOrDefault("?")}, base=${baseDir.absolutePath}")
        }.onFailure {
            AppLogger.e(TAG, "Libbox.setup failed", it)
        }
    }

    private val serverHandler = object : CommandServerHandler {
        override fun serviceStop() {
            AppLogger.i(TAG, "CommandServerHandler.serviceStop called")
            status.value = status.value.copy(state = TunnelState.STOPPED)
        }

        override fun serviceReload() {
            AppLogger.i(TAG, "CommandServerHandler.serviceReload called")
        }

        // IMPORTANT: Must NEVER return null; command_server.go dereferences status.Enabled directly!
        override fun getSystemProxyStatus(): SystemProxyStatus {
            return SystemProxyStatus().apply {
                available = false
                enabled = false
            }
        }

        override fun setSystemProxyEnabled(enabled: Boolean) = Unit

        override fun writeDebugMessage(message: String?) {
            if (!message.isNullOrBlank()) {
                AppLogger.d("sing-box", message)
            }
        }

        override fun triggerNativeCrash() = Unit

        override fun connectSSHAgent(): Int = -1
    }

    private val clientHandler = object : CommandClientHandler {
        override fun connected() {
            AppLogger.i(TAG, "CommandClient connected to CommandServer")
            status.value = status.value.copy(state = TunnelState.STARTED)
        }

        override fun disconnected(message: String?) {
            if (!message.isNullOrBlank()) {
                AppLogger.w(TAG, "CommandClient disconnected: $message")
            }
        }

        override fun clearLogs() = Unit

        override fun initializeClashMode(modeList: StringIterator?, currentMode: String?) = Unit

        override fun updateClashMode(newMode: String?) = Unit

        override fun setDefaultLogLevel(level: Int) = Unit

        override fun writeStatus(message: io.nekohasekai.libbox.StatusMessage?) {
            message ?: return
            val current = status.value
            status.value = current.copy(
                uplink = message.uplink,
                downlink = message.downlink,
                uplinkTotal = message.uplinkTotal,
                downlinkTotal = message.downlinkTotal,
                memory = message.memory,
                connectionsIn = message.connectionsIn,
                connectionsOut = message.connectionsOut,
            )
        }

        override fun writeGroups(groupIterator: io.nekohasekai.libbox.OutboundGroupIterator?) {
            groupIterator ?: return
            val parsed = mutableListOf<OutboundGroup>()
            while (groupIterator.hasNext()) {
                val group = groupIterator.next()
                val items = mutableListOf<GroupItem>()
                val itemIterator = group.items
                while (itemIterator.hasNext()) {
                    val item = itemIterator.next()
                    items += GroupItem(
                        tag = item.tag,
                        type = item.type,
                        delayMs = item.urlTestDelay,
                    )
                }
                parsed += OutboundGroup(
                    tag = group.tag,
                    type = group.type,
                    selected = group.selected,
                    selectable = group.selectable,
                    items = items,
                )
            }
            groups.value = parsed
        }

        override fun writeOutbounds(items: OutboundGroupItemIterator?) = Unit

        override fun writeLogs(logIterator: LogIterator?) {
            logIterator ?: return
            while (logIterator.hasNext()) {
                val entry = logIterator.next() ?: continue
                logFlow.tryEmit(
                    TunnelEngine.LogLine(
                        level = entry.level,
                        time = System.currentTimeMillis(),
                        message = entry.message,
                    ),
                )
            }
        }

        override fun writeConnectionEvents(events: ConnectionEvents?) = Unit
    }

    override fun start(
        profileName: String,
        config: String,
        mtu: Int,
        openTun: () -> Boolean,
    ) {
        ensureSetup()

        // Clean up any previous session before starting
        cleanupInternal()

        openTunCallback = openTun
        status.value = TunnelStatus(state = TunnelState.STARTING, profileName = profileName)

        // Validate config with sing-box before starting CommandServer
        runCatching {
            Libbox.checkConfig(config)
            AppLogger.i(TAG, "Config validation (Libbox.checkConfig) succeeded")
        }.onFailure { err ->
            AppLogger.e(TAG, "Config validation failed: ${err.message}", err)
            throw IllegalArgumentException(err.message ?: "Invalid sing-box config", err)
        }

        val platformInterface = Platform()
        platform = platformInterface

        runCatching { Libbox.promoteOOMDraft() }
        runCatching { Libbox.discardPowerReportDraft() }

        AppLogger.i(TAG, "Starting CommandServer for profile '$profileName'")
        val server = CommandServer(serverHandler, platformInterface)
        server.start()
        commandServer = server

        AppLogger.i(TAG, "Calling startOrReloadService")
        server.startOrReloadService(config, OverrideOptions())
        AppLogger.i(TAG, "startOrReloadService succeeded")

        status.value = status.value.copy(
            state = TunnelState.STARTED,
            startedAt = System.currentTimeMillis(),
            message = "",
        )

        runCatching {
            val options = CommandClientOptions().apply {
                statusInterval = 1_000_000_000L // 1 second in nanoseconds
                addCommand(Libbox.CommandStatus)
                addCommand(Libbox.CommandLog)
                addCommand(Libbox.CommandGroup)
            }
            val client = CommandClient(clientHandler, options)
            client.connect()
            commandClient = client
        }.onFailure {
            AppLogger.w(TAG, "Status CommandClient connection warning: ${it.message}", it)
        }
    }

    private fun cleanupInternal() {
        runCatching { commandClient?.disconnect() }
        commandClient = null
        runCatching { commandServer?.closeService() }
        runCatching { commandServer?.close() }
        commandServer = null
        platform?.closeDefaultInterfaceMonitor(null)
        platform = null
    }

    override fun stop() {
        AppLogger.i(TAG, "Stopping LibboxEngine")
        cleanupInternal()
        openTunCallback = null
        status.value = TunnelStatus()
        groups.value = emptyList()
    }

    override fun selectOutbound(groupTag: String, itemTag: String) {
        runCatching { commandClient?.selectOutbound(groupTag, itemTag) }
            .onFailure { AppLogger.w(TAG, "selectOutbound failed", it) }
    }

    override fun urlTest(groupTag: String) {
        runCatching { commandClient?.urlTest(groupTag) }
            .onFailure { AppLogger.w(TAG, "urlTest failed", it) }
    }

    override fun clearLogs() {
        runCatching { commandClient?.clearLogs() }
    }

    // ------------------------------------------------------ platform layer

    private inner class Platform : PlatformInterface {

        override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

        override fun autoDetectInterfaceControl(fd: Int) {
            val ok = Engines.tunProvider?.protectSocket(fd) ?: false
            if (!ok) {
                AppLogger.w(TAG, "protectSocket($fd) returned false")
            }
        }

        override fun openTun(options: TunOptions): Int {
            AppLogger.i(TAG, "Platform.openTun requested (mtu=${options.mtu}, autoRoute=${options.autoRoute})")
            val ready = openTunCallback?.invoke() ?: false
            if (!ready) error("tun interface was not established")
            val fd = Engines.tunProvider?.tunFileDescriptor() ?: -1
            if (fd < 0) error("no tun descriptor available")
            AppLogger.i(TAG, "Platform.openTun established fd=$fd")
            return fd
        }

        override fun useProcFS(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

        // IMPORTANT: Must NEVER return null; service.go dereferences result.UserId when err == nil!
        @SuppressLint("NewApi")
        override fun findConnectionOwner(
            ipProtocol: Int,
            sourceAddress: String?,
            sourcePort: Int,
            destinationAddress: String?,
            destinationPort: Int,
        ): ConnectionOwner {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                error("findConnectionOwner requires Android 10+")
            }
            val cm = app.nebulabox.Application.instance.getSystemService(ConnectivityManager::class.java)
                ?: error("ConnectivityManager unavailable")
            val uid = cm.getConnectionOwnerUid(
                ipProtocol,
                InetSocketAddress(sourceAddress ?: "", sourcePort),
                InetSocketAddress(destinationAddress ?: "", destinationPort),
            )
            if (uid == Process.INVALID_UID) {
                error("android: connection owner not found")
            }
            val packages = app.nebulabox.Application.instance.packageManager.getPackagesForUid(uid)
            return ConnectionOwner().apply {
                userId = uid
                userName = packages?.firstOrNull() ?: ""
                setAndroidPackageNames(StringArray(packages?.toList().orEmpty().iterator()))
            }
        }

        override fun getInterfaces(): NetworkInterfaceIterator {
            val interfaces = mutableListOf<io.nekohasekai.libbox.NetworkInterface>()
            runCatching {
                val app = app.nebulabox.Application.instance
                val cm = app.getSystemService(ConnectivityManager::class.java)
                val kernelInterfaces = runCatching {
                    NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                }.getOrDefault(emptyList())

                if (cm != null) {
                    for (network in cm.allNetworks) {
                        val linkProperties = cm.getLinkProperties(network) ?: continue
                        val networkCapabilities = cm.getNetworkCapabilities(network) ?: continue
                        val ifaceName = linkProperties.interfaceName ?: continue
                        if (ifaceName.isBlank()) continue
                        val networkInterface = kernelInterfaces.find { it.name == ifaceName } ?: continue

                        val boxInterface = io.nekohasekai.libbox.NetworkInterface()
                        boxInterface.name = ifaceName
                        boxInterface.index = networkInterface.index
                        boxInterface.mtu = runCatching { networkInterface.mtu.takeIf { it > 0 } ?: 1500 }.getOrDefault(1500)

                        boxInterface.dnsServer = StringArray(
                            linkProperties.dnsServers.mapNotNull { it.hostAddress?.substringBefore('%') }
                                .filter { it.isNotBlank() }
                                .iterator(),
                        )
                        boxInterface.gateway = StringArray(
                            linkProperties.routes
                                .filter { it.destination.prefixLength == 0 }
                                .mapNotNull { it.gateway?.hostAddress?.substringBefore('%') }
                                .filter { it.isNotBlank() && it != "0.0.0.0" && it != "::" }
                                .iterator(),
                        )
                        boxInterface.type = when {
                            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Libbox.InterfaceTypeWIFI
                            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Libbox.InterfaceTypeCellular
                            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Libbox.InterfaceTypeEthernet
                            else -> Libbox.InterfaceTypeOther
                        }

                        val validPrefixes = networkInterface.interfaceAddresses
                            .mapNotNull { it.toValidPrefixOrNull() }
                        boxInterface.addresses = StringArray(validPrefixes.iterator())

                        var dumpFlags = 0
                        if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                            dumpFlags = OsConstants.IFF_UP or OsConstants.IFF_RUNNING
                        }
                        if (networkInterface.isLoopback) {
                            dumpFlags = dumpFlags or OsConstants.IFF_LOOPBACK
                        }
                        if (networkInterface.isPointToPoint) {
                            dumpFlags = dumpFlags or OsConstants.IFF_POINTOPOINT
                        }
                        if (networkInterface.supportsMulticast()) {
                            dumpFlags = dumpFlags or OsConstants.IFF_MULTICAST
                        }
                        boxInterface.flags = dumpFlags
                        boxInterface.metered = !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                        interfaces.add(boxInterface)
                    }
                }

                // Fallback if ConnectivityManager.allNetworks returned nothing
                if (interfaces.isEmpty()) {
                    for (iface in kernelInterfaces) {
                        if (!iface.isUp || iface.isLoopback || iface.name.startsWith("tun") || iface.name.startsWith("dummy")) {
                            continue
                        }
                        val validPrefixes = iface.interfaceAddresses.mapNotNull { it.toValidPrefixOrNull() }
                        if (validPrefixes.isEmpty()) continue
                        val box = io.nekohasekai.libbox.NetworkInterface()
                        box.name = iface.name
                        box.index = iface.index
                        box.mtu = runCatching { iface.mtu.takeIf { it > 0 } ?: 1500 }.getOrDefault(1500)
                        box.addresses = StringArray(validPrefixes.iterator())
                        box.gateway = StringArray(emptyList<String>().iterator())
                        box.dnsServer = StringArray(emptyList<String>().iterator())
                        box.type = Libbox.InterfaceTypeOther
                        var flags = OsConstants.IFF_UP or OsConstants.IFF_RUNNING
                        if (iface.isPointToPoint) flags = flags or OsConstants.IFF_POINTOPOINT
                        if (iface.supportsMulticast()) flags = flags or OsConstants.IFF_MULTICAST
                        box.flags = flags
                        box.metered = false
                        interfaces.add(box)
                    }
                }
            }.onFailure {
                AppLogger.e(TAG, "getInterfaces error", it)
            }
            return InterfaceArray(interfaces.iterator())
        }

        override fun underNetworkExtension(): Boolean = false

        override fun includeAllNetworks(): Boolean = false

        override fun clearDNSCache() = Unit

        override fun readWIFIState(): WIFIState? = null

        override fun localDNSTransport(): LocalDNSTransport? = null

        private var networkCallback: ConnectivityManager.NetworkCallback? = null

        override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) {
            listener ?: return
            val cm = runCatching {
                app.nebulabox.Application.instance.getSystemService(ConnectivityManager::class.java)
            }.getOrNull()

            fun notifyDefaultInterface(network: Network?) {
                monitorHandler.post {
                    runCatching {
                        if (network != null && cm != null) {
                            val caps = cm.getNetworkCapabilities(network)
                            if (caps != null && !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                                for (attempt in 0 until 5) {
                                    val lp = cm.getLinkProperties(network)
                                    val ifaceName = lp?.interfaceName
                                    if (!ifaceName.isNullOrBlank()) {
                                        val ni = runCatching { NetworkInterface.getByName(ifaceName) }.getOrNull()
                                        if (ni != null) {
                                            AppLogger.i(TAG, "Default interface updated: $ifaceName (index=${ni.index})")
                                            listener.updateDefaultInterface(ifaceName, ni.index, false, false)
                                            return@runCatching
                                        }
                                    }
                                    Thread.sleep(50)
                                }
                            }
                        }
                        // Fallback: find active non-VPN network from allNetworks
                        if (cm != null) {
                            for (net in cm.allNetworks) {
                                val caps = cm.getNetworkCapabilities(net) ?: continue
                                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
                                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
                                val lp = cm.getLinkProperties(net) ?: continue
                                val ifaceName = lp.interfaceName ?: continue
                                val ni = runCatching { NetworkInterface.getByName(ifaceName) }.getOrNull() ?: continue
                                AppLogger.i(TAG, "Default interface (fallback network): $ifaceName (index=${ni.index})")
                                listener.updateDefaultInterface(ifaceName, ni.index, false, false)
                                return@runCatching
                            }
                        }
                        // Second fallback: physical network interface
                        val fallback = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                            .firstOrNull { ni ->
                                ni.isUp && !ni.isLoopback && !ni.isPointToPoint &&
                                    !ni.name.startsWith("tun") && !ni.name.startsWith("ppp") &&
                                    !ni.name.startsWith("dummy")
                            }
                        if (fallback != null) {
                            AppLogger.i(TAG, "Default interface (kernel fallback): ${fallback.name} (index=${fallback.index})")
                            listener.updateDefaultInterface(fallback.name, fallback.index, false, false)
                        }
                    }.onFailure {
                        AppLogger.w(TAG, "notifyDefaultInterface warning", it)
                    }
                }
            }

            notifyDefaultInterface(cm?.activeNetwork)

            if (cm != null) {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                    .build()

                val cb = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        notifyDefaultInterface(network)
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities,
                    ) {
                        if (!networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                            notifyDefaultInterface(network)
                        }
                    }

                    override fun onLost(network: Network) {
                        notifyDefaultInterface(null)
                    }
                }
                networkCallback = cb
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        cm.registerBestMatchingNetworkCallback(request, cb, monitorHandler)
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        cm.requestNetwork(request, cb, monitorHandler)
                    } else {
                        cm.registerDefaultNetworkCallback(cb)
                    }
                }.onFailure {
                    AppLogger.w(TAG, "NetworkCallback registration fallback", it)
                    runCatching { cm.registerDefaultNetworkCallback(cb) }
                }
            }
        }

        override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) {
            val cb = networkCallback ?: return
            networkCallback = null
            runCatching {
                val cm = app.nebulabox.Application.instance.getSystemService(ConnectivityManager::class.java)
                cm?.unregisterNetworkCallback(cb)
            }
        }

        override fun startNeighborMonitor(listener: NeighborUpdateListener?) = Unit

        override fun closeNeighborMonitor(listener: NeighborUpdateListener?) = Unit

        override fun usePlatformShell(): Boolean = false

        override fun checkPlatformShell() {
            error("shell is not supported on Android")
        }

        override fun openShellSession(
            user: PlatformUser?,
            command: String?,
            environ: StringIterator?,
            term: String?,
            rows: Int,
            cols: Int,
        ): ShellSession {
            error("shell is not supported on Android")
        }

        override fun readSystemSSHHostKey(): String {
            error("not supported")
        }

        override fun lookupSFTPServer(): String {
            error("not supported")
        }

        override fun tailscaleHostname(): String = runCatching {
            Settings.Global.getString(
                app.nebulabox.Application.instance.contentResolver,
                Settings.Global.DEVICE_NAME,
            )?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: "${Build.MANUFACTURER} ${Build.MODEL}"

        override fun usePlatformBridge(): Boolean = false

        override fun createBridge(options: BridgeOptions?): BridgeSession {
            error("bridge mode requires root")
        }

        // IMPORTANT: Must NEVER return null; service.go dereferences platformUser.Username when err == nil!
        override fun lookupUser(username: String?): PlatformUser {
            val name = username?.takeIf { it.isNotBlank() } ?: error("empty username")
            val pm = app.nebulabox.Application.instance.packageManager
            val appInfo = runCatching { pm.getApplicationInfo(name, 0) }.getOrNull()
                ?: error("user not found: $name")
            return PlatformUser().apply {
                this.username = appInfo.packageName
                this.uid = appInfo.uid
                this.gid = appInfo.uid
                this.homeDir = appInfo.dataDir ?: "/data/user/0/${appInfo.packageName}"
            }
        }

        override fun registerMyInterface(name: String?) {
            if (!name.isNullOrBlank()) {
                AppLogger.i(TAG, "Registered TUN interface: $name")
            }
        }

        override fun sendNotification(notification: Notification?) = Unit

        override fun cancelNotification(identifier: String?, typeID: Int) = Unit

        /**
         * Safely converts an [InterfaceAddress] into a CIDR prefix for Go's `netip.MustParsePrefix`.
         * Returns null if the address or prefix length is invalid so `netip.MustParsePrefix` never panics.
         */
        private fun InterfaceAddress.toValidPrefixOrNull(): String? {
            val addr = address ?: return null
            val prefixLen = networkPrefixLength.toInt()
            return when (addr) {
                is Inet6Address -> {
                    if (prefixLen !in 0..128) return null
                    val rawHost = runCatching {
                        Inet6Address.getByAddress(addr.address).hostAddress
                    }.getOrNull() ?: addr.hostAddress ?: return null
                    val cleanHost = rawHost.substringBefore('%').trim()
                    if (cleanHost.isEmpty() || !cleanHost.contains(':')) return null
                    "$cleanHost/$prefixLen"
                }
                is Inet4Address -> {
                    if (prefixLen !in 0..32) return null
                    val cleanHost = (addr.hostAddress ?: return null).substringBefore('%').trim()
                    if (cleanHost.isEmpty() || !cleanHost.contains('.')) return null
                    "$cleanHost/$prefixLen"
                }
                else -> null
            }
        }
    }

    private class StringArray(private val iterator: Iterator<String>) : StringIterator {
        override fun len(): Int = 0
        override fun hasNext(): Boolean = iterator.hasNext()
        override fun next(): String = iterator.next()
    }

    private class InterfaceArray(
        private val iterator: Iterator<io.nekohasekai.libbox.NetworkInterface>,
    ) : NetworkInterfaceIterator {
        override fun hasNext(): Boolean = iterator.hasNext()
        override fun next(): io.nekohasekai.libbox.NetworkInterface = iterator.next()
    }

    companion object {
        private const val TAG = "LibboxEngine"
    }
}
