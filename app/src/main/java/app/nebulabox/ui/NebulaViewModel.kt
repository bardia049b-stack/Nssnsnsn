package app.nebulabox.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.nebulabox.BuildConfig
import app.nebulabox.automation.AutomationNotifications
import app.nebulabox.automation.AutomationScheduler
import app.nebulabox.automation.ReleaseChecker
import app.nebulabox.config.ConfigBuilder
import app.nebulabox.data.AppSettings
import app.nebulabox.data.Profile
import app.nebulabox.data.ProfileStore
import app.nebulabox.data.Protocol
import app.nebulabox.data.SettingsStore
import app.nebulabox.data.SubscriptionItem
import app.nebulabox.engine.Engines
import app.nebulabox.engine.OutboundGroup
import app.nebulabox.engine.TunnelEngine
import app.nebulabox.engine.TunnelState
import app.nebulabox.engine.TunnelStatus
import app.nebulabox.R
import app.nebulabox.service.Actions
import app.nebulabox.util.AppLogger
import app.nebulabox.util.IpLocationChecker
import app.nebulabox.util.ShareLinkParser
import app.nebulabox.util.ConnectionProbe
import app.nebulabox.util.SubscriptionUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL

class NebulaViewModel(
    private val application: Application,
    private val profileStore: ProfileStore,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    val profiles: StateFlow<List<Profile>> = profileStore.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subscriptions: StateFlow<List<SubscriptionItem>> = profileStore.subscriptions
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

    private val _activeDelayMs = MutableStateFlow<Long?>(null)
    val activeDelayMs: StateFlow<Long?> = _activeDelayMs.asStateFlow()

    private val _activeTestError = MutableStateFlow<String?>(null)
    val activeTestError: StateFlow<String?> = _activeTestError.asStateFlow()

    private val _testingProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val testingProgress: StateFlow<Pair<Int, Int>?> = _testingProgress.asStateFlow()

    private val _testingProfileIds = MutableStateFlow<Set<String>>(emptySet())
    val testingProfileIds: StateFlow<Set<String>> = _testingProfileIds.asStateFlow()

    private val _updatingSubscriptions = MutableStateFlow(false)
    val updatingSubscriptions: StateFlow<Boolean> = _updatingSubscriptions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val customConfigPingMutex = Mutex()

    private var locationJob: Job? = null
    private var batchTestJob: Job? = null

    var draftProfile: Profile? = null

    private val pendingImport = MutableStateFlow<String?>(null)
    private val importResult = MutableSharedFlow<ImportResult>(extraBufferCapacity = 8)
    val importResults = importResult.asSharedFlow()

    private val snack = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snacks = snack.asSharedFlow()

    private val pendingConnectId = MutableStateFlow<String?>(null)

    private var healthJob: Job? = null
    private var autoPingJob: Job? = null

    private val _connectionHealth = MutableStateFlow(ConnectionHealth())
    val connectionHealth: StateFlow<ConnectionHealth> = _connectionHealth.asStateFlow()

    private val _availableUpdate = MutableStateFlow<ReleaseChecker.UpdateInfo?>(null)
    val availableUpdate: StateFlow<ReleaseChecker.UpdateInfo?> = _availableUpdate.asStateFlow()

    init {
        Engines.obtain()

        viewModelScope.launch {
            Engines.active
                .flatMapLatest { engine -> engine?.logs ?: emptyFlow() }
                .collect { logs.emit(it) }
        }

        viewModelScope.launch {
            status
                .map { it.state }
                .distinctUntilChanged()
                .collect { state ->
                    if (state == TunnelState.STARTED) {
                        _activeTestError.value = null
                        fetchExitLocationQuietly()
                        verifyConnection()
                        startAutoPing()
                    } else {
                        healthJob?.cancel()
                        autoPingJob?.cancel()
                        _connectionHealth.value = ConnectionHealth()
                        locationJob?.cancel()
                        _checkingLocation.value = false
                        _endpointLocation.value = null
                        _activeDelayMs.value = null
                        _activeTestError.value = null
                    }
                }
        }

        viewModelScope.launch {
            delay(4000L)
            val info = withContext(Dispatchers.IO) { ReleaseChecker.latestRelease() } ?: return@launch
            if (ReleaseChecker.isNewer(info)) {
                _availableUpdate.value = info
            }
        }
    }

    val connected: Boolean get() = status.value.state == TunnelState.STARTED

    val activeEngine: TunnelEngine? get() = Engines.active.value

    fun showSnack(message: String) {
        viewModelScope.launch { snack.emit(message) }
    }

    private fun launchLoading(block: suspend () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                block()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshLocation() {
        if (status.value.state != TunnelState.STARTED) return
        testActiveConnectionDelay()
    }

    fun testActiveConnectionDelay() {
        val currentSettings = settings.value.normalized()
        val selected = profiles.value.firstOrNull { it.id == currentSettings.selectedProfileId }
            ?: profiles.value.firstOrNull()
        if (status.value.state != TunnelState.STARTED) {
            if (selected != null) {
                testSingleProfileRealPing(selected)
            } else {
                showSnack(application.getString(R.string.no_server_selected))
            }
            return
        }

        locationJob?.cancel()
        locationJob = viewModelScope.launch(Dispatchers.IO) {
            _checkingLocation.value = true
            _activeTestError.value = null
            try {
                val engine = Engines.obtain()
                val url = currentSettings.delayTestUrl.ifBlank { "https://www.gstatic.com/generate_204" }

                val (delayMs, loc) = coroutineScope {
                    val delayDeferred = async { engine.measureActiveDelay(url) }
                    val locDeferred = async { IpLocationChecker.fetchLocation(currentSettings.socksPort) }
                    delayDeferred.await() to locDeferred.await()
                }

                if (delayMs > 0L) {
                    _activeDelayMs.value = delayMs
                    _activeTestError.value = null
                    if (selected != null) {
                        profileStore.updateDelays(mapOf(selected.id to delayMs.toInt()))
                    }
                } else if (loc != null && loc.delayMs > 0L) {
                    _activeDelayMs.value = loc.delayMs
                    _activeTestError.value = null
                    if (selected != null) {
                        profileStore.updateDelays(mapOf(selected.id to loc.delayMs.toInt()))
                    }
                } else {
                    _activeDelayMs.value = -1L
                    _activeTestError.value = "Timeout"
                }

                if (loc != null) {
                    val finalDelay = if (delayMs > 0L) delayMs else loc.delayMs
                    _endpointLocation.value = loc.copy(delayMs = finalDelay)
                }
            } finally {
                _checkingLocation.value = false
            }
        }
    }

    private fun fetchExitLocationQuietly() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch(Dispatchers.IO) {
            delay(800L)
            if (status.value.state != TunnelState.STARTED) return@launch
            val currentSettings = settingsStore.current().normalized()
            val engine = Engines.obtain()
            val url = currentSettings.delayTestUrl.ifBlank { "https://www.gstatic.com/generate_204" }

            val (delayMs, loc) = coroutineScope {
                val delayDeferred = async { engine.measureActiveDelay(url) }
                val locDeferred = async { IpLocationChecker.fetchLocation(currentSettings.socksPort) }
                delayDeferred.await() to locDeferred.await()
            }

            if (status.value.state != TunnelState.STARTED) return@launch

            if (delayMs > 0L) {
                _activeDelayMs.value = delayMs
                currentSettings.selectedProfileId?.let { id ->
                    profileStore.updateDelays(mapOf(id to delayMs.toInt()))
                }
            }
            if (loc != null) {
                val finalDelay = if (delayMs > 0L) delayMs else loc.delayMs
                if (_activeDelayMs.value == null && finalDelay > 0L) {
                    _activeDelayMs.value = finalDelay
                }
                _endpointLocation.value = loc.copy(delayMs = finalDelay)
                AppLogger.i("GeoIP", "Connected exit IP: ${loc.ip} (${loc.flagEmoji} ${loc.countryName}, ${finalDelay} ms)")
            }
        }
    }

    private suspend fun runSingleRealPing(profile: Profile, settings: AppSettings): Int {
        if (profile.protocol != Protocol.CUSTOM &&
            profile.protocol != Protocol.HYSTERIA2 &&
            profile.protocol != Protocol.TUIC &&
            profile.protocol != Protocol.WIREGUARD &&
            profile.tls.alpn.firstOrNull()?.startsWith("h3") != true &&
            profile.server.isNotBlank() &&
            profile.serverPort > 0
        ) {
            val tcpTime = socketConnectTime(profile.server, profile.serverPort, 1500)
            if (tcpTime <= -1) {
                return -1
            }
        }

        val config = runCatching { ConfigBuilder.buildForSpeedtest(profile, settings) }.getOrNull()
            ?: return -1
        val testUrl = settings.delayTestUrl.ifBlank { "https://www.gstatic.com/generate_204" }
        val engine = Engines.obtain()
        val delayMs = if (profile.protocol == Protocol.CUSTOM) {
            customConfigPingMutex.withLock {
                engine.measureOutboundDelay(config, testUrl)
            }
        } else {
            engine.measureOutboundDelay(config, testUrl)
        }
        return if (delayMs > 0L) delayMs.toInt() else -1
    }

    fun testSingleProfileRealPing(profile: Profile) {
        viewModelScope.launch(Dispatchers.IO) {
            _testingProfileIds.value = _testingProfileIds.value + profile.id
            try {
                val currentSettings = settingsStore.current().normalized()
                val delayMs = runSingleRealPing(profile, currentSettings)
                profileStore.updateDelays(mapOf(profile.id to delayMs))
                notifySlowProfile(profile, delayMs, currentSettings)
                if (delayMs > 0) {
                    snack.emit("${profile.displayName}: $delayMs ms")
                } else {
                    snack.emit("${profile.displayName}: -1 ms")
                }
            } finally {
                _testingProfileIds.value = _testingProfileIds.value - profile.id
            }
        }
    }

    private fun notifySlowProfile(profile: Profile, delayMs: Int, currentSettings: AppSettings) {
        val threshold = currentSettings.slowServerThresholdMs.coerceIn(100, 5000)
        if (!currentSettings.notifySlowServers || delayMs < threshold) return
        val notificationId = 3000 + kotlin.math.abs(profile.id.hashCode() % 1_000_000)
        AutomationNotifications.show(
            context = application,
            notificationId = notificationId,
            title = application.getString(R.string.slow_server),
            message = "${profile.displayName}: $delayMs ms (threshold $threshold ms)",
        )
    }

    private fun startAutoPing() {
        if (autoPingJob?.isActive == true) return
        autoPingJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val minutes = settingsStore.current().normalized().autoPingMinutes
                if (minutes <= 0) return@launch
                delay(minutes * 60_000L)
                if (status.value.state != TunnelState.STARTED) continue
                if (_testingProgress.value != null) continue
                if (!onUnmeteredNetwork()) continue
                pingStaleServers(minutes)
            }
        }
    }

    private fun onUnmeteredNetwork(): Boolean {
        val manager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        val wifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        return wifi || capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private suspend fun pingStaleServers(minutes: Int) {
        val cutoff = System.currentTimeMillis() - minutes * 60_000L
        val targets = profiles.value
            .filter { it.lastTestedAt < cutoff }
            .filter { it.server.isNotBlank() && it.serverPort > 0 }
            .filter { it.protocol != Protocol.CUSTOM && it.protocol != Protocol.WIREGUARD }
            .take(30)
        if (targets.isEmpty()) return
        val semaphore = Semaphore(6)
        coroutineScope {
            targets.map { profile ->
                async {
                    semaphore.withPermit {
                        _testingProfileIds.value = _testingProfileIds.value + profile.id
                        val delayMs = socketConnectTime(profile.server, profile.serverPort, 1500)
                        _testingProfileIds.value = _testingProfileIds.value - profile.id
                        profileStore.updateDelays(mapOf(profile.id to delayMs))
                    }
                }
            }.awaitAll()
        }
    }

    fun cancelAllPing() {
        batchTestJob?.cancel()
        batchTestJob = null
        _testingProgress.value = null
        _testingProfileIds.value = emptySet()
    }

    fun testAllRealPing() {
        if (_testingProgress.value != null) {
            cancelAllPing()
            return
        }

        batchTestJob = viewModelScope.launch(Dispatchers.IO) {
            val currentSettings = settingsStore.current().normalized()
            val subFilter = currentSettings.selectedSubscriptionId
            val targetList = profiles.value.filter {
                if (subFilter.isBlank()) it.subscriptionId.isBlank() else it.subscriptionId == subFilter
            }
            if (targetList.isEmpty()) {
                snack.emit(application.getString(R.string.no_profiles_to_test))
                return@launch
            }

            val total = targetList.size
            var completed = 0
            _testingProgress.value = 0 to total

            val semaphore = Semaphore(6)

            try {
                coroutineScope {
                    targetList.map { profile ->
                        async {
                            semaphore.withPermit {
                                _testingProfileIds.value = _testingProfileIds.value + profile.id
                                val delayMs = try {
                                    runSingleRealPing(profile, currentSettings)
                                } catch (_: Throwable) {
                                    -1
                                } finally {
                                    _testingProfileIds.value = _testingProfileIds.value - profile.id
                                }

                                profileStore.updateDelays(mapOf(profile.id to delayMs))
                                notifySlowProfile(profile, delayMs, currentSettings)
                                synchronized(this@NebulaViewModel) {
                                    completed++
                                    _testingProgress.value = completed to total
                                }
                            }
                        }
                    }.awaitAll()
                }
            } finally {
                _testingProgress.value = null
                _testingProfileIds.value = emptySet()
            }
        }
    }

    fun testAllTcpPing() {
        if (_testingProgress.value != null) {
            cancelAllPing()
            return
        }

        batchTestJob = viewModelScope.launch(Dispatchers.IO) {
            val currentSettings = settingsStore.current().normalized()
            val subFilter = currentSettings.selectedSubscriptionId
            val targetList = profiles.value.filter {
                if (subFilter.isBlank()) it.subscriptionId.isBlank() else it.subscriptionId == subFilter
            }
            if (targetList.isEmpty()) return@launch

            val total = targetList.size
            var completed = 0
            _testingProgress.value = 0 to total
            val semaphore = Semaphore(12)

            try {
                coroutineScope {
                    targetList.map { profile ->
                        async {
                            semaphore.withPermit {
                                _testingProfileIds.value = _testingProfileIds.value + profile.id
                                val delayMs = if (profile.protocol != Protocol.CUSTOM &&
                                    profile.protocol != Protocol.HYSTERIA2 &&
                                    profile.protocol != Protocol.WIREGUARD &&
                                    profile.server.isNotBlank() &&
                                    profile.serverPort > 0
                                ) {
                                    socketConnectTime(profile.server, profile.serverPort, 1500)
                                } else {
                                    -1
                                }
                                _testingProfileIds.value = _testingProfileIds.value - profile.id
                                profileStore.updateDelays(mapOf(profile.id to delayMs))
                                notifySlowProfile(profile, delayMs, currentSettings)
                                synchronized(this@NebulaViewModel) {
                                    completed++
                                    _testingProgress.value = completed to total
                                }
                            }
                        }
                    }.awaitAll()
                }
            } finally {
                _testingProgress.value = null
                _testingProfileIds.value = emptySet()
            }
        }
    }

    private fun socketConnectTime(host: String, port: Int, timeoutMs: Int = 1500): Int {
        var socket: Socket? = null
        val start = System.currentTimeMillis()
        return try {
            socket = Socket()
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            (System.currentTimeMillis() - start).toInt().coerceAtLeast(1)
        } catch (_: Throwable) {
            -1
        } finally {
            runCatching { socket?.close() }
        }
    }

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
        viewModelScope.launch { snack.emit(application.getString(R.string.vpn_permission_denied)) }
    }

    fun disconnect() {
        Actions.disconnect(application)
    }

    fun restartTunnel() {
        val selectedId = settings.value.selectedProfileId
            ?: profiles.value.firstOrNull()?.id
            ?: return
        viewModelScope.launch {
            if (status.value.state != TunnelState.STOPPED) {
                Actions.disconnect(application)
                delay(450L)
            }
            Actions.connect(application, selectedId)
            snack.emit(application.getString(R.string.restarting_service))
        }
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
            val state = status.value.state
            val running = state == TunnelState.STARTED || state == TunnelState.STARTING
            settingsStore.update { it.copy(selectedProfileId = profile.id) }
            if (running) {
                snack.emit(application.getString(R.string.switching_server, profile.displayName))
                Actions.disconnect(application)
                withTimeoutOrNull(6000) { status.first { it.state == TunnelState.STOPPED } }
                Actions.connect(application, profile.id)
            }
        }
    }

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
        viewModelScope.launch {
            profileStore.delete(id)
            val currentSettings = settingsStore.current()
            if (currentSettings.selectedProfileId == id) {
                val replacement = profileStore.all().firstOrNull()?.id
                settingsStore.update { it.copy(selectedProfileId = replacement) }
            }
        }
    }

    fun deleteProfiles(ids: Set<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            profileStore.deleteAll(ids)
            val currentSettings = settingsStore.current()
            if (currentSettings.selectedProfileId?.let { it in ids } == true) {
                val replacement = profileStore.all().firstOrNull()?.id
                settingsStore.update { it.copy(selectedProfileId = replacement) }
            }
            snack.emit(application.getString(R.string.removed_count, ids.size))
        }
    }

    fun duplicateProfile(profile: Profile) {
        val usedNames = profiles.value.mapTo(HashSet()) { it.displayName }
        val baseName = "${profile.displayName} (copy)"
        var newName = baseName
        var suffix = 2
        while (newName in usedNames) {
            newName = "${profile.displayName} (copy $suffix)"
            suffix++
        }
        val duplicate = profile.copy(
            id = ShareLinkParser.newId(),
            name = newName,
            subscriptionId = "",
            subscriptionUrl = "",
            order = 0,
            lastTestedAt = 0L,
            lastDelayMs = 0,
        )
        viewModelScope.launch {
            profileStore.upsert(duplicate)
            settingsStore.update {
                it.copy(selectedSubscriptionId = "", selectedProfileId = duplicate.id)
            }
            snack.emit(application.getString(R.string.duplicated_to_all))
        }
    }

    fun moveProfile(from: Int, to: Int) {
        viewModelScope.launch { profileStore.move(from, to) }
    }

    fun sortByTestResults() {
        launchLoading {
            withContext(Dispatchers.IO) {
                profileStore.sortByTestResults()
            }
            snack.emit(application.getString(R.string.sorted_by_results))
        }
    }

    fun removeDuplicateProfiles() {
        launchLoading {
            val count = withContext(Dispatchers.IO) {
                profileStore.removeDuplicates()
            }
            snack.emit(application.getString(R.string.removed_duplicates_count, count))
        }
    }

    fun removeInvalidProfiles() {
        launchLoading {
            val count = withContext(Dispatchers.IO) {
                profileStore.removeInvalid()
            }
            if (count < 0) {
                snack.emit(application.getString(R.string.remove_invalid_all_failed))
            } else {
                snack.emit(application.getString(R.string.removed_invalid_count, count))
            }
        }
    }

    fun deleteAllProfiles() {
        launchLoading {
            withContext(Dispatchers.IO) {
                val subFilter = settingsStore.current().selectedSubscriptionId
                profileStore.clearGroup(subFilter)
            }
            snack.emit(application.getString(R.string.removed_group_all))
        }
    }

    fun exportAllShareLinks(callback: (String) -> Unit) {
        launchLoading {
            val text = withContext(Dispatchers.IO) {
                val subFilter = settings.value.selectedSubscriptionId
                val list = profileStore.all().filter {
                    val matchGroup = if (subFilter.isBlank()) it.subscriptionId.isBlank() else it.subscriptionId == subFilter
                    matchGroup && it.protocol != Protocol.CUSTOM
                }
                list.joinToString("\n") { ShareLinkParser.toShareUri(it) }
            }
            callback(text)
        }
    }

    fun submitImportText(text: String) {
        if (text.isBlank()) return
        launchLoading {
            withContext(Dispatchers.IO) {
                importTextInternal(text)
            }
        }
    }

    suspend fun consumePendingImport() {
        val text = pendingImport.value ?: return
        pendingImport.value = null
        withContext(Dispatchers.IO) {
            importTextInternal(text)
        }
    }

    private suspend fun importTextInternal(text: String) {
        val trimmed = text.trim()

        if ((trimmed.startsWith("https://", true) || trimmed.startsWith("http://", true)) &&
            !trimmed.contains("\n") && !trimmed.substringAfter("://").substringBefore("/").contains("@")
        ) {
            val fetched = fetchUrlContent(trimmed)
            if (!fetched.isNullOrBlank()) {
                val parsedSub = runCatching { ShareLinkParser.parseMany(fetched) }.getOrDefault(emptyList())
                if (parsedSub.isNotEmpty()) {
                    val hostName = runCatching { URL(trimmed).host }.getOrDefault(application.getString(R.string.subscription_label))
                    val subItem = SubscriptionItem(
                        id = ShareLinkParser.newId(),
                        remarks = hostName,
                        url = trimmed,
                        enabled = true,
                        updatedAt = System.currentTimeMillis(),
                    )
                    profileStore.upsertSubscription(subItem)
                    profileStore.replaceSubscriptionProfiles(subItem.id, subItem.url, parsedSub)
                    settingsStore.update {
                        it.copy(
                            selectedSubscriptionId = subItem.id,
                            selectedProfileId = if (it.selectedProfileId.isNullOrBlank()) parsedSub.first().id else it.selectedProfileId,
                        )
                    }
                    importResult.emit(ImportResult(parsedSub.size, text))
                    return
                }
            }
        }

        val parsed = runCatching { ShareLinkParser.parseMany(text) }.getOrDefault(emptyList())
            .map {
                it.copy(subscriptionId = "", subscriptionUrl = "", lastDelayMs = 0, lastTestedAt = 0L)
            }
        if (parsed.isEmpty()) {
            importResult.emit(ImportResult(0, text))
            return
        }
        profileStore.addAll(parsed)
        settingsStore.update {
            it.copy(
                selectedSubscriptionId = "",
                selectedProfileId = if (it.selectedProfileId.isNullOrBlank()) parsed.first().id else it.selectedProfileId,
            )
        }
        importResult.emit(ImportResult(parsed.size, text))
    }

    fun exportProfiles(callback: (String) -> Unit) {
        viewModelScope.launch { callback(profileStore.exportJson()) }
    }

    fun selectSubscriptionFilter(subId: String) {
        viewModelScope.launch {
            settingsStore.update { it.copy(selectedSubscriptionId = subId) }
        }
    }

    fun addOrUpdateSubscription(id: String?, remarks: String, url: String) {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) return
        val subId = id ?: ShareLinkParser.newId()
        val name = remarks.trim().ifBlank {
            runCatching { URL(cleanUrl).host }.getOrDefault(application.getString(R.string.subscription_label))
        }
        launchLoading {
            _updatingSubscriptions.value = true
            try {
                withContext(Dispatchers.IO) {
                    val sub = SubscriptionItem(
                        id = subId,
                        remarks = name,
                        url = cleanUrl,
                        enabled = true,
                        updatedAt = System.currentTimeMillis(),
                    )
                    profileStore.upsertSubscription(sub)
                    val fetched = fetchSubscription(cleanUrl)
                    val body = fetched.body
                    if (!body.isNullOrBlank()) {
                        profileStore.upsertSubscription(SubscriptionUsage.applyTo(sub, fetched.quota))
                    }
                    if (body.isNullOrBlank()) {
                        snack.emit(application.getString(R.string.subscription_fetch_failed, name))
                        return@withContext
                    }
                    val parsed = runCatching { ShareLinkParser.parseMany(body) }.getOrDefault(emptyList())
                    if (parsed.isNotEmpty()) {
                        profileStore.replaceSubscriptionProfiles(sub.id, sub.url, parsed)
                        settingsStore.update {
                            it.copy(
                                selectedSubscriptionId = sub.id,
                                selectedProfileId = if (it.selectedProfileId.isNullOrBlank()) parsed.first().id else it.selectedProfileId,
                            )
                        }
                        snack.emit(
                            application.getString(R.string.subscription_imported, name, parsed.size),
                        )
                    } else {
                        snack.emit(application.getString(R.string.subscription_empty, name))
                    }
                }
            } finally {
                _updatingSubscriptions.value = false
            }
        }
    }

    fun updateAllSubscriptions() {
        if (_updatingSubscriptions.value) return
        launchLoading {
            _updatingSubscriptions.value = true
            try {
                withContext(Dispatchers.IO) {
                    val subs = profileStore.allSubscriptions().filter { it.enabled }
                    if (subs.isEmpty()) {
                        snack.emit(application.getString(R.string.subscriptions_none))
                        return@withContext
                    }
                    var updatedSubs = 0
                    var totalProfiles = 0
                    for (sub in subs) {
                        val fetched = fetchSubscription(sub.url)
                        val body = fetched.body ?: continue
                        val parsed = runCatching { ShareLinkParser.parseMany(body) }.getOrDefault(emptyList())
                        if (parsed.isNotEmpty()) {
                            profileStore.replaceSubscriptionProfiles(sub.id, sub.url, parsed)
                            profileStore.upsertSubscription(
                                SubscriptionUsage.applyTo(
                                    sub.copy(updatedAt = System.currentTimeMillis()),
                                    fetched.quota,
                                ),
                            )
                            updatedSubs++
                            totalProfiles += parsed.size
                        } else {
                            profileStore.upsertSubscription(SubscriptionUsage.applyTo(sub, fetched.quota))
                        }
                    }
                    snack.emit(
                        application.getString(
                            R.string.subscriptions_updated,
                            updatedSubs,
                            subs.size,
                            totalProfiles,
                        ),
                    )
                }
            } finally {
                _updatingSubscriptions.value = false
            }
        }
    }

    fun deleteSubscription(subId: String) {
        viewModelScope.launch {
            profileStore.deleteSubscription(subId, removeProfiles = true)
            if (settings.value.selectedSubscriptionId == subId) {
                settingsStore.update { it.copy(selectedSubscriptionId = "") }
            }
            snack.emit(application.getString(R.string.subscription_deleted))
        }
    }

    private data class SubscriptionFetch(val body: String?, val quota: SubscriptionUsage.Quota?)

    private suspend fun fetchSubscription(urlStr: String): SubscriptionFetch = withContext(Dispatchers.IO) {
        val currentSettings = settingsStore.current().normalized()
        val proxy = if (status.value.state == TunnelState.STARTED) {
            Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", currentSettings.socksPort))
        } else {
            Proxy.NO_PROXY
        }
        if (proxy == Proxy.NO_PROXY) {
            httpFetchWithQuota(urlStr, proxy) ?: SubscriptionFetch(null, null)
        } else {
            httpFetchWithQuota(urlStr, proxy)
                ?: httpFetchWithQuota(urlStr, Proxy.NO_PROXY)
                ?: SubscriptionFetch(null, null)
        }
    }

    private fun httpFetchWithQuota(urlStr: String, proxy: Proxy): SubscriptionFetch? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection(proxy) as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 15000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "JavidTun/${BuildConfig.VERSION_NAME}")
                setRequestProperty("Accept", "*/*")
            }
            if (conn.responseCode !in 200..299) return null
            val headers = conn.headerFields
                .filterKeys { it != null }
                .mapValues { entry -> entry.value ?: emptyList<String>() }
            val quota = SubscriptionUsage.parse(headers)
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            SubscriptionFetch(body, quota)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private suspend fun fetchUrlContent(urlStr: String): String? = withContext(Dispatchers.IO) {
        val currentSettings = settingsStore.current().normalized()
        if (status.value.state == TunnelState.STARTED) {
            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", currentSettings.socksPort))
            httpFetch(urlStr, proxy)?.let { return@withContext it }
        }
        httpFetch(urlStr, Proxy.NO_PROXY)
    }

    private fun httpFetch(urlStr: String, proxy: Proxy): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection(proxy) as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 15000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "JavidTun/2.1.0")
                setRequestProperty("Accept", "*/*")
            }
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    fun refreshSubscription(subId: String) {
        if (subId.isBlank()) return
        viewModelScope.launch {
            val sub = subscriptions.value.firstOrNull { it.id == subId } ?: return@launch
            withContext(Dispatchers.IO) {
                val fetched = fetchSubscription(sub.url)
                val quota = fetched.quota
                if (quota == null) return@withContext
                var updated = SubscriptionUsage.applyTo(sub, quota)
                val body = fetched.body
                if (!body.isNullOrBlank()) {
                    val parsed = runCatching { ShareLinkParser.parseMany(body) }.getOrDefault(emptyList())
                    if (parsed.isNotEmpty()) {
                        profileStore.replaceSubscriptionProfiles(sub.id, sub.url, parsed)
                        updated = updated.copy(updatedAt = System.currentTimeMillis())
                    }
                }
                profileStore.upsertSubscription(updated)
            }
            realignSelectionAfterRefresh(subId)
            if (!settings.value.selectedProfileId.isNullOrBlank()) {
                verifyConnection()
            }
        }
    }

    /**
     * A refresh can drop the server the user is on. Whatever is left is picked by name so the panel
     * and the tunnel do not end up pointing at two different servers.
     */
    private suspend fun realignSelectionAfterRefresh(subId: String) {
        val all = profiles.value
        val selectedId = settingsStore.current().selectedProfileId
        if (selectedId != null && all.any { it.id == selectedId }) return
        val remaining = all.filter { it.subscriptionId == subId }
        if (remaining.isEmpty()) return
        val replacement = remaining.first().id
        AppLogger.i("NebulaViewModel", "The selected server was gone after the refresh, the selection moves to $replacement")
        settingsStore.update { it.copy(selectedProfileId = replacement) }
    }

    private fun verifyConnection() {
        healthJob?.cancel()
        healthJob = viewModelScope.launch(Dispatchers.IO) {
            _connectionHealth.value = ConnectionHealth(phase = HealthPhase.CHECKING)
            val currentSettings = settingsStore.current().normalized()
            val selected = profiles.value.firstOrNull { it.id == currentSettings.selectedProfileId }
                ?: profiles.value.firstOrNull()
            val subscription = selected
                ?.takeIf { it.subscriptionId.isNotBlank() }
                ?.let { profile -> subscriptions.value.firstOrNull { it.id == profile.subscriptionId } }

            val result = ConnectionProbe.verifyThroughProxy(
                proxyPort = currentSettings.socksPort,
                testUrls = listOf(
                    currentSettings.delayTestUrl.ifBlank { "https://www.gstatic.com/generate_204" },
                    "http://cp.cloudflare.com/generate_204",
                ),
            )

            val exhausted = subscription != null && (subscription.isQuotaExhausted || subscription.isExpired)
            val failure = if (!result.reachable && exhausted) ConnectionProbe.Failure.QUOTA else result.failure

            if (result.reachable) {
                _connectionHealth.value = ConnectionHealth(
                    phase = HealthPhase.ONLINE,
                    delayMs = result.delayMs,
                    checkedAt = System.currentTimeMillis(),
                )
                _activeDelayMs.value = result.delayMs
                _activeTestError.value = null
                selected?.let { profileStore.updateDelays(mapOf(it.id to result.delayMs.toInt())) }
                return@launch
            }

            val logEvidence = logs.replayCache
                .takeLast(40)
                .lastOrNull { ConnectionProbe.looksLikeCoreEof(it.message) }
                ?.message
                .orEmpty()

            _connectionHealth.value = ConnectionHealth(
                phase = if (exhausted) HealthPhase.EXHAUSTED else HealthPhase.NO_TRAFFIC,
                failure = failure,
                detail = when {
                    exhausted && subscription?.isExpired == true -> application.getString(R.string.failure_subscription_expired)
                    exhausted -> application.getString(R.string.failure_quota)
                    result.detail.isNotBlank() -> result.detail
                    else -> logEvidence
                },
                delayMs = -1L,
                checkedAt = System.currentTimeMillis(),
            )
            _activeDelayMs.value = -1L
            _activeTestError.value = summarizeFailure(failure, subscription?.remarks.orEmpty())

            if (exhausted) {
                disconnect()
            }
        }
    }

    private fun summarizeFailure(failure: ConnectionProbe.Failure, subName: String): String {
        val suffix = if (subName.isBlank()) "" else " ($subName)"
        val base = when (failure) {
            ConnectionProbe.Failure.QUOTA -> R.string.failure_quota
            ConnectionProbe.Failure.SERVER_CLOSED -> R.string.failure_server_closed
            ConnectionProbe.Failure.HANDSHAKE -> R.string.failure_handshake
            ConnectionProbe.Failure.TIMEOUT -> R.string.failure_timeout
            ConnectionProbe.Failure.REFUSED -> R.string.failure_refused
            ConnectionProbe.Failure.DNS -> R.string.failure_dns
            ConnectionProbe.Failure.NONE -> 0
            ConnectionProbe.Failure.UNKNOWN -> R.string.failure_no_traffic
        }
        if (base == 0) return ""
        return application.getString(base) + suffix
    }

    enum class HealthPhase { IDLE, CHECKING, ONLINE, NO_TRAFFIC, EXHAUSTED }

    data class ConnectionHealth(
        val phase: HealthPhase = HealthPhase.IDLE,
        val failure: ConnectionProbe.Failure = ConnectionProbe.Failure.NONE,
        val detail: String = "",
        val delayMs: Long = 0L,
        val checkedAt: Long = 0L,
    )

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            val before = settingsStore.current()
            settingsStore.update(transform)
            val after = settingsStore.current()
            val automationChanged = before.autoUpdateSubscriptions != after.autoUpdateSubscriptions ||
                before.subscriptionUpdateIntervalHours != after.subscriptionUpdateIntervalHours ||
                before.autoCheckAppUpdates != after.autoCheckAppUpdates
            if (automationChanged) {
                withContext(Dispatchers.IO) {
                    AutomationScheduler.sync(application, after)
                }
            }
        }
    }

    fun checkForAppUpdates() {
        AutomationScheduler.checkForReleaseNow(application)
        checkForUpdates(manual = true)
    }

    fun checkForUpdates(manual: Boolean) {
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { ReleaseChecker.latestRelease() }
            val newer = info != null && ReleaseChecker.isNewer(info)
            _availableUpdate.value = if (newer) info else null
            if (manual) {
                showSnack(
                    if (newer) {
                        application.getString(R.string.update_available_snack, info?.tag.orEmpty())
                    } else {
                        application.getString(R.string.release_up_to_date)
                    },
                )
            }
        }
    }

    fun dismissUpdateBanner() {
        _availableUpdate.value = null
    }

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
