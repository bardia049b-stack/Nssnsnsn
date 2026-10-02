package app.nebulabox.engine

import android.annotation.SuppressLint
import android.os.Build
import android.system.OsConstants
import android.util.Log
import app.nebulabox.service.TunnelVpnService
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
import io.nekohasekai.libbox.ShellSession
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.SystemProxyStatus
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.net.Inet6Address
import java.net.InterfaceAddress
import java.net.NetworkInterface

/**
 * Drives the sing-box native core.
 * Compatible with sing-box libbox.aar v1.14.2.
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

    private val serverHandler = object : CommandServerHandler {
        override fun serviceStop() {
            status.value = status.value.copy(state = TunnelState.STOPPED)
        }
        override fun serviceReload() = Unit
        override fun getSystemProxyStatus(): SystemProxyStatus? = null
        override fun setSystemProxyEnabled(enabled: Boolean) = Unit
        override fun writeDebugMessage(message: String?) {
            Log.d(TAG, message ?: "")
        }
        override fun triggerNativeCrash() = Unit
        override fun connectSSHAgent(): Int = -1
    }

    private val clientHandler = object : CommandClientHandler {
        override fun connected() {
            status.value = status.value.copy(state = TunnelState.STARTED)
        }
        override fun disconnected(message: String?) {
            status.value = status.value.copy(
                state = TunnelState.STOPPED,
                message = message ?: "",
            )
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
                    items += GroupItem(tag = item.tag, type = item.type, delayMs = item.urlTestDelay)
                }
                parsed += OutboundGroup(
                    tag = group.tag, type = group.type, selected = group.selected,
                    selectable = group.selectable, items = items,
                )
            }
            groups.value = parsed
        }
        override fun writeOutbounds(items: OutboundGroupItemIterator?) = Unit
        override fun writeLogs(logIterator: LogIterator?) {
            logIterator ?: return
            while (logIterator.hasNext()) {
                val entry = logIterator.next()
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

    override fun start(profileName: String, config: String, mtu: Int, openTun: () -> Boolean) {
        openTunCallback = openTun
        status.value = TunnelStatus(state = TunnelState.STARTING, profileName = profileName)
        val platformInterface = Platform()
        platform = platformInterface
        val server = CommandServer(serverHandler, platformInterface)
        server.start()
        commandServer = server
        server.startOrReloadService(config, OverrideOptions())
        status.value = status.value.copy(
            state = TunnelState.STARTED,
            startedAt = System.currentTimeMillis(),
            message = "",
        )
        runCatching {
            val options = CommandClientOptions().apply {
                statusInterval = 1_000_000_000L
                addCommand(Libbox.CommandStatus)
                addCommand(Libbox.CommandLog)
                addCommand(Libbox.CommandGroup)
            }
            val client = CommandClient(clientHandler, options)
            client.connect()
            commandClient = client
        }.onFailure { Log.w(TAG, "status client unavailable", it) }
    }

    override fun stop() {
        runCatching { commandClient?.disconnect() }
        runCatching { commandClient?.serviceClose() }
        commandClient = null
        runCatching { commandServer?.closeService() }
        runCatching { commandServer?.close() }
        commandServer = null
        platform = null
        openTunCallback = null
        status.value = TunnelStatus()
        groups.value = emptyList()
    }

    override fun selectOutbound(groupTag: String, itemTag: String) {
        runCatching { commandClient?.selectOutbound(groupTag, itemTag) }
    }

    override fun urlTest(groupTag: String) {
        runCatching { commandClient?.urlTest(groupTag) }
    }

    override fun clearLogs() {
        runCatching { commandClient?.clearLogs() }
    }

    private inner class Platform : PlatformInterface {
        override fun usePlatformAutoDetectInterfaceControl(): Boolean = true
        override fun autoDetectInterfaceControl(fd: Int) {
            Engines.tunProvider?.protectSocket(fd)
        }
        override fun openTun(options: TunOptions): Int {
            val ready = openTunCallback?.invoke() ?: false
            if (!ready) error("tun interface was not established")
            val fd = Engines.tunProvider?.tunFileDescriptor() ?: -1
            if (fd < 0) error("no tun descriptor available")
            return fd
        }
        override fun useProcFS(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
        override fun findConnectionOwner(
            ipProtocol: Int, sourceAddress: String?, sourcePort: Int,
            destinationAddress: String?, destinationPort: Int,
        ): ConnectionOwner? = null
        override fun getInterfaces(): NetworkInterfaceIterator {
            val result = mutableListOf<io.nekohasekai.libbox.NetworkInterface>()
            val all = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            for (iface in all) {
                val box = io.nekohasekai.libbox.NetworkInterface()
                box.name = iface.name
                box.index = iface.index
                runCatching { box.mtu = iface.mtu }
                box.addresses = StringArray(iface.interfaceAddresses.map { it.toPrefix() }.iterator())
                box.gateway = StringArray(emptyList<String>().iterator())
                box.dnsServer = StringArray(emptyList<String>().iterator())
                box.type = Libbox.InterfaceTypeOther
                var flags = 0
                if (iface.isUp) flags = flags or OsConstants.IFF_UP or OsConstants.IFF_RUNNING
                if (iface.isLoopback) flags = flags or OsConstants.IFF_LOOPBACK
                if (iface.isPointToPoint) flags = flags or OsConstants.IFF_POINTOPOINT
                if (iface.supportsMulticast()) flags = flags or OsConstants.IFF_MULTICAST
                box.flags = flags
                box.metered = false
                result.add(box)
            }
            return InterfaceArray(result.iterator())
        }
        override fun underNetworkExtension(): Boolean = false
        override fun includeAllNetworks(): Boolean = false
        override fun clearDNSCache() = Unit
        override fun readWIFIState(): WIFIState? = null
        override fun localDNSTransport(): LocalDNSTransport? = null
        override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) = Unit
        override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) = Unit
        override fun startNeighborMonitor(listener: NeighborUpdateListener?) = Unit
        override fun closeNeighborMonitor(listener: NeighborUpdateListener?) = Unit
        override fun usePlatformShell(): Boolean = false
        override fun checkPlatformShell() {
            error("shell is not supported on Android")
        }
        override fun openShellSession(
            user: PlatformUser?, command: String?, environ: StringIterator?,
            term: String?, rows: Int, cols: Int,
        ): ShellSession {
            error("shell is not supported on Android")
        }
        override fun readSystemSSHHostKey(): String? = null
        override fun lookupSFTPServer(): String? = null
        override fun tailscaleHostname(): String? = null
        override fun usePlatformBridge(): Boolean = false
        override fun createBridge(options: BridgeOptions?): BridgeSession {
            error("bridge mode requires root")
        }
        override fun usePlatformAutoRedirect(): Boolean = false
        override fun lookupUser(username: String?): PlatformUser? = null
        override fun registerMyInterface(name: String?) = Unit
        override fun sendNotification(notification: Notification?) = Unit
        override fun cancelNotification(identifier: String?, typeID: Int) = Unit
        private fun InterfaceAddress.toPrefix(): String = if (address is Inet6Address) {
            "${Inet6Address.getByAddress(address.address).hostAddress}/$networkPrefixLength"
        } else {
            "${address.hostAddress}/$networkPrefixLength"
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

        @Volatile
        var service: TunnelVpnService? = null
    }
}
