package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.example.data.model.VehicleBodyStyle
import com.example.data.model.VehicleRigGear
import kotlin.math.cos
import kotlin.math.sin

private data class RigPalette(
    val bodyBrush: Brush,
    val base: Color,
    val highlight: Color,
    val shadow: Color,
    val outline: Color,
    val glassBrush: Brush,
    val glassGloss: Color,
    val trim: Color,
    val cladding: Color,
    val headlight: Color,
    val taillight: Color,
    val metalAccent: Color
)

@Composable
fun VehicleRigIllustration(
    bodyStyle: VehicleBodyStyle,
    accentColor: Color,
    modifier: Modifier = Modifier,
    rigGear: VehicleRigGear = VehicleRigGear.STOCK
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // Soft ambient ground shadow
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color.Black.copy(alpha = 0.24f), Color.Transparent),
                center = Offset(w * 0.50f, h * 0.84f),
                radius = w * 0.44f
            ),
            topLeft = Offset(w * 0.08f, h * 0.78f),
            size = Size(w * 0.84f, h * 0.14f)
        )

        val luminance = (accentColor.red * 0.299f + accentColor.green * 0.587f + accentColor.blue * 0.114f)
        val isVeryLight = luminance > 0.78f

        val highlight = lerp(accentColor, Color.White, if (isVeryLight) 0.12f else 0.30f)
        val shadow = lerp(accentColor, Color.Black, if (isVeryLight) 0.18f else 0.36f)
        val outline = if (isVeryLight) Color(0xFF313934).copy(alpha = 0.48f) else Color(0xFF141815).copy(alpha = 0.36f)

        val palette = RigPalette(
            bodyBrush = Brush.verticalGradient(
                colors = listOf(highlight, accentColor, shadow),
                startY = h * 0.22f,
                endY = h * 0.76f
            ),
            base = accentColor,
            highlight = highlight,
            shadow = shadow,
            outline = outline,
            glassBrush = Brush.verticalGradient(
                colors = listOf(Color(0xFF2D434D), Color(0xFF152126)),
                startY = h * 0.22f,
                endY = h * 0.50f
            ),
            glassGloss = Color.White.copy(alpha = 0.20f),
            trim = Color(0xFF1C211E),
            cladding = Color(0xFF282F2B),
            headlight = Color(0xFFFFF2C2),
            taillight = Color(0xFFE14B3B),
            metalAccent = Color(0xFFDCE2DD)
        )

        when (bodyStyle) {
            VehicleBodyStyle.SUV_4X4 -> drawSuv4x4(w, h, palette, rigGear)
            VehicleBodyStyle.CROSSOVER -> drawCrossover(w, h, palette, rigGear)
            VehicleBodyStyle.PICKUP -> drawPickup(w, h, palette, rigGear)
            VehicleBodyStyle.SEDAN -> drawSedan(w, h, palette, rigGear)
            VehicleBodyStyle.HATCHBACK -> drawHatchback(w, h, palette, rigGear)
            VehicleBodyStyle.COUPE -> drawCoupe(w, h, palette, rigGear)
            VehicleBodyStyle.VAN -> drawVan(w, h, palette, rigGear)
            VehicleBodyStyle.MOTO -> drawMoto(w, h, palette, rigGear)
        }
    }
}

private fun DrawScope.drawAlloyWheel(
    cx: Float,
    cy: Float,
    radius: Float,
    palette: RigPalette,
    spokes: Int = 6,
    offroadTread: Boolean = false
) {
    // Wheel arch shadow behind tire
    drawCircle(
        color = Color(0xFF111412),
        radius = radius * 1.18f,
        center = Offset(cx, cy - radius * 0.05f)
    )
    // Outer tire
    drawCircle(color = palette.trim, radius = radius, center = Offset(cx, cy))
    if (offroadTread) {
        drawCircle(
            color = Color(0xFF111412),
            radius = radius * 0.96f,
            center = Offset(cx, cy),
            style = Stroke(width = radius * 0.14f)
        )
    }
    // Sidewall ring
    drawCircle(color = Color(0xFF383F3B), radius = radius * 0.72f, center = Offset(cx, cy))
    // Alloy rim bed
    drawCircle(color = Color(0xFF1E2421), radius = radius * 0.56f, center = Offset(cx, cy))
    // Rim lip
    drawCircle(
        color = palette.metalAccent,
        radius = radius * 0.54f,
        center = Offset(cx, cy),
        style = Stroke(width = radius * 0.09f)
    )
    // Spokes
    for (i in 0 until spokes) {
        val angle = (i * (360f / spokes)) * (Math.PI / 180.0)
        val sx = cx + (cos(angle) * radius * 0.48f).toFloat()
        val sy = cy + (sin(angle) * radius * 0.48f).toFloat()
        drawLine(
            color = palette.metalAccent,
            start = Offset(cx, cy),
            end = Offset(sx, sy),
            strokeWidth = radius * 0.10f,
            cap = StrokeCap.Round
        )
    }
    // Center hub cap
    drawCircle(color = Color(0xFF4F5752), radius = radius * 0.19f, center = Offset(cx, cy))
    drawCircle(color = palette.metalAccent, radius = radius * 0.08f, center = Offset(cx, cy))
}

