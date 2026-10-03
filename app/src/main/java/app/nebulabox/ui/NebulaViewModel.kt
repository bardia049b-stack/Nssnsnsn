package app.nebulabox.ui

import android.app.Application
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.nebulabox.data.AppSettings
import app.nebulabox.data.Profile
import app.nebulabox.data.ProfileStore
import app.nebulabox.data.SettingsStore
import app.nebulabox.engine.Engines
import app.nebulabox.engine.OutboundGroup
import app.nebulabox.engine.TunnelEngine
import app.nebulabox.engine.TunnelState
import app.nebulabox.engine.TunnelStatus
import app.nebulabox.service.Actions
import app.nebulabox.util.AppLogger
import app.nebulabox.util.IpLocationChecker
import app.nebulabox.util.ShareLinkParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NebulaViewModel(
    private val application: Application,
    private val profileStore: ProfileStore,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    val profiles: StateFlow<List<Profile>> = profileStore.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<AppSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val status: StateFlow<TunnelStatus> = Engines.active
        .flatMapLatest { engine ->
            engine?.status ?: flowOf(TunnelStatus())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TunnelStatus())

    val groups: StateFlow<List<OutboundGroup>> = Engines.active
        .flatMapLatest { engine ->
            engine?.groups ?: flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val logs: MutableSharedFlow<TunnelEngine.LogLine> = MutableSharedFlow(extraBufferCapacity = 512)

    private val _endpointLocation = MutableStateFlow<IpLocationChecker.EndpointLocation?>(null)
    val endpointLocation: StateFlow<IpLocationChecker.EndpointLocation?> = _endpointLocation.asStateFlow()

    private val _checkingLocation = MutableStateFlow(false)
    val checkingLocation: StateFlow<Boolean> = _checkingLocation.asStateFlow()

    private var locationJob: Job? = null

    /** Profile currently being edited in the bottom sheet, if any. */
    var draftProfile: Profile? = null

    private val pendingImport = MutableStateFlow<String?>(null)
    private val importResult = MutableSharedFlow<ImportResult>(extraBufferCapacity = 8)
    val importResults = importResult.asSharedFlow()

    private val snack = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snacks = snack.asSharedFlow()

    /** Set while waiting for the user to approve the VPN permission dialog. */
    private val pendingConnectId = MutableStateFlow<String?>(null)

    init {
        // Mirrors the active engine's log stream into a buffer the UI can read.
        viewModelScope.launch {
            Engines.active
                .flatMapLatest { engine -> engine?.logs ?: emptyFlow() }
                .collect { logs.emit(it) }
        }

        // Automatically query connected Exit IP & Country when tunnel state becomes STARTED.
        viewModelScope.launch {
            status
                .map { it.state }
                .distinctUntilChanged()
                .collect { state ->
                    if (state == TunnelState.STARTED) {
                        fetchExitLocationWithRetry()
                    } else {
                        locationJob?.cancel()
                        _checkingLocation.value = false
                        _endpointLocation.value = null
                    }
                }
        }
    }

    val connected: Boolean get() = status.value.state == TunnelState.STARTED

    val activeEngine: TunnelEngine? get() = Engines.active.value

    // ----------------------------------------------------------- location

    fun refreshLocation() {
        if (status.value.state != TunnelState.STARTED) return
        fetchExitLocationWithRetry(initialDelayMs = 0L)
    }

    private fun fetchExitLocationWithRetry(initialDelayMs: Long = 700L) {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            _checkingLocation.value = true
            if (initialDelayMs > 0) delay(initialDelayMs)
            for (attempt in 1..3) {
                if (status.value.state != TunnelState.STARTED) break
                val loc = IpLocationChecker.fetchLocation()
                if (loc != null) {
                    _endpointLocation.value = loc
                    AppLogger.i("GeoIP", "Connected exit IP: ${loc.ip} (${loc.flagEmoji} ${loc.countryName}, ${loc.delayMs} ms)")
                    break
                }
                delay(1500L)
            }
            _checkingLocation.value = false
        }
    }

    // ----------------------------------------------------------- connecting

    fun connect(profile: Profile) {
        viewModelScope.launch {
            settingsStore.update { it.copy(selectedProfileId = profile.id) }
        }
        val intent = VpnService.prepare(application)
        if (intent != null) {
            pendingConnectId.value = profile.id
            viewModelScope.launch { vpnPermissionRequests.emit(intent) }
        } else {
            Actions.connect(application, profile.id)
        }
    }

    val vpnPermissionRequests = MutableSharedFlow<Intent>(extraBufferCapacity = 2)

    fun onVpnPermissionGranted() {
        val id = pendingConnectId.value ?: return
        pendingConnectId.value = null
        Actions.connect(application, id)
    }

    fun onVpnPermissionDenied() {
        pendingConnectId.value = null
        viewModelScope.launch { snack.emit("VPN permission denied") }
    }

    fun disconnect() {
        Actions.disconnect(application)
    }

    fun toggle(profile: Profile) {
        when (status.value.state) {
            TunnelState.STARTED, TunnelState.STARTING -> disconnect()
            TunnelState.STOPPING -> Unit
            TunnelState.STOPPED -> connect(profile)
        }
    }

    fun selectProfile(profile: Profile) {
        viewModelScope.launch {
            settingsStore.update { it.copy(selectedProfileId = profile.id) }
        }
    }

    // ------------------------------------------------------------- profiles

    fun saveProfile(profile: Profile) {
        viewModelScope.launch {
            profileStore.upsert(profile)
            val currentSettings = settingsStore.current()
            if (currentSettings.selectedProfileId.isNullOrBlank()) {
                settingsStore.update { it.copy(selectedProfileId = profile.id) }
            }
        }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch { profileStore.delete(id) }
    }

    fun moveProfile(from: Int, to: Int) {
        viewModelScope.launch { profileStore.move(from, to) }
    }

    /** Immediately parses and imports links or Custom JSON text. */
    fun submitImportText(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            importTextInternal(text)
        }
    }

    /** Kept for compatibility with MainActivity lifecycle calls. */
    suspend fun consumePendingImport() {
        val text = pendingImport.value ?: return
        pendingImport.value = null
        importTextInternal(text)
    }

    private suspend fun importTextInternal(text: String) {
        val parsed = runCatching { ShareLinkParser.parseMany(text) }.getOrDefault(emptyList())
        if (parsed.isEmpty()) {
            importResult.emit(ImportResult(0, text))
            return
        }
        profileStore.addAll(parsed)
        val currentSettings = settingsStore.current()
        if (currentSettings.selectedProfileId.isNullOrBlank()) {
            settingsStore.update { it.copy(selectedProfileId = parsed.first().id) }
        }
        importResult.emit(ImportResult(parsed.size, text))
    }

    fun exportProfiles(callback: (String) -> Unit) {
        viewModelScope.launch { callback(profileStore.exportJson()) }
    }

    // ------------------------------------------------------------- settings

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsStore.update(transform) }
    }

    // ---------------------------------------------------------------- misc

    fun selectOutbound(groupTag: String, itemTag: String) {
        Engines.active.value?.selectOutbound(groupTag, itemTag)
    }

    fun urlTest(groupTag: String) {
        Engines.active.value?.urlTest(groupTag)
    }

    fun clearLogs() {
        Engines.active.value?.clearLogs()
    }

    data class ImportResult(val count: Int, val raw: String)
}

class NebulaViewModelFactory(
    private val application: Application,
    private val profileStore: ProfileStore,
    private val settingsStore: SettingsStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        NebulaViewModel(application, profileStore, settingsStore) as T
}
