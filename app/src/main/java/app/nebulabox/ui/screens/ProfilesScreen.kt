package app.nebulabox.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nebulabox.R
import app.nebulabox.data.Profile
import app.nebulabox.data.Protocol
import app.nebulabox.ui.NebulaViewModel

private fun readClipboardText(context: Context): String {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return ""
    val clip = cm.primaryClip ?: return ""
    if (clip.itemCount <= 0) return ""
    return clip.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
}

@Composable
fun ProfilesScreen(
    viewModel: NebulaViewModel,
    onEdit: (Profile) -> Unit,
    onNew: () -> Unit,
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pasteOpen by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }

    Scaffold(
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FloatingActionButton(
                    onClick = {
                        val clip = readClipboardText(context)
                        if (pasteText.isBlank() && clip.isNotBlank()) {
                            pasteText = clip
                        }
                        pasteOpen = true
                    },
                ) {
                    Icon(Icons.Filled.ContentPaste, stringResource(R.string.action_import))
                }
                FloatingActionButton(onClick = onNew) {
                    Icon(Icons.Filled.Add, stringResource(R.string.action_new_profile))
                }
            }
        },
    ) { padding ->
        if (profiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.empty_profiles_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = stringResource(R.string.empty_profiles_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val clip = readClipboardText(context)
                                if (clip.isNotBlank()) {
                                    viewModel.submitImportText(clip)
                                } else {
                                    pasteOpen = true
                                }
                            },
                        ) {
                            Icon(Icons.Filled.ContentPaste, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_paste_clipboard))
                        }
                        TextButton(onClick = { pasteOpen = true }) {
                            Text(stringResource(R.string.action_import))
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(profiles, key = { _, item -> item.id }) { index, profile ->
                    ProfileRow(
                        profile = profile,
                        selected = profile.id == settings.selectedProfileId,
                        onSelect = { viewModel.selectProfile(profile) },
                        onEdit = { onEdit(profile) },
                        onDelete = { viewModel.deleteProfile(profile.id) },
                        onMoveUp = { if (index > 0) viewModel.moveProfile(index, index - 1) },
                    )
                }
            }
        }
    }

    if (pasteOpen) {
        PasteSheet(
            value = pasteText,
            onValueChange = { pasteText = it },
            onPasteFromClipboard = {
                val clip = readClipboardText(context)
                if (clip.isNotBlank()) pasteText = clip
            },
            onDismiss = {
                pasteOpen = false
                pasteText = ""
            },
            onSubmit = {
                val textToImport = pasteText
                pasteOpen = false
                pasteText = ""
                viewModel.submitImportText(textToImport)
            },
        )
    }
}

@Composable
private fun ProfileRow(
    profile: Profile,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
) {
    val subtitle = when {
        profile.protocol == Protocol.CUSTOM && profile.server.isNotBlank() && profile.serverPort > 0 ->
            "custom (JSON)  ·  ${profile.server}:${profile.serverPort}"
        profile.protocol == Protocol.CUSTOM && profile.server.isNotBlank() ->
            "custom (JSON)  ·  ${profile.server}"
        profile.protocol == Protocol.CUSTOM ->
            "custom (JSON)"
        else ->
            "${profile.protocol.wire}  ·  ${profile.server}:${profile.serverPort}"
    }

    Card(onClick = onSelect, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(Modifier.weight(1f)) {
                Text(profile.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, stringResource(R.string.action_edit))
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun PasteSheet(
    value: String,
    onValueChange: (String) -> Unit,
    onPasteFromClipboard: () -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_import)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.import_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(8.dp))
                OutlinedButton(
                    onClick = onPasteFromClipboard,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.ContentPaste, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_paste_clipboard))
                }
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    maxLines = 12,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    label = { Text(stringResource(R.string.import_label)) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSubmit, enabled = value.isNotBlank()) {
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

/** Unused placeholder kept out of the composition to satisfy lint on some AGP versions. */
@Suppress("unused")
private val arrow = Icons.AutoMirrored.Filled.KeyboardArrowRight
