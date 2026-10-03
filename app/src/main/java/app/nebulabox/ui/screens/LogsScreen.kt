package app.nebulabox.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nebulabox.R
import app.nebulabox.ui.NebulaViewModel
import app.nebulabox.util.AppLogger
import app.nebulabox.util.ClipboardHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsScreen(viewModel: NebulaViewModel) {
    val context = LocalContext.current
    val appLogs by AppLogger.logs.collectAsState()
    val crashes by AppLogger.crashes.collectAsState()
    val lastConfig by AppLogger.lastGeneratedConfig.collectAsState()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var levelFilter by rememberSaveable { mutableStateOf("ALL") }

    LaunchedEffect(Unit) {
        AppLogger.refreshNativeCrashes()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Diagnostics",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${appLogs.size} logs · ${crashes.size} crashes",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        val textToCopy = when (selectedTab) {
                            0 -> AppLogger.exportLogsText()
                            1 -> AppLogger.exportCrashesText()
                            2 -> lastConfig.ifBlank { "(No config generated yet)" }
                            else -> AppLogger.readSystemLogcat()
                        }
                        ClipboardHelper.copyText(context, "JavidTun Diagnostics", textToCopy)
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy",
                    )
                }
                IconButton(
                    onClick = {
                        when (selectedTab) {
                            0 -> {
                                AppLogger.clearLogs()
                                viewModel.clearLogs()
                            }
                            1 -> AppLogger.clearCrashes()
                            else -> AppLogger.refreshNativeCrashes()
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteSweep,
                        contentDescription = stringResource(R.string.action_clear_logs),
                    )
                }
            }
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            divider = {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            },
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Logs (${appLogs.size})", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Crashes", maxLines = 1)
                        if (crashes.isNotEmpty()) {
                            Spacer(Modifier.width(6.dp))
                            Badge(containerColor = MaterialTheme.colorScheme.error) {
                                Text(crashes.size.toString())
                            }
                        }
                    }
                },
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Config", maxLines = 1) },
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                text = { Text("Logcat", maxLines = 1) },
            )
        }

        when (selectedTab) {
            0 -> LiveLogsTab(
                entries = appLogs,
                levelFilter = levelFilter,
                onLevelFilterChange = { levelFilter = it },
                onCopyAll = {
                    ClipboardHelper.copyText(context, "JavidTun Logs", AppLogger.exportLogsText())
                },
            )
            1 -> CrashesTab(
                crashes = crashes,
                onRefresh = { AppLogger.refreshNativeCrashes() },
                onClear = { AppLogger.clearCrashes() },
                onCopy = { text -> ClipboardHelper.copyText(context, "JavidTun Crash Report", text) },
            )
            2 -> ConfigTab(
                configJson = lastConfig,
                onCopy = { ClipboardHelper.copyText(context, "JavidTun Config", lastConfig) },
            )
            3 -> SystemLogcatTab(
                onCopy = { text -> ClipboardHelper.copyText(context, "JavidTun Logcat", text) },
            )
        }
    }
}

