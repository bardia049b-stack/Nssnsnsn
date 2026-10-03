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
 * The contract between the UI and the native Xray-core (`libv2ray.aar`) tunnel engine.
 */
interface TunnelEngine {

    val status: MutableStateFlow<TunnelStatus>
    val groups: MutableStateFlow<List<OutboundGroup>>
    val logs: Flow<LogLine>

    /** Human readable name of the implementation, shown in the About & Home screens. */
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

    /**
     * Measures real HTTP delay (in ms) for an arbitrary Xray JSON config (no inbounds required),
     * backed by `Libv2ray.measureOutboundDelay(config, testUrl)`. Returns -1L on failure.
     */
    fun measureOutboundDelay(config: String, testUrl: String): Long = -1L

    /**
     * Measures real HTTP delay (in ms) through the currently running Xray instance,
     * backed by `CoreController.measureDelay(testUrl)`. Returns -1L on failure.
     */
    fun measureActiveDelay(testUrl: String): Long = -1L

    data class LogLine(val level: Int, val time: Long, val message: String)
}

interface TunProvider {
    /** Returns the raw TUN file descriptor. */
    fun tunFileDescriptor(): Int

    /** Routes a raw socket around the tunnel. */
    fun protectSocket(fd: Int): Boolean
}

object Engines {
    val active: MutableStateFlow<TunnelEngine?> = MutableStateFlow(null)

    @Volatile
    var tunProvider: TunProvider? = null

    fun obtain(): TunnelEngine {
        active.value?.let { return it }
        val engine = if (app.nebulabox.BuildConfig.HAS_ENGINE) {
            runCatching {
                val clazz = Class.forName("app.nebulabox.engine.LibboxEngine")
                clazz.getDeclaredConstructor().newInstance() as TunnelEngine
            }.getOrElse {
                UnavailableEngine(
                    "Xray-core present but failed to initialise: ${it.message}",
                )
            }
        } else {
            UnavailableEngine(null)
        }
        active.value = engine
        return engine
    }
}
