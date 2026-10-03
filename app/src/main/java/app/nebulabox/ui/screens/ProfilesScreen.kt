package app.nebulabox.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nebulabox.config.ConfigBuilder
import app.nebulabox.data.Profile
import app.nebulabox.data.Protocol
import app.nebulabox.engine.TunnelState
import app.nebulabox.ui.NebulaViewModel
import app.nebulabox.ui.components.ConnectionDock
import app.nebulabox.ui.components.EmptyServerState
import app.nebulabox.ui.components.JavidTopBar
import app.nebulabox.ui.components.QuickActionStrip
import app.nebulabox.ui.components.ServerProfileCard
import app.nebulabox.ui.components.SubscriptionGroupBar
import app.nebulabox.ui.dialogs.ConfirmActionDialog
import app.nebulabox.ui.dialogs.ImportConfigDialog
import app.nebulabox.ui.dialogs.QrCodeDialog
import app.nebulabox.ui.dialogs.ShareProfileDialog
import app.nebulabox.ui.dialogs.SubscriptionsSheet
import app.nebulabox.util.ClipboardHelper
import app.nebulabox.util.ShareLinkParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    viewModel: NebulaViewModel,
    onEdit: (Profile) -> Unit,
    onNewWithProtocol: (Protocol) -> Unit,
    onOpenDrawer: () -> Unit = {},
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

    var showSearch by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    var showSubscriptionsDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteAllConfirm by rememberSaveable { mutableStateOf(false) }
    var showDeleteDupConfirm by rememberSaveable { mutableStateOf(false) }
    var showDeleteInvalidConfirm by rememberSaveable { mutableStateOf(false) }
    var shareTarget by remember { mutableStateOf<Profile?>(null) }
    var qrDialogProfile by remember { mutableStateOf<Profile?>(null) }

    LaunchedEffect(showSubscriptionsInit) {
        if (showSubscriptionsInit) {
            showSubscriptionsDialog = true
            onSubscriptionsDismissed()
        }
    }

    val selectedSubId = settings.selectedSubscriptionId
    val filteredProfiles = remember(profiles, selectedSubId, searchQuery) {
        val bySub = if (selectedSubId.isBlank()) {
            profiles
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

    val subBadgeMap = remember(subscriptions) {
        subscriptions.associate { it.id to (it.remarks.firstOrNull()?.uppercase() ?: "") }
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
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
                onMenuClick = onOpenDrawer,
                onCancelTesting = { viewModel.cancelAllPing() },
                onImportClipboard = importClipboardAction,
                onImportUrlOrText = { showImportDialog = true },
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
        },
        bottomBar = {
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
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            QuickActionStrip(
                serverCount = filteredProfiles.size,
                onRealPingAll = { viewModel.testAllRealPing() },
                onSortByPing = { viewModel.sortByTestResults() },
                onImportClipboard = importClipboardAction,
                onOpenSubscriptions = { showSubscriptionsDialog = true },
            )

            SubscriptionGroupBar(
                subscriptions = subscriptions,
                profiles = profiles,
                selectedSubId = selectedSubId,
                onSelectGroup = { viewModel.selectSubscriptionFilter(it) },
            )

            if (filteredProfiles.isEmpty()) {
                EmptyServerState(
                    hasAnyProfiles = profiles.isNotEmpty(),
                    onImportClipboard = importClipboardAction,
                    onOpenImportDialog = { showImportDialog = true },
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
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
                            onSelect = { viewModel.selectProfile(profile) },
                            onShare = { shareTarget = profile },
                            onEdit = { onEdit(profile) },
                            onDelete = { viewModel.deleteProfile(profile.id) },
                            onPingSingle = { viewModel.testSingleProfileRealPing(profile) },
                        )
                    }
                }
            }
        }
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
            onAddSubscription = { remarks, url ->
                viewModel.addOrUpdateSubscription(id = null, remarks = remarks, url = url)
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
}
