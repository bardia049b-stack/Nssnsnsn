package app.nebulabox.ui

import android.app.Application
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.nebulabox.automation.AutomationNotifications
import app.nebulabox.automation.AutomationScheduler
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
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
                    } else {
                        locationJob?.cancel()
                        _checkingLocation.value = false
                        _endpointLocation.value = null
                        _activeDelayMs.value = null
                        _activeTestError.value = null
                    }
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
                showSnack("No profile selected")
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
            title = "Slow server",
            message = "${profile.displayName}: $delayMs ms (threshold $threshold ms)",
        )
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
                snack.emit("No profiles to test")
                return@launch
            }

            val total = targetList.size
            var completed = 0
            _testingProgress.value = 0 to total

            profileStore.clearTestDelays(targetList.mapTo(HashSet()) { it.id })
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
            profileStore.clearTestDelays(targetList.mapTo(HashSet()) { it.id })
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
            snack.emit("Restarting service…")
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
            snack.emit("Removed ${ids.size} configuration(s)")
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
            snack.emit("Configuration duplicated to All")
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
            snack.emit("Sorted by test results")
        }
    }

    fun removeDuplicateProfiles() {
        launchLoading {
            val count = withContext(Dispatchers.IO) {
                profileStore.removeDuplicates()
            }
            snack.emit("Removed $count duplicate configuration(s)")
        }
    }

    fun removeInvalidProfiles() {
        launchLoading {
            val count = withContext(Dispatchers.IO) {
                profileStore.removeInvalid()
            }
            snack.emit("Removed $count invalid configuration(s)")
        }
    }

    fun deleteAllProfiles() {
        launchLoading {
            withContext(Dispatchers.IO) {
                val subFilter = settingsStore.current().selectedSubscriptionId
                profileStore.clearGroup(subFilter)
            }
            snack.emit("All configurations in current group removed")
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
            runCatching { URL(cleanUrl).host }.getOrDefault("Subscription")
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
                    val body = fetchUrlContent(cleanUrl)
                    if (body.isNullOrBlank()) {
                        snack.emit("Saved subscription '$name', but failed to fetch URL")
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
                        snack.emit("Subscription '$name': imported ${parsed.size} configuration(s)")
                    } else {
                        snack.emit("Subscription '$name' returned 0 valid configurations")
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
                        snack.emit("No subscriptions configured")
                        return@withContext
                    }
                    var updatedSubs = 0
                    var totalProfiles = 0
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
                    snack.emit("Updated $updatedSubs/${subs.size} subscription(s) ($totalProfiles configurations)")
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
            snack.emit("Subscription deleted")
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
        showSnack("Checking for JavidTun updates")
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
