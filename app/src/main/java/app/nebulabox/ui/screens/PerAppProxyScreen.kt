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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
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
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val packages = runCatching {
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
            }.getOrDefault(emptyList())

            val loaded = packages
                .asSequence()
                .filter { it.packageName != context.packageName }
                .map { appInfo ->
                    val label = runCatching { pm.getApplicationLabel(appInfo).toString() }
                        .getOrDefault(appInfo.packageName)
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                        (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                    val bmp = runCatching {
                        drawableToImageBitmap(pm.getApplicationIcon(appInfo))
                    }.getOrNull()
                    InstalledAppEntry(
                        packageName = appInfo.packageName,
                        label = label,
                        isSystem = isSystem,
                        iconBitmap = bmp,
                    )
                }
                .sortedBy { it.label.lowercase() }
                .toList()

            withContext(Dispatchers.Main) {
                allApps = loaded
                isLoading = false
            }
        }
    }

    val filteredApps = remember(allApps, searchQuery, showSystemApps, settings.perAppPackages) {
        allApps.filter { app ->
            val matchesSystem = showSystemApps || !app.isSystem || app.packageName in settings.perAppPackages
            val matchesQuery = searchQuery.isBlank() ||
                app.label.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
            matchesSystem && matchesQuery
        }.sortedWith(
            compareByDescending<InstalledAppEntry> { it.packageName in settings.perAppPackages }
                .thenBy { it.label.lowercase() },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable {
                    viewModel.updateSettings { it.copy(perAppEnabled = !settings.perAppEnabled) }
                },
            shape = RoundedCornerShape(16.dp),
            color = if (settings.perAppEnabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Use Per-App Proxy",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (settings.perAppEnabled) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (settings.perAppEnabled) {
                            "${settings.perAppPackages.size} apps selected"
                        } else {
                            "Disabled · All apps use tunnel"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (settings.perAppEnabled) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = settings.perAppMode == "exclude",
                onClick = { viewModel.updateSettings { it.copy(perAppMode = "exclude") } },
                label = { Text("Bypass Selected") },
            )
            FilterChip(
                selected = settings.perAppMode == "include",
                onClick = { viewModel.updateSettings { it.copy(perAppMode = "include") } },
                label = { Text("Only Proxy Selected") },
            )
            Spacer(Modifier.weight(1f))
            if (settings.perAppPackages.isNotEmpty()) {
                TextButton(
                    onClick = { viewModel.updateSettings { it.copy(perAppPackages = emptySet()) } },
                ) {
                    Text("Clear")
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                placeholder = { Text("Search apps...") },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear")
                        }
                    }
                },
            )
            FilterChip(
                selected = showSystemApps,
                onClick = { showSystemApps = !showSystemApps },
                label = { Text("System") },
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 4.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val isChecked = app.packageName in settings.perAppPackages
                    ListItem(
                        modifier = Modifier.clickable {
                            val updated = settings.perAppPackages.toMutableSet()
                            if (isChecked) updated.remove(app.packageName) else updated.add(app.packageName)
                            viewModel.updateSettings {
                                it.copy(
                                    perAppEnabled = if (updated.isNotEmpty() && !it.perAppEnabled) true else it.perAppEnabled,
                                    perAppPackages = updated,
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            if (app.iconBitmap != null) {
                                Image(
                                    bitmap = app.iconBitmap,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                )
                            } else {
                                Surface(
                                    modifier = Modifier.size(40.dp),
                                    shape = RoundedCornerShape(10.dp),
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
                            Switch(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    val updated = settings.perAppPackages.toMutableSet()
                                    if (checked) updated.add(app.packageName) else updated.remove(app.packageName)
                                    viewModel.updateSettings {
                                        it.copy(
                                            perAppEnabled = if (updated.isNotEmpty() && !it.perAppEnabled) true else it.perAppEnabled,
                                            perAppPackages = updated,
                                        )
                                    }
                                },
                            )
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
