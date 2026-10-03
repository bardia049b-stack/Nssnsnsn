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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nebulabox.data.Profile
import app.nebulabox.data.SubscriptionItem

@Composable
fun SubscriptionGroupBar(
    subscriptions: List<SubscriptionItem>,
    profiles: List<Profile>,
    selectedSubId: String,
    onSelectGroup: (String) -> Unit,
    onPingAll: () -> Unit = {},
    onSortByPing: () -> Unit = {},
) {
    val tabs = remember(subscriptions, profiles) {
        buildList {
            add(Triple("", "All", profiles.size))
            subscriptions.forEach { sub ->
                val count = profiles.count { it.subscriptionId == sub.id }
                add(Triple(sub.id, sub.remarks, count))
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { (subId, title, count) ->
            val selected = subId == selectedSubId
            FilterChip(
                selected = selected,
                onClick = { onSelectGroup(subId) },
                label = {
                    Text(
                        text = "$title ($count)",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
