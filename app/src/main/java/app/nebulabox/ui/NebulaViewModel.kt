package app.nebulabox.ui

import android.app.Application
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.nebulabox.config.ConfigBuilder
import app.nebulabox.data.AppSettings
import app.nebulabox.data.Profile
import app.nebulabox.data.ProfileStore
import app.nebulabox.data.SettingsStore
import app.nebulabox.data.SubscriptionItem
import app.nebulabox.engine.Engines
import app.nebulabox.engine.OutboundGroup
import app.nebulabox.engine.TunnelEngine
import app.nebulabox.engine.TunnelState
import app.nebulabox.engine.TunnelStatus
import app.nebulabox.service.Actions
import app.nebulabox.util.AppLogger
import app.nebulabox.util.IpLocationChecker
import app.nebulabox.util.ShareLinkParser
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
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

    private val _testingProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val testingProgress: StateFlow<Pair<Int, Int>?> = _testingProgress.asStateFlow()

    private val _testingProfileIds = MutableStateFlow<Set<String>>(emptySet())
    val testingProfileIds: StateFlow<Set<String>> = _testingProfileIds.asStateFlow()

    private val _updatingSubscriptions = MutableStateFlow(false)
    val updatingSubscriptions: StateFlow<Boolean> = _updatingSubscriptions.asStateFlow()

    private var locationJob: Job? = null
    private var batchTestJob: Job? = null

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
        // Ensure engine is initialized early
        Engines.obtain()

        viewModelScope.launch {
            Engines.active
                .flatMapLatest { engine -> engine?.logs ?: emptyFlow() }
                .collect { logs.emit(it) }
        }

        // Automatically query connected Exit IP & Country + Real Delay when tunnel state becomes STARTED.
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
                        _activeDelayMs.value = null
                    }
                }
        }
    }

    val connected: Boolean get() = status.value.state == TunnelState.STARTED

    val activeEngine: TunnelEngine? get() = Engines.active.value

    fun showSnack(message: String) {
        viewModelScope.launch { snack.emit(message) }
    }

    // ----------------------------------------------------------- location & active delay test

    fun refreshLocation() {
        if (status.value.state != TunnelState.STARTED) return
        fetchExitLocationWithRetry(initialDelayMs = 0L)
    }

    /**
     * Tests the currently active tunnel's real HTTP 204 latency via `CoreController.measureDelay`
     * (matching v2rayNG's bottom-bar tap `measureV2rayDelay`) and refreshes Exit IP/Country.
     */
    fun testActiveConnectionDelay() {
        val currentSettings = settings.value.normalized()
        val selected = profiles.value.firstOrNull { it.id == currentSettings.selectedProfileId }
            ?: profiles.value.firstOrNull()
        if (status.value.state != TunnelState.STARTED) {
            if (selected != null) {
                testSingleProfileRealPing(selected)
            } else {
                showSnack("No profile selected")
            }
            return
        }

        locationJob?.cancel()
        locationJob = viewModelScope.launch(Dispatchers.IO) {
            _checkingLocation.value = true
            val engine = Engines.obtain()
            val url1 = currentSettings.delayTestUrl.ifBlank { "https://www.gstatic.com/generate_204" }
            var delayMs = engine.measureActiveDelay(url1)
            if (delayMs <= 0L) {
                delayMs = engine.measureActiveDelay("https://www.google.com/generate_204")
            }
            if (delayMs > 0L) {
                _activeDelayMs.value = delayMs
                if (selected != null) {
                    profileStore.updateDelays(mapOf(selected.id to delayMs.toInt()))
                }
            }

            val loc = IpLocationChecker.fetchLocation(currentSettings.socksPort)
            if (loc != null) {
                val mergedLoc = if (delayMs > 0L) loc.copy(delayMs = delayMs) else loc
                _endpointLocation.value = mergedLoc
                snack.emit("Connected: ${mergedLoc.flagEmoji} ${mergedLoc.countryName} · ${mergedLoc.delayMs} ms")
            } else if (delayMs > 0L) {
                snack.emit("Real delay: $delayMs ms")
            } else {
                snack.emit("Delay test failed (-1 ms)")
            }
            _checkingLocation.value = false
        }
    }

    private fun fetchExitLocationWithRetry(initialDelayMs: Long = 600L) {
        locationJob?.cancel()
        locationJob = viewModelScope.launch(Dispatchers.IO) {
            _checkingLocation.value = true
            if (initialDelayMs > 0) delay(initialDelayMs)
            val currentSettings = settingsStore.current().normalized()
            val engine = Engines.obtain()

            val d = engine.measureActiveDelay(currentSettings.delayTestUrl.ifBlank { "https://www.gstatic.com/generate_204" })
            if (d > 0L) {
                _activeDelayMs.value = d
                currentSettings.selectedProfileId?.let { id ->
                    profileStore.updateDelays(mapOf(id to d.toInt()))
                }
            }

            for (attempt in 1..3) {
                if (status.value.state != TunnelState.STARTED) break
                val loc = IpLocationChecker.fetchLocation(currentSettings.socksPort)
                if (loc != null) {
                    val finalLoc = if (d > 0L) loc.copy(delayMs = d) else loc
                    _endpointLocation.value = finalLoc
                    AppLogger.i("GeoIP", "Connected exit IP: ${finalLoc.ip} (${finalLoc.flagEmoji} ${finalLoc.countryName}, ${finalLoc.delayMs} ms)")
                    break
                }
                delay(1200L)
            }
            _checkingLocation.value = false
        }
    }

    // ----------------------------------------------------------- real ping & tcp ping (v2rayNG parity)

    /**
     * Tests a single profile's real HTTP 204 latency using `Libv2ray.measureOutboundDelay`
     * (works whether VPN is connected or disconnected!).
     */
    fun testSingleProfileRealPing(profile: Profile) {
        viewModelScope.launch(Dispatchers.IO) {
            _testingProfileIds.value = _testingProfileIds.value + profile.id
            try {
                val currentSettings = settingsStore.current().normalized()
                val testUrl = currentSettings.delayTestUrl.ifBlank { "https://www.gstatic.com/generate_204" }
                val config = runCatching { ConfigBuilder.buildForSpeedtest(profile, currentSettings) }.getOrNull()
                val delayMs = if (config != null) {
                    var res = Engines.obtain().measureOutboundDelay(config, testUrl)
                    if (res <= 0L) {
                        res = Engines.obtain().measureOutboundDelay(config, "https://cp.cloudflare.com/generate_204")
                    }
                    res.toInt()
                } else {
                    -1
                }
                profileStore.updateDelays(mapOf(profile.id to delayMs))
                if (delayMs > 0) {
                    snack.emit("${profile.displayName}: $delayMs ms")
                } else {
                    snack.emit("${profile.displayName}: Timeout (-1 ms)")
                }
            } finally {
                _testingProfileIds.value = _testingProfileIds.value - profile.id
            }
        }
    }

    /**
     * Batch Real Ping (`TestAllRealPing` in v2rayNG): tests all profiles in the current subscription group
     * concurrently using `Libv2ray.measureOutboundDelay`.
     */
    fun testAllRealPing() {
        if (_testingProgress.value != null) {
            batchTestJob?.cancel()
            _testingProgress.value = null
            _testingProfileIds.value = emptySet()
            showSnack("Ping test cancelled")
            return
        }

        batchTestJob = viewModelScope.launch(Dispatchers.IO) {
            val currentSettings = settingsStore.current().normalized()
            val subFilter = currentSettings.selectedSubscriptionId
            val targetList = profiles.value.filter {
                subFilter.isBlank() || it.subscriptionId == subFilter
            }
            if (targetList.isEmpty()) {
                snack.emit("No profiles to test")
                return@launch
            }

            val total = targetList.size
            var completed = 0
            var successCount = 0
            _testingProgress.value = 0 to total
            val testUrl = currentSettings.delayTestUrl.ifBlank { "https://www.gstatic.com/generate_204" }
            val engine = Engines.obtain()
            val semaphore = Semaphore(6)

            try {
                coroutineScope {
                    targetList.map { profile ->
                        async {
                            semaphore.withPermit {
                                _testingProfileIds.value = _testingProfileIds.value + profile.id
                                val delayMs = try {
                                    val config = ConfigBuilder.buildForSpeedtest(profile, currentSettings)
                                    var d = engine.measureOutboundDelay(config, testUrl)
                                    if (d <= 0L) {
                                        d = engine.measureOutboundDelay(config, "https://cp.cloudflare.com/generate_204")
                                    }
                                    d.toInt()
                                } catch (_: Throwable) {
                                    -1
                                } finally {
                                    _testingProfileIds.value = _testingProfileIds.value - profile.id
                                }

                                profileStore.updateDelays(mapOf(profile.id to delayMs))
                                synchronized(this@NebulaViewModel) {
                                    completed++
                                    if (delayMs > 0) successCount++
                                    _testingProgress.value = completed to total
                                }
                            }
                        }
                    }.awaitAll()
                }
                snack.emit("Real ping finished: $successCount / $total reachable")
            } finally {
                _testingProgress.value = null
                _testingProfileIds.value = emptySet()
            }
        }
    }

    /**
     * Batch TCP Ping (`TestAll` in v2rayNG): fast TCP socket connect handshake time across all profiles.
     */
    fun testAllTcpPing() {
        if (_testingProgress.value != null) {
            batchTestJob?.cancel()
            _testingProgress.value = null
            _testingProfileIds.value = emptySet()
            return
        }

        batchTestJob = viewModelScope.launch(Dispatchers.IO) {
            val currentSettings = settingsStore.current().normalized()
            val subFilter = currentSettings.selectedSubscriptionId
            val targetList = profiles.value.filter {
                subFilter.isBlank() || it.subscriptionId == subFilter
            }
            if (targetList.isEmpty()) return@launch

            val total = targetList.size
            var completed = 0
            var successCount = 0
            _testingProgress.value = 0 to total
            val semaphore = Semaphore(12)

            try {
                coroutineScope {
                    targetList.map { profile ->
                        async {
                            semaphore.withPermit {
                                _testingProfileIds.value = _testingProfileIds.value + profile.id
                                val delayMs = if (profile.server.isNotBlank() && profile.serverPort > 0) {
                                    socketConnectTime(profile.server, profile.serverPort, 2500)
                                } else {
                                    -1
                                }
                                _testingProfileIds.value = _testingProfileIds.value - profile.id
                                profileStore.updateDelays(mapOf(profile.id to delayMs))
                                synchronized(this@NebulaViewModel) {
                                    completed++
                                    if (delayMs > 0) successCount++
                                    _testingProgress.value = completed to total
                                }
                            }
                        }
                    }.awaitAll()
                }
                snack.emit("TCP ping finished: $successCount / $total reachable")
            } finally {
                _testingProgress.value = null
                _testingProfileIds.value = emptySet()
            }
        }
    }

    private fun socketConnectTime(host: String, port: Int, timeoutMs: Int = 2000): Int {
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
            snack.emit("Restarting Xray-core service…")
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
            val wasConnected = status.value.state == TunnelState.STARTED
            settingsStore.update { it.copy(selectedProfileId = profile.id) }
            if (wasConnected) {
                // Just like v2rayNG: selecting a new server while connected hot-switches to it
                Actions.connect(application, profile.id)
            }
        }
    }

    // ------------------------------------------------------------- profiles & v2rayNG batch ops

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

    fun sortByTestResults() {
        viewModelScope.launch {
            profileStore.sortByTestResults()
            snack.emit("Sorted profiles by latency")
        }
    }

    fun removeDuplicateProfiles() {
        viewModelScope.launch {
            val count = profileStore.removeDuplicates()
            snack.emit("Removed $count duplicate profile(s)")
        }
    }

    fun removeInvalidProfiles() {
        viewModelScope.launch {
            val count = profileStore.removeInvalid()
            snack.emit("Removed $count invalid/timed-out profile(s)")
        }
    }

    fun deleteAllProfiles() {
        viewModelScope.launch {
            profileStore.clear()
            snack.emit("All profiles deleted")
        }
    }

    fun exportAllShareLinks(callback: (String) -> Unit) {
        viewModelScope.launch {
            val subFilter = settings.value.selectedSubscriptionId
            val list = profileStore.all().filter {
                subFilter.isBlank() || it.subscriptionId == subFilter
            }
            val text = list.joinToString("\n") { ShareLinkParser.toShareUri(it) }
            callback(text)
        }
    }

    /** Immediately parses and imports links or Custom JSON text. */
    fun submitImportText(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            importTextInternal(text)
        }
    }

    suspend fun consumePendingImport() {
        val text = pendingImport.value ?: return
        pendingImport.value = null
        importTextInternal(text)
    }

    private suspend fun importTextInternal(text: String) {
        val trimmed = text.trim()
        // If user pasted a single http(s) subscription URL that doesn't have userInfo, offer to import as subscription
        if ((trimmed.startsWith("https://", true) || trimmed.startsWith("http://", true)) &&
            !trimmed.contains("\n") && !trimmed.substringAfter("://").substringBefore("/").contains("@")
        ) {
            val fetched = fetchUrlContent(trimmed)
            if (!fetched.isNullOrBlank()) {
                val parsedSub = runCatching { ShareLinkParser.parseMany(fetched) }.getOrDefault(emptyList())
                if (parsedSub.isNotEmpty()) {
                    val hostName = runCatching { URL(trimmed).host }.getOrDefault("Subscription")
                    val subItem = SubscriptionItem(
                        id = ShareLinkParser.newId(),
                        remarks = hostName,
                        url = trimmed,
                        enabled = true,
                        updatedAt = System.currentTimeMillis(),
                    )
                    profileStore.upsertSubscription(subItem)
                    profileStore.replaceSubscriptionProfiles(subItem.id, subItem.url, parsedSub)
                    if (settingsStore.current().selectedProfileId.isNullOrBlank()) {
                        settingsStore.update { it.copy(selectedProfileId = parsedSub.first().id) }
                    }
                    importResult.emit(ImportResult(parsedSub.size, text))
                    return
                }
            }
        }

        val currentSubId = settingsStore.current().selectedSubscriptionId
        val parsed = runCatching { ShareLinkParser.parseMany(text) }.getOrDefault(emptyList())
            .map { if (currentSubId.isNotBlank()) it.copy(subscriptionId = currentSubId) else it }
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

    // ------------------------------------------------------------- subscriptions

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
            runCatching { URL(cleanUrl).host }.getOrDefault("Subscription")
        }
        viewModelScope.launch(Dispatchers.IO) {
            _updatingSubscriptions.value = true
            try {
                val sub = SubscriptionItem(
                    id = subId,
                    remarks = name,
                    url = cleanUrl,
                    enabled = true,
                    updatedAt = System.currentTimeMillis(),
                )
                profileStore.upsertSubscription(sub)
                val body = fetchUrlContent(cleanUrl)
                if (body.isNullOrBlank()) {
                    snack.emit("Saved subscription '$name', but failed to fetch URL")
                    return@launch
                }
                val parsed = runCatching { ShareLinkParser.parseMany(body) }.getOrDefault(emptyList())
                if (parsed.isNotEmpty()) {
                    profileStore.replaceSubscriptionProfiles(sub.id, sub.url, parsed)
                    if (settingsStore.current().selectedProfileId.isNullOrBlank()) {
                        settingsStore.update { it.copy(selectedProfileId = parsed.first().id) }
                    }
                    snack.emit("Subscription '$name': imported ${parsed.size} profile(s)")
                } else {
                    snack.emit("Subscription '$name' returned 0 valid profiles")
                }
            } finally {
                _updatingSubscriptions.value = false
            }
        }
    }

    fun updateAllSubscriptions() {
        if (_updatingSubscriptions.value) return
        viewModelScope.launch(Dispatchers.IO) {
            val subs = profileStore.allSubscriptions().filter { it.enabled }
            if (subs.isEmpty()) {
                snack.emit("No subscriptions configured. Add a subscription URL first.")
                return@launch
            }
            _updatingSubscriptions.value = true
            var updatedSubs = 0
            var totalProfiles = 0
            try {
                for (sub in subs) {
                    val body = fetchUrlContent(sub.url) ?: continue
                    val parsed = runCatching { ShareLinkParser.parseMany(body) }.getOrDefault(emptyList())
                    if (parsed.isNotEmpty()) {
                        profileStore.replaceSubscriptionProfiles(sub.id, sub.url, parsed)
                        profileStore.upsertSubscription(sub.copy(updatedAt = System.currentTimeMillis()))
                        updatedSubs++
                        totalProfiles += parsed.size
                    }
                }
                snack.emit("Updated $updatedSubs/${subs.size} subscription(s) ($totalProfiles profiles)")
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
            snack.emit("Subscription deleted")
        }
    }

    private suspend fun fetchUrlContent(urlStr: String): String? = withContext(Dispatchers.IO) {
        // Try through local proxy first if tunnel is running, then fall back to direct connection
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
                setRequestProperty("User-Agent", "v2rayNG/2.3.10")
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
