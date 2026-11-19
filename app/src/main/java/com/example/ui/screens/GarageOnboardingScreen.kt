package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import com.example.data.model.VehicleBodyStyle
import com.example.ui.KalyeViewModel
import com.example.ui.components.VehicleRigIllustration

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GarageOnboardingScreen(
    viewModel: KalyeViewModel,
    onDone: () -> Unit
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val distanceUnit = preferences.distanceUnit

    BackHandler {
        viewModel.skipOnboarding()
        onDone()
    }

    var nickname by remember { mutableStateOf("") }
    var makeModel by remember { mutableStateOf("") }
    var odometerInput by remember { mutableStateOf("") }
    var bodyStyle by remember { mutableStateOf(VehicleBodyStyle.SUV_4X4) }
    var drivetrain by remember { mutableStateOf("4x4") }
    var fuelType by remember { mutableStateOf("Diesel") }
    var colorHex by remember { mutableLongStateOf(0xFF4B5D38L) }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
        ) {
            // Top bar with Skip option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "WELCOME TO KALYE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Set Up Your First Rig",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                TextButton(
                    onClick = {
                        viewModel.skipOnboarding()
                        onDone()
                    },
                    modifier = Modifier.testTag("onboarding_skip_button")
                ) {
                    Text("Skip for now")
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Personalize your vehicle so every ride tracks odometer mileage, fuel efficiency, and rig-specific trip history.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Live Rig Preview Card
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(132.dp)
                            .padding(14.dp)
                            .clip(RoundedCornerShape(16.dp))
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
                            bodyStyle = bodyStyle,
                            accentColor = Color(colorHex),
                            modifier = Modifier
                                .fillMaxWidth(0.75f)
                                .height(104.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("Rig Nickname (e.g., Trail Ranger)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_nickname_input")
                )

                OutlinedTextField(
                    value = makeModel,
                    onValueChange = { makeModel = it },
                    label = { Text("Make & Model (e.g., Ford Ranger Wildtrak)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_model_input")
                )

                OutlinedTextField(
                    value = odometerInput,
                    onValueChange = { odometerInput = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = {
                        Text(
                            if (distanceUnit == DistanceUnit.MI) "Starting Odometer (mi)"
                            else "Starting Odometer (km)"
                        )
                    },
                    placeholder = { Text("0") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_odometer_input")
                )

                Text(
                    text = "BODY STYLE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VehicleBodyStyle.entries.forEach { style ->
                        FilterChip(
                            selected = bodyStyle == style,
                            onClick = { bodyStyle = style },
                            label = { Text(style.label) },
                            modifier = Modifier.testTag("onboarding_style_${style.name.lowercase()}")
                        )
                    }
                }

                Text(
                    text = "DRIVETRAIN & FUEL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
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

                Text(
                    text = "PAINT COLOR",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    colorSwatches.forEach { hex ->
                        val selected = colorHex == hex
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .border(
                                    width = if (selected) 2.5.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                    shape = CircleShape
                                )
                                .clickable { colorHex = hex }
                                .testTag("onboarding_color_${java.lang.Long.toHexString(hex)}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Button(
                    onClick = {
                        val rawOdo = odometerInput.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                        val odoKm = if (distanceUnit == DistanceUnit.MI) rawOdo * 1.609344 else rawOdo
                        viewModel.completeOnboardingVehicle(
                            nickname = nickname,
                            makeModel = makeModel,
                            bodyStyle = bodyStyle,
                            drivetrain = drivetrain,
                            fuelType = fuelType,
                            accentColorHex = colorHex,
                            startingOdometerKm = odoKm
                        )
                        onDone()
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .height(56.dp)
                        .testTag("onboarding_save_rig_button")
                ) {
                    Icon(Icons.Rounded.DirectionsCar, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Rig & Start Tracking", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
