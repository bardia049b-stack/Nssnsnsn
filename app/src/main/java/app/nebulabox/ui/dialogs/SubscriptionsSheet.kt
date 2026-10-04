package app.nebulabox.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.nebulabox.R
import app.nebulabox.data.SubscriptionItem
import app.nebulabox.ui.AppDivider

@Composable
fun SubscriptionsSheet(
    subscriptions: List<SubscriptionItem>,
    isUpdating: Boolean,
    onDismiss: () -> Unit,
    onSaveSubscription: (id: String?, remarks: String, url: String) -> Unit,
    onUpdateAll: () -> Unit,
    onDeleteSubscription: (id: String, deleteProfiles: Boolean) -> Unit,
) {
    var remarks by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<SubscriptionItem?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.subscription_groups))
                if (subscriptions.isNotEmpty()) {
                    IconButton(onClick = onUpdateAll, enabled = !isUpdating) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.update_all_subscriptions))
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (subscriptions.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(subscriptions, key = { it.id }) { subscription ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(subscription.remarks, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        subscription.url,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        editingId = subscription.id
                                        remarks = subscription.remarks
                                        url = subscription.url
                                    },
                                ) {
                                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.edit_subscription))
                                }
                                IconButton(onClick = { pendingDelete = subscription }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_delete_24dp),
                                        contentDescription = stringResource(R.string.delete_subscription),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            AppDivider()
                        }
                    }
                }

                Text(
                    text = if (editingId == null) "Add Subscription" else "Edit Subscription",
                    style = MaterialTheme.typography.labelLarge,
                )
                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text(stringResource(R.string.group_name)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Subscription URL (https://…)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val name = remarks.trim().ifBlank {
                        runCatching { java.net.URL(url.trim()).host }.getOrDefault("Subscription")
                    }
                    onSaveSubscription(editingId, name, url.trim())
                    remarks = ""
                    url = ""
                    editingId = null
                    onDismiss()
                },
                enabled = url.isNotBlank() && !isUpdating,
            ) {
                Text(if (editingId == null) "Save & Sync" else "Save Changes & Sync")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (editingId != null) {
                        editingId = null
                        remarks = ""
                        url = ""
                    } else {
                        onDismiss()
                    }
                },
            ) {
                Text(if (editingId == null) "Close" else "Cancel edit")
            }
        },
    )

    pendingDelete?.let { subscription ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            shape = RoundedCornerShape(22.dp),
            title = { Text(stringResource(R.string.delete_subscription_705)) },
            text = {
                Text("Delete ‘${subscription.remarks}’ and its imported servers? This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSubscription(subscription.id, true)
                        pendingDelete = null
                        if (editingId == subscription.id) {
                            editingId = null
                            remarks = ""
                            url = ""
                        }
                    },
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
