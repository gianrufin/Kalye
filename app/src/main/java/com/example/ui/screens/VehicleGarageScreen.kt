package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.SettingsInputComponent
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DistanceUnit
import com.example.data.model.GeoUtils
import com.example.data.model.VehicleBodyStyle
import com.example.data.model.VehicleProfile
import com.example.data.model.VehicleRigGear
import com.example.data.model.VehicleStats
import com.example.ui.KalyeViewModel
import com.example.ui.components.RigAvatarBadge
import com.example.ui.components.RigAvatarStudioSheet
import com.example.ui.components.VehicleRigIllustration
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VehicleGarageScreen(
    viewModel: KalyeViewModel,
    onBack: () -> Unit,
    onStartDriveWithRig: () -> Unit
) {
    val vehicles by viewModel.vehicles.collectAsStateWithLifecycle()
    val activeVehicle by viewModel.activeVehicle.collectAsStateWithLifecycle()
    val vehicleStats by viewModel.vehicleStats.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    val statsMap = remember(vehicleStats) { vehicleStats.associateBy { it.vehicle.id } }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingVehicle by remember { mutableStateOf<VehicleProfile?>(null) }
    var deletingVehicle by remember { mutableStateOf<VehicleProfile?>(null) }
    var showAvatarStudio by remember { mutableStateOf(false) }

    val avatarVehicle = remember(vehicles, activeVehicle, preferences.rigAvatar.vehicleId) {
        vehicles.firstOrNull { it.id == preferences.rigAvatar.vehicleId } ?: activeVehicle
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("garage_back_button")
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "PERSONALIZE RIG",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Vehicle Garage",
                    style = MaterialTheme.typography.headlineMedium
                )
            }
            RigAvatarBadge(
                vehicle = avatarVehicle,
                spec = preferences.rigAvatar,
                size = 48.dp,
                showEditPill = true,
                onClick = { showAvatarStudio = true },
                modifier = Modifier.testTag("garage_avatar_studio_badge")
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.testTag("add_vehicle_button")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add custom vehicle")
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Empty-garage or Active Rig Hero Showcase Card
            item {
                AnimatedContent(
                    targetState = activeVehicle,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "active_rig_hero"
                ) { hero ->
                    if (hero == null) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            tonalElevation = 1.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("empty_garage_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Your garage is empty",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Add your first car or rig to track vehicle odometer mileage, fuel efficiency, and per-rig trip totals.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = { showAddDialog = true },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("empty_garage_add_button")
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Add First Rig")
                                }
                            }
                        }
                    } else {
                        val heroStats = statsMap[hero.id]
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(144.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val ridgeColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val path = Path().apply {
                                            moveTo(0f, size.height * 0.78f)
                                            lineTo(size.width * 0.22f, size.height * 0.44f)
                                            lineTo(size.width * 0.40f, size.height * 0.62f)
                                            lineTo(size.width * 0.68f, size.height * 0.26f)
                                            lineTo(size.width, size.height * 0.68f)
                                        }
                                        drawPath(path, color = ridgeColor, style = Stroke(width = 3f))
                                    }
                                    VehicleRigIllustration(
                                        bodyStyle = hero.bodyStyle,
                                        accentColor = Color(hero.accentColorHex),
                                        rigGear = hero.rigGear,
                                        modifier = Modifier
                                            .fillMaxWidth(0.78f)
                                            .height(124.dp)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = hero.nickname,
                                            style = MaterialTheme.typography.headlineMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Text(
                                                text = "Active Rig",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { editingVehicle = hero },
                                        modifier = Modifier.testTag("hero_edit_vehicle_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Edit,
                                            contentDescription = "Edit active rig"
                                        )
                                    }
                                }

                                Text(
                                    text = hero.makeModel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                // Odometer, Trip count & Fuel Efficiency metrics strip
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    RigStatBadge(
                                        label = "ODOMETER",
                                        value = GeoUtils.formatOdometer(
                                            heroStats?.currentOdometerKm ?: hero.startingOdometerKm,
                                            preferences.distanceUnit
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    RigStatBadge(
                                        label = "TRIPS",
                                        value = "${heroStats?.tripCount ?: 0}",
                                        modifier = Modifier.weight(0.65f)
                                    )
                                    val avgEff = heroStats?.averageKmPerLiter
                                    val effText = if (avgEff != null) {
                                        if (preferences.distanceUnit == DistanceUnit.MI) {
                                            String.format(Locale.US, "%.1f mpg", avgEff * 2.3521458)
                                        } else {
                                            String.format(Locale.US, "%.1f km/L", avgEff)
                                        }
                                    } else "—"
                                    RigStatBadge(
                                        label = "AVG ECON",
                                        value = effText,
                                        modifier = Modifier.weight(0.95f)
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SpecBadgeChip(
                                        icon = Icons.Rounded.LocalGasStation,
                                        text = hero.fuelType
                                    )
                                    SpecBadgeChip(
                                        icon = Icons.Rounded.SettingsInputComponent,
                                        text = hero.drivetrain
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(hero.accentColorHex))
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = hero.bodyStyle.label,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    }
                                    if (hero.rigGear != VehicleRigGear.STOCK) {
                                        SpecBadgeChip(
                                            icon = Icons.Rounded.AutoAwesome,
                                            text = hero.rigGear.label
                                        )
                                    }
                                }

                                FilledTonalButton(
                                    onClick = { showAvatarStudio = true },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("garage_customize_avatar_button")
                                ) {
                                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Customize Rig Avatar Badge", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }

            if (vehicles.isNotEmpty()) {
                item {
                    Text(
                        text = "My Vehicles (${vehicles.size})",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                items(vehicles, key = { it.id }) { vehicle ->
                    val isSelected = vehicle.id == activeVehicle?.id
                    VehicleListCard(
                        vehicle = vehicle,
                        stats = statsMap[vehicle.id],
                        distanceUnit = preferences.distanceUnit,
                        selected = isSelected,
                        onSelect = { viewModel.selectVehicle(vehicle.id) },
                        onEdit = { editingVehicle = vehicle },
                        onDelete = { deletingVehicle = vehicle }
                    )
                }
            }
        }

        // Bottom Start Drive with Active Rig CTA
        if (activeVehicle != null) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Button(
                    onClick = onStartDriveWithRig,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .height(54.dp)
                        .testTag("garage_start_drive_button")
                ) {
                    Icon(Icons.Rounded.DirectionsCar, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Use ${activeVehicle?.nickname} on Track", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    if (showAddDialog) {
        AddEditVehicleDialog(
            existing = null,
            distanceUnit = preferences.distanceUnit,
            onDismiss = { showAddDialog = false },
            onSave = { nickname, makeModel, bodyStyle, drivetrain, fuelType, colorHex, odoKm, rigGear ->
                viewModel.addVehicle(nickname, makeModel, bodyStyle, drivetrain, fuelType, colorHex, odoKm, rigGear)
                showAddDialog = false
            }
        )
    }

    editingVehicle?.let { target ->
        AddEditVehicleDialog(
            existing = target,
            distanceUnit = preferences.distanceUnit,
            onDismiss = { editingVehicle = null },
            onSave = { nickname, makeModel, bodyStyle, drivetrain, fuelType, colorHex, odoKm, rigGear ->
                viewModel.updateVehicle(
                    vehicleId = target.id,
                    nickname = nickname,
                    makeModel = makeModel,
                    bodyStyle = bodyStyle,
                    drivetrain = drivetrain,
                    fuelType = fuelType,
                    accentColorHex = colorHex,
                    startingOdometerKm = odoKm,
                    rigGear = rigGear
                )
                editingVehicle = null
            }
        )
    }

    if (showAvatarStudio) {
        RigAvatarStudioSheet(
            vehicles = vehicles,
            activeVehicle = activeVehicle,
            currentSpec = preferences.rigAvatar,
            onDismiss = { showAvatarStudio = false },
            onSaveAvatar = { spec, chosenVeh, newGear ->
                if (chosenVeh != null && newGear != null) {
                    viewModel.updateVehicle(
                        vehicleId = chosenVeh.id,
                        nickname = chosenVeh.nickname,
                        makeModel = chosenVeh.makeModel,
                        bodyStyle = chosenVeh.bodyStyle,
                        drivetrain = chosenVeh.drivetrain,
                        fuelType = chosenVeh.fuelType,
                        accentColorHex = chosenVeh.accentColorHex,
                        startingOdometerKm = chosenVeh.startingOdometerKm,
                        rigGear = newGear
                    )
                }
                viewModel.updateRigAvatar(spec)
            },
            onAddRigFirst = { showAddDialog = true }
        )
    }

    deletingVehicle?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingVehicle = null },
            title = { Text("Remove ${target.nickname}?") },
            text = {
                Text("This removes ${target.nickname} from your Garage. Recorded trips remain safely in your Ride History.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteVehicle(target.id)
                        deletingVehicle = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_vehicle_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingVehicle = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun RigStatBadge(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SpecBadgeChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun VehicleListCard(
    vehicle: VehicleProfile,
    stats: VehicleStats?,
    distanceUnit: DistanceUnit,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "vehicle_border"
    )
    val bgColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.24f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "vehicle_bg"
    )

    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        border = BorderStroke(if (selected) 1.8.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vehicle_card_${vehicle.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 76.dp, height = 52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                VehicleRigIllustration(
                    bodyStyle = vehicle.bodyStyle,
                    accentColor = Color(vehicle.accentColorHex),
                    rigGear = vehicle.rigGear,
                    modifier = Modifier
                        .fillMaxWidth(0.90f)
                        .height(44.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = vehicle.nickname,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (selected) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = vehicle.makeModel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val odoStr = GeoUtils.formatOdometer(
                    stats?.currentOdometerKm ?: vehicle.startingOdometerKm,
                    distanceUnit
                )
                val tripsStr = "${stats?.tripCount ?: 0} trips"
                Text(
                    text = "${vehicle.fuelType} • $odoStr • $tripsStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onEdit,
                modifier = Modifier.testTag("edit_vehicle_${vehicle.id}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "Edit ${vehicle.nickname}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_vehicle_${vehicle.id}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Delete ${vehicle.nickname}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddEditVehicleDialog(
    existing: VehicleProfile?,
    distanceUnit: DistanceUnit,
    onDismiss: () -> Unit,
    onSave: (String, String, VehicleBodyStyle, String, String, Long, Double, VehicleRigGear) -> Unit
) {
    var nickname by remember(existing) { mutableStateOf(existing?.nickname ?: "") }
    var makeModel by remember(existing) { mutableStateOf(existing?.makeModel ?: "") }
    val initialOdoDisplay = remember(existing, distanceUnit) {
        val km = existing?.startingOdometerKm ?: 0.0
        if (km <= 0.0) ""
        else {
            val v = if (distanceUnit == DistanceUnit.MI) km * 0.621371 else km
            String.format(Locale.US, "%.0f", v)
        }
    }
    var odometerInput by remember(existing) { mutableStateOf(initialOdoDisplay) }
    var bodyStyle by remember(existing) { mutableStateOf(existing?.bodyStyle ?: VehicleBodyStyle.SUV_4X4) }
    var drivetrain by remember(existing) { mutableStateOf(existing?.drivetrain ?: "4x4") }
    var fuelType by remember(existing) { mutableStateOf(existing?.fuelType ?: "Diesel") }
    var colorHex by remember(existing) { mutableLongStateOf(existing?.accentColorHex ?: 0xFF4B5D38L) }
    var rigGear by remember(existing) { mutableStateOf(existing?.rigGear ?: VehicleRigGear.STOCK) }

    val colorSwatches = remember {
        listOf(
            0xFFF4F5F2L, // Pearl White
            0xFF4B5D38L, // Olive
            0xFFC8652BL, // Clay Terracotta
            0xFF2A5C48L, // Forest Pine
            0xFF5D6B78L, // Slate Gray
            0xFFB84A27L, // Ember Red
            0xFFD4A23CL, // Sand Dune
            0xFF252A2EL  // Basalt Charcoal
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add Custom Rig" else "Edit ${existing.nickname}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(92.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    VehicleRigIllustration(
                        bodyStyle = bodyStyle,
                        accentColor = Color(colorHex),
                        rigGear = rigGear,
                        modifier = Modifier
                            .fillMaxWidth(0.74f)
                            .height(74.dp)
                    )
                }

                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("Rig Nickname (e.g., Desert Jimny)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("vehicle_nickname_input")
                )

                OutlinedTextField(
                    value = makeModel,
                    onValueChange = { makeModel = it },
                    label = { Text("Make & Model (e.g., Suzuki Jimny)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("vehicle_model_input")
                )

                OutlinedTextField(
                    value = odometerInput,
                    onValueChange = { odometerInput = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = {
                        Text(
                            if (distanceUnit == DistanceUnit.MI) "Base Odometer (mi)"
                            else "Base Odometer (km)"
                        )
                    },
                    placeholder = { Text("0") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    leadingIcon = { Icon(Icons.Rounded.Speed, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().testTag("vehicle_odometer_input")
                )

                Text("BODY STYLE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    VehicleBodyStyle.entries.forEach { style ->
                        FilterChip(
                            selected = bodyStyle == style,
                            onClick = { bodyStyle = style },
                            label = { Text(style.label) },
                            modifier = Modifier.testTag("garage_style_${style.name.lowercase()}")
                        )
                    }
                }

                Text("RIG GEAR & TRIM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    VehicleRigGear.entries.forEach { gear ->
                        FilterChip(
                            selected = rigGear == gear,
                            onClick = { rigGear = gear },
                            label = { Text(gear.label) },
                            modifier = Modifier.testTag("garage_gear_${gear.name.lowercase()}")
                        )
                    }
                }

                Text("DRIVETRAIN & FUEL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("4x4", "AWD", "FWD", "2WD").forEach { dt ->
                        FilterChip(
                            selected = drivetrain == dt,
                            onClick = { drivetrain = dt },
                            label = { Text(dt) }
                        )
                    }
                    listOf("Diesel", "Gasoline", "Hybrid", "EV").forEach { fuel ->
                        FilterChip(
                            selected = fuelType == fuel,
                            onClick = { fuelType = fuel },
                            label = { Text(fuel) }
                        )
                    }
                }

                Text("PAINT COLOR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    colorSwatches.forEach { hex ->
                        val selected = colorHex == hex
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .border(
                                    width = if (selected) 2.5.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                    shape = CircleShape
                                )
                                .clickable { colorHex = hex }
                                .testTag("garage_color_${java.lang.Long.toHexString(hex)}")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rawOdo = odometerInput.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                    val odoKm = if (distanceUnit == DistanceUnit.MI) rawOdo * 1.609344 else rawOdo
                    onSave(nickname, makeModel, bodyStyle, drivetrain, fuelType, colorHex, odoKm, rigGear)
                },
                modifier = Modifier.testTag("save_vehicle_button")
            ) {
                Text(if (existing == null) "Save Rig" else "Update Rig")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
