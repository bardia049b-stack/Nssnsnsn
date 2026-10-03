package app.nebulabox.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nebulabox.R
import app.nebulabox.data.AppSettings
import app.nebulabox.data.Profile
import app.nebulabox.engine.Engines
import app.nebulabox.engine.TunnelState
import app.nebulabox.ui.NebulaViewModel
import app.nebulabox.util.Formatters
import app.nebulabox.util.IpLocationChecker

@Composable
fun HomeScreen(
    viewModel: NebulaViewModel,
    onOpenProfiles: () -> Unit,
    onOpenGroups: () -> Unit,
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val location by viewModel.endpointLocation.collectAsStateWithLifecycle()
    val checkingLocation by viewModel.checkingLocation.collectAsStateWithLifecycle()
    val activeDelayMs by viewModel.activeDelayMs.collectAsStateWithLifecycle()
    val testingIds by viewModel.testingProfileIds.collectAsStateWithLifecycle()

    val selected = profiles.firstOrNull { it.id == settings.selectedProfileId }
        ?: profiles.firstOrNull()
    val isTestingSelected = checkingLocation || (selected != null && selected.id in testingIds)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            EngineBadge(useHevTun = settings.useHevTun)
        }

        EngineBanner()

        ConnectCard(
            profile = selected,
            state = status.state,
            message = status.message,
            location = location,
            activeDelayMs = activeDelayMs ?: selected?.lastDelayMs?.takeIf { it > 0 }?.toLong(),
            checkingLocation = isTestingSelected,
            onRefreshLocation = { viewModel.testActiveConnectionDelay() },
            onToggle = {
                if (selected != null) viewModel.toggle(selected)
            },
            onRestart = { viewModel.restartTunnel() },
            onPickProfile = onOpenProfiles,
        )

        if (status.state == TunnelState.STARTED) {
            TrafficCard(
                uplink = status.uplink,
                downlink = status.downlink,
                uplinkTotal = status.uplinkTotal,
                downlinkTotal = status.downlinkTotal,
                startedAt = status.startedAt,
                memory = status.memory,
            )
        }

        QuickXrayControlsCard(
            settings = settings,
            connected = status.state == TunnelState.STARTED,
            onUpdateSettings = { transform ->
                viewModel.updateSettings(transform)
                if (status.state == TunnelState.STARTED) {
                    viewModel.restartTunnel()
                }
            },
        )

        ProfilesSummary(
            total = profiles.size,
            selected = selected,
            onOpen = onOpenProfiles,
        )

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun EngineBadge(useHevTun: Boolean) {
    val engine = Engines.active.value ?: Engines.obtain()
    val modeLabel = if (useHevTun) "hev-tun" else "Xray TUN"
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = "${engine.implementationName} · $modeLabel",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun EngineBanner() {
    val engine = Engines.active.value ?: Engines.obtain()
    if (engine.functional) return
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.engine_missing_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = stringResource(R.string.engine_missing_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun ConnectCard(
    profile: Profile?,
    state: TunnelState,
    message: String,
    location: IpLocationChecker.EndpointLocation?,
    activeDelayMs: Long?,
    checkingLocation: Boolean,
    onRefreshLocation: () -> Unit,
    onToggle: () -> Unit,
    onRestart: () -> Unit,
    onPickProfile: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val running = state == TunnelState.STARTED
            val pending = state == TunnelState.STARTING || state == TunnelState.STOPPING

            Surface(
                shape = CircleShape,
                color = when {
                    running -> MaterialTheme.colorScheme.primaryContainer
                    pending -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier
                    .size(84.dp)
                    .clickable(enabled = profile != null && !pending) { onToggle() },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.PowerSettingsNew,
                        contentDescription = null,
                        tint = if (running) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(36.dp),
                    )
                }
            }

            Text(
                text = stringResource(
                    when (state) {
                        TunnelState.STARTED -> R.string.status_started
                        TunnelState.STARTING -> R.string.status_starting
                        TunnelState.STOPPING -> R.string.status_stopping
                        TunnelState.STOPPED -> R.string.status_stopped
                    },
                ),
                style = MaterialTheme.typography.titleLarge,
            )

            Text(
                text = profile?.displayName ?: stringResource(R.string.no_profile_selected),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (profile != null) {
                val protoInfo = buildString {
                    append(profile.protocol.wire.uppercase())
                    if (profile.transport.type.isNotBlank()) {
                        append(" · ${profile.transport.type.uppercase()}")
                    }
                    if (profile.tls.reality) {
                        append(" · REALITY")
                    } else if (profile.tls.enabled) {
                        append(" · TLS")
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = protoInfo,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            // Connected Exit IP & Country OR Real Ping Test Bar (matching v2rayNG bottom bar)
            ConnectedLocationOrPingBadge(
                running = running,
                location = location,
                activeDelayMs = activeDelayMs,
                checking = checkingLocation,
                onTest = onRefreshLocation,
            )

            if (message.isNotBlank() && state == TunnelState.STOPPED) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onToggle,
                    enabled = profile != null && !pending && Engines.active.value?.functional != false,
                    modifier = Modifier.weight(1f),
                    colors = if (running) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                ) {
                    Text(
                        stringResource(
                            if (running) R.string.action_disconnect else R.string.action_connect,
                        ),
                    )
                }

                if (running) {
                    OutlinedButton(onClick = onRestart) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = "Restart", modifier = Modifier.size(18.dp))
                    }
                }
            }

            TextButton(onClick = onPickProfile) {
                Text(stringResource(R.string.action_change_profile))
            }
        }
    }
}

