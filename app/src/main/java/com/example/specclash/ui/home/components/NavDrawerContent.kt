package com.example.specclash.ui.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.specclash.BuildConfig
import com.example.specclash.R
import com.example.specclash.data.local.ComparisonHistoryEntity
import com.example.specclash.data.local.SavedMatchupEntity
import com.example.specclash.domain.SpecComparator.ScoringPreset
import com.example.specclash.ui.home.HomeViewModel

/**
 * Scrollable content of the [ModalNavigationDrawer].
 *
 *  * Header: app logo + title + build version.
 *  * Pinned matchups (saved_matchups).
 *  * Recent comparisons (last 10).
 *  * Scoring engine preferences.
 *  * Cache & data management.
 *  * About & disclaimers.
 */
@Composable
fun NavDrawerContent(
    viewModel: HomeViewModel,
    onSelectMatchup: (slugA: String, slugB: String) -> Unit,
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pinned by viewModel.savedMatchups.collectAsStateWithLifecycle()
    val history by viewModel.recentHistory.collectAsStateWithLifecycle()
    val preset by viewModel.selectedPreset.collectAsStateWithLifecycle()
    val excludeLegacy by viewModel.excludeLegacyDevices.collectAsStateWithLifecycle()
    val cachedCount by viewModel.cachedPhoneCount.collectAsStateWithLifecycle()
    var showAboutDialog by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }

    ModalDrawerSheet(
        modifier = modifier,
        drawerContainerColor = MaterialTheme.colorScheme.surface,
    ) {
        DrawerHeader(buildVersion = BuildConfig.VERSION_NAME)
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Pinned matchups
            item {
                SectionHeader(
                    title = "Pinned Matchups",
                    icon = Icons.Filled.Bookmark,
                )
            }
            if (pinned.isEmpty()) {
                item { EmptyHint("No pinned matchups yet. Pin any active comparison using the bookmark icon.") }
            } else {
                items(
                    items = pinned,
                    key = { "pinned_${it.slugA}_${it.slugB}_${it.id}" },
                ) { matchup ->
                    PinnedRow(
                        matchup = matchup,
                        onClick = {
                            onSelectMatchup(matchup.slugA, matchup.slugB)
                            onCloseDrawer()
                        },
                        onDelete = { viewModel.deletePinnedMatchup(matchup.slugA, matchup.slugB) },
                    )
                }
            }

            item { Spacer(Modifier.height(4.dp)); HorizontalDivider() }

            // Recent history
            item {
                SectionHeader(
                    title = "Recent Comparisons",
                    icon = Icons.Filled.History,
                )
            }
            if (history.isEmpty()) {
                item { EmptyHint("Your last 10 comparisons will appear here.") }
            } else {
                items(
                    items = history,
                    key = { "history_${it.slugA}_${it.slugB}_${it.id}" },
                ) { entry ->
                    HistoryRow(
                        entry = entry,
                        onClick = {
                            onSelectMatchup(entry.slugA, entry.slugB)
                            onCloseDrawer()
                        },
                    )
                }
            }

            item { Spacer(Modifier.height(4.dp)); HorizontalDivider() }

            // Scoring engine preferences
            item {
                SectionHeader(
                    title = "Scoring Engine",
                    icon = Icons.Filled.Tune,
                )
            }
            item {
                PresetSelector(
                    selected = preset,
                    onSelect = { viewModel.setPreset(it) },
                )
            }
            item {
                LegacySwitch(
                    enabled = excludeLegacy,
                    onToggle = { viewModel.setExcludeLegacyDevices(it) },
                )
            }
            item {
                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
            }

            // Cache management
            item {
                SectionHeader(
                    title = "Cache & Data",
                    icon = Icons.Filled.Storage,
                )
            }
            item {
                CacheRow(
                    count = cachedCount,
                    onClear = { showClearCacheConfirm = true },
                )
            }
            item {
                ClearHistoryRow(
                    onClick = { viewModel.clearHistory() },
                )
            }

            item { Spacer(Modifier.height(4.dp)); HorizontalDivider() }

            // About
            item {
                SectionHeader(
                    title = "About & Disclaimers",
                    icon = Icons.Filled.Info,
                )
            }
            item {
                AboutRow(
                    onClick = { showAboutDialog = true },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }
    if (showClearCacheConfirm) {
        ClearCacheConfirmDialog(
            onConfirm = {
                showClearCacheConfirm = false
                viewModel.clearOfflineCache()
            },
            onDismiss = { showClearCacheConfirm = false },
        )
    }
}

// --- Header ----------------------------------------------------------------

@Composable
private fun DrawerHeader(buildVersion: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = "SpecClash",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Hardware Comparison & Value Engine",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "v$buildVersion",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// --- Section header --------------------------------------------------------

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

// --- Pinned row -----------------------------------------------------------

@Composable
private fun PinnedRow(
    matchup: SavedMatchupEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        DualAvatar(imageA = matchup.imageA, imageB = matchup.imageB)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = matchup.nameA,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = "vs ${matchup.nameB}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Filled.DeleteOutline,
                contentDescription = "Remove pin",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// --- History row -----------------------------------------------------------

@Composable
private fun HistoryRow(
    entry: ComparisonHistoryEntity,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Smartphone,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${entry.nameA} vs ${entry.nameB}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = relativeTime(entry.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// --- Dual avatar (two phones side by side) --------------------------------

@Composable
private fun DualAvatar(imageA: String, imageB: String) {
    Box(
        modifier = Modifier
            .size(width = 36.dp, height = 24.dp),
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(imageA).crossfade(true).build(),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(22.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
        )
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(imageB).crossfade(true).build(),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(22.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
        )
    }
}

// --- Preset selector ------------------------------------------------------

@Composable
private fun PresetSelector(
    selected: ScoringPreset,
    onSelect: (ScoringPreset) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 4.dp)) {
        ScoringPreset.entries.forEach { preset ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(preset) }
                    .padding(vertical = 4.dp, horizontal = 4.dp),
            ) {
                RadioButton(
                    selected = selected == preset,
                    onClick = { onSelect(preset) },
                )
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = preset.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = preset.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LegacySwitch(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggle(!enabled) }
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Depreciate Obsolete Devices",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Applies a 25% value penalty to phones with 7nm+ silicon or released before 2021.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

// --- Cache & data ----------------------------------------------------------

@Composable
private fun CacheRow(count: Int, onClear: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Text(
            text = "Cached Specs in DB: $count phones",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onClear) {
            Text("Clear Offline Cache", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ClearHistoryRow(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Text(
            text = "Clear Recent Comparisons",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Filled.DeleteOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// --- About row -------------------------------------------------------------

@Composable
private fun AboutRow(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "About SpecClash",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("About SpecClash") },
        text = {
            Column {
                Text(
                    text = "SpecClash is a side-by-side hardware comparison and value-for-money engine " +
                        "for flagship, mid-range, and entry-level smartphones.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                Text("Data sources", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "\u2022 Hardware specifications: GSMArena public listings.\n" +
                        "\u2022 Lab benchmarks: GSMArena \"Our Tests\" (Geekbench 6, AnTuTu v10, peak brightness, active-use endurance).\n" +
                        "\u2022 Prices: GSMArena regional listings (approximate).",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(10.dp))
                Text("Disclaimers", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "\u2022 Prices are approximate baseline estimates and vary by region, carrier, and retailer.\n" +
                        "\u2022 Lab benchmarks are pulled from the GSMArena review when available; chipset-tier heuristics fill the gap for un-reviewed devices.\n" +
                        "\u2022 This project is unaffiliated with GSMArena. Always verify final pricing and specs at the manufacturer.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
    )
}

@Composable
private fun ClearCacheConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear offline cache?") },
        text = {
            Text(
                text = "This removes every cached phone spec from the local database. " +
                    "Pinned matchups and recent history are preserved. Active comparisons will need to be re-fetched.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Clear", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// --- Helpers ---------------------------------------------------------------

private fun relativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = (now - timestamp).coerceAtLeast(0)
    val minutes = diff / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> {
            val date = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
            date.format(java.util.Date(timestamp))
        }
    }
}
