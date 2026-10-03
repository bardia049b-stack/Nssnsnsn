package app.nebulabox.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nebulabox.R
import app.nebulabox.engine.TunnelState
import app.nebulabox.engine.TunnelStatus
import app.nebulabox.ui.colorFabActive
import app.nebulabox.ui.colorPing
import app.nebulabox.ui.colorPingRed
import app.nebulabox.util.Formatters
import app.nebulabox.util.IpLocationChecker
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    val rotationAnim = remember { Animatable(0f) }

    LaunchedEffect(isRunning) {
        if (!isRunning) {
            rotationAnim.snapTo(0f)
        }
    }

    val titleText = when {
        testingProgress != null -> {
            val left = (testingProgress.second - testingProgress.first).coerceAtLeast(0)
            "Testing servers… ($left / ${testingProgress.second})"
        }
        isTestingActive -> "Testing connection…"
        status.state == TunnelState.STARTED -> activeProfileName?.ifBlank { "Connected" } ?: "Connected"
        status.state == TunnelState.STARTING -> "Connecting…"
        status.state == TunnelState.STOPPING -> "Disconnecting…"
        else -> status.message.ifBlank { activeProfileName?.ifBlank { "Ready to connect" } ?: "Not connected" }
    }

    val subtitleText = when {
        status.state == TunnelState.STARTED -> {
            val delayPart = when {
                activePingMs != null && activePingMs > 0L -> "${activePingMs} ms"
                activePingMs != null && activePingMs < 0L -> "Timeout"
                else -> "Tap to test delay"
            }
            val ipPart = if (exitIpInfo != null && exitIpInfo.ip.isNotBlank()) {
                " • ${exitIpInfo.flagEmoji} ${exitIpInfo.countryName} (${exitIpInfo.ip})"
            } else {
                ""
            }
            delayPart + ipPart
        }
        else -> "Tap button to start tunnel"
    }

    val speedText = if (isRunning) {
        "↑ ${Formatters.speed(status.uplink)}   ↓ ${Formatters.speed(status.downlink)}"
    } else {
        null
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .clickable(enabled = isRunning, onClick = onTestCurrentServer),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 6.dp,
            border = BorderStroke(
                width = 1.dp,
                color = if (isRunning) {
                    colorPing.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                },
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
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
                            text = titleText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(Modifier.height(3.dp))

                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            isRunning && activePingMs != null && activePingMs < 0L -> colorPingRed
                            isRunning && activePingMs != null && activePingMs > 0L -> colorPing
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (!speedText.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = speedText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                FloatingActionButton(
                    onClick = {
                        if (!isBusy) {
                            if (!isRunning) {
                                scope.launch {
                                    rotationAnim.animateTo(
                                        targetValue = 360f,
                                        animationSpec = tween(durationMillis = 900),
                                    )
                                }
                            }
                            onToggleService()
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = if (isRunning) {
                        colorFabActive
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    contentColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 2.dp,
                        pressedElevation = 4.dp,
                    ),
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (isRunning) R.drawable.ic_stop_24dp else R.drawable.ic_play_24dp,
                        ),
                        contentDescription = if (isRunning) "Stop" else "Start",
                        tint = Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer { rotationZ = rotationAnim.value },
                    )
                }
            }
        }
    }
}
