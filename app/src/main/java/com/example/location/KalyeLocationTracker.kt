package com.example.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.example.data.model.RoutePoint
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class GpsSignalStatus {
    PERMISSION_NEEDED,
    SEARCHING,
    LOCKED_HIGH,
    LOCKED_FAIR
}

data class LiveTelemetry(
    val point: RoutePoint? = null,
    val status: GpsSignalStatus = GpsSignalStatus.SEARCHING
)

class KalyeLocationTracker(context: Context) {
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    @SuppressLint("MissingPermission")
    fun observeHighAccuracyLocation(): Flow<LiveTelemetry> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1500L)
            .setMinUpdateIntervalMillis(1000L)
            .setMinUpdateDistanceMeters(2.0f)
            .setWaitForAccurateLocation(false)
            .build()

        // Seed with last known location immediately if available
        fusedClient.lastLocation.addOnSuccessListener { last: Location? ->
            if (last != null) {
                trySend(
                    LiveTelemetry(
                        point = last.toRoutePoint(),
                        status = if (last.accuracy <= 15f) GpsSignalStatus.LOCKED_HIGH else GpsSignalStatus.LOCKED_FAIR
                    )
                )
            }
        }

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                // Reject wild outliers (> 50m accuracy) once locked
                val status = when {
                    loc.accuracy <= 12f -> GpsSignalStatus.LOCKED_HIGH
                    loc.accuracy <= 45f -> GpsSignalStatus.LOCKED_FAIR
                    else -> GpsSignalStatus.SEARCHING
                }
                trySend(
                    LiveTelemetry(
                        point = loc.toRoutePoint(),
                        status = status
                    )
                )
            }
        }

        try {
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            trySend(LiveTelemetry(point = null, status = GpsSignalStatus.PERMISSION_NEEDED))
        }

        awaitClose {
            fusedClient.removeLocationUpdates(callback)
        }
    }

    private fun Location.toRoutePoint(): RoutePoint = RoutePoint(
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = if (hasAltitude()) altitude else 0.0,
        accuracyMeters = if (hasAccuracy()) accuracy else 10f,
        speedMps = if (hasSpeed()) speed else 0f,
        bearing = if (hasBearing()) bearing else 0f,
        timestampMillis = time.takeIf { it > 0L } ?: System.currentTimeMillis()
    )
}
