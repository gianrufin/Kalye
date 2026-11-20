package com.example.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.data.model.DistanceUnit
import com.example.data.model.GeoUtils
import com.example.data.model.MapLayerType
import com.example.data.model.RoutePoint
import com.example.data.model.ShareCardInkStyle
import com.example.data.model.ShareCardLayout
import com.example.data.model.StopType
import com.example.data.model.TripStop
import com.example.data.model.TripSummary
import com.example.maps.OfflineTileCacheManager
import com.example.maps.TileCoord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tan

data class ShareCardConfig(
    val layout: ShareCardLayout = ShareCardLayout.CLASSIC_STACK,
    val inkStyle: ShareCardInkStyle = ShareCardInkStyle.WHITE_TRANSPARENT,
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
    val showTrailLine: Boolean = true,
    val showStopsOnMap: Boolean = true
)

data class RoadmapViewport(
    val intZoom: Int,
    val exactZoom: Double,
    val effectiveTilePx: Double,
    val centerWorldX: Double,
    val centerWorldY: Double,
    val originCanvasX: Float,
    val originCanvasY: Float,
    val minWorldX: Double,
    val minWorldY: Double,
    val maxWorldX: Double,
    val maxWorldY: Double,
    val centerLat: Double,
    val visibleTiles: List<TileCoord>
) {
    fun project(lat: Double, lon: Double): Pair<Float, Float> {
        val (wx, wy) = OfflineTileCacheManager.latLonToWorldPixel(
            lat, lon, intZoom.toDouble(), effectiveTilePx
        )
        val px = originCanvasX + (wx - centerWorldX).toFloat()
        val py = originCanvasY + (wy - centerWorldY).toFloat()
        return px to py
    }
}

object ShareCardRenderer {

