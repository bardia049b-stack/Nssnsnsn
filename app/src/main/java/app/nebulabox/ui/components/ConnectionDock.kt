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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nebulabox.engine.TunnelState
import app.nebulabox.engine.TunnelStatus
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
    onTestCurrentServer: () -> Unit,
    onToggleService: () -> Unit,
) {
    val isRunning = status.state == TunnelState.STARTED
    val isBusy = status.state == TunnelState.STARTING || status.state == TunnelState.STOPPING

    val statusText = when {
        testingProgress != null -> {
            val left = (testingProgress.second - testingProgress.first).coerceAtLeast(0)
            "Testing servers ($left / ${testingProgress.second})"
        }
        isTestingActive -> "Checking connection..."
        status.state == TunnelState.STARTED -> "Connected"
        status.state == TunnelState.STARTING -> "Connecting..."
        status.state == TunnelState.STOPPING -> "Disconnecting..."
        else -> "Disconnected"
    }

    val containerColor = if (isRunning) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
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
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = isRunning, onClick = onTestCurrentServer),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when (status.state) {
                                        TunnelState.STARTED -> colorPing
                                        TunnelState.STARTING, TunnelState.STOPPING -> MaterialTheme.colorScheme.primary
                                        TunnelState.STOPPED -> MaterialTheme.colorScheme.outline
                                    },
                                ),
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isRunning) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    FlagText(
                        text = activeProfileName?.ifBlank { "No server selected" } ?: "No server selected",
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
                        if (!isBusy) {
                            onToggleService()
                        }
                    },
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isRunning) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        contentColor = if (isRunning) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PowerSettingsNew,
                        contentDescription = if (isRunning) "Disconnect" else "Connect",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            AnimatedVisibility(visible = isRunning) {
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
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            } else {
                                Text(
                                    text = if (isTestingActive) "Checking exit IP..." else "Tap to check exit IP & delay",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        if (activePingMs != null && activePingMs != 0L) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (activePingMs > 0L) "$activePingMs ms" else "Timeout",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (activePingMs > 0L) colorPing else colorPingRed,
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = "↑ ${Formatters.speed(status.uplink)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "↓ ${Formatters.speed(status.downlink)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
