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
            title = stringResource(R.string.tunnel_vpn),
            expanded = vpnExpanded,
            onExpandedChange = { vpnExpanded = it },
        ) {
            SettingsSwitchRow(
                title = stringResource(R.string.high_performance_tun_relay),
                summary = if (s.useHevTun) "Enabled (hev-socks5-tunnel + local SOCKS)" else "Disabled (Direct Core TUN)",
                checked = s.useHevTun,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(useHevTun = v) } },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.per_app_proxy_386),
                summary = if (s.perAppEnabled) "${s.perAppPackages.size} apps selected (${s.perAppMode})" else "Proxy all installed apps",
                checked = s.perAppEnabled,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(perAppEnabled = v) } },
            )
            if (s.perAppEnabled) {
                SettingsListRow(
                    title = stringResource(R.string.per_app_mode),
                    options = listOf("exclude" to "Bypass selected apps", "include" to "Only proxy selected apps"),
                    selectedValue = s.perAppMode,
                    onSelected = { v -> viewModel.updateSettings { it.copy(perAppMode = v) } },
                )
                SettingsMenuRow(
                    title = stringResource(R.string.select_apps),
                    subtitle = "${s.perAppPackages.size} apps configured",
                    onClick = { showAppPicker = true },
                )
            }
            SettingsSwitchRow(
                title = stringResource(R.string.bypass_lan),
                summary = stringResource(R.string.route_private_local_network_addresses_directly),
                checked = s.bypassLan,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(bypassLan = v) } },
            )
            SettingsEditRow(
                title = stringResource(R.string.vpn_mtu),
                value = s.mtu.toString(),
                onValueChanged = { text ->
                    text.toIntOrNull()?.let { mtu ->
                        if (mtu in 1280..1500) viewModel.updateSettings { it.copy(mtu = mtu) }
                    }
                },
            )
            SettingsEditRow(
                title = stringResource(R.string.local_socks5_port),
                value = s.socksPort.toString(),
                onValueChanged = { text ->
                    text.toIntOrNull()?.let { port ->
                        if (port in 1024..65535) viewModel.updateSettings { it.copy(socksPort = port) }
                    }
                },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.enable_ipv6),
                summary = stringResource(R.string.enable_ipv6_routing_on_tunnel_interface),
                checked = s.ipv6,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(ipv6 = v) } },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.allow_lan_connections),
                summary = stringResource(R.string.bind_local_proxy_port_on_0_0_0_0),
                checked = s.allowLan,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(allowLan = v) } },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.auto_connect_after_reboot),
                summary = stringResource(R.string.reconnect_to_the_selected_server_when_android_fi),
                checked = s.autoConnect,
                onCheckedChange = { enabled ->
                    viewModel.updateSettings { it.copy(autoConnect = enabled) }
                },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.live_speed_in_notification),
                summary = stringResource(R.string.show_upload_and_download_speed_in_status_bar),
                checked = s.showSpeedInNotification,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(showSpeedInNotification = v) } },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.reconnect_after_network_changes),
                summary = stringResource(R.string.restart_the_selected_tunnel_after_wi_fi_or_mobil),
                checked = s.reconnectOnNetworkChange,
                onCheckedChange = { enabled ->
                    viewModel.updateSettings { it.copy(reconnectOnNetworkChange = enabled) }
                },
            )
            SettingsMenuRow(
                title = stringResource(R.string.configure_android_kill_switch),
                subtitle = stringResource(R.string.in_system_vpn_settings_enable_always_on_vpn_and_),
                onClick = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }
                },
            )
        }

        SettingsSectionCard(
            title = stringResource(R.string.routing_sniffing),
            expanded = routingExpanded,
            onExpandedChange = { routingExpanded = it },
        ) {
            SettingsListRow(
                title = stringResource(R.string.routing_preset),
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
                title = stringResource(R.string.domain_strategy),
                options = listOf(
                    "AsIs" to "AsIs (Default)",
                    "IPIfNonMatch" to "IPIfNonMatch",
                    "IPOnDemand" to "IPOnDemand",
                ),
                selectedValue = s.domainStrategy,
                onSelected = { v -> viewModel.updateSettings { it.copy(domainStrategy = v) } },
            )
            SettingsListRow(
                title = stringResource(R.string.server_domain_resolve_method),
                options = listOf(
                    "happy_eyeballs" to "Happy Eyeballs (Pre-resolve + UseIP)",
                    "asis" to "AsIs (Do not pre-resolve)",
                ),
                selectedValue = s.outboundDomainResolve,
                onSelected = { v -> viewModel.updateSettings { it.copy(outboundDomainResolve = v) } },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.traffic_sniffing_333),
                summary = stringResource(R.string.override_destination_from_tls_sni_http_host),
                checked = s.sniffing,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(sniffing = v) } },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.route_only_sniffing),
                summary = stringResource(R.string.use_sniffed_domain_only_for_routing_rules),
                checked = s.routeOnly,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(routeOnly = v) } },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.block_ads),
                summary = stringResource(R.string.block_advertising_domains),
                checked = s.blockAds,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(blockAds = v) } },
            )
        }

        SettingsSectionCard(
            title = stringResource(R.string.dns_configuration),
            expanded = dnsExpanded,
            onExpandedChange = { dnsExpanded = it },
        ) {
            SettingsSwitchRow(
                title = stringResource(R.string.enable_local_dns),
                summary = stringResource(R.string.intercept_port_53_to_internal_dns_out),
                checked = s.localDnsEnabled,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(localDnsEnabled = v) } },
            )
            SettingsEditRow(
                title = stringResource(R.string.remote_dns),
                value = s.remoteDns,
                onValueChanged = { v -> viewModel.updateSettings { it.copy(remoteDns = v.ifBlank { "https://cloudflare-dns.com/dns-query" }) } },
            )
            SettingsEditRow(
                title = stringResource(R.string.direct_dns),
                value = s.directDns,
                onValueChanged = { v -> viewModel.updateSettings { it.copy(directDns = v.ifBlank { "223.5.5.5" }) } },
            )
            SettingsEditRow(
                title = stringResource(R.string.vpn_dns),
                value = s.vpnDns,
                onValueChanged = { v -> viewModel.updateSettings { it.copy(vpnDns = v.ifBlank { "1.1.1.1" }) } },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.enable_fakedns),
                summary = stringResource(R.string.return_synthetic_ips_198_18_0_0_15_for_faster_dn),
                checked = s.fakeDns,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(fakeDns = v) } },
            )
            SettingsEditRow(
                title = stringResource(R.string.real_ping_test_url),
                value = s.delayTestUrl,
                onValueChanged = { v -> viewModel.updateSettings { it.copy(delayTestUrl = v.ifBlank { "https://www.gstatic.com/generate_204" }) } },
            )
        }

        SettingsSectionCard(
            title = stringResource(R.string.tls_fragment_anti_dpi),
            expanded = fragmentExpanded,
            onExpandedChange = { fragmentExpanded = it },
        ) {
            SettingsSwitchRow(
                title = stringResource(R.string.enable_tls_fragment),
                summary = if (s.fragmentEnabled) "${s.fragmentPackets} | len=${s.fragmentLength} | delay=${s.fragmentInterval}ms" else "Split TLS ClientHello packets to bypass DPI",
                checked = s.fragmentEnabled,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(fragmentEnabled = v) } },
            )
            if (s.fragmentEnabled) {
                SettingsListRow(
                    title = stringResource(R.string.fragment_packets),
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
                    title = stringResource(R.string.fragment_length_bytes),
                    value = s.fragmentLength,
                    onValueChanged = { v -> viewModel.updateSettings { it.copy(fragmentLength = v.ifBlank { "50-100" }) } },
                )
                SettingsEditRow(
                    title = stringResource(R.string.fragment_interval_ms),
                    value = s.fragmentInterval,
                    onValueChanged = { v -> viewModel.updateSettings { it.copy(fragmentInterval = v.ifBlank { "10-20" }) } },
                )
            }
        }

        SettingsSectionCard(
            title = stringResource(R.string.multiplex_mux),
            expanded = muxExpanded,
            onExpandedChange = { muxExpanded = it },
        ) {
            SettingsSwitchRow(
                title = stringResource(R.string.enable_multiplex_mux),
                summary = stringResource(R.string.multiplex_tcp_connections_over_a_single_stream),
                checked = s.tcpMux,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(tcpMux = v) } },
            )
            if (s.tcpMux) {
                SettingsEditRow(
                    title = stringResource(R.string.mux_concurrency),
                    value = s.muxConcurrency.toString(),
                    onValueChanged = { text ->
                        text.toIntOrNull()?.let { c ->
                            if (c in 1..64) viewModel.updateSettings { it.copy(muxConcurrency = c) }
                        }
                    },
                )
                SettingsEditRow(
                    title = stringResource(R.string.xudp_concurrency),
                    value = s.muxXudpConcurrency.toString(),
                    onValueChanged = { text ->
                        text.toIntOrNull()?.let { c ->
                            if (c in 1..64) viewModel.updateSettings { it.copy(muxXudpConcurrency = c) }
                        }
                    },
                )
            }
            SettingsSwitchRow(
                title = stringResource(R.string.tcp_fast_open),
                summary = stringResource(R.string.reduce_initial_handshake_latency_where_supported),
                checked = s.tcpFastOpen,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(tcpFastOpen = v) } },
            )
        }

        SettingsSectionCard(
            title = stringResource(R.string.automation_notifications),
            expanded = automationExpanded,
            onExpandedChange = { automationExpanded = it },
        ) {
            SettingsSwitchRow(
                title = stringResource(R.string.auto_update_subscriptions),
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
                    title = stringResource(R.string.subscription_update_interval),
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
                    title = stringResource(R.string.notify_after_subscription_update),
                    summary = stringResource(R.string.show_updated_groups_and_imported_server_counts),
                    checked = s.notifySubscriptionUpdates,
                    onCheckedChange = { enabled ->
                        viewModel.updateSettings { it.copy(notifySubscriptionUpdates = enabled) }
                    },
                )
            }
            SettingsSwitchRow(
                title = stringResource(R.string.check_for_new_app_versions),
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
                title = stringResource(R.string.notify_about_new_versions),
                summary = stringResource(R.string.show_one_alert_for_each_new_github_release),
                checked = s.notifyAppUpdates,
                onCheckedChange = { enabled ->
                    viewModel.updateSettings { it.copy(notifyAppUpdates = enabled) }
                },
            )
            SettingsMenuRow(
                title = stringResource(R.string.check_for_updates_now),
                subtitle = stringResource(R.string.check_the_latest_javidtun_release_on_github),
                onClick = { viewModel.checkForAppUpdates() },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.clipboard_import_prompt),
                summary = stringResource(R.string.ask_before_importing_a_copied_link_or_config_whe),
                checked = s.clipboardAutoImport,
                onCheckedChange = { enabled ->
                    viewModel.updateSettings { it.copy(clipboardAutoImport = enabled) }
                },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.slow_server_alert),
                summary = stringResource(R.string.notify_after_a_latency_test_exceeds_the_chosen_l),
                checked = s.notifySlowServers,
                onCheckedChange = { enabled ->
                    viewModel.updateSettings { it.copy(notifySlowServers = enabled) }
                },
            )
            if (s.notifySlowServers) {
                SettingsListRow(
                    title = stringResource(R.string.slow_server_threshold),
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
            title = stringResource(R.string.appearance_core),
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
