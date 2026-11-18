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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.kalyeDataStore: DataStore<Preferences> by preferencesDataStore(name = "kalye_prefs")

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val DISTANCE_UNIT = stringPreferencesKey("distance_unit")
        val CURRENCY_CODE = stringPreferencesKey("currency_code")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val MAP_LAYER = stringPreferencesKey("map_layer")
        val ACTIVE_TRIP_ID = stringPreferencesKey("active_trip_id")
    }

    val preferencesFlow: Flow<UserPreferences> = context.kalyeDataStore.data.map { prefs ->
        UserPreferences(
            distanceUnit = DistanceUnit.safeFrom(prefs[Keys.DISTANCE_UNIT]),
            currencyCode = prefs[Keys.CURRENCY_CODE] ?: "PHP",
            themeMode = ThemeMode.safeFrom(prefs[Keys.THEME_MODE]),
            mapLayer = MapLayerType.safeFrom(prefs[Keys.MAP_LAYER]),
            activeTripId = prefs[Keys.ACTIVE_TRIP_ID]
        )
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
}
