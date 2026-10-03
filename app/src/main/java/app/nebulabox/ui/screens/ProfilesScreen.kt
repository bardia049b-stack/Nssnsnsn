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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nebulabox.R
import app.nebulabox.config.ConfigBuilder
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
import app.nebulabox.ui.dividerColorDark
import app.nebulabox.ui.dividerColorLight
import app.nebulabox.util.Formatters
import app.nebulabox.util.IpLocationChecker
import app.nebulabox.util.ShareLinkParser
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch

/**
 * Main screen built directly from `v2rayNG 2.3.10` (`MainScreen.kt`, `MainTopBar.kt`,
 * `MainServerPager.kt`, `MainBottomBar.kt`).
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
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val testingProgress by viewModel.testingProgress.collectAsStateWithLifecycle()
    val isUpdatingSubs by viewModel.updatingSubscriptions.collectAsStateWithLifecycle()
    val isTestingActive by viewModel.checkingLocation.collectAsStateWithLifecycle()
    val activePingMs by viewModel.activeDelayMs.collectAsStateWithLifecycle()
    val exitIpInfo by viewModel.endpointLocation.collectAsStateWithLifecycle()
    val isTesting = testingProgress != null || isTestingActive

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
                    p.protocol.wire.lowercase().contains(q) ||
                    p.remark.lowercase().contains(q)
            }
        }
    }

    val subBadgeMap = remember(subscriptions) {
        subscriptions.associate { it.id to (it.remarks.firstOrNull()?.toString() ?: "") }
    }

    Scaffold(
        topBar = {
            MainTopBar(
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
                onImportClipboard = {
                    val clip = readClipboard(context)
                    if (clip.isNullOrBlank()) {
                        Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.submitImportText(clip)
                    }
                },
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
                testingProgress = testingProgress,
                exitIpInfo = exitIpInfo,
                isDarkTheme = isDarkTheme,
                onTestCurrentServer = { viewModel.testActiveConnectionDelay() },
                onToggleService = {
                    val target = profiles.firstOrNull { it.id == settings.selectedProfileId }
                        ?: profiles.firstOrNull()
                    if (target != null) {
                        viewModel.toggle(target)
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
            if (subscriptions.isNotEmpty()) {
                val allTabs = remember(subscriptions, profiles) {
                    buildList {
                        add(Triple("", "All", profiles.size))
                        subscriptions.forEach { sub ->
                            val count = profiles.count { it.subscriptionId == sub.id }
                            add(Triple(sub.id, sub.remarks, count))
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
                            onClick = { viewModel.selectSubscriptionFilter(subId) },
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
                                "Tap + in the top bar to import configuration from Clipboard"
                            } else {
                                "No matching servers"
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
                                        viewModel.submitImportText(clip)
                                    }
                                },
                            ) {
                                Text("Import config from Clipboard")
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
                            subscriptionBadge = if (selectedSubId.isBlank()) {
                                subBadgeMap[profile.subscriptionId].orEmpty()
                            } else {
                                ""
                            },
                            onSelect = { viewModel.selectProfile(profile) },
                            onShare = { shareTarget = profile },
                            onEdit = { onEdit(profile) },
                            onDelete = { viewModel.deleteProfile(profile.id) },
                        )
                        ItemDivider()
                    }
                }
            }
        }
    }

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
                val uri = ShareLinkParser.toShareUri(profile)
                if (uri.isBlank()) {
                    Toast.makeText(context, "Cannot export URI for this profile", Toast.LENGTH_SHORT).show()
                } else {
                    copyToClipboard(context, profile.displayName, uri)
                }
            },
            onCopyFullConfig = {
                shareTarget = null
                val json = runCatching { ConfigBuilder.build(profile, settings.normalized()) }
                    .getOrElse { profile.customConfig }
                copyToClipboard(context, "${profile.displayName} JSON", json)
            },
        )
    }

    qrDialogProfile?.let { profile ->
        QRCodeDialog(
            profile = profile,
            uri = ShareLinkParser.toShareUri(profile),
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
        SubscriptionsManagerDialog(
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
        ConfirmDialog(
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
        ConfirmDialog(
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
        ConfirmDialog(
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

@Composable
private fun ItemDivider(modifier: Modifier = Modifier) {
    val color = if (LocalDarkTheme.current) dividerColorDark else dividerColorLight
    HorizontalDivider(
        modifier = modifier.padding(horizontal = 12.dp),
        thickness = 0.5.dp,
        color = color,
    )
}

/**
 * Exact `v2rayNG 2.3.10` `MainTopBar` (`com.v2ray.ang.ui.main.MainTopBar` + `AppTopBar`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(
    isLoading: Boolean,
    isTesting: Boolean,
    showSearch: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchClose: () -> Unit,
    onSearchToggle: (Boolean) -> Unit,
    onMenuClick: () -> Unit,
    onCancelTesting: () -> Unit,
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
                                    "Filter config",
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
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_back_24dp),
                            contentDescription = "Back",
                        )
                    }
                } else {
                    IconButton(onClick = onMenuClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_menu_24dp),
                            contentDescription = "Menu",
                        )
                    }
                }
            },
            actions = {
                if (!showSearch) {
                    IconButton(onClick = { onSearchToggle(true) }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_search_24dp),
                            contentDescription = "Search",
                        )
                    }
                }

                if (isTesting) {
                    TextButton(onClick = onCancelTesting) {
                        Text(
                            text = stringResource(android.R.string.cancel),
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }

                // Exact v2rayNG ImportMenuAction (ic_add_24dp)
                Box {
                    IconButton(onClick = { showImportMenu = true }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_add_24dp),
                            contentDescription = "Import",
                        )
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
                            text = { Text("Import config from URL / JSON") },
                            onClick = {
                                showImportMenu = false
                                onImportUrlOrText()
                            },
                        )
                        AppDivider()
                        DropdownMenuItem(
                            text = { Text("Type manually [VMess]") },
                            onClick = { showImportMenu = false; onNewProtocol(Protocol.VMESS) },
                        )
                        DropdownMenuItem(
                            text = { Text("Type manually [VLESS]") },
                            onClick = { showImportMenu = false; onNewProtocol(Protocol.VLESS) },
                        )
                        DropdownMenuItem(
                            text = { Text("Type manually [Shadowsocks]") },
                            onClick = { showImportMenu = false; onNewProtocol(Protocol.SHADOWSOCKS) },
                        )
                        DropdownMenuItem(
                            text = { Text("Type manually [Socks]") },
                            onClick = { showImportMenu = false; onNewProtocol(Protocol.SOCKS) },
                        )
                        DropdownMenuItem(
                            text = { Text("Type manually [Http]") },
                            onClick = { showImportMenu = false; onNewProtocol(Protocol.HTTP) },
                        )
                        DropdownMenuItem(
                            text = { Text("Type manually [Trojan]") },
                            onClick = { showImportMenu = false; onNewProtocol(Protocol.TROJAN) },
                        )
                        DropdownMenuItem(
                            text = { Text("Type manually [WireGuard]") },
                            onClick = { showImportMenu = false; onNewProtocol(Protocol.WIREGUARD) },
                        )
                        DropdownMenuItem(
                            text = { Text("Type manually [Hysteria2]") },
                            onClick = { showImportMenu = false; onNewProtocol(Protocol.HYSTERIA2) },
                        )
                        DropdownMenuItem(
                            text = { Text("Type manually [Custom]") },
                            onClick = { showImportMenu = false; onNewProtocol(Protocol.CUSTOM) },
                        )
                    }
                }

                // Exact v2rayNG MainMoreMenuAction (ic_more_vert_24dp)
                Box {
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_more_vert_24dp),
                            contentDescription = "More",
                        )
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
                            text = { Text("Real ping all configuration") },
                            onClick = { showMoreMenu = false; onPingAllReal() },
                        )
                        DropdownMenuItem(
                            text = { Text("Ping all configuration") },
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
                            text = { Text("Export all non-custom config") },
                            onClick = { showMoreMenu = false; onExportAll() },
                        )
                        DropdownMenuItem(
                            text = { Text("Remove duplicate config") },
                            onClick = { showMoreMenu = false; onDeleteDuplicates() },
                        )
                        DropdownMenuItem(
                            text = { Text("Remove invalid config") },
                            onClick = { showMoreMenu = false; onDeleteInvalid() },
                        )
                        DropdownMenuItem(
                            text = { Text("Remove all config") },
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
 * Exact `v2rayNG 2.3.10` `ServerListItem` (`com.v2ray.ang.ui.main.MainServerPager.kt`).
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
) {
    val testResult = profile.testDelayString

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onSelect),
    ) {
        // Left selection indicator bar (exact v2rayNG 10.dp box with 6.dp spacer + 4.dp primary bar)
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
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        ) {
            // Line 1: Remarks + Share / Edit / Delete (exact v2rayNG 2.3.10 vector icons)
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
                        painter = painterResource(id = R.drawable.ic_share_24dp),
                        contentDescription = "Share",
                        modifier = Modifier.size(24.dp),
                    )
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_edit_24dp),
                        contentDescription = "Edit",
                        modifier = Modifier.size(24.dp),
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_delete_24dp),
                        contentDescription = "Delete",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Line 2: Subscription badge + server : port
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (subscriptionBadge.isNotEmpty()) {
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = subscriptionBadge,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = profile.formattedAddress,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Line 3: Type description (orange) + Ping result (green / pink-red, empty when 0)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = profile.typeDescription,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorConfigType,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = testResult,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (profile.lastDelayMs < 0) colorPingRed else colorPing,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Exact `v2rayNG 2.3.10` `MainBottomBar` (`com.v2ray.ang.ui.main.MainBottomBar.kt`).
 */
