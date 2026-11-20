package com.example.ui.components

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RigAvatarBackdrop
import com.example.data.model.RigAvatarFrame
import com.example.data.model.RigAvatarSpec
import com.example.data.model.VehicleBodyStyle
import com.example.data.model.VehicleProfile
import com.example.data.model.VehicleRigGear
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RigAvatarBadge(
    vehicle: VehicleProfile?,
    spec: RigAvatarSpec,
    modifier: Modifier = Modifier,
    size: Dp = 58.dp,
    showEditPill: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val backdrop = spec.backdrop
    val topColor = Color(backdrop.topHex)
    val bottomColor = Color(backdrop.bottomHex)
    val ridgeColor = Color(backdrop.ridgeHex)
    val frame = spec.frame

    val interactiveMod = if (onClick != null) {
        Modifier
            .size(size.coerceAtLeast(48.dp))
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
            .size(size)
            .clip(CircleShape)
    }

    Box(
        modifier = modifier.then(interactiveMod),
        contentAlignment = Alignment.Center
    ) {
        // Layered scenic mountain backdrop + compass/trail ring
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val cx = w / 2f
            val cy = h / 2f
            val r = minOf(w, h) / 2f

            drawCircle(
                brush = Brush.verticalGradient(colors = listOf(topColor, bottomColor)),
                radius = r,
                center = Offset(cx, cy)
            )

            // Distant mountain ridge silhouette
            val ridge = Path().apply {
                moveTo(0f, h * 0.72f)
                lineTo(w * 0.24f, h * 0.46f)
                lineTo(w * 0.44f, h * 0.60f)
                lineTo(w * 0.70f, h * 0.34f)
                lineTo(w, h * 0.62f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(ridge, color = ridgeColor.copy(alpha = 0.24f))
            val ridgeStroke = Path().apply {
                moveTo(0f, h * 0.72f)
                lineTo(w * 0.24f, h * 0.46f)
                lineTo(w * 0.44f, h * 0.60f)
                lineTo(w * 0.70f, h * 0.34f)
                lineTo(w, h * 0.62f)
            }
            drawPath(
                ridgeStroke,
                color = ridgeColor.copy(alpha = 0.55f),
                style = Stroke(width = (r * 0.055f).coerceAtLeast(1.5f), cap = StrokeCap.Round)
            )

            when (frame) {
                RigAvatarFrame.COMPASS_RING -> {
                    drawCircle(
                        color = ridgeColor.copy(alpha = 0.85f),
                        radius = r - (r * 0.05f),
                        center = Offset(cx, cy),
                        style = Stroke(width = (r * 0.06f).coerceAtLeast(2f))
                    )
                    // 12 compass bezel tick marks
                    for (i in 0 until 12) {
                        val angle = (i * 30.0) * (Math.PI / 180.0)
                        val inner = if (i % 3 == 0) r * 0.82f else r * 0.88f
                        val outer = r * 0.94f
                        drawLine(
                            color = Color.White.copy(alpha = if (i % 3 == 0) 0.85f else 0.45f),
                            start = Offset(cx + (cos(angle) * inner).toFloat(), cy + (sin(angle) * inner).toFloat()),
                            end = Offset(cx + (cos(angle) * outer).toFloat(), cy + (sin(angle) * outer).toFloat()),
                            strokeWidth = if (i % 3 == 0) (r * 0.045f).coerceAtLeast(1.6f) else (r * 0.025f).coerceAtLeast(1f),
                            cap = StrokeCap.Round
                        )
                    }
                }
                RigAvatarFrame.TRAIL_BORDER -> {
                    drawCircle(
                        color = Color(0xFFE07A48),
                        radius = r - (r * 0.055f),
                        center = Offset(cx, cy),
                        style = Stroke(width = (r * 0.09f).coerceAtLeast(2.5f))
                    )
                }
                RigAvatarFrame.CLEAN_DISC -> {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.35f),
                        radius = r - (r * 0.03f),
                        center = Offset(cx, cy),
                        style = Stroke(width = (r * 0.04f).coerceAtLeast(1.4f))
                    )
                }
            }
        }

        if (vehicle != null) {
            VehicleRigIllustration(
                bodyStyle = vehicle.bodyStyle,
                accentColor = Color(vehicle.accentColorHex),
                rigGear = vehicle.rigGear,
                modifier = Modifier
                    .fillMaxSize(0.82f)
                    .offset(y = if (spec.callsign.isNotBlank() && size >= 54.dp) (-size * 0.05f) else 0.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.DirectionsCar,
                contentDescription = "Vehicle avatar",
                tint = Color.White,
                modifier = Modifier.size(size * 0.44f)
            )
        }

        if (spec.callsign.isNotBlank() && size >= 54.dp) {
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = Color(0xFF0E1310).copy(alpha = 0.84f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = (size * 0.07f).coerceAtLeast(3.dp))
            ) {
                Text(
                    text = spec.callsign.uppercase().take(10),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (size >= 90.dp) 11.sp else 8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.padding(horizontal = if (size >= 90.dp) 10.dp else 6.dp, vertical = 1.dp)
                )
            }
        }

        if (showEditPill) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(22.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Customize avatar",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RigAvatarStudioSheet(
    vehicles: List<VehicleProfile>,
    activeVehicle: VehicleProfile?,
    currentSpec: RigAvatarSpec,
    onDismiss: () -> Unit,
    onSaveAvatar: (RigAvatarSpec, VehicleProfile?, VehicleRigGear?) -> Unit,
    onAddRigFirst: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedVehicleId by remember(currentSpec, activeVehicle, vehicles) {
        mutableStateOf(
            currentSpec.vehicleId?.takeIf { id -> vehicles.any { it.id == id } }
                ?: activeVehicle?.id
                ?: vehicles.firstOrNull()?.id
        )
    }
    val chosenVehicle = vehicles.firstOrNull { it.id == selectedVehicleId } ?: activeVehicle

    var selectedBackdrop by remember(currentSpec) { mutableStateOf(currentSpec.backdrop) }
    var selectedFrame by remember(currentSpec) { mutableStateOf(currentSpec.frame) }
    var callsign by remember(currentSpec) { mutableStateOf(currentSpec.callsign) }
    var selectedGear by remember(chosenVehicle?.id, chosenVehicle?.rigGear) {
        mutableStateOf(chosenVehicle?.rigGear ?: VehicleRigGear.STOCK)
    }

    val previewVehicle = chosenVehicle?.copy(rigGear = selectedGear)
    val previewSpec = RigAvatarSpec(
        vehicleId = chosenVehicle?.id,
        backdrop = selectedBackdrop,
        frame = selectedFrame,
        callsign = callsign.trim().take(10)
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = "RIG AVATAR STUDIO",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Customize Your Rig Badge",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Live Avatar Badge Preview
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RigAvatarBadge(
                        vehicle = previewVehicle,
                        spec = previewSpec,
                        size = 112.dp,
                        modifier = Modifier.testTag("avatar_studio_preview")
                    )
                    Text(
                        text = chosenVehicle?.let { "${it.nickname} • ${selectedBackdrop.label}" }
                            ?: "Add a vehicle rig to feature on your badge",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Featured Rig Picker
            Text(
                text = "FEATURED RIG",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            if (vehicles.isEmpty()) {
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onAddRigFirst()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Rounded.DirectionsCar, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Set Up a Vehicle in Garage First")
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    vehicles.forEach { veh ->
                        val isChosen = veh.id == chosenVehicle?.id
                        Surface(
                            onClick = {
                                selectedVehicleId = veh.id
                                selectedGear = veh.rigGear
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isChosen) {
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            border = BorderStroke(
                                width = if (isChosen) 1.8.dp else 1.dp,
                                color = if (isChosen) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.testTag("avatar_pick_rig_${veh.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                VehicleRigIllustration(
                                    bodyStyle = veh.bodyStyle,
                                    accentColor = Color(veh.accentColorHex),
                                    rigGear = veh.rigGear,
                                    modifier = Modifier.size(width = 58.dp, height = 30.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = veh.nickname,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                if (isChosen) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Rig Trim / Gear Overlay (applies directly to selected rig!)
            if (chosenVehicle != null) {
                Text(
                    text = "RIG GEAR & TRIM",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    VehicleRigGear.entries.forEach { gear ->
                        FilterChip(
                            selected = selectedGear == gear,
                            onClick = { selectedGear = gear },
                            label = { Text(gear.label) },
                            modifier = Modifier.testTag("avatar_gear_${gear.name.lowercase()}")
                        )
                    }
                }
            }

            // Scenic Backdrop Picker
            Text(
                text = "BACKDROP SCENE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RigAvatarBackdrop.entries.forEach { bg ->
                    val isSelected = selectedBackdrop == bg
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedBackdrop = bg }
                            .padding(4.dp)
                            .testTag("avatar_backdrop_${bg.name.lowercase()}")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(bg.topHex), Color(bg.bottomHex))
                                    )
                                )
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape
                                )
                        )
                        Text(
                            text = bg.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Dial Frame Picker
            Text(
                text = "BADGE FRAME",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                RigAvatarFrame.entries.forEach { fr ->
                    FilterChip(
                        selected = selectedFrame == fr,
                        onClick = { selectedFrame = fr },
                        label = { Text(fr.label) },
                        modifier = Modifier.testTag("avatar_frame_${fr.name.lowercase()}")
                    )
                }
            }

            OutlinedTextField(
                value = callsign,
                onValueChange = { callsign = it.take(10) },
                label = { Text("Badge Callsign (Optional, e.g., OVERLAND)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("avatar_callsign_input")
            )

            Button(
                onClick = {
                    val gearChanged = if (chosenVehicle != null && selectedGear != chosenVehicle.rigGear) {
                        selectedGear
                    } else null
                    onSaveAvatar(previewSpec, chosenVehicle, gearChanged)
                    onDismiss()
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("save_rig_avatar_button")
            ) {
                Text("Save Rig Avatar", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
