package app.nebulabox.ui.screens

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nebulabox.BuildConfig
import app.nebulabox.R
import app.nebulabox.engine.Engines
import app.nebulabox.locale.LocaleManager
import app.nebulabox.ui.NebulaViewModel

@Composable
fun SettingsScreen(viewModel: NebulaViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAppPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.tab_settings),
            style = MaterialTheme.typography.headlineMedium,
        )

        SectionCard(stringResource(R.string.section_appearance)) {
            Picker(
                label = stringResource(R.string.setting_language),
                options = listOf(
                    LocaleManager.SYSTEM to stringResource(R.string.lang_system),
                    LocaleManager.ENGLISH to stringResource(R.string.lang_english),
                    LocaleManager.PERSIAN to stringResource(R.string.lang_persian),
                ),
                selectedKey = LocaleManager.storedLanguage(),
                onSelect = { code ->
                    LocaleManager.storeLanguage(context, code)
                    viewModel.updateSettings { it.copy(language = code) }
                    (context as? Activity)?.recreate()
                },
            )
            Picker(
                label = stringResource(R.string.setting_theme),
                options = listOf(
                    "system" to stringResource(R.string.theme_system),
                    "light" to stringResource(R.string.theme_light),
                    "dark" to stringResource(R.string.theme_dark),
                ),
                selectedKey = settings.theme,
                onSelect = { value -> viewModel.updateSettings { it.copy(theme = value) } },
            )
            ToggleRow(
                label = stringResource(R.string.setting_dynamic_color),
                checked = settings.dynamicColor,
                onChange = { on -> viewModel.updateSettings { it.copy(dynamicColor = on) } },
            )
        }

        SectionCard("VPN & TUN Engine (v2rayNG)") {
            ToggleRow(
                label = "Use hev-socks5-tunnel (v2rayNG Default)",
                checked = settings.useHevTun,
                onChange = { on -> viewModel.updateSettings { it.copy(useHevTun = on) } },
            )
            TextRow(
                label = "VPN MTU (1280 - 1500)",
                value = settings.mtu.toString(),
                onChange = { v ->
                    val parsed = v.filter(Char::isDigit).toIntOrNull() ?: 1500
                    viewModel.updateSettings { it.copy(mtu = parsed.coerceIn(1280, 1500)) }
                },
            )
            TextRow(
                label = "Local SOCKS5 / HTTP Proxy Port",
                value = settings.socksPort.toString(),
                onChange = { v ->
                    val parsed = v.filter(Char::isDigit).toIntOrNull() ?: 10808
                    viewModel.updateSettings { it.copy(socksPort = parsed.coerceIn(1024, 65535)) }
                },
            )
            ToggleRow(
                label = "Allow Connections from LAN (0.0.0.0)",
                checked = settings.allowLan,
                onChange = { on -> viewModel.updateSettings { it.copy(allowLan = on) } },
            )
            ToggleRow(
                label = "Display Speed in Notification",
                checked = settings.showSpeedInNotification,
                onChange = { on -> viewModel.updateSettings { it.copy(showSpeedInNotification = on) } },
            )
        }

        SectionCard("Xray TLS Fragment & Anti-DPI") {
            ToggleRow(
                label = "Enable TLS Fragment & UDP Noise (finalmask)",
                checked = settings.fragmentEnabled,
                onChange = { on -> viewModel.updateSettings { it.copy(fragmentEnabled = on) } },
            )
            if (settings.fragmentEnabled) {
                Picker(
                    label = "Fragment Packets",
                    options = listOf(
                        "tlshello" to "tlshello (TLS ClientHello)",
                        "1-3" to "1-3 (First 1-3 TCP packets)",
                        "1-5" to "1-5 (First 1-5 TCP packets)",
                    ),
                    selectedKey = settings.fragmentPackets,
                    onSelect = { v -> viewModel.updateSettings { it.copy(fragmentPackets = v) } },
                )
                TextRow(
                    label = "Fragment Length (bytes, e.g. 50-100)",
                    value = settings.fragmentLength,
                    onChange = { v -> viewModel.updateSettings { it.copy(fragmentLength = v) } },
                )
                TextRow(
                    label = "Fragment Interval / Delay (ms, e.g. 10-20)",
                    value = settings.fragmentInterval,
                    onChange = { v -> viewModel.updateSettings { it.copy(fragmentInterval = v) } },
                )
            }
        }

        SectionCard(stringResource(R.string.section_routing)) {
            Picker(
                label = stringResource(R.string.setting_route_mode),
                options = listOf(
                    "global" to stringResource(R.string.route_global),
                    "white_iran" to "Bypass Iran (white_iran — .ir & Iranian IPs Direct)",
                    "rule" to stringResource(R.string.route_rule),
                    "direct" to stringResource(R.string.route_direct),
                ),
                selectedKey = settings.routeMode,
                onSelect = { value -> viewModel.updateSettings { it.copy(routeMode = value) } },
            )
            Picker(
                label = "Routing Domain Strategy",
                options = listOf(
                    "AsIs" to "AsIs (Fastest — Recommended)",
                    "IPIfNonMatch" to "IPIfNonMatch",
                    "IPOnDemand" to "IPOnDemand",
                ),
                selectedKey = settings.domainStrategy,
                onSelect = { value -> viewModel.updateSettings { it.copy(domainStrategy = value) } },
            )
            Picker(
                label = "Outbound Server Domain Resolve",
                options = listOf(
                    "happy_eyeballs" to "System DNS + Happy Eyeballs (v2rayNG Default)",
                    "asis" to "AsIs (Do not pre-resolve)",
                ),
                selectedKey = settings.outboundDomainResolve,
                onSelect = { value -> viewModel.updateSettings { it.copy(outboundDomainResolve = value) } },
            )
            ToggleRow(
                label = stringResource(R.string.setting_bypass_lan),
                checked = settings.bypassLan,
                onChange = { on -> viewModel.updateSettings { it.copy(bypassLan = on) } },
            )
            ToggleRow(
                label = stringResource(R.string.setting_bypass_cn),
                checked = settings.bypassChina,
                onChange = { on -> viewModel.updateSettings { it.copy(bypassChina = on) } },
            )
            ToggleRow(
                label = stringResource(R.string.setting_block_ads),
                checked = settings.blockAds,
                onChange = { on -> viewModel.updateSettings { it.copy(blockAds = on) } },
            )
            ToggleRow(
                label = stringResource(R.string.setting_ipv6),
                checked = settings.ipv6,
                onChange = { on -> viewModel.updateSettings { it.copy(ipv6 = on) } },
            )
        }

        SectionCard("Per-App Proxy") {
            ToggleRow(
                label = "Enable Per-App Proxy",
                checked = settings.perAppEnabled,
                onChange = { on -> viewModel.updateSettings { it.copy(perAppEnabled = on) } },
            )
            if (settings.perAppEnabled) {
                Picker(
                    label = "Per-App Mode",
                    options = listOf(
                        "exclude" to "Bypass Selected Apps (Exclude)",
                        "include" to "Proxy Only Selected Apps (Include)",
                    ),
                    selectedKey = settings.perAppMode,
                    onSelect = { v -> viewModel.updateSettings { it.copy(perAppMode = v) } },
                )
                OutlinedButton(
                    onClick = { showAppPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Select Apps (${settings.perAppPackages.size} selected)")
                }
            }
        }

        SectionCard(stringResource(R.string.section_dns)) {
            TextRow(
                label = stringResource(R.string.setting_remote_dns),
                value = settings.remoteDns,
                onChange = { v -> viewModel.updateSettings { it.copy(remoteDns = v) } },
            )
            TextRow(
                label = stringResource(R.string.setting_direct_dns),
                value = settings.directDns,
                onChange = { v -> viewModel.updateSettings { it.copy(directDns = v) } },
            )
            TextRow(
                label = "VPN DNS",
                value = settings.vpnDns,
                onChange = { v -> viewModel.updateSettings { it.copy(vpnDns = v) } },
            )
            ToggleRow(
                label = "Enable FakeDNS (198.18.0.0/15)",
                checked = settings.fakeDns,
                onChange = { on -> viewModel.updateSettings { it.copy(fakeDns = on) } },
            )
            TextRow(
                label = "Real Delay Test URL",
                value = settings.delayTestUrl,
                onChange = { v -> viewModel.updateSettings { it.copy(delayTestUrl = v) } },
            )
        }

        SectionCard(stringResource(R.string.section_tunnel)) {
            ToggleRow(
                label = stringResource(R.string.setting_sniffing),
                checked = settings.sniffing,
                onChange = { on -> viewModel.updateSettings { it.copy(sniffing = on) } },
            )
            ToggleRow(
                label = "Sniffing Route Only",
                checked = settings.routeOnly,
                onChange = { on -> viewModel.updateSettings { it.copy(routeOnly = on) } },
            )
            ToggleRow(
                label = stringResource(R.string.setting_tcp_mux),
                checked = settings.tcpMux,
                onChange = { on -> viewModel.updateSettings { it.copy(tcpMux = on) } },
            )
            ToggleRow(
                label = stringResource(R.string.setting_tcp_fast_open),
                checked = settings.tcpFastOpen,
                onChange = { on -> viewModel.updateSettings { it.copy(tcpFastOpen = on) } },
            )
            Picker(
                label = stringResource(R.string.setting_log_level),
                options = listOf("debug", "info", "warning", "error", "none")
                    .map { it to it },
                selectedKey = settings.logLevel,
                onSelect = { v -> viewModel.updateSettings { it.copy(logLevel = v) } },
            )
        }

        SectionCard(stringResource(R.string.section_about)) {
            InfoRow(stringResource(R.string.about_version), BuildConfig.VERSION_NAME)
            InfoRow(
                stringResource(R.string.about_engine),
                (Engines.active.value ?: Engines.obtain()).implementationName,
            )
            InfoRow(
                stringResource(R.string.about_engine_state),
                stringResource(
                    if ((Engines.active.value ?: Engines.obtain()).functional) {
                        R.string.engine_ready
                    } else {
                        R.string.engine_missing_title
                    },
                ),
            )
        }
    }

    if (showAppPicker) {
        PerAppPickerDialog(
            selectedPackages = settings.perAppPackages,
            onTogglePackage = { pkg, checked ->
                viewModel.updateSettings { cur ->
                    val next = if (checked) cur.perAppPackages + pkg else cur.perAppPackages - pkg
                    cur.copy(perAppPackages = next)
                }
            },
            onDismiss = { showAppPicker = false },
        )
    }
}

