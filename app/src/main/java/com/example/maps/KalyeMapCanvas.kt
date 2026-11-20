package com.example.maps

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.model.DistanceUnit
import com.example.data.model.MapLayerType
import com.example.data.model.RoutePoint
import com.example.data.model.StopType
import com.example.data.model.ThemeMode
import com.example.data.model.TripStop
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

data class MapViewportBounds(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
    val centerLat: Double,
    val centerLon: Double,
    val zoom: Int
)

@Composable
fun KalyeMapCanvas(
    tileManager: OfflineTileCacheManager,
    routePoints: List<RoutePoint>,
    stops: List<TripStop>,
    currentLocation: RoutePoint?,
    mapLayer: MapLayerType,
    themeMode: ThemeMode,
    distanceUnit: DistanceUnit,
    centerLat: Double,
    centerLon: Double,
    zoom: Double,
    interactive: Boolean = true,
    showScaleBadge: Boolean = true,
    onCenterChanged: (Double, Double, Double) -> Unit = { _, _, _ -> },
    onViewportBoundsChanged: (MapViewportBounds) -> Unit = {},
    onStopTapped: (TripStop) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = themeMode == ThemeMode.DARK

    val tileBitmaps = remember { mutableStateMapOf<String, Bitmap?>() }
    var tileTrigger by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    var internalLat by remember(centerLat) { mutableDoubleStateOf(centerLat) }
    var internalLon by remember(centerLon) { mutableDoubleStateOf(centerLon) }
    var internalZoom by remember(zoom) { mutableDoubleStateOf(zoom) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val bgColor = if (isDark) Color(0xFF0E1310) else Color(0xFFF3F1EC)
    val gridColor = if (isDark) Color(0xFF1F2722) else Color(0xFFE0DDD5)
    val minimalMapFilter = remember(isDark) {
        val desat = ColorMatrix().apply { setToSaturation(if (isDark) 0.10f else 0.12f) }
        val tone = if (isDark) {
            // Invert bright OSM base into sleek obsidian dark-mode minimal streets
            ColorMatrix(
                floatArrayOf(
                    -1.12f, 0f, 0f, 0f, 286f,
                    0f, -1.10f, 0f, 0f, 287f,
                    0f, 0f, -1.08f, 0f, 284f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        } else {
            // Soft warm-alabaster editorial contrast for minimal light street view
            ColorMatrix(
                floatArrayOf(
                    0.93f, 0f, 0f, 0f, 14f,
                    0f, 0.93f, 0f, 0f, 13f,
                    0f, 0f, 0.91f, 0f, 10f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }
        tone.timesAssign(desat)
        ColorFilter.colorMatrix(tone)
    }

    val density = LocalDensity.current
    val tilePx = with(density) { 256.dp.toPx() }.toDouble()

    Box(modifier = modifier.background(bgColor)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (interactive) {
                        Modifier
                            .pointerInput(mapLayer, isDark) {
                                detectTransformGestures { _, pan, zoomChange, _ ->
                                    val newZoom = (internalZoom + ln(zoomChange.toDouble()) / ln(2.0)).coerceIn(4.0, 17.5)
                                    val intZ = floor(newZoom).toInt()
                                    val scaleFactor = 2.0.pow(newZoom - intZ)
                                    val effectiveTilePx = tilePx * scaleFactor

                                    val (wx, wy) = OfflineTileCacheManager.latLonToWorldPixel(
                                        internalLat,
                                        internalLon,
                                        intZ.toDouble(),
                                        effectiveTilePx
                                    )
                                    val (newLat, newLon) = OfflineTileCacheManager.worldPixelToLatLon(
                                        wx - pan.x,
                                        wy - pan.y,
                                        intZ.toDouble(),
                                        effectiveTilePx
                                    )
                                    internalLat = newLat.coerceIn(-82.0, 82.0)
                                    internalLon = ((newLon + 540.0) % 360.0) - 180.0
                                    internalZoom = newZoom
                                    onCenterChanged(internalLat, internalLon, internalZoom)
                                }
                            }
                            .pointerInput(stops, internalLat, internalLon, internalZoom) {
                                detectTapGestures { tapOffset ->
                                    val intZ = floor(internalZoom).toInt().coerceIn(4, 17)
                                    val scaleFactor = 2.0.pow(internalZoom - intZ)
                                    val effectiveTilePx = tilePx * scaleFactor
                                    val (cx, cy) = OfflineTileCacheManager.latLonToWorldPixel(
                                        internalLat, internalLon, intZ.toDouble(), effectiveTilePx
                                    )
                                    val halfW = size.width / 2.0
                                    val halfH = size.height / 2.0

                                    val hit = stops.minByOrNull { stop ->
                                        val (sx, sy) = OfflineTileCacheManager.latLonToWorldPixel(
                                            stop.latitude, stop.longitude, intZ.toDouble(), effectiveTilePx
                                        )
                                        val px = (sx - cx + halfW).toFloat()
                                        val py = (sy - cy + halfH).toFloat()
                                        hypot(px - tapOffset.x, py - tapOffset.y)
                                    }
                                    if (hit != null) {
                                        val (sx, sy) = OfflineTileCacheManager.latLonToWorldPixel(
                                            hit.latitude, hit.longitude, intZ.toDouble(), effectiveTilePx
                                        )
                                        val px = (sx - cx + halfW).toFloat()
                                        val py = (sy - cy + halfH).toFloat()
                                        if (hypot(px - tapOffset.x, py - tapOffset.y) <= 56f) {
                                            onStopTapped(hit)
                                        }
                                    }
                                }
                            }
                    } else Modifier
                )
        ) {
            val intZ = floor(internalZoom).toInt().coerceIn(4, 17)
            val scaleFactor = 2.0.pow(internalZoom - intZ)
            val effectiveTilePx = tilePx * scaleFactor

            val (centerWorldX, centerWorldY) = OfflineTileCacheManager.latLonToWorldPixel(
                internalLat, internalLon, intZ.toDouble(), effectiveTilePx
            )

            val halfW = size.width / 2.0
            val halfH = size.height / 2.0
            val minWorldX = centerWorldX - halfW
            val minWorldY = centerWorldY - halfH
            val maxWorldX = centerWorldX + halfW
            val maxWorldY = centerWorldY + halfH

            val maxTileIndex = (1 shl intZ) - 1
            val startTileX = floor(minWorldX / effectiveTilePx).toInt().coerceIn(0, maxTileIndex)
            val endTileX = floor(maxWorldX / effectiveTilePx).toInt().coerceIn(0, maxTileIndex)
            val startTileY = floor(minWorldY / effectiveTilePx).toInt().coerceIn(0, maxTileIndex)
            val endTileY = floor(maxWorldY / effectiveTilePx).toInt().coerceIn(0, maxTileIndex)

            // Draw subtle vector grid underneath while tiles load
            val gridStep = (effectiveTilePx / 2.0).toFloat().coerceAtLeast(48f)
            var gx = ((-minWorldX % gridStep) + gridStep) % gridStep
            while (gx < size.width) {
                drawLine(gridColor, Offset(gx.toFloat(), 0f), Offset(gx.toFloat(), size.height), strokeWidth = 1f)
                gx += gridStep
            }
            var gy = ((-minWorldY % gridStep) + gridStep) % gridStep
            while (gy < size.height) {
                drawLine(gridColor, Offset(0f, gy.toFloat()), Offset(size.width, gy.toFloat()), strokeWidth = 1f)
                gy += gridStep
            }

            // Draw minimal muted street tiles
            val dummyRead = tileTrigger // Subscribe Canvas draw to tileTrigger updates
            if (dummyRead >= 0) {
                for (tx in startTileX..endTileX) {
                    for (ty in startTileY..endTileY) {
                        val key = tileManager.tileKey(isDark, intZ, tx, ty)
                        val cached = tileManager.getMemoryTile(isDark, intZ, tx, ty) ?: tileBitmaps[key]
                        if (cached != null) {
                            val left = (tx * effectiveTilePx - minWorldX).roundToInt()
                            val top = (ty * effectiveTilePx - minWorldY).roundToInt()
                            val tileDrawSize = ceil(effectiveTilePx).roundToInt() + 1
                            drawImage(
                                image = cached.asImageBitmap(),
                                dstOffset = IntOffset(left, top),
                                dstSize = IntSize(tileDrawSize, tileDrawSize),
                                colorFilter = minimalMapFilter
                            )
                        } else if (!tileBitmaps.containsKey(key)) {
                            tileBitmaps[key] = null
                            scope.launch {
                                val loaded = tileManager.loadTile(mapLayer, isDark, intZ, tx, ty)
                                if (loaded != null) {
                                    tileBitmaps[key] = loaded
                                    tileTrigger++
                                }
                            }
                        }
                    }
                }
            }

            fun project(lat: Double, lon: Double): Offset {
                val (wx, wy) = OfflineTileCacheManager.latLonToWorldPixel(
                    lat, lon, intZ.toDouble(), effectiveTilePx
                )
                return Offset((wx - minWorldX).toFloat(), (wy - minWorldY).toFloat())
            }

            // Draw route polyline
            if (routePoints.size >= 2) {
                val path = Path()
                routePoints.forEachIndexed { idx, pt ->
                    val offset = project(pt.latitude, pt.longitude)
                    if (idx == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
                }
                // Casing for crisp contrast over topo lines
                drawPath(
                    path = path,
                    color = if (isDark) Color(0xFF0A0C0A) else Color.White,
                    style = Stroke(width = 14f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                // Main terracotta overland line
                drawPath(
                    path = path,
                    color = primaryColor,
                    style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            // Start & End markers
            routePoints.firstOrNull()?.let { start ->
                val p = project(start.latitude, start.longitude)
                drawCircle(Color.White, radius = 14f, center = p)
                drawCircle(secondaryColor, radius = 9f, center = p)
            }
            if (routePoints.size > 1) {
                routePoints.lastOrNull()?.let { end ->
                    val p = project(end.latitude, end.longitude)
                    drawCircle(Color.White, radius = 14f, center = p)
                    drawCircle(primaryColor, radius = 9f, center = p)
                }
            }

            // Stop-over / Fuel / Ferry / Camp pins
            stops.forEach { stop ->
                val p = project(stop.latitude, stop.longitude)
                val pinColor = when (stop.stopType) {
                    StopType.FUEL -> tertiaryColor
                    StopType.FERRY -> Color(0xFF2186A8)
                    StopType.CAMP -> secondaryColor
                    StopType.STOP_OVER -> primaryColor
                    StopType.TOLL -> Color(0xFF8C5A3C)
                    StopType.REPAIR -> Color(0xFFC24343)
                }
                drawCircle(Color.White, radius = 19f, center = p)
                drawCircle(pinColor, radius = 14f, center = p)
                drawCircle(Color.White, radius = 4f, center = p)
            }

            // Live GPS marker with accuracy halo
            currentLocation?.let { loc ->
                val p = project(loc.latitude, loc.longitude)
                val metersPerPx = (156543.03392 * cos(Math.toRadians(loc.latitude))) / 2.0.pow(internalZoom)
                val accuracyPx = (loc.accuracyMeters / metersPerPx).toFloat().coerceIn(22f, 120f)
                drawCircle(primaryColor.copy(alpha = 0.18f), radius = accuracyPx, center = p)
                drawCircle(
                    color = primaryColor.copy(alpha = 0.45f),
                    radius = accuracyPx,
                    center = p,
                    style = Stroke(width = 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
                )
                drawCircle(Color.White, radius = 18f, center = p)
                drawCircle(Color(0xFF1F87E8), radius = 12f, center = p)
            }
        }

        // Report viewport bounds whenever center/zoom changes
        LaunchedEffect(internalLat, internalLon, internalZoom) {
            val intZ = floor(internalZoom).toInt().coerceIn(4, 17)
            val latDelta = 180.0 / 2.0.pow(intZ - 1) * 0.45
            val lonDelta = 360.0 / 2.0.pow(intZ - 1) * 0.45
            onViewportBoundsChanged(
                MapViewportBounds(
                    minLat = (internalLat - latDelta).coerceIn(-85.0, 85.0),
                    maxLat = (internalLat + latDelta).coerceIn(-85.0, 85.0),
                    minLon = (internalLon - lonDelta).coerceIn(-180.0, 180.0),
                    maxLon = (internalLon + lonDelta).coerceIn(-180.0, 180.0),
                    centerLat = internalLat,
                    centerLon = internalLon,
                    zoom = intZ
                )
            )
        }

        // Dynamic Scale Bar overlay (bottom-left of map)
        val scaleLabel = remember(internalZoom, internalLat, distanceUnit) {
            val metersPerDp = (156543.03392 * cos(Math.toRadians(internalLat))) / 2.0.pow(internalZoom)
            val barMeters = metersPerDp * 64.0
            when (distanceUnit) {
                DistanceUnit.KM -> if (barMeters >= 1000) String.format(Locale.US, "%.1f km", barMeters / 1000.0)
                else String.format(Locale.US, "%.0f m", barMeters)
                DistanceUnit.MI -> {
                    val miles = barMeters / 1609.344
                    if (miles >= 0.25) String.format(Locale.US, "%.2f mi", miles)
                    else String.format(Locale.US, "%.0f ft", barMeters * 3.28084)
                }
            }
        }

        if (showScaleBadge) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                tonalElevation = 2.dp
            ) {
                Text(
                    text = "Z${internalZoom.roundToInt()} • $scaleLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
