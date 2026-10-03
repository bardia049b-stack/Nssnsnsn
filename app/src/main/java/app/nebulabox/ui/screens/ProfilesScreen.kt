package app.nebulabox.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nebulabox.R
import app.nebulabox.config.ConfigBuilder
import app.nebulabox.data.AppSettings
import app.nebulabox.data.Profile
import app.nebulabox.data.Protocol
import app.nebulabox.data.SubscriptionItem
import app.nebulabox.ui.NebulaViewModel
import app.nebulabox.util.ShareLinkParser
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

private fun readClipboardText(context: Context): String {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return ""
    val clip = cm.primaryClip ?: return ""
    if (clip.itemCount <= 0) return ""
    return clip.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

@Composable
fun ProfilesScreen(
    viewModel: NebulaViewModel,
    onEdit: (Profile) -> Unit,
    onNew: () -> Unit,
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val testingProgress by viewModel.testingProgress.collectAsStateWithLifecycle()
    val testingProfileIds by viewModel.testingProfileIds.collectAsStateWithLifecycle()
    val updatingSubs by viewModel.updatingSubscriptions.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var pasteOpen by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var moreMenuOpen by remember { mutableStateOf(false) }
    var addMenuOpen by remember { mutableStateOf(false) }
    var subDialogOpen by remember { mutableStateOf(false) }
    var shareProfile by remember { mutableStateOf<Profile?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    val filteredProfiles = remember(profiles, settings.selectedSubscriptionId, searchQuery) {
        profiles.filter { p ->
            val subOk = settings.selectedSubscriptionId.isBlank() || p.subscriptionId == settings.selectedSubscriptionId
            val q = searchQuery.trim().lowercase()
            val queryOk = q.isEmpty() ||
                p.displayName.lowercase().contains(q) ||
                p.server.lowercase().contains(q) ||
                p.protocol.wire.lowercase().contains(q)
            subOk && queryOk
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.tab_profiles),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = filteredProfiles.size.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Search toggle
                        IconButton(onClick = {
                            searchOpen = !searchOpen
                            if (!searchOpen) searchQuery = ""
                        }) {
                            Icon(
                                if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                                contentDescription = "Search",
                            )
                        }

                        // Real Ping All button (v2rayNG TestAllRealPing)
                        IconButton(onClick = { viewModel.testAllRealPing() }) {
                            if (testingProgress != null) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Bolt,
                                    contentDescription = "Real Ping All",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        // Import menu (+)
                        Box {
                            IconButton(onClick = { addMenuOpen = true }) {
                                Icon(Icons.Filled.Add, contentDescription = "Import / Add")
                            }
                            DropdownMenu(
                                expanded = addMenuOpen,
                                onDismissRequest = { addMenuOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Import from Clipboard") },
                                    leadingIcon = { Icon(Icons.Filled.ContentPaste, null) },
                                    onClick = {
                                        addMenuOpen = false
                                        val clip = readClipboardText(context)
                                        if (clip.isNotBlank()) {
                                            viewModel.submitImportText(clip)
                                        } else {
                                            pasteOpen = true
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Paste Config / Custom JSON") },
                                    leadingIcon = { Icon(Icons.Filled.Edit, null) },
                                    onClick = {
                                        addMenuOpen = false
                                        val clip = readClipboardText(context)
                                        if (clip.isNotBlank()) pasteText = clip
                                        pasteOpen = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Add Subscription URL") },
                                    leadingIcon = { Icon(Icons.Filled.Link, null) },
                                    onClick = {
                                        addMenuOpen = false
                                        subDialogOpen = true
                                    },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Create Manual Profile") },
                                    leadingIcon = { Icon(Icons.Filled.Add, null) },
                                    onClick = {
                                        addMenuOpen = false
                                        onNew()
                                    },
                                )
                            }
                        }

                        // v2rayNG More Menu (⋮)
                        Box {
                            IconButton(onClick = { moreMenuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More Actions")
                            }
                            DropdownMenu(
                                expanded = moreMenuOpen,
                                onDismissRequest = { moreMenuOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Real Ping All (HTTP 204)") },
                                    leadingIcon = { Icon(Icons.Filled.Bolt, null) },
                                    onClick = {
                                        moreMenuOpen = false
                                        viewModel.testAllRealPing()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("TCP Ping All (Socket)") },
                                    leadingIcon = { Icon(Icons.Filled.NetworkPing, null) },
                                    onClick = {
                                        moreMenuOpen = false
                                        viewModel.testAllTcpPing()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Sort by Test Results") },
                                    leadingIcon = { Icon(Icons.Filled.Sort, null) },
                                    onClick = {
                                        moreMenuOpen = false
                                        viewModel.sortByTestResults()
                                    },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Update Subscriptions") },
                                    leadingIcon = { Icon(Icons.Filled.CloudSync, null) },
                                    onClick = {
                                        moreMenuOpen = false
                                        viewModel.updateAllSubscriptions()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Manage Subscriptions") },
                                    leadingIcon = { Icon(Icons.Filled.Link, null) },
                                    onClick = {
                                        moreMenuOpen = false
                                        subDialogOpen = true
                                    },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Remove Duplicate Configs") },
                                    leadingIcon = { Icon(Icons.Filled.CleaningServices, null) },
                                    onClick = {
                                        moreMenuOpen = false
                                        viewModel.removeDuplicateProfiles()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Remove Invalid / Timeout") },
                                    leadingIcon = { Icon(Icons.Filled.FilterAltOff, null) },
                                    onClick = {
                                        moreMenuOpen = false
                                        viewModel.removeInvalidProfiles()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Export All to Clipboard") },
                                    leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                                    onClick = {
                                        moreMenuOpen = false
                                        viewModel.exportAllShareLinks { links ->
                                            if (links.isNotBlank()) {
                                                copyToClipboard(context, "NebulaBox Profiles", links)
                                                viewModel.showSnack("Copied ${filteredProfiles.size} profile URI(s) to clipboard")
                                            }
                                        }
                                    },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Delete All Profiles",
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.DeleteSweep,
                                            null,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    onClick = {
                                        moreMenuOpen = false
                                        confirmDeleteAll = true
                                    },
                                )
                            }
                        }
                    }
                }

                if (searchOpen) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by name, host, or protocol…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Subscription Group Filter Chips (matching v2rayNG MainServerPager tabs)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = settings.selectedSubscriptionId.isBlank(),
                        onClick = { viewModel.selectSubscriptionFilter("") },
                        label = { Text("All (${profiles.size})") },
                    )
                    subscriptions.forEach { sub ->
                        val count = profiles.count { it.subscriptionId == sub.id }
                        FilterChip(
                            selected = settings.selectedSubscriptionId == sub.id,
                            onClick = { viewModel.selectSubscriptionFilter(sub.id) },
                            label = { Text("${sub.remarks} ($count)") },
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = { subDialogOpen = true },
                        label = { Text("+ Subscription") },
                    )
                }

                // Progress indicator during batch ping or subscription update
                val progress = testingProgress
                if (progress != null && progress.second > 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { progress.first.toFloat() / progress.second.toFloat() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "Testing delay: ${progress.first} / ${progress.second} (tap ⚡ to cancel)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else if (updatingSubs) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FloatingActionButton(
                    onClick = {
                        val clip = readClipboardText(context)
                        if (clip.isNotBlank()) {
                            viewModel.submitImportText(clip)
                        } else {
                            pasteOpen = true
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Icon(Icons.Filled.ContentPaste, stringResource(R.string.action_import))
                }
                FloatingActionButton(onClick = { viewModel.testAllRealPing() }) {
                    Icon(Icons.Filled.Bolt, "Real Ping All")
                }
            }
        },
    ) { padding ->
        if (filteredProfiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.empty_profiles_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = stringResource(R.string.empty_profiles_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val clip = readClipboardText(context)
                                if (clip.isNotBlank()) {
                                    viewModel.submitImportText(clip)
                                } else {
                                    pasteOpen = true
                                }
                            },
                        ) {
                            Icon(Icons.Filled.ContentPaste, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_paste_clipboard))
                        }
                        TextButton(onClick = { pasteOpen = true }) {
                            Text(stringResource(R.string.action_import))
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = 120.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(filteredProfiles, key = { _, item -> item.id }) { _, profile ->
                    ProfileRow(
                        profile = profile,
                        selected = profile.id == settings.selectedProfileId,
                        isTesting = profile.id in testingProfileIds,
                        onSelect = { viewModel.selectProfile(profile) },
                        onTestPing = { viewModel.testSingleProfileRealPing(profile) },
                        onShare = { shareProfile = profile },
                        onEdit = { onEdit(profile) },
                        onDelete = { viewModel.deleteProfile(profile.id) },
                    )
                }
            }
        }
    }

    if (pasteOpen) {
        PasteSheet(
            value = pasteText,
            onValueChange = { pasteText = it },
            onPasteFromClipboard = {
                val clip = readClipboardText(context)
                if (clip.isNotBlank()) pasteText = clip
            },
            onDismiss = {
                pasteOpen = false
                pasteText = ""
            },
            onSubmit = {
                val textToImport = pasteText
                pasteOpen = false
                pasteText = ""
                viewModel.submitImportText(textToImport)
            },
        )
    }

    if (subDialogOpen) {
        SubscriptionDialog(
            subscriptions = subscriptions,
            updating = updatingSubs,
            onAddSubscription = { name, url ->
                viewModel.addOrUpdateSubscription(null, name, url)
            },
            onUpdateAll = { viewModel.updateAllSubscriptions() },
            onDeleteSubscription = { id -> viewModel.deleteSubscription(id) },
            onDismiss = { subDialogOpen = false },
        )
    }

    shareProfile?.let { prof ->
        ShareProfileDialog(
            profile = prof,
            settings = settings,
            onDismiss = { shareProfile = null },
            onCopied = { msg -> viewModel.showSnack(msg) },
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete all profiles?") },
            text = { Text("This will remove all ${profiles.size} saved profiles.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteAll = false
                        viewModel.deleteAllProfiles()
                    },
                ) {
                    Text("Delete All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ProfileRow(
    profile: Profile,
    selected: Boolean,
    isTesting: Boolean,
    onSelect: () -> Unit,
    onTestPing: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val hostPort = when {
        profile.server.isNotBlank() && profile.serverPort > 0 -> "${profile.server}:${profile.serverPort}"
        profile.server.isNotBlank() -> profile.server
        else -> "Custom JSON"
    }

    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = if (selected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            )
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected, onClick = onSelect)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = hostPort,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Real Ping Badge (Tap to test single server)
                PingBadge(
                    delayMs = profile.lastDelayMs,
                    testedAt = profile.lastTestedAt,
                    isTesting = isTesting,
                    onClick = onTestPing,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Protocol / Transport / TLS chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TagPill(profile.protocol.wire.uppercase(), MaterialTheme.colorScheme.secondaryContainer)
                    if (profile.protocol != Protocol.CUSTOM && profile.transport.type.isNotBlank()) {
                        TagPill(profile.transport.type.uppercase(), MaterialTheme.colorScheme.surfaceVariant)
                    }
                    if (profile.tls.reality) {
                        TagPill("REALITY", MaterialTheme.colorScheme.tertiaryContainer)
                    } else if (profile.tls.enabled) {
                        TagPill("TLS", MaterialTheme.colorScheme.surfaceVariant)
                    }
                }

                // v2rayNG card action buttons: Share (QR/URI/JSON), Edit, Delete
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onShare, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = "Share / QR",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.action_edit),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagPill(text: String, bgColor: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun PingBadge(
    delayMs: Int,
    testedAt: Long,
    isTesting: Boolean,
    onClick: () -> Unit,
) {
    val bgColor: Color
    val textColor: Color
    val label: String

    when {
        isTesting -> {
            bgColor = MaterialTheme.colorScheme.surfaceVariant
            textColor = MaterialTheme.colorScheme.onSurfaceVariant
            label = "…"
        }
        delayMs in 1..399 -> {
            bgColor = Color(0xFF1B5E20).copy(alpha = 0.18f)
            textColor = Color(0xFF2E7D32)
            label = "$delayMs ms"
        }
        delayMs in 400..899 -> {
            bgColor = Color(0xFFE65100).copy(alpha = 0.18f)
            textColor = Color(0xFFEF6C00)
            label = "$delayMs ms"
        }
        delayMs >= 900 -> {
            bgColor = Color(0xFFB71C1C).copy(alpha = 0.18f)
            textColor = Color(0xFFD32F2F)
            label = "$delayMs ms"
        }
        testedAt > 0L && delayMs <= 0 -> {
            bgColor = MaterialTheme.colorScheme.errorContainer
            textColor = MaterialTheme.colorScheme.onErrorContainer
            label = "Timeout"
        }
        else -> {
            bgColor = MaterialTheme.colorScheme.surfaceVariant
            textColor = MaterialTheme.colorScheme.primary
            label = "Ping"
        }
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        modifier = Modifier.clickable(enabled = !isTesting) { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (isTesting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
            )
        }
    }
}

@Composable
private fun ShareProfileDialog(
    profile: Profile,
    settings: AppSettings,
    onDismiss: () -> Unit,
    onCopied: (String) -> Unit,
) {
    val context = LocalContext.current
    val shareUri = remember(profile) { ShareLinkParser.toShareUri(profile) }
    val fullXrayJson = remember(profile, settings) {
        runCatching { ConfigBuilder.build(profile, settings) }.getOrDefault(profile.customConfig)
    }
    val qrBitmap = remember(shareUri) { generateQrBitmap(shareUri, 560) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.QrCode2, contentDescription = null)
                Text(
                    text = profile.displayName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (qrBitmap != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        modifier = Modifier.padding(4.dp),
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier
                                .size(220.dp)
                                .padding(8.dp),
                        )
                    }
                }

                Button(
                    onClick = {
                        copyToClipboard(context, profile.displayName, shareUri)
                        onCopied("Copied share URI to clipboard")
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Copy Share Link (URI)")
                }

                OutlinedButton(
                    onClick = {
                        copyToClipboard(context, "${profile.displayName} JSON", fullXrayJson)
                        onCopied("Copied full Xray JSON config to clipboard")
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Copy Full Xray JSON Config")
                }
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
private fun SubscriptionDialog(
    subscriptions: List<SubscriptionItem>,
    updating: Boolean,
    onAddSubscription: (String, String) -> Unit,
    onUpdateAll: () -> Unit,
    onDeleteSubscription: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Subscriptions (v2rayNG)") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Remarks / Group Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Subscription URL (https://…)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            onAddSubscription(name, url)
                            name = ""
                            url = ""
                        },
                        enabled = url.trim().startsWith("http"),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Add & Sync")
                    }
                    OutlinedButton(
                        onClick = onUpdateAll,
                        enabled = subscriptions.isNotEmpty() && !updating,
                    ) {
                        Icon(Icons.Filled.CloudSync, null, modifier = Modifier.size(18.dp))
                    }
                }

                if (subscriptions.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        text = "Saved Subscriptions (${subscriptions.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    subscriptions.forEach { sub ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(sub.remarks, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    text = sub.url,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = { onDeleteSubscription(sub.id) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Delete Subscription",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
}

@Composable
private fun PasteSheet(
    value: String,
    onValueChange: (String) -> Unit,
    onPasteFromClipboard: () -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_import)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.import_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onPasteFromClipboard,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.ContentPaste, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_paste_clipboard))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    maxLines = 12,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    label = { Text(stringResource(R.string.import_label)) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSubmit, enabled = value.isNotBlank()) {
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

private fun generateQrBitmap(content: String, sizePx: Int): Bitmap? {
    if (content.isBlank() || content.length > 2900) return null
    return try {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bmp.setPixel(x, y, if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }
        bmp
    } catch (_: Throwable) {
        null
    }
}
