package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DistanceUnit
import com.example.data.model.GeoUtils
import com.example.data.model.MapLayerType
import com.example.data.model.OfflineRegion
import com.example.data.model.RoutePoint
import com.example.data.model.StopType
import com.example.data.model.TagCategory
import com.example.data.model.ThemeMode
import com.example.data.model.TripStop
import com.example.data.model.TripSummary
import com.example.data.model.UserPreferences
import com.example.data.model.VehicleBodyStyle
import com.example.data.model.VehicleProfile
import com.example.data.model.VehicleStats
import com.example.data.repository.KalyeRepository
import com.example.location.GpsSignalStatus
import com.example.location.KalyeLocationTracker
import com.example.location.LiveTelemetry
import com.example.maps.MapViewportBounds
import com.example.maps.OfflineTileCacheManager
import com.example.maps.RegionDownloadProgress
import com.example.update.AppUpdateChecker
import com.example.update.AppUpdateStatus
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class KalyeViewModel(
    private val repository: KalyeRepository,
    val tileManager: OfflineTileCacheManager,
    private val locationTracker: KalyeLocationTracker,
    val currentUserId: String,
    val userDisplayName: String?,
    val userEmail: String?,
    private val updateChecker: AppUpdateChecker = AppUpdateChecker()
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = repository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UserPreferences())

    val vehicles: StateFlow<List<VehicleProfile>> = repository.vehiclesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val activeVehicle: StateFlow<VehicleProfile?> = combine(vehicles, preferences) { list, prefs ->
        list.firstOrNull { it.id == prefs.activeVehicleId } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val shouldShowOnboarding: StateFlow<Boolean> = combine(
        repository.vehiclesFlow,
        repository.preferencesFlow
    ) { list, prefs ->
        list.isEmpty() && !prefs.garageOnboardingDismissed
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    val tripsState: StateFlow<UiState<List<TripSummary>>> = repository.observeTrips(currentUserId)
        .map<List<TripSummary>, UiState<List<TripSummary>>> { UiState.Success(it) }
        .catch { e ->
            if (e is CancellationException) throw e
            emit(UiState.Error(e.message ?: "Unable to load trips"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val vehicleStats: StateFlow<List<VehicleStats>> = combine(vehicles, tripsState) { vehs, tripsUi ->
        val trips = (tripsUi as? UiState.Success)?.data ?: emptyList()
        vehs.map { veh ->
            val matching = trips.filter { it.vehicleId == veh.id }
            val totalDistMeters = matching.sumOf { it.distanceMeters }
            val totalLiters = matching.sumOf { it.totalFuelLiters }
            val totalFuelCost = matching.sumOf { it.totalFuelCost }
            val fuelTrips = matching.filter { it.totalFuelLiters > 0.0 && it.distanceMeters > 0.0 }
            val avgKmPerL = GeoUtils.computeKmPerLiter(
                fuelTrips.sumOf { it.distanceMeters },
                fuelTrips.sumOf { it.totalFuelLiters }
            )
            VehicleStats(
                vehicle = veh,
                currentOdometerKm = veh.startingOdometerKm + (totalDistMeters / 1000.0),
                tripCount = matching.size,
                totalDistanceMeters = totalDistMeters,
                totalFuelLiters = totalLiters,
                totalFuelCost = totalFuelCost,
                averageKmPerLiter = avgKmPerL
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val activeTrip: StateFlow<TripSummary?> = repository.observeActiveTrip(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeTrackPoints: StateFlow<List<RoutePoint>> = activeTrip
        .flatMapLatest { trip ->
            if (trip == null) flowOf(emptyList()) else repository.observeTrackPoints(trip.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeTripStops: StateFlow<List<TripStop>> = activeTrip
        .flatMapLatest { trip ->
            if (trip == null) flowOf(emptyList()) else repository.observeStopsForTrip(trip.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val allStops: StateFlow<List<TripStop>> = repository.observeAllStops(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val categories: StateFlow<List<TagCategory>> = repository.observeCategories(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val offlineRegions: StateFlow<List<OfflineRegion>> = repository.observeOfflineRegions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _liveTelemetry = MutableStateFlow(LiveTelemetry())
    val liveTelemetry: StateFlow<LiveTelemetry> = _liveTelemetry.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _cloudSyncError = MutableStateFlow<String?>(null)
    val cloudSyncError: StateFlow<String?> = _cloudSyncError.asStateFlow()

    private val _downloadProgress = MutableStateFlow<RegionDownloadProgress?>(null)
    val downloadProgress: StateFlow<RegionDownloadProgress?> = _downloadProgress.asStateFlow()

    private val _diskCacheBytes = MutableStateFlow(0L)
    val diskCacheBytes: StateFlow<Long> = _diskCacheBytes.asStateFlow()

    private val _locationPermissionGranted = MutableStateFlow(false)
    val locationPermissionGranted: StateFlow<Boolean> = _locationPermissionGranted.asStateFlow()

    private val _updateStatus = MutableStateFlow<AppUpdateStatus>(AppUpdateStatus.Idle)
    val updateStatus: StateFlow<AppUpdateStatus> = _updateStatus.asStateFlow()

    val updateRepoSlug: String
        get() = updateChecker.repoSlug

    init {
        viewModelScope.launch {
            repository.ensureDefaultCategories(currentUserId)
            repository.pullRemotePreferencesIfAvailable()
            repository.syncPendingToCloud(currentUserId)
            refreshDiskUsage()
        }
        checkForUpdates(silent = true)
        observeCloudStreams()
    }

    private fun observeCloudStreams() {
        viewModelScope.launch {
            repository.observeRemoteTrips(currentUserId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w(TAG, "Cloud trip sync warning", rootError)
                    _cloudSyncError.value = rootError.message
                }
                .collect { remoteTrips ->
                    _cloudSyncError.value = null
                    repository.mergeRemoteTrips(currentUserId, remoteTrips)
                }
        }
        viewModelScope.launch {
            repository.observeRemoteStops(currentUserId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.w(TAG, "Cloud stops sync warning", firestoreError ?: error)
                }
                .collect { remoteStops ->
                    repository.mergeRemoteStops(currentUserId, remoteStops)
                }
        }
        viewModelScope.launch {
            repository.observeRemoteCategories(currentUserId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.w(TAG, "Cloud category sync warning", firestoreError ?: error)
                }
                .collect { remoteCats ->
                    repository.mergeRemoteCategories(currentUserId, remoteCats)
                }
        }
        viewModelScope.launch {
            repository.observeRemoteVehicles(currentUserId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.w(TAG, "Cloud vehicle sync warning", firestoreError ?: error)
                }
                .collect { remoteVehicles ->
                    repository.mergeRemoteVehicles(remoteVehicles)
                }
        }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        _locationPermissionGranted.value = granted
        if (!granted) {
            _liveTelemetry.value = LiveTelemetry(point = null, status = GpsSignalStatus.PERMISSION_NEEDED)
            return
        }
        viewModelScope.launch {
            locationTracker.observeHighAccuracyLocation().collect { telemetry ->
                _liveTelemetry.value = telemetry
            }
        }
    }

    fun observeTripById(tripId: String): Flow<TripSummary?> = repository.observeTrip(tripId)

    fun observePointsForTrip(tripId: String): Flow<List<RoutePoint>> =
        repository.observeTrackPoints(tripId)

    fun observeStopsForTrip(tripId: String): Flow<List<TripStop>> =
        repository.observeStopsForTrip(tripId)

    fun startRide(
        title: String,
        categoryTag: String,
        onStarted: (TripSummary) -> Unit
    ) {
        viewModelScope.launch {
            val rig = activeVehicle.value
            val trip = repository.startNewTrip(
                userId = currentUserId,
                title = title,
                categoryTag = categoryTag,
                currencyCode = preferences.value.currencyCode,
                vehicleId = rig?.id.orEmpty(),
                vehicleName = rig?.nickname.orEmpty()
            )
            // Seed initial point if GPS already locked
            _liveTelemetry.value.point?.let { initialPt ->
                repository.appendTrackPoint(trip.id, initialPt)
            }
            onStarted(trip)
        }
    }

    fun togglePauseRide(trip: TripSummary) {
        viewModelScope.launch {
            repository.setTripPaused(trip.id, !trip.isPaused)
        }
    }

    fun finishRide(
        trip: TripSummary,
        finalTitle: String,
        notes: String,
        onFinished: (String) -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.finishTrip(trip.id, finalTitle, notes)
            if (preferences.value.stealthModeEnabled) {
                repository.setStealthModeEnabled(false)
            }
            _syncMessage.value = if (res.isSuccess) {
                "Ride saved & synced to cloud"
            } else {
                "Ride saved offline — will sync when connected"
            }
            onFinished(trip.id)
        }
    }

    fun updateTripDetails(
        tripId: String,
        title: String,
        categoryTag: String,
        notes: String
    ) {
        viewModelScope.launch {
            val res = repository.updateTripDetails(tripId, title, categoryTag, notes)
            _syncMessage.value = if (res.isSuccess) "Trip updated" else "Saved offline"
        }
    }

    fun deleteTrip(tripId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteTrip(tripId)
            _syncMessage.value = "Trip deleted"
            onDeleted()
        }
    }

    fun logStopOrExpense(
        tripId: String,
        stopType: StopType,
        title: String,
        note: String,
        costAmount: Double,
        fuelLiters: Double,
        pricePerLiter: Double,
        latitude: Double?,
        longitude: Double?,
        elevationMeters: Double?
    ) {
        viewModelScope.launch {
            val currentPt = _liveTelemetry.value.point
            val lat = latitude ?: currentPt?.latitude ?: 14.5995
            val lon = longitude ?: currentPt?.longitude ?: 120.9842
            val ele = elevationMeters ?: currentPt?.altitudeMeters ?: 0.0
            val stop = TripStop(
                id = "",
                userId = currentUserId,
                tripId = tripId,
                stopType = stopType,
                title = title.ifBlank { stopType.label },
                note = note,
                latitude = lat,
                longitude = lon,
                elevationMeters = ele,
                costAmount = costAmount,
                fuelLiters = fuelLiters,
                pricePerLiter = pricePerLiter,
                currencyCode = preferences.value.currencyCode,
                timestampMillis = System.currentTimeMillis()
            )
            val res = repository.addOrUpdateStop(stop)
            _syncMessage.value = if (res.isSuccess) {
                "${stopType.label} logged"
            } else {
                "${stopType.label} saved offline"
            }
        }
    }

    fun deleteStop(stop: TripStop) {
        viewModelScope.launch {
            repository.deleteStop(stop)
            _syncMessage.value = "Entry removed"
        }
    }

    fun addCustomCategory(name: String, colorHex: Long, iconKey: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.addCustomCategory(currentUserId, name, colorHex, iconKey)
            _syncMessage.value = "Tag \"$name\" added"
        }
    }

    fun deleteCategory(categoryId: String) {
        viewModelScope.launch {
            repository.deleteCategory(categoryId)
        }
    }

    fun toggleDarkModeQuick() {
        val current = preferences.value.themeMode
        val next = if (current == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK
        setThemeMode(next)
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            repository.updateThemeMode(mode, preferences.value, userDisplayName)
        }
    }

    fun setDistanceUnit(unit: DistanceUnit) {
        viewModelScope.launch {
            repository.updateDistanceUnit(unit, preferences.value, userDisplayName)
        }
    }

    fun setCurrencyCode(code: String) {
        viewModelScope.launch {
            repository.updateCurrencyCode(code, preferences.value, userDisplayName)
        }
    }

    fun setMapLayer(layer: MapLayerType) {
        viewModelScope.launch {
            repository.updateMapLayer(layer, preferences.value, userDisplayName)
        }
    }

    fun toggleOfflineCacheMode() {
        val next = if (preferences.value.mapLayer == MapLayerType.OFFLINE) {
            MapLayerType.STREET
        } else {
            MapLayerType.OFFLINE
        }
        setMapLayer(next)
    }

    fun setStealthModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setStealthModeEnabled(enabled)
        }
    }

    fun selectVehicle(vehicleId: String) {
        viewModelScope.launch {
            repository.selectVehicle(vehicleId)
            val chosen = vehicles.value.firstOrNull { it.id == vehicleId }
            if (chosen != null) {
                _syncMessage.value = "Active rig: ${chosen.nickname}"
            }
        }
    }

    fun addVehicle(
        nickname: String,
        makeModel: String,
        bodyStyle: VehicleBodyStyle,
        drivetrain: String,
        fuelType: String,
        accentColorHex: Long,
        startingOdometerKm: Double = 0.0
    ) {
        val cleanName = nickname.trim().ifBlank { bodyStyle.label }
        val cleanModel = makeModel.trim().ifBlank { "${bodyStyle.label} • $drivetrain" }
        val newVehicle = VehicleProfile(
            id = "veh_${UUID.randomUUID().toString().replace("-", "").take(10)}",
            nickname = cleanName,
            makeModel = cleanModel,
            bodyStyle = bodyStyle,
            drivetrain = drivetrain,
            fuelType = fuelType,
            accentColorHex = accentColorHex,
            startingOdometerKm = startingOdometerKm.coerceAtLeast(0.0)
        )
        viewModelScope.launch {
            repository.addVehicle(newVehicle)
            _syncMessage.value = "Added $cleanName to Garage"
        }
    }

    fun updateVehicle(
        vehicleId: String,
        nickname: String,
        makeModel: String,
        bodyStyle: VehicleBodyStyle,
        drivetrain: String,
        fuelType: String,
        accentColorHex: Long,
        startingOdometerKm: Double
    ) {
        val cleanName = nickname.trim().ifBlank { bodyStyle.label }
        val cleanModel = makeModel.trim().ifBlank { "${bodyStyle.label} • $drivetrain" }
        val updated = VehicleProfile(
            id = vehicleId,
            nickname = cleanName,
            makeModel = cleanModel,
            bodyStyle = bodyStyle,
            drivetrain = drivetrain,
            fuelType = fuelType,
            accentColorHex = accentColorHex,
            startingOdometerKm = startingOdometerKm.coerceAtLeast(0.0)
        )
        viewModelScope.launch {
            repository.updateVehicle(updated)
            _syncMessage.value = "Updated $cleanName"
        }
    }

    fun completeOnboardingVehicle(
        nickname: String,
        makeModel: String,
        bodyStyle: VehicleBodyStyle,
        drivetrain: String,
        fuelType: String,
        accentColorHex: Long,
        startingOdometerKm: Double
    ) {
        val cleanName = nickname.trim().ifBlank { bodyStyle.label }
        val cleanModel = makeModel.trim().ifBlank { "${bodyStyle.label} • $drivetrain" }
        val newVehicle = VehicleProfile(
            id = "veh_${UUID.randomUUID().toString().replace("-", "").take(10)}",
            nickname = cleanName,
            makeModel = cleanModel,
            bodyStyle = bodyStyle,
            drivetrain = drivetrain,
            fuelType = fuelType,
            accentColorHex = accentColorHex,
            startingOdometerKm = startingOdometerKm.coerceAtLeast(0.0)
        )
        viewModelScope.launch {
            repository.addVehicle(newVehicle)
            repository.setGarageOnboardingDismissed(true)
            _syncMessage.value = "Welcome! $cleanName is ready to track"
        }
    }

    fun skipOnboarding() {
        viewModelScope.launch {
            repository.setGarageOnboardingDismissed(true)
        }
    }

    fun deleteVehicle(vehicleId: String) {
        viewModelScope.launch {
            repository.deleteVehicle(vehicleId)
            _syncMessage.value = "Vehicle removed from Garage"
        }
    }

    fun downloadOfflineRegion(
        name: String,
        bounds: MapViewportBounds,
        minZoom: Int,
        maxZoom: Int
    ) {
        viewModelScope.launch {
            val isDark = preferences.value.themeMode == ThemeMode.DARK
            var lastProgress: RegionDownloadProgress? = null
            tileManager.downloadRegion(
                isDark = isDark,
                minLat = bounds.minLat,
                maxLat = bounds.maxLat,
                minLon = bounds.minLon,
                maxLon = bounds.maxLon,
                minZoom = minZoom,
                maxZoom = maxZoom
            ).collect { progress ->
                _downloadProgress.value = progress
                lastProgress = progress
            }
            val finalProg = lastProgress
            if (finalProg != null) {
                repository.saveOfflineRegion(
                    OfflineRegion(
                        id = "reg_${UUID.randomUUID().toString().take(10)}",
                        name = name.ifBlank { "Cached Sector" },
                        minLat = bounds.minLat,
                        maxLat = bounds.maxLat,
                        minLon = bounds.minLon,
                        maxLon = bounds.maxLon,
                        minZoom = minZoom,
                        maxZoom = maxZoom,
                        layerType = MapLayerType.STREET,
                        tileCount = finalProg.downloaded,
                        sizeBytes = finalProg.bytesDownloaded,
                        downloadedAtMillis = System.currentTimeMillis()
                    )
                )
            }
            refreshDiskUsage()
            _downloadProgress.value = null
            _syncMessage.value = "Offline street sector cached"
        }
    }

    fun deleteOfflineRegion(regionId: String) {
        viewModelScope.launch {
            repository.deleteOfflineRegion(regionId)
        }
    }

    fun clearAllTileCache() {
        viewModelScope.launch {
            tileManager.clearAllTiles()
            refreshDiskUsage()
            _syncMessage.value = "Tile cache cleared"
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            val count = repository.syncPendingToCloud(currentUserId)
            _syncMessage.value = if (count > 0) "Synced $count pending items" else "Cloud database is up to date"
        }
    }

    fun checkForUpdates(silent: Boolean = false) {
        if (_updateStatus.value is AppUpdateStatus.Checking) return
        viewModelScope.launch {
            if (!silent) {
                _updateStatus.value = AppUpdateStatus.Checking
            }
            val result = updateChecker.checkForUpdates()
            if (silent && result is AppUpdateStatus.Error) {
                if (_updateStatus.value is AppUpdateStatus.Checking) {
                    _updateStatus.value = AppUpdateStatus.Idle
                }
                return@launch
            }
            _updateStatus.value = result
            if (!silent) {
                when (result) {
                    is AppUpdateStatus.UpdateAvailable -> {
                        _syncMessage.value = "New version ${result.release.tagName} available"
                    }
                    is AppUpdateStatus.UpToDate -> {
                        _syncMessage.value = "Kalye v${result.currentVersion} is up to date"
                    }
                    is AppUpdateStatus.Error -> {
                        _syncMessage.value = result.message
                    }
                    else -> Unit
                }
            }
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    private fun refreshDiskUsage() {
        viewModelScope.launch {
            _diskCacheBytes.value = tileManager.getTotalDiskUsageBytes()
        }
    }

    companion object {
        private const val TAG = "KalyeViewModel"
    }
}
