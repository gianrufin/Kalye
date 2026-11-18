package com.example.data.repository

import android.content.Context
import com.example.data.local.KalyeDao
import com.example.data.local.KalyeDatabase
import com.example.data.local.OfflineMapRegionEntity
import com.example.data.local.PreferencesRepository
import com.example.data.local.TagCategoryEntity
import com.example.data.local.TrackPointEntity
import com.example.data.local.TripEntity
import com.example.data.local.TripStopEntity
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
import com.example.data.remote.KalyeFirestoreDataSource
import com.example.widget.KalyeDailyWidget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class KalyeRepository(
    private val appContext: Context,
    private val dao: KalyeDao,
    private val remote: KalyeFirestoreDataSource,
    private val prefsRepo: PreferencesRepository
) {
    constructor(context: Context, remote: KalyeFirestoreDataSource) : this(
        appContext = context.applicationContext,
        dao = KalyeDatabase.getInstance(context.applicationContext).kalyeDao(),
        remote = remote,
        prefsRepo = PreferencesRepository(context.applicationContext)
    )

    val preferencesFlow: Flow<UserPreferences> = prefsRepo.preferencesFlow

    fun observeTrips(userId: String): Flow<List<TripSummary>> =
        dao.observeTrips(userId).map { list -> list.map { it.toDomain() } }

    fun observeActiveTrip(userId: String): Flow<TripSummary?> =
        dao.observeActiveTrip(userId).map { it?.toDomain() }

    fun observeTrip(tripId: String): Flow<TripSummary?> =
        dao.observeTrip(tripId).map { it?.toDomain() }

    fun observeTrackPoints(tripId: String): Flow<List<RoutePoint>> =
        dao.observeTrackPoints(tripId).map { list -> list.map { it.toDomain() } }

    fun observeAllStops(userId: String): Flow<List<TripStop>> =
        dao.observeAllStops(userId).map { list -> list.map { it.toDomain() } }

    fun observeStopsForTrip(tripId: String): Flow<List<TripStop>> =
        dao.observeStopsForTrip(tripId).map { list -> list.map { it.toDomain() } }

    fun observeCategories(userId: String): Flow<List<TagCategory>> =
        dao.observeCategories(userId).map { list -> list.map { it.toDomain() } }

    fun observeOfflineRegions(): Flow<List<OfflineRegion>> =
        dao.observeOfflineRegions().map { list -> list.map { it.toDomain() } }

    fun observeRemoteTrips(userId: String): Flow<List<TripSummary>> = remote.observeTrips(userId)
    fun observeRemoteStops(userId: String): Flow<List<TripStop>> = remote.observeStops(userId)
    fun observeRemoteCategories(userId: String): Flow<List<TagCategory>> = remote.observeCategories(userId)

    suspend fun ensureDefaultCategories(userId: String) {
        val existing = dao.getCategories(userId)
        if (existing.isNotEmpty()) return
        val defaults = listOf(
            TagCategory("cat_overland", userId, "Overland", 0xFFB84A27L, "terrain", true),
            TagCategory("cat_camp", userId, "Car Camp", 0xFF2A5C48L, "cabin", true),
            TagCategory("cat_ferry", userId, "Coastal Ferry", 0xFF1F6E8CL, "directions_boat", true),
            TagCategory("cat_pass", userId, "Mountain Pass", 0xFF9F6410L, "landscape", true),
            TagCategory("cat_long", userId, "Long Drive", 0xFF5A4E7CL, "route", true)
        )
        defaults.forEach { cat ->
            dao.upsertCategory(
                TagCategoryEntity(
                    id = cat.id,
                    userId = userId,
                    name = cat.name,
                    colorHex = cat.colorHex,
                    iconKey = cat.iconKey,
                    defaultTag = cat.defaultTag,
                    syncedFlag = false
                )
            )
            remote.upsertCategory(cat).onSuccess {
                dao.upsertCategory(
                    TagCategoryEntity(
                        id = cat.id,
                        userId = userId,
                        name = cat.name,
                        colorHex = cat.colorHex,
                        iconKey = cat.iconKey,
                        defaultTag = cat.defaultTag,
                        syncedFlag = true
                    )
                )
            }
        }
    }

    suspend fun mergeRemoteTrips(userId: String, remoteTrips: List<TripSummary>) {
        remoteTrips.forEach { trip ->
            val local = dao.getTripById(trip.id)
            if (local == null || (!local.activeFlag && local.syncedFlag)) {
                dao.upsertTrip(
                    TripEntity(
                        id = trip.id,
                        userId = userId,
                        title = trip.title,
                        categoryTag = trip.categoryTag,
                        notes = trip.notes,
                        startTimeMillis = trip.startTimeMillis,
                        endTimeMillis = trip.endTimeMillis,
                        distanceMeters = trip.distanceMeters,
                        elevationGainMeters = trip.elevationGainMeters,
                        elevationLossMeters = trip.elevationLossMeters,
                        maxElevationMeters = trip.maxElevationMeters,
                        minElevationMeters = trip.minElevationMeters,
                        maxSpeedKmh = trip.maxSpeedKmh,
                        totalFuelCost = trip.totalFuelCost,
                        totalFerryCost = trip.totalFerryCost,
                        totalOtherCost = trip.totalOtherCost,
                        totalFuelLiters = trip.totalFuelLiters,
                        currencyCode = trip.currencyCode,
                        routePolyline = trip.routePolyline,
                        pointCount = trip.pointCount,
                        activeFlag = false,
                        pausedFlag = false,
                        syncedFlag = true,
                        updatedAtMillis = System.currentTimeMillis()
                    )
                )
                // Restore sampled route points locally if track_points table has none for this trip
                val localPoints = dao.getTrackPoints(trip.id)
                if (localPoints.isEmpty() && trip.routePolyline.isNotBlank()) {
                    GeoUtils.decodeRoutePoints(trip.routePolyline).forEach { pt ->
                        dao.insertTrackPoint(
                            TrackPointEntity(
                                tripId = trip.id,
                                latitude = pt.latitude,
                                longitude = pt.longitude,
                                altitudeMeters = pt.altitudeMeters,
                                accuracyMeters = pt.accuracyMeters,
                                speedMps = pt.speedMps,
                                bearing = pt.bearing,
                                timestampMillis = trip.startTimeMillis
                            )
                        )
                    }
                }
            }
        }
        KalyeDailyWidget.refreshAll(appContext)
    }

    suspend fun mergeRemoteStops(userId: String, remoteStops: List<TripStop>) {
        remoteStops.forEach { stop ->
            dao.upsertStop(
                TripStopEntity(
                    id = stop.id,
                    userId = userId,
                    tripId = stop.tripId,
                    stopType = stop.stopType.name,
                    title = stop.title,
                    note = stop.note,
                    latitude = stop.latitude,
                    longitude = stop.longitude,
                    elevationMeters = stop.elevationMeters,
                    costAmount = stop.costAmount,
                    fuelLiters = stop.fuelLiters,
                    pricePerLiter = stop.pricePerLiter,
                    currencyCode = stop.currencyCode,
                    timestampMillis = stop.timestampMillis,
                    syncedFlag = true
                )
            )
        }
    }

    suspend fun mergeRemoteCategories(userId: String, remoteCats: List<TagCategory>) {
        remoteCats.forEach { cat ->
            dao.upsertCategory(
                TagCategoryEntity(
                    id = cat.id,
                    userId = userId,
                    name = cat.name,
                    colorHex = cat.colorHex,
                    iconKey = cat.iconKey,
                    defaultTag = cat.defaultTag,
                    syncedFlag = true
                )
            )
        }
    }

    suspend fun startNewTrip(
        userId: String,
        title: String,
        categoryTag: String,
        currencyCode: String
    ): TripSummary {
        val now = System.currentTimeMillis()
        val tripId = "trip_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val entity = TripEntity(
            id = tripId,
            userId = userId,
            title = title.ifBlank { "Overland Excursion" },
            categoryTag = categoryTag.ifBlank { "Overland" },
            notes = "",
            startTimeMillis = now,
            endTimeMillis = now,
            distanceMeters = 0.0,
            elevationGainMeters = 0.0,
            elevationLossMeters = 0.0,
            maxElevationMeters = 0.0,
            minElevationMeters = 0.0,
            maxSpeedKmh = 0.0,
            totalFuelCost = 0.0,
            totalFerryCost = 0.0,
            totalOtherCost = 0.0,
            totalFuelLiters = 0.0,
            currencyCode = currencyCode,
            routePolyline = "",
            pointCount = 0,
            activeFlag = true,
            pausedFlag = false,
            syncedFlag = false,
            updatedAtMillis = now
        )
        dao.upsertTrip(entity)
        prefsRepo.setActiveTripId(tripId)
        KalyeDailyWidget.refreshAll(appContext)
        return entity.toDomain()
    }

    suspend fun setTripPaused(tripId: String, paused: Boolean) {
        val existing = dao.getTripById(tripId) ?: return
        dao.upsertTrip(
            existing.copy(
                pausedFlag = paused,
                endTimeMillis = System.currentTimeMillis(),
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun appendTrackPoint(tripId: String, point: RoutePoint) {
        val trip = dao.getTripById(tripId) ?: return
        if (!trip.activeFlag || trip.pausedFlag) return

        val existingPoints = dao.getTrackPoints(tripId)
        val lastPoint = existingPoints.lastOrNull()

        var deltaDistance = 0.0
        var deltaGain = 0.0
        var deltaLoss = 0.0

        if (lastPoint != null) {
            deltaDistance = GeoUtils.haversineMeters(
                lastPoint.latitude, lastPoint.longitude,
                point.latitude, point.longitude
            )
            // Ignore GPS jitter < 2.5m unless stationary timeout > 30s
            if (deltaDistance < 2.5 && (point.timestampMillis - lastPoint.timestampMillis) < 30_000L) {
                return
            }
            val eleDiff = point.altitudeMeters - lastPoint.altitudeMeters
            // Smooth GPS altitude noise (threshold 2.0m)
            if (eleDiff >= 2.0) deltaGain = eleDiff
            else if (eleDiff <= -2.0) deltaLoss = -eleDiff
        }

        dao.insertTrackPoint(
            TrackPointEntity(
                tripId = tripId,
                latitude = point.latitude,
                longitude = point.longitude,
                altitudeMeters = point.altitudeMeters,
                accuracyMeters = point.accuracyMeters,
                speedMps = point.speedMps,
                bearing = point.bearing,
                timestampMillis = point.timestampMillis
            )
        )

        val newCount = existingPoints.size + 1
        val newMaxEle = if (existingPoints.isEmpty()) point.altitudeMeters else maxOf(trip.maxElevationMeters, point.altitudeMeters)
        val newMinEle = if (existingPoints.isEmpty()) point.altitudeMeters else minOf(trip.minElevationMeters, point.altitudeMeters)
        val speedKmh = (point.speedMps * 3.6).coerceAtLeast(0.0)

        val updatedPoints = existingPoints.map { it.toDomain() } + point
        val updatedTrip = trip.copy(
            endTimeMillis = point.timestampMillis,
            distanceMeters = trip.distanceMeters + deltaDistance,
            elevationGainMeters = trip.elevationGainMeters + deltaGain,
            elevationLossMeters = trip.elevationLossMeters + deltaLoss,
            maxElevationMeters = newMaxEle,
            minElevationMeters = newMinEle,
            maxSpeedKmh = maxOf(trip.maxSpeedKmh, speedKmh),
            pointCount = newCount,
            routePolyline = GeoUtils.encodeRoutePoints(updatedPoints),
            updatedAtMillis = System.currentTimeMillis(),
            syncedFlag = false
        )
        dao.upsertTrip(updatedTrip)
        KalyeDailyWidget.refreshAll(appContext)
    }

    suspend fun finishTrip(tripId: String, finalTitle: String? = null, notes: String? = null): Result<Unit> {
        val trip = dao.getTripById(tripId) ?: return Result.failure(IllegalStateException("Trip not found"))
        val points = dao.getTrackPoints(tripId).map { it.toDomain() }
        val polyline = GeoUtils.encodeRoutePoints(points)
        val now = System.currentTimeMillis()
        val finished = trip.copy(
            title = finalTitle?.takeIf { it.isNotBlank() } ?: trip.title,
            notes = notes ?: trip.notes,
            endTimeMillis = maxOf(trip.endTimeMillis, now),
            routePolyline = polyline,
            pointCount = points.size,
            activeFlag = false,
            pausedFlag = false,
            syncedFlag = false,
            updatedAtMillis = now
        )
        dao.upsertTrip(finished)
        prefsRepo.setActiveTripId(null)
        KalyeDailyWidget.refreshAll(appContext)

        val syncRes = remote.upsertTrip(finished.toDomain())
        if (syncRes.isSuccess) {
            dao.upsertTrip(finished.copy(syncedFlag = true))
        }
        return syncRes
    }

    suspend fun updateTripDetails(
        tripId: String,
        title: String,
        categoryTag: String,
        notes: String
    ): Result<Unit> {
        val trip = dao.getTripById(tripId) ?: return Result.failure(IllegalStateException("Trip not found"))
        val updated = trip.copy(
            title = title.ifBlank { trip.title },
            categoryTag = categoryTag.ifBlank { trip.categoryTag },
            notes = notes,
            syncedFlag = false,
            updatedAtMillis = System.currentTimeMillis()
        )
        dao.upsertTrip(updated)
        val syncRes = remote.upsertTrip(updated.toDomain())
        if (syncRes.isSuccess) {
            dao.upsertTrip(updated.copy(syncedFlag = true))
        }
        return syncRes
    }

    suspend fun deleteTrip(tripId: String): Result<Unit> {
        val stops = dao.getStopsForTrip(tripId)
        dao.deleteStopsForTrip(tripId)
        dao.deleteTrackPointsForTrip(tripId)
        dao.deleteTripById(tripId)
        KalyeDailyWidget.refreshAll(appContext)
        stops.forEach { stop ->
            remote.deleteStop(stop.id)
        }
        return remote.deleteTrip(tripId)
    }

    suspend fun addOrUpdateStop(stop: TripStop): Result<Unit> {
        val stopId = stop.id.ifBlank { "stop_${UUID.randomUUID().toString().replace("-", "").take(16)}" }
        val normalized = stop.copy(id = stopId, isSynced = false)
        dao.upsertStop(
            TripStopEntity(
                id = normalized.id,
                userId = normalized.userId,
                tripId = normalized.tripId,
                stopType = normalized.stopType.name,
                title = normalized.title,
                note = normalized.note,
                latitude = normalized.latitude,
                longitude = normalized.longitude,
                elevationMeters = normalized.elevationMeters,
                costAmount = normalized.costAmount,
                fuelLiters = normalized.fuelLiters,
                pricePerLiter = normalized.pricePerLiter,
                currencyCode = normalized.currencyCode,
                timestampMillis = normalized.timestampMillis,
                syncedFlag = false
            )
        )
        recalculateTripTotals(normalized.tripId)
        KalyeDailyWidget.refreshAll(appContext)

        val syncRes = remote.upsertStop(normalized)
        if (syncRes.isSuccess) {
            dao.upsertStop(
                TripStopEntity(
                    id = normalized.id,
                    userId = normalized.userId,
                    tripId = normalized.tripId,
                    stopType = normalized.stopType.name,
                    title = normalized.title,
                    note = normalized.note,
                    latitude = normalized.latitude,
                    longitude = normalized.longitude,
                    elevationMeters = normalized.elevationMeters,
                    costAmount = normalized.costAmount,
                    fuelLiters = normalized.fuelLiters,
                    pricePerLiter = normalized.pricePerLiter,
                    currencyCode = normalized.currencyCode,
                    timestampMillis = normalized.timestampMillis,
                    syncedFlag = true
                )
            )
        }
        return syncRes
    }

    suspend fun deleteStop(stop: TripStop): Result<Unit> {
        dao.deleteStopById(stop.id)
        recalculateTripTotals(stop.tripId)
        KalyeDailyWidget.refreshAll(appContext)
        return remote.deleteStop(stop.id)
    }

    private suspend fun recalculateTripTotals(tripId: String) {
        val trip = dao.getTripById(tripId) ?: return
        val stops = dao.getStopsForTrip(tripId)
        val fuelCost = stops.filter { it.stopType == StopType.FUEL.name }.sumOf { it.costAmount }
        val ferryCost = stops.filter { it.stopType == StopType.FERRY.name }.sumOf { it.costAmount }
        val otherCost = stops.filter {
            it.stopType != StopType.FUEL.name && it.stopType != StopType.FERRY.name
        }.sumOf { it.costAmount }
        val fuelLiters = stops.filter { it.stopType == StopType.FUEL.name }.sumOf { it.fuelLiters }

        val updated = trip.copy(
            totalFuelCost = fuelCost,
            totalFerryCost = ferryCost,
            totalOtherCost = otherCost,
            totalFuelLiters = fuelLiters,
            syncedFlag = false,
            updatedAtMillis = System.currentTimeMillis()
        )
        dao.upsertTrip(updated)
        if (!updated.activeFlag) {
            val res = remote.upsertTrip(updated.toDomain())
            if (res.isSuccess) {
                dao.upsertTrip(updated.copy(syncedFlag = true))
            }
        }
    }

    suspend fun addCustomCategory(
        userId: String,
        name: String,
        colorHex: Long,
        iconKey: String
    ): Result<Unit> {
        val id = "cat_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val cat = TagCategory(
            id = id,
            userId = userId,
            name = name.trim().take(40),
            colorHex = colorHex,
            iconKey = iconKey,
            defaultTag = false
        )
        dao.upsertCategory(
            TagCategoryEntity(
                id = cat.id,
                userId = userId,
                name = cat.name,
                colorHex = cat.colorHex,
                iconKey = cat.iconKey,
                defaultTag = false,
                syncedFlag = false
            )
        )
        val res = remote.upsertCategory(cat)
        if (res.isSuccess) {
            dao.upsertCategory(
                TagCategoryEntity(
                    id = cat.id,
                    userId = userId,
                    name = cat.name,
                    colorHex = cat.colorHex,
                    iconKey = cat.iconKey,
                    defaultTag = false,
                    syncedFlag = true
                )
            )
        }
        return res
    }

    suspend fun deleteCategory(categoryId: String): Result<Unit> {
        dao.deleteCategoryById(categoryId)
        return remote.deleteCategory(categoryId)
    }

    suspend fun saveOfflineRegion(region: OfflineRegion) {
        dao.upsertOfflineRegion(
            OfflineMapRegionEntity(
                id = region.id,
                name = region.name,
                minLat = region.minLat,
                maxLat = region.maxLat,
                minLon = region.minLon,
                maxLon = region.maxLon,
                minZoom = region.minZoom,
                maxZoom = region.maxZoom,
                layerType = region.layerType.name,
                tileCount = region.tileCount,
                sizeBytes = region.sizeBytes,
                downloadedAtMillis = region.downloadedAtMillis
            )
        )
    }

    suspend fun deleteOfflineRegion(regionId: String) {
        dao.deleteOfflineRegionById(regionId)
    }

    suspend fun syncPendingToCloud(userId: String): Int {
        var syncedCount = 0
        try {
            val pendingTrips = dao.getUnsyncedCompletedTrips(userId)
            pendingTrips.forEach { tripEntity ->
                val res = remote.upsertTrip(tripEntity.toDomain())
                if (res.isSuccess) {
                    dao.upsertTrip(tripEntity.copy(syncedFlag = true))
                    syncedCount++
                }
            }
            val pendingStops = dao.getUnsyncedStops(userId)
            pendingStops.forEach { stopEntity ->
                val res = remote.upsertStop(stopEntity.toDomain())
                if (res.isSuccess) {
                    dao.upsertStop(stopEntity.copy(syncedFlag = true))
                    syncedCount++
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        return syncedCount
    }

    suspend fun updateDistanceUnit(unit: DistanceUnit, currentPrefs: UserPreferences, displayName: String?) {
        prefsRepo.setDistanceUnit(unit)
        KalyeDailyWidget.refreshAll(appContext)
        remote.savePreferences(currentPrefs.copy(distanceUnit = unit), displayName)
    }

    suspend fun updateCurrencyCode(code: String, currentPrefs: UserPreferences, displayName: String?) {
        prefsRepo.setCurrencyCode(code)
        KalyeDailyWidget.refreshAll(appContext)
        remote.savePreferences(currentPrefs.copy(currencyCode = code), displayName)
    }

    suspend fun updateThemeMode(mode: ThemeMode, currentPrefs: UserPreferences, displayName: String?) {
        prefsRepo.setThemeMode(mode)
        remote.savePreferences(currentPrefs.copy(themeMode = mode), displayName)
    }

    suspend fun updateMapLayer(layer: MapLayerType, currentPrefs: UserPreferences, displayName: String?) {
        prefsRepo.setMapLayer(layer)
        remote.savePreferences(currentPrefs.copy(mapLayer = layer), displayName)
    }

    suspend fun pullRemotePreferencesIfAvailable() {
        val remotePrefs = remote.fetchPreferences() ?: return
        prefsRepo.setDistanceUnit(remotePrefs.distanceUnit)
        prefsRepo.setCurrencyCode(remotePrefs.currencyCode)
        prefsRepo.setThemeMode(remotePrefs.themeMode)
        prefsRepo.setMapLayer(remotePrefs.mapLayer)
    }

    suspend fun getTrackPointsOnce(tripId: String): List<RoutePoint> =
        dao.getTrackPoints(tripId).map { it.toDomain() }

    suspend fun getStopsForTripOnce(tripId: String): List<TripStop> =
        dao.getStopsForTrip(tripId).map { it.toDomain() }
}