@Composable
private fun MainBottomBar(
    status: app.nebulabox.engine.TunnelStatus,
    activePingMs: Long?,
    isTestingActive: Boolean,
    testingProgress: Pair<Int, Int>?,
    exitIpInfo: IpLocationChecker.EndpointLocation?,
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

    val statusText = when {
        testingProgress != null -> {
            val left = (testingProgress.second - testingProgress.first).coerceAtLeast(0)
            "Testing… ($left / ${testingProgress.second})"
        }
        isTestingActive -> "Testing…"
        status.state == TunnelState.STARTED -> {
            when {
                activePingMs != null && activePingMs > 0L -> {
                    val base = "Test available: ${activePingMs}ms"
                    if (exitIpInfo != null && exitIpInfo.ip.isNotBlank()) {
                        "$base\n(${exitIpInfo.flagEmoji} ${exitIpInfo.countryName}) ${exitIpInfo.ip}"
                    } else {
                        base
                    }
                }
                activePingMs != null && activePingMs < 0L -> "Test failed: Timeout"
                exitIpInfo != null && exitIpInfo.ip.isNotBlank() ->
                    "Connected\n(${exitIpInfo.flagEmoji} ${exitIpInfo.countryName}) ${exitIpInfo.ip}"
                else -> "Connected, tap to check connection"
            }
        }
        status.state == TunnelState.STARTING -> "Starting service…"
        status.state == TunnelState.STOPPING -> "Stopping service…"
        else -> status.message.ifBlank { "Not connected" }
    }

    val speedText = if (isRunning) {
        "${Formatters.speed(status.uplink)} ↑\n${Formatters.speed(status.downlink)} ↓"
    } else {
        ""
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onTestCurrentServer)
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            AppDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(start = 16.dp, end = 96.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (speedText.isNotEmpty()) {
                    Text(
                        text = speedText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp),
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
                                targetValue = 720f,
                                animationSpec = tween(durationMillis = 1600),
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
            contentColor = Color.White,
        ) {
            Icon(
                painter = painterResource(
                    id = if (isRunning) R.drawable.ic_stop_24dp else R.drawable.ic_play_24dp,
                ),
                contentDescription = if (isRunning) "Stop" else "Start",
                tint = Color.White,
                modifier = Modifier.graphicsLayer { rotationZ = rotationAnim.value },
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
                    text = "Export full configuration to Clipboard",
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
                Text("Subscription group setting")
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
                                        painter = painterResource(id = R.drawable.ic_delete_24dp),
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            AppDivider()
                        }
                    }
                }

                Text("Add Subscription", style = MaterialTheme.typography.labelLarge)
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
                Text("Save & Update")
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
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
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
