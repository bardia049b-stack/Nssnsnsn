package app.nebulabox.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nebulabox.R
import app.nebulabox.data.Profile
import app.nebulabox.data.Protocol
import app.nebulabox.data.SubscriptionItem
import app.nebulabox.engine.TunnelState
import app.nebulabox.ui.AppDivider
import app.nebulabox.ui.LocalDarkTheme
import app.nebulabox.ui.NebulaViewModel
import app.nebulabox.ui.colorConfigType
import app.nebulabox.ui.colorFabActive
import app.nebulabox.ui.colorFabInactiveDark
import app.nebulabox.ui.colorFabInactiveLight
import app.nebulabox.ui.colorPing
import app.nebulabox.ui.colorPingRed
import app.nebulabox.util.Formatters
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch

/**
 * Main screen modeled directly on `v2rayNG 2.3.10` (`MainScreen.kt`, `MainTopBar.kt`,
 * `MainServerPager.kt`, `MainBottomBar.kt`):
 *  - Clean TopAppBar with inline search, `+` Import menu, and `⋮` More menu
 *  - Subscription group `ScrollableTabRow` with orange indicator
 *  - Crisp `ServerListItem` rows with left selection indicator, Share/Edit/Delete actions,
 *    orange protocol/transport tag, and green/red ping result
 *  - Bottom status bar (tap to test real delay + live speed + exit IP/country) with docked orange Play/Stop FAB
 */
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
    val isPinging by viewModel.isPinging.collectAsStateWithLifecycle()
    val isUpdatingSubs by viewModel.isUpdatingSubs.collectAsStateWithLifecycle()
    val isTestingActive by viewModel.isTestingActiveDelay.collectAsStateWithLifecycle()
    val activePingMs by viewModel.activeConnectionPingMs.collectAsStateWithLifecycle()
    val exitIpInfo by viewModel.exitIpInfo.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val isDarkTheme = LocalDarkTheme.current

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
                    p.protocol.label.lowercase().contains(q) ||
                    p.group.lowercase().contains(q)
            }
        }
    }

    val subBadgeMap = remember(subscriptions) {
        subscriptions.associate { it.id to (it.remarks.firstOrNull()?.uppercase() ?: "") }
    }

    Scaffold(
        topBar = {
            MainTopBar(
                isLoading = isPinging || isUpdatingSubs || isTestingActive,
                showSearch = showSearch,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onSearchClose = {
                    searchQuery = ""
                    showSearch = false
                },
                onSearchToggle = { showSearch = it },
                onMenuClick = onOpenDrawer,
                onImportClipboard = {
                    val clip = readClipboard(context)
                    if (clip.isNullOrBlank()) {
                        Toast.makeText(context, R.string.clipboard_empty, Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.importFromText(clip)
                    }
                },
                onImportUrlOrText = { showImportDialog = true },
                onNewProtocol = onNewWithProtocol,
                onRestartService = {
                    if (status.state == TunnelState.STARTED) {
                        viewModel.selectProfile(settings.selectedProfileId ?: profiles.firstOrNull()?.id.orEmpty())
                    } else {
                        viewModel.toggleTunnel()
                    }
                },
                onPingAllTcp = { viewModel.pingAll(realPing = false) },
                onPingAllReal = { viewModel.pingAll(realPing = true) },
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
                    viewModel.exportAllToClipboard { text ->
                        if (text.isBlank()) {
                            Toast.makeText(context, "No shareable profiles", Toast.LENGTH_SHORT).show()
                        } else {
                            copyToClipboard(context, "v2rayNG Export", text)
                        }
                    }
                },
            )
        },
        bottomBar = {
            MainBottomBar(
                status = status,
                activePingMs = activePingMs,
                isTestingActive = isTestingActive,
                exitIpInfo = exitIpInfo,
                isDarkTheme = isDarkTheme,
                onTestCurrentServer = { viewModel.testActiveConnectionDelay() },
                onToggleService = { viewModel.toggleTunnel() },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Subscription Group Tab Bar (exact v2rayNG GroupTabBar)
            if (subscriptions.isNotEmpty()) {
                val allTabs = remember(subscriptions, profiles) {
                    buildList {
                        add(Triple("", "All (${profiles.size})", profiles.size))
                        subscriptions.forEach { sub ->
                            val count = profiles.count { it.subscriptionId == sub.id }
                            add(Triple(sub.id, "${sub.remarks} ($count)", count))
                        }
                    }
                }
                val selectedTabIndex = allTabs.indexOfFirst { it.first == selectedSubId }.coerceAtLeast(0)

                ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface),
                    edgePadding = 16.dp,
                    indicator = { tabPositions ->
                        if (selectedTabIndex in tabPositions.indices) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    },
                ) {
                    allTabs.forEachIndexed { index, (subId, title, _) ->
                        Tab(
                            selected = index == selectedTabIndex,
                            onClick = { viewModel.selectSubscriptionGroup(subId) },
                            modifier = Modifier
                                .widthIn(min = 56.dp)
                                .heightIn(min = 46.dp),
                            text = {
                                Text(
                                    text = title,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }

            if (filteredProfiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = if (profiles.isEmpty()) {
                                "Tap + in the top bar to import configs from clipboard or add a server."
                            } else {
                                "No matching servers in this group."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (profiles.isEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    val clip = readClipboard(context)
                                    if (clip.isNullOrBlank()) {
                                        showImportDialog = true
                                    } else {
                                        viewModel.importFromText(clip)
                                    }
                                },
                            ) {
                                Text("Import from Clipboard")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    items(filteredProfiles, key = { it.id }) { profile ->
                        val isSelected = profile.id == settings.selectedProfileId ||
                            (settings.selectedProfileId == null && profile == profiles.firstOrNull())
                        ServerListItem(
                            profile = profile,
                            isSelected = isSelected,
                            subscriptionBadge = subBadgeMap[profile.subscriptionId].orEmpty(),
                            onSelect = { viewModel.selectProfile(profile.id) },
                            onShare = { shareTarget = profile },
                            onEdit = { onEdit(profile) },
                            onDelete = { viewModel.deleteProfile(profile.id) },
                            onPingSingle = { viewModel.pingSingle(profile, realPing = true) },
                        )
                        AppDivider(modifier = Modifier.padding(horizontal = 12.dp))
                    }
                }
            }
        }
    }

    // Share method dialog (exact v2rayNG ShareMethodDialog)
    shareTarget?.let { profile ->
        ShareMethodDialog(
            profile = profile,
            onDismiss = { shareTarget = null },
            onShowQrCode = {
                shareTarget = null
                qrDialogProfile = profile
            },
            onCopyUri = {
                shareTarget = null
                val uri = viewModel.shareProfileUri(profile)
                if (uri.isBlank()) {
                    Toast.makeText(context, "Cannot export URI for this profile", Toast.LENGTH_SHORT).show()
                } else {
                    copyToClipboard(context, profile.displayName, uri)
                }
            },
            onCopyFullConfig = {
                shareTarget = null
                val json = viewModel.exportProfileFullJson(profile)
                copyToClipboard(context, "${profile.displayName} JSON", json)
            },
        )
    }

    qrDialogProfile?.let { profile ->
        QRCodeDialog(
            profile = profile,
            uri = viewModel.shareProfileUri(profile),
            onDismiss = { qrDialogProfile = null },
        )
    }

    if (showImportDialog) {
        ImportTextOrUrlDialog(
            onDismiss = { showImportDialog = false },
            onSubmit = { text, asSubscription, subRemarks ->
                showImportDialog = false
                if (asSubscription && (text.startsWith("http://") || text.startsWith("https://"))) {
                    viewModel.addOrUpdateSubscription(
                        remarks = subRemarks.ifBlank { "Subscription" },
                        url = text.trim(),
                    )
                } else {
                    viewModel.importFromText(text)
                }
            },
        )
    }

    if (showSubscriptionsDialog) {
        SubscriptionsManagerDialog(
            subscriptions = subscriptions,
            isUpdating = isUpdatingSubs,
            onDismiss = { showSubscriptionsDialog = false },
            onAddSubscription = { remarks, url ->
                viewModel.addOrUpdateSubscription(remarks, url)
            },
            onUpdateAll = { viewModel.updateAllSubscriptions() },
            onDeleteSubscription = { id, deleteProfiles ->
                viewModel.deleteSubscription(id, deleteProfiles)
            },
        )
    }

    if (showDeleteDupConfirm) {
        ConfirmDialog(
            title = "Delete duplicate configs?",
            message = "Remove servers with identical protocol, address, port, credentials, and transport settings.",
            onDismiss = { showDeleteDupConfirm = false },
            onConfirm = {
                showDeleteDupConfirm = false
                viewModel.removeDuplicates()
            },
        )
    }

    if (showDeleteInvalidConfirm) {
        ConfirmDialog(
            title = "Delete invalid configs?",
            message = "Remove all servers that timed out (-1 ms) during the last test.",
            onDismiss = { showDeleteInvalidConfirm = false },
            onConfirm = {
                showDeleteInvalidConfirm = false
                viewModel.removeInvalidProfiles()
            },
        )
    }

    if (showDeleteAllConfirm) {
        ConfirmDialog(
            title = "Delete all configs?",
            message = "Remove all servers in the current group.",
            onDismiss = { showDeleteAllConfirm = false },
            onConfirm = {
                showDeleteAllConfirm = false
                viewModel.clearAllProfiles(selectedSubId.takeIf { it.isNotBlank() })
            },
        )
    }
}

/**
 * Exact `v2rayNG 2.3.10` TopAppBar (`MainTopBar.kt` & `AppTopBar` in `Components.kt`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(
    isLoading: Boolean,
    showSearch: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchClose: () -> Unit,
    onSearchToggle: (Boolean) -> Unit,
    onMenuClick: () -> Unit,
    onImportClipboard: () -> Unit,
    onImportUrlOrText: () -> Unit,
    onNewProtocol: (Protocol) -> Unit,
    onRestartService: () -> Unit,
    onPingAllTcp: () -> Unit,
    onPingAllReal: () -> Unit,
    onSortByTestResults: () -> Unit,
    onUpdateSubscriptions: () -> Unit,
    onDeleteDuplicates: () -> Unit,
    onDeleteInvalid: () -> Unit,
    onDeleteAll: () -> Unit,
    onExportAll: () -> Unit,
) {
    var showImportMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    Column {
        TopAppBar(
            title = {
                if (showSearch) {
                    val focusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
                            placeholder = {
                                Text(
                                    "Search servers...",
                                    style = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp),
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                cursorColor = MaterialTheme.colorScheme.secondary,
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear")
                            }
                        }
                    }
                } else {
                    Text(text = stringResource(R.string.app_name))
                }
            },
            navigationIcon = {
                if (showSearch) {
                    IconButton(onClick = onSearchClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                } else {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                }
            },
            actions = {
                if (!showSearch) {
                    IconButton(onClick = { onSearchToggle(true) }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                }

                // + Import / Add Menu (exact v2rayNG ImportMenuAction)
                Box {
                    IconButton(onClick = { showImportMenu = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Import / Add")
                    }
                    DropdownMenu(
                        expanded = showImportMenu,
                        onDismissRequest = { showImportMenu = false },
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        DropdownMenuItem(
                            text = { Text("Import config from Clipboard") },
                            onClick = {
                                showImportMenu = false
                                onImportClipboard()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Import from URL / Subscription / JSON") },
                            onClick = {
                                showImportMenu = false
                                onImportUrlOrText()
                            },
                        )
                        AppDivider()
                        DropdownMenuItem(
                            text = { Text("Type manually [VLESS]") },
                            onClick = { showImportMenu = false; onNewProtocol(Protocol.VLESS) },
                        )
                        DropdownMenuItem(
                            text = { Text("Type manually [VMess]") },
                            onClick = { showImportMenu = false; onNewProtocol(Protocol.VMESS) },
                        )
                        DropdownMenuItem(
                            text = { Text("Type manually [Shadowsocks]") },
                            onClick = { showImportMenu = false; onNewProtocol(Protocol.SHADOWSOCKS) },
                        )
                        DropdownMenuItem(
                            text = { Text("Type manually [Trojan]") },
                            onClick = { showImportMenu = false; onNewProtocol(Protocol.TROJAN) },
                        )
                        DropdownMenuItem(
                            text = { Text("Type manually [Hysteria2]") },
                            onClick = { showImportMenu = false; onNewProtocol(Protocol.HYSTERIA2) },
                        )
                        DropdownMenuItem(
                            text = { Text("Type manually [Socks]") },
                            onClick = { showImportMenu = false; onNewProtocol(Protocol.SOCKS) },
                        )
                        DropdownMenuItem(
                            text = { Text("Type manually [WireGuard]") },
                            onClick = { showImportMenu = false; onNewProtocol(Protocol.WIREGUARD) },
                        )
                        DropdownMenuItem(
                            text = { Text("Type manually [Custom JSON]") },
                            onClick = { showImportMenu = false; onNewProtocol(Protocol.CUSTOM) },
                        )
                    }
                }

                // ⋮ More Menu (exact v2rayNG MainMoreMenuAction)
                Box {
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        DropdownMenuItem(
                            text = { Text("Restart service") },
                            onClick = { showMoreMenu = false; onRestartService() },
                        )
                        DropdownMenuItem(
                            text = { Text("Real ping all server") },
                            onClick = { showMoreMenu = false; onPingAllReal() },
                        )
                        DropdownMenuItem(
                            text = { Text("Ping all server (TCP)") },
                            onClick = { showMoreMenu = false; onPingAllTcp() },
                        )
                        DropdownMenuItem(
                            text = { Text("Sort by test results") },
                            onClick = { showMoreMenu = false; onSortByTestResults() },
                        )
                        DropdownMenuItem(
                            text = { Text("Update subscription") },
                            onClick = { showMoreMenu = false; onUpdateSubscriptions() },
                        )
                        DropdownMenuItem(
                            text = { Text("Export all config to clipboard") },
                            onClick = { showMoreMenu = false; onExportAll() },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete duplicate config") },
                            onClick = { showMoreMenu = false; onDeleteDuplicates() },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete invalid config") },
                            onClick = { showMoreMenu = false; onDeleteInvalid() },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete all config") },
                            onClick = { showMoreMenu = false; onDeleteAll() },
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurface,
            ),
        )
        AnimatedVisibility(
            visible = isLoading,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        AppDivider()
    }
}

/**
 * Exact `v2rayNG 2.3.10` Server Row (`ServerListItem` in `MainServerPager.kt`).
 */
@Composable
private fun ServerListItem(
    profile: Profile,
    isSelected: Boolean,
    subscriptionBadge: String,
    onSelect: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPingSingle: () -> Unit,
) {
    val testResult = when {
        profile.lastDelayMs == -2 -> "..."
        profile.lastDelayMs == 0 -> ""
        else -> "${profile.lastDelayMs} ms"
    }

    val typeDescription = remember(profile) {
        buildProtocolDescription(profile)
    }

    val serverEndpoint = remember(profile) {
        if (profile.protocol == Protocol.CUSTOM) {
            "Custom Xray JSON"
        } else {
            "${profile.server}:${profile.serverPort}"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onSelect),
    ) {
        // Left selection indicator bar (exact v2rayNG 10.dp box with 4.dp vertical bar)
        Box(
            Modifier
                .width(10.dp)
                .fillMaxHeight(),
        ) {
            if (isSelected) {
                Row {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .padding(vertical = 10.dp)
                            .background(MaterialTheme.colorScheme.secondary),
                    )
                }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        ) {
            // Line 1: Remarks + Share / Edit / Delete icons
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = profile.displayName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Share",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Line 2: Subscription badge + server host:port
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (subscriptionBadge.isNotBlank()) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = subscriptionBadge,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = serverEndpoint,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Line 3: Left = Protocol/Transport/Security (Orange), Right = Ping delay (Green/Red, tap to test)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = typeDescription,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorConfigType,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = testResult.ifEmpty { "Tap to ping" },
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        testResult.isEmpty() -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        profile.lastDelayMs < 0 -> colorPingRed
                        else -> colorPing
                    },
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onPingSingle)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
    }
}

private fun buildProtocolDescription(profile: Profile): String {
    if (profile.protocol == Protocol.CUSTOM) return "CUSTOM"
    val parts = mutableListOf(profile.protocol.name)
    val net = profile.transport.type.ifBlank { "tcp" }.lowercase()
    if (net.isNotBlank() && net != "tcp") {
        parts.add(net)
    }
    when {
        profile.tls.reality -> parts.add("reality")
        profile.tls.enabled -> parts.add(if (profile.tls.insecure) "tls insecure" else "tls")
    }
    return parts.joinToString(" / ")
}

/**
 * Exact `v2rayNG 2.3.10` Bottom Status Bar + Floating Play/Stop FAB (`MainBottomBar.kt`).
 */
@Composable
private fun MainBottomBar(
    status: app.nebulabox.engine.TunnelStatus,
    activePingMs: Long,
    isTestingActive: Boolean,
    exitIpInfo: NebulaViewModel.ExitIpInfo,
    isDarkTheme: Boolean,
    onTestCurrentServer: () -> Unit,
    onToggleService: () -> Unit,
) {
    val isRunning = status.state == TunnelState.STARTED
    val isBusy = status.state == TunnelState.STARTING || status.state == TunnelState.STOPPING
    val scope = rememberCoroutineScope()
    val rotationAnim = remember { Animatable(0f) }

    LaunchedEffect(isRunning) {
        if (!isRunning) {
            rotationAnim.snapTo(0f)
        }
    }

    val primaryText = when (status.state) {
        TunnelState.STARTED -> when {
            isTestingActive -> "Testing connection..."
            activePingMs > 0L -> "Connected: test delay ${activePingMs} ms"
            activePingMs == -2L -> "Connected: test timeout (tap to retry)"
            else -> "Connected, tap to check connection"
        }
        TunnelState.STARTING -> "Starting service..."
        TunnelState.STOPPING -> "Stopping service..."
        TunnelState.STOPPED -> status.message.ifBlank { "Not connected" }
    }

    val secondaryText = if (isRunning) {
        val speedPart = "↑ ${Formatters.speed(status.uplink)}   ↓ ${Formatters.speed(status.downlink)}"
        val ipPart = if (exitIpInfo.ip.isNotBlank()) {
            "  •  ${exitIpInfo.flag} ${exitIpInfo.ip} ${exitIpInfo.country}".trimEnd()
        } else {
            ""
        }
        speedPart + ipPart
    } else {
        null
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .clickable(enabled = isRunning, onClick = onTestCurrentServer)
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            AppDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(start = 16.dp, end = 92.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = primaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (status.state == TunnelState.STOPPED && status.message.isNotBlank()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!secondaryText.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = secondaryText,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorPing,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = {
                if (!isBusy) {
                    if (!isRunning) {
                        scope.launch {
                            rotationAnim.animateTo(
                                targetValue = 360f,
                                animationSpec = tween(durationMillis = 1200),
                            )
                        }
                    }
                    onToggleService()
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 24.dp)
                .offset(y = (-28).dp)
                .navigationBarsPadding(),
            shape = CircleShape,
            containerColor = if (isRunning) {
                colorFabActive
            } else if (isDarkTheme) {
                colorFabInactiveDark
            } else {
                colorFabInactiveLight
            },
        ) {
            Icon(
                imageVector = if (isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = if (isRunning) "Stop" else "Start",
                tint = Color.White,
                modifier = Modifier
                    .size(26.dp)
                    .graphicsLayer { rotationZ = rotationAnim.value },
            )
        }
    }
}

@Composable
private fun ShareMethodDialog(
    profile: Profile,
    onDismiss: () -> Unit,
    onShowQrCode: () -> Unit,
    onCopyUri: () -> Unit,
    onCopyFullConfig: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(profile.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                if (profile.protocol != Protocol.CUSTOM) {
                    Text(
                        text = "Export config to QRcode",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onShowQrCode)
                            .padding(vertical = 12.dp),
                    )
                    Text(
                        text = "Export config to Clipboard",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onCopyUri)
                            .padding(vertical = 12.dp),
                    )
                }
                Text(
                    text = "Export full Xray JSON to Clipboard",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onCopyFullConfig)
                        .padding(vertical = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun QRCodeDialog(
    profile: Profile,
    uri: String,
    onDismiss: () -> Unit,
) {
    val qrBitmap = remember(uri) {
        if (uri.isNotBlank()) generateQrBitmap(uri, 640) else null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(profile.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (qrBitmap != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier
                                .size(240.dp)
                                .padding(12.dp),
                        )
                    }
                } else {
                    Text("Cannot generate QR code for this profile.")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun ImportTextOrUrlDialog(
    onDismiss: () -> Unit,
    onSubmit: (text: String, asSubscription: Boolean, subRemarks: String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var subRemarks by remember { mutableStateOf("") }
    var saveAsSub by remember { mutableStateOf(true) }
    val isUrl = text.trim().let { it.startsWith("http://") || it.startsWith("https://") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Config / Subscription") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Share links, Subscription URL, or Xray JSON") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 7,
                )
                if (isUrl) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { saveAsSub = !saveAsSub },
                    ) {
                        Checkbox(checked = saveAsSub, onCheckedChange = { saveAsSub = it })
                        Text("Save as Subscription Group", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (saveAsSub) {
                        OutlinedTextField(
                            value = subRemarks,
                            onValueChange = { subRemarks = it },
                            label = { Text("Group Name (e.g. My Sub)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(text.trim(), isUrl && saveAsSub, subRemarks.trim()) },
                enabled = text.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
fun SubscriptionsManagerDialog(
    subscriptions: List<SubscriptionItem>,
    isUpdating: Boolean,
    onDismiss: () -> Unit,
    onAddSubscription: (remarks: String, url: String) -> Unit,
    onUpdateAll: () -> Unit,
    onDeleteSubscription: (id: String, deleteProfiles: Boolean) -> Unit,
) {
    var remarks by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Subscription Groups")
                if (subscriptions.isNotEmpty()) {
                    IconButton(onClick = onUpdateAll, enabled = !isUpdating) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Update All")
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (subscriptions.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(subscriptions, key = { it.id }) { sub ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(sub.remarks, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        sub.url,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(onClick = { onDeleteSubscription(sub.id, true) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            AppDivider()
                        }
                    }
                }

                Text("Add New Subscription", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Remarks (Name)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Subscription URL (https://...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAddSubscription(remarks.ifBlank { "Subscription" }, url.trim())
                    remarks = ""
                    url = ""
                },
                enabled = url.isNotBlank() && !isUpdating,
            ) {
                Text("Add & Sync")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_ok), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private fun generateQrBitmap(content: String, size: Int): Bitmap? = runCatching {
    val hints = mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.MARGIN to 1,
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bmp.setPixel(x, y, if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    bmp
}.getOrNull()

private fun readClipboard(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}
