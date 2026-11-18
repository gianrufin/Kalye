package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.DistanceUnit
import com.example.data.model.GeoUtils
import com.example.data.model.RoutePoint
import com.example.data.model.TripSummary
import com.example.export.SpreadsheetExporter
import com.example.ui.KalyeViewModel
import com.example.ui.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TripsScreen(
    viewModel: KalyeViewModel,
    bottomBarPadding: PaddingValues,
    onTripSelected: (String) -> Unit,
    onStartTrackRequested: () -> Unit
) {
    val context = LocalContext.current
    val tripsState by viewModel.tripsState.collectAsStateWithLifecycle()
    val allStops by viewModel.allStops.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    var selectedTagFilter by remember { mutableStateOf("All") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(bottomBarPadding)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "EXPEDITION LOG",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Ride History",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (tripsState is UiState.Success && (tripsState as UiState.Success).data.isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        val trips = (tripsState as UiState.Success).data
                        val csv = SpreadsheetExporter.buildAllTripsCsv(
                            trips = trips,
                            allStops = allStops,
                            unit = preferences.distanceUnit
                        )
                        SpreadsheetExporter.exportAndShareCsv(context, "kalye_all_trips", csv)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("export_all_csv_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("CSV")
                }
            }
        }

        // Category Tag Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedTagFilter == "All",
                onClick = { selectedTagFilter = "All" },
                label = { Text("All") }
            )
            categories.forEach { cat ->
                FilterChip(
                    selected = selectedTagFilter == cat.name,
                    onClick = { selectedTagFilter = cat.name },
                    label = { Text(cat.name) },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(cat.colorHex))
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        when (val state = tripsState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is UiState.Success -> {
                val filtered = remember(state.data, selectedTagFilter) {
                    if (selectedTagFilter == "All") state.data
                    else state.data.filter { it.categoryTag.equals(selectedTagFilter, ignoreCase = true) }
                }

                if (filtered.isEmpty()) {
                    EmptyTripsCard(
                        onStartTrackRequested = onStartTrackRequested,
                        modifier = Modifier.padding(20.dp)
                    )
                } else {
                    val totalMeters = filtered.sumOf { it.distanceMeters }
                    val totalClimb = filtered.sumOf { it.elevationGainMeters }
                    val totalSpend = filtered.sumOf { it.totalCost }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            SummaryTotalsCard(
                                rideCount = filtered.size,
                                totalDistance = GeoUtils.formatDistance(totalMeters, preferences.distanceUnit),
                                totalClimb = "+${GeoUtils.formatElevation(totalClimb, preferences.distanceUnit)}",
                                totalSpend = GeoUtils.formatCurrency(totalSpend, preferences.currencyCode)
                            )
                        }

                        items(filtered, key = { it.id }) { trip ->
                            val stopsCount = allStops.count { it.tripId == trip.id }
                            TripHistoryCard(
                                trip = trip,
                                stopsCount = stopsCount,
                                unit = preferences.distanceUnit,
                                onClick = { onTripSelected(trip.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryTotalsCard(
    rideCount: Int,
    totalDistance: String,
    totalClimb: String,
    totalSpend: String
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SummaryCol("RIDES", "$rideCount")
            SummaryCol("DISTANCE", totalDistance)
            SummaryCol("CLIMB", totalClimb)
            SummaryCol("TOTAL SPEND", totalSpend)
        }
    }
}

@Composable
private fun SummaryCol(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun TripHistoryCard(
    trip: TripSummary,
    stopsCount: Int,
    unit: DistanceUnit,
    onClick: () -> Unit
) {
    val dateStr = remember(trip.startTimeMillis) {
        SimpleDateFormat("EEE, MMM d • HH:mm", Locale.US).format(Date(trip.startTimeMillis))
    }
    val previewPoints = remember(trip.routePolyline) {
        GeoUtils.decodeRoutePoints(trip.routePolyline)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("trip_card_${trip.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = trip.categoryTag.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (trip.isSynced) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                        contentDescription = if (trip.isSynced) "Synced" else "Pending sync",
                        tint = if (trip.isSynced) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = trip.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = GeoUtils.formatDistance(trip.distanceMeters, unit),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Mini Route Silhouette Preview
                MiniRouteSilhouette(
                    points = previewPoints,
                    modifier = Modifier.size(width = 86.dp, height = 56.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "+${GeoUtils.formatElevation(trip.elevationGainMeters, unit)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = GeoUtils.formatDuration(trip.durationMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$stopsCount stops • ${GeoUtils.formatCurrency(trip.totalCost, trip.currencyCode)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun MiniRouteSilhouette(
    points: List<RoutePoint>,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .padding(8.dp)
    ) {
        if (points.size < 2) {
            val p = Path().apply {
                moveTo(0f, size.height * 0.75f)
                cubicTo(
                    size.width * 0.35f, size.height * 0.1f,
                    size.width * 0.65f, size.height * 0.9f,
                    size.width, size.height * 0.25f
                )
            }
            drawPath(
                path = p,
                color = lineColor.copy(alpha = 0.45f),
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )
            return@Canvas
        }
        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLon = points.minOf { it.longitude }
        val maxLon = points.maxOf { it.longitude }
        val latSpan = (maxLat - minLat).coerceAtLeast(0.0005)
        val lonSpan = (maxLon - minLon).coerceAtLeast(0.0005)
        val scale = minOf(size.width / lonSpan, size.height / latSpan)
        val offX = (size.width - lonSpan * scale).toFloat() / 2f
        val offY = (size.height - latSpan * scale).toFloat() / 2f

        val path = Path()
        points.forEachIndexed { idx, pt ->
            val x = offX + ((pt.longitude - minLon) * scale).toFloat()
            val y = offY + ((maxLat - pt.latitude) * scale).toFloat()
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
private fun EmptyTripsCard(
    onStartTrackRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Image(
                painter = painterResource(id = R.drawable.img_overland_hero),
                contentDescription = "Overland ridge landscape",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "No expeditions recorded yet",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Hit the Track tab to map your next overland trail, log fuel stops & ferry fees, and generate altitude profiles.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onStartTrackRequested,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Rounded.DirectionsCar, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Go to Track Map")
            }
        }
    }
}