@Composable
private fun ConnectedLocationOrPingBadge(
    running: Boolean,
    location: IpLocationChecker.EndpointLocation?,
    activeDelayMs: Long?,
    checking: Boolean,
    onTest: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !checking) { onTest() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (running && location != null) {
                    Text(
                        text = location.flagEmoji,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = location.countryName.ifBlank { location.countryCode },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    text = "${location.delayMs} ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Text(
                            text = buildString {
                                append("IP: ${location.ip}")
                                if (location.city.isNotBlank() &&
                                    !location.city.equals(location.countryName, ignoreCase = true)
                                ) {
                                    append(" · ${location.city}")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Icon(
                        imageVector = if (running) Icons.Filled.Public else Icons.Filled.NetworkCheck,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Column {
                        Text(
                            text = when {
                                checking -> "Testing real connection delay (HTTP 204)…"
                                activeDelayMs != null && activeDelayMs > 0 -> "Real Delay: $activeDelayMs ms (Tap to re-test)"
                                running -> "Tap to check exit IP, country & real delay"
                                else -> "Tap to test selected server real delay (Xray)"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            if (checking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                IconButton(
                    onClick = onTest,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Test Delay",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickXrayControlsCard(
    settings: AppSettings,
    connected: Boolean,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Xray Quick Controls",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            // 1. TLS Fragment toggle (essential for Iranian ISPs)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "TLS Fragment & Noise (Anti-DPI)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "Splits TLS ClientHello (${settings.fragmentPackets}, ${settings.fragmentLength}B)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.fragmentEnabled,
                    onCheckedChange = { on ->
                        onUpdateSettings { it.copy(fragmentEnabled = on) }
                    },
                )
            }

            // 2. Routing Mode Quick Chips (Global / Bypass Iran / Rule)
            Text(
                text = "Routing Preset",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val modes = listOf(
                    "global" to "Global (All Proxy)",
                    "white_iran" to "Bypass Iran (.ir Direct)",
                    "rule" to "Bypass LAN/CN",
                )
                modes.forEach { (key, label) ->
                    FilterChip(
                        selected = settings.routeMode == key,
                        onClick = { onUpdateSettings { it.copy(routeMode = key) } },
                        label = { Text(label) },
                    )
                }
            }

            // 3. TUN Engine Mode Quick Chips (hev-socks5-tunnel vs Xray Native TUN)
            Text(
                text = "TUN Engine",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = settings.useHevTun,
                    onClick = { onUpdateSettings { it.copy(useHevTun = true) } },
                    label = { Text("hev-socks5-tunnel (v2rayNG)") },
                )
                FilterChip(
                    selected = !settings.useHevTun,
                    onClick = { onUpdateSettings { it.copy(useHevTun = false) } },
                    label = { Text("Xray Native TUN") },
                )
            }
        }
    }
}

@Composable
private fun TrafficCard(
    uplink: Long,
    downlink: Long,
    uplinkTotal: Long,
    downlinkTotal: Long,
    startedAt: Long,
    memory: Long,
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.title_traffic),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SpeedRow(
                    label = stringResource(R.string.label_uplink),
                    rate = uplink,
                    total = uplinkTotal,
                )
                SpeedRow(
                    label = stringResource(R.string.label_downlink),
                    rate = downlink,
                    total = downlinkTotal,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Stat(stringResource(R.string.label_uptime), Formatters.duration(startedAt))
                Stat(stringResource(R.string.label_memory), Formatters.size(memory))
            }
        }
    }
}

@Composable
private fun SpeedRow(label: String, rate: Long, total: Long) {
    Column(horizontalAlignment = Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Filled.CompareArrows,
                null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(Formatters.speed(rate), style = MaterialTheme.typography.titleMedium)
        Text(
            text = Formatters.size(total),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ProfilesSummary(total: Int, selected: Profile?, onOpen: () -> Unit) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.label_profile_count, total),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = selected?.displayName ?: stringResource(R.string.no_profile_selected),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onOpen) {
                Text(stringResource(R.string.action_manage))
            }
        }
    }
}
