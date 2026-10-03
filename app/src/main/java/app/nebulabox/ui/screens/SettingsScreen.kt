package app.nebulabox.ui.screens

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nebulabox.BuildConfig
import app.nebulabox.R
import app.nebulabox.locale.LocaleManager
import app.nebulabox.ui.AppDivider
import app.nebulabox.ui.NebulaViewModel

/**
 * Settings screen modeled directly on `v2rayNG 2.3.10` (`SettingsActivity.kt` & `SettingsItem.kt`):
 *  - Clean flat list with `CollapsiblePreferenceGroupHeader` in orange (`MaterialTheme.colorScheme.secondary`)
 *  - `SettingsSwitchItem`, `SettingsListItem`, and `SettingsEditItem`
 */
@Composable
fun SettingsScreen(viewModel: NebulaViewModel) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAppPicker by rememberSaveable { mutableStateOf(false) }

    var vpnExpanded by rememberSaveable { mutableStateOf(true) }
    var routingExpanded by rememberSaveable { mutableStateOf(true) }
    var dnsExpanded by rememberSaveable { mutableStateOf(true) }
    var fragmentExpanded by rememberSaveable { mutableStateOf(true) }
    var muxExpanded by rememberSaveable { mutableStateOf(false) }
    var uiExpanded by rememberSaveable { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // 1. VPN Settings
        CollapsiblePreferenceGroupHeader(
            title = "VPN Settings",
            expanded = vpnExpanded,
            onExpandedChange = { vpnExpanded = it },
        )
        if (vpnExpanded) {
            SettingsSwitchItem(
                title = "Use hev-socks5-tunnel (v2rayNG mode)",
                summary = if (s.useHevTun) "Enabled (hev-socks5-tunnel + Xray SOCKS)" else "Disabled (Xray Native TUN)",
                checked = s.useHevTun,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(useHevTun = v) } },
            )
            SettingsSwitchItem(
                title = "Per-app proxy",
                summary = if (s.perAppEnabled) "${s.perAppPackages.size} apps selected (${s.perAppMode})" else "Proxy all apps",
                checked = s.perAppEnabled,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(perAppEnabled = v) } },
            )
            if (s.perAppEnabled) {
                SettingsListItem(
                    title = "Per-app proxy mode",
                    options = listOf("exclude" to "Bypass selected apps", "include" to "Only proxy selected apps"),
                    selectedValue = s.perAppMode,
                    onSelected = { v -> viewModel.updateSettings { it.copy(perAppMode = v) } },
                )
                SettingsMenuItem(
                    title = "Select apps for per-app proxy",
                    subtitle = "${s.perAppPackages.size} apps configured",
                    onClick = { showAppPicker = true },
                )
            }
            SettingsSwitchItem(
                title = "Bypass LAN",
                summary = "Route private local network addresses directly",
                checked = s.bypassLan,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(bypassLan = v) } },
            )
            SettingsEditItem(
                title = "VPN MTU",
                value = s.mtu.toString(),
                onValueChanged = { text ->
                    text.toIntOrNull()?.let { mtu ->
                        if (mtu in 1280..1500) viewModel.updateSettings { it.copy(mtu = mtu) }
                    }
                },
            )
            SettingsEditItem(
                title = "SOCKS5 port",
                value = s.socksPort.toString(),
                onValueChanged = { text ->
                    text.toIntOrNull()?.let { port ->
                        if (port in 1024..65535) viewModel.updateSettings { it.copy(socksPort = port) }
                    }
                },
            )
            SettingsSwitchItem(
                title = "Enable IPv6",
                summary = "Enable IPv6 on TUN interface",
                checked = s.ipv6,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(ipv6 = v) } },
            )
            SettingsSwitchItem(
                title = "Allow connections from LAN",
                summary = "Bind SOCKS5 port on 0.0.0.0",
                checked = s.allowLan,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(allowLan = v) } },
            )
            SettingsSwitchItem(
                title = "Enable speed display in notification",
                summary = "Show live uplink/downlink speed in status bar",
                checked = s.showSpeedInNotification,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(showSpeedInNotification = v) } },
            )
        }
        AppDivider()

        // 2. Routing & Sniffing Settings
        CollapsiblePreferenceGroupHeader(
            title = "Routing & Sniffing",
            expanded = routingExpanded,
            onExpandedChange = { routingExpanded = it },
        )
        if (routingExpanded) {
            SettingsListItem(
                title = "Routing preset",
                options = listOf(
                    "global" to "Global (Proxy all except LAN)",
                    "white_iran" to "Bypass LAN & Iran (domain:ir / geosite:category-ir / geoip:ir)",
                    "rule" to "Bypass LAN & Mainland China",
                    "direct" to "Direct All",
                ),
                selectedValue = s.routeMode,
                onSelected = { v -> viewModel.updateSettings { it.copy(routeMode = v) } },
            )
            SettingsListItem(
                title = "Domain strategy",
                options = listOf(
                    "AsIs" to "AsIs (v2rayNG default)",
                    "IPIfNonMatch" to "IPIfNonMatch",
                    "IPOnDemand" to "IPOnDemand",
                ),
                selectedValue = s.domainStrategy,
                onSelected = { v -> viewModel.updateSettings { it.copy(domainStrategy = v) } },
            )
            SettingsListItem(
                title = "Server domain resolve method",
                options = listOf(
                    "happy_eyeballs" to "Happy Eyeballs (Pre-resolve + UseIP)",
                    "asis" to "AsIs (Do not pre-resolve)",
                ),
                selectedValue = s.outboundDomainResolve,
                onSelected = { v -> viewModel.updateSettings { it.copy(outboundDomainResolve = v) } },
            )
            SettingsSwitchItem(
                title = "Enable traffic sniffing",
                summary = "Override destination from TLS SNI / HTTP Host (http, tls, quic)",
                checked = s.sniffing,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(sniffing = v) } },
            )
            SettingsSwitchItem(
                title = "Route only (Sniffing)",
                summary = "Use sniffed domain only for routing rules",
                checked = s.routeOnly,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(routeOnly = v) } },
            )
            SettingsSwitchItem(
                title = "Block ads (geosite:category-ads-all)",
                summary = "Block ad domains via Xray blackhole outbound",
                checked = s.blockAds,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(blockAds = v) } },
            )
        }
        AppDivider()

        // 3. DNS Settings
        CollapsiblePreferenceGroupHeader(
            title = "DNS Settings",
            expanded = dnsExpanded,
            onExpandedChange = { dnsExpanded = it },
        )
        if (dnsExpanded) {
            SettingsEditItem(
                title = "Remote DNS",
                value = s.remoteDns,
                onValueChanged = { v -> viewModel.updateSettings { it.copy(remoteDns = v.ifBlank { "https://cloudflare-dns.com/dns-query" }) } },
            )
            SettingsEditItem(
                title = "Domestic DNS",
                value = s.directDns,
                onValueChanged = { v -> viewModel.updateSettings { it.copy(directDns = v.ifBlank { "8.8.8.8" }) } },
            )
            SettingsEditItem(
                title = "VPN DNS",
                value = s.vpnDns,
                onValueChanged = { v -> viewModel.updateSettings { it.copy(vpnDns = v.ifBlank { "1.1.1.1" }) } },
            )
            SettingsSwitchItem(
                title = "Enable FakeDNS",
                summary = "Return synthetic IPs (198.18.0.0/15) for faster DNS response",
                checked = s.fakeDns,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(fakeDns = v) } },
            )
            SettingsEditItem(
                title = "Real ping test URL",
                value = s.delayTestUrl,
                onValueChanged = { v -> viewModel.updateSettings { it.copy(delayTestUrl = v.ifBlank { "https://www.gstatic.com/generate_204" }) } },
            )
        }
        AppDivider()

        // 4. Fragment Settings (Anti-DPI)
        CollapsiblePreferenceGroupHeader(
            title = "Fragment Settings (Anti-DPI)",
            expanded = fragmentExpanded,
            onExpandedChange = { fragmentExpanded = it },
        )
        if (fragmentExpanded) {
            SettingsSwitchItem(
                title = "Enable TLS Fragment",
                summary = "Split TLS ClientHello packets to bypass ISP SNI/DPI filtering",
                checked = s.fragmentEnabled,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(fragmentEnabled = v) } },
            )
            if (s.fragmentEnabled) {
                SettingsListItem(
                    title = "Fragment packets",
                    options = listOf(
                        "tlshello" to "tlshello (TLS ClientHello)",
                        "1-3" to "1-3 (TCP packets 1 to 3)",
                        "1-5" to "1-5 (TCP packets 1 to 5)",
                    ),
                    selectedValue = s.fragmentPackets,
                    onSelected = { v -> viewModel.updateSettings { it.copy(fragmentPackets = v) } },
                )
                SettingsEditItem(
                    title = "Fragment length",
                    value = s.fragmentLength,
                    onValueChanged = { v -> viewModel.updateSettings { it.copy(fragmentLength = v.ifBlank { "50-100" }) } },
                )
                SettingsEditItem(
                    title = "Fragment interval (ms)",
                    value = s.fragmentInterval,
                    onValueChanged = { v -> viewModel.updateSettings { it.copy(fragmentInterval = v.ifBlank { "10-20" }) } },
                )
            }
        }
        AppDivider()

        // 5. Mux Settings
        CollapsiblePreferenceGroupHeader(
            title = "Mux Settings",
            expanded = muxExpanded,
            onExpandedChange = { muxExpanded = it },
        )
        if (muxExpanded) {
            SettingsSwitchItem(
                title = "Enable Mux",
                summary = "Multiplex TCP/XUDP streams over a single connection",
                checked = s.tcpMux,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(tcpMux = v) } },
            )
            if (s.tcpMux) {
                SettingsEditItem(
                    title = "Mux concurrency",
                    value = s.muxConcurrency.toString(),
                    onValueChanged = { text ->
                        text.toIntOrNull()?.let { n ->
                            if (n in 1..64) viewModel.updateSettings { it.copy(muxConcurrency = n) }
                        }
                    },
                )
            }
        }
        AppDivider()

        // 6. UI & Core Settings
        CollapsiblePreferenceGroupHeader(
            title = "UI & Core Settings",
            expanded = uiExpanded,
            onExpandedChange = { uiExpanded = it },
        )
        if (uiExpanded) {
            SettingsListItem(
                title = stringResource(R.string.settings_group_appearance),
                options = listOf(
                    "system" to stringResource(R.string.lang_system),
                    "en" to stringResource(R.string.lang_english),
                    "fa" to stringResource(R.string.lang_persian),
                ),
                selectedValue = s.language,
                onSelected = { tag ->
                    viewModel.updateSettings { it.copy(language = tag) }
                    LocaleManager.storeLanguage(context, tag)
                },
            )
            SettingsListItem(
                title = "Theme",
                options = listOf(
                    "system" to stringResource(R.string.theme_system),
                    "light" to stringResource(R.string.theme_light),
                    "dark" to stringResource(R.string.theme_dark),
                ),
                selectedValue = s.theme,
                onSelected = { v -> viewModel.updateSettings { it.copy(theme = v) } },
            )
            SettingsListItem(
                title = "Core log level",
                options = listOf(
                    "debug" to "debug",
                    "info" to "info",
                    "warning" to "warning",
                    "error" to "error",
                    "none" to "none",
                ),
                selectedValue = s.logLevel,
                onSelected = { v -> viewModel.updateSettings { it.copy(logLevel = v) } },
            )
        }
        AppDivider()

        // Version Footer (exact v2rayNG VersionInfoBlock)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "${stringResource(R.string.app_name)} v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = viewModel.activeEngine?.implementationName ?: "Xray-core",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showAppPicker) {
        PerAppPickerDialog(
            selected = s.perAppPackages,
            onDismiss = { showAppPicker = false },
            onSave = { next ->
                viewModel.updateSettings { it.copy(perAppPackages = next) }
                showAppPicker = false
            },
        )
    }
}

