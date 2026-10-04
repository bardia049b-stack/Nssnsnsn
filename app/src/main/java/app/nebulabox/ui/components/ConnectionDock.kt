package app.nebulabox.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nebulabox.R
import app.nebulabox.engine.TunnelState
import app.nebulabox.engine.TunnelStatus
import app.nebulabox.ui.NebulaViewModel
import app.nebulabox.ui.colorPing
import app.nebulabox.ui.colorPingRed
import app.nebulabox.util.Formatters
import app.nebulabox.util.IpLocationChecker

@Composable
fun ConnectionDock(
    status: TunnelStatus,
    activeProfileName: String?,
    activePingMs: Long?,
    isTestingActive: Boolean,
    testingProgress: Pair<Int, Int>?,
    exitIpInfo: IpLocationChecker.EndpointLocation?,
    health: NebulaViewModel.ConnectionHealth,
    onTestCurrentServer: () -> Unit,
    onToggleService: () -> Unit,
) {
    val running = status.state == TunnelState.STARTED
    val busy = status.state == TunnelState.STARTING || status.state == TunnelState.STOPPING
    val verified = running && health.phase == NebulaViewModel.HealthPhase.ONLINE
    val failed = running && (
        health.phase == NebulaViewModel.HealthPhase.NO_TRAFFIC ||
            health.phase == NebulaViewModel.HealthPhase.EXHAUSTED
        )

    val statusText = when {
        testingProgress != null -> {
            val left = (testingProgress.second - testingProgress.first).coerceAtLeast(0)
            stringResource(R.string.status_testing_servers, left, testingProgress.second)
        }
        isTestingActive -> stringResource(R.string.status_checking)
        !running && busy -> stringResource(R.string.status_connecting)
        !running -> stringResource(R.string.status_disconnected)
        verified -> stringResource(R.string.status_verified)
        failed -> {
            if (health.phase == NebulaViewModel.HealthPhase.EXHAUSTED) {
                stringResource(R.string.status_quota_done)
            } else {
                stringResource(R.string.status_no_traffic)
            }
        }
        health.phase == NebulaViewModel.HealthPhase.CHECKING -> stringResource(R.string.status_verifying)
        else -> stringResource(R.string.status_checking)
    }

    val statusColor = when {
        verified -> colorPing
        failed -> colorPingRed
        busy || (running && health.phase == NebulaViewModel.HealthPhase.CHECKING) -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    val containerColor = when {
        verified -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        failed -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 15.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(statusColor),
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (verified || failed) statusColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(Modifier.height(5.dp))

                    FlagText(
                        text = activeProfileName?.ifBlank { stringResource(R.string.no_server_selected) }
                            ?: stringResource(R.string.no_server_selected),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.width(12.dp))

                FilledIconButton(
                    onClick = {
                        if (!busy) {
                            onToggleService()
                        }
                    },
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (running) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        contentColor = if (running) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PowerSettingsNew,
                        contentDescription = if (running) {
                            stringResource(R.string.disconnect)
                        } else {
                            stringResource(R.string.connect)
                        },
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            AnimatedVisibility(visible = failed) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = health.detail.ifBlank {
                            if (health.phase == NebulaViewModel.HealthPhase.EXHAUSTED) {
                                stringResource(R.string.status_quota_done)
                            } else {
                                stringResource(R.string.status_no_traffic_hint)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onTestCurrentServer)
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(17.dp),
                        )
                        Text(
                            text = stringResource(R.string.dock_tap_to_check),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            AnimatedVisibility(visible = running && !failed) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onTestCurrentServer),
                ) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    )
                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            if (exitIpInfo != null && exitIpInfo.ip.isNotBlank()) {
                                CountryFlagIcon(
                                    countryCode = exitIpInfo.countryCode,
                                    flagEmoji = exitIpInfo.flagEmoji,
                                    size = 18.dp,
                                )
                                Text(
                                    text = "${exitIpInfo.countryName.ifBlank { exitIpInfo.countryCode }} · ${exitIpInfo.ip}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.dock_tap_to_check),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        val ping = activePingMs?.takeIf { it > 0L } ?: health.delayMs.takeIf { it > 0L }
                        if (ping != null) {
                            Text(
                                text = "${ping} ms",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                color = when {
                                    ping < 250 -> colorPing
                                    ping < 600 -> Color(0xFFE0A030)
                                    else -> colorPingRed
                                },
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = "↑ ${Formatters.speed(status.uplink)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "↓ ${Formatters.speed(status.downlink)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = stringResource(R.string.dock_tap_to_check),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}
