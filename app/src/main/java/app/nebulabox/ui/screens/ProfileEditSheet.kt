package app.nebulabox.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.nebulabox.R
import app.nebulabox.data.Profile
import app.nebulabox.data.Protocol
import app.nebulabox.util.ShareLinkParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditSheet(
    initial: Profile?,
    onDismiss: () -> Unit,
    onSave: (Profile) -> Unit,
) {
    val context = LocalContext.current
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
    var alpnText by remember { mutableStateOf(profile.tls.alpn.joinToString(",")) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 660.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
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

        if (profile.protocol == Protocol.CUSTOM) {
            OutlinedButton(
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = cm?.primaryClip?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    if (clip.isNotBlank()) {
                        profile = profile.copy(customConfig = clip)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.ContentPaste, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_paste_clipboard))
            }

            OutlinedTextField(
                value = profile.customConfig,
                onValueChange = { profile = profile.copy(customConfig = it) },
                label = { Text(stringResource(R.string.custom_json_config)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 8,
                maxLines = 16,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        } else {
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
                Protocol.VLESS -> {
                    OutlinedTextField(
                        value = profile.uuid,
                        onValueChange = { profile = profile.copy(uuid = it) },
                        label = { Text(stringResource(R.string.field_uuid)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    SimpleDropdown(
                        label = stringResource(R.string.flow),
                        value = profile.flow,
                        options = listOf("", "xtls-rprx-vision", "xtls-rprx-vision-udp443"),
                        onSelect = { profile = profile.copy(flow = it) },
                    )
                    OutlinedTextField(
                        value = profile.encryption,
                        onValueChange = { profile = profile.copy(encryption = it) },
                        label = { Text(stringResource(R.string.encryption_default_none)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }

                Protocol.TUIC -> {
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
                    SimpleDropdown(
                        label = stringResource(R.string.vmess_security),
                        value = profile.security.ifBlank { "auto" },
                        options = listOf("auto", "aes-128-gcm", "chacha20-poly1305", "none", "zero"),
                        onSelect = { profile = profile.copy(security = it) },
                    )
                }

                Protocol.TROJAN -> {
                    OutlinedTextField(
                        value = profile.password,
                        onValueChange = { profile = profile.copy(password = it) },
                        label = { Text(stringResource(R.string.field_password)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }

                Protocol.HYSTERIA2 -> {
                    OutlinedTextField(
                        value = profile.password,
                        onValueChange = { profile = profile.copy(password = it) },
                        label = { Text(stringResource(R.string.auth_password)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = profile.obfsPassword,
                        onValueChange = { profile = profile.copy(obfsPassword = it) },
                        label = { Text(stringResource(R.string.salamander_obfs_password_optional)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = profile.portHopping,
                        onValueChange = { profile = profile.copy(portHopping = it) },
                        label = { Text(stringResource(R.string.port_hopping_e_g_20000_50000)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }

                Protocol.SHADOWSOCKS -> {
                    SimpleDropdown(
                        label = stringResource(R.string.field_method),
                        value = profile.method.ifBlank { "2022-blake3-aes-128-gcm" },
                        options = listOf(
                            "2022-blake3-aes-128-gcm",
                            "2022-blake3-aes-256-gcm",
                            "2022-blake3-chacha20-poly1305",
                            "aes-256-gcm",
                            "aes-128-gcm",
                            "chacha20-ietf-poly1305",
                            "xchacha20-ietf-poly1305",
                            "none",
                        ),
                        onSelect = { profile = profile.copy(method = it) },
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
                    OutlinedTextField(
                        value = profile.localAddresses.joinToString(","),
                        onValueChange = {
                            val list = it.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() }
                            profile = profile.copy(localAddresses = list)
                        },
                        label = { Text(stringResource(R.string.local_address_e_g_172_16_0_2_32)) },
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

                Protocol.NAIVE, Protocol.CUSTOM, Protocol.DIRECT -> Unit
            }

            if (profile.protocol in setOf(
                    Protocol.VLESS, Protocol.VMESS, Protocol.TROJAN, Protocol.SHADOWSOCKS, Protocol.HYSTERIA2,
                )
            ) {
                if (profile.protocol != Protocol.HYSTERIA2) {
                    SimpleDropdown(
                        label = stringResource(R.string.network_transport),
                        value = profile.transport.type.ifBlank { "tcp" },
                        options = listOf("tcp", "ws", "httpupgrade", "xhttp", "grpc", "kcp"),
                        onSelect = {
                            profile = profile.copy(transport = profile.transport.copy(type = it))
                        },
                    )
                    OutlinedTextField(
                        value = profile.transport.host,
                        onValueChange = {
                            profile = profile.copy(transport = profile.transport.copy(host = it))
                        },
                        label = { Text(stringResource(R.string.request_host_authority)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = if (profile.transport.type == "grpc") profile.transport.serviceName else profile.transport.path,
                        onValueChange = {
                            profile = if (profile.transport.type == "grpc") {
                                profile.copy(transport = profile.transport.copy(serviceName = it))
                            } else {
                                profile.copy(transport = profile.transport.copy(path = it))
                            }
                        },
                        label = { Text(if (profile.transport.type == "grpc") "gRPC ServiceName" else "Path (e.g. /?ed=2560)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }

                SimpleDropdown(
                    label = stringResource(R.string.stream_security),
                    value = when {
                        profile.tls.reality -> "reality"
                        profile.tls.enabled -> "tls"
                        else -> "none"
                    },
                    options = listOf("none", "tls", "reality"),
                    onSelect = { sec ->
                        profile = profile.copy(
                            tls = profile.tls.copy(
                                enabled = sec == "tls" || sec == "reality",
                                reality = sec == "reality",
                            ),
                        )
                    },
                )

                if (profile.tls.enabled || profile.tls.reality) {
                    OutlinedTextField(
                        value = profile.tls.serverName,
                        onValueChange = {
                            profile = profile.copy(tls = profile.tls.copy(serverName = it))
                        },
                        label = { Text(stringResource(R.string.field_sni)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    SimpleDropdown(
                        label = stringResource(R.string.utls_fingerprint),
                        value = profile.tls.utlsFingerprint,
                        options = listOf("", "chrome", "firefox", "safari", "ios", "android", "edge", "360", "qq", "random", "randomized"),
                        onSelect = {
                            profile = profile.copy(tls = profile.tls.copy(utls = it.isNotEmpty(), utlsFingerprint = it))
                        },
                    )
                    OutlinedTextField(
                        value = alpnText,
                        onValueChange = {
                            alpnText = it
                            val list = it.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() }
                            profile = profile.copy(tls = profile.tls.copy(alpn = list))
                        },
                        label = { Text(stringResource(R.string.alpn_e_g_h2_http_1_1_or_h3)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = profile.finalMask,
                        onValueChange = { profile = profile.copy(finalMask = it) },
                        label = { Text(stringResource(R.string.finalmask_json_fm)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = profile.tls.echConfigList,
                        onValueChange = {
                            profile = profile.copy(tls = profile.tls.copy(echConfigList = it))
                        },
                        label = { Text(stringResource(R.string.ech_config_list_ech)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    if (profile.tls.reality) {
                        OutlinedTextField(
                            value = profile.tls.realityPublicKey,
                            onValueChange = {
                                profile = profile.copy(tls = profile.tls.copy(realityPublicKey = it))
                            },
                            label = { Text(stringResource(R.string.reality_public_key_pbk)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = profile.tls.realityShortId,
                            onValueChange = {
                                profile = profile.copy(tls = profile.tls.copy(realityShortId = it))
                            },
                            label = { Text(stringResource(R.string.reality_short_id_sid)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = profile.tls.realitySpiderX,
                            onValueChange = {
                                profile = profile.copy(tls = profile.tls.copy(realitySpiderX = it))
                            },
                            label = { Text(stringResource(R.string.reality_spiderx_spx)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    } else {
                        SwitchRow(
                            label = stringResource(R.string.field_insecure),
                            checked = profile.tls.insecure,
                            onChange = { on ->
                                profile = profile.copy(tls = profile.tls.copy(insecure = on))
                            },
                        )
                    }
                }
            }
        }

        val canSave = if (profile.protocol == Protocol.CUSTOM) {
            profile.customConfig.trim().startsWith("{")
        } else {
            profile.server.isNotBlank() && profile.serverPort > 0
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
            Button(
                onClick = {
                    val toSave = if (profile.protocol == Protocol.CUSTOM) {
                        val parsed = runCatching {
                            ShareLinkParser.parseJsonDocument(profile.customConfig).firstOrNull()
                        }.getOrNull()
                        profile.copy(
                            name = profile.name.ifBlank { parsed?.name ?: "Custom JSON" },
                            server = parsed?.server ?: profile.server,
                            serverPort = parsed?.serverPort ?: profile.serverPort,
                            customConfig = profile.customConfig.trim(),
                        )
                    } else {
                        profile
                    }
                    onSave(toSave)
                },
                enabled = canSave,
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
private fun SimpleDropdown(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value.ifBlank { "none" },
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt.ifBlank { "none" }) },
                    onClick = {
                        onSelect(opt)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProtocolPicker(value: Protocol, onChange: (Protocol) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val supportedProtocols = listOf(
        Protocol.VLESS,
        Protocol.VMESS,
        Protocol.TROJAN,
        Protocol.SHADOWSOCKS,
        Protocol.HYSTERIA2,
        Protocol.WIREGUARD,
        Protocol.SOCKS,
        Protocol.HTTP,
        Protocol.CUSTOM,
    )
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = if (value == Protocol.CUSTOM) "custom (JSON)" else value.wire,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.field_protocol)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            supportedProtocols.forEach { protocol ->
                DropdownMenuItem(
                    text = { Text(if (protocol == Protocol.CUSTOM) "custom (JSON)" else protocol.wire) },
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
    Protocol.WIREGUARD -> 51820
    Protocol.SHADOWSOCKS, Protocol.TROJAN, Protocol.VLESS, Protocol.VMESS,
    Protocol.HYSTERIA2, Protocol.TUIC, Protocol.NAIVE -> 443
    Protocol.CUSTOM, Protocol.DIRECT -> 0
}