@Composable
private fun CollapsiblePreferenceGroupHeader(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onExpandedChange(!expanded) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier
                .size(24.dp)
                .rotate(if (expanded) 180f else 0f),
        )
    }
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    summary: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!summary.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.82f),
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                checkedTrackColor = MaterialTheme.colorScheme.secondary,
            ),
        )
    }
}

@Composable
private fun SettingsListItem(
    title: String,
    options: List<Pair<String, String>>,
    selectedValue: String,
    onSelected: (String) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    val summary = options.firstOrNull { it.first == selectedValue }?.second ?: selectedValue

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                Column {
                    options.forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showDialog = false
                                    onSelected(key)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = key == selectedValue,
                                onClick = {
                                    showDialog = false
                                    onSelected(key)
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SettingsEditItem(
    title: String,
    value: String,
    onValueChanged: (String) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showDialog) {
        var text by remember(value) { mutableStateOf(value) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        onValueChanged(text.trim())
                    },
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SettingsMenuItem(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PerAppPickerDialog(
    selected: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }

    val apps = remember(showSystemApps) {
        val pm = context.packageManager
        pm.getInstalledApplications(0)
            .asSequence()
            .filter {
                it.packageName != context.packageName &&
                    (showSystemApps || (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)
            }
            .map { info ->
                val label = runCatching { pm.getApplicationLabel(info).toString() }
                    .getOrDefault(info.packageName)
                label to info.packageName
            }
            .sortedWith(compareBy({ !selected.contains(it.second) }, { it.first.lowercase() }))
            .toList()
    }

    val filteredApps = remember(apps, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) apps
        else apps.filter { (label, pkg) -> label.lowercase().contains(q) || pkg.lowercase().contains(q) }
    }

    var working by remember { mutableStateOf(selected) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Per-App Proxy") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search apps...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSystemApps = !showSystemApps },
                ) {
                    Checkbox(checked = showSystemApps, onCheckedChange = { showSystemApps = it })
                    Text("Show system apps", style = MaterialTheme.typography.bodySmall)
                }
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(filteredApps, key = { it.second }) { (label, pkg) ->
                        val checked = pkg in working
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { working = if (checked) working - pkg else working + pkg }
                                .padding(vertical = 6.dp),
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    working = if (it) working + pkg else working - pkg
                                },
                            )
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    pkg,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(working) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
