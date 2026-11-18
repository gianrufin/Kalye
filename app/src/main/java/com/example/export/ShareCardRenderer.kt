package com.example.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.data.model.DistanceUnit
import com.example.data.model.GeoUtils
import com.example.data.model.RoutePoint
import com.example.data.model.ShareCardTheme
import com.example.data.model.TripSummary
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ShareCardConfig(
    val theme: ShareCardTheme = ShareCardTheme.TOPO_DARK,
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
    val showRouteSilhouette: Boolean = true,
    val showElevationGain: Boolean = true,
    val showDuration: Boolean = false,
    val showTitleAndTag: Boolean = true,
    val showDate: Boolean = true,
    val showCost: Boolean = false
)

object ShareCardRenderer {

    fun renderToBitmap(
        context: Context,
        trip: TripSummary,
        points: List<RoutePoint>,
        config: ShareCardConfig,
        width: Int = 1080,
        height: Int = 1350
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val displayTypeface = ResourcesCompat.getFont(context, R.font.space_grotesk) ?: Typeface.DEFAULT_BOLD
        val bodyTypeface = ResourcesCompat.getFont(context, R.font.plus_jakarta_sans) ?: Typeface.DEFAULT
        val monoTypeface = ResourcesCompat.getFont(context, R.font.jetbrains_mono) ?: Typeface.MONOSPACE

        val (bgTop, bgBottom, primaryText, mutedText, accentColor) = when (config.theme) {
            ShareCardTheme.TOPO_DARK -> CardPalette(
                Color.parseColor("#0E1310"),
                Color.parseColor("#18211C"),
                Color.parseColor("#F5F2EA"),
                Color.parseColor("#9BA398"),
                Color.parseColor("#E86F48")
            )
            ShareCardTheme.DESERT_SAND -> CardPalette(
                Color.parseColor("#F6F1E5"),
                Color.parseColor("#EAE0CC"),
                Color.parseColor("#1D1A16"),
                Color.parseColor("#6E6558"),
                Color.parseColor("#B84A27")
            )
            ShareCardTheme.ALPINE_PINE -> CardPalette(
                Color.parseColor("#162B22"),
                Color.parseColor("#0D1C16"),
                Color.parseColor("#F3F7F4"),
                Color.parseColor("#9CC0AF"),
                Color.parseColor("#E5A93B")
            )
            ShareCardTheme.TRANSPARENT_GLASS -> CardPalette(
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                Color.WHITE,
                Color.parseColor("#E0E0E0"),
                Color.parseColor("#FF794D")
            )
        }

        if (config.theme == ShareCardTheme.TRANSPARENT_GLASS) {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        } else {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, 0f, height.toFloat(), bgTop, bgBottom, Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            // Subtle topographic contour rings
            val contourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
                color = Color.argb(26, Color.red(mutedText), Color.green(mutedText), Color.blue(mutedText))
            }
            for (i in 1..6) {
                val r = 180f * i
                canvas.drawCircle(width * 0.85f, height * 0.22f, r, contourPaint)
            }
        }

