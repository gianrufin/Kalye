package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.serialization.Serializable
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
    LIGHT("Light"),
    DARK("Dark");

    companion object {
        fun safeFrom(raw: String?): ThemeMode = when (raw) {
            DARK.name -> DARK
            else -> LIGHT
        }
    }
}

enum class MapLayerType(val label: String) {
    TOPO("Minimal Street"),
    STREET("Minimal Street"),
    OFFLINE("Offline Only");

    companion object {
        fun safeFrom(raw: String?): MapLayerType = when (raw) {
            OFFLINE.name -> OFFLINE
            else -> STREET
        }
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

enum class ShareCardLayout(val label: String) {
    CLASSIC_STACK("Centered"),
    CORNER_EDITORIAL("Editorial"),
    COMPACT_BAR("Bottom Strip"),
    ROUTE_HERO("Trail Focus")
}

enum class ShareCardInkStyle(val label: String, val isTransparent: Boolean) {
    WHITE_TRANSPARENT("White Sticker", true),
    DARK_TRANSPARENT("Dark Sticker", true),
    DARK_CARD("Smoked Card", false)
}

@Serializable
enum class VehicleBodyStyle(val label: String) {
    SUV_4X4("SUV / 4x4"),
    CROSSOVER("Crossover"),
    PICKUP("Pickup"),
    SEDAN("Sedan"),
    HATCHBACK("Hatchback"),
    COUPE("Coupe"),
    VAN("Van / Camper"),
    MOTO("Motorcycle");

    companion object {
        fun safeFrom(raw: String?): VehicleBodyStyle =
            entries.firstOrNull { it.name == raw } ?: SUV_4X4
    }
}

@Serializable
enum class VehicleRigGear(val label: String) {
    STOCK("Stock"),
    ROOF_RACK("Roof Rack"),
    EXPEDITION("Overland Kit"),
    RALLY_TRIM("Rally Trim");

    companion object {
        fun safeFrom(raw: String?): VehicleRigGear =
            entries.firstOrNull { it.name == raw } ?: STOCK
    }
}

@Serializable
enum class RigAvatarBackdrop(
    val label: String,
    val topHex: Long,
    val bottomHex: Long,
    val ridgeHex: Long
) {
    ALPINE_PINE("Alpine Pine", 0xFF143124L, 0xFF285C45L, 0xFF66C79AL),
    CANYON_EMBER("Canyon Dusk", 0xFF33180FL, 0xFFC86428L, 0xFFF3B169L),
    TOPO_CHARCOAL("Night Topo", 0xFF101412L, 0xFF242D28L, 0xFFE87842L),
    DESERT_DUNE("Dune Sand", 0xFF5D4623L, 0xFFC99838L, 0xFFF6E7C8L),
    FJORD_SLATE("Fjord Slate", 0xFF1A2836L, 0xFF425E75L, 0xFFB8D3E6L);

    companion object {
        fun safeFrom(raw: String?): RigAvatarBackdrop =
            entries.firstOrNull { it.name == raw } ?: ALPINE_PINE
    }
}

@Serializable
enum class RigAvatarFrame(val label: String) {
    COMPASS_RING("Compass Dial"),
    TRAIL_BORDER("Ember Ring"),
    CLEAN_DISC("Minimal Disc");

    companion object {
        fun safeFrom(raw: String?): RigAvatarFrame =
            entries.firstOrNull { it.name == raw } ?: COMPASS_RING
    }
}

@Serializable
data class RigAvatarSpec(
    val vehicleId: String? = null,
    val backdrop: RigAvatarBackdrop = RigAvatarBackdrop.ALPINE_PINE,
    val frame: RigAvatarFrame = RigAvatarFrame.COMPASS_RING,
    val callsign: String = ""
)

@Serializable
data class VehicleProfile(
    val id: String,
    val nickname: String,
    val makeModel: String,
    val bodyStyle: VehicleBodyStyle = VehicleBodyStyle.SUV_4X4,
    val drivetrain: String = "4x4",
    val fuelType: String = "Diesel",
    val accentColorHex: Long = 0xFF4B5D38L,
    val startingOdometerKm: Double = 0.0,
    val rigGear: VehicleRigGear = VehicleRigGear.STOCK
)

data class VehicleStats(
    val vehicle: VehicleProfile,
    val currentOdometerKm: Double,
    val tripCount: Int,
    val totalDistanceMeters: Double,
    val totalFuelLiters: Double,
    val totalFuelCost: Double,
    val averageKmPerLiter: Double?
)

object VehicleGarageDefaults {
    val presets: List<VehicleProfile> = emptyList()
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
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val mapLayer: MapLayerType = MapLayerType.STREET,
    val activeTripId: String? = null,
    val activeVehicleId: String? = null,
    val garageOnboardingDismissed: Boolean = false,
    val stealthModeEnabled: Boolean = false,
    val rigAvatar: RigAvatarSpec = RigAvatarSpec()
)

data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val accuracyMeters: Float = 5f,
    val speedMps: Float = 0f,
    val bearing: Float = 0f,
    val timestampMillis: Long = System.currentTimeMillis(),
    val verticalAccuracyMeters: Float = 10f
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
    val vehicleId: String = "",
    val vehicleName: String = "",
    val movingDurationMillis: Long = 0L,
    val isActive: Boolean = false,
    val isPaused: Boolean = false,
    val isAutoPaused: Boolean = false,
    val isSynced: Boolean = false
) {
    val totalCost: Double
        get() = totalFuelCost + totalFerryCost + totalOtherCost

    val durationMillis: Long
        get() = (endTimeMillis - startTimeMillis).coerceAtLeast(0L)

    val effectiveMovingMillis: Long
        get() = when {
            movingDurationMillis > 0L -> movingDurationMillis
            isActive -> 0L
            else -> durationMillis
        }
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
    val vehicleId: String = "",
    val vehicleName: String = "",
    val movingDurationMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FirestoreVehicle(
    @DocumentId val id: String = "",
    val userId: String = "",
    val nickname: String = "",
    val makeModel: String = "",
    val bodyStyle: String = "SUV_4X4",
    val drivetrain: String = "4x4",
    val fuelType: String = "Diesel",
    val accentColorHex: Long = 0xFF4B5D38L,
    val startingOdometerKm: Double = 0.0,
    val rigGear: String = "STOCK",
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
    val themeMode: String = "LIGHT",
    val mapLayer: String = "STREET",
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
    "vehicleId" to vehicleId.take(128),
    "vehicleName" to vehicleName.take(80),
    "movingDurationMillis" to movingDurationMillis.coerceAtLeast(0L),
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
    "vehicleId" to vehicleId.take(128),
    "vehicleName" to vehicleName.take(80),
    "movingDurationMillis" to movingDurationMillis.coerceAtLeast(0L),
    "updatedAt" to FieldValue.serverTimestamp()
)

fun VehicleProfile.toCreateMap(uid: String): Map<String, Any> = mapOf(
    "userId" to uid,
    "nickname" to nickname.trim().take(60).ifBlank { bodyStyle.label },
    "makeModel" to makeModel.trim().take(100).ifBlank { "${bodyStyle.label} • $drivetrain" },
    "bodyStyle" to bodyStyle.name,
    "drivetrain" to drivetrain.take(20).ifBlank { "4x4" },
    "fuelType" to fuelType.take(24).ifBlank { "Diesel" },
    "accentColorHex" to accentColorHex,
    "startingOdometerKm" to startingOdometerKm.coerceAtLeast(0.0),
    "rigGear" to rigGear.name,
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp()
)

fun VehicleProfile.toUpdateMap(): Map<String, Any> = mapOf(
    "nickname" to nickname.trim().take(60).ifBlank { bodyStyle.label },
    "makeModel" to makeModel.trim().take(100).ifBlank { "${bodyStyle.label} • $drivetrain" },
    "bodyStyle" to bodyStyle.name,
    "drivetrain" to drivetrain.take(20).ifBlank { "4x4" },
    "fuelType" to fuelType.take(24).ifBlank { "Diesel" },
    "accentColorHex" to accentColorHex,
    "startingOdometerKm" to startingOdometerKm.coerceAtLeast(0.0),
    "rigGear" to rigGear.name,
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

    fun formatMovingTime(durationMillis: Long): String {
        val totalSeconds = (durationMillis / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun computeLiveMovingMillis(
        trip: TripSummary,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        if (!trip.isActive) return trip.effectiveMovingMillis
        val base = trip.movingDurationMillis.coerceAtLeast(0L)
        if (trip.isPaused || trip.isAutoPaused) return base
        val anchor = maxOf(trip.endTimeMillis, trip.startTimeMillis)
        val elapsedSinceAnchor = (nowMillis - anchor).coerceIn(0L, 60_000L)
        return base + elapsedSinceAnchor
    }

    fun computeElevationStats(
        points: List<RoutePoint>,
        hysteresisMeters: Double = 2.5
    ): ElevationStats {
        if (points.isEmpty()) return ElevationStats()
        var anchor = points.first().altitudeMeters
        var minEle = anchor
        var maxEle = anchor
        var gain = 0.0
        var loss = 0.0
        for (i in 1 until points.size) {
            val alt = points[i].altitudeMeters
            if (alt < minEle) minEle = alt
            if (alt > maxEle) maxEle = alt
            val diff = alt - anchor
            if (diff >= hysteresisMeters) {
                gain += diff
                anchor = alt
            } else if (diff <= -hysteresisMeters) {
                loss += -diff
                anchor = alt
            }
        }
        return ElevationStats(
            gainMeters = gain,
            lossMeters = loss,
            minMeters = minEle,
            maxMeters = maxEle
        )
    }

    fun formatOdometer(km: Double, unit: DistanceUnit): String {
        val value = when (unit) {
            DistanceUnit.KM -> km
            DistanceUnit.MI -> km * 0.621371
        }
        return String.format(Locale.US, "%,.0f %s", value.coerceAtLeast(0.0), unit.shortLabel)
    }

    fun computeKmPerLiter(distanceMeters: Double, fuelLiters: Double): Double? {
        if (distanceMeters <= 0.0 || fuelLiters <= 0.0) return null
        return (distanceMeters / 1000.0) / fuelLiters
    }

    fun formatFuelEfficiency(distanceMeters: Double, fuelLiters: Double, unit: DistanceUnit): String? {
        val kmPerL = computeKmPerLiter(distanceMeters, fuelLiters) ?: return null
        return when (unit) {
            DistanceUnit.KM -> String.format(Locale.US, "%.1f km/L", kmPerL)
            DistanceUnit.MI -> String.format(Locale.US, "%.1f mpg", kmPerL * 2.3521458)
        }
    }

    fun formatCostPerDistance(
        totalCost: Double,
        distanceMeters: Double,
        currencyCode: String,
        unit: DistanceUnit
    ): String? {
        if (totalCost <= 0.0 || distanceMeters < 100.0) return null
        val distUnits = when (unit) {
            DistanceUnit.KM -> distanceMeters / 1000.0
            DistanceUnit.MI -> distanceMeters / 1609.344
        }
        if (distUnits <= 0.0) return null
        val symbol = CurrencyCatalog.symbolFor(currencyCode)
        return String.format(Locale.US, "%s%.2f/%s", symbol, totalCost / distUnits, unit.shortLabel)
    }
}

data class ElevationStats(
    val gainMeters: Double = 0.0,
    val lossMeters: Double = 0.0,
    val minMeters: Double = 0.0,
    val maxMeters: Double = 0.0
)

class AltitudeStabilizer {
    private val recentWindow = ArrayDeque<Double>()
    private var smoothedElevation: Double? = null
    private var lastAcceptedTimeMillis: Long = 0L

    fun reset() {
        recentWindow.clear()
        smoothedElevation = null
        lastAcceptedTimeMillis = 0L
    }

    fun process(
        rawMslMeters: Double,
        verticalAccuracyMeters: Float,
        timestampMillis: Long
    ): Double? {
        if (!rawMslMeters.isFinite()) return smoothedElevation

        val previous = smoothedElevation
        // Reject extreme low-quality fixes once we already have a baseline
        if (previous != null && verticalAccuracyMeters > 40f) {
            return previous
        }

        // Reject isolated altitude spikes (> 25m jump within < 6s with coarse vertical accuracy)
        if (previous != null) {
            val dtMillis = (timestampMillis - lastAcceptedTimeMillis).coerceAtLeast(1L)
            val jump = kotlin.math.abs(rawMslMeters - previous)
            if (dtMillis < 6_000L && jump > 25.0 && verticalAccuracyMeters > 6f) {
                return previous
            }
        }

        recentWindow.addLast(rawMslMeters)
        if (recentWindow.size > 5) recentWindow.removeFirst()

        val medianSample = recentWindow.sorted().let { sorted ->
            val mid = sorted.size / 2
            if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
        }

        val alpha = when {
            verticalAccuracyMeters <= 6f -> 0.50
            verticalAccuracyMeters <= 12f -> 0.34
            verticalAccuracyMeters <= 25f -> 0.20
            else -> 0.10
        }

        val nextSmoothed = if (previous == null) {
            medianSample
        } else {
            previous + alpha * (medianSample - previous)
        }
        smoothedElevation = nextSmoothed
        lastAcceptedTimeMillis = timestampMillis
        return nextSmoothed
    }
}

data class AutoPauseResult(
    val movingDurationMillis: Long,
    val isAutoPaused: Boolean,
    val lastMovementTimeMillis: Long,
    val isMovingSample: Boolean
)

object AutoPauseEvaluator {
    const val STATIONARY_THRESHOLD_MPS = 0.7f
    const val RESUME_THRESHOLD_MPS = 1.2f
    const val AUTO_PAUSE_DELAY_MILLIS = 25_000L
    const val MAX_SEGMENT_GAP_MILLIS = 30_000L

    fun evaluate(
        currentMovingMillis: Long,
        wasAutoPaused: Boolean,
        lastMovementTimeMillis: Long,
        lastSampleTimeMillis: Long?,
        deltaDistanceMeters: Double,
        speedMps: Float,
        sampleTimeMillis: Long
    ): AutoPauseResult {
        val rawDelta = if (lastSampleTimeMillis != null) {
            (sampleTimeMillis - lastSampleTimeMillis).coerceAtLeast(0L)
        } else 0L
        val segDelta = rawDelta.coerceAtMost(MAX_SEGMENT_GAP_MILLIS)
        val impliedSpeedMps = if (rawDelta > 0L) (deltaDistanceMeters / (rawDelta / 1000.0)).toFloat() else 0f

        val movingNow = if (wasAutoPaused) {
            speedMps >= RESUME_THRESHOLD_MPS || (deltaDistanceMeters >= 8.0 && impliedSpeedMps >= RESUME_THRESHOLD_MPS)
        } else {
            speedMps >= STATIONARY_THRESHOLD_MPS || deltaDistanceMeters >= 3.0
        }

        return if (movingNow) {
            AutoPauseResult(
                movingDurationMillis = currentMovingMillis + segDelta,
                isAutoPaused = false,
                lastMovementTimeMillis = sampleTimeMillis,
                isMovingSample = true
            )
        } else {
            val anchor = if (lastMovementTimeMillis > 0L) lastMovementTimeMillis else sampleTimeMillis
            val stationaryMillis = (sampleTimeMillis - anchor).coerceAtLeast(0L)
            val shouldPause = wasAutoPaused || stationaryMillis >= AUTO_PAUSE_DELAY_MILLIS
            AutoPauseResult(
                movingDurationMillis = if (shouldPause) currentMovingMillis else currentMovingMillis + segDelta,
                isAutoPaused = shouldPause,
                lastMovementTimeMillis = anchor,
                isMovingSample = false
            )
        }
    }
}