private fun DrawScope.drawGlassGloss(
    startX: Float,
    topY: Float,
    endX: Float,
    bottomY: Float,
    gloss: Color
) {
    val glossPath = Path().apply {
        moveTo(startX, topY)
        lineTo(startX + (endX - startX) * 0.24f, topY)
        lineTo(startX + (endX - startX) * 0.12f, bottomY)
        lineTo(startX - (endX - startX) * 0.06f, bottomY)
        close()
    }
    drawPath(glossPath, color = gloss)
}

private fun DrawScope.drawRoofAccessories(
    w: Float,
    h: Float,
    roofStartX: Float,
    roofEndX: Float,
    roofY: Float,
    palette: RigPalette,
    gear: VehicleRigGear
) {
    when (gear) {
        VehicleRigGear.STOCK -> Unit
        VehicleRigGear.ROOF_RACK -> {
            drawLine(
                color = palette.trim,
                start = Offset(w * roofStartX, h * (roofY - 0.045f)),
                end = Offset(w * roofEndX, h * (roofY - 0.045f)),
                strokeWidth = h * 0.030f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = palette.cladding,
                start = Offset(w * (roofStartX + 0.05f), h * (roofY - 0.045f)),
                end = Offset(w * (roofStartX + 0.05f), h * roofY),
                strokeWidth = w * 0.016f
            )
            drawLine(
                color = palette.cladding,
                start = Offset(w * (roofEndX - 0.05f), h * (roofY - 0.045f)),
                end = Offset(w * (roofEndX - 0.05f), h * roofY),
                strokeWidth = w * 0.016f
            )
        }
        VehicleRigGear.EXPEDITION -> {
            // Heavy overland rack + cargo box + LED lightbar
            drawRoundRect(
                color = palette.trim,
                topLeft = Offset(w * roofStartX, h * (roofY - 0.068f)),
                size = Size(w * (roofEndX - roofStartX), h * 0.042f),
                cornerRadius = CornerRadius(h * 0.018f, h * 0.018f)
            )
            // Rooftop recovery case / awning pack
            drawRoundRect(
                color = Color(0xFF47504B),
                topLeft = Offset(w * (roofStartX + 0.04f), h * (roofY - 0.115f)),
                size = Size(w * ((roofEndX - roofStartX) * 0.56f), h * 0.052f),
                cornerRadius = CornerRadius(h * 0.015f, h * 0.015f)
            )
            // Amber LED light bar pod on front edge
            drawRoundRect(
                color = Color(0xFFFFC14D),
                topLeft = Offset(w * (roofEndX - 0.025f), h * (roofY - 0.065f)),
                size = Size(w * 0.042f, h * 0.036f),
                cornerRadius = CornerRadius(4f, 4f)
            )
        }
        VehicleRigGear.RALLY_TRIM -> {
            // Subtle racing accent stripe + mudflaps
            drawLine(
                color = Color(0xFFD96432),
                start = Offset(w * 0.14f, h * 0.57f),
                end = Offset(w * 0.85f, h * 0.585f),
                strokeWidth = h * 0.024f,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun DrawScope.drawDetailsPillarsAndMirrors(
    w: Float,
    h: Float,
    mirrorX: Float,
    mirrorY: Float,
    handleY: Float,
    handle1X: Float,
    handle2X: Float?,
    palette: RigPalette
) {
    // Side mirror
    drawRoundRect(
        color = palette.trim,
        topLeft = Offset(w * mirrorX, h * mirrorY),
        size = Size(w * 0.045f, h * 0.045f),
        cornerRadius = CornerRadius(h * 0.016f, h * 0.016f)
    )
    // Door handles
    drawRoundRect(
        color = palette.trim.copy(alpha = 0.72f),
        topLeft = Offset(w * handle1X, h * handleY),
        size = Size(w * 0.045f, h * 0.022f),
        cornerRadius = CornerRadius(4f, 4f)
    )
    if (handle2X != null) {
        drawRoundRect(
            color = palette.trim.copy(alpha = 0.72f),
            topLeft = Offset(w * handle2X, h * handleY),
            size = Size(w * 0.045f, h * 0.022f),
            cornerRadius = CornerRadius(4f, 4f)
        )
    }
}

private fun DrawScope.drawSuv4x4(
    w: Float,
    h: Float,
    palette: RigPalette,
    gear: VehicleRigGear
) {
    // Rear-mounted spare tire for rugged 4x4 silhouette
    drawRoundRect(
        color = palette.trim,
        topLeft = Offset(w * 0.08f, h * 0.39f),
        size = Size(w * 0.068f, h * 0.25f),
        cornerRadius = CornerRadius(w * 0.025f, w * 0.025f)
    )
    drawRoundRect(
        color = palette.cladding,
        topLeft = Offset(w * 0.095f, h * 0.44f),
        size = Size(w * 0.036f, h * 0.15f),
        cornerRadius = CornerRadius(w * 0.014f, w * 0.014f)
    )

    drawRoofAccessories(w, h, 0.20f, 0.62f, 0.24f, palette, gear)

    // Boxy 4x4 Body Shell
    val bodyPath = Path().apply {
        moveTo(w * 0.13f, h * 0.69f)
        lineTo(w * 0.13f, h * 0.42f)
        quadraticTo(w * 0.14f, h * 0.26f, w * 0.21f, h * 0.24f)
        lineTo(w * 0.59f, h * 0.24f)
        lineTo(w * 0.72f, h * 0.43f)
        lineTo(w * 0.86f, h * 0.46f)
        quadraticTo(w * 0.90f, h * 0.48f, w * 0.90f, h * 0.68f)
        close()
    }
    drawPath(bodyPath, brush = palette.bodyBrush)
    drawPath(bodyPath, color = palette.outline, style = Stroke(width = h * 0.016f, join = StrokeJoin.Round))

    // Lower armored rocker & bumper
    drawRoundRect(
        color = palette.cladding,
        topLeft = Offset(w * 0.12f, h * 0.635f),
        size = Size(w * 0.79f, h * 0.070f),
        cornerRadius = CornerRadius(h * 0.025f, h * 0.025f)
    )

    // Muscular shoulder line
    drawLine(
        color = palette.highlight.copy(alpha = 0.75f),
        start = Offset(w * 0.15f, h * 0.46f),
        end = Offset(w * 0.87f, h * 0.48f),
        strokeWidth = h * 0.020f,
        cap = StrokeCap.Round
    )

    // DLO Greenhouse Windows
    val winPath = Path().apply {
        moveTo(w * 0.21f, h * 0.275f)
        lineTo(w * 0.575f, h * 0.275f)
        lineTo(w * 0.685f, h * 0.42f)
        lineTo(w * 0.18f, h * 0.42f)
        close()
    }
    drawPath(winPath, brush = palette.glassBrush)
    drawGlassGloss(w * 0.43f, h * 0.28f, w * 0.64f, h * 0.42f, palette.glassGloss)
    drawLine(color = palette.trim, start = Offset(w * 0.37f, h * 0.27f), end = Offset(w * 0.37f, h * 0.43f), strokeWidth = w * 0.020f)
    drawLine(color = palette.base, start = Offset(w * 0.52f, h * 0.27f), end = Offset(w * 0.53f, h * 0.43f), strokeWidth = w * 0.024f)

    drawDetailsPillarsAndMirrors(w, h, 0.64f, 0.40f, 0.49f, 0.42f, 0.56f, palette)

    // LED Taillight & Headlight
    drawRoundRect(color = palette.taillight, topLeft = Offset(w * 0.13f, h * 0.46f), size = Size(w * 0.032f, h * 0.08f), cornerRadius = CornerRadius(4f, 4f))
    drawRoundRect(color = palette.headlight, topLeft = Offset(w * 0.84f, h * 0.49f), size = Size(w * 0.055f, h * 0.06f), cornerRadius = CornerRadius(4f, 4f))

    if (gear == VehicleRigGear.RALLY_TRIM) {
        drawRoundRect(color = Color(0xFFD96432), topLeft = Offset(w * 0.17f, h * 0.70f), size = Size(w * 0.03f, h * 0.075f), cornerRadius = CornerRadius(3f, 3f))
    }

    drawAlloyWheel(w * 0.27f, h * 0.71f, h * 0.132f, palette, spokes = 6, offroadTread = true)
    drawAlloyWheel(w * 0.74f, h * 0.71f, h * 0.132f, palette, spokes = 6, offroadTread = true)
}

private fun DrawScope.drawCrossover(
    w: Float,
    h: Float,
    palette: RigPalette,
    gear: VehicleRigGear
) {
    drawRoofAccessories(w, h, 0.24f, 0.58f, 0.275f, palette, gear)

    // Sleek sculpted Crossover silhouette (sloped D-pillar + raised ride height cladding)
    val bodyPath = Path().apply {
        moveTo(w * 0.12f, h * 0.69f)
        quadraticTo(w * 0.11f, h * 0.51f, w * 0.16f, h * 0.43f)
        quadraticTo(w * 0.22f, h * 0.30f, w * 0.28f, h * 0.275f)
        lineTo(w * 0.56f, h * 0.275f)
        quadraticTo(w * 0.65f, h * 0.31f, w * 0.73f, h * 0.44f)
        lineTo(w * 0.87f, h * 0.49f)
        quadraticTo(w * 0.90f, h * 0.55f, w * 0.89f, h * 0.69f)
        close()
    }
    drawPath(bodyPath, brush = palette.bodyBrush)
    drawPath(bodyPath, color = palette.outline, style = Stroke(width = h * 0.016f, join = StrokeJoin.Round))

    // Signature black crossover lower cladding
    drawRoundRect(
        color = palette.cladding,
        topLeft = Offset(w * 0.115f, h * 0.645f),
        size = Size(w * 0.78f, h * 0.055f),
        cornerRadius = CornerRadius(h * 0.022f, h * 0.022f)
    )

    // Sweeping beltline
    val beltline = Path().apply {
        moveTo(w * 0.16f, h * 0.49f)
        quadraticTo(w * 0.48f, h * 0.45f, w * 0.84f, h * 0.51f)
    }
    drawPath(beltline, color = palette.highlight.copy(alpha = 0.75f), style = Stroke(width = h * 0.018f, cap = StrokeCap.Round))

    // Tapered Crossover greenhouse
    val winPath = Path().apply {
        moveTo(w * 0.27f, h * 0.305f)
        lineTo(w * 0.545f, h * 0.305f)
        lineTo(w * 0.685f, h * 0.44f)
        lineTo(w * 0.21f, h * 0.435f)
        close()
    }
    drawPath(winPath, brush = palette.glassBrush)
    drawGlassGloss(w * 0.44f, h * 0.305f, w * 0.62f, h * 0.44f, palette.glassGloss)
    drawLine(color = palette.trim, start = Offset(w * 0.43f, h * 0.30f), end = Offset(w * 0.44f, h * 0.445f), strokeWidth = w * 0.022f)

    drawDetailsPillarsAndMirrors(w, h, 0.63f, 0.415f, 0.50f, 0.40f, 0.55f, palette)

    drawRoundRect(color = palette.taillight, topLeft = Offset(w * 0.125f, h * 0.47f), size = Size(w * 0.042f, h * 0.055f), cornerRadius = CornerRadius(5f, 5f))
    drawRoundRect(color = palette.headlight, topLeft = Offset(w * 0.835f, h * 0.51f), size = Size(w * 0.052f, h * 0.048f), cornerRadius = CornerRadius(6f, 6f))

    drawAlloyWheel(w * 0.27f, h * 0.71f, h * 0.122f, palette, spokes = 5)
    drawAlloyWheel(w * 0.74f, h * 0.71f, h * 0.122f, palette, spokes = 5)
}

private fun DrawScope.drawPickup(
    w: Float,
    h: Float,
    palette: RigPalette,
    gear: VehicleRigGear
) {
    // Sport bar / bed chase rack
    val rackAlpha = if (gear == VehicleRigGear.EXPEDITION || gear == VehicleRigGear.RALLY_TRIM) 1f else 0.85f
    val rack = Path().apply {
        moveTo(w * 0.16f, h * 0.44f)
        lineTo(w * 0.23f, h * 0.26f)
        lineTo(w * 0.44f, h * 0.26f)
    }
    drawPath(rack, color = palette.trim.copy(alpha = rackAlpha), style = Stroke(width = h * 0.032f, cap = StrokeCap.Round, join = StrokeJoin.Round))

    if (gear == VehicleRigGear.EXPEDITION) {
        drawRoundRect(
            color = Color(0xFF47504B),
            topLeft = Offset(w * 0.15f, h * 0.36f),
            size = Size(w * 0.24f, h * 0.08f),
            cornerRadius = CornerRadius(4f, 4f)
        )
        drawRoundRect(
            color = Color(0xFFFFC14D),
            topLeft = Offset(w * 0.63f, h * 0.205f),
            size = Size(w * 0.045f, h * 0.038f),
            cornerRadius = CornerRadius(4f, 4f)
        )
    } else if (gear == VehicleRigGear.ROOF_RACK) {
        drawLine(color = palette.trim, start = Offset(w * 0.45f, h * 0.215f), end = Offset(w * 0.65f, h * 0.215f), strokeWidth = h * 0.028f, cap = StrokeCap.Round)
    }

    val bodyPath = Path().apply {
        moveTo(w * 0.10f, h * 0.69f)
        lineTo(w * 0.10f, h * 0.44f)
        lineTo(w * 0.40f, h * 0.44f)
        lineTo(w * 0.44f, h * 0.25f)
        lineTo(w * 0.66f, h * 0.25f)
        lineTo(w * 0.76f, h * 0.435f)
        lineTo(w * 0.88f, h * 0.465f)
        quadraticTo(w * 0.91f, h * 0.50f, w * 0.905f, h * 0.69f)
        close()
    }
    drawPath(bodyPath, brush = palette.bodyBrush)
    drawPath(bodyPath, color = palette.outline, style = Stroke(width = h * 0.016f, join = StrokeJoin.Round))

    // Bed-to-cab seam + lower cladding
    drawLine(color = palette.trim.copy(alpha = 0.42f), start = Offset(w * 0.405f, h * 0.44f), end = Offset(w * 0.405f, h * 0.65f), strokeWidth = w * 0.010f)
    drawRoundRect(
        color = palette.cladding,
        topLeft = Offset(w * 0.095f, h * 0.64f),
        size = Size(w * 0.815f, h * 0.065f),
        cornerRadius = CornerRadius(h * 0.025f, h * 0.025f)
    )

    drawLine(
        color = palette.highlight.copy(alpha = 0.75f),
        start = Offset(w * 0.13f, h * 0.47f),
        end = Offset(w * 0.86f, h * 0.485f),
        strokeWidth = h * 0.020f,
        cap = StrokeCap.Round
    )

    val cabWindow = Path().apply {
        moveTo(w * 0.46f, h * 0.285f)
        lineTo(w * 0.64f, h * 0.285f)
        lineTo(w * 0.73f, h * 0.43f)
        lineTo(w * 0.45f, h * 0.43f)
        close()
    }
    drawPath(cabWindow, brush = palette.glassBrush)
    drawGlassGloss(w * 0.55f, h * 0.29f, w * 0.70f, h * 0.43f, palette.glassGloss)
    drawLine(color = palette.trim, start = Offset(w * 0.56f, h * 0.28f), end = Offset(w * 0.56f, h * 0.44f), strokeWidth = w * 0.020f)

    drawDetailsPillarsAndMirrors(w, h, 0.69f, 0.415f, 0.50f, 0.48f, 0.60f, palette)

    drawRoundRect(color = palette.taillight, topLeft = Offset(w * 0.10f, h * 0.465f), size = Size(w * 0.032f, h * 0.08f), cornerRadius = CornerRadius(4f, 4f))
    drawRoundRect(color = palette.headlight, topLeft = Offset(w * 0.845f, h * 0.49f), size = Size(w * 0.052f, h * 0.06f), cornerRadius = CornerRadius(4f, 4f))

    if (gear == VehicleRigGear.RALLY_TRIM) {
        drawRoundRect(color = Color(0xFFD96432), topLeft = Offset(w * 0.15f, h * 0.70f), size = Size(w * 0.03f, h * 0.075f), cornerRadius = CornerRadius(3f, 3f))
    }

    drawAlloyWheel(w * 0.26f, h * 0.715f, h * 0.132f, palette, spokes = 6, offroadTread = true)
    drawAlloyWheel(w * 0.75f, h * 0.715f, h * 0.132f, palette, spokes = 6, offroadTread = true)
}

private fun DrawScope.drawSedan(
    w: Float,
    h: Float,
    palette: RigPalette,
    gear: VehicleRigGear
) {
    drawRoofAccessories(w, h, 0.36f, 0.61f, 0.305f, palette, gear)

    val bodyPath = Path().apply {
        moveTo(w * 0.10f, h * 0.70f)
        quadraticTo(w * 0.095f, h * 0.56f, w * 0.125f, h * 0.51f)
        lineTo(w * 0.23f, h * 0.47f)
        quadraticTo(w * 0.31f, h * 0.32f, w * 0.36f, h * 0.305f)
        lineTo(w * 0.61f, h * 0.305f)
        quadraticTo(w * 0.69f, h * 0.36f, w * 0.76f, h * 0.48f)
        lineTo(w * 0.88f, h * 0.525f)
        quadraticTo(w * 0.91f, h * 0.58f, w * 0.90f, h * 0.70f)
        close()
    }
    drawPath(bodyPath, brush = palette.bodyBrush)
    drawPath(bodyPath, color = palette.outline, style = Stroke(width = h * 0.016f, join = StrokeJoin.Round))

    drawRoundRect(
        color = palette.cladding,
        topLeft = Offset(w * 0.105f, h * 0.66f),
        size = Size(w * 0.795f, h * 0.045f),
        cornerRadius = CornerRadius(h * 0.02f, h * 0.02f)
    )

    drawLine(
        color = palette.highlight.copy(alpha = 0.75f),
        start = Offset(w * 0.15f, h * 0.53f),
        end = Offset(w * 0.85f, h * 0.55f),
        strokeWidth = h * 0.018f,
        cap = StrokeCap.Round
    )

    val winPath = Path().apply {
        moveTo(w * 0.37f, h * 0.335f)
        lineTo(w * 0.595f, h * 0.335f)
        lineTo(w * 0.715f, h * 0.47f)
        lineTo(w * 0.27f, h * 0.47f)
        close()
    }
    drawPath(winPath, brush = palette.glassBrush)
    drawGlassGloss(w * 0.49f, h * 0.335f, w * 0.66f, h * 0.47f, palette.glassGloss)
    drawLine(color = palette.trim, start = Offset(w * 0.485f, h * 0.33f), end = Offset(w * 0.485f, h * 0.48f), strokeWidth = w * 0.020f)

    drawDetailsPillarsAndMirrors(w, h, 0.66f, 0.445f, 0.52f, 0.41f, 0.55f, palette)

    drawRoundRect(color = palette.taillight, topLeft = Offset(w * 0.115f, h * 0.515f), size = Size(w * 0.045f, h * 0.045f), cornerRadius = CornerRadius(4f, 4f))
    drawRoundRect(color = palette.headlight, topLeft = Offset(w * 0.84f, h * 0.545f), size = Size(w * 0.055f, h * 0.045f), cornerRadius = CornerRadius(6f, 6f))

    drawAlloyWheel(w * 0.27f, h * 0.715f, h * 0.118f, palette, spokes = 7)
    drawAlloyWheel(w * 0.74f, h * 0.715f, h * 0.118f, palette, spokes = 7)
}

private fun DrawScope.drawHatchback(
    w: Float,
    h: Float,
    palette: RigPalette,
    gear: VehicleRigGear
) {
    drawRoofAccessories(w, h, 0.21f, 0.55f, 0.295f, palette, gear)

    // Compact upright Hatchback with crisp rear hatch
    val bodyPath = Path().apply {
        moveTo(w * 0.14f, h * 0.70f)
        quadraticTo(w * 0.135f, h * 0.50f, w * 0.18f, h * 0.36f)
        quadraticTo(w * 0.20f, h * 0.295f, w * 0.25f, h * 0.295f)
        lineTo(w * 0.55f, h * 0.295f)
        quadraticTo(w * 0.65f, h * 0.34f, w * 0.73f, h * 0.46f)
        lineTo(w * 0.86f, h * 0.515f)
        quadraticTo(w * 0.89f, h * 0.58f, w * 0.88f, h * 0.70f)
        close()
    }
    drawPath(bodyPath, brush = palette.bodyBrush)
    drawPath(bodyPath, color = palette.outline, style = Stroke(width = h * 0.016f, join = StrokeJoin.Round))

    drawRoundRect(
        color = palette.cladding,
        topLeft = Offset(w * 0.14f, h * 0.655f),
        size = Size(w * 0.74f, h * 0.050f),
        cornerRadius = CornerRadius(h * 0.02f, h * 0.02f)
    )

    val winPath = Path().apply {
        moveTo(w * 0.24f, h * 0.325f)
        lineTo(w * 0.535f, h * 0.325f)
        lineTo(w * 0.68f, h * 0.455f)
        lineTo(w * 0.20f, h * 0.455f)
        close()
    }
    drawPath(winPath, brush = palette.glassBrush)
    drawGlassGloss(w * 0.42f, h * 0.325f, w * 0.62f, h * 0.455f, palette.glassGloss)
    drawLine(color = palette.trim, start = Offset(w * 0.415f, h * 0.32f), end = Offset(w * 0.42f, h * 0.465f), strokeWidth = w * 0.022f)

    drawDetailsPillarsAndMirrors(w, h, 0.635f, 0.435f, 0.51f, 0.45f, null, palette)

    drawRoundRect(color = palette.taillight, topLeft = Offset(w * 0.15f, h * 0.475f), size = Size(w * 0.036f, h * 0.06f), cornerRadius = CornerRadius(4f, 4f))
    drawRoundRect(color = palette.headlight, topLeft = Offset(w * 0.825f, h * 0.53f), size = Size(w * 0.052f, h * 0.048f), cornerRadius = CornerRadius(6f, 6f))

    drawAlloyWheel(w * 0.28f, h * 0.715f, h * 0.120f, palette, spokes = 5)
    drawAlloyWheel(w * 0.72f, h * 0.715f, h * 0.120f, palette, spokes = 5)
}

private fun DrawScope.drawCoupe(
    w: Float,
    h: Float,
    palette: RigPalette,
    gear: VehicleRigGear
) {
    drawRoofAccessories(w, h, 0.36f, 0.58f, 0.32f, palette, gear)

    // Low, wide, fastback Coupe silhouette
    val bodyPath = Path().apply {
        moveTo(w * 0.10f, h * 0.70f)
        quadraticTo(w * 0.10f, h * 0.56f, w * 0.15f, h * 0.50f)
        quadraticTo(w * 0.26f, h * 0.39f, w * 0.38f, h * 0.325f)
        lineTo(w * 0.57f, h * 0.325f)
        quadraticTo(w * 0.68f, h * 0.40f, w * 0.76f, h * 0.495f)
        lineTo(w * 0.89f, h * 0.545f)
        quadraticTo(w * 0.91f, h * 0.60f, w * 0.90f, h * 0.70f)
        close()
    }
    drawPath(bodyPath, brush = palette.bodyBrush)
    drawPath(bodyPath, color = palette.outline, style = Stroke(width = h * 0.016f, join = StrokeJoin.Round))

    // Rear ducktail spoiler accent
    val spoiler = Path().apply {
        moveTo(w * 0.12f, h * 0.495f)
        lineTo(w * 0.095f, h * 0.455f)
        lineTo(w * 0.16f, h * 0.475f)
    }
    drawPath(spoiler, color = palette.trim, style = Stroke(width = h * 0.022f, cap = StrokeCap.Round))

    drawRoundRect(
        color = palette.cladding,
        topLeft = Offset(w * 0.105f, h * 0.665f),
        size = Size(w * 0.795f, h * 0.040f),
        cornerRadius = CornerRadius(h * 0.02f, h * 0.02f)
    )

    val winPath = Path().apply {
        moveTo(w * 0.385f, h * 0.355f)
        lineTo(w * 0.56f, h * 0.355f)
        lineTo(w * 0.70f, h * 0.485f)
        lineTo(w * 0.28f, h * 0.485f)
        close()
    }
    drawPath(winPath, brush = palette.glassBrush)
    drawGlassGloss(w * 0.47f, h * 0.355f, w * 0.64f, h * 0.485f, palette.glassGloss)

    drawDetailsPillarsAndMirrors(w, h, 0.64f, 0.455f, 0.535f, 0.46f, null, palette)

    drawRoundRect(color = palette.taillight, topLeft = Offset(w * 0.115f, h * 0.525f), size = Size(w * 0.048f, h * 0.038f), cornerRadius = CornerRadius(4f, 4f))
    drawRoundRect(color = palette.headlight, topLeft = Offset(w * 0.835f, h * 0.555f), size = Size(w * 0.055f, h * 0.040f), cornerRadius = CornerRadius(6f, 6f))

    drawAlloyWheel(w * 0.27f, h * 0.715f, h * 0.122f, palette, spokes = 8)
    drawAlloyWheel(w * 0.74f, h * 0.715f, h * 0.122f, palette, spokes = 8)
}

private fun DrawScope.drawVan(
    w: Float,
    h: Float,
    palette: RigPalette,
    gear: VehicleRigGear
) {
    drawRoofAccessories(w, h, 0.16f, 0.67f, 0.24f, palette, gear)

    val bodyPath = Path().apply {
        moveTo(w * 0.12f, h * 0.695f)
        lineTo(w * 0.12f, h * 0.29f)
        quadraticTo(w * 0.125f, h * 0.24f, w * 0.18f, h * 0.24f)
        lineTo(w * 0.67f, h * 0.24f)
        quadraticTo(w * 0.73f, h * 0.25f, w * 0.80f, h * 0.445f)
        lineTo(w * 0.875f, h * 0.495f)
        quadraticTo(w * 0.90f, h * 0.54f, w * 0.895f, h * 0.695f)
        close()
    }
    drawPath(bodyPath, brush = palette.bodyBrush)
    drawPath(bodyPath, color = palette.outline, style = Stroke(width = h * 0.016f, join = StrokeJoin.Round))

    drawRoundRect(
        color = palette.cladding,
        topLeft = Offset(w * 0.115f, h * 0.645f),
        size = Size(w * 0.785f, h * 0.060f),
        cornerRadius = CornerRadius(h * 0.022f, h * 0.022f)
    )

    drawLine(
        color = palette.highlight.copy(alpha = 0.72f),
        start = Offset(w * 0.15f, h * 0.475f),
        end = Offset(w * 0.85f, h * 0.50f),
        strokeWidth = h * 0.020f,
        cap = StrokeCap.Round
    )

    val frontWin = Path().apply {
        moveTo(w * 0.58f, h * 0.28f)
        lineTo(w * 0.67f, h * 0.28f)
        lineTo(w * 0.76f, h * 0.445f)
        lineTo(w * 0.58f, h * 0.445f)
        close()
    }
    drawPath(frontWin, brush = palette.glassBrush)
    drawGlassGloss(w * 0.61f, h * 0.28f, w * 0.73f, h * 0.445f, palette.glassGloss)
    drawRoundRect(
        brush = palette.glassBrush,
        topLeft = Offset(w * 0.20f, h * 0.285f),
        size = Size(w * 0.32f, h * 0.155f),
        cornerRadius = CornerRadius(8f, 8f)
    )
    drawGlassGloss(w * 0.26f, h * 0.285f, w * 0.46f, h * 0.44f, palette.glassGloss)

    drawDetailsPillarsAndMirrors(w, h, 0.725f, 0.42f, 0.50f, 0.54f, 0.45f, palette)

    drawRoundRect(color = palette.taillight, topLeft = Offset(w * 0.12f, h * 0.47f), size = Size(w * 0.032f, h * 0.09f), cornerRadius = CornerRadius(4f, 4f))
    drawRoundRect(color = palette.headlight, topLeft = Offset(w * 0.84f, h * 0.515f), size = Size(w * 0.050f, h * 0.06f), cornerRadius = CornerRadius(5f, 5f))

    drawAlloyWheel(w * 0.27f, h * 0.715f, h * 0.120f, palette, spokes = 6)
    drawAlloyWheel(w * 0.74f, h * 0.715f, h * 0.120f, palette, spokes = 6)
}

private fun DrawScope.drawMoto(
    w: Float,
    h: Float,
    palette: RigPalette,
    gear: VehicleRigGear
) {
    drawAlloyWheel(w * 0.25f, h * 0.695f, h * 0.138f, palette, spokes = 8, offroadTread = true)
    drawAlloyWheel(w * 0.75f, h * 0.695f, h * 0.138f, palette, spokes = 8, offroadTread = true)

    // Swingarm + exhaust + engine block
    drawLine(
        color = Color(0xFF5D6560),
        start = Offset(w * 0.25f, h * 0.695f),
        end = Offset(w * 0.46f, h * 0.56f),
        strokeWidth = h * 0.044f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = Color(0xFF939B96),
        start = Offset(w * 0.16f, h * 0.61f),
        end = Offset(w * 0.44f, h * 0.64f),
        strokeWidth = h * 0.046f,
        cap = StrokeCap.Round
    )
    drawRoundRect(
        color = Color(0xFF3E4440),
        topLeft = Offset(w * 0.41f, h * 0.50f),
        size = Size(w * 0.20f, h * 0.165f),
        cornerRadius = CornerRadius(10f, 10f)
    )

    // Front forks & handlebars
    drawLine(
        color = palette.metalAccent,
        start = Offset(w * 0.61f, h * 0.30f),
        end = Offset(w * 0.75f, h * 0.695f),
        strokeWidth = h * 0.036f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = palette.trim,
        start = Offset(w * 0.58f, h * 0.29f),
        end = Offset(w * 0.64f, h * 0.26f),
        strokeWidth = h * 0.032f,
        cap = StrokeCap.Round
    )

    // Pannier box when Roof Rack or Expedition is selected
    if (gear == VehicleRigGear.ROOF_RACK || gear == VehicleRigGear.EXPEDITION) {
        drawRoundRect(
            color = palette.cladding,
            topLeft = Offset(w * 0.15f, h * 0.42f),
            size = Size(w * 0.17f, h * 0.17f),
            cornerRadius = CornerRadius(8f, 8f)
        )
        drawRoundRect(
            color = palette.metalAccent.copy(alpha = 0.6f),
            topLeft = Offset(w * 0.17f, h * 0.45f),
            size = Size(w * 0.13f, h * 0.02f),
            cornerRadius = CornerRadius(2f, 2f)
        )
    }

    // Seat + sculptural tank + ADV beak fairing
    val tankPath = Path().apply {
        moveTo(w * 0.28f, h * 0.445f)
        lineTo(w * 0.46f, h * 0.445f)
        quadraticTo(w * 0.54f, h * 0.33f, w * 0.63f, h * 0.34f)
        lineTo(w * 0.75f, h * 0.40f)
        lineTo(w * 0.63f, h * 0.51f)
        lineTo(w * 0.31f, h * 0.495f)
        close()
    }
    drawPath(tankPath, brush = palette.bodyBrush)
    drawPath(tankPath, color = palette.outline, style = Stroke(width = h * 0.016f, join = StrokeJoin.Round))

    // Saddle pad
    drawRoundRect(
        color = palette.trim,
        topLeft = Offset(w * 0.29f, h * 0.415f),
        size = Size(w * 0.18f, h * 0.042f),
        cornerRadius = CornerRadius(6f, 6f)
    )

    // Rally / Adventure tall windscreen + LED eye
    drawLine(
        color = Color(0xFF9AC4D0),
        start = Offset(w * 0.65f, h * 0.35f),
        end = Offset(w * 0.71f, h * 0.21f),
        strokeWidth = w * 0.024f,
        cap = StrokeCap.Round
    )
    drawCircle(color = palette.headlight, radius = h * 0.036f, center = Offset(w * 0.71f, h * 0.405f))
}
