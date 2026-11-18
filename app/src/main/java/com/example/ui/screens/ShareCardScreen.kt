package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DistanceUnit
import com.example.data.model.GeoUtils
import com.example.data.model.ShareCardTheme
import com.example.export.ShareCardConfig
import com.example.export.ShareCardRenderer
import com.example.ui.KalyeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShareCardScreen(
    tripId: String,
    viewModel: KalyeViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val trip by viewModel.observeTripById(tripId).collectAsStateWithLifecycle(initialValue = null)
    val dbPoints by viewModel.observePointsForTrip(tripId).collectAsStateWithLifecycle(initialValue = emptyList())
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    val effectivePoints = remember(dbPoints, trip) {
        if (dbPoints.isNotEmpty()) dbPoints
        else trip?.routePolyline?.let { GeoUtils.decodeRoutePoints(it) } ?: emptyList()
    }

    var config by remember(preferences.distanceUnit) {
        mutableStateOf(
            ShareCardConfig(
                theme = ShareCardTheme.TOPO_DARK,
                distanceUnit = preferences.distanceUnit,
                showRouteSilhouette = true,
                showElevationGain = true,
                showDuration = false,
                showTitleAndTag = true,
                showDate = true,
                showCost = false
            )
        )
    }

    val currentTrip = trip
    if (currentTrip == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val previewBitmap by produceState<Bitmap?>(initialValue = null, currentTrip, effectivePoints, config) {
        value = withContext(Dispatchers.Default) {
            ShareCardRenderer.renderToBitmap(
                context = context,
                trip = currentTrip,
                points = effectivePoints,
                config = config,
                width = 720,
                height = 900
            )
        }
    }

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
                    text = "SOCIAL SHARE CARD",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Customize PNG Overlay",
                    style = MaterialTheme.typography.titleLarge
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
            // Live Strava-style PNG Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.76f)
                    .align(Alignment.CenterHorizontally)
                    .aspectRatio(4f / 5f)
                    .clip(RoundedCornerShape(22.dp))
            ) {
                if (config.theme == ShareCardTheme.TRANSPARENT_GLASS) {
                    CheckerboardBackground(modifier = Modifier.fillMaxSize())
                }
                previewBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Share card PNG preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Card Visual Theme Selector
            Text("CARD THEME", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShareCardTheme.entries.forEach { theme ->
                    FilterChip(
                        selected = config.theme == theme,
                        onClick = { config = config.copy(theme = theme) },
                        label = { Text(theme.label) }
                    )
                }
            }

            // Unit Switcher right on the card studio (KM vs MI)
            Text("MILEAGE UNIT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                DistanceUnit.entries.forEachIndexed { index, unit ->
                    SegmentedButton(
                        selected = config.distanceUnit == unit,
                        onClick = { config = config.copy(distanceUnit = unit) },
                        shape = SegmentedButtonDefaults.itemShape(index, DistanceUnit.entries.size),
                        label = { Text("${unit.label} (${unit.shortLabel})") }
                    )
                }
            }

            // Overlay Data Toggles
            Text("DATA OVERLAYS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = config.showRouteSilhouette,
                    onClick = { config = config.copy(showRouteSilhouette = !config.showRouteSilhouette) },
                    label = { Text("Route Map") }
                )
                FilterChip(
                    selected = config.showElevationGain,
                    onClick = { config = config.copy(showElevationGain = !config.showElevationGain) },
                    label = { Text("Elevation") }
                )
                FilterChip(
                    selected = config.showDuration,
                    onClick = { config = config.copy(showDuration = !config.showDuration) },
                    label = { Text("Duration") }
                )
                FilterChip(
                    selected = config.showTitleAndTag,
                    onClick = { config = config.copy(showTitleAndTag = !config.showTitleAndTag) },
                    label = { Text("Title & Tag") }
                )
                FilterChip(
                    selected = config.showDate,
                    onClick = { config = config.copy(showDate = !config.showDate) },
                    label = { Text("Date") }
                )
                FilterChip(
                    selected = config.showCost,
                    onClick = { config = config.copy(showCost = !config.showCost) },
                    label = { Text("Trip Cost") }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Bottom Export PNG CTA
        Button(
            onClick = {
                ShareCardRenderer.exportAndSharePng(
                    context = context,
                    trip = currentTrip,
                    points = effectivePoints,
                    config = config
                )
            },
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .height(56.dp)
                .testTag("export_png_card_button")
        ) {
            Icon(Icons.Rounded.Share, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Export & Share PNG (1080×1350)", style = MaterialTheme.typography.titleMedium)
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
                    color = if (dark) Color(0xFF2B2F2B) else Color(0xFF3E443E),
                    topLeft = Offset(c * cell, r * cell),
                    size = Size(cell, cell)
                )
            }
        }
    }
}