@Composable
private fun LiveLogsTab(
    entries: List<AppLogger.LogEntry>,
    levelFilter: String,
    onLevelFilterChange: (String) -> Unit,
    onCopyAll: () -> Unit,
) {
    val listState = rememberLazyListState()
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }

    val filtered = remember(entries, levelFilter) {
        when (levelFilter) {
            "ERROR" -> entries.filter { it.level == AppLogger.Level.ERROR }
            "WARN" -> entries.filter { it.level == AppLogger.Level.WARN || it.level == AppLogger.Level.ERROR }
            "INFO" -> entries.filter { it.level != AppLogger.Level.DEBUG }
            else -> entries
        }
    }

    LaunchedEffect(filtered.size) {
        if (filtered.isNotEmpty()) {
            listState.animateScrollToItem(filtered.size - 1)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("ALL", "INFO", "WARN", "ERROR").forEach { lvl ->
                FilterChip(
                    selected = levelFilter == lvl,
                    onClick = { onLevelFilterChange(lvl) },
                    label = { Text(lvl, style = MaterialTheme.typography.labelMedium) },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = onCopyAll,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copy All", maxLines = 1, softWrap = false)
            }
        }

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.empty_logs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            SelectionContainer {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(filtered, key = { it.id }) { entry ->
                        val baseText = "${timeFormat.format(Date(entry.timestamp))} ${entry.level.label}/${entry.tag}: ${entry.message}"
                        val fullText = if (!entry.stacktrace.isNullOrBlank()) {
                            "$baseText\n${entry.stacktrace}"
                        } else {
                            baseText
                        }
                        Text(
                            text = fullText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp,
                                lineHeight = 15.sp,
                            ),
                            color = colorForAppLevel(entry.level),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CrashesTab(
    crashes: List<AppLogger.CrashReport>,
    onRefresh: () -> Unit,
    onClear: () -> Unit,
    onCopy: (String) -> Unit,
) {
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onRefresh,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Scan Native", maxLines = 1, softWrap = false)
            }
            if (crashes.isNotEmpty()) {
                OutlinedButton(
                    onClick = { onCopy(AppLogger.exportCrashesText()) },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy All", maxLines = 1, softWrap = false)
                }
                OutlinedButton(
                    onClick = onClear,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Clear", maxLines = 1, softWrap = false)
                }
            }
        }

        if (crashes.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.BugReport,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "No crash reports recorded.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(crashes, key = { it.id }) { crash ->
                    var expanded by rememberSaveable(crash.id) { mutableStateOf(true) }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(
                            0.8.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { expanded = !expanded },
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = crash.source,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier
                                            .background(
                                                MaterialTheme.colorScheme.errorContainer,
                                                RoundedCornerShape(6.dp),
                                            )
                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = timeFormat.format(Date(crash.timestamp)),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { onCopy(crash.details) }) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy crash")
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = crash.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error,
                            )
                            if (expanded) {
                                Spacer(Modifier.height(8.dp))
                                SelectionContainer {
                                    Text(
                                        text = crash.details,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            lineHeight = 14.sp,
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                MaterialTheme.colorScheme.surface,
                                                RoundedCornerShape(10.dp),
                                            )
                                            .padding(10.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigTab(
    configJson: String,
    onCopy: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Active Core JSON Configuration",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = onCopy,
                enabled = configJson.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copy JSON", maxLines = 1, softWrap = false)
            }
        }

        if (configJson.isBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Connect to a server profile to generate and inspect the active JSON configuration.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            RoundedCornerShape(12.dp),
                        )
                        .padding(14.dp),
                ) {
                    Text(
                        text = configJson,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemLogcatTab(
    onCopy: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var logcatText by remember { mutableStateOf("Loading system logcat...") }

    fun reload() {
        scope.launch {
            logcatText = withContext(Dispatchers.IO) {
                AppLogger.deviceInfoHeader() + "\n" + AppLogger.readSystemLogcat(400)
            }
        }
    }

    LaunchedEffect(Unit) {
        reload()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { reload() },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Refresh", maxLines = 1, softWrap = false)
            }
            OutlinedButton(
                onClick = { onCopy(logcatText) },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copy Logcat", maxLines = 1, softWrap = false)
            }
        }

        SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        RoundedCornerShape(12.dp),
                    )
                    .padding(12.dp),
            ) {
                Text(
                    text = logcatText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun colorForAppLevel(level: AppLogger.Level): Color = when (level) {
    AppLogger.Level.ERROR -> MaterialTheme.colorScheme.error
    AppLogger.Level.WARN -> MaterialTheme.colorScheme.tertiary
    AppLogger.Level.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
    AppLogger.Level.INFO -> MaterialTheme.colorScheme.onSurface
}
