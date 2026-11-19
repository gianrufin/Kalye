package com.example.location

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Looper
import androidx.core.location.LocationCompat
import com.example.data.model.AltitudeStabilizer
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
    private val appContext = context.applicationContext
    private val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    @SuppressLint("MissingPermission")
    fun observeHighAccuracyLocation(): Flow<LiveTelemetry> = callbackFlow {
        // Piggyback cleanly alongside Google Maps / Android Auto without min-distance gating
        // so stationary traffic auto-pause evaluates accurately at 25s.
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1200L)
            .setWaitForAccurateLocation(false)
            .build()

        val stabilizer = AltitudeStabilizer()

        // Optional zero-permission barometer fusion for smooth real-time climb/descent tracking
        val pressureSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)
        var latestPressureHpa: Float? = null
        var baroRefPressureHpa: Float? = null
        var baroRefAltitudeMeters: Double? = null

        val baroListener = if (pressureSensor != null) {
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val hpa = event.values.firstOrNull() ?: return
                    if (hpa in 300f..1100f) {
                        latestPressureHpa = hpa
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
        } else null

        if (pressureSensor != null && baroListener != null) {
            sensorManager?.registerListener(baroListener, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }

        fun resolveAccurateAltitude(loc: Location): Pair<Double, Float> {
            val hasMsl = LocationCompat.hasMslAltitude(loc)
            val hasRawAlt = loc.hasAltitude()
            val vAcc = if (LocationCompat.hasVerticalAccuracy(loc)) {
                LocationCompat.getVerticalAccuracyMeters(loc).coerceAtLeast(1f)
            } else {
                12f
            }

            if (!hasMsl && !hasRawAlt) {
                return 0.0 to vAcc
            }

            val rawAltitude = if (hasMsl) {
                LocationCompat.getMslAltitudeMeters(loc)
            } else {
                loc.altitude
            }
            val timestamp = loc.time.takeIf { it > 0L } ?: System.currentTimeMillis()
            val gpsSmoothed = stabilizer.process(rawAltitude, vAcc, timestamp) ?: rawAltitude

            val curPressure = latestPressureHpa
            if (curPressure != null) {
                val refP = baroRefPressureHpa
                val refAlt = baroRefAltitudeMeters
                if (refP == null || refAlt == null) {
                    if (vAcc <= 20f) {
                        baroRefPressureHpa = curPressure
                        baroRefAltitudeMeters = gpsSmoothed
                    }
                } else {
                    val baroAltAbsCur = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, curPressure).toDouble()
                    val baroAltAbsRef = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, refP).toDouble()
                    val baroEstimate = refAlt + (baroAltAbsCur - baroAltAbsRef)
                    // Drift-correct barometer slowly toward high-confidence GPS MSL fixes
                    if (vAcc <= 15f) {
                        val correctionAlpha = if (vAcc <= 6f) 0.16 else 0.08
                        baroRefAltitudeMeters = refAlt + correctionAlpha * (gpsSmoothed - baroEstimate)
                    }
                    val blended = if (vAcc <= 4f) {
                        0.45 * baroEstimate + 0.55 * gpsSmoothed
                    } else {
                        0.72 * baroEstimate + 0.28 * gpsSmoothed
                    }
                    return blended to minOf(vAcc, 4f)
                }
            }

            return gpsSmoothed to vAcc
        }

        fun Location.toAccurateRoutePoint(): RoutePoint {
            val (alt, vAcc) = resolveAccurateAltitude(this)
            return RoutePoint(
                latitude = latitude,
                longitude = longitude,
                altitudeMeters = alt,
                accuracyMeters = if (hasAccuracy()) accuracy else 10f,
                speedMps = if (hasSpeed()) speed else 0f,
                bearing = if (hasBearing()) bearing else 0f,
                timestampMillis = time.takeIf { it > 0L } ?: System.currentTimeMillis(),
                verticalAccuracyMeters = vAcc
            )
        }

        // Seed with last known location immediately if available
        fusedClient.lastLocation.addOnSuccessListener { last: Location? ->
            if (last != null) {
                trySend(
                    LiveTelemetry(
                        point = last.toAccurateRoutePoint(),
                        status = if (last.accuracy <= 15f) GpsSignalStatus.LOCKED_HIGH else GpsSignalStatus.LOCKED_FAIR
                    )
                )
            }
        }

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val status = when {
                    loc.accuracy <= 12f -> GpsSignalStatus.LOCKED_HIGH
                    loc.accuracy <= 45f -> GpsSignalStatus.LOCKED_FAIR
                    else -> GpsSignalStatus.SEARCHING
                }
                trySend(
                    LiveTelemetry(
                        point = loc.toAccurateRoutePoint(),
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
            if (baroListener != null) {
                sensorManager?.unregisterListener(baroListener)
            }
        }
    }
}
