package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Cabin
import androidx.compose.material.icons.rounded.DirectionsBoat
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.PinDrop
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.CurrencyCatalog
import com.example.data.model.DistanceUnit
import com.example.data.model.StopType
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogStopSheet(
    initialType: StopType,
    currencyCode: String,
    distanceUnit: DistanceUnit,
    onDismiss: () -> Unit,
    onSave: (
        type: StopType,
        title: String,
        note: String,
        costAmount: Double,
        fuelLiters: Double,
        pricePerLiter: Double
    ) -> Unit
) {
    var selectedType by remember { mutableStateOf(initialType) }
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var litersText by remember { mutableStateOf("") }
    var pricePerUnitText by remember { mutableStateOf("") }
    var totalCostText by remember { mutableStateOf("") }

    val currencySymbol = CurrencyCatalog.symbolFor(currencyCode)
    val volumeUnitLabel = if (distanceUnit == DistanceUnit.KM) "Liters" else "Gallons"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Log Waypoint & Cost",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StopType.entries.forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = { Text(type.label) },
                        leadingIcon = {
                            Icon(
                                imageVector = iconForStopType(type),
                                contentDescription = null
                            )
                        }
                    )
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = {
                    Text(
                        when (selectedType) {
                            StopType.FUEL -> "Station / Town (e.g., Shell Atok)"
                            StopType.FERRY -> "Ferry Route / Port (e.g., Batangas–Calapan)"
                            StopType.CAMP -> "Camp Site / Coordinates Name"
                            StopType.STOP_OVER -> "Stop-over / Viewpoint Name"
                            StopType.TOLL -> "Tollgate / Eco-Fee Name"
                            StopType.REPAIR -> "Shop / Gear Item"
                        }
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("stop_title_input")
            )

            if (selectedType == StopType.FUEL) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = litersText,
                        onValueChange = { raw ->
                            litersText = raw
                            val l = raw.toDoubleOrNull()
                            val p = pricePerUnitText.toDoubleOrNull()
                            if (l != null && p != null) {
                                totalCostText = String.format(Locale.US, "%.2f", l * p)
                            }
                        },
                        label = { Text(volumeUnitLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("fuel_liters_input")
                    )
                    OutlinedTextField(
                        value = pricePerUnitText,
                        onValueChange = { raw ->
                            pricePerUnitText = raw
                            val l = litersText.toDoubleOrNull()
                            val p = raw.toDoubleOrNull()
                            if (l != null && p != null) {
                                totalCostText = String.format(Locale.US, "%.2f", l * p)
                            }
                        },
                        label = { Text("Price/${if (distanceUnit == DistanceUnit.KM) "L" else "gal"} ($currencySymbol)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("fuel_price_input")
                    )
                }
            }

            OutlinedTextField(
                value = totalCostText,
                onValueChange = { totalCostText = it },
                label = {
                    Text(
                        when (selectedType) {
                            StopType.FERRY -> "Ferry Shipping Fee ($currencySymbol)"
                            StopType.FUEL -> "Total Fuel Cost ($currencySymbol)"
                            else -> "Cost / Fee ($currencySymbol, optional)"
                        }
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("stop_cost_input")
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Notes (road conditions, ticket #, camp quality)") },
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = {
                    val liters = litersText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                    val price = pricePerUnitText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                    val total = totalCostText.toDoubleOrNull()?.coerceAtLeast(0.0)
                        ?: if (liters > 0 && price > 0) liters * price else 0.0
                    onSave(
                        selectedType,
                        title.trim().ifBlank { selectedType.label },
                        note.trim(),
                        total,
                        liters,
                        price
                    )
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("save_stop_button")
            ) {
                Text("Save ${selectedType.label}", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

fun iconForStopType(type: StopType): ImageVector = when (type) {
    StopType.FUEL -> Icons.Rounded.LocalGasStation
    StopType.FERRY -> Icons.Rounded.DirectionsBoat
    StopType.CAMP -> Icons.Rounded.Cabin
    StopType.STOP_OVER -> Icons.Rounded.PinDrop
    StopType.TOLL -> Icons.Rounded.ReceiptLong
    StopType.REPAIR -> Icons.Rounded.Build
}