        val pad = 84f
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = monoTypeface
            textSize = 32f
            letterSpacing = 0.18f
            color = accentColor
            if (config.theme == ShareCardTheme.TRANSPARENT_GLASS) {
                setShadowLayer(8f, 0f, 2f, Color.argb(160, 0, 0, 0))
            }
        }
        canvas.drawText("KALYE • OVERLAND", pad, 118f, brandPaint)

        if (config.showDate) {
            val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(trip.startTimeMillis))
            val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = monoTypeface
                textSize = 28f
                textAlign = Paint.Align.RIGHT
                color = mutedText
                if (config.theme == ShareCardTheme.TRANSPARENT_GLASS) {
                    setShadowLayer(8f, 0f, 2f, Color.argb(160, 0, 0, 0))
                }
            }
            canvas.drawText(dateStr.uppercase(Locale.US), width - pad, 118f, datePaint)
        }

        var cursorY = 195f
        if (config.showTitleAndTag) {
            val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = bodyTypeface
                textSize = 30f
                color = accentColor
                if (config.theme == ShareCardTheme.TRANSPARENT_GLASS) {
                    setShadowLayer(8f, 0f, 2f, Color.argb(160, 0, 0, 0))
                }
            }
            canvas.drawText(trip.categoryTag.uppercase(Locale.US), pad, cursorY, tagPaint)
            cursorY += 56f

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = displayTypeface
                textSize = 54f
                color = primaryText
                if (config.theme == ShareCardTheme.TRANSPARENT_GLASS) {
                    setShadowLayer(10f, 0f, 3f, Color.argb(170, 0, 0, 0))
                }
            }
            canvas.drawText(trip.title.take(26), pad, cursorY, titlePaint)
            cursorY += 48f
        }

        // Route Silhouette in center zone
        if (config.showRouteSilhouette) {
            val mapRect = RectF(pad, cursorY + 40f, width - pad, height - 430f)
            drawRouteSilhouette(canvas, points, mapRect, accentColor, primaryText, config.theme == ShareCardTheme.TRANSPARENT_GLASS)
        }

        // Hero Mileage Stat (bottom-anchored Strava-style)
        val heroY = height - 270f
        val distLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = monoTypeface
            textSize = 30f
            letterSpacing = 0.14f
            color = mutedText
            if (config.theme == ShareCardTheme.TRANSPARENT_GLASS) {
                setShadowLayer(8f, 0f, 2f, Color.argb(170, 0, 0, 0))
            }
        }
        canvas.drawText("DISTANCE", pad, heroY - 125f, distLabelPaint)

        val distNum = GeoUtils.distanceNumber(trip.distanceMeters, config.distanceUnit)
        val unitLabel = config.distanceUnit.shortLabel.uppercase(Locale.US)

        val heroPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = displayTypeface
            textSize = 148f
            color = primaryText
            if (config.theme == ShareCardTheme.TRANSPARENT_GLASS) {
                setShadowLayer(14f, 0f, 4f, Color.argb(190, 0, 0, 0))
            }
        }
        canvas.drawText(distNum, pad, heroY, heroPaint)

        val numWidth = heroPaint.measureText(distNum)
        val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = displayTypeface
            textSize = 56f
            color = accentColor
            if (config.theme == ShareCardTheme.TRANSPARENT_GLASS) {
                setShadowLayer(10f, 0f, 3f, Color.argb(180, 0, 0, 0))
            }
        }
        canvas.drawText(" $unitLabel", pad + numWidth, heroY, unitPaint)

        // Secondary optional overlay stats row
        val secondaryStats = buildList {
            if (config.showElevationGain) {
                add("ELEV GAIN" to "+${GeoUtils.formatElevation(trip.elevationGainMeters, config.distanceUnit)}")
            }
            if (config.showDuration) {
                add("TIME" to GeoUtils.formatDuration(trip.durationMillis))
            }
            if (config.showCost) {
                add("EXPEDITION COST" to GeoUtils.formatCurrency(trip.totalCost, trip.currencyCode))
            }
        }

        if (secondaryStats.isNotEmpty()) {
            val rowY = height - 115f
            val colWidth = (width - pad * 2) / secondaryStats.size.coerceAtLeast(1)
            val statLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = monoTypeface
                textSize = 24f
                color = mutedText
                if (config.theme == ShareCardTheme.TRANSPARENT_GLASS) {
                    setShadowLayer(8f, 0f, 2f, Color.argb(180, 0, 0, 0))
                }
            }
            val statValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = displayTypeface
                textSize = 44f
                color = primaryText
                if (config.theme == ShareCardTheme.TRANSPARENT_GLASS) {
                    setShadowLayer(10f, 0f, 3f, Color.argb(180, 0, 0, 0))
                }
            }
            secondaryStats.forEachIndexed { idx, (lbl, value) ->
                val x = pad + idx * colWidth
                canvas.drawText(lbl, x, rowY - 48f, statLabelPaint)
                canvas.drawText(value, x, rowY, statValuePaint)
            }
        }

        return bitmap
    }

    private fun drawRouteSilhouette(
        canvas: Canvas,
        points: List<RoutePoint>,
        rect: RectF,
        accentColor: Int,
        dotColor: Int,
        addShadow: Boolean
    ) {
        if (points.size < 2) {
            val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 10f
                strokeCap = Paint.Cap.ROUND
                color = accentColor
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

        val innerPad = 48f
        val drawW = rect.width() - innerPad * 2
        val drawH = rect.height() - innerPad * 2
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
            strokeWidth = 22f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.argb(65, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        }
        canvas.drawPath(path, glowPaint)

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 11f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = accentColor
            if (addShadow) {
                setShadowLayer(10f, 0f, 3f, Color.argb(170, 0, 0, 0))
            }
        }
        canvas.drawPath(path, linePaint)

        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dotColor }
        canvas.drawCircle(startX, startY, 14f, pinPaint)
        canvas.drawCircle(endX, endY, 16f, pinPaint)
    }

    fun exportAndSharePng(
        context: Context,
        trip: TripSummary,
        points: List<RoutePoint>,
        config: ShareCardConfig
    ): Uri {
        val bitmap = renderToBitmap(context, trip, points, config)
        val dir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val safeName = trip.title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(28).ifBlank { "kalye_card" }
        val file = File(dir, "${safeName}_card.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "${trip.title} — Kalye Ride Card")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Kalye PNG Card"))
        return uri
    }

    private data class CardPalette(
        val bgTop: Int,
        val bgBottom: Int,
        val primaryText: Int,
        val mutedText: Int,
        val accentColor: Int
    )
}
