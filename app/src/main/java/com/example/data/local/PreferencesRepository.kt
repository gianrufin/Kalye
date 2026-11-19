package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.DistanceUnit
import com.example.data.model.MapLayerType
import com.example.data.model.ThemeMode
import com.example.data.model.UserPreferences
import com.example.data.model.VehicleGarageDefaults
import com.example.data.model.VehicleProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.kalyeDataStore: DataStore<Preferences> by preferencesDataStore(name = "kalye_prefs")

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val DISTANCE_UNIT = stringPreferencesKey("distance_unit")
        val CURRENCY_CODE = stringPreferencesKey("currency_code")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val MAP_LAYER = stringPreferencesKey("map_layer")
        val ACTIVE_TRIP_ID = stringPreferencesKey("active_trip_id")
        val ACTIVE_VEHICLE_ID = stringPreferencesKey("active_vehicle_id")
        val VEHICLES_JSON = stringPreferencesKey("vehicles_json")
    }

    private val json = Json { ignoreUnknownKeys = true }

    val preferencesFlow: Flow<UserPreferences> = context.kalyeDataStore.data.map { prefs ->
        UserPreferences(
            distanceUnit = DistanceUnit.safeFrom(prefs[Keys.DISTANCE_UNIT]),
            currencyCode = prefs[Keys.CURRENCY_CODE] ?: "PHP",
            themeMode = ThemeMode.safeFrom(prefs[Keys.THEME_MODE]),
            mapLayer = MapLayerType.safeFrom(prefs[Keys.MAP_LAYER]),
            activeTripId = prefs[Keys.ACTIVE_TRIP_ID],
            activeVehicleId = prefs[Keys.ACTIVE_VEHICLE_ID] ?: VehicleGarageDefaults.presets.first().id
        )
    }

    val vehiclesFlow: Flow<List<VehicleProfile>> = context.kalyeDataStore.data.map { prefs ->
        val raw = prefs[Keys.VEHICLES_JSON]
        val parsed = if (!raw.isNullOrBlank()) {
            runCatching { json.decodeFromString<List<VehicleProfile>>(raw) }.getOrNull()
        } else null
        if (parsed.isNullOrEmpty()) VehicleGarageDefaults.presets else parsed
    }

    suspend fun setDistanceUnit(unit: DistanceUnit) {
        context.kalyeDataStore.edit { it[Keys.DISTANCE_UNIT] = unit.name }
    }

    suspend fun setCurrencyCode(code: String) {
        context.kalyeDataStore.edit { it[Keys.CURRENCY_CODE] = code }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.kalyeDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setMapLayer(layer: MapLayerType) {
        context.kalyeDataStore.edit { it[Keys.MAP_LAYER] = layer.name }
    }

    suspend fun setActiveTripId(tripId: String?) {
        context.kalyeDataStore.edit { prefs ->
            if (tripId == null) prefs.remove(Keys.ACTIVE_TRIP_ID)
            else prefs[Keys.ACTIVE_TRIP_ID] = tripId
        }
    }

    suspend fun setActiveVehicleId(vehicleId: String) {
        context.kalyeDataStore.edit { it[Keys.ACTIVE_VEHICLE_ID] = vehicleId }
    }

    suspend fun addVehicle(vehicle: VehicleProfile) {
        context.kalyeDataStore.edit { prefs ->
            val current = decodeVehicles(prefs[Keys.VEHICLES_JSON])
            val updated = current + vehicle
            prefs[Keys.VEHICLES_JSON] = json.encodeToString(updated)
            prefs[Keys.ACTIVE_VEHICLE_ID] = vehicle.id
        }
    }

    suspend fun deleteVehicle(vehicleId: String) {
        context.kalyeDataStore.edit { prefs ->
            val current = decodeVehicles(prefs[Keys.VEHICLES_JSON])
            val remaining = current.filterNot { it.id == vehicleId }
                .ifEmpty { VehicleGarageDefaults.presets }
            prefs[Keys.VEHICLES_JSON] = json.encodeToString(remaining)
            if (prefs[Keys.ACTIVE_VEHICLE_ID] == vehicleId) {
                prefs[Keys.ACTIVE_VEHICLE_ID] = remaining.first().id
            }
        }
    }

    private fun decodeVehicles(raw: String?): List<VehicleProfile> {
        val parsed = if (!raw.isNullOrBlank()) {
            runCatching { json.decodeFromString<List<VehicleProfile>>(raw) }.getOrNull()
        } else null
        return if (parsed.isNullOrEmpty()) VehicleGarageDefaults.presets else parsed
    }
}
