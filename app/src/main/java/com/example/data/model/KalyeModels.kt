package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class DistanceUnit(val label: String, val shortLabel: String) {
    KM("Kilometers", "km"),
    MI("Miles", "mi");

    companion object {
        fun safeFrom(raw: String?): DistanceUnit =
            entries.firstOrNull { it.name == raw } ?: KM
    }
}

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark (OLED)");

    companion object {
        fun safeFrom(raw: String?): ThemeMode =
            entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}

enum class MapLayerType(val label: String) {
    TOPO("Topo"),
    STREET("Street"),
    OFFLINE("Offline");

    companion object {
        fun safeFrom(raw: String?): MapLayerType =
            entries.firstOrNull { it.name == raw } ?: TOPO
    }
}

enum class StopType(val label: String) {
    FUEL("Fuel Stop"),
    FERRY("Ferry Fee"),
    CAMP("Car Camp"),
    STOP_OVER("Stop-over"),
    TOLL("Toll / Permit"),
    REPAIR("Repair / Gear");

    companion object {
        fun safeFrom(raw: String?): StopType =
            entries.firstOrNull { it.name == raw } ?: STOP_OVER
    }
}

enum class ShareCardTheme(val label: String) {
    TOPO_DARK("Topo Basalt"),
    DESERT_SAND("Desert Sand"),
    ALPINE_PINE("Alpine Pine"),
    TRANSPARENT_GLASS("Transparent Overlay")
}

data class CurrencyOption(
    val code: String,
    val symbol: String,
    val name: String
)

object CurrencyCatalog {
    val supported = listOf(
        CurrencyOption("PHP", "₱", "Philippine Peso"),
        CurrencyOption("USD", "$", "US Dollar"),
        CurrencyOption("EUR", "€", "Euro"),
        CurrencyOption("GBP", "£", "British Pound"),
        CurrencyOption("AUD", "A$", "Australian Dollar"),
        CurrencyOption("JPY", "¥", "Japanese Yen"),
        CurrencyOption("SGD", "S$", "Singapore Dollar"),
        CurrencyOption("MYR", "RM", "Malaysian Ringgit"),
        CurrencyOption("CAD", "C$", "Canadian Dollar")
    )

    fun symbolFor(code: String): String =
        supported.firstOrNull { it.code == code }?.symbol ?: code
}

data class UserPreferences(
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
    val currencyCode: String = "PHP",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val mapLayer: MapLayerType = MapLayerType.TOPO,
    val activeTripId: String? = null
)

data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val accuracyMeters: Float = 5f,
    val speedMps: Float = 0f,
    val bearing: Float = 0f,
    val timestampMillis: Long = System.currentTimeMillis()
)

data class TripSummary(
    val id: String,
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
    val isActive: Boolean = false,
    val isPaused: Boolean = false,
    val isSynced: Boolean = false
) {
    val totalCost: Double
        get() = totalFuelCost + totalFerryCost + totalOtherCost

    val durationMillis: Long
        get() = (endTimeMillis - startTimeMillis).coerceAtLeast(0L)
}

data class TripStop(
    val id: String,
    val userId: String,
    val tripId: String,
    val stopType: StopType,
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
    val isSynced: Boolean = false
)

data class TagCategory(
    val id: String,
    val userId: String,
    val name: String,
    val colorHex: Long,
    val iconKey: String,
    val defaultTag: Boolean = false
)

data class OfflineRegion(
    val id: String,
    val name: String,
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
    val minZoom: Int,
    val maxZoom: Int,
    val layerType: MapLayerType,
    val tileCount: Int,
    val sizeBytes: Long,
    val downloadedAtMillis: Long
)

// --- Firestore DTOs (Code-to-Rules Parity) ---

