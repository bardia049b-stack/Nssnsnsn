package app.nebulabox.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nebulabox.BuildConfig
import app.nebulabox.R
import app.nebulabox.engine.Engines
import app.nebulabox.locale.LocaleManager
import app.nebulabox.ui.NebulaViewModel

@Composable
fun SettingsScreen(viewModel: NebulaViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.tab_settings),
            style = MaterialTheme.typography.headlineMedium,
        )

        SectionCard(stringResource(R.string.section_appearance)) {
            Picker(
                label = stringResource(R.string.setting_language),
                options = listOf(
                    LocaleManager.SYSTEM to stringResource(R.string.lang_system),
                    LocaleManager.ENGLISH to stringResource(R.string.lang_english),
                    LocaleManager.PERSIAN to stringResource(R.string.lang_persian),
                ),
                selectedKey = LocaleManager.storedLanguage(),
                onSelect = { code ->
                    LocaleManager.storeLanguage(context, code)
                    viewModel.updateSettings { it.copy(language = code) }
                    (context as? Activity)?.recreate()
                },
            )
            Picker(
                label = stringResource(R.string.setting_theme),
                options = listOf(
                    "system" to stringResource(R.string.theme_system),
                    "light" to stringResource(R.string.theme_light),
                    "dark" to stringResource(R.string.theme_dark),
                ),
                selectedKey = settings.theme,
                onSelect = { value -> viewModel.updateSettings { it.copy(theme = value) } },
            )
            ToggleRow(
                label = stringResource(R.string.setting_dynamic_color),
                checked = settings.dynamicColor,
                onChange = { on -> viewModel.updateSettings { it.copy(dynamicColor = on) } },
            )
        }

        SectionCard(stringResource(R.string.section_routing)) {
            Picker(
                label = stringResource(R.string.setting_route_mode),
                options = listOf(
                    "global" to stringResource(R.string.route_global),
                    "rule" to stringResource(R.string.route_rule),
                    "direct" to stringResource(R.string.route_direct),
                ),
                selectedKey = settings.routeMode,
                onSelect = { value -> viewModel.updateSettings { it.copy(routeMode = value) } },
            )
            ToggleRow(
                label = stringResource(R.string.setting_bypass_lan),
                checked = settings.bypassLan,
                onChange = { on -> viewModel.updateSettings { it.copy(bypassLan = on) } },
            )
            ToggleRow(
                label = stringResource(R.string.setting_bypass_cn),
                checked = settings.bypassChina,
                onChange = { on -> viewModel.updateSettings { it.copy(bypassChina = on) } },
            )
            ToggleRow(
                label = stringResource(R.string.setting_block_ads),
                checked = settings.blockAds,
                onChange = { on -> viewModel.updateSettings { it.copy(blockAds = on) } },
            )
            ToggleRow(
                label = stringResource(R.string.setting_ipv6),
                checked = settings.ipv6,
                onChange = { on -> viewModel.updateSettings { it.copy(ipv6 = on) } },
            )
        }

        SectionCard(stringResource(R.string.section_dns)) {
            TextRow(
                label = stringResource(R.string.setting_remote_dns),
                value = settings.remoteDns,
                onChange = { v -> viewModel.updateSettings { it.copy(remoteDns = v) } },
            )
            TextRow(
                label = stringResource(R.string.setting_direct_dns),
                value = settings.directDns,
                onChange = { v -> viewModel.updateSettings { it.copy(directDns = v) } },
            )
            Picker(
                label = stringResource(R.string.setting_dns_strategy),
                options = listOf(
                    "prefer_ipv4" to "prefer_ipv4",
                    "prefer_ipv6" to "prefer_ipv6",
                    "ipv4_only" to "ipv4_only",
                    "ipv6_only" to "ipv6_only",
                ),
                selectedKey = settings.dnsStrategy,
                onSelect = { v -> viewModel.updateSettings { it.copy(dnsStrategy = v) } },
            )
        }

        SectionCard(stringResource(R.string.section_tunnel)) {
            ToggleRow(
                label = stringResource(R.string.setting_tcp_mux),
                checked = settings.tcpMux,
                onChange = { on -> viewModel.updateSettings { it.copy(tcpMux = on) } },
            )
            ToggleRow(
                label = stringResource(R.string.setting_tcp_fast_open),
                checked = settings.tcpFastOpen,
                onChange = { on -> viewModel.updateSettings { it.copy(tcpFastOpen = on) } },
            )
            ToggleRow(
                label = stringResource(R.string.setting_sniffing),
                checked = settings.sniffing,
                onChange = { on -> viewModel.updateSettings { it.copy(sniffing = on) } },
            )
            Picker(
                label = stringResource(R.string.setting_log_level),
                options = listOf("trace", "debug", "info", "warning", "error", "fatal")
                    .map { it to it },
                selectedKey = settings.logLevel,
                onSelect = { v -> viewModel.updateSettings { it.copy(logLevel = v) } },
            )
        }

        SectionCard(stringResource(R.string.section_about)) {
            InfoRow(stringResource(R.string.about_version), BuildConfig.VERSION_NAME)
            InfoRow(
                stringResource(R.string.about_engine),
                Engines.active.value?.implementationName ?: "-",
            )
            InfoRow(
                stringResource(R.string.about_engine_state),
                stringResource(
                    if (Engines.active.value?.functional == true) {
                        R.string.engine_ready
                    } else {
                        R.string.engine_missing_title
                    },
                ),
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TextRow(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Picker(
    label: String,
    options: List<Pair<String, String>>,
    selectedKey: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedKey }?.second ?: selectedKey

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (key, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(key)
                        expanded = false
                    },
                )
            }
        }
    }
}
