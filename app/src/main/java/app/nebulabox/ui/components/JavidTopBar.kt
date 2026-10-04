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
    onSpeedTest: () -> Unit,
    onOpenLeakTest: () -> Unit,
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
                                    "Search servers...",
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
                                Icon(Icons.Filled.Clear, contentDescription = "Clear")
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
                            contentDescription = "Back",
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
                            contentDescription = "Search",
                        )
                    }
                }

                IconButton(onClick = onOpenSubscriptions) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_subscriptions_24dp),
                        contentDescription = "Subscriptions",
                    )
                }

                Box {
                    IconButton(onClick = { showImportMenu = true }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_add_24dp),
                            contentDescription = "Add",
                        )
                    }
                    DropdownMenu(
                        expanded = showImportMenu,
                        onDismissRequest = { showImportMenu = false },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        DropdownMenuItem(
                            text = { Text("Import from Clipboard") },
                            onClick = {
                                showImportMenu = false
                                onImportClipboard()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Import URL / Subscription / JSON") },
                            onClick = {
                                showImportMenu = false
                                onImportUrlOrText()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Scan QR code") },
                            onClick = {
                                showImportMenu = false
                                onScanQr()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Import QR from image") },
                            onClick = {
                                showImportMenu = false
                                onImportQrImage()
                            },
                        )
                        AppDivider()
                        DropdownMenuItem(
                            text = { Text("New VLESS") },
                            onClick = { showImportMenu = false; onNewProtocol(Protocol.VLESS) },
                        )
                        DropdownMenuItem(
                            text = { Text("New VMess") },
                            onClick = { showImportMenu = false; onNewProtocol(Protocol.VMESS) },
                        )
                        DropdownMenuItem(
                            text = { Text("New Trojan") },
                            onClick = { showImportMenu = false; onNewProtocol(Protocol.TROJAN) },
                        )
                        DropdownMenuItem(
                            text = { Text("New Shadowsocks") },
                            onClick = { showImportMenu = false; onNewProtocol(Protocol.SHADOWSOCKS) },
                        )
                        DropdownMenuItem(
                            text = { Text("New Hysteria2") },
                            onClick = { showImportMenu = false; onNewProtocol(Protocol.HYSTERIA2) },
                        )
                        DropdownMenuItem(
                            text = { Text("New WireGuard") },
                            onClick = { showImportMenu = false; onNewProtocol(Protocol.WIREGUARD) },
                        )
                        DropdownMenuItem(
                            text = { Text("New SOCKS / HTTP") },
                            onClick = { showImportMenu = false; onNewProtocol(Protocol.SOCKS) },
                        )
                        DropdownMenuItem(
                            text = { Text("New Custom JSON") },
                            onClick = { showImportMenu = false; onNewProtocol(Protocol.CUSTOM) },
                        )
                    }
                }

                Box {
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_more_vert_24dp),
                            contentDescription = "More",
                        )
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        DropdownMenuItem(
                            text = { Text("Real ping all servers") },
                            onClick = { showMoreMenu = false; onPingAllReal() },
                        )
                        DropdownMenuItem(
                            text = { Text("TCP ping all servers") },
                            onClick = { showMoreMenu = false; onPingAllTcp() },
                        )
                        DropdownMenuItem(
                            text = { Text("Sort by ping results") },
                            onClick = { showMoreMenu = false; onSortByTestResults() },
                        )
                        DropdownMenuItem(
                            text = { Text("Update subscriptions") },
                            onClick = { showMoreMenu = false; onUpdateSubscriptions() },
                        )
                        DropdownMenuItem(
                            text = { Text("Restart tunnel") },
                            onClick = { showMoreMenu = false; onRestartService() },
                        )
                        AppDivider()
                        DropdownMenuItem(
                            text = { Text("Speed test & exit IP") },
                            onClick = { showMoreMenu = false; onSpeedTest() },
                        )
                        DropdownMenuItem(
                            text = { Text("WebRTC leak test") },
                            onClick = { showMoreMenu = false; onOpenLeakTest() },
                        )
                        AppDivider()
                        DropdownMenuItem(
                            text = { Text("Export configs to clipboard") },
                            onClick = { showMoreMenu = false; onExportAll() },
                        )
                        DropdownMenuItem(
                            text = { Text("Remove duplicate configs") },
                            onClick = { showMoreMenu = false; onDeleteDuplicates() },
                        )
                        DropdownMenuItem(
                            text = { Text("Remove invalid configs") },
                            onClick = { showMoreMenu = false; onDeleteInvalid() },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Remove all configs",
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
