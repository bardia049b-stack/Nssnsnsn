package app.nebulabox.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.nebulabox.ui.NebulaViewModel
import java.util.Locale

@Composable
fun SpeedTestSheet(
    state: NebulaViewModel.SpeedTestState?,
    onRunAgain: () -> Unit,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (state == null) return

    val location = state.location
    val downloadLine = state.downloadMbps?.let {
        String.format(Locale.US, "%.1f Mbps", it)
    }

    val summary = buildString {
        location?.let {
            append(it.displaySummary)
            if (it.isp.isNotBlank()) append(" · ${it.isp}")
            append("\n${it.ip} · ${it.delayMs} ms")
        }
        if (downloadLine != null) append("\nDownload: $downloadLine")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Speed test & exit IP") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.running) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(12.dp))
                        Text("Measuring through the active tunnel…")
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = downloadLine ?: "—",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Text(
                                text = if (state.transferred.isBlank()) "Download throughput" else "Download throughput · ${state.transferred} transferred",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }

                    if (location != null) {
                        InfoRow("Exit IP", location.ip)
                        InfoRow("Location", location.displaySummary)
                        if (location.isp.isNotBlank()) InfoRow("ISP", location.isp)
                        InfoRow("Latency", "${location.delayMs} ms")
                    } else {
                        Text(
                            text = "Exit IP lookup did not return a result.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    state.error?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRunAgain, enabled = !state.running) {
                Text("Run again")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = { onCopy(summary) },
                    enabled = !state.running && summary.isNotBlank(),
                ) {
                    Text("Copy")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        },
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
