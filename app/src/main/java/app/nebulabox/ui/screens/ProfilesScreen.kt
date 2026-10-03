package app.nebulabox.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.nebulabox.data.Profile
import app.nebulabox.data.Protocol
import app.nebulabox.data.TunnelPhase
import app.nebulabox.ui.NebulaViewModel
import app.nebulabox.ui.components.ConnectionDock
import app.nebulabox.ui.components.EmptyServerState
import app.nebulabox.ui.components.JavidTopBar
import app.nebulabox.ui.components.ServerProfileCard
import app.nebulabox.ui.components.SubscriptionGroupBar
import app.nebulabox.ui.dialogs.ConfirmActionDialog
import app.nebulabox.ui.dialogs.ImportConfigDialog
import app.nebulabox.ui.dialogs.QrCodeDialog
import app.nebulabox.ui.dialogs.ShareProfileDialog
import app.nebulabox.ui.dialogs.SubscriptionsSheet
import app.nebulabox.util.ClipboardHelper

@Composable
fun ProfilesScreen(
    viewModel: NebulaViewModel,
    onEdit: (Profile) -> Unit,
    onNewWithProtocol: (Protocol) -> Unit,
    showSubscriptionsInit: Boolean = false,
    onSubscriptionsDismissed: () -> Unit = {},
) {
    val context = LocalContext.current
    val profiles by viewModel.profiles.collectAsState()
    val subscriptions by viewModel.subscriptions.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val phase by viewModel.phase.collectAsState()
    val traffic by viewModel.traffic.collectAsState()
    val testing by viewModel.testingAll.collectAsState()
    val updatingSubs by viewModel.updatingSubs.collectAsState()
    val activeDelayMs by viewModel.activeDelayMs.collectAsState()
    val checkingLocation by viewModel.checkingLocation.collectAsState()
    val exitIpInfo by viewModel.exitIpInfo.collectAsState()

    var selectedSubId by rememberSaveable { mutableStateOf(settings.selectedSubscriptionId) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    var showSubsDialog by rememberSaveable { mutableStateOf(false) }
    var qrProfile by remember { mutableStateOf<Profile?>(null) }
    var shareOptionsProfile by remember { mutableStateOf<Profile?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }
    var confirmRemoveInvalid by remember { mutableStateOf(false) }
    var confirmRemoveDuplicates by remember { mutableStateOf(false) }

    LaunchedEffect(showSubscriptionsInit) {
        if (showSubscriptionsInit) {
            showSubsDialog = true
            onSubscriptionsDismissed()
        }
    }

    val filtered = remember(profiles, selectedSubId, searchQuery) {
        profiles.filter { p ->
            val matchSub = selectedSubId.isEmpty() || p.subscriptionId == selectedSubId
            val matchQuery = searchQuery.isBlank() ||
                p.name.contains(searchQuery, ignoreCase = true) ||
                p.server.contains(searchQuery, ignoreCase = true) ||
                p.protocol.name.contains(searchQuery, ignoreCase = true)
            matchSub && matchQuery
        }
    }

    val selectedProfile = remember(profiles, settings.selectedProfileId) {
        profiles.firstOrNull { it.id == settings.selectedProfileId } ?: profiles.firstOrNull()
    }
    val subMap = remember(subscriptions) { subscriptions.associateBy { it.id } }
    val isConnected = phase == TunnelPhase.Connected
    val isConnectingOrStopping = phase == TunnelPhase.Starting || phase == TunnelPhase.Stopping
    val listState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxSize()) {
        JavidTopBar(
            isConnected = isConnected,
            isLoading = testing || updatingSubs || isConnectingOrStopping || checkingLocation,
            isTesting = testing,
            showSearch = showSearch,
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            onSearchClose = {
                showSearch = false
                searchQuery = ""
            },
            onSearchToggle = { showSearch = it },
            onOpenSubscriptions = { showSubsDialog = true },
            onCancelTesting = { viewModel.cancelTesting() },
            onImportClipboard = {
                val clip = ClipboardHelper.readText(context)
                if (!clip.isNullOrBlank()) {
                    viewModel.submitImportText(clip, selectedSubId)
                } else {
                    showImportDialog = true
                }
            },
            onImportUrlOrText = { showImportDialog = true },
            onNewProtocol = onNewWithProtocol,
            onRestartService = {
                if (isConnected) {
                    viewModel.disconnect()
                    viewModel.connect()
                } else {
                    viewModel.connect()
                }
            },
            onPingAllTcp = { viewModel.testAllProfiles(selectedSubId) },
            onPingAllReal = { viewModel.testAllProfilesReal(selectedSubId) },
            onSortByTestResults = { viewModel.sortByDelay(selectedSubId) },
            onUpdateSubscriptions = { viewModel.refreshAllSubscriptions() },
            onDeleteDuplicates = { confirmRemoveDuplicates = true },
            onDeleteInvalid = { confirmRemoveInvalid = true },
            onDeleteAll = { confirmClearAll = true },
            onExportAll = {
                val text = viewModel.exportAllLinks(selectedSubId)
                if (text.isNotBlank()) {
                    ClipboardHelper.copyText(context, "JavidTun Export", text)
                }
            },
        )

        ConnectionDock(
            phase = phase,
            selectedProfile = selectedProfile,
            activeDelayMs = activeDelayMs,
            checkingLocation = checkingLocation,
            exitIpInfo = exitIpInfo,
            traffic = traffic,
            onToggleConnection = { viewModel.toggleConnection() },
            onVerifyConnection = { viewModel.verifyActiveConnection() },
        )

        SubscriptionGroupBar(
            subscriptions = subscriptions,
            profiles = profiles,
            selectedSubId = selectedSubId,
            onSelectSubId = { id ->
                selectedSubId = id
                viewModel.selectSubscription(id)
            },
            onPingAll = { viewModel.testAllProfilesReal(selectedSubId) },
            onSortByPing = { viewModel.sortByDelay(selectedSubId) },
        )

        Box(modifier = Modifier.weight(1f)) {
            if (filtered.isEmpty()) {
                EmptyServerState(
                    onPasteClipboard = {
                        val clip = ClipboardHelper.readText(context)
                        if (!clip.isNullOrBlank()) {
                            viewModel.submitImportText(clip, selectedSubId)
                        } else {
                            showImportDialog = true
                        }
                    },
                    onImportInput = { showImportDialog = true },
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
                    items(filtered, key = { it.id }) { profile ->
                        val selected = profile.id == selectedProfile?.id
                        val subName = subMap[profile.subscriptionId]?.name
                        ServerProfileCard(
                            profile = profile,
                            selected = selected,
                            subscriptionName = subName,
                            onSelect = { viewModel.selectProfile(profile.id) },
                            onEdit = { onEdit(profile) },
                            onShare = { shareOptionsProfile = profile },
                            onDelete = { viewModel.deleteProfile(profile.id) },
                            onPing = { viewModel.testSingleProfileReal(profile) },
                        )
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        ImportConfigDialog(
            onDismiss = { showImportDialog = false },
            onImport = { text ->
                viewModel.submitImportText(text, selectedSubId)
                showImportDialog = false
            },
        )
    }

    if (showSubsDialog) {
        SubscriptionsSheet(
            viewModel = viewModel,
            onDismiss = { showSubsDialog = false },
        )
    }

    shareOptionsProfile?.let { profile ->
        ShareProfileDialog(
            profile = profile,
            onDismiss = { shareOptionsProfile = null },
            onShowQr = {
                shareOptionsProfile = null
                qrProfile = profile
            },
            onCopyLink = {
                ClipboardHelper.copyText(context, profile.name, profile.toShareUri())
                shareOptionsProfile = null
            },
            onDuplicate = {
                viewModel.duplicateProfile(profile)
                shareOptionsProfile = null
            },
        )
    }

    qrProfile?.let { profile ->
        QrCodeDialog(
            profile = profile,
            onDismiss = { qrProfile = null },
            onCopyUri = {
                ClipboardHelper.copyText(context, profile.name, profile.toShareUri())
            },
        )
    }

    if (confirmRemoveDuplicates) {
        ConfirmActionDialog(
            title = "Remove duplicate configs",
            message = "Remove profiles with identical server, port, protocol, and credentials?",
            onConfirm = {
                viewModel.removeDuplicates()
                confirmRemoveDuplicates = false
            },
            onDismiss = { confirmRemoveDuplicates = false },
        )
    }

    if (confirmRemoveInvalid) {
        ConfirmActionDialog(
            title = "Remove invalid configs",
            message = "Delete all profiles that timed out (-1 ms) during latency testing?",
            onConfirm = {
                viewModel.removeInvalidProfiles(selectedSubId)
                confirmRemoveInvalid = false
            },
            onDismiss = { confirmRemoveInvalid = false },
        )
    }

    if (confirmClearAll) {
        ConfirmActionDialog(
            title = "Remove all configs",
            message = if (selectedSubId.isEmpty()) {
                "Delete all server profiles?"
            } else {
                "Delete all profiles in the current subscription group?"
            },
            onConfirm = {
                viewModel.clearProfilesInGroup(selectedSubId)
                confirmClearAll = false
            },
            onDismiss = { confirmClearAll = false },
        )
    }
}
