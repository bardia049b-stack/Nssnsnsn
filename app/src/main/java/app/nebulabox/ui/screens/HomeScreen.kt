package app.nebulabox.ui.screens

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
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import app.nebulabox.BuildConfig
import app.nebulabox.R
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

    val selected = profiles.firstOrNull { it.id == settings.selectedProfileId }
        ?: profiles.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        EngineBanner()

        ConnectCard(
            profile = selected,
            state = status.state,
            message = status.message,
            location = location,
            checkingLocation = checkingLocation,
            onRefreshLocation = { viewModel.refreshLocation() },
            onToggle = {
                if (selected != null) viewModel.toggle(selected)
            },
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
                connectionsIn = status.connectionsIn,
                connectionsOut = status.connectionsOut,
            )
        }

        ProfilesSummary(
            total = profiles.size,
            selected = selected,
            onOpen = onOpenProfiles,
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onOpenGroups,
                modifier = Modifier.weight(1f),
                enabled = status.state == TunnelState.STARTED,
            ) {
                Icon(Icons.Filled.Groups, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_groups))
            }
        }

        Spacer(Modifier.height(24.dp))
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
            Text(
                text = stringResource(R.string.engine_missing_hint),
                style = MaterialTheme.typography.labelSmall,
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
    checkingLocation: Boolean,
    onRefreshLocation: () -> Unit,
    onToggle: () -> Unit,
    onPickProfile: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
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
                modifier = Modifier.size(88.dp),
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

            if (running) {
                ConnectedLocationBadge(
                    location = location,
                    checking = checkingLocation,
                    onRefresh = onRefreshLocation,
                )
            }

            if (message.isNotBlank() && state == TunnelState.STOPPED) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }

            Button(
                onClick = onToggle,
                enabled = profile != null && !pending && Engines.active.value?.functional != false,
                modifier = Modifier.fillMaxWidth(),
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

            TextButton(onClick = onPickProfile) {
                Text(stringResource(R.string.action_change_profile))
            }
        }
    }
}

@Composable
private fun ConnectedLocationBadge(
    location: IpLocationChecker.EndpointLocation?,
    checking: Boolean,
    onRefresh: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        modifier = Modifier.fillMaxWidth(),
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
                if (location != null) {
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
                        imageVector = Icons.Filled.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Column {
                        Text(
                            text = if (checking) "Detecting exit IP & country…" else "Tap refresh to check exit IP & location",
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
                    onClick = onRefresh,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh IP Location",
                        modifier = Modifier.size(18.dp),
                    )
                }
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
    connectionsIn: Int,
    connectionsOut: Int,
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
                Stat(
                    stringResource(R.string.label_connections),
                    "$connectionsIn / $connectionsOut",
                )
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

/** Kept so a release build without the core still reports its own version. */
@Composable
private fun VersionLabel() {
    Text(
        text = "v${BuildConfig.VERSION_NAME}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
