package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.MapLayerType
import com.example.data.model.OfflineRegion
import com.example.data.model.RoutePoint
import com.example.data.model.StopType
import com.example.data.model.TagCategory
import com.example.data.model.TripStop
import com.example.data.model.TripSummary

@Entity(tableName = "trips", indices = [Index("userId")])
data class TripEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val categoryTag: String,
    val notes: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val distanceMeters: Double,
    val elevationGainMeters: Double,
    val elevationLossMeters: Double,
    val maxElevationMeters: Double,
    val minElevationMeters: Double,
    val maxSpeedKmh: Double,
    val totalFuelCost: Double,
    val totalFerryCost: Double,
    val totalOtherCost: Double,
    val totalFuelLiters: Double,
    val currencyCode: String,
    val routePolyline: String,
    val pointCount: Int,
    val vehicleId: String = "",
    val vehicleName: String = "",
    val movingDurationMillis: Long = 0L,
    val autoPausedFlag: Boolean = false,
    val lastMovementTimeMillis: Long = 0L,
    val activeFlag: Boolean,
    val pausedFlag: Boolean,
    val syncedFlag: Boolean,
    val updatedAtMillis: Long
) {
    fun toDomain(): TripSummary = TripSummary(
        id = id,
        userId = userId,
        title = title,
        categoryTag = categoryTag,
        notes = notes,
        startTimeMillis = startTimeMillis,
        endTimeMillis = endTimeMillis,
        distanceMeters = distanceMeters,
        elevationGainMeters = elevationGainMeters,
        elevationLossMeters = elevationLossMeters,
        maxElevationMeters = maxElevationMeters,
        minElevationMeters = minElevationMeters,
        maxSpeedKmh = maxSpeedKmh,
        totalFuelCost = totalFuelCost,
        totalFerryCost = totalFerryCost,
        totalOtherCost = totalOtherCost,
        totalFuelLiters = totalFuelLiters,
        currencyCode = currencyCode,
        routePolyline = routePolyline,
        pointCount = pointCount,
        vehicleId = vehicleId,
        vehicleName = vehicleName,
        movingDurationMillis = movingDurationMillis,
        isActive = activeFlag,
        isPaused = pausedFlag,
        isAutoPaused = autoPausedFlag,
        isSynced = syncedFlag
    )
}

@Entity(
    tableName = "track_points",
    indices = [Index("tripId")]
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0L,
    val tripId: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val accuracyMeters: Float,
    val speedMps: Float,
    val bearing: Float,
    val timestampMillis: Long
) {
    fun toDomain(): RoutePoint = RoutePoint(
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = altitudeMeters,
        accuracyMeters = accuracyMeters,
        speedMps = speedMps,
        bearing = bearing,
        timestampMillis = timestampMillis
    )
}

@Entity(
    tableName = "trip_stops",
    indices = [Index("userId"), Index("tripId")]
)
data class TripStopEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val tripId: String,
    val stopType: String,
    val title: String,
    val note: String,
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double,
    val costAmount: Double,
    val fuelLiters: Double,
    val pricePerLiter: Double,
    val currencyCode: String,
    val timestampMillis: Long,
    val syncedFlag: Boolean
) {
    fun toDomain(): TripStop = TripStop(
        id = id,
        userId = userId,
        tripId = tripId,
        stopType = StopType.safeFrom(stopType),
        title = title,
        note = note,
        latitude = latitude,
        longitude = longitude,
        elevationMeters = elevationMeters,
        costAmount = costAmount,
        fuelLiters = fuelLiters,
        pricePerLiter = pricePerLiter,
        currencyCode = currencyCode,
        timestampMillis = timestampMillis,
        isSynced = syncedFlag
    )
}

@Entity(tableName = "tag_categories", indices = [Index("userId")])
data class TagCategoryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val colorHex: Long,
    val iconKey: String,
    val defaultTag: Boolean,
    val syncedFlag: Boolean
) {
    fun toDomain(): TagCategory = TagCategory(
        id = id,
        userId = userId,
        name = name,
        colorHex = colorHex,
        iconKey = iconKey,
        defaultTag = defaultTag
    )
}

@Entity(tableName = "offline_regions")
data class OfflineMapRegionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
    val minZoom: Int,
    val maxZoom: Int,
    val layerType: String,
    val tileCount: Int,
    val sizeBytes: Long,
    val downloadedAtMillis: Long
) {
    fun toDomain(): OfflineRegion = OfflineRegion(
        id = id,
        name = name,
        minLat = minLat,
        maxLat = maxLat,
        minLon = minLon,
        maxLon = maxLon,
        minZoom = minZoom,
        maxZoom = maxZoom,
        layerType = MapLayerType.safeFrom(layerType),
        tileCount = tileCount,
        sizeBytes = sizeBytes,
        downloadedAtMillis = downloadedAtMillis
    )
}
