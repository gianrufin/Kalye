package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface KalyeDao {
    @Query("SELECT * FROM trips WHERE userId = :userId ORDER BY startTimeMillis DESC")
    fun observeTrips(userId: String): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE userId = :userId AND activeFlag = 1 LIMIT 1")
    fun observeActiveTrip(userId: String): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE id = :tripId LIMIT 1")
    fun observeTrip(tripId: String): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE id = :tripId LIMIT 1")
    suspend fun getTripById(tripId: String): TripEntity?

    @Query("SELECT * FROM trips WHERE userId = :userId AND activeFlag = 1 LIMIT 1")
    suspend fun getActiveTrip(userId: String): TripEntity?

    @Query("SELECT * FROM trips WHERE userId = :userId AND startTimeMillis >= :dayStartMillis ORDER BY startTimeMillis DESC")
    suspend fun getTodayTripsSnapshot(userId: String, dayStartMillis: Long): List<TripEntity>

    @Query("SELECT * FROM trips WHERE userId = :userId AND syncedFlag = 0 AND activeFlag = 0")
    suspend fun getUnsyncedCompletedTrips(userId: String): List<TripEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrip(trip: TripEntity)

    @Query("DELETE FROM trips WHERE id = :tripId")
    suspend fun deleteTripById(tripId: String)

    @Query("SELECT * FROM track_points WHERE tripId = :tripId ORDER BY timestampMillis ASC")
    fun observeTrackPoints(tripId: String): Flow<List<TrackPointEntity>>

    @Query("SELECT * FROM track_points WHERE tripId = :tripId ORDER BY timestampMillis ASC")
    suspend fun getTrackPoints(tripId: String): List<TrackPointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackPoint(point: TrackPointEntity)

    @Query("DELETE FROM track_points WHERE tripId = :tripId")
    suspend fun deleteTrackPointsForTrip(tripId: String)

    @Query("SELECT * FROM trip_stops WHERE userId = :userId ORDER BY timestampMillis DESC")
    fun observeAllStops(userId: String): Flow<List<TripStopEntity>>

    @Query("SELECT * FROM trip_stops WHERE tripId = :tripId ORDER BY timestampMillis ASC")
    fun observeStopsForTrip(tripId: String): Flow<List<TripStopEntity>>

    @Query("SELECT * FROM trip_stops WHERE tripId = :tripId ORDER BY timestampMillis ASC")
    suspend fun getStopsForTrip(tripId: String): List<TripStopEntity>

    @Query("SELECT * FROM trip_stops WHERE userId = :userId AND syncedFlag = 0")
    suspend fun getUnsyncedStops(userId: String): List<TripStopEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStop(stop: TripStopEntity)

    @Query("DELETE FROM trip_stops WHERE id = :stopId")
    suspend fun deleteStopById(stopId: String)

    @Query("DELETE FROM trip_stops WHERE tripId = :tripId")
    suspend fun deleteStopsForTrip(tripId: String)

    @Query("SELECT * FROM tag_categories WHERE userId = :userId ORDER BY defaultTag DESC, name ASC")
    fun observeCategories(userId: String): Flow<List<TagCategoryEntity>>

    @Query("SELECT * FROM tag_categories WHERE userId = :userId")
    suspend fun getCategories(userId: String): List<TagCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: TagCategoryEntity)

    @Query("DELETE FROM tag_categories WHERE id = :categoryId")
    suspend fun deleteCategoryById(categoryId: String)

    @Query("SELECT * FROM offline_regions ORDER BY downloadedAtMillis DESC")
    fun observeOfflineRegions(): Flow<List<OfflineMapRegionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOfflineRegion(region: OfflineMapRegionEntity)

    @Query("DELETE FROM offline_regions WHERE id = :regionId")
    suspend fun deleteOfflineRegionById(regionId: String)
}
