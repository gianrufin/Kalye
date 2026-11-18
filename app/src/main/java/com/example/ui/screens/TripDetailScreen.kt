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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.GeoUtils
import com.example.data.model.StopType
import com.example.data.model.TripStop
import com.example.export.GpxExporter
import com.example.export.SpreadsheetExporter
import com.example.maps.KalyeMapCanvas
import com.example.ui.KalyeViewModel
import com.example.ui.components.ElevationProfileChart
import com.example.ui.components.LogStopSheet
import com.example.ui.components.iconForStopType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TripDetailScreen(
    tripId: String,
    viewModel: KalyeViewModel,
    onBack: () -> Unit,
    onOpenShareCard: (String) -> Unit
) {
    val context = LocalContext.current
    val trip by viewModel.observeTripById(tripId).collectAsStateWithLifecycle(initialValue = null)
    val dbPoints by viewModel.observePointsForTrip(tripId).collectAsStateWithLifecycle(initialValue = emptyList())
    val stops by viewModel.observeStopsForTrip(tripId).collectAsStateWithLifecycle(initialValue = emptyList())
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    val effectivePoints = remember(dbPoints, trip) {
        if (dbPoints.isNotEmpty()) dbPoints
        else trip?.routePolyline?.let { GeoUtils.decodeRoutePoints(it) } ?: emptyList()
    }

    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAddStopSheet by remember { mutableStateOf(false) }
    var editTitle by remember { mutableStateOf("") }
    var editNotes by remember { mutableStateOf("") }

    val currentTrip = trip
    if (currentTrip == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val centerLat = remember(effectivePoints, stops) {
        effectivePoints.map { it.latitude }.average().takeIf { !it.isNaN() }
            ?: stops.firstOrNull()?.latitude ?: 16.4023
    }
    val centerLon = remember(effectivePoints, stops) {
        effectivePoints.map { it.longitude }.average().takeIf { !it.isNaN() }
            ?: stops.firstOrNull()?.longitude ?: 120.5960
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_button")) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentTrip.categoryTag.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = currentTrip.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            IconButton(
                onClick = {
                    editTitle = currentTrip.title
                    editNotes = currentTrip.notes
                    showEditDialog = true
                }
            ) {
                Icon(Icons.Rounded.Edit, contentDescription = "Edit trip")
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete trip")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Map Preview
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(22.dp))
                ) {
                    KalyeMapCanvas(
                        tileManager = viewModel.tileManager,
                        routePoints = effectivePoints,
                        stops = stops,
                        currentLocation = null,
                        mapLayer = preferences.mapLayer,
                        themeMode = preferences.themeMode,
                        distanceUnit = preferences.distanceUnit,
                        centerLat = centerLat,
                        centerLon = centerLon,
                        zoom = 12.5,
                        interactive = true
                    )
                }
            }

            // Primary Telemetry Grid
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricTile(
                        label = "DISTANCE",
                        value = GeoUtils.formatDistance(currentTrip.distanceMeters, preferences.distanceUnit),
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        label = "ELEV GAIN",
                        value = "+${GeoUtils.formatElevation(currentTrip.elevationGainMeters, preferences.distanceUnit)}",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricTile(
                        label = "DURATION",
                        value = GeoUtils.formatDuration(currentTrip.durationMillis),
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        label = "PEAK / MAX SPD",
                        value = "${GeoUtils.formatElevation(currentTrip.maxElevationMeters, preferences.distanceUnit)} • ${GeoUtils.formatSpeed(currentTrip.maxSpeedKmh, preferences.distanceUnit)}",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Export Actions Row: Strava-Style PNG Card, GPX Track, CSV Spreadsheet
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onOpenShareCard(currentTrip.id) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(50.dp)
                            .testTag("open_share_card_button")
                    ) {
                        Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share Card")
                    }
                    FilledTonalButton(
                        onClick = {
                            GpxExporter.exportAndShare(context, currentTrip, effectivePoints, stops)
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("export_gpx_button")
                    ) {
                        Icon(Icons.Rounded.Route, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("GPX")
                    }
                    OutlinedButton(
                        onClick = {
                            val csv = SpreadsheetExporter.buildTripLedgerCsv(
                                currentTrip, stops, preferences.distanceUnit
                            )
                            SpreadsheetExporter.exportAndShareCsv(context, currentTrip.title, csv)
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("export_csv_button")
                    ) {
                        Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CSV")
                    }
                }
            }

            // Interactive Altitude Profile Chart
            item {
                ElevationProfileChart(
                    points = effectivePoints,
                    distanceUnit = preferences.distanceUnit
                )
            }

            // Journey Cost Breakdown Card
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "EXPEDITION COST LEDGER",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        CostRow(
                            label = "Fuel (${String.format(Locale.US, "%.1f", currentTrip.totalFuelLiters)} L)",
                            value = GeoUtils.formatCurrency(currentTrip.totalFuelCost, currentTrip.currencyCode)
                        )
                        CostRow(
                            label = "Ferry & Shipping Fees",
                            value = GeoUtils.formatCurrency(currentTrip.totalFerryCost, currentTrip.currencyCode)
                        )
                        CostRow(
                            label = "Camps, Tolls & Stop-overs",
                            value = GeoUtils.formatCurrency(currentTrip.totalOtherCost, currentTrip.currencyCode)
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total Trip Cost", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = GeoUtils.formatCurrency(currentTrip.totalCost, currentTrip.currencyCode),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (currentTrip.totalFuelLiters > 0 && currentTrip.distanceMeters > 0) {
                            val distKm = currentTrip.distanceMeters / 1000.0
                            val eff = distKm / currentTrip.totalFuelLiters
                            Text(
                                text = "Fuel efficiency: ${String.format(Locale.US, "%.1f", eff)} km/L",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        if (currentTrip.notes.isNotBlank()) {
                            Text(
                                text = currentTrip.notes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Waypoints & Expenses Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Stops, Fuel & Ferries (${stops.size})",
                        style = MaterialTheme.typography.titleLarge
                    )
                    FilledTonalButton(
                        onClick = { showAddStopSheet = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("detail_add_stop_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Log Entry")
                    }
                }
            }

            if (stops.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No fuel stops, ferry tickets, or camp pins logged on this trip yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(stops, key = { it.id }) { stop ->
                    StopRowCard(
                        stop = stop,
                        onDelete = { viewModel.deleteStop(stop) }
                    )
                }
            }
        }
    }

    if (showAddStopSheet) {
        LogStopSheet(
            initialType = StopType.FUEL,
            currencyCode = preferences.currencyCode,
            distanceUnit = preferences.distanceUnit,
            onDismiss = { showAddStopSheet = false },
            onSave = { type, title, note, cost, liters, price ->
                viewModel.logStopOrExpense(
                    tripId = currentTrip.id,
                    stopType = type,
                    title = title,
                    note = note,
                    costAmount = cost,
                    fuelLiters = liters,
                    pricePerLiter = price,
                    latitude = effectivePoints.lastOrNull()?.latitude ?: centerLat,
                    longitude = effectivePoints.lastOrNull()?.longitude ?: centerLon,
                    elevationMeters = effectivePoints.lastOrNull()?.altitudeMeters ?: 0.0
                )
                showAddStopSheet = false
            }
        )
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Trip Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editNotes,
                        onValueChange = { editNotes = it },
                        label = { Text("Notes") },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateTripDetails(currentTrip.id, editTitle, currentTrip.categoryTag, editNotes)
                    showEditDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Expedition?") },
            text = { Text("This removes the recorded route, elevation profile, and itemized expenses from your account.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteTrip(currentTrip.id, onDeleted = onBack)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun CostRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun StopRowCard(
    stop: TripStop,
    onDelete: () -> Unit
) {
    val timeStr = remember(stop.timestampMillis) {
        SimpleDateFormat("MMM d • HH:mm", Locale.US).format(Date(stop.timestampMillis))
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
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
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = iconForStopType(stop.stopType),
                        contentDescription = stop.stopType.label,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stop.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = buildString {
                        append("${stop.stopType.label} • $timeStr")
                        if (stop.fuelLiters > 0) append(" • ${String.format(Locale.US, "%.1f", stop.fuelLiters)}L")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (stop.note.isNotBlank()) {
                    Text(stop.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (stop.costAmount > 0) {
                Text(
                    text = GeoUtils.formatCurrency(stop.costAmount, stop.currencyCode),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Delete entry",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
