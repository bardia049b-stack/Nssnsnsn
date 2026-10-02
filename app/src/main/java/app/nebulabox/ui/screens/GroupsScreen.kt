package app.nebulabox.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nebulabox.R
import app.nebulabox.engine.OutboundGroup
import app.nebulabox.ui.NebulaViewModel
import app.nebulabox.util.Formatters

/** Selector / URL-test groups reported by the tunnel while it is running. */
@Composable
fun GroupsScreen(viewModel: NebulaViewModel) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()

    if (groups.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.empty_groups),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(groups, key = { it.tag }) { group ->
            GroupCard(
                group = group,
                onSelect = { viewModel.selectOutbound(group.tag, it) },
                onTest = { viewModel.urlTest(group.tag) },
            )
        }
    }
}

@Composable
private fun GroupCard(
    group: OutboundGroup,
    onSelect: (String) -> Unit,
    onTest: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(group.tag, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = group.type,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onTest, enabled = group.selectable) {
                    Icon(Icons.Filled.Bolt, stringResource(R.string.action_url_test))
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                androidx.compose.foundation.layout.FlowRowCompat(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    group.items.forEach { item ->
                        AssistChip(
                            onClick = { onSelect(item.tag) },
                            enabled = group.selectable,
                            label = {
                                Text(
                                    if (item.delayMs >= 0) {
                                        "${item.tag}  ${Formatters.delay(item.delayMs)}"
                                    } else {
                                        item.tag
                                    },
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (item.tag == group.selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}
