package app.nebulabox.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nebulabox.R
import app.nebulabox.data.Protocol
import app.nebulabox.ui.AppDivider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JavidTopBar(
    isConnected: Boolean,
    isLoading: Boolean,
    isTesting: Boolean,
    showSearch: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchClose: () -> Unit,
    onSearchToggle: (Boolean) -> Unit,
    onOpenSubscriptions: () -> Unit,
    onCancelTesting: () -> Unit,
    onImportClipboard: () -> Unit,
    onImportUrlOrText: () -> Unit,
    onScanQr: () -> Unit,
    onImportQrImage: () -> Unit,
    onNewProtocol: (Protocol) -> Unit,
    onRestartService: () -> Unit,
    onPingAllTcp: () -> Unit,
    onPingAllReal: () -> Unit,
    onSortByTestResults: () -> Unit,
    onUpdateSubscriptions: () -> Unit,
    onDeleteDuplicates: () -> Unit,
    onDeleteInvalid: () -> Unit,
    onDeleteAll: () -> Unit,
    onExportAll: () -> Unit,
) {
    var showImportMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
        TopAppBar(
            title = {
                if (showSearch) {
                    val focusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                            ),
                            placeholder = {
                                Text(
                                    stringResource(R.string.search_servers),
                                    style = TextStyle(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 16.sp,
                                    ),
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                cursorColor = MaterialTheme.colorScheme.primary,
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.clear))
                            }
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_javid_logo),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            },
            navigationIcon = {
                if (showSearch) {
                    IconButton(onClick = onSearchClose) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_back_24dp),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                }
            },
            actions = {
                if (isTesting) {
                    TextButton(onClick = onCancelTesting) {
                        Text(
                            text = stringResource(android.R.string.cancel),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                if (!showSearch) {
                    IconButton(onClick = { onSearchToggle(true) }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_search_24dp),
                            contentDescription = stringResource(R.string.search),
                        )
                    }
                }

                IconButton(onClick = onOpenSubscriptions) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_subscriptions_24dp),
                        contentDescription = stringResource(R.string.subscriptions),
                    )
                }

                Box {
                    IconButton(onClick = { showImportMenu = true }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_add_24dp),
                            contentDescription = stringResource(R.string.add),
                        )
                    }
                    DropdownMenu(
                        expanded = showImportMenu,
                        onDismissRequest = { showImportMenu = false },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.import_from_clipboard)) },
                            onClick = {
                                showImportMenu = false
                                onImportClipboard()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.import_url_subscription_json)) },
                            onClick = {
                                showImportMenu = false
                                onImportUrlOrText()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.scan_qr_code)) },
                            onClick = {
                                showImportMenu = false
                                onScanQr()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.import_qr_from_image)) },
                            onClick = {
                                showImportMenu = false
                                onImportQrImage()
                            },
                        )
                        AppDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.new_vless)) },
                            onClick = { showImportMenu = false; onNewProtocol(Protocol.VLESS) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.new_vmess)) },
                            onClick = { showImportMenu = false; onNewProtocol(Protocol.VMESS) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.new_trojan)) },
                            onClick = { showImportMenu = false; onNewProtocol(Protocol.TROJAN) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.new_shadowsocks)) },
                            onClick = { showImportMenu = false; onNewProtocol(Protocol.SHADOWSOCKS) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.new_hysteria2)) },
                            onClick = { showImportMenu = false; onNewProtocol(Protocol.HYSTERIA2) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.new_wireguard)) },
                            onClick = { showImportMenu = false; onNewProtocol(Protocol.WIREGUARD) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.new_socks_http)) },
                            onClick = { showImportMenu = false; onNewProtocol(Protocol.SOCKS) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.new_custom_json)) },
                            onClick = { showImportMenu = false; onNewProtocol(Protocol.CUSTOM) },
                        )
                    }
                }

                Box {
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_more_vert_24dp),
                            contentDescription = stringResource(R.string.more),
                        )
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.real_ping_all_servers)) },
                            onClick = { showMoreMenu = false; onPingAllReal() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.tcp_ping_all_servers)) },
                            onClick = { showMoreMenu = false; onPingAllTcp() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sort_by_ping_results)) },
                            onClick = { showMoreMenu = false; onSortByTestResults() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.update_subscriptions)) },
                            onClick = { showMoreMenu = false; onUpdateSubscriptions() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.restart_tunnel)) },
                            onClick = { showMoreMenu = false; onRestartService() },
                        )
                        AppDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.export_configs_to_clipboard)) },
                            onClick = { showMoreMenu = false; onExportAll() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.remove_duplicate_configs)) },
                            onClick = { showMoreMenu = false; onDeleteDuplicates() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.remove_invalid_configs)) },
                            onClick = { showMoreMenu = false; onDeleteInvalid() },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.remove_all_configs),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = { showMoreMenu = false; onDeleteAll() },
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        AnimatedVisibility(
            visible = isLoading,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        )
    }
}
