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

interface TunnelEngine {

    val status: MutableStateFlow<TunnelStatus>
    val groups: MutableStateFlow<List<OutboundGroup>>
    val logs: Flow<LogLine>

    val implementationName: String

    val functional: Boolean

    fun start(profileName: String, config: String, mtu: Int, openTun: () -> Boolean)

    fun stop()
    fun selectOutbound(groupTag: String, itemTag: String)
    fun urlTest(groupTag: String)
    fun clearLogs()

    fun measureOutboundDelay(config: String, testUrl: String): Long = -1L

    fun measureActiveDelay(testUrl: String): Long = -1L

    data class LogLine(val level: Int, val time: Long, val message: String)
}

interface TunProvider {

    fun tunFileDescriptor(): Int

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
