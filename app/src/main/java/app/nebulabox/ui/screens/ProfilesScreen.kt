package app.nebulabox.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nebulabox.config.ConfigBuilder
import app.nebulabox.data.Profile
import app.nebulabox.data.Protocol
import app.nebulabox.engine.TunnelState
import app.nebulabox.ui.NebulaViewModel
import app.nebulabox.ui.components.ConnectionDock
import app.nebulabox.ui.components.EmptyServerState
import app.nebulabox.ui.components.JavidTopBar
import app.nebulabox.ui.components.ServerProfileCard
import app.nebulabox.ui.components.SubscriptionGroupBar
import app.nebulabox.ui.dialogs.ConfirmActionDialog
import app.nebulabox.ui.dialogs.ImportConfigDialog
import app.nebulabox.ui.dialogs.QrCodeDialog
import app.nebulabox.ui.dialogs.QrScanDialog
import app.nebulabox.ui.dialogs.ShareProfileDialog
import app.nebulabox.ui.dialogs.SubscriptionsSheet
import app.nebulabox.util.ClipboardHelper
import app.nebulabox.util.ShareLinkParser

@Composable
fun ProfilesScreen(
    viewModel: NebulaViewModel,
    onEdit: (Profile) -> Unit,
    onNewWithProtocol: (Protocol) -> Unit,
    showSubscriptionsInit: Boolean = false,
    onSubscriptionsDismissed: () -> Unit = {},
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val testingProgress by viewModel.testingProgress.collectAsStateWithLifecycle()
    val isUpdatingSubs by viewModel.updatingSubscriptions.collectAsStateWithLifecycle()
    val isTestingActive by viewModel.checkingLocation.collectAsStateWithLifecycle()
    val activePingMs by viewModel.activeDelayMs.collectAsStateWithLifecycle()
    val exitIpInfo by viewModel.endpointLocation.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    var showQrScanner by rememberSaveable { mutableStateOf(false) }
    var clipboardCandidate by remember { mutableStateOf<String?>(null) }
    var lastClipboardHash by rememberSaveable { mutableStateOf("") }
    var showSubscriptionsDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteAllConfirm by rememberSaveable { mutableStateOf(false) }
    var showDeleteDupConfirm by rememberSaveable { mutableStateOf(false) }
    var showDeleteInvalidConfirm by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteProfile by remember { mutableStateOf<Profile?>(null) }
    var pendingDeleteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedProfileIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var qrDialogProfile by remember { mutableStateOf<Profile?>(null) }
    var shareTarget by remember { mutableStateOf<Profile?>(null) }
    val currentClipboardHash by rememberUpdatedState(lastClipboardHash)

    DisposableEffect(lifecycleOwner, settings.clipboardAutoImport) {
        val observer = LifecycleEventObserver { _, event ->
            if (settings.clipboardAutoImport && event == Lifecycle.Event.ON_RESUME) {
                val text = ClipboardHelper.readText(context)?.trim().orEmpty()
                if (text.isNotBlank() && text.length <= 250_000) {
                    val importable = ShareLinkParser.looksLikeShareLink(text) ||
                        runCatching { ShareLinkParser.parseMany(text).isNotEmpty() }.getOrDefault(false)
                    val hash = text.hashCode().toString()
                    if (importable && hash != currentClipboardHash) {
                        lastClipboardHash = hash
                        clipboardCandidate = text
                    }
                }
            }
        }
        if (settings.clipboardAutoImport) lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(showSubscriptionsInit) {
        if (showSubscriptionsInit) {
            showSubscriptionsDialog = true
            onSubscriptionsDismissed()
        }
    }

    val selectedSubId = settings.selectedSubscriptionId
    LaunchedEffect(selectedSubId, searchQuery) { selectedProfileIds = emptySet() }

    val filteredProfiles = remember(profiles, selectedSubId, searchQuery) {
        val bySub = if (selectedSubId.isBlank()) {
            profiles.filter { it.subscriptionId.isBlank() }
        } else {
            profiles.filter { it.subscriptionId == selectedSubId }
        }
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            bySub
        } else {
            bySub.filter { p ->
                p.displayName.lowercase().contains(q) ||
                    p.server.lowercase().contains(q) ||
                    p.protocol.wire.lowercase().contains(q) ||
                    p.remark.lowercase().contains(q)
            }
        }
    }

    val listState = rememberLazyListState()
    val firstProfileId = filteredProfiles.firstOrNull()?.id
    LaunchedEffect(firstProfileId, filteredProfiles.size) {
        if (filteredProfiles.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    val subBadgeMap = remember(subscriptions) {
        subscriptions.associate { it.id to it.remarks }
    }

    val selectedProfile = remember(profiles, settings.selectedProfileId) {
        profiles.firstOrNull { it.id == settings.selectedProfileId } ?: profiles.firstOrNull()
    }

    val importClipboardAction = {
        val clip = ClipboardHelper.readText(context)
        if (clip.isNullOrBlank()) {
            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.submitImportText(clip)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        JavidTopBar(
            isConnected = status.state == TunnelState.STARTED,
            isLoading = isLoading,
            isTesting = testingProgress != null,
            showSearch = showSearch,
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            onSearchClose = {
                searchQuery = ""
                showSearch = false
            },
            onSearchToggle = { showSearch = it },
            onOpenSubscriptions = { showSubscriptionsDialog = true },
            onCancelTesting = { viewModel.cancelAllPing() },
            onImportClipboard = importClipboardAction,
            onImportUrlOrText = { showImportDialog = true },
            onScanQr = { showQrScanner = true },
            onNewProtocol = onNewWithProtocol,
            onRestartService = { viewModel.restartTunnel() },
            onPingAllTcp = { viewModel.testAllTcpPing() },
            onPingAllReal = { viewModel.testAllRealPing() },
            onSortByTestResults = { viewModel.sortByTestResults() },
            onUpdateSubscriptions = {
                if (subscriptions.isEmpty()) {
                    showSubscriptionsDialog = true
                } else {
                    viewModel.updateAllSubscriptions()
                }
            },
            onDeleteDuplicates = { showDeleteDupConfirm = true },
            onDeleteInvalid = { showDeleteInvalidConfirm = true },
            onDeleteAll = { showDeleteAllConfirm = true },
            onExportAll = {
                viewModel.exportAllShareLinks { text ->
                    if (text.isBlank()) {
                        Toast.makeText(context, "No shareable profiles", Toast.LENGTH_SHORT).show()
                    } else {
                        ClipboardHelper.copyText(context, "JavidTun Export", text)
                    }
                }
            },
        )

        ConnectionDock(
            status = status,
            activeProfileName = selectedProfile?.displayName,
            activePingMs = activePingMs,
            isTestingActive = isTestingActive,
            testingProgress = testingProgress,
            exitIpInfo = exitIpInfo,
            onTestCurrentServer = { viewModel.testActiveConnectionDelay() },
            onToggleService = {
                if (selectedProfile != null) {
                    viewModel.toggle(selectedProfile)
                } else {
                    viewModel.showSnack("Add or import a server first")
                }
            },
        )

        SubscriptionGroupBar(
            subscriptions = subscriptions,
            profiles = profiles,
            selectedSubId = selectedSubId,
            currentListCount = filteredProfiles.size,
            onSelectGroup = { viewModel.selectSubscriptionFilter(it) },
            onPingAll = { viewModel.testAllRealPing() },
            onSortByPing = { viewModel.sortByTestResults() },
        )

        if (selectedProfileIds.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(
                            text = "${selectedProfileIds.size} selected",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                selectedProfileIds = if (selectedProfileIds.size == filteredProfiles.size) {
                                    emptySet()
                                } else {
                                    filteredProfiles.mapTo(LinkedHashSet()) { it.id }
                                }
                            },
                        ) {
                            Text(if (selectedProfileIds.size == filteredProfiles.size) "Deselect all" else "Select all")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { selectedProfileIds = emptySet() }) {
                            Text("Cancel")
                        }
                        TextButton(
                            onClick = {
                                val selected = filteredProfiles.filter {
                                    it.id in selectedProfileIds && it.protocol != Protocol.CUSTOM
                                }
                                val export = selected.joinToString("\n") { ShareLinkParser.toShareUri(it) }
                                if (export.isBlank()) {
                                    Toast.makeText(context, "Selected custom profiles cannot be exported as share links", Toast.LENGTH_SHORT).show()
                                } else {
                                    ClipboardHelper.copyText(context, "JavidTun Export", export)
                                }
                            },
                        ) {
                            Text("Export")
                        }
                        TextButton(onClick = { pendingDeleteIds = selectedProfileIds }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (filteredProfiles.isEmpty()) {
                EmptyServerState(
                    hasAnyProfiles = searchQuery.isNotBlank(),
                    onImportClipboard = importClipboardAction,
                    onOpenImportDialog = { showImportDialog = true },
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 6.dp,
                        bottom = 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filteredProfiles, key = { it.id }) { profile ->
                        val isSelected = profile.id == selectedProfile?.id
                        ServerProfileCard(
                            profile = profile,
                            isSelected = isSelected,
                            subscriptionBadge = if (selectedSubId.isBlank()) {
                                subBadgeMap[profile.subscriptionId].orEmpty()
                            } else {
                                ""
                            },
                            isMultiSelectMode = selectedProfileIds.isNotEmpty(),
                            isChecked = profile.id in selectedProfileIds,
                            onSelect = { viewModel.selectProfile(profile) },
                            onShare = { shareTarget = profile },
                            onEdit = { onEdit(profile) },
                            onDelete = { pendingDeleteProfile = profile },
                            onDuplicate = { viewModel.duplicateProfile(profile) },
                            onPingSingle = { viewModel.testSingleProfileRealPing(profile) },
                            onLongPress = {
                                selectedProfileIds = selectedProfileIds + profile.id
                            },
                            onToggleSelected = {
                                selectedProfileIds = if (profile.id in selectedProfileIds) {
                                    selectedProfileIds - profile.id
                                } else {
                                    selectedProfileIds + profile.id
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showQrScanner) {
        QrScanDialog(
            onDismiss = { showQrScanner = false },
            onScanned = { value ->
                showQrScanner = false
                viewModel.submitImportText(value)
            },
        )
    }

    clipboardCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { clipboardCandidate = null },
            title = { Text("Import from clipboard?") },
            text = {
                Text(
                    candidate.take(180).let { if (candidate.length > 180) "$it…" else it },
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        clipboardCandidate = null
                        viewModel.submitImportText(candidate)
                    },
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { clipboardCandidate = null }) {
                    Text("Not now")
                }
            },
        )
    }

    shareTarget?.let { profile ->
        ShareProfileDialog(
            profile = profile,
            onDismiss = { shareTarget = null },
            onShowQrCode = {
                shareTarget = null
                qrDialogProfile = profile
            },
            onCopyUri = {
                shareTarget = null
                val uri = ShareLinkParser.toShareUri(profile)
                if (uri.isBlank()) {
                    Toast.makeText(context, "Cannot export URI for this profile", Toast.LENGTH_SHORT).show()
                } else {
                    ClipboardHelper.copyText(context, profile.displayName, uri)
                }
            },
            onCopyFullConfig = {
                shareTarget = null
                val json = runCatching { ConfigBuilder.build(profile, settings.normalized()) }
                    .getOrElse { profile.customConfig }
                ClipboardHelper.copyText(context, "${profile.displayName} JSON", json)
            },
        )
    }

    qrDialogProfile?.let { profile ->
        QrCodeDialog(
            profile = profile,
            uri = ShareLinkParser.toShareUri(profile),
            onDismiss = { qrDialogProfile = null },
        )
    }

    if (showImportDialog) {
        ImportConfigDialog(
            onDismiss = { showImportDialog = false },
            onSubmit = { text, asSubscription, subRemarks ->
                showImportDialog = false
                if (asSubscription && (text.startsWith("http://") || text.startsWith("https://"))) {
                    viewModel.addOrUpdateSubscription(
                        id = null,
                        remarks = subRemarks.ifBlank { "Subscription" },
                        url = text.trim(),
                    )
                } else {
                    viewModel.submitImportText(text)
                }
            },
        )
    }

    if (showSubscriptionsDialog) {
        SubscriptionsSheet(
            subscriptions = subscriptions,
            isUpdating = isUpdatingSubs,
            onDismiss = { showSubscriptionsDialog = false },
            onSaveSubscription = { id, remarks, url ->
                viewModel.addOrUpdateSubscription(id = id, remarks = remarks, url = url)
            },
            onUpdateAll = { viewModel.updateAllSubscriptions() },
            onDeleteSubscription = { id, _ ->
                viewModel.deleteSubscription(id)
            },
        )
    }

    if (showDeleteDupConfirm) {
        ConfirmActionDialog(
            title = "Remove duplicate configs?",
            message = "Remove configurations with identical protocol, address, port, credentials, and transport settings.",
            onDismiss = { showDeleteDupConfirm = false },
            onConfirm = {
                showDeleteDupConfirm = false
                viewModel.removeDuplicateProfiles()
            },
        )
    }

    if (showDeleteInvalidConfirm) {
        ConfirmActionDialog(
            title = "Remove invalid configs?",
            message = "Remove all configurations that failed (-1 ms) during the last test.",
            onDismiss = { showDeleteInvalidConfirm = false },
            onConfirm = {
                showDeleteInvalidConfirm = false
                viewModel.removeInvalidProfiles()
            },
        )
    }

    if (showDeleteAllConfirm) {
        ConfirmActionDialog(
            title = "Remove all configs?",
            message = "Remove all configurations in the current group.",
            onDismiss = { showDeleteAllConfirm = false },
            onConfirm = {
                showDeleteAllConfirm = false
                viewModel.deleteAllProfiles()
            },
        )
    }

    pendingDeleteProfile?.let { profile ->
        ConfirmActionDialog(
            title = "Delete ‘${profile.displayName}’?",
            message = "This configuration will be removed from this device.",
            onDismiss = { pendingDeleteProfile = null },
            onConfirm = {
                pendingDeleteProfile = null
                viewModel.deleteProfile(profile.id)
            },
        )
    }

    if (pendingDeleteIds.isNotEmpty()) {
        val count = pendingDeleteIds.size
        ConfirmActionDialog(
            title = "Delete $count selected configurations?",
            message = "This action cannot be undone.",
            onDismiss = { pendingDeleteIds = emptySet() },
            onConfirm = {
                val ids = pendingDeleteIds
                pendingDeleteIds = emptySet()
                selectedProfileIds = emptySet()
                viewModel.deleteProfiles(ids)
            },
        )
    }
}
