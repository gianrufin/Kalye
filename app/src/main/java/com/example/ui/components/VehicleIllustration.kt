package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.example.data.model.VehicleBodyStyle

@Composable
fun VehicleRigIllustration(
    bodyStyle: VehicleBodyStyle,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Ground shadow
        drawOval(
            color = Color.Black.copy(alpha = 0.14f),
            topLeft = Offset(w * 0.10f, h * 0.80f),
            size = Size(w * 0.80f, h * 0.10f)
        )

        val darkBody = lerp(accentColor, Color.Black, 0.28f)
        val highlightBody = lerp(accentColor, Color.White, 0.20f)
        val windowGlass = Color(0xFF223035)
        val trimColor = Color(0xFF1E2220)

        when (bodyStyle) {
            VehicleBodyStyle.SUV_4X4 -> drawSuv(w, h, accentColor, darkBody, highlightBody, windowGlass, trimColor)
            VehicleBodyStyle.PICKUP -> drawPickup(w, h, accentColor, darkBody, highlightBody, windowGlass, trimColor)
            VehicleBodyStyle.SEDAN -> drawSedan(w, h, accentColor, darkBody, highlightBody, windowGlass, trimColor)
            VehicleBodyStyle.VAN -> drawVan(w, h, accentColor, darkBody, highlightBody, windowGlass, trimColor)
            VehicleBodyStyle.MOTO -> drawMoto(w, h, accentColor, darkBody, trimColor)
        }
    }
}

private fun DrawScope.drawWheel(cx: Float, cy: Float, radius: Float, trim: Color) {
    drawCircle(color = trim, radius = radius, center = Offset(cx, cy))
    drawCircle(color = Color(0xFF3E4541), radius = radius * 0.72f, center = Offset(cx, cy))
    drawCircle(color = Color(0xFFD9DDD8), radius = radius * 0.45f, center = Offset(cx, cy))
    drawCircle(color = Color(0xFF5A625D), radius = radius * 0.16f, center = Offset(cx, cy))
}

private fun DrawScope.drawSuv(
    w: Float,
    h: Float,
    body: Color,
    darkBody: Color,
    highlight: Color,
    glass: Color,
    trim: Color
) {
    // Spare tire mounted on back
    drawRoundRect(
        color = trim,
        topLeft = Offset(w * 0.08f, h * 0.40f),
        size = Size(w * 0.06f, h * 0.24f),
        cornerRadius = CornerRadius(w * 0.02f, w * 0.02f)
    )

    // Roof rack
    drawLine(
        color = trim,
        start = Offset(w * 0.20f, h * 0.21f),
        end = Offset(w * 0.62f, h * 0.21f),
        strokeWidth = h * 0.035f,
        cap = StrokeCap.Round
    )
    drawLine(color = trim, start = Offset(w * 0.26f, h * 0.21f), end = Offset(w * 0.26f, h * 0.26f), strokeWidth = h * 0.025f)
    drawLine(color = trim, start = Offset(w * 0.54f, h * 0.21f), end = Offset(w * 0.54f, h * 0.26f), strokeWidth = h * 0.025f)

    val silhouette = Path().apply {
        moveTo(w * 0.13f, h * 0.68f)
        lineTo(w * 0.13f, h * 0.44f)
        lineTo(w * 0.19f, h * 0.26f)
        lineTo(w * 0.62f, h * 0.26f)
        lineTo(w * 0.73f, h * 0.43f)
        lineTo(w * 0.87f, h * 0.47f)
        lineTo(w * 0.89f, h * 0.68f)
        close()
    }
    drawPath(silhouette, color = body)
    drawPath(silhouette, color = trim.copy(alpha = 0.24f), style = Stroke(width = h * 0.018f, join = StrokeJoin.Round))

    // Lower cladding & bumper
    drawRoundRect(
        color = darkBody,
        topLeft = Offset(w * 0.12f, h * 0.63f),
        size = Size(w * 0.78f, h * 0.08f),
        cornerRadius = CornerRadius(h * 0.03f, h * 0.03f)
    )

    // Side highlight stripe
    drawLine(
        color = highlight,
        start = Offset(w * 0.16f, h * 0.47f),
        end = Offset(w * 0.84f, h * 0.49f),
        strokeWidth = h * 0.022f,
        cap = StrokeCap.Round
    )

    // Side windows
    val winPath = Path().apply {
        moveTo(w * 0.21f, h * 0.29f)
        lineTo(w * 0.59f, h * 0.29f)
        lineTo(w * 0.69f, h * 0.42f)
        lineTo(w * 0.18f, h * 0.42f)
        close()
    }
    drawPath(winPath, color = glass)
    drawLine(color = body, start = Offset(w * 0.40f, h * 0.28f), end = Offset(w * 0.40f, h * 0.43f), strokeWidth = w * 0.022f)
    drawLine(color = body, start = Offset(w * 0.54f, h * 0.28f), end = Offset(w * 0.54f, h * 0.43f), strokeWidth = w * 0.018f)

    // Headlight
    drawRoundRect(
        color = Color(0xFFFFE59E),
        topLeft = Offset(w * 0.83f, h * 0.49f),
        size = Size(w * 0.05f, h * 0.07f),
        cornerRadius = CornerRadius(4f, 4f)
    )

    drawWheel(w * 0.27f, h * 0.71f, h * 0.125f, trim)
    drawWheel(w * 0.74f, h * 0.71f, h * 0.125f, trim)
}