    fun buildExportFilename(nowMillis: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(nowMillis))
        return "kalye_${stamp}.png"
    }

    fun computeRoadmapViewport(
        points: List<RoutePoint>,
        stops: List<TripStop>,
        width: Int,
        height: Int
    ): RoadmapViewport {
        val w = width.toFloat().coerceAtLeast(100f)
        val h = height.toFloat().coerceAtLeast(100f)
        val s = w / 1080f

        // Open map region between the top pills and the bottom summary HUD card
        val safeLeft = 94f * s
        val safeRight = w - 94f * s
        val safeTop = 186f * s
        val safeBottom = h - 418f * s
        val safeW = (safeRight - safeLeft).coerceAtLeast(80f).toDouble()
        val safeH = (safeBottom - safeTop).coerceAtLeast(80f).toDouble()
        val originX = (safeLeft + safeRight) / 2f
        val originY = (safeTop + safeBottom) / 2f

        val allCoords = buildList {
            points.forEach { add(it.latitude to it.longitude) }
            stops.forEach { add(it.latitude to it.longitude) }
        }.ifEmpty { listOf(14.5995 to 120.9842) }

        val minLat = allCoords.minOf { it.first }.coerceIn(-82.0, 82.0)
        val maxLat = allCoords.maxOf { it.first }.coerceIn(-82.0, 82.0)
        val minLon = allCoords.minOf { it.second }.coerceIn(-180.0, 180.0)
        val maxLon = allCoords.maxOf { it.second }.coerceIn(-180.0, 180.0)

        fun normX(lon: Double): Double = (lon + 180.0) / 360.0
        fun normY(lat: Double): Double {
            val rad = Math.toRadians(lat.coerceIn(-82.0, 82.0))
            return (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0
        }

        val nx1 = normX(minLon)
        val nx2 = normX(maxLon)
        val ny1 = normY(maxLat)
        val ny2 = normY(minLat)

        // Ensure minimum span (~600m) for single-point or ultra-short trips
        val spanX = (nx2 - nx1).coerceAtLeast(0.000018)
        val spanY = (ny2 - ny1).coerceAtLeast(0.000018)
        val midNx = (nx1 + nx2) / 2.0
        val midNy = (ny1 + ny2) / 2.0
        val centerLat = (minLat + maxLat) / 2.0

        // Larger base tile scale so street names remain sharp and legible on 1080x1350 PNG
        val baseTilePx = 340.0 * s
        val targetWorldPx = minOf(safeW / spanX, safeH / spanY) * 0.90
        val exactZoom = (ln(targetWorldPx / baseTilePx) / ln(2.0)).coerceIn(4.0, 15.6)
        val intZoom = floor(exactZoom).toInt().coerceIn(4, 15)
        val scaleFactor = 2.0.pow(exactZoom - intZoom)
        val effectiveTilePx = baseTilePx * scaleFactor

        val worldScale = effectiveTilePx * (1 shl intZoom)
        val centerWorldX = midNx * worldScale
        val centerWorldY = midNy * worldScale

        val minWorldX = centerWorldX - originX.toDouble()
        val maxWorldX = centerWorldX + (w - originX).toDouble()
        val minWorldY = centerWorldY - originY.toDouble()
        val maxWorldY = centerWorldY + (h - originY).toDouble()

        val maxTileIndex = (1 shl intZoom) - 1
        val startTileX = floor(minWorldX / effectiveTilePx).toInt().coerceIn(0, maxTileIndex)
        val endTileX = floor(maxWorldX / effectiveTilePx).toInt().coerceIn(0, maxTileIndex)
        val startTileY = floor(minWorldY / effectiveTilePx).toInt().coerceIn(0, maxTileIndex)
        val endTileY = floor(maxWorldY / effectiveTilePx).toInt().coerceIn(0, maxTileIndex)

        val visibleTiles = buildList {
            for (tx in startTileX..endTileX) {
                for (ty in startTileY..endTileY) {
                    add(TileCoord(intZoom, tx, ty))
                }
            }
        }

        return RoadmapViewport(
            intZoom = intZoom,
            exactZoom = exactZoom,
            effectiveTilePx = effectiveTilePx,
            centerWorldX = centerWorldX,
            centerWorldY = centerWorldY,
            originCanvasX = originX,
            originCanvasY = originY,
            minWorldX = minWorldX,
            minWorldY = minWorldY,
            maxWorldX = maxWorldX,
            maxWorldY = maxWorldY,
            centerLat = centerLat,
            visibleTiles = visibleTiles
        )
    }

    suspend fun loadRoadmapTiles(
        tileManager: OfflineTileCacheManager,
        points: List<RoutePoint>,
        stops: List<TripStop>,
        isDarkMap: Boolean,
        width: Int = 1080,
        height: Int = 1350
    ): Map<TileCoord, Bitmap> = withContext(Dispatchers.IO) {
        val viewport = computeRoadmapViewport(points, stops, width, height)
        val tiles = viewport.visibleTiles.take(36)
        if (tiles.isEmpty()) return@withContext emptyMap()

        val loaded = withTimeoutOrNull(4500L) {
            coroutineScope {
                tiles.map { coord ->
                    async {
                        val bmp = runCatching {
                            tileManager.loadTile(MapLayerType.STREET, isDarkMap, coord.z, coord.x, coord.y)
                        }.getOrNull()
                        coord to bmp
                    }
                }.awaitAll()
            }
        } ?: emptyList()

        buildMap {
            loaded.forEach { (coord, bmp) ->
                if (bmp != null) put(coord, bmp)
            }
        }
    }

    suspend fun renderToBitmapAsync(
        context: Context,
        trip: TripSummary,
        points: List<RoutePoint>,
        stops: List<TripStop>,
        config: ShareCardConfig,
        tileManager: OfflineTileCacheManager?,
        width: Int = 1080,
        height: Int = 1350
    ): Bitmap {
        val tiles = if (config.layout == ShareCardLayout.ROADMAP_CARD && tileManager != null) {
            loadRoadmapTiles(
                tileManager = tileManager,
                points = points,
                stops = if (config.showStopsOnMap) stops else emptyList(),
                isDarkMap = config.inkStyle == ShareCardInkStyle.DARK_CARD,
                width = width,
                height = height
            )
        } else {
            emptyMap()
        }

        return withContext(Dispatchers.Default) {
            renderToBitmap(
                context = context,
                trip = trip,
                points = points,
                config = config,
                width = width,
                height = height,
                stops = stops,
                preloadedTiles = tiles
            )
        }
    }

    fun renderToBitmap(
        context: Context,
        trip: TripSummary,
        points: List<RoutePoint>,
        config: ShareCardConfig,
        width: Int = 1080,
        height: Int = 1350,
        stops: List<TripStop> = emptyList(),
        preloadedTiles: Map<TileCoord, Bitmap> = emptyMap()
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val outfitBase = ResourcesCompat.getFont(context, R.font.outfit) ?: Typeface.DEFAULT_BOLD
        val displayTypeface = Typeface.create(outfitBase, Typeface.BOLD)
        val bodyBase = ResourcesCompat.getFont(context, R.font.manrope) ?: Typeface.DEFAULT
        val bodyTypeface = Typeface.create(bodyBase, Typeface.BOLD)
        val monoBase = ResourcesCompat.getFont(context, R.font.jetbrains_mono) ?: Typeface.MONOSPACE
        val monoTypeface = Typeface.create(monoBase, Typeface.BOLD)

        val scale = width / 1080f
        val distNum = GeoUtils.distanceNumber(trip.distanceMeters, config.distanceUnit)
        val distUnit = config.distanceUnit.shortLabel.lowercase(Locale.US)
        val distanceText = "$distNum $distUnit"
        val elevationText = "+${GeoUtils.formatElevation(trip.elevationGainMeters, config.distanceUnit)}"
        val movingTimeText = GeoUtils.formatMovingTime(trip.effectiveMovingMillis)
        val tripName = trip.title.ifBlank { "Overland Trip" }

        if (config.layout == ShareCardLayout.ROADMAP_CARD) {
            renderRoadmapCard(
                canvas = canvas,
                w = width.toFloat(),
                h = height.toFloat(),
                s = scale,
                trip = trip,
                tripName = tripName,
                distance = distanceText,
                elevation = elevationText,
                movingTime = movingTimeText,
                points = points,
                stops = if (config.showStopsOnMap) stops else emptyList(),
                preloadedTiles = preloadedTiles,
                config = config,
                displayTf = displayTypeface,
                bodyTf = bodyTypeface,
                monoTf = monoTypeface
            )
            return bitmap
        }

        val palette = when (config.inkStyle) {
            ShareCardInkStyle.WHITE_TRANSPARENT -> InkPalette(
                isTransparent = true,
                primaryText = Color.WHITE,
                labelText = Color.argb(225, 255, 255, 255),
                accentColor = Color.parseColor("#FF7A45"),
                shadowColor = Color.argb(185, 0, 0, 0),
                dividerColor = Color.argb(110, 255, 255, 255)
            )
            ShareCardInkStyle.DARK_TRANSPARENT -> InkPalette(
                isTransparent = true,
                primaryText = Color.parseColor("#141815"),
                labelText = Color.argb(215, 20, 24, 21),
                accentColor = Color.parseColor("#C85220"),
                shadowColor = Color.argb(140, 255, 255, 255),
                dividerColor = Color.argb(95, 20, 24, 21)
            )
            ShareCardInkStyle.DARK_CARD -> InkPalette(
                isTransparent = false,
                primaryText = Color.parseColor("#F5F2EA"),
                labelText = Color.parseColor("#A7B0A7"),
                accentColor = Color.parseColor("#E87842"),
                shadowColor = Color.TRANSPARENT,
                dividerColor = Color.argb(65, 255, 255, 255)
            )
        }

        if (palette.isTransparent) {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        } else {
            val cardRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    Color.parseColor("#101512"),
                    Color.parseColor("#1A231E"),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(cardRect, 48f * scale, 48f * scale, bgPaint)
        }

        when (config.layout) {
            ShareCardLayout.CLASSIC_STACK -> renderClassicStack(
                canvas, width.toFloat(), height.toFloat(), scale,
                tripName, distanceText, elevationText, movingTimeText,
                points, config.showTrailLine, palette, displayTypeface, bodyTypeface, monoTypeface
            )
            ShareCardLayout.CORNER_EDITORIAL -> renderCornerEditorial(
                canvas, width.toFloat(), height.toFloat(), scale,
                tripName, distanceText, elevationText, movingTimeText,
                points, config.showTrailLine, palette, displayTypeface, bodyTypeface, monoTypeface
            )
            ShareCardLayout.COMPACT_BAR -> renderCompactBar(
                canvas, width.toFloat(), height.toFloat(), scale,
                tripName, distanceText, elevationText, movingTimeText,
                points, config.showTrailLine, palette, displayTypeface, bodyTypeface, monoTypeface
            )
            ShareCardLayout.ROUTE_HERO -> renderRouteHero(
                canvas, width.toFloat(), height.toFloat(), scale,
                tripName, distanceText, elevationText, movingTimeText,
                points, config.showTrailLine, palette, displayTypeface, bodyTypeface, monoTypeface
            )
            ShareCardLayout.ROADMAP_CARD -> Unit
        }

        return bitmap
    }

    private fun renderRoadmapCard(
        canvas: Canvas,
        w: Float,
        h: Float,
        s: Float,
        trip: TripSummary,
        tripName: String,
        distance: String,
        elevation: String,
        movingTime: String,
        points: List<RoutePoint>,
        stops: List<TripStop>,
        preloadedTiles: Map<TileCoord, Bitmap>,
        config: ShareCardConfig,
        displayTf: Typeface,
        bodyTf: Typeface,
        monoTf: Typeface
    ) {
        val style = when (config.inkStyle) {
            ShareCardInkStyle.DARK_CARD -> RoadmapPalette(
                isDarkMap = true,
                canvasBg = Color.parseColor("#0E1310"),
                gridColor = Color.parseColor("#1E2822"),
                contourColor = Color.argb(42, 90, 184, 142),
                routeColor = Color.parseColor("#E87842"),
                routeCasing = Color.parseColor("#0A0D0B"),
                routeGlow = Color.argb(85, 232, 120, 66),
                startPin = Color.parseColor("#5AB88E"),
                endPin = Color.parseColor("#E87842"),
                hudFill = Color.argb(232, 16, 21, 18),
                hudBorder = Color.parseColor("#2E3731"),
                primaryText = Color.parseColor("#F5F2EA"),
                mutedText = Color.parseColor("#A7B0A7"),
                accentText = Color.parseColor("#E87842"),
                chipFill = Color.argb(215, 28, 36, 31),
                chipText = Color.parseColor("#F2EFE8"),
                badgeFill = Color.argb(220, 24, 61, 46),
                badgeText = Color.parseColor("#8CDAB6"),
                divider = Color.argb(55, 245, 242, 234),
                saturation = 0.10f
            )
            ShareCardInkStyle.DARK_TRANSPARENT -> RoadmapPalette(
                isDarkMap = false,
                canvasBg = Color.parseColor("#ECEEEB"),
                gridColor = Color.parseColor("#D9DDD8"),
                contourColor = Color.argb(38, 30, 77, 59),
                routeColor = Color.parseColor("#C86428"),
                routeCasing = Color.WHITE,
                routeGlow = Color.argb(65, 200, 100, 40),
                startPin = Color.parseColor("#1E4D3B"),
                endPin = Color.parseColor("#C86428"),
                hudFill = Color.argb(240, 20, 27, 23),
                hudBorder = Color.parseColor("#323F37"),
                primaryText = Color.parseColor("#F5F2EA"),
                mutedText = Color.parseColor("#B3BDB3"),
                accentText = Color.parseColor("#F0864D"),
                chipFill = Color.argb(230, 21, 28, 24),
                chipText = Color.parseColor("#F5F2EA"),
                badgeFill = Color.argb(225, 38, 86, 66),
                badgeText = Color.parseColor("#C2ECD7"),
                divider = Color.argb(55, 255, 255, 255),
                saturation = 0.04f
            )
            ShareCardInkStyle.WHITE_TRANSPARENT -> RoadmapPalette(
                isDarkMap = false,
                canvasBg = Color.parseColor("#F3F1EC"),
                gridColor = Color.parseColor("#E1DDD2"),
                contourColor = Color.argb(38, 168, 134, 96),
                routeColor = Color.parseColor("#C86428"),
                routeCasing = Color.WHITE,
                routeGlow = Color.argb(72, 200, 100, 40),
                startPin = Color.parseColor("#1E4D3B"),
                endPin = Color.parseColor("#C86428"),
                hudFill = Color.argb(238, 252, 250, 245),
                hudBorder = Color.parseColor("#D5CFC1"),
                primaryText = Color.parseColor("#151916"),
                mutedText = Color.parseColor("#545C54"),
                accentText = Color.parseColor("#C86428"),
                chipFill = Color.argb(232, 252, 250, 245),
                chipText = Color.parseColor("#151916"),
                badgeFill = Color.parseColor("#D6E8DD"),
                badgeText = Color.parseColor("#1E4D3B"),
                divider = Color.argb(36, 21, 25, 22),
                saturation = 0.16f
            )
        }

        val cardClip = Path().apply {
            addRoundRect(RectF(0f, 0f, w, h), 48f * s, 48f * s, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(cardClip)

        // 1. Base cartographic background
        canvas.drawColor(style.canvasBg)

        val viewport = computeRoadmapViewport(points, stops, w.toInt(), h.toInt())

        // 2. Cartographic coordinate grid + subtle contour lines (visible while offline or underneath tiles)
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.gridColor
            strokeWidth = 1.6f * s
        }
        val gridStep = (viewport.effectiveTilePx / 2.0).toFloat().coerceAtLeast(64f * s)
        var gx = ((-viewport.minWorldX % gridStep) + gridStep) % gridStep
        while (gx < w) {
            canvas.drawLine(gx.toFloat(), 0f, gx.toFloat(), h, gridPaint)
            gx += gridStep
        }
        var gy = ((-viewport.minWorldY % gridStep) + gridStep) % gridStep
        while (gy < h) {
            canvas.drawLine(0f, gy.toFloat(), w, gy.toFloat(), gridPaint)
            gy += gridStep
        }

        if (preloadedTiles.isEmpty()) {
            val contourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.contourColor
                this.style = Paint.Style.STROKE
                strokeWidth = 2.2f * s
                pathEffect = DashPathEffect(floatArrayOf(12f * s, 8f * s), 0f)
            }
            val cx = viewport.originCanvasX
            val cy = viewport.originCanvasY
            listOf(180f * s, 310f * s, 450f * s, 600f * s).forEach { r ->
                canvas.drawOval(
                    RectF(cx - r * 1.25f, cy - r * 0.88f, cx + r * 1.25f, cy + r * 0.88f),
                    contourPaint
                )
            }
        }

        // 3. Draw real street map tiles filtered to Kalye's minimal editorial palette
        if (preloadedTiles.isNotEmpty()) {
            val tileColorFilter = buildMinimalMapColorFilter(style.isDarkMap, style.saturation)
            val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = tileColorFilter
            }
            viewport.visibleTiles.forEach { coord ->
                val bmp = preloadedTiles[coord] ?: return@forEach
                val left = (coord.x * viewport.effectiveTilePx - viewport.minWorldX).roundToInt()
                val top = (coord.y * viewport.effectiveTilePx - viewport.minWorldY).roundToInt()
                val tileSize = ceil(viewport.effectiveTilePx).roundToInt() + 1
                val dst = Rect(left, top, left + tileSize, top + tileSize)
                canvas.drawBitmap(bmp, null, dst, tilePaint)
            }
        }

        // 4. Soft edge gradient framing so top pills & bottom HUD always pop cleanly over busy streets
        val topScrim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, 220f * s,
                Color.argb(if (style.isDarkMap) 150 else 110, Color.red(style.canvasBg), Color.green(style.canvasBg), Color.blue(style.canvasBg)),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, 220f * s, topScrim)

        val bottomScrim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, h - 460f * s, 0f, h,
                Color.TRANSPARENT,
                Color.argb(if (style.isDarkMap) 185 else 135, Color.red(style.canvasBg), Color.green(style.canvasBg), Color.blue(style.canvasBg)),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, h - 460f * s, w, h, bottomScrim)

        // 5. Draw complete travel route polyline + Start / End / Stop pins
        if (points.size >= 2) {
            val routePath = Path()
            var startX = 0f
            var startY = 0f
            var endX = 0f
            var endY = 0f
            points.forEachIndexed { idx, pt ->
                val (px, py) = viewport.project(pt.latitude, pt.longitude)
                if (idx == 0) {
                    routePath.moveTo(px, py)
                    startX = px
                    startY = py
                } else {
                    routePath.lineTo(px, py)
                }
                endX = px
                endY = py
            }

            // Outer glow
            canvas.drawPath(
                routePath,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE
                    strokeWidth = 28f * s
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    color = style.routeGlow
                }
            )
            // Contrast casing
            canvas.drawPath(
                routePath,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE
                    strokeWidth = 17f * s
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    color = style.routeCasing
                }
            )
            // Primary route line
            canvas.drawPath(
                routePath,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE
                    strokeWidth = 10f * s
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    color = style.routeColor
                }
            )

            // Logged Stop pins (Fuel, Ferry, Camp, Stop-Over, Toll, Repair)
            stops.forEach { stop ->
                val (sx, sy) = viewport.project(stop.latitude, stop.longitude)
                val pinColor = when (stop.stopType) {
                    StopType.FUEL -> Color.parseColor("#D49022")
                    StopType.FERRY -> Color.parseColor("#2186A8")
                    StopType.CAMP -> Color.parseColor("#2D8A5B")
                    StopType.STOP_OVER -> style.routeColor
                    StopType.TOLL -> Color.parseColor("#8C5A3C")
                    StopType.REPAIR -> Color.parseColor("#C24343")
                }
                canvas.drawCircle(sx, sy, 15f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
                canvas.drawCircle(sx, sy, 10.5f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pinColor })
                canvas.drawCircle(sx, sy, 3.5f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            }

            // Start & Finish markers
            canvas.drawCircle(startX, startY, 18f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            canvas.drawCircle(startX, startY, 12f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.startPin })

            canvas.drawCircle(endX, endY, 20f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            canvas.drawCircle(endX, endY, 13.5f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.endPin })
            canvas.drawCircle(endX, endY, 4.5f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })

            // Tiny Start/Finish tags when far enough apart so they never collide
            if (hypot(endX - startX, endY - startY) > 120f * s) {
                drawMapPinPill(canvas, "START", startX, startY - 30f * s, s, style.badgeFill, style.badgeText, monoTf)
                drawMapPinPill(canvas, "FINISH", endX, endY - 32f * s, s, style.hudFill, style.accentText, monoTf)
            }
        } else if (points.size == 1) {
            val (px, py) = viewport.project(points[0].latitude, points[0].longitude)
            canvas.drawCircle(px, py, 24f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.routeGlow })
            canvas.drawCircle(px, py, 16f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            canvas.drawCircle(px, py, 11f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.endPin })
        }

        // 6. Top Floating Header Pills (KALYE ROADMAP + Date / Vehicle Rig)
        val pad = 48f * s
        val topY = 46f * s
        val dateText = if (trip.startTimeMillis > 0L) {
            SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(trip.startTimeMillis)).uppercase(Locale.US)
        } else {
            "OVERLAND"
        }
        val brandText = "KALYE ROADMAP • $dateText"
        drawRoundedPill(
            canvas = canvas,
            text = brandText,
            leftX = pad,
            topY = topY,
            height = 56f * s,
            s = s,
            bgColor = style.chipFill,
            borderColor = style.hudBorder,
            textColor = style.chipText,
            tf = monoTf,
            textSize = 21f * s,
            accentDotColor = style.accentText
        )

        if (trip.vehicleName.isNotBlank()) {
            val rigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = bodyTf
                textSize = 22f * s
            }
            val rigLabel = ellipsizeText(trip.vehicleName, rigPaint, 320f * s)
            val rigWidth = rigPaint.measureText(rigLabel) + 48f * s
            drawRoundedPill(
                canvas = canvas,
                text = rigLabel,
                leftX = w - pad - rigWidth,
                topY = topY,
                height = 56f * s,
                s = s,
                bgColor = style.chipFill,
                borderColor = style.hudBorder,
                textColor = style.chipText,
                tf = bodyTf,
                textSize = 22f * s,
                accentDotColor = null
            )
        }

        // 7. Bottom Floating Summary HUD Card
        val hudTop = h - 368f * s
        val hudRect = RectF(pad, hudTop, w - pad, h - pad)

        // Subtle Map Scale Indicator floating just above HUD right corner
        drawScalePillAboveHud(
            canvas = canvas,
            rightX = w - pad,
            bottomY = hudTop - 16f * s,
            s = s,
            viewport = viewport,
            distanceUnit = config.distanceUnit,
            bgColor = style.chipFill,
            borderColor = style.hudBorder,
            textColor = style.chipText,
            monoTf = monoTf
        )

        val hudFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.hudFill }
        val hudBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = 2.2f * s
            color = style.hudBorder
        }
        canvas.drawRoundRect(hudRect, 34f * s, 34f * s, hudFillPaint)
        canvas.drawRoundRect(hudRect, 34f * s, 34f * s, hudBorderPaint)

        val innerX = hudRect.left + 38f * s
        val innerRight = hudRect.right - 38f * s
        var cursorY = hudRect.top + 34f * s

        // Category + Stops / Peak Elev Pills
        val tagText = trip.categoryTag.ifBlank { "OVERLAND" }.uppercase(Locale.US)
        val tagUsedW = drawCompactTag(
            canvas = canvas,
            text = tagText,
            leftX = innerX,
            topY = cursorY,
            s = s,
            bgColor = style.badgeFill,
            textColor = style.badgeText,
            monoTf = monoTf
        )

        var nextPillX = innerX + tagUsedW + 12f * s
        if (stops.isNotEmpty()) {
            val stopLabel = "${stops.size} ${if (stops.size == 1) "STOP" else "STOPS"}"
            nextPillX += drawCompactTag(
                canvas = canvas,
                text = stopLabel,
                leftX = nextPillX,
                topY = cursorY,
                s = s,
                bgColor = Color.argb(38, Color.red(style.accentText), Color.green(style.accentText), Color.blue(style.accentText)),
                textColor = style.accentText,
                monoTf = monoTf
            ) + 12f * s
        }

        if (trip.maxElevationMeters > 50.0) {
            val peakLabel = "PEAK ${GeoUtils.formatElevation(trip.maxElevationMeters, config.distanceUnit).uppercase(Locale.US)}"
            val peakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = monoTf
                textSize = 20f * s
                color = style.mutedText
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(peakLabel, innerRight, cursorY + 28f * s, peakPaint)
        }

        cursorY += 56f * s

        // Trip Name
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = displayTf
            textSize = 46f * s
            color = style.primaryText
        }
        val clippedTitle = ellipsizeText(tripName, titlePaint, innerRight - innerX)
        canvas.drawText(clippedTitle, innerX, cursorY + 34f * s, titlePaint)

        // Divider
        val divY = hudRect.top + 160f * s
        canvas.drawLine(
            innerX,
            divY,
            innerRight,
            divY,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.divider
                strokeWidth = 2f * s
            }
        )

        // 3-Column Metrics Row
        val colW = (innerRight - innerX) / 3f
        val lblPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = monoTf
            textSize = 21f * s
            letterSpacing = 0.12f
            color = style.mutedText
        }
        val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = displayTf
            textSize = 48f * s
            color = style.primaryText
        }
        val labelY = divY + 46f * s
        val valY = divY + 106f * s

        val stats = listOf(
            "DISTANCE" to distance,
            "MOVING TIME" to movingTime,
            "ELEV GAIN" to elevation
        )
        stats.forEachIndexed { idx, (lbl, value) ->
            val x = innerX + colW * idx
            canvas.drawText(lbl, x, labelY, lblPaint)
            canvas.drawText(ellipsizeText(value, valPaint, colW - 14f * s), x, valY, valPaint)
        }

        canvas.restore()
    }

    private fun buildMinimalMapColorFilter(isDark: Boolean, saturation: Float): ColorMatrixColorFilter {
        val desat = ColorMatrix().apply { setSaturation(saturation) }
        val tone = if (isDark) {
            ColorMatrix(
                floatArrayOf(
                    -1.12f, 0f, 0f, 0f, 286f,
                    0f, -1.10f, 0f, 0f, 287f,
                    0f, 0f, -1.08f, 0f, 284f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        } else {
            ColorMatrix(
                floatArrayOf(
                    0.94f, 0f, 0f, 0f, 12f,
                    0f, 0.94f, 0f, 0f, 11f,
                    0f, 0f, 0.92f, 0f, 9f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }
        tone.preConcat(desat)
        return ColorMatrixColorFilter(tone)
    }

    private fun drawRoundedPill(
        canvas: Canvas,
        text: String,
        leftX: Float,
        topY: Float,
        height: Float,
        s: Float,
        bgColor: Int,
        borderColor: Int,
        textColor: Int,
        tf: Typeface,
        textSize: Float,
        accentDotColor: Int?
    ) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf
            this.textSize = textSize
            color = textColor
        }
        val dotSpace = if (accentDotColor != null) 24f * s else 0f
        val pillWidth = textPaint.measureText(text) + 44f * s + dotSpace
        val rect = RectF(leftX, topY, leftX + pillWidth, topY + height)
        canvas.drawRoundRect(rect, height / 2f, height / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor })
        canvas.drawRoundRect(
            rect,
            height / 2f,
            height / 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.8f * s
                color = borderColor
            }
        )
        var textX = leftX + 22f * s
        if (accentDotColor != null) {
            canvas.drawCircle(
                leftX + 26f * s,
                rect.centerY(),
                6.5f * s,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentDotColor }
            )
            textX += 20f * s
        }
        val fm = textPaint.fontMetrics
        val baseline = rect.centerY() - (fm.ascent + fm.descent) / 2f
        canvas.drawText(text, textX, baseline, textPaint)
    }

    private fun drawCompactTag(
        canvas: Canvas,
        text: String,
        leftX: Float,
        topY: Float,
        s: Float,
        bgColor: Int,
        textColor: Int,
        monoTf: Typeface
    ): Float {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = monoTf
            textSize = 20f * s
            letterSpacing = 0.08f
            color = textColor
        }
        val pillH = 38f * s
        val pillW = p.measureText(text) + 28f * s
        val rect = RectF(leftX, topY, leftX + pillW, topY + pillH)
        canvas.drawRoundRect(rect, 12f * s, 12f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor })
        val fm = p.fontMetrics
        canvas.drawText(text, leftX + 14f * s, rect.centerY() - (fm.ascent + fm.descent) / 2f, p)
        return pillW
    }

    private fun drawMapPinPill(
        canvas: Canvas,
        text: String,
        centerX: Float,
        centerY: Float,
        s: Float,
        bgColor: Int,
        textColor: Int,
        monoTf: Typeface
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = monoTf
            textSize = 18f * s
            letterSpacing = 0.08f
            color = textColor
            textAlign = Paint.Align.CENTER
        }
        val w = p.measureText(text) + 24f * s
        val h = 32f * s
        val rect = RectF(centerX - w / 2f, centerY - h / 2f, centerX + w / 2f, centerY + h / 2f)
        canvas.drawRoundRect(rect, h / 2f, h / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor })
        val fm = p.fontMetrics
        canvas.drawText(text, centerX, rect.centerY() - (fm.ascent + fm.descent) / 2f, p)
    }

    private fun drawScalePillAboveHud(
        canvas: Canvas,
        rightX: Float,
        bottomY: Float,
        s: Float,
        viewport: RoadmapViewport,
        distanceUnit: DistanceUnit,
        bgColor: Int,
        borderColor: Int,
        textColor: Int,
        monoTf: Typeface
    ) {
        val scaleAdj = viewport.effectiveTilePx / (256.0 * s)
        val metersPerBasePx = (156543.03392 * cos(Math.toRadians(viewport.centerLat))) /
            (2.0.pow(viewport.intZoom) * scaleAdj)
        val barMeters = metersPerBasePx * 96.0 * s
        val scaleLabel = when (distanceUnit) {
            DistanceUnit.KM -> if (barMeters >= 1000) String.format(Locale.US, "%.1f km", barMeters / 1000.0)
            else String.format(Locale.US, "%.0f m", barMeters)
            DistanceUnit.MI -> {
                val miles = barMeters / 1609.344
                if (miles >= 0.25) String.format(Locale.US, "%.2f mi", miles)
                else String.format(Locale.US, "%.0f ft", barMeters * 3.28084)
            }
        }
        val text = "N ↑ • $scaleLabel"
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = monoTf
            textSize = 19f * s
            color = textColor
        }
        val pillH = 38f * s
        val pillW = p.measureText(text) + 30f * s
        val rect = RectF(rightX - pillW, bottomY - pillH, rightX, bottomY)
        canvas.drawRoundRect(rect, 12f * s, 12f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor })
        canvas.drawRoundRect(
            rect,
            12f * s,
            12f * s,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.5f * s
                color = borderColor
            }
        )
        val fm = p.fontMetrics
        canvas.drawText(text, rect.left + 15f * s, rect.centerY() - (fm.ascent + fm.descent) / 2f, p)
    }

    private fun renderClassicStack(
        canvas: Canvas,
        w: Float,
        h: Float,
        s: Float,
        tripName: String,
        distance: String,
        elevation: String,
        movingTime: String,
        points: List<RoutePoint>,
        showTrail: Boolean,
        palette: InkPalette,
        displayTf: Typeface,
        @Suppress("UNUSED_PARAMETER") bodyTf: Typeface,
        monoTf: Typeface
    ) {
        val centerX = w / 2f
        val pad = 92f * s

        drawCenteredText(canvas, "KALYE", centerX, 118f * s, monoPaint(28f * s, 0.24f, palette.accentColor, monoTf, palette))

        if (showTrail) {
            val trailRect = RectF(w * 0.16f, h * 0.14f, w * 0.84f, h * 0.48f)
            drawRouteSilhouette(canvas, points, trailRect, palette.accentColor, palette.primaryText, palette.isTransparent, palette.shadowColor, 12f * s)
        }

        val titleY = if (showTrail) h * 0.54f else h * 0.34f
        drawCenteredMultiline(
            canvas = canvas,
            text = tripName,
            centerX = centerX,
            topY = titleY,
            maxWidth = (w - pad * 2).toInt(),
            paint = textPaint(50f * s, palette.primaryText, displayTf, palette)
        )

        val labelP = monoPaint(26f * s, 0.14f, palette.labelText, monoTf, palette).apply {
            textAlign = Paint.Align.CENTER
        }
        val bigStatP = textPaint(118f * s, palette.primaryText, displayTf, palette).apply {
            textAlign = Paint.Align.CENTER
        }
        val medStatP = textPaint(78f * s, palette.primaryText, displayTf, palette).apply {
            textAlign = Paint.Align.CENTER
        }

        val distBlockY = h * 0.675f
        canvas.drawText("DISTANCE", centerX, distBlockY - 102f * s, labelP)
        canvas.drawText(distance, centerX, distBlockY, bigStatP)

        val rowY = h * 0.86f
        val leftX = w * 0.30f
        val rightX = w * 0.70f
        canvas.drawText("MOVING TIME", leftX, rowY - 72f * s, labelP)
        canvas.drawText(movingTime, leftX, rowY, medStatP)

        canvas.drawText("ELEVATION", rightX, rowY - 72f * s, labelP)
        canvas.drawText(elevation, rightX, rowY, medStatP)
    }

    private fun renderCornerEditorial(
        canvas: Canvas,
        w: Float,
        h: Float,
        s: Float,
        tripName: String,
        distance: String,
        elevation: String,
        movingTime: String,
        points: List<RoutePoint>,
        showTrail: Boolean,
        palette: InkPalette,
        displayTf: Typeface,
        @Suppress("UNUSED_PARAMETER") bodyTf: Typeface,
        monoTf: Typeface
    ) {
        val pad = 84f * s

        if (showTrail) {
            val trailRect = RectF(w * 0.36f, h * 0.08f, w - pad, h * 0.44f)
            drawRouteSilhouette(canvas, points, trailRect, palette.accentColor, palette.primaryText, palette.isTransparent, palette.shadowColor, 11f * s)
        }

        val brandP = monoPaint(26f * s, 0.2f, palette.accentColor, monoTf, palette)
        canvas.drawText("KALYE", pad, h * 0.50f, brandP)

        val titlePaint = textPaint(58f * s, palette.primaryText, displayTf, palette)
        drawLeftMultiline(
            canvas = canvas,
            text = tripName,
            leftX = pad,
            topY = h * 0.53f,
            maxWidth = (w - pad * 2).toInt(),
            paint = titlePaint
        )

        val labelP = monoPaint(25f * s, 0.14f, palette.labelText, monoTf, palette)
        val heroP = textPaint(132f * s, palette.primaryText, displayTf, palette)

        val distY = h * 0.77f
        canvas.drawText("DISTANCE", pad, distY - 114f * s, labelP)
        canvas.drawText(distance, pad, distY, heroP)

        val subP = textPaint(62f * s, palette.primaryText, displayTf, palette)
        val bottomY = h * 0.90f
        canvas.drawText("MOVING TIME", pad, bottomY - 60f * s, labelP)
        canvas.drawText(movingTime, pad, bottomY, subP)

        val col2X = w * 0.54f
        canvas.drawText("ELEVATION", col2X, bottomY - 60f * s, labelP)
        canvas.drawText(elevation, col2X, bottomY, subP)
    }

    private fun renderCompactBar(
        canvas: Canvas,
        w: Float,
        h: Float,
        s: Float,
        tripName: String,
        distance: String,
        elevation: String,
        movingTime: String,
        points: List<RoutePoint>,
        showTrail: Boolean,
        palette: InkPalette,
        displayTf: Typeface,
        @Suppress("UNUSED_PARAMETER") bodyTf: Typeface,
        monoTf: Typeface
    ) {
        val pad = 76f * s

        if (showTrail) {
            val trailRect = RectF(w * 0.18f, h * 0.12f, w * 0.82f, h * 0.58f)
            drawRouteSilhouette(canvas, points, trailRect, palette.accentColor, palette.primaryText, palette.isTransparent, palette.shadowColor, 11f * s)
        }

        val nameY = h * 0.74f
        val brandP = monoPaint(24f * s, 0.2f, palette.accentColor, monoTf, palette)
        canvas.drawText("KALYE", pad, nameY - 56f * s, brandP)

        val titleP = textPaint(48f * s, palette.primaryText, displayTf, palette)
        canvas.drawText(ellipsizeText(tripName, titleP, w - pad * 2), pad, nameY, titleP)

        val divY = h * 0.78f
        val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.dividerColor
            strokeWidth = 2.5f * s
        }
        canvas.drawLine(pad, divY, w - pad, divY, divPaint)

        val statY = h * 0.895f
        val colW = (w - pad * 2) / 3f
        val labelP = monoPaint(23f * s, 0.12f, palette.labelText, monoTf, palette)
        val valP = textPaint(54f * s, palette.primaryText, displayTf, palette)

        val metrics = listOf(
            "DISTANCE" to distance,
            "ELEVATION" to elevation,
            "MOVING TIME" to movingTime
        )
        metrics.forEachIndexed { idx, (lbl, value) ->
            val x = pad + idx * colW
            canvas.drawText(lbl, x, statY - 54f * s, labelP)
            canvas.drawText(value, x, statY, valP)
        }
    }

    private fun renderRouteHero(
        canvas: Canvas,
        w: Float,
        h: Float,
        s: Float,
        tripName: String,
        distance: String,
        elevation: String,
        movingTime: String,
        points: List<RoutePoint>,
        showTrail: Boolean,
        palette: InkPalette,
        displayTf: Typeface,
        @Suppress("UNUSED_PARAMETER") bodyTf: Typeface,
        monoTf: Typeface
    ) {
        val pad = 80f * s
        val centerX = w / 2f

        if (showTrail) {
            val trailRect = RectF(pad, h * 0.09f, w - pad, h * 0.61f)
            drawRouteSilhouette(canvas, points, trailRect, palette.accentColor, palette.primaryText, palette.isTransparent, palette.shadowColor, 14f * s)
        }

        val titleY = h * 0.67f
        drawCenteredText(canvas, "KALYE", centerX, titleY - 46f * s, monoPaint(25f * s, 0.22f, palette.accentColor, monoTf, palette))
        drawCenteredMultiline(
            canvas = canvas,
            text = tripName,
            centerX = centerX,
            topY = titleY - 12f * s,
            maxWidth = (w - pad * 2).toInt(),
            paint = textPaint(50f * s, palette.primaryText, displayTf, palette)
        )

        val statY = h * 0.89f
        val colW = (w - pad * 2) / 3f
        val labelP = monoPaint(23f * s, 0.12f, palette.labelText, monoTf, palette).apply {
            textAlign = Paint.Align.CENTER
        }
        val valP = textPaint(52f * s, palette.primaryText, displayTf, palette).apply {
            textAlign = Paint.Align.CENTER
        }

        val metrics = listOf(
            "DISTANCE" to distance,
            "MOVING TIME" to movingTime,
            "ELEVATION" to elevation
        )
        metrics.forEachIndexed { idx, (lbl, value) ->
            val cx = pad + colW * idx + colW / 2f
            canvas.drawText(lbl, cx, statY - 52f * s, labelP)
            canvas.drawText(value, cx, statY, valP)
        }
    }

    private fun drawCenteredText(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint) {
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, x, y, paint)
    }

    private fun drawCenteredMultiline(
        canvas: Canvas,
        text: String,
        centerX: Float,
        topY: Float,
        maxWidth: Int,
        paint: TextPaint
    ) {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth.coerceAtLeast(100))
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(2)
            .build()
        canvas.save()
        canvas.translate(centerX - maxWidth / 2f, topY)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawLeftMultiline(
        canvas: Canvas,
        text: String,
        leftX: Float,
        topY: Float,
        maxWidth: Int,
        paint: TextPaint
    ) {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth.coerceAtLeast(100))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(2)
            .build()
        canvas.save()
        canvas.translate(leftX, topY)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun ellipsizeText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 3 && paint.measureText(text.substring(0, end) + "…") > maxWidth) {
            end--
        }
        return text.substring(0, end.coerceAtLeast(1)) + "…"
    }

    private fun textPaint(size: Float, color: Int, tf: Typeface, palette: InkPalette): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf
            textSize = size
            this.color = color
            if (palette.isTransparent && palette.shadowColor != Color.TRANSPARENT) {
                setShadowLayer(12f, 0f, 3f, palette.shadowColor)
            }
        }

    private fun monoPaint(
        size: Float,
        tracking: Float,
        color: Int,
        tf: Typeface,
        palette: InkPalette
    ): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = tf
        textSize = size
        letterSpacing = tracking
        this.color = color
        if (palette.isTransparent && palette.shadowColor != Color.TRANSPARENT) {
            setShadowLayer(8f, 0f, 2f, palette.shadowColor)
        }
    }

    private fun drawRouteSilhouette(
        canvas: Canvas,
        points: List<RoutePoint>,
        rect: RectF,
        accentColor: Int,
        dotColor: Int,
        addShadow: Boolean,
        shadowColor: Int,
        strokePx: Float
    ) {
        if (points.size < 2) {
            val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = strokePx
                strokeCap = Paint.Cap.ROUND
                color = accentColor
                if (addShadow && shadowColor != Color.TRANSPARENT) {
                    setShadowLayer(10f, 0f, 3f, shadowColor)
                }
            }
            val p = Path().apply {
                moveTo(rect.left + rect.width() * 0.1f, rect.bottom - rect.height() * 0.25f)
                cubicTo(
                    rect.left + rect.width() * 0.35f, rect.top + rect.height() * 0.1f,
                    rect.left + rect.width() * 0.65f, rect.bottom - rect.height() * 0.15f,
                    rect.right - rect.width() * 0.1f, rect.top + rect.height() * 0.2f
                )
            }
            canvas.drawPath(p, placeholderPaint)
            return
        }

        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLon = points.minOf { it.longitude }
        val maxLon = points.maxOf { it.longitude }
        val latSpan = (maxLat - minLat).coerceAtLeast(0.0005)
        val lonSpan = (maxLon - minLon).coerceAtLeast(0.0005)

        val innerPad = 36f
        val drawW = (rect.width() - innerPad * 2).coerceAtLeast(20f)
        val drawH = (rect.height() - innerPad * 2).coerceAtLeast(20f)
        val scale = minOf(drawW / lonSpan, drawH / latSpan)
        val offsetX = rect.left + (rect.width() - lonSpan * scale).toFloat() / 2f
        val offsetY = rect.top + (rect.height() - latSpan * scale).toFloat() / 2f

        val path = Path()
        var startX = 0f
        var startY = 0f
        var endX = 0f
        var endY = 0f

        points.forEachIndexed { idx, pt ->
            val x = offsetX + ((pt.longitude - minLon) * scale).toFloat()
            val y = offsetY + ((maxLat - pt.latitude) * scale).toFloat()
            if (idx == 0) {
                path.moveTo(x, y)
                startX = x
                startY = y
            } else {
                path.lineTo(x, y)
            }
            endX = x
            endY = y
        }

        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx * 2.1f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.argb(70, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        }
        canvas.drawPath(path, glowPaint)

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = accentColor
            if (addShadow && shadowColor != Color.TRANSPARENT) {
                setShadowLayer(10f, 0f, 3f, shadowColor)
            }
        }
        canvas.drawPath(path, linePaint)

        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dotColor
            if (addShadow && shadowColor != Color.TRANSPARENT) {
                setShadowLayer(8f, 0f, 2f, shadowColor)
            }
        }
        canvas.drawCircle(startX, startY, strokePx * 1.15f, pinPaint)
        canvas.drawCircle(endX, endY, strokePx * 1.35f, pinPaint)
    }

    suspend fun exportAndSharePngAsync(
        context: Context,
        trip: TripSummary,
        points: List<RoutePoint>,
        stops: List<TripStop>,
        config: ShareCardConfig,
        tileManager: OfflineTileCacheManager?
    ): Uri {
        val bitmap = renderToBitmapAsync(
            context = context,
            trip = trip,
            points = points,
            stops = stops,
            config = config,
            tileManager = tileManager
        )
        return shareRenderedBitmap(context, trip, config, bitmap)
    }

    fun exportAndSharePng(
        context: Context,
        trip: TripSummary,
        points: List<RoutePoint>,
        config: ShareCardConfig
    ): Uri {
        val bitmap = renderToBitmap(context, trip, points, config)
        return shareRenderedBitmap(context, trip, config, bitmap)
    }

    private fun shareRenderedBitmap(
        context: Context,
        trip: TripSummary,
        config: ShareCardConfig,
        bitmap: Bitmap
    ): Uri {
        val dir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val filename = buildExportFilename()
        val file = File(dir, filename)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val isRoadmap = config.layout == ShareCardLayout.ROADMAP_CARD
        val subject = if (isRoadmap) "${trip.title} — Kalye Travel Roadmap" else "${trip.title} — Kalye Ride Sticker"
        val chooserTitle = if (isRoadmap) "Share Kalye Travel Roadmap" else "Share Kalye Transparent PNG"
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, chooserTitle))
        return uri
    }

    suspend fun savePngToGalleryAsync(
        context: Context,
        trip: TripSummary,
        points: List<RoutePoint>,
        stops: List<TripStop>,
        config: ShareCardConfig,
        tileManager: OfflineTileCacheManager?
    ): Result<String> {
        val bitmap = renderToBitmapAsync(
            context = context,
            trip = trip,
            points = points,
            stops = stops,
            config = config,
            tileManager = tileManager
        )
        return saveRenderedBitmap(context, bitmap)
    }

    fun savePngToGallery(
        context: Context,
        trip: TripSummary,
        points: List<RoutePoint>,
        config: ShareCardConfig
    ): Result<String> {
        val bitmap = renderToBitmap(context, trip, points, config)
        return saveRenderedBitmap(context, bitmap)
    }

    private fun saveRenderedBitmap(context: Context, bitmap: Bitmap): Result<String> = runCatching {
        val filename = buildExportFilename()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Kalye")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Unable to create image entry")
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: error("Unable to open image stream")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Kalye").apply {
                if (!exists()) mkdirs()
            }
            val file = File(dir, filename)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
        filename
    }

    private data class InkPalette(
        val isTransparent: Boolean,
        val primaryText: Int,
        val labelText: Int,
        val accentColor: Int,
        val shadowColor: Int,
        val dividerColor: Int
    )

    private data class RoadmapPalette(
        val isDarkMap: Boolean,
        val canvasBg: Int,
        val gridColor: Int,
        val contourColor: Int,
        val routeColor: Int,
        val routeCasing: Int,
        val routeGlow: Int,
        val startPin: Int,
        val endPin: Int,
        val hudFill: Int,
        val hudBorder: Int,
        val primaryText: Int,
        val mutedText: Int,
        val accentText: Int,
        val chipFill: Int,
        val chipText: Int,
        val badgeFill: Int,
        val badgeText: Int,
        val divider: Int,
        val saturation: Float
    )
}
