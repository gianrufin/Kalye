package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.DistanceUnit
import com.example.data.model.MapLayerType
import com.example.data.model.ThemeMode
import com.example.data.model.UserPreferences
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
        val GARAGE_ONBOARDING_DISMISSED = booleanPreferencesKey("garage_onboarding_dismissed")
        val STEALTH_MODE_ENABLED = booleanPreferencesKey("stealth_mode_enabled")
    }

    private val json = Json { ignoreUnknownKeys = true }

    val preferencesFlow: Flow<UserPreferences> = context.kalyeDataStore.data.map { prefs ->
        UserPreferences(
            distanceUnit = DistanceUnit.safeFrom(prefs[Keys.DISTANCE_UNIT]),
            currencyCode = prefs[Keys.CURRENCY_CODE] ?: "PHP",
            themeMode = ThemeMode.safeFrom(prefs[Keys.THEME_MODE]),
            mapLayer = MapLayerType.safeFrom(prefs[Keys.MAP_LAYER]),
            activeTripId = prefs[Keys.ACTIVE_TRIP_ID],
            activeVehicleId = prefs[Keys.ACTIVE_VEHICLE_ID]?.takeIf { it.isNotBlank() },
            garageOnboardingDismissed = prefs[Keys.GARAGE_ONBOARDING_DISMISSED] ?: false,
            stealthModeEnabled = prefs[Keys.STEALTH_MODE_ENABLED] ?: false
        )
    }

    val vehiclesFlow: Flow<List<VehicleProfile>> = context.kalyeDataStore.data.map { prefs ->
        decodeVehicles(prefs[Keys.VEHICLES_JSON])
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

    suspend fun setGarageOnboardingDismissed(dismissed: Boolean) {
        context.kalyeDataStore.edit { it[Keys.GARAGE_ONBOARDING_DISMISSED] = dismissed }
    }

    suspend fun setStealthModeEnabled(enabled: Boolean) {
        context.kalyeDataStore.edit { it[Keys.STEALTH_MODE_ENABLED] = enabled }
    }

    suspend fun setActiveTripId(tripId: String?) {
        context.kalyeDataStore.edit { prefs ->
            if (tripId == null) prefs.remove(Keys.ACTIVE_TRIP_ID)
            else prefs[Keys.ACTIVE_TRIP_ID] = tripId
        }
    }

    suspend fun setActiveVehicleId(vehicleId: String?) {
        context.kalyeDataStore.edit { prefs ->
            if (vehicleId.isNullOrBlank()) prefs.remove(Keys.ACTIVE_VEHICLE_ID)
            else prefs[Keys.ACTIVE_VEHICLE_ID] = vehicleId
        }
    }

    suspend fun addVehicle(vehicle: VehicleProfile) {
        context.kalyeDataStore.edit { prefs ->
            val current = decodeVehicles(prefs[Keys.VEHICLES_JSON])
            val updated = current.filterNot { it.id == vehicle.id } + vehicle
            prefs[Keys.VEHICLES_JSON] = json.encodeToString(updated)
            prefs[Keys.ACTIVE_VEHICLE_ID] = vehicle.id
            prefs[Keys.GARAGE_ONBOARDING_DISMISSED] = true
        }
    }

    suspend fun updateVehicle(vehicle: VehicleProfile) {
        context.kalyeDataStore.edit { prefs ->
            val current = decodeVehicles(prefs[Keys.VEHICLES_JSON])
            val updated = if (current.any { it.id == vehicle.id }) {
                current.map { if (it.id == vehicle.id) vehicle else it }
            } else {
                current + vehicle
            }
            prefs[Keys.VEHICLES_JSON] = json.encodeToString(updated)
        }
    }

    suspend fun replaceVehiclesFromCloud(remoteVehicles: List<VehicleProfile>) {
        context.kalyeDataStore.edit { prefs ->
            prefs[Keys.VEHICLES_JSON] = json.encodeToString(remoteVehicles)
            val currentActive = prefs[Keys.ACTIVE_VEHICLE_ID]
            if (remoteVehicles.isEmpty()) {
                prefs.remove(Keys.ACTIVE_VEHICLE_ID)
            } else if (currentActive.isNullOrBlank() || remoteVehicles.none { it.id == currentActive }) {
                prefs[Keys.ACTIVE_VEHICLE_ID] = remoteVehicles.first().id
            }
            if (remoteVehicles.isNotEmpty()) {
                prefs[Keys.GARAGE_ONBOARDING_DISMISSED] = true
            }
        }
    }

    suspend fun deleteVehicle(vehicleId: String) {
        context.kalyeDataStore.edit { prefs ->
            val current = decodeVehicles(prefs[Keys.VEHICLES_JSON])
            val remaining = current.filterNot { it.id == vehicleId }
            prefs[Keys.VEHICLES_JSON] = json.encodeToString(remaining)
            if (prefs[Keys.ACTIVE_VEHICLE_ID] == vehicleId) {
                val nextId = remaining.firstOrNull()?.id
                if (nextId != null) {
                    prefs[Keys.ACTIVE_VEHICLE_ID] = nextId
                } else {
                    prefs.remove(Keys.ACTIVE_VEHICLE_ID)
                }
            }
        }
    }

    private fun decodeVehicles(raw: String?): List<VehicleProfile> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<VehicleProfile>>(raw) }.getOrNull() ?: emptyList()
    }
}