private data class InstalledAppItem(
    val label: String,
    val packageName: String,
    val isSystem: Boolean,
)

@Composable
private fun PerAppPickerDialog(
    selectedPackages: Set<String>,
    onTogglePackage: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }

    val allApps = remember {
        val pm = context.packageManager
        runCatching {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.packageName != context.packageName }
                .map { appInfo ->
                    val label = runCatching { pm.getApplicationLabel(appInfo).toString() }
                        .getOrDefault(appInfo.packageName)
                    val isSys = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    InstalledAppItem(label, appInfo.packageName, isSys)
                }
                .sortedWith(
                    compareByDescending<InstalledAppItem> { it.packageName in selectedPackages }
                        .thenBy { it.label.lowercase() },
                )
        }.getOrDefault(emptyList())
    }

    val filtered = remember(allApps, search, showSystem, selectedPackages) {
        val q = search.trim().lowercase()
        allApps.filter { app ->
            val sysOk = showSystem || !app.isSystem || app.packageName in selectedPackages
            val qOk = q.isEmpty() || app.label.lowercase().contains(q) || app.packageName.lowercase().contains(q)
            sysOk && qOk
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Per-App Proxy (${selectedPackages.size} selected)") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Search apps…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Show system apps", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = showSystem, onCheckedChange = { showSystem = it })
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        val checked = app.packageName in selectedPackages
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTogglePackage(app.packageName, !checked) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { onTogglePackage(app.packageName, it) },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(app.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TextRow(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Picker(
    label: String,
    options: List<Pair<String, String>>,
    selectedKey: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedKey }?.second ?: selectedKey

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (key, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(key)
                        expanded = false
                    },
                )
            }
        }
    }
}