private fun DrawScope.drawPickup(
    w: Float,
    h: Float,
    body: Color,
    darkBody: Color,
    highlight: Color,
    glass: Color,
    trim: Color
) {
    // Roll bar / bed rack
    val rack = Path().apply {
        moveTo(w * 0.14f, h * 0.44f)
        lineTo(w * 0.20f, h * 0.27f)
        lineTo(w * 0.42f, h * 0.27f)
    }
    drawPath(rack, color = trim, style = Stroke(width = h * 0.032f, cap = StrokeCap.Round, join = StrokeJoin.Round))

    val silhouette = Path().apply {
        moveTo(w * 0.11f, h * 0.68f)
        lineTo(w * 0.11f, h * 0.45f)
        lineTo(w * 0.42f, h * 0.45f)
        lineTo(w * 0.45f, h * 0.26f)
        lineTo(w * 0.66f, h * 0.26f)
        lineTo(w * 0.75f, h * 0.44f)
        lineTo(w * 0.89f, h * 0.47f)
        lineTo(w * 0.90f, h * 0.68f)
        close()
    }
    drawPath(silhouette, color = body)
    drawPath(silhouette, color = trim.copy(alpha = 0.24f), style = Stroke(width = h * 0.018f, join = StrokeJoin.Round))

    drawRoundRect(
        color = darkBody,
        topLeft = Offset(w * 0.10f, h * 0.63f),
        size = Size(w * 0.81f, h * 0.075f),
        cornerRadius = CornerRadius(h * 0.03f, h * 0.03f)
    )

    drawLine(
        color = highlight,
        start = Offset(w * 0.13f, h * 0.48f),
        end = Offset(w * 0.86f, h * 0.49f),
        strokeWidth = h * 0.02f,
        cap = StrokeCap.Round
    )

    val cabWindow = Path().apply {
        moveTo(w * 0.47f, h * 0.29f)
        lineTo(w * 0.64f, h * 0.29f)
        lineTo(w * 0.72f, h * 0.43f)
        lineTo(w * 0.46f, h * 0.43f)
        close()
    }
    drawPath(cabWindow, color = glass)
    drawLine(color = body, start = Offset(w * 0.56f, h * 0.28f), end = Offset(w * 0.56f, h * 0.44f), strokeWidth = w * 0.02f)

    drawRoundRect(
        color = Color(0xFFFFE59E),
        topLeft = Offset(w * 0.84f, h * 0.49f),
        size = Size(w * 0.05f, h * 0.065f),
        cornerRadius = CornerRadius(4f, 4f)
    )

    drawWheel(w * 0.26f, h * 0.71f, h * 0.125f, trim)
    drawWheel(w * 0.75f, h * 0.71f, h * 0.125f, trim)
}

private fun DrawScope.drawSedan(
    w: Float,
    h: Float,
    body: Color,
    darkBody: Color,
    highlight: Color,
    glass: Color,
    trim: Color
) {
    val silhouette = Path().apply {
        moveTo(w * 0.11f, h * 0.69f)
        lineTo(w * 0.12f, h * 0.52f)
        lineTo(w * 0.23f, h * 0.48f)
        lineTo(w * 0.36f, h * 0.31f)
        lineTo(w * 0.63f, h * 0.31f)
        lineTo(w * 0.75f, h * 0.48f)
        lineTo(w * 0.89f, h * 0.53f)
        lineTo(w * 0.90f, h * 0.69f)
        close()
    }
    drawPath(silhouette, color = body)
    drawPath(silhouette, color = trim.copy(alpha = 0.24f), style = Stroke(width = h * 0.018f, join = StrokeJoin.Round))

    drawRoundRect(
        color = darkBody,
        topLeft = Offset(w * 0.11f, h * 0.65f),
        size = Size(w * 0.79f, h * 0.055f),
        cornerRadius = CornerRadius(h * 0.02f, h * 0.02f)
    )

    drawLine(
        color = highlight,
        start = Offset(w * 0.16f, h * 0.53f),
        end = Offset(w * 0.84f, h * 0.55f),
        strokeWidth = h * 0.02f,
        cap = StrokeCap.Round
    )

    val winPath = Path().apply {
        moveTo(w * 0.37f, h * 0.34f)
        lineTo(w * 0.61f, h * 0.34f)
        lineTo(w * 0.71f, h * 0.47f)
        lineTo(w * 0.27f, h * 0.47f)
        close()
    }
    drawPath(winPath, color = glass)
    drawLine(color = body, start = Offset(w * 0.49f, h * 0.33f), end = Offset(w * 0.49f, h * 0.48f), strokeWidth = w * 0.02f)

    drawRoundRect(
        color = Color(0xFFFFE59E),
        topLeft = Offset(w * 0.84f, h * 0.55f),
        size = Size(w * 0.05f, h * 0.055f),
        cornerRadius = CornerRadius(4f, 4f)
    )

    drawWheel(w * 0.27f, h * 0.71f, h * 0.115f, trim)
    drawWheel(w * 0.74f, h * 0.71f, h * 0.115f, trim)
}

