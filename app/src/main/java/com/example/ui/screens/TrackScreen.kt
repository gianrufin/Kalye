package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cabin
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DirectionsBoat
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Garage
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.GpsNotFixed
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PinDrop
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DistanceUnit
import com.example.data.model.GeoUtils
import com.example.data.model.MapLayerType
import com.example.data.model.StopType
import com.example.data.model.ThemeMode
import com.example.data.model.TripStop
import com.example.data.model.TripSummary
import com.example.location.GpsSignalStatus
import com.example.location.KalyeTrackingService
import com.example.location.LiveTelemetry
import com.example.maps.KalyeMapCanvas
import com.example.ui.KalyeViewModel
import com.example.ui.components.LogStopSheet
import com.example.ui.components.VehicleRigIllustration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackScreen(
    viewModel: KalyeViewModel,
    bottomBarPadding: PaddingValues,
    onRideFinished: (String) -> Unit,
    onOpenGarage: () -> Unit = {}
) {
    val context = LocalContext.current
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val activeVehicle by viewModel.activeVehicle.collectAsStateWithLifecycle()
    val vehicles by viewModel.vehicles.collectAsStateWithLifecycle()
    val activeTrip by viewModel.activeTrip.collectAsStateWithLifecycle()
    val activePoints by viewModel.activeTrackPoints.collectAsStateWithLifecycle()
    val activeStops by viewModel.activeTripStops.collectAsStateWithLifecycle()
    val telemetry by viewModel.liveTelemetry.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val permissionGranted by viewModel.locationPermissionGranted.collectAsStateWithLifecycle()

    var showVehiclePickerSheet by remember { mutableStateOf(false) }

    var followGps by remember { mutableStateOf(true) }
    var mapCenterLat by remember { mutableDoubleStateOf(16.4023) } // Cordillera default before GPS lock
    var mapCenterLon by remember { mutableDoubleStateOf(120.5960) }
    var mapZoom by remember { mutableDoubleStateOf(13.5) }

    var selectedCategory by remember(categories) {
        mutableStateOf(categories.firstOrNull()?.name ?: "Overland")
    }
    var stopSheetType by remember { mutableStateOf<StopType?>(null) }
    var showFinishDialog by remember { mutableStateOf(false) }
    var finishTitleText by remember { mutableStateOf("") }
    var finishNotesText by remember { mutableStateOf("") }
    var selectedPinStop by remember { mutableStateOf<TripStop?>(null) }

    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val fineGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onLocationPermissionResult(fineGranted || coarseGranted)
    }

    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            viewModel.onLocationPermissionResult(true)
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    LaunchedEffect(telemetry.point, followGps) {
        val pt = telemetry.point
        if (pt != null && followGps) {
            mapCenterLat = pt.latitude
            mapCenterLon = pt.longitude
        }
    }

    val isStealthActive = preferences.stealthModeEnabled && activeTrip != null
    BackHandler(enabled = isStealthActive) {
        viewModel.setStealthModeEnabled(false)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isStealthActive) {
            StealthTrackingHud(
                trip = activeTrip!!,
                telemetry = telemetry,
                distanceUnit = preferences.distanceUnit,
                activeRigName = activeVehicle?.nickname ?: activeTrip?.vehicleName.orEmpty(),
                bottomBarPadding = bottomBarPadding,
                onExitStealth = { viewModel.setStealthModeEnabled(false) },
                onTogglePause = { viewModel.togglePauseRide(activeTrip!!) },
                onQuickLog = { stopSheetType = it },
                onFinishRequested = {
                    val t = activeTrip!!
                    finishTitleText = t.title
                    finishNotesText = t.notes
                    showFinishDialog = true
                }
            )
        } else {
            // Minimal Muted Street Map Canvas (no traffic clutter, no conflicting navigation)
            KalyeMapCanvas(
                tileManager = viewModel.tileManager,
                routePoints = activePoints,
                stops = activeStops,
                currentLocation = telemetry.point,
                mapLayer = preferences.mapLayer,
                themeMode = preferences.themeMode,
                distanceUnit = preferences.distanceUnit,
                centerLat = mapCenterLat,
                centerLon = mapCenterLon,
                zoom = mapZoom,
                interactive = true,
                onCenterChanged = { lat, lon, z ->
                    followGps = false
                    mapCenterLat = lat
                    mapCenterLon = lon
                    mapZoom = z
                },
                onStopTapped = { stop -> selectedPinStop = stop },
                modifier = Modifier.fillMaxSize()
            )

            // Top Floating Telemetry & Quick Controls Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // GPS Accuracy + Altitude Pill
                Surface(
                    onClick = {
                        if (!permissionGranted) permissionLauncher.launch(requiredPermissions)
                    },
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val (gpsIcon, gpsColor, gpsLabel) = when (telemetry.status) {
                            GpsSignalStatus.LOCKED_HIGH -> Triple(
                                Icons.Rounded.GpsFixed,
                                Color(0xFF2D8A5B),
                                "±${telemetry.point?.accuracyMeters?.roundToInt() ?: 4}m"
                            )
                            GpsSignalStatus.LOCKED_FAIR -> Triple(
                                Icons.Rounded.GpsFixed,
                                MaterialTheme.colorScheme.tertiary,
                                "±${telemetry.point?.accuracyMeters?.roundToInt() ?: 18}m"
                            )
                            GpsSignalStatus.SEARCHING -> Triple(
                                Icons.Rounded.GpsNotFixed,
                                MaterialTheme.colorScheme.tertiary,
                                "Searching GPS"
                            )
                            GpsSignalStatus.PERMISSION_NEEDED -> Triple(
                                Icons.Rounded.GpsNotFixed,
                                MaterialTheme.colorScheme.error,
                                "Enable GPS"
                            )
                        }
                        Icon(
                            imageVector = gpsIcon,
                            contentDescription = "GPS Signal Status",
                            tint = gpsColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = gpsLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        telemetry.point?.let { pt ->
                            Spacer(modifier = Modifier.width(10.dp))
                            Icon(
                                imageVector = Icons.Rounded.Terrain,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = GeoUtils.formatElevation(pt.altitudeMeters, preferences.distanceUnit),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Right Micro-Actions: Offline indicator / Stealth HUD button + Light/Dark toggle
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (preferences.mapLayer == MapLayerType.OFFLINE) {
                        Surface(
                            onClick = { viewModel.toggleOfflineCacheMode() },
                            shape = RoundedCornerShape(100.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            tonalElevation = 2.dp,
                            modifier = Modifier.testTag("offline_mode_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudOff,
                                    contentDescription = "Offline tiles only",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Offline",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    if (activeTrip != null) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            tonalElevation = 2.dp
                        ) {
                            IconButton(
                                onClick = { viewModel.setStealthModeEnabled(true) },
                                modifier = Modifier.testTag("stealth_mode_toggle")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VisibilityOff,
                                    contentDescription = "Enable Stealth Battery-Saver View",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        tonalElevation = 2.dp
                    ) {
                        IconButton(
                            onClick = { viewModel.toggleDarkModeQuick() },
                            modifier = Modifier.testTag("quick_dark_mode_toggle")
                        ) {
                            AnimatedContent(
                                targetState = preferences.themeMode == ThemeMode.DARK,
                                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(150)) },
                                label = "theme_toggle_icon"
                            ) { isDark ->
                                Icon(
                                    imageVector = if (isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                    contentDescription = "Toggle Light or Dark Mode",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Thumb-Zone Bottom Cockpit (One-Handed Driving Friendly)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .padding(bottomBarPadding)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Map Zoom & Recenter FABs right above the bottom deck
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmallFloatingActionButton(
                        onClick = { mapZoom = (mapZoom - 1.0).coerceAtLeast(4.0) },
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        Text("-", style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    SmallFloatingActionButton(
                        onClick = { mapZoom = (mapZoom + 1.0).coerceAtMost(17.0) },
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        Text("+", style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = {
                            followGps = true
                            telemetry.point?.let {
                                mapCenterLat = it.latitude
                                mapCenterLon = it.longitude
                                mapZoom = 15.0
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = if (followGps) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MyLocation,
                            contentDescription = "Recenter on GPS"
                        )
                    }
                }

                AnimatedContent(
                    targetState = activeTrip,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(160))
                    },
                    label = "cockpit_state"
                ) { trip ->
                    if (trip == null) {
                        // IDLE: Active Rig Pill (or Empty-Garage CTA) + Category tag selector + Giant Start Ride button
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            tonalElevation = 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val currentRig = activeVehicle
                                Surface(
                                    onClick = {
                                        if (vehicles.isEmpty()) onOpenGarage()
                                        else showVehiclePickerSheet = true
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("track_vehicle_picker_pill")
                                ) {
                                    if (currentRig == null) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DirectionsCar,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "No active rig yet",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Tap to set up your vehicle in Garage",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.Rounded.Add,
                                                contentDescription = "Add rig",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            VehicleRigIllustration(
                                                bodyStyle = currentRig.bodyStyle,
                                                accentColor = Color(currentRig.accentColorHex),
                                                modifier = Modifier.size(width = 48.dp, height = 30.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = currentRig.nickname,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "${currentRig.drivetrain} • ${currentRig.fuelType}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer
                                            ) {
                                                Text(
                                                    text = "Active Rig",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Rounded.ExpandMore,
                                                contentDescription = "Select vehicle",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    categories.forEach { cat ->
                                        FilterChip(
                                            selected = selectedCategory == cat.name,
                                            onClick = { selectedCategory = cat.name },
                                            label = { Text(cat.name) }
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        if (!permissionGranted) {
                                            permissionLauncher.launch(requiredPermissions)
                                        }
                                        val defaultTitle = buildDefaultTripTitle(selectedCategory)
                                        viewModel.startRide(
                                            title = defaultTitle,
                                            categoryTag = selectedCategory
                                        ) { started ->
                                            KalyeTrackingService.start(context, started.id)
                                        }
                                    },
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(58.dp)
                                        .testTag("start_ride_button")
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Start $selectedCategory Ride",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    } else {
                        // ACTIVE RIDE COCKPIT: Oversized telemetry + auto-pause indicator + speed + stop logging + Pause/Finish
                        val rigLabel = (activeVehicle?.nickname ?: trip.vehicleName).ifBlank { "Location Track" }
                        val liveSpeedKmh = ((telemetry.point?.speedMps ?: 0f) * 3.6).coerceAtLeast(0.0)
                        Surface(
                            shape = RoundedCornerShape(26.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            tonalElevation = 4.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        val statusLabel = when {
                                            trip.isPaused -> "PAUSED • ${rigLabel.uppercase()}"
                                            trip.isAutoPaused -> "AUTO-PAUSED • STATIONARY"
                                            else -> "● RECORDING • ${rigLabel.uppercase()}"
                                        }
                                        val statusColor = when {
                                            trip.isPaused || trip.isAutoPaused -> MaterialTheme.colorScheme.tertiary
                                            else -> MaterialTheme.colorScheme.secondary
                                        }
                                        Text(
                                            text = statusLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = statusColor,
                                            modifier = Modifier.testTag("active_ride_status_label")
                                        )
                                        Text(
                                            text = GeoUtils.formatDistance(trip.distanceMeters, preferences.distanceUnit),
                                            style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = GeoUtils.formatMovingTime(trip.effectiveMovingMillis),
                                            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${GeoUtils.formatSpeed(liveSpeedKmh, preferences.distanceUnit)} • +${GeoUtils.formatElevation(trip.elevationGainMeters, preferences.distanceUnit)}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // One-Tap Quick Waypoint & Expense Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    QuickLogChip(
                                        label = "Fuel",
                                        icon = Icons.Rounded.LocalGasStation,
                                        onClick = { stopSheetType = StopType.FUEL },
                                        modifier = Modifier.weight(1f).testTag("quick_log_fuel")
                                    )
                                    QuickLogChip(
                                        label = "Ferry",
                                        icon = Icons.Rounded.DirectionsBoat,
                                        onClick = { stopSheetType = StopType.FERRY },
                                        modifier = Modifier.weight(1f).testTag("quick_log_ferry")
                                    )
                                    QuickLogChip(
                                        label = "Camp",
                                        icon = Icons.Rounded.Cabin,
                                        onClick = { stopSheetType = StopType.CAMP },
                                        modifier = Modifier.weight(1f).testTag("quick_log_camp")
                                    )
                                    QuickLogChip(
                                        label = "Stop",
                                        icon = Icons.Rounded.PinDrop,
                                        onClick = { stopSheetType = StopType.STOP_OVER },
                                        modifier = Modifier.weight(1f).testTag("quick_log_stop")
                                    )
                                }

                                // Thumb-friendly Pause / Finish Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = { viewModel.togglePauseRide(trip) },
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(54.dp)
                                            .testTag("pause_resume_button")
                                    ) {
                                        Icon(
                                            imageVector = if (trip.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                            contentDescription = null
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (trip.isPaused) "Resume" else "Pause")
                                    }

                                    Button(
                                        onClick = {
                                            finishTitleText = trip.title
                                            finishNotesText = trip.notes
                                            showFinishDialog = true
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(54.dp)
                                            .testTag("finish_ride_button")
                                    ) {
                                        Icon(Icons.Rounded.Stop, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Finish Ride")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Bottom Sheet to log Fuel / Ferry / Camp / Stop-over
    val currentStopType = stopSheetType
    val currentTrip = activeTrip
    if (currentStopType != null && currentTrip != null) {
        LogStopSheet(
            initialType = currentStopType,
            currencyCode = preferences.currencyCode,
            distanceUnit = preferences.distanceUnit,
            onDismiss = { stopSheetType = null },
            onSave = { type, title, note, cost, liters, price ->
                viewModel.logStopOrExpense(
                    tripId = currentTrip.id,
                    stopType = type,
                    title = title,
                    note = note,
                    costAmount = cost,
                    fuelLiters = liters,
                    pricePerLiter = price,
                    latitude = telemetry.point?.latitude ?: mapCenterLat,
                    longitude = telemetry.point?.longitude ?: mapCenterLon,
                    elevationMeters = telemetry.point?.altitudeMeters ?: 0.0
                )
                stopSheetType = null
            }
        )
    }

    // Finish Ride Confirmation Dialog
    if (showFinishDialog && currentTrip != null) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text("Save Expedition") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = finishTitleText,
                        onValueChange = { finishTitleText = it },
                        label = { Text("Ride Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = finishNotesText,
                        onValueChange = { finishNotesText = it },
                        label = { Text("Trail / Expedition Notes") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFinishDialog = false
                        KalyeTrackingService.stop(context)
                        viewModel.finishRide(currentTrip, finishTitleText, finishNotesText) { finishedId ->
                            onRideFinished(finishedId)
                        }
                    },
                    modifier = Modifier.testTag("confirm_finish_ride")
                ) {
                    Text("Save & View")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishDialog = false }) {
                    Text("Keep Riding")
                }
            }
        )
    }

    // Tapped Map Pin Details Dialog
    selectedPinStop?.let { stop ->
        AlertDialog(
            onDismissRequest = { selectedPinStop = null },
            title = { Text("${stop.stopType.label}: ${stop.title}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (stop.costAmount > 0) {
                        Text("Cost: ${GeoUtils.formatCurrency(stop.costAmount, stop.currencyCode)}")
                    }
                    if (stop.fuelLiters > 0) {
                        Text("Volume: ${String.format(Locale.US, "%.2f", stop.fuelLiters)} L")
                    }
                    Text("Elevation: ${GeoUtils.formatElevation(stop.elevationMeters, preferences.distanceUnit)}")
                    if (stop.note.isNotBlank()) {
                        Text(stop.note, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedPinStop = null }) {
                    Text("Close")
                }
            }
        )
    }

    if (showVehiclePickerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showVehiclePickerSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Select Active Rig", style = MaterialTheme.typography.headlineMedium)
                    TextButton(
                        onClick = {
                            showVehiclePickerSheet = false
                            onOpenGarage()
                        }
                    ) {
                        Icon(Icons.Rounded.Garage, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Garage")
                    }
                }

                vehicles.forEach { veh ->
                    val selected = veh.id == activeVehicle?.id
                    Surface(
                        onClick = {
                            viewModel.selectVehicle(veh.id)
                            showVehiclePickerSheet = false
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(
                            if (selected) 1.6.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            VehicleRigIllustration(
                                bodyStyle = veh.bodyStyle,
                                accentColor = Color(veh.accentColorHex),
                                modifier = Modifier.size(width = 58.dp, height = 38.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(veh.nickname, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${veh.makeModel} • ${veh.fuelType}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Active",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun StealthTrackingHud(
    trip: TripSummary,
    telemetry: LiveTelemetry,
    distanceUnit: DistanceUnit,
    activeRigName: String,
    bottomBarPadding: PaddingValues,
    onExitStealth: () -> Unit,
    onTogglePause: () -> Unit,
    onQuickLog: (StopType) -> Unit,
    onFinishRequested: () -> Unit
) {
    val liveSpeedKmh = ((telemetry.point?.speedMps ?: 0f) * 3.6).coerceAtLeast(0.0)
    val oledText = Color(0xFFE9ECE8)
    val oledMuted = Color(0xFF8C968D)
    val oledAccent = Color(0xFFE07A48)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF040505))
            .statusBarsPadding()
            .padding(bottomBarPadding)
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .testTag("stealth_hud_container"),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header: Status & Exit Stealth
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                val badge = when {
                    trip.isPaused -> "PAUSED"
                    trip.isAutoPaused -> "AUTO-PAUSED • STATIONARY"
                    else -> "● STEALTH TRACKING"
                }
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (trip.isPaused || trip.isAutoPaused) oledAccent else Color(0xFF5DC48A)
                )
                if (activeRigName.isNotBlank()) {
                    Text(
                        text = activeRigName,
                        style = MaterialTheme.typography.titleMedium,
                        color = oledMuted
                    )
                }
            }

            OutlinedButton(
                onClick = onExitStealth,
                shape = RoundedCornerShape(100.dp),
                border = BorderStroke(1.dp, Color(0xFF2B312C)),
                modifier = Modifier.testTag("exit_stealth_mode_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Visibility,
                    contentDescription = "Show map",
                    tint = oledText,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Show Map", color = oledText)
            }
        }

        // Oversized OLED High-Contrast Telemetry
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "DISTANCE",
                    style = MaterialTheme.typography.labelSmall,
                    color = oledMuted
                )
                Text(
                    text = GeoUtils.formatDistance(trip.distanceMeters, distanceUnit),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 54.sp,
                        fontFeatureSettings = "tnum"
                    ),
                    color = oledText
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("MOVING TIME", style = MaterialTheme.typography.labelSmall, color = oledMuted)
                    Text(
                        text = GeoUtils.formatMovingTime(trip.effectiveMovingMillis),
                        style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"),
                        color = oledText
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ELEV GAIN", style = MaterialTheme.typography.labelSmall, color = oledMuted)
                    Text(
                        text = "+${GeoUtils.formatElevation(trip.elevationGainMeters, distanceUnit)}",
                        style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"),
                        color = oledText
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("SPEED", style = MaterialTheme.typography.labelSmall, color = oledMuted)
                    Text(
                        text = GeoUtils.formatSpeed(liveSpeedKmh, distanceUnit),
                        style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                        color = oledAccent
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ALTITUDE", style = MaterialTheme.typography.labelSmall, color = oledMuted)
                    Text(
                        text = GeoUtils.formatElevation(telemetry.point?.altitudeMeters ?: 0.0, distanceUnit),
                        style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                        color = oledText
                    )
                }
            }
        }

        // Minimal Bottom Actions
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = { onQuickLog(StopType.FUEL) },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF131714),
                    border = BorderStroke(1.dp, Color(0xFF282E29)),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.LocalGasStation, contentDescription = "Fuel", tint = oledAccent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Fuel", color = oledText, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Surface(
                    onClick = { onQuickLog(StopType.STOP_OVER) },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF131714),
                    border = BorderStroke(1.dp, Color(0xFF282E29)),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.PinDrop, contentDescription = "Stop", tint = oledAccent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Stop", color = oledText, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onTogglePause,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF2B312C)),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                ) {
                    Icon(
                        imageVector = if (trip.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        contentDescription = null,
                        tint = oledText
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (trip.isPaused) "Resume" else "Pause", color = oledText)
                }

                Button(
                    onClick = onFinishRequested,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = oledAccent,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Finish Ride", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun QuickLogChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.height(48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun buildDefaultTripTitle(category: String): String {
    val timeLabel = SimpleDateFormat("MMM d • HH:mm", Locale.US).format(Date())
    return "$category ($timeLabel)"
}
