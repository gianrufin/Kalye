package com.example.export

import android.content.ContentValues
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
import com.example.data.model.RoutePoint
import com.example.data.model.ShareCardInkStyle
import com.example.data.model.ShareCardLayout
import com.example.data.model.TripSummary
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ShareCardConfig(
    val layout: ShareCardLayout = ShareCardLayout.CLASSIC_STACK,
    val inkStyle: ShareCardInkStyle = ShareCardInkStyle.WHITE_TRANSPARENT,
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
    val showTrailLine: Boolean = true
)

object ShareCardRenderer {

    fun buildExportFilename(nowMillis: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(nowMillis))
        return "kalye_${stamp}.png"
    }

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
            canvas.drawRoundRect(cardRect, 48f, 48f, bgPaint)
        }

        val scale = width / 1080f
        val distNum = GeoUtils.distanceNumber(trip.distanceMeters, config.distanceUnit)
        val distUnit = config.distanceUnit.shortLabel.lowercase(Locale.US)
        val distanceText = "$distNum $distUnit"
        val elevationText = "+${GeoUtils.formatElevation(trip.elevationGainMeters, config.distanceUnit)}"
        val movingTimeText = GeoUtils.formatMovingTime(trip.effectiveMovingMillis)
        val tripName = trip.title.ifBlank { "Overland Trip" }

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
        }

        return bitmap
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
        bodyTf: Typeface,
        monoTf: Typeface
    ) {
        val centerX = w / 2f
        val pad = 92f * s

        // Brand tag centered at top
        drawCenteredText(canvas, "KALYE", centerX, 118f * s, monoPaint(28f * s, 0.24f, palette.accentColor, monoTf, palette))

        // Travelled trail line upper-center
        if (showTrail) {
            val trailRect = RectF(w * 0.16f, h * 0.14f, w * 0.84f, h * 0.48f)
            drawRouteSilhouette(canvas, points, trailRect, palette.accentColor, palette.primaryText, palette.isTransparent, palette.shadowColor, 12f * s)
        }

        // Trip Name centered
        val titleY = if (showTrail) h * 0.54f else h * 0.34f
        drawCenteredMultiline(
            canvas = canvas,
            text = tripName,
            centerX = centerX,
            topY = titleY,
            maxWidth = (w - pad * 2).toInt(),
            paint = textPaint(50f * s, palette.primaryText, displayTf, palette)
        )

        // Stacked Strava Metrics (Distance -> Moving Time -> Elevation)
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
        bodyTf: Typeface,
        monoTf: Typeface
    ) {
        val pad = 84f * s

        // Floating trail line in upper-right quadrant
        if (showTrail) {
            val trailRect = RectF(w * 0.36f, h * 0.08f, w - pad, h * 0.44f)
            drawRouteSilhouette(canvas, points, trailRect, palette.accentColor, palette.primaryText, palette.isTransparent, palette.shadowColor, 11f * s)
        }

        // Trip Name & editorial header bottom-left
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
        bodyTf: Typeface,
        monoTf: Typeface
    ) {
        val pad = 76f * s

        // Trail floats in upper canvas so bottom third stays clean over subject photos
        if (showTrail) {
            val trailRect = RectF(w * 0.18f, h * 0.12f, w * 0.82f, h * 0.58f)
            drawRouteSilhouette(canvas, points, trailRect, palette.accentColor, palette.primaryText, palette.isTransparent, palette.shadowColor, 11f * s)
        }

        // Trip Name & brand right above bottom stat strip
        val nameY = h * 0.74f
        val brandP = monoPaint(24f * s, 0.2f, palette.accentColor, monoTf, palette)
        canvas.drawText("KALYE", pad, nameY - 56f * s, brandP)

        val titleP = textPaint(48f * s, palette.primaryText, displayTf, palette)
        canvas.drawText(ellipsizeText(tripName, titleP, w - pad * 2), pad, nameY, titleP)

        // Subtle divider line
        val divY = h * 0.78f
        val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.dividerColor
            strokeWidth = 2.5f * s
        }
        canvas.drawLine(pad, divY, w - pad, divY, divPaint)

        // Crisp 3-column horizontal stat bar: Distance | Elevation | Moving Time
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
        bodyTf: Typeface,
        monoTf: Typeface
    ) {
        val pad = 80f * s
        val centerX = w / 2f

        // Large prominent trail outline taking top 58% of card
        if (showTrail) {
            val trailRect = RectF(pad, h * 0.09f, w - pad, h * 0.61f)
            drawRouteSilhouette(canvas, points, trailRect, palette.accentColor, palette.primaryText, palette.isTransparent, palette.shadowColor, 14f * s)
        }

        // Trip name centered below trail
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

        // Centered 3-column stat bar near bottom
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

    fun exportAndSharePng(
        context: Context,
        trip: TripSummary,
        points: List<RoutePoint>,
        config: ShareCardConfig
    ): Uri {
        val bitmap = renderToBitmap(context, trip, points, config)
        val dir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val filename = buildExportFilename()
        val file = File(dir, filename)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "${trip.title} — Kalye Ride Sticker")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Kalye Transparent PNG"))
        return uri
    }

    fun savePngToGallery(
        context: Context,
        trip: TripSummary,
        points: List<RoutePoint>,
        config: ShareCardConfig
    ): Result<String> = runCatching {
        val bitmap = renderToBitmap(context, trip, points, config)
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
}
