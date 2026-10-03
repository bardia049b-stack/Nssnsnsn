package app.nebulabox.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.nebulabox.ui.NebulaViewModel

private data class RouteModeOption(
    val key: String,
    val title: String,
    val subtitle: String,
)

private val ROUTE_MODES = listOf(
    RouteModeOption(
        key = "global",
        title = "Proxy All Traffic",
        subtitle = "Route all traffic through the active proxy server",
    ),
    RouteModeOption(
        key = "bypass_lan",
        title = "Bypass Local Network (LAN)",
        subtitle = "Connect private local IPs directly and proxy everything else",
    ),
    RouteModeOption(
        key = "bypass_iran",
        title = "Bypass Iran (.ir & domestic IPs)",
        subtitle = "Connect Iranian domains and domestic IPs directly without VPN",
    ),
    RouteModeOption(
        key = "bypass_china",
        title = "Bypass Mainland & LAN",
        subtitle = "Bypass private and domestic mainland ranges",
    ),
    RouteModeOption(
        key = "direct",
        title = "Direct Only",
        subtitle = "Send all traffic directly without proxy",
    ),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoutingFragmentScreen(viewModel: NebulaViewModel) {
    val s by viewModel.settings.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "TLS Fragment",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        )

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.updateSettings { it.copy(fragmentEnabled = !s.fragmentEnabled) }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable TLS Fragment",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Splits TLS ClientHello packets to bypass DPI inspection",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = s.fragmentEnabled,
                        onCheckedChange = { v ->
                            viewModel.updateSettings { it.copy(fragmentEnabled = v) }
                        },
                    )
                }

                if (s.fragmentEnabled) {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "Packets Mode",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf("tlshello", "1-1", "1-2", "1-3", "1-5").forEach { pkt ->
                            FilterChip(
                                selected = s.fragmentPackets == pkt,
                                onClick = {
                                    viewModel.updateSettings { it.copy(fragmentPackets = pkt) }
                                },
                                label = { Text(pkt) },
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = s.fragmentLength,
                            onValueChange = { v ->
                                viewModel.updateSettings { it.copy(fragmentLength = v) }
                            },
                            label = { Text("Length (bytes)") },
                            placeholder = { Text("50-100") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = s.fragmentInterval,
                            onValueChange = { v ->
                                viewModel.updateSettings { it.copy(fragmentInterval = v) }
                            },
                            label = { Text("Delay (ms)") },
                            placeholder = { Text("10-20") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = s.fragmentLength == "10-20" && s.fragmentInterval == "10-20",
                            onClick = {
                                viewModel.updateSettings {
                                    it.copy(
                                        fragmentPackets = "tlshello",
                                        fragmentLength = "10-20",
                                        fragmentInterval = "10-20",
                                    )
                                }
                            },
                            label = { Text("Preset: Fast (10-20)") },
                        )
                        FilterChip(
                            selected = s.fragmentLength == "50-100" && s.fragmentInterval == "10-20",
                            onClick = {
                                viewModel.updateSettings {
                                    it.copy(
                                        fragmentPackets = "tlshello",
                                        fragmentLength = "50-100",
                                        fragmentInterval = "10-20",
                                    )
                                }
                            },
                            label = { Text("Preset: Balanced (50-100)") },
                        )
                        FilterChip(
                            selected = s.fragmentLength == "100-200" && s.fragmentInterval == "10-20",
                            onClick = {
                                viewModel.updateSettings {
                                    it.copy(
                                        fragmentPackets = "tlshello",
                                        fragmentLength = "100-200",
                                        fragmentInterval = "10-20",
                                    )
                                }
                            },
                            label = { Text("Preset: Standard (100-200)") },
                        )
                    }
                }
            }
        }

        Text(
            text = "Routing Mode",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp),
        )

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                ROUTE_MODES.forEachIndexed { index, option ->
                    val selected = s.routeMode == option.key
                    ListItem(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.updateSettings { it.copy(routeMode = option.key) }
                            },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    viewModel.updateSettings { it.copy(routeMode = option.key) }
                                },
                            )
                        },
                        headlineContent = {
                            Text(
                                text = option.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = option.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                    if (index < ROUTE_MODES.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        )
                    }
                }
            }
        }

        Text(
            text = "Domain Strategy & Sniffing",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp),
        )

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = "Domain Resolution Strategy",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("AsIs", "IPIfNonMatch", "IPOnDemand").forEach { strategy ->
                            FilterChip(
                                selected = s.domainStrategy == strategy,
                                onClick = {
                                    viewModel.updateSettings { it.copy(domainStrategy = strategy) }
                                },
                                label = { Text(strategy) },
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )

                ListItem(
                    modifier = Modifier.clickable {
                        viewModel.updateSettings { it.copy(sniffing = !s.sniffing) }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("Traffic Sniffing") },
                    supportingContent = { Text("Detect HTTP and TLS domain names from packets") },
                    trailingContent = {
                        Switch(
                            checked = s.sniffing,
                            onCheckedChange = { v ->
                                viewModel.updateSettings { it.copy(sniffing = v) }
                            },
                        )
                    },
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )

                ListItem(
                    modifier = Modifier.clickable {
                        viewModel.updateSettings { it.copy(blockAds = !s.blockAds) }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("Block Ads & Trackers") },
                    supportingContent = { Text("Reject known ad-serving domains in routing") },
                    trailingContent = {
                        Switch(
                            checked = s.blockAds,
                            onCheckedChange = { v ->
                                viewModel.updateSettings { it.copy(blockAds = v) }
                            },
                        )
                    },
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )

                ListItem(
                    modifier = Modifier.clickable {
                        viewModel.updateSettings { it.copy(tcpMux = !s.tcpMux) }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("TCP Multiplexing (Mux)") },
                    supportingContent = { Text("Multiplex multiple TCP streams over one connection") },
                    trailingContent = {
                        Switch(
                            checked = s.tcpMux,
                            onCheckedChange = { v ->
                                viewModel.updateSettings { it.copy(tcpMux = v) }
                            },
                        )
                    },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
