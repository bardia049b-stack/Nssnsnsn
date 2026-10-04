package app.nebulabox.ui.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.nebulabox.R
import app.nebulabox.ui.NebulaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class InstalledAppEntry(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val iconBitmap: ImageBitmap?,
)

@Composable
fun PerAppProxyScreen(viewModel: NebulaViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showSystemApps by rememberSaveable { mutableStateOf(false) }
    var allApps by remember { mutableStateOf<List<InstalledAppEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        allApps = withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            runCatching {
                packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                    .asSequence()
                    .filter { it.packageName != context.packageName }
                    .map { appInfo ->
                        val label = runCatching {
                            packageManager.getApplicationLabel(appInfo).toString()
                        }.getOrDefault(appInfo.packageName)
                        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                            (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                        val icon = runCatching {
                            drawableToImageBitmap(packageManager.getApplicationIcon(appInfo))
                        }.getOrNull()
                        InstalledAppEntry(
                            packageName = appInfo.packageName,
                            label = label,
                            isSystem = isSystem,
                            iconBitmap = icon,
                        )
                    }
                    .sortedBy { it.label.lowercase() }
                    .toList()
            }.getOrDefault(emptyList())
        }
        isLoading = false
    }

    val filteredApps = remember(allApps, searchQuery, showSystemApps, settings.perAppPackages) {
        allApps.asSequence()
            .filter { showSystemApps || !it.isSystem || it.packageName in settings.perAppPackages }
            .filter {
                searchQuery.isBlank() ||
                    it.label.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
            }
            .sortedWith(
                compareByDescending<InstalledAppEntry> { it.packageName in settings.perAppPackages }
                    .thenBy { it.label.lowercase() },
            )
            .toList()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable {
                    viewModel.updateSettings { it.copy(perAppEnabled = !settings.perAppEnabled) }
                },
            shape = RoundedCornerShape(20.dp),
            color = if (settings.perAppEnabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.use_per_app_proxy),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (settings.perAppEnabled) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = if (settings.perAppEnabled) {
                            "${settings.perAppPackages.size} selected · ${settings.perAppModeLabel()} · reconnect to apply"
                        } else {
                            "Off · selected apps are saved but not applied"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (settings.perAppEnabled) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(
                    checked = settings.perAppEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.updateSettings { it.copy(perAppEnabled = enabled) }
                    },
                )
            }
        }

        Text(
            text = stringResource(R.string.proxy_mode),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 8.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val bypassSelected = settings.perAppMode == "exclude"
            if (bypassSelected) {
                FilledTonalButton(
                    onClick = { viewModel.updateSettings { it.copy(perAppMode = "exclude") } },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(stringResource(R.string.bypass_selected), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                OutlinedButton(
                    onClick = { viewModel.updateSettings { it.copy(perAppMode = "exclude") } },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(stringResource(R.string.bypass_selected), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!bypassSelected) {
                FilledTonalButton(
                    onClick = { viewModel.updateSettings { it.copy(perAppMode = "include") } },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(stringResource(R.string.only_proxy_selected), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                OutlinedButton(
                    onClick = { viewModel.updateSettings { it.copy(perAppMode = "include") } },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(stringResource(R.string.only_proxy_selected), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            placeholder = { Text(stringResource(R.string.search_apps_924)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.clear_search))
                    }
                }
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = showSystemApps,
                onClick = { showSystemApps = !showSystemApps },
                label = { Text(stringResource(R.string.system_apps)) },
            )
            Spacer(Modifier.weight(1f))
            if (settings.perAppPackages.isNotEmpty()) {
                TextButton(
                    onClick = { viewModel.updateSettings { it.copy(perAppPackages = emptySet()) } },
                ) {
                    Text(stringResource(R.string.clear_selection))
                }
            }
            Text(
                text = "${filteredApps.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (filteredApps.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.no_matching_apps),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val isChecked = app.packageName in settings.perAppPackages
                    val onToggle = {
                        val updated = settings.perAppPackages.toMutableSet()
                        if (isChecked) updated.remove(app.packageName) else updated.add(app.packageName)
                        viewModel.updateSettings { it.copy(perAppPackages = updated) }
                    }
                    ListItem(
                        modifier = Modifier.clickable(onClick = onToggle),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            if (app.iconBitmap != null) {
                                Image(
                                    bitmap = app.iconBitmap,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                )
                            } else {
                                Surface(
                                    modifier = Modifier.size(42.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                ) {}
                            }
                        },
                        headlineContent = {
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            Switch(checked = isChecked, onCheckedChange = { onToggle() })
                        },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    )
                }
            }
        }
    }
}

private fun app.nebulabox.data.AppSettings.perAppModeLabel(): String =
    if (perAppMode == "include") "Only selected apps" else "Bypass selected apps"

private fun drawableToImageBitmap(drawable: Drawable): ImageBitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap.asImageBitmap()
    }
    val width = drawable.intrinsicWidth.coerceAtLeast(1).coerceAtMost(96)
    val height = drawable.intrinsicHeight.coerceAtLeast(1).coerceAtMost(96)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap.asImageBitmap()
}
