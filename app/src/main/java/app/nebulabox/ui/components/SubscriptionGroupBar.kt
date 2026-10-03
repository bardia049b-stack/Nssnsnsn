package app.nebulabox.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
) {
    if (subscriptions.isEmpty()) return

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
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                shape = RoundedCornerShape(10.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}
