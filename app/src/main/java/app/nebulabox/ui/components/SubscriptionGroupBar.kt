package app.nebulabox.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.nebulabox.data.Profile
import app.nebulabox.data.Subscription

@Composable
fun SubscriptionGroupBar(
    subscriptions: List<Subscription>,
    profiles: List<Profile>,
    selectedSubId: String,
    onSelectSubId: (String) -> Unit,
    onPingAll: () -> Unit = {},
    onSortByPing: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = selectedSubId.isEmpty(),
            onClick = { onSelectSubId("") },
            label = {
                Text(
                    text = "All (${profiles.size})",
                    style = MaterialTheme.typography.labelMedium,
                )
            },
        )

        subscriptions.forEach { sub ->
            val count = profiles.count { it.subscriptionId == sub.id }
            val selected = selectedSubId == sub.id
            FilterChip(
                selected = selected,
                onClick = { onSelectSubId(sub.id) },
                label = {
                    Text(
                        text = "${sub.name.ifBlank { "Group" }} ($count)",
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
            )
        }

        if (profiles.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            AssistChip(
                onClick = onPingAll,
                label = { Text("Ping", style = MaterialTheme.typography.labelMedium) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
            AssistChip(
                onClick = onSortByPing,
                label = { Text("Sort", style = MaterialTheme.typography.labelMedium) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Sort,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }
    }
}
