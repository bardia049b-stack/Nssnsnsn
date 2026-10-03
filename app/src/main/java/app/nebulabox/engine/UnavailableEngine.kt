package app.nebulabox.engine

import app.nebulabox.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class UnavailableEngine(private val detail: String?) : TunnelEngine {

    override val status = MutableStateFlow(TunnelStatus())
    override val groups = MutableStateFlow<List<OutboundGroup>>(emptyList())
    override val logs = MutableSharedFlow<TunnelEngine.LogLine>(extraBufferCapacity = 64)

    override val implementationName: String
        get() = "unavailable"

    override val functional: Boolean
        get() = false

    override fun start(
        profileName: String,
        config: String,
        mtu: Int,
        openTun: () -> Boolean,
    ) {
        status.value = TunnelStatus(
            state = TunnelState.STOPPED,
            profileName = profileName,
            message = detail ?: app.nebulabox.Application.instance.getString(R.string.error_no_engine),
        )
        throw IllegalStateException(status.value.message)
    }

    override fun stop() {
        status.value = TunnelStatus()
    }

    override fun selectOutbound(groupTag: String, itemTag: String) = Unit

    override fun urlTest(groupTag: String) = Unit

    override fun clearLogs() = Unit
}
