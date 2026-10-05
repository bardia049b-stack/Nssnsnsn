package app.nebulabox.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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

@Composable
fun SettingsScreen(
    viewModel: NebulaViewModel,
    onOpenLogs: () -> Unit = {},
) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    var tunnelExpanded by rememberSaveable { mutableStateOf(false) }
    var routingExpanded by rememberSaveable { mutableStateOf(false) }
    var dnsExpanded by rememberSaveable { mutableStateOf(false) }
    var fragmentExpanded by rememberSaveable { mutableStateOf(false) }
    var muxExpanded by rememberSaveable { mutableStateOf(false) }

    val searching = query.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.settings_search_hint)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = MaterialTheme.shapes.medium,
        )

        // ---------- everyday settings ----------
        SettingsSectionCard(
            title = stringResource(R.string.section_common),
            expanded = true,
            onExpandedChange = {},
            collapsible = false,
        ) {
            Filtered(query, stringResource(R.string.setting_language), "language", "زبان") {
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
                    },
                )
            }
            Filtered(query, stringResource(R.string.setting_theme), "theme", "تم") {
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
            }
            Filtered(query, stringResource(R.string.setting_dynamic_color), "material you", "رنگ پویا") {
                SettingsSwitchRow(
                    title = stringResource(R.string.setting_dynamic_color),
                    summary = stringResource(R.string.setting_dynamic_color_summary),
                    checked = s.dynamicColor,
                    onCheckedChange = { v -> viewModel.updateSettings { it.copy(dynamicColor = v) } },
                )
            }
            Filtered(query, stringResource(R.string.setting_auto_connect), "auto connect", "اتصال خودکار") {
                SettingsSwitchRow(
                    title = stringResource(R.string.setting_auto_connect),
                    summary = stringResource(R.string.setting_auto_connect_summary),
                    checked = s.autoConnect,
                    onCheckedChange = { v -> viewModel.updateSettings { it.copy(autoConnect = v) } },
                )
            }
            Filtered(query, stringResource(R.string.auto_reconnect), "reconnect", "اتصال دوباره") {
                SettingsSwitchRow(
                    title = stringResource(R.string.auto_reconnect),
                    summary = stringResource(R.string.auto_reconnect_summary),
                    checked = s.autoReconnect,
                    onCheckedChange = { v -> viewModel.updateSettings { it.copy(autoReconnect = v) } },
                )
            }
            Filtered(query, stringResource(R.string.setting_speed_notification), "notification", "اعلان", "سرعت") {
                SettingsSwitchRow(
                    title = stringResource(R.string.setting_speed_notification),
                    summary = stringResource(R.string.setting_speed_notification_summary),
                    checked = s.showSpeedInNotification,
                    onCheckedChange = { v -> viewModel.updateSettings { it.copy(showSpeedInNotification = v) } },
                )
            }
            Filtered(query, stringResource(R.string.setting_notify_slow), "slow", "کند") {
                SettingsSwitchRow(
                    title = stringResource(R.string.setting_notify_slow),
                    summary = stringResource(R.string.setting_notify_slow_summary),
                    checked = s.notifySlowServers,
                    onCheckedChange = { v -> viewModel.updateSettings { it.copy(notifySlowServers = v) } },
                )
            }
            if (s.notifySlowServers) {
                Filtered(query, stringResource(R.string.setting_slow_threshold)) {
                    SettingsEditRow(
                        title = stringResource(R.string.setting_slow_threshold),
                        value = s.slowServerThresholdMs.toString(),
                        onValueChanged = { text ->
                            text.toIntOrNull()?.let { ms ->
                                if (ms in 100..5000) viewModel.updateSettings { it.copy(slowServerThresholdMs = ms) }
                            }
                        },
                    )
                }
            }
            Filtered(query, stringResource(R.string.setting_clipboard_import), "clipboard", "کلیپ‌بورد") {
                SettingsSwitchRow(
                    title = stringResource(R.string.setting_clipboard_import),
                    summary = stringResource(R.string.setting_clipboard_import_summary),
                    checked = s.clipboardAutoImport,
                    onCheckedChange = { v -> viewModel.updateSettings { it.copy(clipboardAutoImport = v) } },
                )
            }
            Filtered(query, stringResource(R.string.setting_remote_dns), "dns") {
                SettingsEditRow(
                    title = stringResource(R.string.setting_remote_dns),
                    value = s.remoteDns,
                    onValueChanged = { v ->
                        viewModel.updateSettings { it.copy(remoteDns = v.ifBlank { "https://cloudflare-dns.com/dns-query" }) }
                    },
                )
            }
            Filtered(query, stringResource(R.string.configure_android_kill_switch), "kill", "کیل") {
                SettingsMenuRow(
                    title = stringResource(R.string.configure_android_kill_switch),
                    subtitle = stringResource(R.string.in_system_vpn_settings_enable_always_on_vpn_and_),
                    onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) } },
                )
            }
            Filtered(query, stringResource(R.string.setting_open_logs), "logs", "لاگ", "گزارش") {
                SettingsMenuRow(
                    title = stringResource(R.string.setting_open_logs),
                    subtitle = stringResource(R.string.setting_open_logs_summary),
                    onClick = onOpenLogs,
                )
            }
        }

        // ---------- the technical half ----------
        SettingsSectionCard(
            title = stringResource(R.string.section_advanced),
            expanded = advancedExpanded || searching,
            onExpandedChange = { advancedExpanded = it },
        ) {
            Text(
                text = stringResource(R.string.section_advanced_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )

            SettingsSectionCard(
                title = stringResource(R.string.setting_tunnel_vpn),
                expanded = tunnelExpanded || searching,
                onExpandedChange = { tunnelExpanded = it },
                nested = true,
            ) {
                Filtered(query, stringResource(R.string.setting_high_perf_tunnel), "hev", "tun") {
                    SettingsSwitchRow(
                        title = stringResource(R.string.setting_high_perf_tunnel),
                        summary = if (s.useHevTun) {
                            stringResource(R.string.setting_high_perf_tunnel_on)
                        } else {
                            stringResource(R.string.setting_high_perf_tunnel_off)
                        },
                        checked = s.useHevTun,
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(useHevTun = v) } },
                    )
                }
                Filtered(query, stringResource(R.string.setting_bypass_lan), "lan") {
                    SettingsSwitchRow(
                        title = stringResource(R.string.setting_bypass_lan),
                        summary = stringResource(R.string.setting_bypass_lan_summary),
                        checked = s.bypassLan,
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(bypassLan = v) } },
                    )
                }
                Filtered(query, stringResource(R.string.setting_ipv6), "ipv6") {
                    SettingsSwitchRow(
                        title = stringResource(R.string.setting_ipv6),
                        summary = stringResource(R.string.setting_ipv6_summary),
                        checked = s.ipv6,
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(ipv6 = v) } },
                    )
                }
                Filtered(query, stringResource(R.string.setting_vpn_mtu), "mtu") {
                    SettingsEditRow(
                        title = stringResource(R.string.setting_vpn_mtu),
                        value = s.mtu.toString(),
                        onValueChanged = { text ->
                            text.toIntOrNull()?.let { mtu ->
                                if (mtu in 1280..1500) viewModel.updateSettings { it.copy(mtu = mtu) }
                            }
                        },
                    )
                }
                Filtered(query, stringResource(R.string.setting_socks_port), "socks", "پورت") {
                    SettingsEditRow(
                        title = stringResource(R.string.setting_socks_port),
                        value = s.socksPort.toString(),
                        onValueChanged = { text ->
                            text.toIntOrNull()?.let { port ->
                                if (port in 1024..65535) viewModel.updateSettings { it.copy(socksPort = port) }
                            }
                        },
                    )
                }
                Filtered(query, stringResource(R.string.setting_socks_auth), "socks", "رمز") {
                    SettingsSwitchRow(
                        title = stringResource(R.string.setting_socks_auth),
                        summary = stringResource(R.string.setting_socks_auth_summary),
                        checked = s.socksAuth,
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(socksAuth = v) } },
                    )
                }
                if (s.socksAuth) {
                    Filtered(query, stringResource(R.string.setting_socks_password)) {
                        SettingsEditRow(
                            title = stringResource(R.string.setting_socks_password),
                            value = s.socksPassword,
                            onValueChanged = { v ->
                                if (v.length in 1..64) viewModel.updateSettings { it.copy(socksPassword = v) }
                            },
                        )
                    }
                }
                Filtered(query, stringResource(R.string.setting_allow_lan), "lan") {
                    SettingsSwitchRow(
                        title = stringResource(R.string.setting_allow_lan),
                        summary = stringResource(R.string.setting_allow_lan_summary),
                        checked = s.allowLan,
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(allowLan = v) } },
                    )
                }
            }

            SettingsSectionCard(
                title = stringResource(R.string.setting_routing_sniffing),
                expanded = routingExpanded || searching,
                onExpandedChange = { routingExpanded = it },
                nested = true,
            ) {
                Filtered(query, stringResource(R.string.setting_routing_preset), "routing", "پریست") {
                    SettingsListRow(
                        title = stringResource(R.string.setting_routing_preset),
                        options = listOf(
                            "global" to stringResource(R.string.route_global),
                            "white_iran" to stringResource(R.string.setting_route_white_iran),
                            "rule" to stringResource(R.string.route_rule),
                            "direct" to stringResource(R.string.route_direct),
                        ),
                        selectedValue = s.routeMode,
                        onSelected = { v -> viewModel.updateSettings { it.copy(routeMode = v) } },
                    )
                }
                Filtered(query, stringResource(R.string.setting_bypass_cn), "china") {
                    SettingsSwitchRow(
                        title = stringResource(R.string.setting_bypass_cn),
                        summary = stringResource(R.string.setting_bypass_cn_summary),
                        checked = s.bypassChina,
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(bypassChina = v) } },
                    )
                }
                Filtered(query, stringResource(R.string.setting_sniffing), "sniff") {
                    SettingsSwitchRow(
                        title = stringResource(R.string.setting_sniffing),
                        summary = stringResource(R.string.setting_sniffing_summary),
                        checked = s.sniffing,
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(sniffing = v) } },
                    )
                }
                if (s.sniffing) {
                    Filtered(query, stringResource(R.string.setting_route_only)) {
                        SettingsSwitchRow(
                            title = stringResource(R.string.setting_route_only),
                            summary = stringResource(R.string.setting_route_only_summary),
                            checked = s.routeOnly,
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(routeOnly = v) } },
                        )
                    }
                }
                Filtered(query, stringResource(R.string.setting_block_ads), "ads", "تبلیغ") {
                    SettingsSwitchRow(
                        title = stringResource(R.string.setting_block_ads),
                        summary = stringResource(R.string.setting_block_ads_summary),
                        checked = s.blockAds,
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(blockAds = v) } },
                    )
                }
                Filtered(query, stringResource(R.string.setting_domain_strategy)) {
                    SettingsListRow(
                        title = stringResource(R.string.setting_domain_strategy),
                        options = listOf(
                            "AsIs" to "AsIs",
                            "IPIfNonMatch" to "IPIfNonMatch",
                            "IPOnDemand" to "IPOnDemand",
                        ),
                        selectedValue = s.domainStrategy,
                        onSelected = { v -> viewModel.updateSettings { it.copy(domainStrategy = v) } },
                    )
                }
                Filtered(query, stringResource(R.string.setting_server_resolve)) {
                    SettingsListRow(
                        title = stringResource(R.string.setting_server_resolve),
                        options = listOf(
                            "happy_eyeballs" to "Happy Eyeballs",
                            "asis" to "AsIs",
                        ),
                        selectedValue = s.outboundDomainResolve,
                        onSelected = { v -> viewModel.updateSettings { it.copy(outboundDomainResolve = v) } },
                    )
                }
            }

            SettingsSectionCard(
                title = stringResource(R.string.section_dns),
                expanded = dnsExpanded || searching,
                onExpandedChange = { dnsExpanded = it },
                nested = true,
            ) {
                Filtered(query, stringResource(R.string.setting_direct_dns), "dns") {
                    SettingsEditRow(
                        title = stringResource(R.string.setting_direct_dns),
                        value = s.directDns,
                        onValueChanged = { v -> viewModel.updateSettings { it.copy(directDns = v.ifBlank { "223.5.5.5" }) } },
                    )
                }
                Filtered(query, "vpn dns", "dns", stringResource(R.string.setting_vpn_dns)) {
                    SettingsEditRow(
                        title = stringResource(R.string.setting_vpn_dns),
                        value = s.vpnDns,
                        onValueChanged = { v -> viewModel.updateSettings { it.copy(vpnDns = v.ifBlank { "1.1.1.1" }) } },
                    )
                }
                Filtered(query, stringResource(R.string.setting_dns_strategy), "dns") {
                    SettingsListRow(
                        title = stringResource(R.string.setting_dns_strategy),
                        options = listOf(
                            "ipv4_only" to stringResource(R.string.dns_strategy_ipv4_only),
                            "ipv6_only" to stringResource(R.string.dns_strategy_ipv6_only),
                            "prefer_ipv4" to stringResource(R.string.dns_strategy_prefer_ipv4),
                            "prefer_ipv6" to stringResource(R.string.dns_strategy_prefer_ipv6),
                        ),
                        selectedValue = s.dnsStrategy,
                        onSelected = { v -> viewModel.updateSettings { it.copy(dnsStrategy = v) } },
                    )
                }
                Filtered(query, "FakeDNS", "fakedns", "dns") {
                    SettingsSwitchRow(
                        title = stringResource(R.string.setting_fake_dns),
                        summary = stringResource(R.string.setting_fake_dns_summary),
                        checked = s.fakeDns,
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(fakeDns = v) } },
                    )
                }
                Filtered(query, stringResource(R.string.setting_real_ping_url), "ping", "delay") {
                    SettingsEditRow(
                        title = stringResource(R.string.setting_real_ping_url),
                        value = s.delayTestUrl,
                        onValueChanged = { v ->
                            viewModel.updateSettings { it.copy(delayTestUrl = v.ifBlank { "https://www.gstatic.com/generate_204" }) }
                        },
                    )
                }
            }

            SettingsSectionCard(
                title = stringResource(R.string.setting_fragment),
                expanded = fragmentExpanded || searching,
                onExpandedChange = { fragmentExpanded = it },
                nested = true,
            ) {
                Filtered(query, stringResource(R.string.setting_fragment), "fragment") {
                    SettingsSwitchRow(
                        title = stringResource(R.string.setting_fragment),
                        summary = if (s.fragmentEnabled) {
                            "${s.fragmentPackets} | len=${s.fragmentLength} | delay=${s.fragmentInterval}ms"
                        } else {
                            stringResource(R.string.setting_fragment_summary)
                        },
                        checked = s.fragmentEnabled,
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(fragmentEnabled = v) } },
                    )
                }
                if (s.fragmentEnabled) {
                    Filtered(query, stringResource(R.string.setting_fragment_packets)) {
                        SettingsListRow(
                            title = stringResource(R.string.setting_fragment_packets),
                            options = listOf(
                                "tlshello" to "tlshello",
                                "1-2" to "1-2",
                                "1-3" to "1-3",
                                "1-5" to "1-5",
                            ),
                            selectedValue = s.fragmentPackets,
                            onSelected = { v -> viewModel.updateSettings { it.copy(fragmentPackets = v) } },
                        )
                    }
                    Filtered(query, stringResource(R.string.setting_fragment_length)) {
                        SettingsEditRow(
                            title = stringResource(R.string.setting_fragment_length),
                            value = s.fragmentLength,
                            onValueChanged = { v -> viewModel.updateSettings { it.copy(fragmentLength = v.ifBlank { "50-100" }) } },
                        )
                    }
                    Filtered(query, stringResource(R.string.setting_fragment_interval)) {
                        SettingsEditRow(
                            title = stringResource(R.string.setting_fragment_interval),
                            value = s.fragmentInterval,
                            onValueChanged = { v -> viewModel.updateSettings { it.copy(fragmentInterval = v.ifBlank { "10-20" }) } },
                        )
                    }
                }
            }

            SettingsSectionCard(
                title = stringResource(R.string.setting_mux),
                expanded = muxExpanded || searching,
                onExpandedChange = { muxExpanded = it },
                nested = true,
            ) {
                Filtered(query, stringResource(R.string.setting_mux), "mux") {
                    SettingsSwitchRow(
                        title = stringResource(R.string.setting_mux),
                        summary = stringResource(R.string.setting_mux_summary),
                        checked = s.tcpMux,
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(tcpMux = v) } },
                    )
                }
                if (s.tcpMux) {
                    Filtered(query, stringResource(R.string.setting_mux_concurrency)) {
                        SettingsEditRow(
                            title = stringResource(R.string.setting_mux_concurrency),
                            value = s.muxConcurrency.toString(),
                            onValueChanged = { text ->
                                text.toIntOrNull()?.let { c ->
                                    if (c in 1..64) viewModel.updateSettings { it.copy(muxConcurrency = c) }
                                }
                            },
                        )
                    }
                    Filtered(query, stringResource(R.string.setting_xudp_concurrency)) {
                        SettingsEditRow(
                            title = stringResource(R.string.setting_xudp_concurrency),
                            value = s.muxXudpConcurrency.toString(),
                            onValueChanged = { text ->
                                text.toIntOrNull()?.let { c ->
                                    if (c in 1..64) viewModel.updateSettings { it.copy(muxXudpConcurrency = c) }
                                }
                            },
                        )
                    }
                }
                Filtered(query, stringResource(R.string.setting_tcp_fast_open), "tfo") {
                    SettingsSwitchRow(
                        title = stringResource(R.string.setting_tcp_fast_open),
                        summary = stringResource(R.string.setting_tcp_fast_open_summary),
                        checked = s.tcpFastOpen,
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(tcpFastOpen = v) } },
                    )
                }
            }

            Filtered(query, stringResource(R.string.setting_log_level), "log") {
                SettingsListRow(
                    title = stringResource(R.string.setting_log_level),
                    options = listOf(
                        "debug" to stringResource(R.string.level_debug),
                        "info" to stringResource(R.string.level_info),
                        "warning" to stringResource(R.string.level_warning),
                        "error" to stringResource(R.string.level_error),
                        "none" to stringResource(R.string.level_none),
                    ),
                    selectedValue = s.logLevel,
                    onSelected = { v -> viewModel.updateSettings { it.copy(logLevel = v) } },
                )
            }
            Filtered(query, stringResource(R.string.auto_ping_servers), "ping", "خودکار") {
                SettingsListRow(
                    title = stringResource(R.string.auto_ping_servers),
                    options = listOf(
                        "0" to stringResource(R.string.level_none),
                        "5" to "5",
                        "15" to "15",
                        "30" to "30",
                    ),
                    selectedValue = s.autoPingMinutes.toString(),
                    onSelected = { v ->
                        v.toIntOrNull()?.let { minutes -> viewModel.updateSettings { it.copy(autoPingMinutes = minutes) } }
                    },
                )
            }
        }

        SettingsSectionCard(
            title = stringResource(R.string.section_about),
            expanded = true,
            onExpandedChange = {},
            collapsible = false,
        ) {
            SettingsMenuRow(
                title = stringResource(R.string.about_version),
                subtitle = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                onClick = {},
            )
            SettingsMenuRow(
                title = stringResource(R.string.setting_about_core),
                subtitle = viewModel.activeEngine?.implementationName ?: "JavidTun Core",
                onClick = {},
            )
            SettingsMenuRow(
                title = stringResource(R.string.setting_open_logs),
                subtitle = stringResource(R.string.setting_open_logs_summary),
                onClick = onOpenLogs,
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Filtered(
    query: String,
    vararg keywords: String,
    content: @Composable () -> Unit,
) {
    if (query.isBlank() || keywords.any { it.contains(query.trim(), ignoreCase = true) }) {
        content()
    }
}