@Keep
@IgnoreExtraProperties
data class FirestoreTrip(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val categoryTag: String = "Overland",
    val notes: String = "",
    val startTimeMillis: Long = 0L,
    val endTimeMillis: Long = 0L,
    val distanceMeters: Double = 0.0,
    val elevationGainMeters: Double = 0.0,
    val elevationLossMeters: Double = 0.0,
    val maxElevationMeters: Double = 0.0,
    val minElevationMeters: Double = 0.0,
    val maxSpeedKmh: Double = 0.0,
    val totalFuelCost: Double = 0.0,
    val totalFerryCost: Double = 0.0,
    val totalOtherCost: Double = 0.0,
    val totalFuelLiters: Double = 0.0,
    val currencyCode: String = "PHP",
    val routePolyline: String = "",
    val pointCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FirestoreStop(
    @DocumentId val id: String = "",
    val userId: String = "",
    val tripId: String = "",
    val stopType: String = "STOP_OVER",
    val title: String = "",
    val note: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val elevationMeters: Double = 0.0,
    val costAmount: Double = 0.0,
    val fuelLiters: Double = 0.0,
    val pricePerLiter: Double = 0.0,
    val currencyCode: String = "PHP",
    val timestampMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FirestoreCategory(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val colorHex: Long = 0xFFB84A27L,
    val iconKey: String = "terrain",
    val defaultTag: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FirestorePreferences(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String? = null,
    val distanceUnit: String = "KM",
    val currencyCode: String = "PHP",
    val themeMode: String = "SYSTEM",
    val mapLayer: String = "TOPO",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun TripSummary.toCreateMap(uid: String): Map<String, Any> = mapOf(
    "userId" to uid,
    "title" to title.take(120).ifBlank { "Overland Ride" },
    "categoryTag" to categoryTag.take(40).ifBlank { "Overland" },
    "notes" to notes.take(1000),
    "startTimeMillis" to startTimeMillis.coerceAtLeast(0L),
    "endTimeMillis" to endTimeMillis.coerceAtLeast(0L),
    "distanceMeters" to distanceMeters.coerceAtLeast(0.0),
    "elevationGainMeters" to elevationGainMeters.coerceAtLeast(0.0),
    "elevationLossMeters" to elevationLossMeters.coerceAtLeast(0.0),
    "maxElevationMeters" to maxElevationMeters,
    "minElevationMeters" to minElevationMeters,
    "maxSpeedKmh" to maxSpeedKmh.coerceAtLeast(0.0),
    "totalFuelCost" to totalFuelCost.coerceAtLeast(0.0),
    "totalFerryCost" to totalFerryCost.coerceAtLeast(0.0),
    "totalOtherCost" to totalOtherCost.coerceAtLeast(0.0),
    "totalFuelLiters" to totalFuelLiters.coerceAtLeast(0.0),
    "currencyCode" to currencyCode.take(6).ifBlank { "PHP" },
    "routePolyline" to routePolyline.take(150000),
    "pointCount" to pointCount.coerceAtLeast(0).toLong(),
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp()
)

fun TripSummary.toUpdateMap(): Map<String, Any> = mapOf(
    "title" to title.take(120).ifBlank { "Overland Ride" },
    "categoryTag" to categoryTag.take(40).ifBlank { "Overland" },
    "notes" to notes.take(1000),
    "endTimeMillis" to endTimeMillis.coerceAtLeast(0L),
    "distanceMeters" to distanceMeters.coerceAtLeast(0.0),
    "elevationGainMeters" to elevationGainMeters.coerceAtLeast(0.0),
    "elevationLossMeters" to elevationLossMeters.coerceAtLeast(0.0),
    "maxElevationMeters" to maxElevationMeters,
    "minElevationMeters" to minElevationMeters,
    "maxSpeedKmh" to maxSpeedKmh.coerceAtLeast(0.0),
    "totalFuelCost" to totalFuelCost.coerceAtLeast(0.0),
    "totalFerryCost" to totalFerryCost.coerceAtLeast(0.0),
    "totalOtherCost" to totalOtherCost.coerceAtLeast(0.0),
    "totalFuelLiters" to totalFuelLiters.coerceAtLeast(0.0),
    "currencyCode" to currencyCode.take(6).ifBlank { "PHP" },
    "routePolyline" to routePolyline.take(150000),
    "pointCount" to pointCount.coerceAtLeast(0).toLong(),
    "updatedAt" to FieldValue.serverTimestamp()
)

fun TripStop.toCreateMap(uid: String): Map<String, Any> = mapOf(
    "userId" to uid,
    "tripId" to tripId,
    "stopType" to stopType.name,
    "title" to title.take(120).ifBlank { stopType.label },
    "note" to note.take(500),
    "latitude" to latitude.coerceIn(-90.0, 90.0),
    "longitude" to longitude.coerceIn(-180.0, 180.0),
    "elevationMeters" to elevationMeters,
    "costAmount" to costAmount.coerceAtLeast(0.0),
    "fuelLiters" to fuelLiters.coerceAtLeast(0.0),
    "pricePerLiter" to pricePerLiter.coerceAtLeast(0.0),
    "currencyCode" to currencyCode.take(6).ifBlank { "PHP" },
    "timestampMillis" to timestampMillis.coerceAtLeast(0L),
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp()
)

fun TripStop.toUpdateMap(): Map<String, Any> = mapOf(
    "stopType" to stopType.name,
    "title" to title.take(120).ifBlank { stopType.label },
    "note" to note.take(500),
    "latitude" to latitude.coerceIn(-90.0, 90.0),
    "longitude" to longitude.coerceIn(-180.0, 180.0),
    "elevationMeters" to elevationMeters,
    "costAmount" to costAmount.coerceAtLeast(0.0),
    "fuelLiters" to fuelLiters.coerceAtLeast(0.0),
    "pricePerLiter" to pricePerLiter.coerceAtLeast(0.0),
    "currencyCode" to currencyCode.take(6).ifBlank { "PHP" },
    "timestampMillis" to timestampMillis.coerceAtLeast(0L),
    "updatedAt" to FieldValue.serverTimestamp()
)

object GeoUtils {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    fun encodeRoutePoints(points: List<RoutePoint>, maxSampled: Int = 500): String {
        if (points.isEmpty()) return ""
        val sampled = if (points.size <= maxSampled) {
            points
        } else {
            val step = (points.size - 1).toDouble() / (maxSampled - 1)
            (0 until maxSampled).map { i -> points[(i * step).roundToInt().coerceIn(0, points.lastIndex)] }
        }
        return sampled.joinToString(";") { p ->
            String.format(Locale.US, "%.5f,%.5f,%.1f", p.latitude, p.longitude, p.altitudeMeters)
        }
    }

    fun decodeRoutePoints(encoded: String): List<RoutePoint> {
        if (encoded.isBlank()) return emptyList()
        return encoded.split(";").mapNotNull { token ->
            val parts = token.split(",")
            if (parts.size >= 2) {
                val lat = parts[0].toDoubleOrNull()
                val lon = parts[1].toDoubleOrNull()
                val ele = parts.getOrNull(2)?.toDoubleOrNull() ?: 0.0
                if (lat != null && lon != null) {
                    RoutePoint(latitude = lat, longitude = lon, altitudeMeters = ele)
                } else null
            } else null
        }
    }

    fun formatDistance(meters: Double, unit: DistanceUnit): String {
        val value = when (unit) {
            DistanceUnit.KM -> meters / 1000.0
            DistanceUnit.MI -> meters / 1609.344
        }
        return if (value >= 100) {
            String.format(Locale.US, "%.1f %s", value, unit.shortLabel)
        } else {
            String.format(Locale.US, "%.2f %s", value, unit.shortLabel)
        }
    }

    fun distanceNumber(meters: Double, unit: DistanceUnit): String {
        val value = when (unit) {
            DistanceUnit.KM -> meters / 1000.0
            DistanceUnit.MI -> meters / 1609.344
        }
        return if (value >= 100) {
            String.format(Locale.US, "%.1f", value)
        } else {
            String.format(Locale.US, "%.2f", value)
        }
    }

    fun formatElevation(meters: Double, unit: DistanceUnit): String {
        return when (unit) {
            DistanceUnit.KM -> String.format(Locale.US, "%.0f m", meters)
            DistanceUnit.MI -> String.format(Locale.US, "%.0f ft", meters * 3.28084)
        }
    }

    fun formatSpeed(kmh: Double, unit: DistanceUnit): String {
        return when (unit) {
            DistanceUnit.KM -> String.format(Locale.US, "%.0f km/h", kmh)
            DistanceUnit.MI -> String.format(Locale.US, "%.0f mph", kmh * 0.621371)
        }
    }

    fun formatCurrency(amount: Double, currencyCode: String): String {
        val symbol = CurrencyCatalog.symbolFor(currencyCode)
        return String.format(Locale.US, "%s%,.2f", symbol, amount)
    }

    fun formatDuration(durationMillis: Long): String {
        val totalSeconds = (durationMillis / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            String.format(Locale.US, "%dh %02dm", hours, minutes)
        } else {
            String.format(Locale.US, "%02dm %02ds", minutes, seconds)
        }
    }
}
