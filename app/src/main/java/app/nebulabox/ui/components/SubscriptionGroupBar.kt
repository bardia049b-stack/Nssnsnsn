package app.nebulabox.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nebulabox.R
import app.nebulabox.data.Profile
import app.nebulabox.data.SubscriptionItem

@Composable
fun SubscriptionGroupBar(
    subscriptions: List<SubscriptionItem>,
    profiles: List<Profile>,
    selectedSubId: String,
    currentListCount: Int,
    onSelectGroup: (String) -> Unit,
) {
    val allLabel = stringResource(R.string.group_all)
    val tabs = remember(subscriptions, profiles, allLabel) {
        buildList {
            val manualCount = profiles.count { it.subscriptionId.isBlank() }
            add(Triple("", allLabel, manualCount))
            subscriptions.forEach { sub ->
                val count = profiles.count { it.subscriptionId == sub.id }
                add(Triple(sub.id, sub.remarks, count))
            }
        }
    }

    val activeGroupTitle = remember(tabs, selectedSubId) {
        tabs.firstOrNull { it.first == selectedSubId }?.second ?: allLabel
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (subscriptions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
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
            }
        }

        if (currentListCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "$activeGroupTitle ($currentListCount)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
