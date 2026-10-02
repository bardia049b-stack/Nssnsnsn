package app.nebulabox.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

enum class TunnelState { STOPPED, STARTING, STARTED, STOPPING }

data class TunnelStatus(
    val state: TunnelState = TunnelState.STOPPED,
    val profileName: String = "",
    val uplink: Long = 0,
    val downlink: Long = 0,
    val uplinkTotal: Long = 0,
    val downlinkTotal: Long = 0,
    val memory: Long = 0,
    val connectionsIn: Int = 0,
    val connectionsOut: Int = 0,
    val startedAt: Long = 0,
    val message: String = "",
)

data class OutboundGroup(
    val tag: String,
    val type: String,
    val selected: String,
    val selectable: Boolean,
    val items: List<GroupItem>,
)

data class GroupItem(
    val tag: String,
    val type: String,
    val delayMs: Int = -1,
)

/**
 * The contract between the UI and whatever drives the tunnel.
 *
 * Two implementations exist:
 *  - [LibboxEngine] in src/engine/java, backed by the sing-box native core.
 *    It is compiled in only when app/libs/libbox.aar is present.
 *  - [UnavailableEngine], compiled when the core is absent, which reports the
 *    situation honestly instead of pretending to tunnel.
 */
interface TunnelEngine {

    val status: MutableStateFlow<TunnelStatus>
    val groups: MutableStateFlow<List<OutboundGroup>>
    val logs: Flow<LogLine>

    /** Human readable name of the implementation, shown in the About screen. */
    val implementationName: String

    /** Whether this build can actually establish a tunnel. */
    val functional: Boolean

    /**
     * Starts the tunnel. [openTun] is called on a worker thread and must return
     * true once the Android VPN session exists and its descriptor is retrievable.
     */
    fun start(profileName: String, config: String, mtu: Int, openTun: () -> Boolean)

    fun stop()
    fun selectOutbound(groupTag: String, itemTag: String)
    fun urlTest(groupTag: String)
    fun clearLogs()

    data class LogLine(val level: Int, val time: Long, val message: String)
}

/**
 * The Android side of the tunnel contract, implemented by the VPN service.
 *
 * It lives in the engine package rather than referencing the service directly
 * so that the native implementation stays compilable in isolation.
 */
interface TunProvider {
    /** Detaches the tun descriptor; ownership passes to the caller. */
    fun tunFileDescriptor(): Int

    /** Routes a raw socket around the tunnel. */
    fun protectSocket(fd: Int): Boolean
}

/**
 * Shared handle so the UI can observe tunnel state without binding to the
 * service. The service publishes the active engine here on start.
 */
object Engines {
    val active: MutableStateFlow<TunnelEngine?> = MutableStateFlow(null)

    /** Set by the VPN service while it is alive. */
    @Volatile
    var tunProvider: TunProvider? = null

    /**
     * Resolves which engine this APK contains. Reflection is used so that the
     * app module does not need the native core on the compile classpath when it
     * has not been built yet.
     */
    fun obtain(): TunnelEngine {
        active.value?.let { return it }
        val engine = if (app.nebulabox.BuildConfig.HAS_ENGINE) {
            runCatching {
                val clazz = Class.forName("app.nebulabox.engine.LibboxEngine")
                clazz.getDeclaredConstructor().newInstance() as TunnelEngine
            }.getOrElse {
                UnavailableEngine(
                    "libbox present but failed to initialise: ${it.message}",
                )
            }
        } else {
            UnavailableEngine(null)
        }
        active.value = engine
        return engine
    }
}
