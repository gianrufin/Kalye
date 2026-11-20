package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.DistanceUnit
import com.example.data.model.GeoUtils
import com.example.data.model.ShareCardInkStyle
import com.example.data.model.ShareCardLayout
import com.example.export.ShareCardConfig
import com.example.export.ShareCardRenderer
import com.example.ui.KalyeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShareCardScreen(
    tripId: String,
    viewModel: KalyeViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val trip by viewModel.observeTripById(tripId).collectAsStateWithLifecycle(initialValue = null)
    val dbPoints by viewModel.observePointsForTrip(tripId).collectAsStateWithLifecycle(initialValue = emptyList())
    val stops by viewModel.observeStopsForTrip(tripId).collectAsStateWithLifecycle(initialValue = emptyList())
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    val effectivePoints = remember(dbPoints, trip) {
        if (dbPoints.isNotEmpty()) dbPoints
        else trip?.routePolyline?.let { GeoUtils.decodeRoutePoints(it) } ?: emptyList()
    }

    var config by remember(preferences.distanceUnit) {
        mutableStateOf(
            ShareCardConfig(
                layout = ShareCardLayout.CLASSIC_STACK,
                inkStyle = ShareCardInkStyle.WHITE_TRANSPARENT,
                distanceUnit = preferences.distanceUnit,
                showTrailLine = true,
                showStopsOnMap = true
            )
        )
    }

    var previewOverPhoto by remember { mutableStateOf(true) }
    var isExporting by remember { mutableStateOf(false) }
    var lastStickerLayout by remember { mutableStateOf(ShareCardLayout.CLASSIC_STACK) }

    val isRoadmap = config.layout == ShareCardLayout.ROADMAP_CARD

    val currentTrip = trip
    if (currentTrip == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val previewBitmap by produceState<Bitmap?>(
        initialValue = null,
        currentTrip,
        effectivePoints,
        stops,
        config
    ) {
        value = ShareCardRenderer.renderToBitmapAsync(
            context = context,
            trip = currentTrip,
            points = effectivePoints,
            stops = stops,
            config = config,
            tileManager = viewModel.tileManager,
            width = 720,
            height = 900
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("share_card_back_button")) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SHARE CARD STUDIO",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (isRoadmap) "Travel Roadmap Card" else "Transparent PNG Sticker",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                if (!isRoadmap && config.inkStyle.isTransparent) {
                    FilterChip(
                        selected = !previewOverPhoto,
                        onClick = { previewOverPhoto = !previewOverPhoto },
                        leadingIcon = {
                            Icon(
                                imageVector = if (previewOverPhoto) Icons.Rounded.Landscape else Icons.Rounded.GridOn,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(if (previewOverPhoto) "Photo" else "Alpha") },
                        modifier = Modifier.testTag("toggle_preview_backdrop")
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Live Strava-style PNG / Travel Roadmap Preview
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth(0.76f)
                        .align(Alignment.CenterHorizontally)
                        .aspectRatio(4f / 5f)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (!isRoadmap && config.inkStyle.isTransparent) {
                            AnimatedContent(
                                targetState = previewOverPhoto,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "backdrop_transition"
                            ) { overPhoto ->
                                if (overPhoto) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        Image(
                                            painter = painterResource(id = R.drawable.img_overland_hero),
                                            contentDescription = "Sample photo background",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(
                                                            Color.Black.copy(alpha = 0.25f),
                                                            Color.Black.copy(alpha = 0.55f)
                                                        )
                                                    )
                                                )
                                        )
                                    }
                                } else {
                                    CheckerboardBackground(modifier = Modifier.fillMaxSize())
                                }
                            }
                        }

                        AnimatedContent(
                            targetState = previewBitmap,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "card_preview_transition",
                            modifier = Modifier.fillMaxSize()
                        ) { bmp ->
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Share card PNG preview",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                }
                            }
                        }
                    }
                }

                // Prominent Mode Switcher: Transparent Sticker vs Full-Route Travel Roadmap
                Text(
                    text = "CARD TYPE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !isRoadmap,
                        onClick = {
                            if (isRoadmap) {
                                config = config.copy(layout = lastStickerLayout)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        icon = {
                            SegmentedButtonDefaults.Icon(active = !isRoadmap) {
                                Icon(Icons.Rounded.Route, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        },
                        label = { Text("Sticker Overlay", maxLines = 1) },
                        modifier = Modifier.testTag("mode_sticker_overlay")
                    )
                    SegmentedButton(
                        selected = isRoadmap,
                        onClick = {
                            if (!isRoadmap) {
                                lastStickerLayout = config.layout
                                config = config.copy(layout = ShareCardLayout.ROADMAP_CARD)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        icon = {
                            SegmentedButtonDefaults.Icon(active = isRoadmap) {
                                Icon(Icons.Rounded.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        },
                        label = { Text("Travel Roadmap", maxLines = 1) },
                        modifier = Modifier.testTag("layout_roadmap_card")
                    )
                }

                if (!isRoadmap) {
                    // Sticker Layout Selector (wrapped in FlowRow so every layout is visible at a glance)
                    Text(
                        text = "STICKER LAYOUT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ShareCardLayout.entries
                            .filter { it != ShareCardLayout.ROADMAP_CARD }
                            .forEach { layout ->
                                FilterChip(
                                    selected = config.layout == layout,
                                    onClick = {
                                        lastStickerLayout = layout
                                        config = config.copy(layout = layout)
                                    },
                                    leadingIcon = if (config.layout == layout) {
                                        { Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    label = { Text(layout.label) },
                                    modifier = Modifier.testTag("layout_${layout.name.lowercase()}")
                                )
                            }
                    }
                }

                // Sticker Ink / Roadmap Map Theme Style
                Text(
                    text = if (isRoadmap) "MAP THEME" else "STICKER INK",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ShareCardInkStyle.entries.forEach { ink ->
                        val chipLabel = if (isRoadmap) {
                            when (ink) {
                                ShareCardInkStyle.WHITE_TRANSPARENT -> "Light Atlas"
                                ShareCardInkStyle.DARK_TRANSPARENT -> "Slate Mono"
                                ShareCardInkStyle.DARK_CARD -> "Night Map"
                            }
                        } else {
                            ink.label
                        }
                        FilterChip(
                            selected = config.inkStyle == ink,
                            onClick = { config = config.copy(inkStyle = ink) },
                            label = { Text(chipLabel) },
                            modifier = Modifier.testTag("ink_${ink.name.lowercase()}")
                        )
                    }
                }

                // Mileage Unit + Trail Line / Stop Pins Toggle
                Text(
                    text = if (isRoadmap) "UNITS & MAP PINS" else "UNITS & TRAIL LINE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                        DistanceUnit.entries.forEachIndexed { index, unit ->
                            SegmentedButton(
                                selected = config.distanceUnit == unit,
                                onClick = { config = config.copy(distanceUnit = unit) },
                                shape = SegmentedButtonDefaults.itemShape(index, DistanceUnit.entries.size),
                                label = { Text(unit.shortLabel.uppercase()) }
                            )
                        }
                    }

                    if (isRoadmap) {
                        FilterChip(
                            selected = config.showStopsOnMap,
                            onClick = { config = config.copy(showStopsOnMap = !config.showStopsOnMap) },
                            leadingIcon = {
                                Icon(Icons.Rounded.Place, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = { Text("Stop Pins") },
                            modifier = Modifier.testTag("toggle_stop_pins")
                        )
                    } else {
                        FilterChip(
                            selected = config.showTrailLine,
                            onClick = { config = config.copy(showTrailLine = !config.showTrailLine) },
                            leadingIcon = {
                                Icon(Icons.Rounded.Route, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = { Text("Trail Line") },
                            modifier = Modifier.testTag("toggle_trail_line")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
            }

            // Bottom Save & Share PNG Actions
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        enabled = !isExporting,
                        onClick = {
                            scope.launch {
                                isExporting = true
                                try {
                                    val res = ShareCardRenderer.savePngToGalleryAsync(
                                        context = context,
                                        trip = currentTrip,
                                        points = effectivePoints,
                                        stops = stops,
                                        config = config,
                                        tileManager = viewModel.tileManager
                                    )
                                    res.onSuccess { filename ->
                                        snackbarHostState.showSnackbar("Saved $filename to Pictures/Kalye")
                                    }.onFailure { err ->
                                        snackbarHostState.showSnackbar(err.message ?: "Unable to save PNG")
                                    }
                                } finally {
                                    isExporting = false
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .testTag("save_png_card_button")
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save PNG")
                    }

                    Button(
                        enabled = !isExporting,
                        onClick = {
                            scope.launch {
                                isExporting = true
                                try {
                                    val result = runCatching {
                                        ShareCardRenderer.exportAndSharePngAsync(
                                            context = context,
                                            trip = currentTrip,
                                            points = effectivePoints,
                                            stops = stops,
                                            config = config,
                                            tileManager = viewModel.tileManager
                                        )
                                    }
                                    result.onFailure { err ->
                                        snackbarHostState.showSnackbar(err.message ?: "Unable to share PNG")
                                    }
                                } finally {
                                    isExporting = false
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(54.dp)
                            .testTag("export_png_card_button")
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isExporting) "Rendering…" else "Share PNG", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckerboardBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cell = 24f
        val cols = (size.width / cell).toInt() + 1
        val rows = (size.height / cell).toInt() + 1
        for (r in 0..rows) {
            for (c in 0..cols) {
                val dark = (r + c) % 2 == 0
                drawRect(
                    color = if (dark) Color(0xFF282D29) else Color(0xFF3C433E),
                    topLeft = Offset(c * cell, r * cell),
                    size = Size(cell, cell)
                )
            }
        }
    }
}
