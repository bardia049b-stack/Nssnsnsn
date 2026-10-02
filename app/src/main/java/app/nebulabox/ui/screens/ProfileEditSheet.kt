package app.nebulabox.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.nebulabox.R
import app.nebulabox.data.Profile
import app.nebulabox.data.Protocol
import app.nebulabox.data.TlsSettings
import app.nebulabox.data.Transport
import app.nebulabox.util.ShareLinkParser

/**
 * Manual entry form for a server. Fields shown depend on the protocol, because
 * a VLESS reality server and a Shadowsocks server share almost nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditSheet(
    initial: Profile?,
    onDismiss: () -> Unit,
    onSave: (Profile) -> Unit,
) {
    var profile by remember {
        mutableStateOf(
            initial ?: Profile(
                id = ShareLinkParser.newId(),
                name = "",
                protocol = Protocol.VLESS,
                server = "",
                serverPort = 443,
            ),
        )
    }
    var serverPort by remember { mutableStateOf(profile.serverPort.toString()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 640.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(
                if (initial == null) R.string.action_new_profile else R.string.action_edit,
            ),
            style = MaterialTheme.typography.titleLarge,
        )

        OutlinedTextField(
            value = profile.name,
            onValueChange = { profile = profile.copy(name = it) },
            label = { Text(stringResource(R.string.field_name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        ProtocolPicker(
            value = profile.protocol,
            onChange = { picked ->
                profile = profile.copy(
                    protocol = picked,
                    serverPort = defaultPort(picked),
                )
                serverPort = defaultPort(picked).toString()
            },
        )

        OutlinedTextField(
            value = profile.server,
            onValueChange = { profile = profile.copy(server = it) },
            label = { Text(stringResource(R.string.field_server)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedTextField(
            value = serverPort,
            onValueChange = {
                serverPort = it.filter(Char::isDigit)
                profile = profile.copy(serverPort = serverPort.toIntOrNull() ?: 0)
            },
            label = { Text(stringResource(R.string.field_port)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        when (profile.protocol) {
            Protocol.VLESS, Protocol.TUIC -> {
                OutlinedTextField(
                    value = profile.uuid,
                    onValueChange = { profile = profile.copy(uuid = it) },
                    label = { Text(stringResource(R.string.field_uuid)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            Protocol.VMESS -> {
                OutlinedTextField(
                    value = profile.uuid,
                    onValueChange = { profile = profile.copy(uuid = it) },
                    label = { Text(stringResource(R.string.field_uuid)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = profile.alterId.toString(),
                    onValueChange = {
                        profile = profile.copy(alterId = it.toIntOrNull() ?: 0)
                    },
                    label = { Text("Alter ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            Protocol.TROJAN, Protocol.HYSTERIA2 -> {
                OutlinedTextField(
                    value = profile.password,
                    onValueChange = { profile = profile.copy(password = it) },
                    label = { Text(stringResource(R.string.field_password)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            Protocol.SHADOWSOCKS -> {
                OutlinedTextField(
                    value = profile.method,
                    onValueChange = { profile = profile.copy(method = it) },
                    label = { Text(stringResource(R.string.field_method)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = profile.password,
                    onValueChange = { profile = profile.copy(password = it) },
                    label = { Text(stringResource(R.string.field_password)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            Protocol.SOCKS, Protocol.HTTP -> {
                OutlinedTextField(
                    value = profile.username,
                    onValueChange = { profile = profile.copy(username = it) },
                    label = { Text(stringResource(R.string.field_username)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = profile.password,
                    onValueChange = { profile = profile.copy(password = it) },
                    label = { Text(stringResource(R.string.field_password)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            Protocol.WIREGUARD -> {
                OutlinedTextField(
                    value = profile.privateKey,
                    onValueChange = { profile = profile.copy(privateKey = it) },
                    label = { Text(stringResource(R.string.field_private_key)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = profile.peerPublicKey,
                    onValueChange = { profile = profile.copy(peerPublicKey = it) },
                    label = { Text(stringResource(R.string.field_peer_key)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            Protocol.SSH -> {
                OutlinedTextField(
                    value = profile.username,
                    onValueChange = { profile = profile.copy(username = it) },
                    label = { Text(stringResource(R.string.field_username)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = profile.password,
                    onValueChange = { profile = profile.copy(password = it) },
                    label = { Text(stringResource(R.string.field_password)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            Protocol.NAIVE, Protocol.DIRECT -> Unit
        }

        // transport + tls apply to the stream protocols only
        if (profile.protocol in setOf(
                Protocol.VLESS, Protocol.VMESS, Protocol.TROJAN, Protocol.SHADOWSOCKS,
            )
        ) {
            OutlinedTextField(
                value = profile.transport.type,
                onValueChange = {
                    profile = profile.copy(transport = profile.transport.copy(type = it))
                },
                label = { Text(stringResource(R.string.field_transport)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = profile.transport.host,
                onValueChange = {
                    profile = profile.copy(transport = profile.transport.copy(host = it))
                },
                label = { Text("Host / SNI") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = profile.transport.path,
                onValueChange = {
                    profile = profile.copy(transport = profile.transport.copy(path = it))
                },
                label = { Text("Path") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            SwitchRow(
                label = stringResource(R.string.field_tls),
                checked = profile.tls.enabled,
                onChange = { on ->
                    profile = profile.copy(tls = profile.tls.copy(enabled = on))
                },
            )
            if (profile.tls.enabled) {
                OutlinedTextField(
                    value = profile.tls.serverName,
                    onValueChange = {
                        profile = profile.copy(tls = profile.tls.copy(serverName = it))
                    },
                    label = { Text(stringResource(R.string.field_sni)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                SwitchRow(
                    label = stringResource(R.string.field_insecure),
                    checked = profile.tls.insecure,
                    onChange = { on ->
                        profile = profile.copy(tls = profile.tls.copy(insecure = on))
                    },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
            Button(
                onClick = { onSave(profile) },
                enabled = profile.server.isNotBlank() && profile.serverPort > 0,
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProtocolPicker(value: Protocol, onChange: (Protocol) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value.wire,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.field_protocol)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Protocol.entries.forEach { protocol ->
                DropdownMenuItem(
                    text = { Text(protocol.wire) },
                    onClick = {
                        onChange(protocol)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun defaultPort(protocol: Protocol): Int = when (protocol) {
    Protocol.SSH -> 22
    Protocol.SOCKS -> 1080
    Protocol.HTTP -> 8080
    Protocol.SHADOWSOCKS, Protocol.TROJAN, Protocol.VLESS, Protocol.VMESS,
    Protocol.HYSTERIA2, Protocol.TUIC, Protocol.WIREGUARD, Protocol.NAIVE -> 443
    Protocol.DIRECT -> 0
}
