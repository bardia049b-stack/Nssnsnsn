package app.nebulabox.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nebulabox.ui.NebulaViewModel
import app.nebulabox.util.AppLogger
import app.nebulabox.util.ClipboardHelper

@Composable
fun LogsScreen(viewModel: NebulaViewModel) {
    val entries by AppLogger.entries.collectAsStateWithLifecycle()
    val crashes by AppLogger.crashes.collectAsStateWithLifecycle()
    val activeConfigJson by AppLogger.lastActiveConfigJson.collectAsStateWithLifecycle()
    val engineLogs by viewModel.logs.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var filterLevel by rememberSaveable { mutableStateOf<AppLogger.Level?>(null) }

    LaunchedEffect(engineLogs.size) {
        engineLogs.lastOrNull()?.let { line ->
            if (entries.lastOrNull()?.message != line) {
                AppLogger.i("CoreEngine", line)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(
                onClick = {
                    val report = AppLogger.exportDiagnosticReport(context)
                    ClipboardHelper.copyText(context, "JavidTun Diagnostics", report)
                },
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy Full Report")
            }
            IconButton(
                onClick = {
                    val report = AppLogger.exportDiagnosticReport(context)
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "JavidTun Diagnostic Report")
                        putExtra(Intent.EXTRA_TEXT, report)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share Diagnostic Report"))
                },
            ) {
                Icon(Icons.Outlined.Share, contentDescription = "Share Report")
            }
            IconButton(
                onClick = {
                    if (selectedTab == 1) {
                        AppLogger.clearCrashes()
                    } else {
                        AppLogger.clearLogs()
                        viewModel.clearLogs()
                    }
                },
            ) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Clear")
            }
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Live Logs (${entries.size})", maxLines = 1) },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Crashes", maxLines = 1)
                        if (crashes.isNotEmpty()) {
                            Badge(containerColor = MaterialTheme.colorScheme.error) {
                                Text("${crashes.size}")
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
        }

        Spacer(Modifier.height(10.dp))

        when (selectedTab) {
            0 -> LiveLogsTab(
                entries = entries,
                filterLevel = filterLevel,
                onFilterChange = { filterLevel = it },
                onCopyLine = { line ->
                    ClipboardHelper.copyText(context, "Log Entry", line)
                },
            )
            1 -> CrashesTab(
                crashes = crashes,
                onCopyCrash = { crash ->
                    ClipboardHelper.copyText(context, "Crash Report", crash)
                },
            )
            2 -> ActiveConfigTab(
                configJson = activeConfigJson,
                onCopy = {
                    ClipboardHelper.copyText(context, "Active Config JSON", activeConfigJson)
                },
            )
        }
    }
}

@Composable
private fun LiveLogsTab(
    entries: List<AppLogger.LogEntry>,
    filterLevel: AppLogger.Level?,
    onFilterChange: (AppLogger.Level?) -> Unit,
    onCopyLine: (String) -> Unit,
) {
    val filtered = if (filterLevel == null) {
        entries
    } else {
        entries.filter { it.level.priority >= filterLevel.priority }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(filtered.size) {
        if (filtered.isNotEmpty()) {
            listState.animateScrollToItem(filtered.lastIndex)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = filterLevel == null,
                onClick = { onFilterChange(null) },
                label = { Text("All") },
            )
            FilterChip(
                selected = filterLevel == AppLogger.Level.INFO,
                onClick = { onFilterChange(AppLogger.Level.INFO) },
                label = { Text("Info+") },
            )
            FilterChip(
                selected = filterLevel == AppLogger.Level.WARN,
                onClick = { onFilterChange(AppLogger.Level.WARN) },
                label = { Text("Warn+") },
            )
            FilterChip(
                selected = filterLevel == AppLogger.Level.ERROR,
                onClick = { onFilterChange(AppLogger.Level.ERROR) },
                label = { Text("Errors") },
            )
        }

        Spacer(Modifier.height(8.dp))

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
        ) {
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No log events recorded yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(filtered, key = { it.id }) { entry ->
                            LogEntryItem(entry = entry, onCopy = { onCopyLine(entry.format()) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryItem(
    entry: AppLogger.LogEntry,
    onCopy: () -> Unit,
) {
    val badgeColor = when (entry.level) {
        AppLogger.Level.DEBUG -> Color(0xFF64748B)
        AppLogger.Level.INFO -> Color(0xFF0EA5E9)
        AppLogger.Level.WARN -> Color(0xFFF59E0B)
        AppLogger.Level.ERROR -> Color(0xFFEF4444)
        AppLogger.Level.CRASH -> Color(0xFFDC2626)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                color = badgeColor.copy(alpha = 0.16f),
                shape = RoundedCornerShape(5.dp),
            ) {
                Text(
                    text = entry.level.label,
                    color = badgeColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            Text(
                text = entry.timestamp,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = entry.tag,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(20.dp),
            ) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = "Copy",
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = entry.message,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!entry.stackTrace.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = entry.stackTrace,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun CrashesTab(
    crashes: List<AppLogger.CrashReport>,
    onCopyCrash: (String) -> Unit,
) {
    if (crashes.isEmpty()) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.BugReport,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "No crashes recorded",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(crashes, key = { it.id }) { crash ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                    ),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = crash.summary,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    text = "${crash.timestamp} • thread: ${crash.threadName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            FilledTonalButton(
                                onClick = {
                                    onCopyCrash(
                                        buildString {
                                            appendLine("Time: ${crash.timestamp}")
                                            appendLine("Thread: ${crash.threadName}")
                                            appendLine("Device: ${crash.deviceInfo}")
                                            appendLine("Summary: ${crash.summary}")
                                            appendLine()
                                            appendLine(crash.stackTrace)
                                        },
                                    )
                                },
                            ) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Copy")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        SelectionContainer {
                            Text(
                                text = crash.stackTrace,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveConfigTab(
    configJson: String,
    onCopy: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 16.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
    ) {
        if (configJson.isBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Connect to a server to inspect the generated JSON configuration.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Generated Core Config JSON",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                    )
                    FilledTonalButton(
                        onClick = onCopy,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = "Copy JSON",
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Copy JSON",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                SelectionContainer {
                    Text(
                        text = configJson,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }
}