private fun DrawScope.drawVan(
    w: Float,
    h: Float,
    body: Color,
    darkBody: Color,
    highlight: Color,
    glass: Color,
    trim: Color
) {
    // Awning bar on roof
    drawRoundRect(
        color = trim,
        topLeft = Offset(w * 0.18f, h * 0.21f),
        size = Size(w * 0.50f, h * 0.045f),
        cornerRadius = CornerRadius(4f, 4f)
    )

    val silhouette = Path().apply {
        moveTo(w * 0.13f, h * 0.69f)
        lineTo(w * 0.13f, h * 0.26f)
        lineTo(w * 0.69f, h * 0.26f)
        lineTo(w * 0.80f, h * 0.46f)
        lineTo(w * 0.88f, h * 0.51f)
        lineTo(w * 0.89f, h * 0.69f)
        close()
    }
    drawPath(silhouette, color = body)
    drawPath(silhouette, color = trim.copy(alpha = 0.24f), style = Stroke(width = h * 0.018f, join = StrokeJoin.Round))

    drawRoundRect(
        color = darkBody,
        topLeft = Offset(w * 0.12f, h * 0.64f),
        size = Size(w * 0.78f, h * 0.065f),
        cornerRadius = CornerRadius(h * 0.02f, h * 0.02f)
    )

    drawLine(
        color = highlight,
        start = Offset(w * 0.16f, h * 0.48f),
        end = Offset(w * 0.84f, h * 0.51f),
        strokeWidth = h * 0.022f,
        cap = StrokeCap.Round
    )

    val frontWin = Path().apply {
        moveTo(w * 0.59f, h * 0.29f)
        lineTo(w * 0.67f, h * 0.29f)
        lineTo(w * 0.76f, h * 0.45f)
        lineTo(w * 0.59f, h * 0.45f)
        close()
    }
    drawPath(frontWin, color = glass)
    drawRoundRect(
        color = glass,
        topLeft = Offset(w * 0.22f, h * 0.30f),
        size = Size(w * 0.30f, h * 0.14f),
        cornerRadius = CornerRadius(6f, 6f)
    )

    drawRoundRect(
        color = Color(0xFFFFE59E),
        topLeft = Offset(w * 0.83f, h * 0.52f),
        size = Size(w * 0.048f, h * 0.06f),
        cornerRadius = CornerRadius(4f, 4f)
    )

    drawWheel(w * 0.27f, h * 0.71f, h * 0.118f, trim)
    drawWheel(w * 0.74f, h * 0.71f, h * 0.118f, trim)
}

private fun DrawScope.drawMoto(
    w: Float,
    h: Float,
    body: Color,
    darkBody: Color,
    trim: Color
) {
    drawWheel(w * 0.26f, h * 0.69f, h * 0.135f, trim)
    drawWheel(w * 0.75f, h * 0.69f, h * 0.135f, trim)

    // Frame & engine block
    drawLine(
        color = trim,
        start = Offset(w * 0.26f, h * 0.69f),
        end = Offset(w * 0.46f, h * 0.54f),
        strokeWidth = h * 0.045f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = Color(0xFF6B736E),
        start = Offset(w * 0.60f, h * 0.32f),
        end = Offset(w * 0.75f, h * 0.69f),
        strokeWidth = h * 0.042f,
        cap = StrokeCap.Round
    )
    drawRoundRect(
        color = Color(0xFF454C47),
        topLeft = Offset(w * 0.41f, h * 0.51f),
        size = Size(w * 0.20f, h * 0.16f),
        cornerRadius = CornerRadius(8f, 8f)
    )

    // Pannier box + seat + fuel tank + beak fairing
    drawRoundRect(
        color = darkBody,
        topLeft = Offset(w * 0.16f, h * 0.43f),
        size = Size(w * 0.16f, h * 0.16f),
        cornerRadius = CornerRadius(6f, 6f)
    )
    val tankPath = Path().apply {
        moveTo(w * 0.30f, h * 0.44f)
        lineTo(w * 0.48f, h * 0.44f)
        lineTo(w * 0.56f, h * 0.34f)
        lineTo(w * 0.70f, h * 0.38f)
        lineTo(w * 0.62f, h * 0.52f)
        lineTo(w * 0.32f, h * 0.50f)
        close()
    }
    drawPath(tankPath, color = body)
    drawPath(tankPath, color = trim.copy(alpha = 0.24f), style = Stroke(width = h * 0.018f, join = StrokeJoin.Round))

    // Adventure windscreen
    drawLine(
        color = Color(0xFF9AC4D0),
        start = Offset(w * 0.65f, h * 0.36f),
        end = Offset(w * 0.70f, h * 0.23f),
        strokeWidth = w * 0.025f,
        cap = StrokeCap.Round
    )
}
