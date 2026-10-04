package app.nebulabox.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.nebulabox.R

@Composable
fun ImportConfigDialog(
    onDismiss: () -> Unit,
    onSubmit: (text: String, asSubscription: Boolean, subRemarks: String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var subRemarks by remember { mutableStateOf("") }
    var saveAsSub by remember { mutableStateOf(true) }
    val isUrl = text.trim().let { it.startsWith("http://") || it.startsWith("https://") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        title = { Text(stringResource(R.string.import_config_subscription)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.share_links_subscription_url_or_json)) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 7,
                )
                if (isUrl) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { saveAsSub = !saveAsSub },
                    ) {
                        Checkbox(checked = saveAsSub, onCheckedChange = { saveAsSub = it })
                        Text(stringResource(R.string.save_as_subscription_group), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (saveAsSub) {
                        OutlinedTextField(
                            value = subRemarks,
                            onValueChange = { subRemarks = it },
                            label = { Text(stringResource(R.string.group_name_e_g_main_sub)) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(text.trim(), isUrl && saveAsSub, subRemarks.trim()) },
                enabled = text.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
