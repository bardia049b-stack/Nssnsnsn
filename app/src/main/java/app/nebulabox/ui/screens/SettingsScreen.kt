package app.nebulabox.ui.screens

import android.os.Build
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nebulabox.BuildConfig
import app.nebulabox.R
import app.nebulabox.locale.LocaleManager
import app.nebulabox.ui.NebulaViewModel
import app.nebulabox.ui.components.SettingsEditRow
import app.nebulabox.ui.components.SettingsListRow
import app.nebulabox.ui.components.SettingsMenuRow
import app.nebulabox.ui.components.SettingsSectionCard
import app.nebulabox.ui.components.SettingsSwitchRow
import app.nebulabox.ui.dialogs.AppPickerDialog

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
    var automationExpanded by rememberSaveable { mutableStateOf(true) }
    var uiExpanded by rememberSaveable { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        SettingsSectionCard(
            title = "Tunnel & VPN",
            expanded = vpnExpanded,
            onExpandedChange = { vpnExpanded = it },
        ) {
            SettingsSwitchRow(
                title = "High-performance TUN relay",
                summary = if (s.useHevTun) "Enabled (hev-socks5-tunnel + local SOCKS)" else "Disabled (Direct Core TUN)",
                checked = s.useHevTun,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(useHevTun = v) } },
            )
            SettingsSwitchRow(
                title = "Per-app proxy",
                summary = if (s.perAppEnabled) "${s.perAppPackages.size} apps selected (${s.perAppMode})" else "Proxy all installed apps",
                checked = s.perAppEnabled,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(perAppEnabled = v) } },
            )
            if (s.perAppEnabled) {
                SettingsListRow(
                    title = "Per-app mode",
                    options = listOf("exclude" to "Bypass selected apps", "include" to "Only proxy selected apps"),
                    selectedValue = s.perAppMode,
                    onSelected = { v -> viewModel.updateSettings { it.copy(perAppMode = v) } },
                )
                SettingsMenuRow(
                    title = "Select apps",
                    subtitle = "${s.perAppPackages.size} apps configured",
                    onClick = { showAppPicker = true },
                )
            }
            SettingsSwitchRow(
                title = "Bypass LAN",
                summary = "Route private local network addresses directly",
                checked = s.bypassLan,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(bypassLan = v) } },
            )
            SettingsEditRow(
                title = "VPN MTU",
                value = s.mtu.toString(),
                onValueChanged = { text ->
                    text.toIntOrNull()?.let { mtu ->
                        if (mtu in 1280..1500) viewModel.updateSettings { it.copy(mtu = mtu) }
                    }
                },
            )
            SettingsEditRow(
                title = "Local SOCKS5 port",
                value = s.socksPort.toString(),
                onValueChanged = { text ->
                    text.toIntOrNull()?.let { port ->
                        if (port in 1024..65535) viewModel.updateSettings { it.copy(socksPort = port) }
                    }
                },
            )
            SettingsSwitchRow(
                title = "Enable IPv6",
                summary = "Enable IPv6 routing on tunnel interface",
                checked = s.ipv6,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(ipv6 = v) } },
            )
            SettingsSwitchRow(
                title = "Allow LAN connections",
                summary = "Bind local proxy port on 0.0.0.0",
                checked = s.allowLan,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(allowLan = v) } },
            )
            SettingsSwitchRow(
                title = "Auto-connect after reboot",
                summary = "Reconnect to the selected server when Android finishes starting",
                checked = s.autoConnect,
                onCheckedChange = { enabled ->
                    viewModel.updateSettings { it.copy(autoConnect = enabled) }
                },
            )
            SettingsSwitchRow(
                title = "Live speed in notification",
                summary = "Show upload and download speed in status bar",
                checked = s.showSpeedInNotification,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(showSpeedInNotification = v) } },
            )
            SettingsSwitchRow(
                title = "Reconnect after network changes",
                summary = "Restart the selected tunnel after Wi-Fi or mobile network handoff",
                checked = s.reconnectOnNetworkChange,
                onCheckedChange = { enabled ->
                    viewModel.updateSettings { it.copy(reconnectOnNetworkChange = enabled) }
                },
            )
            SettingsMenuRow(
                title = "Configure Android kill switch",
                subtitle = "In system VPN settings, enable Always-on VPN and Block connections without VPN",
                onClick = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }
                },
            )
        }

        SettingsSectionCard(
            title = "Routing & Sniffing",
            expanded = routingExpanded,
            onExpandedChange = { routingExpanded = it },
        ) {
            SettingsListRow(
                title = "Routing preset",
                options = listOf(
                    "global" to "Global (Proxy all except LAN)",
                    "white_iran" to "Bypass LAN & Iran",
                    "rule" to "Bypass LAN & Mainland China",
                    "direct" to "Direct All",
                ),
                selectedValue = s.routeMode,
                onSelected = { v -> viewModel.updateSettings { it.copy(routeMode = v) } },
            )
            SettingsListRow(
                title = "Domain strategy",
                options = listOf(
                    "AsIs" to "AsIs (Default)",
                    "IPIfNonMatch" to "IPIfNonMatch",
                    "IPOnDemand" to "IPOnDemand",
                ),
                selectedValue = s.domainStrategy,
                onSelected = { v -> viewModel.updateSettings { it.copy(domainStrategy = v) } },
            )
            SettingsListRow(
                title = "Server domain resolve method",
                options = listOf(
                    "happy_eyeballs" to "Happy Eyeballs (Pre-resolve + UseIP)",
                    "asis" to "AsIs (Do not pre-resolve)",
                ),
                selectedValue = s.outboundDomainResolve,
                onSelected = { v -> viewModel.updateSettings { it.copy(outboundDomainResolve = v) } },
            )
            SettingsSwitchRow(
                title = "Traffic sniffing",
                summary = "Override destination from TLS SNI / HTTP Host",
                checked = s.sniffing,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(sniffing = v) } },
            )
            SettingsSwitchRow(
                title = "Route only (Sniffing)",
                summary = "Use sniffed domain only for routing rules",
                checked = s.routeOnly,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(routeOnly = v) } },
            )
            SettingsSwitchRow(
                title = "Block ads",
                summary = "Block advertising domains",
                checked = s.blockAds,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(blockAds = v) } },
            )
        }

        SettingsSectionCard(
            title = "DNS Configuration",
            expanded = dnsExpanded,
            onExpandedChange = { dnsExpanded = it },
        ) {
            SettingsSwitchRow(
                title = "Enable Local DNS",
                summary = "Intercept port 53 to internal dns-out",
                checked = s.localDnsEnabled,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(localDnsEnabled = v) } },
            )
            SettingsEditRow(
                title = "Remote DNS",
                value = s.remoteDns,
                onValueChanged = { v -> viewModel.updateSettings { it.copy(remoteDns = v.ifBlank { "https://cloudflare-dns.com/dns-query" }) } },
            )
            SettingsEditRow(
                title = "Direct DNS",
                value = s.directDns,
                onValueChanged = { v -> viewModel.updateSettings { it.copy(directDns = v.ifBlank { "223.5.5.5" }) } },
            )
            SettingsEditRow(
                title = "VPN DNS",
                value = s.vpnDns,
                onValueChanged = { v -> viewModel.updateSettings { it.copy(vpnDns = v.ifBlank { "1.1.1.1" }) } },
            )
            SettingsSwitchRow(
                title = "Enable FakeDNS",
                summary = "Return synthetic IPs (198.18.0.0/15) for faster DNS response",
                checked = s.fakeDns,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(fakeDns = v) } },
            )
            SettingsEditRow(
                title = "Real ping test URL",
                value = s.delayTestUrl,
                onValueChanged = { v -> viewModel.updateSettings { it.copy(delayTestUrl = v.ifBlank { "https://www.gstatic.com/generate_204" }) } },
            )
        }

        SettingsSectionCard(
            title = "TLS Fragment (Anti-DPI)",
            expanded = fragmentExpanded,
            onExpandedChange = { fragmentExpanded = it },
        ) {
            SettingsSwitchRow(
                title = "Enable TLS Fragment",
                summary = if (s.fragmentEnabled) "${s.fragmentPackets} | len=${s.fragmentLength} | delay=${s.fragmentInterval}ms" else "Split TLS ClientHello packets to bypass DPI",
                checked = s.fragmentEnabled,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(fragmentEnabled = v) } },
            )
            if (s.fragmentEnabled) {
                SettingsListRow(
                    title = "Fragment packets",
                    options = listOf(
                        "tlshello" to "tlshello (TLS ClientHello only)",
                        "1-2" to "1-2 (First 1-2 TCP packets)",
                        "1-3" to "1-3 (First 1-3 TCP packets)",
                        "1-5" to "1-5 (First 1-5 TCP packets)",
                    ),
                    selectedValue = s.fragmentPackets,
                    onSelected = { v -> viewModel.updateSettings { it.copy(fragmentPackets = v) } },
                )
                SettingsEditRow(
                    title = "Fragment length (bytes)",
                    value = s.fragmentLength,
                    onValueChanged = { v -> viewModel.updateSettings { it.copy(fragmentLength = v.ifBlank { "50-100" }) } },
                )
                SettingsEditRow(
                    title = "Fragment interval (ms)",
                    value = s.fragmentInterval,
                    onValueChanged = { v -> viewModel.updateSettings { it.copy(fragmentInterval = v.ifBlank { "10-20" }) } },
                )
            }
        }

        SettingsSectionCard(
            title = "Multiplex (Mux)",
            expanded = muxExpanded,
            onExpandedChange = { muxExpanded = it },
        ) {
            SettingsSwitchRow(
                title = "Enable Multiplex (Mux)",
                summary = "Multiplex TCP connections over a single stream",
                checked = s.tcpMux,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(tcpMux = v) } },
            )
            if (s.tcpMux) {
                SettingsEditRow(
                    title = "Mux concurrency",
                    value = s.muxConcurrency.toString(),
                    onValueChanged = { text ->
                        text.toIntOrNull()?.let { c ->
                            if (c in 1..64) viewModel.updateSettings { it.copy(muxConcurrency = c) }
                        }
                    },
                )
                SettingsEditRow(
                    title = "XUDP concurrency",
                    value = s.muxXudpConcurrency.toString(),
                    onValueChanged = { text ->
                        text.toIntOrNull()?.let { c ->
                            if (c in 1..64) viewModel.updateSettings { it.copy(muxXudpConcurrency = c) }
                        }
                    },
                )
            }
            SettingsSwitchRow(
                title = "TCP Fast Open",
                summary = "Reduce initial handshake latency where supported",
                checked = s.tcpFastOpen,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(tcpFastOpen = v) } },
            )
        }

        SettingsSectionCard(
            title = "Automation & Notifications",
            expanded = automationExpanded,
            onExpandedChange = { automationExpanded = it },
        ) {
            SettingsSwitchRow(
                title = "Auto-update subscriptions",
                summary = if (s.autoUpdateSubscriptions) {
                    "Enabled · every ${s.subscriptionUpdateIntervalHours} hours, with a notification"
                } else {
                    "Off · subscription groups update only when you ask"
                },
                checked = s.autoUpdateSubscriptions,
                onCheckedChange = { enabled ->
                    viewModel.updateSettings { it.copy(autoUpdateSubscriptions = enabled) }
                },
            )
            if (s.autoUpdateSubscriptions) {
                SettingsListRow(
                    title = "Subscription update interval",
                    options = listOf(
                        "6" to "Every 6 hours",
                        "12" to "Every 12 hours",
                        "24" to "Every 24 hours",
                    ),
                    selectedValue = s.subscriptionUpdateIntervalHours.toString(),
                    onSelected = { hours ->
                        viewModel.updateSettings { it.copy(subscriptionUpdateIntervalHours = hours.toIntOrNull() ?: 12) }
                    },
                )
                SettingsSwitchRow(
                    title = "Notify after subscription update",
                    summary = "Show updated groups and imported server counts",
                    checked = s.notifySubscriptionUpdates,
                    onCheckedChange = { enabled ->
                        viewModel.updateSettings { it.copy(notifySubscriptionUpdates = enabled) }
                    },
                )
            }
            SettingsSwitchRow(
                title = "Check for new app versions",
                summary = if (s.autoCheckAppUpdates) {
                    "Check GitHub Releases daily and alert when a newer version is published"
                } else {
                    "Off · use Check for updates to run a manual check"
                },
                checked = s.autoCheckAppUpdates,
                onCheckedChange = { enabled ->
                    viewModel.updateSettings { it.copy(autoCheckAppUpdates = enabled) }
                },
            )
            SettingsSwitchRow(
                title = "Notify about new versions",
                summary = "Show one alert for each new GitHub release",
                checked = s.notifyAppUpdates,
                onCheckedChange = { enabled ->
                    viewModel.updateSettings { it.copy(notifyAppUpdates = enabled) }
                },
            )
            SettingsMenuRow(
                title = "Check for updates now",
                subtitle = "Check the latest JavidTun release on GitHub",
                onClick = { viewModel.checkForAppUpdates() },
            )
            SettingsSwitchRow(
                title = "Clipboard import prompt",
                summary = "Ask before importing a copied link or config when the app opens",
                checked = s.clipboardAutoImport,
                onCheckedChange = { enabled ->
                    viewModel.updateSettings { it.copy(clipboardAutoImport = enabled) }
                },
            )
            SettingsSwitchRow(
                title = "Slow server alert",
                summary = "Notify after a latency test exceeds the chosen limit",
                checked = s.notifySlowServers,
                onCheckedChange = { enabled ->
                    viewModel.updateSettings { it.copy(notifySlowServers = enabled) }
                },
            )
            if (s.notifySlowServers) {
                SettingsListRow(
                    title = "Slow server threshold",
                    options = listOf(
                        "300" to "300 ms",
                        "500" to "500 ms",
                        "800" to "800 ms",
                        "1200" to "1200 ms",
                    ),
                    selectedValue = s.slowServerThresholdMs.toString(),
                    onSelected = { threshold ->
                        viewModel.updateSettings { it.copy(slowServerThresholdMs = threshold.toIntOrNull() ?: 500) }
                    },
                )
            }
        }

        SettingsSectionCard(
            title = "Appearance & Core",
            expanded = uiExpanded,
            onExpandedChange = { uiExpanded = it },
        ) {
            SettingsListRow(
                title = stringResource(R.string.setting_language),
                options = listOf(
                    "system" to stringResource(R.string.lang_system),
                    "en" to stringResource(R.string.lang_english),
                    "fa" to stringResource(R.string.lang_persian),
                ),
                selectedValue = s.language,
                onSelected = { code ->
                    viewModel.updateSettings { it.copy(language = code) }
                    LocaleManager.storeLanguage(context, code)
                    LocaleManager.restart(context)
                },
            )
            SettingsListRow(
                title = stringResource(R.string.setting_theme),
                options = listOf(
                    "system" to stringResource(R.string.theme_system),
                    "light" to stringResource(R.string.theme_light),
                    "dark" to stringResource(R.string.theme_dark),
                ),
                selectedValue = s.theme,
                onSelected = { v -> viewModel.updateSettings { it.copy(theme = v) } },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SettingsSwitchRow(
                    title = stringResource(R.string.setting_dynamic_color),
                    summary = stringResource(R.string.use_material_you_wallpaper_colors_on_android_12),
                    checked = s.dynamicColor,
                    onCheckedChange = { v -> viewModel.updateSettings { it.copy(dynamicColor = v) } },
                )
            } else {
                SettingsMenuRow(
                    title = stringResource(R.string.setting_dynamic_color),
                    subtitle = stringResource(R.string.dynamic_color_unsupported),
                    onClick = {},
                )
            }
            SettingsListRow(
                title = stringResource(R.string.setting_log_level),
                options = listOf(
                    "debug" to "Debug",
                    "info" to "Info",
                    "warning" to "Warning",
                    "error" to "Error",
                    "none" to "None",
                ),
                selectedValue = s.logLevel,
                onSelected = { v -> viewModel.updateSettings { it.copy(logLevel = v) } },
            )
            SettingsMenuRow(
                title = stringResource(R.string.about_version),
                subtitle = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                onClick = {},
            )
            SettingsMenuRow(
                title = stringResource(R.string.about_engine),
                subtitle = viewModel.activeEngine?.implementationName ?: "JavidTun Core",
                onClick = {},
            )
        }

        Spacer(Modifier.height(32.dp))
    }

    if (showAppPicker) {
        AppPickerDialog(
            context = context,
            selectedPackages = s.perAppPackages,
            onDismiss = { showAppPicker = false },
            onSave = { selected ->
                viewModel.updateSettings { it.copy(perAppPackages = selected) }
                showAppPicker = false
            },
        )
    }
}
