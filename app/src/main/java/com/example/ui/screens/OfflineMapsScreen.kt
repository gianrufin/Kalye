package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.OfflineRegion
import com.example.maps.KalyeMapCanvas
import com.example.maps.MapViewportBounds
import com.example.ui.KalyeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OfflineMapsScreen(
    viewModel: KalyeViewModel,
    bottomBarPadding: PaddingValues
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val regions by viewModel.offlineRegions.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val diskCacheBytes by viewModel.diskCacheBytes.collectAsStateWithLifecycle()
    val telemetry by viewModel.liveTelemetry.collectAsStateWithLifecycle()

    var centerLat by remember { mutableDoubleStateOf(telemetry.point?.latitude ?: 16.4023) }
    var centerLon by remember { mutableDoubleStateOf(telemetry.point?.longitude ?: 120.5960) }
    var zoom by remember { mutableDoubleStateOf(12.0) }
    var currentBounds by remember {
        mutableStateOf(
            MapViewportBounds(
                minLat = centerLat - 0.12,
                maxLat = centerLat + 0.12,
                minLon = centerLon - 0.12,
                maxLon = centerLon + 0.12,
                centerLat = centerLat,
                centerLon = centerLon,
                zoom = 12
            )
        )
    }

    var regionName by remember { mutableStateOf("") }
    var detailLevelIdx by remember { mutableStateOf(0) } // 0 = Regional (Z10-13), 1 = Trail Detail (Z11-15)
    val minZoom = if (detailLevelIdx == 0) 10 else 11
    val maxZoom = if (detailLevelIdx == 0) 13 else 15

    val estimatedTiles = remember(currentBounds, minZoom, maxZoom) {
        viewModel.tileManager.estimateTileList(
            currentBounds.minLat,
            currentBounds.maxLat,
            currentBounds.minLon,
            currentBounds.maxLon,
            minZoom,
            maxZoom
        ).size
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(bottomBarPadding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "WILDERNESS CACHE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Offline Maps",
                        style = MaterialTheme.typography.headlineLarge
                    )
                }
                val mb = diskCacheBytes / (1024.0 * 1024.0)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = String.format(Locale.US, "%.1f MB cached", mb),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Interactive Map Selector
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(22.dp))
            ) {
                KalyeMapCanvas(
                    tileManager = viewModel.tileManager,
                    routePoints = emptyList(),
                    stops = emptyList(),
                    currentLocation = telemetry.point,
                    mapLayer = preferences.mapLayer,
                    themeMode = preferences.themeMode,
                    distanceUnit = preferences.distanceUnit,
                    centerLat = centerLat,
                    centerLon = centerLon,
                    zoom = zoom,
                    interactive = true,
                    onCenterChanged = { lat, lon, z ->
                        centerLat = lat
                        centerLon = lon
                        zoom = z
                    },
                    onViewportBoundsChanged = { bounds -> currentBounds = bounds }
                )

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(10.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                ) {
                    Text(
                        text = "Pan & zoom map to select offline area",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Download Controls Card
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = regionName,
                        onValueChange = { regionName = it },
                        label = { Text("Region Name (e.g., Sagada Ridge)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("offline_region_name_input")
                    )

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val labels = listOf("Regional (Z10–13)", "Trail Detail (Z11–15)")
                        labels.forEachIndexed { idx, label ->
                            SegmentedButton(
                                selected = detailLevelIdx == idx,
                                onClick = { detailLevelIdx = idx },
                                shape = SegmentedButtonDefaults.itemShape(idx, labels.size),
                                label = { Text(label) }
                            )
                        }
                    }

                    Text(
                        text = "Estimated download: $estimatedTiles topo/street tiles (~${String.format(Locale.US, "%.1f", estimatedTiles * 0.025)} MB)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val prog = downloadProgress
                    if (prog != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LinearProgressIndicator(
                                progress = {
                                    if (prog.total > 0) prog.downloaded.toFloat() / prog.total else 0f
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "Caching tile ${prog.downloaded} of ${prog.total}...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.downloadOfflineRegion(
                                name = regionName,
                                bounds = currentBounds,
                                minZoom = minZoom,
                                maxZoom = maxZoom,
                                layer = preferences.mapLayer
                            )
                            regionName = ""
                        },
                        enabled = downloadProgress == null,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("download_offline_region_button")
                    ) {
                        Icon(Icons.Rounded.CloudDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cache Map Area Offline")
                    }
                }
            }
        }

        // Cached Regions List
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Saved Sectors (${regions.size})",
                    style = MaterialTheme.typography.titleLarge
                )
                if (diskCacheBytes > 0) {
                    TextButton(onClick = { viewModel.clearAllTileCache() }) {
                        Text("Clear Cache")
                    }
                }
            }
        }

        if (regions.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Map,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Pan to a trailhead above and cache tiles before losing cell signal.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(regions, key = { it.id }) { region ->
                OfflineRegionCard(
                    region = region,
                    onCenterMap = {
                        centerLat = (region.minLat + region.maxLat) / 2.0
                        centerLon = (region.minLon + region.maxLon) / 2.0
                        zoom = region.minZoom.toDouble()
                    },
                    onDelete = { viewModel.deleteOfflineRegion(region.id) }
                )
            }
        }
    }
}

@Composable
private fun OfflineRegionCard(
    region: OfflineRegion,
    onCenterMap: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = remember(region.downloadedAtMillis) {
        SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(region.downloadedAtMillis))
    }
    Surface(
        onClick = onCenterMap,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.OfflinePin,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(region.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${region.tileCount} tiles • Z${region.minZoom}–${region.maxZoom} • $dateStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete region")
            }
        }
    }
}
