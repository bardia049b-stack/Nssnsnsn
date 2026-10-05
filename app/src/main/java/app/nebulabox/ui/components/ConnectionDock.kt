package app.nebulabox.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import app.nebulabox.ui.colorWarning
import app.nebulabox.ui.colorPingRed
import app.nebulabox.util.Formatters
import app.nebulabox.util.IpLocationChecker

private val BUTTON_SIZE = 112.dp

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
    onOpenServerList: () -> Unit = {},
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

    val buttonColor by animateColorAsState(
        targetValue = when {
            failed -> MaterialTheme.colorScheme.error
            running -> colorPing
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(durationMillis = 300),
        label = "power",
    )

    val haptics = LocalHapticFeedback.current

    val ringColor = MaterialTheme.colorScheme.primary
    val onlineColor = colorPing
    val ringAngle by rememberInfiniteTransition(label = "connect").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring",
    )

    val pulse = remember { Animatable(0f) }
    LaunchedEffect(verified) {
        if (!verified) return@LaunchedEffect
        pulse.snapTo(0f)
        pulse.animateTo(1f, tween(durationMillis = 650, easing = LinearEasing))
    }

    var firstFrame by remember { mutableStateOf(true) }
    LaunchedEffect(running, busy) {
        if (firstFrame) {
            firstFrame = false
            return@LaunchedEffect
        }
        if (busy) return@LaunchedEffect
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    val ping = activePingMs?.takeIf { it > 0L } ?: health.delayMs.takeIf { it > 0L }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(BUTTON_SIZE + 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (busy) {
                        val stroke = 3.dp.toPx()
                        drawArc(
                            color = ringColor,
                            startAngle = ringAngle,
                            sweepAngle = 100f,
                            useCenter = false,
                            topLeft = Offset(stroke / 2, stroke / 2),
                            size = Size(size.width - stroke, size.height - stroke),
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                    }
                    val progress = pulse.value
                    if (progress > 0f) {
                        drawCircle(
                            color = onlineColor.copy(alpha = 0.30f * (1f - progress)),
                            radius = size.minDimension / 2 - 3.dp.toPx() + 22.dp.toPx() * progress,
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .size(BUTTON_SIZE)
                        .clip(CircleShape)
                        .clickable(enabled = !busy, onClick = onToggleService),
                    shape = CircleShape,
                    color = buttonColor,
                    contentColor = Color.White,
                    shadowElevation = 4.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.PowerSettingsNew,
                            contentDescription = if (running) {
                                stringResource(R.string.disconnect)
                            } else {
                                stringResource(R.string.connect)
                            },
                            modifier = Modifier.size(50.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

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
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = activeProfileName?.ifBlank { stringResource(R.string.no_server_selected) }
                    ?: stringResource(R.string.no_server_selected),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (running) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onTestCurrentServer),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (exitIpInfo != null && exitIpInfo.ip.isNotBlank()) {
                            CountryFlagIcon(
                                countryCode = exitIpInfo.countryCode,
                                flagEmoji = exitIpInfo.flagEmoji,
                                size = 20.dp,
                            )
                            Text(
                                text = "${exitIpInfo.countryName.ifBlank { exitIpInfo.countryCode }} · ${exitIpInfo.ip}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.dock_tap_to_check),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        if (ping != null) {
                            Text(
                                text = "$ping ms",
                                style = MaterialTheme.typography.labelLarge,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = when {
                                    ping < 250 -> colorPing
                                    ping < 600 -> colorWarning
                                    else -> colorPingRed
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Text(
                        text = "↑ ${Formatters.speed(status.uplink)}",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "↓ ${Formatters.speed(status.downlink)}",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AnimatedVisibility(visible = failed) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DockAction(
                            label = stringResource(R.string.try_again),
                            icon = Icons.Outlined.Refresh,
                            onClick = onTestCurrentServer,
                        )
                        DockAction(
                            label = stringResource(R.string.change_server),
                            icon = Icons.Outlined.KeyboardArrowDown,
                            onClick = onOpenServerList,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DockAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
