package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.model.DistanceUnit
import com.example.data.model.FirestoreCategory
import com.example.data.model.FirestorePreferences
import com.example.data.model.FirestoreStop
import com.example.data.model.FirestoreTrip
import com.example.data.model.FirestoreVehicle
import com.example.data.model.MapLayerType
import com.example.data.model.StopType
import com.example.data.model.TagCategory
import com.example.data.model.ThemeMode
import com.example.data.model.TripStop
import com.example.data.model.TripSummary
import com.example.data.model.UserPreferences
import com.example.data.model.VehicleBodyStyle
import com.example.data.model.VehicleProfile
import com.example.data.model.toCreateMap
import com.example.data.model.toUpdateMap
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.tasks.await

class KalyeFirestoreDataSource(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeTrips(userId: String): Flow<List<TripSummary>> = flow {
        require(userId.isNotBlank()) { "userId must not be blank" }
        val path = "users/$userId/trips"
        emitAll(
            db.collection("users").document(userId).collection("trips")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FirestoreTrip::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).map { dto ->
                        TripSummary(
                            id = dto.id,
                            userId = dto.userId.ifBlank { userId },
                            title = dto.title,
                            categoryTag = dto.categoryTag,
                            notes = dto.notes,
                            startTimeMillis = dto.startTimeMillis,
                            endTimeMillis = dto.endTimeMillis,
                            distanceMeters = dto.distanceMeters,
                            elevationGainMeters = dto.elevationGainMeters,
                            elevationLossMeters = dto.elevationLossMeters,
                            maxElevationMeters = dto.maxElevationMeters,
                            minElevationMeters = dto.minElevationMeters,
                            maxSpeedKmh = dto.maxSpeedKmh,
                            totalFuelCost = dto.totalFuelCost,
                            totalFerryCost = dto.totalFerryCost,
                            totalOtherCost = dto.totalOtherCost,
                            totalFuelLiters = dto.totalFuelLiters,
                            currencyCode = dto.currencyCode,
                            routePolyline = dto.routePolyline,
                            pointCount = dto.pointCount.toInt(),
                            vehicleId = dto.vehicleId,
                            vehicleName = dto.vehicleName,
                            movingDurationMillis = dto.movingDurationMillis,
                            isActive = false,
                            isPaused = false,
                            isSynced = true
                        )
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeVehicles(userId: String): Flow<List<VehicleProfile>> = flow {
        require(userId.isNotBlank()) { "userId must not be blank" }
        val path = "users/$userId/vehicles"
        emitAll(
            db.collection("users").document(userId).collection("vehicles")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FirestoreVehicle::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).map { dto ->
                        VehicleProfile(
                            id = dto.id,
                            nickname = dto.nickname,
                            makeModel = dto.makeModel,
                            bodyStyle = VehicleBodyStyle.safeFrom(dto.bodyStyle),
                            drivetrain = dto.drivetrain,
                            fuelType = dto.fuelType,
                            accentColorHex = dto.accentColorHex,
                            startingOdometerKm = dto.startingOdometerKm
                        )
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeStops(userId: String): Flow<List<TripStop>> = flow {
        require(userId.isNotBlank()) { "userId must not be blank" }
        val path = "users/$userId/stops"
        emitAll(
            db.collection("users").document(userId).collection("stops")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FirestoreStop::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).map { dto ->
                        TripStop(
                            id = dto.id,
                            userId = dto.userId.ifBlank { userId },
                            tripId = dto.tripId,
                            stopType = StopType.safeFrom(dto.stopType),
                            title = dto.title,
                            note = dto.note,
                            latitude = dto.latitude,
                            longitude = dto.longitude,
                            elevationMeters = dto.elevationMeters,
                            costAmount = dto.costAmount,
                            fuelLiters = dto.fuelLiters,
                            pricePerLiter = dto.pricePerLiter,
                            currencyCode = dto.currencyCode,
                            timestampMillis = dto.timestampMillis,
                            isSynced = true
                        )
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeCategories(userId: String): Flow<List<TagCategory>> = flow {
        require(userId.isNotBlank()) { "userId must not be blank" }
        val path = "users/$userId/categories"
        emitAll(
            db.collection("users").document(userId).collection("categories")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FirestoreCategory::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).map { dto ->
                        TagCategory(
                            id = dto.id,
                            userId = dto.userId.ifBlank { userId },
                            name = dto.name,
                            colorHex = dto.colorHex,
                            iconKey = dto.iconKey,
                            defaultTag = dto.defaultTag
                        )
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun upsertTrip(trip: TripSummary): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/trips/${trip.id}"
        return try {
            val uid = requireUserId()
            require(trip.id.isNotBlank()) { "Trip ID must not be blank" }
            val docRef = db.collection("users").document(uid).collection("trips").document(trip.id)
            docPath = docRef.path
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(trip.toUpdateMap()).await()
            } else {
                docRef.set(trip.toCreateMap(uid)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteTrip(tripId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/trips/$tripId"
        return try {
            val uid = requireUserId()
            require(tripId.isNotBlank()) { "Trip ID must not be blank" }
            val docRef = db.collection("users").document(uid).collection("trips").document(tripId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun upsertStop(stop: TripStop): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/stops/${stop.id}"
        return try {
            val uid = requireUserId()
            require(stop.id.isNotBlank()) { "Stop ID must not be blank" }
            val docRef = db.collection("users").document(uid).collection("stops").document(stop.id)
            docPath = docRef.path
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(stop.toUpdateMap()).await()
            } else {
                docRef.set(stop.toCreateMap(uid)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteStop(stopId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/stops/$stopId"
        return try {
            val uid = requireUserId()
            require(stopId.isNotBlank()) { "Stop ID must not be blank" }
            val docRef = db.collection("users").document(uid).collection("stops").document(stopId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun upsertCategory(category: TagCategory): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/categories/${category.id}"
        return try {
            val uid = requireUserId()
            require(category.id.isNotBlank()) { "Category ID must not be blank" }
            val docRef = db.collection("users").document(uid).collection("categories").document(category.id)
            docPath = docRef.path
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "name" to category.name.take(40).ifBlank { "Overland" },
                        "colorHex" to category.colorHex,
                        "iconKey" to category.iconKey.take(30).ifBlank { "terrain" },
                        "defaultTag" to category.defaultTag,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "name" to category.name.take(40).ifBlank { "Overland" },
                        "colorHex" to category.colorHex,
                        "iconKey" to category.iconKey.take(30).ifBlank { "terrain" },
                        "defaultTag" to category.defaultTag,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCategory(categoryId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/categories/$categoryId"
        return try {
            val uid = requireUserId()
            require(categoryId.isNotBlank()) { "Category ID must not be blank" }
            val docRef = db.collection("users").document(uid).collection("categories").document(categoryId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun upsertVehicle(vehicle: VehicleProfile): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/vehicles/${vehicle.id}"
        return try {
            val uid = requireUserId()
            require(vehicle.id.isNotBlank()) { "Vehicle ID must not be blank" }
            val docRef = db.collection("users").document(uid).collection("vehicles").document(vehicle.id)
            docPath = docRef.path
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(vehicle.toUpdateMap()).await()
            } else {
                docRef.set(vehicle.toCreateMap(uid)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteVehicle(vehicleId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/vehicles/$vehicleId"
        return try {
            val uid = requireUserId()
            require(vehicleId.isNotBlank()) { "Vehicle ID must not be blank" }
            val docRef = db.collection("users").document(uid).collection("vehicles").document(vehicleId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun savePreferences(prefs: UserPreferences, displayName: String?): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/settings/preferences"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("settings").document("preferences")
            docPath = docRef.path
            val existing = docRef.get().await()
            if (existing.exists()) {
                val updateMap = mapOf(
                    "displayName" to displayName?.take(100),
                    "distanceUnit" to prefs.distanceUnit.name,
                    "currencyCode" to prefs.currencyCode.take(6),
                    "themeMode" to prefs.themeMode.name,
                    "mapLayer" to prefs.mapLayer.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                docRef.update(updateMap).await()
            } else {
                val createMap = mapOf(
                    "userId" to uid,
                    "displayName" to displayName?.take(100),
                    "distanceUnit" to prefs.distanceUnit.name,
                    "currencyCode" to prefs.currencyCode.take(6),
                    "themeMode" to prefs.themeMode.name,
                    "mapLayer" to prefs.mapLayer.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                docRef.set(createMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun fetchPreferences(): UserPreferences? {
        val uid = auth.currentUser?.uid ?: return null
        val docRef = db.collection("users").document(uid).collection("settings").document("preferences")
        return try {
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) return null
            val dto = snapshot.toObject(
                FirestorePreferences::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return null
            UserPreferences(
                distanceUnit = DistanceUnit.safeFrom(dto.distanceUnit),
                currencyCode = dto.currencyCode,
                themeMode = ThemeMode.safeFrom(dto.themeMode),
                mapLayer = MapLayerType.safeFrom(dto.mapLayer)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            null
        }
    }
}
