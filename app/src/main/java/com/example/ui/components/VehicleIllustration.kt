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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import com.example.data.model.VehicleBodyStyle
import com.example.data.model.VehicleRigGear
import kotlin.math.cos
import kotlin.math.sin

// Canonical 220 x 100 (2.2:1) automotive side-profile studio stage.
// Every silhouette scales uniformly inside this locked viewport so wheels stay perfectly circular
// and proportions remain balanced across badges, list cards, dialogs, phones, and tablets.
private const val STAGE_W = 220f
private const val STAGE_H = 100f
private const val GROUND_Y = 86.5f

private data class RigPalette(
    val bodyBrush: Brush,
    val upperHighlight: Color,
    val base: Color,
    val lowerShadow: Color,
    val outline: Color,
    val glassBrush: Brush,
    val glassGloss: Color,
    val pillarDark: Color,
    val tire: Color,
    val sidewall: Color,
    val cladding: Color,
    val alloy: Color,
    val alloyDark: Color,
    val brakeRotor: Color,
    val headlight: Color,
    val taillight: Color,
    val rallyAccent: Color
)

@Composable
fun VehicleRigIllustration(
    bodyStyle: VehicleBodyStyle,
    accentColor: Color,
    modifier: Modifier = Modifier,
    rigGear: VehicleRigGear = VehicleRigGear.STOCK
) {
    Canvas(modifier = modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas

        val scale = minOf(size.width / STAGE_W, size.height / STAGE_H)
        val offsetX = (size.width - STAGE_W * scale) / 2f
        val offsetY = (size.height - STAGE_H * scale) / 2f

        withTransform({
            translate(left = offsetX, top = offsetY)
            scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
        }) {
            val luminance = accentColor.red * 0.299f + accentColor.green * 0.587f + accentColor.blue * 0.114f
            val isVeryLight = luminance > 0.78f
            val isVeryDark = luminance < 0.18f

            val highlight = lerp(accentColor, Color.White, if (isVeryLight) 0.08f else 0.34f)
            val shadow = lerp(accentColor, Color.Black, if (isVeryLight) 0.15f else 0.38f)
            val outline = when {
                isVeryLight -> Color(0xFF2B332E).copy(alpha = 0.55f)
                isVeryDark -> Color(0xFF98A39C).copy(alpha = 0.36f)
                else -> Color(0xFF141816).copy(alpha = 0.40f)
            }

            val palette = RigPalette(
                bodyBrush = Brush.verticalGradient(
                    colors = listOf(highlight, accentColor, shadow),
                    startY = 20f,
                    endY = 76f
                ),
                upperHighlight = highlight,
                base = accentColor,
                lowerShadow = shadow,
                outline = outline,
                glassBrush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF334B56), Color(0xFF172227)),
                    startY = 22f,
                    endY = 50f
                ),
                glassGloss = Color.White.copy(alpha = 0.22f),
                pillarDark = Color(0xFF191E1B),
                tire = Color(0xFF181C1A),
                sidewall = Color(0xFF2F3632),
                cladding = Color(0xFF242A27),
                alloy = Color(0xFFDDE3DE),
                alloyDark = Color(0xFF474F4A),
                brakeRotor = Color(0xFF7B837E),
                headlight = Color(0xFFFFF4C8),
                taillight = Color(0xFFE04838),
                rallyAccent = Color(0xFFE06D36)
            )

            // Studio contact shadow along ground plane
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.24f), Color.Transparent),
                    center = Offset(110f, GROUND_Y + 0.5f),
                    radius = 90f
                ),
                topLeft = Offset(20f, GROUND_Y - 3.5f),
                size = Size(180f, 8.5f)
            )

            when (bodyStyle) {
                VehicleBodyStyle.SUV_4X4 -> drawSuv4x4(palette, rigGear)
                VehicleBodyStyle.CROSSOVER -> drawCrossover(palette, rigGear)
                VehicleBodyStyle.PICKUP -> drawPickup(palette, rigGear)
                VehicleBodyStyle.SEDAN -> drawSedan(palette, rigGear)
                VehicleBodyStyle.HATCHBACK -> drawHatchback(palette, rigGear)
                VehicleBodyStyle.COUPE -> drawCoupe(palette, rigGear)
                VehicleBodyStyle.VAN -> drawVan(palette, rigGear)
                VehicleBodyStyle.MOTO -> drawMoto(palette, rigGear)
            }
        }
    }
}

private fun DrawScope.drawWheelArchCutout(
    cx: Float,
    cy: Float,
    wheelRadius: Float,
    palette: RigPalette,
    flared: Boolean = false
) {
    val archR = wheelRadius * 1.24f
    // Deep wheel-well cavity
    drawArc(
        color = Color(0xFF111513),
        startAngle = 175f,
        sweepAngle = 190f,
        useCenter = true,
        topLeft = Offset(cx - archR, cy - archR),
        size = Size(archR * 2f, archR * 2f)
    )
    if (flared) {
        // Molded off-road fender flare arch lip
        drawArc(
            color = palette.cladding,
            startAngle = 188f,
            sweepAngle = 164f,
            useCenter = false,
            topLeft = Offset(cx - archR - 0.8f, cy - archR - 0.8f),
            size = Size((archR + 0.8f) * 2f, (archR + 0.8f) * 2f),
            style = Stroke(width = 2.6f, cap = StrokeCap.Round)
        )
    } else {
        drawArc(
            color = palette.outline,
            startAngle = 188f,
            sweepAngle = 164f,
            useCenter = false,
            topLeft = Offset(cx - archR, cy - archR),
            size = Size(archR * 2f, archR * 2f),
            style = Stroke(width = 1.1f, cap = StrokeCap.Round)
        )
    }
}

private fun DrawScope.drawWheel(
    cx: Float,
    cy: Float,
    radius: Float,
    palette: RigPalette,
    spokes: Int = 6,
    offroadTread: Boolean = false
) {
    // Ground contact shadow right under tire
    drawOval(
        color = Color.Black.copy(alpha = 0.28f),
        topLeft = Offset(cx - radius * 0.9f, cy + radius - 1.4f),
        size = Size(radius * 1.8f, 3.2f)
    )

    // Outer tire
    drawCircle(color = palette.tire, radius = radius, center = Offset(cx, cy))
    if (offroadTread) {
        drawCircle(
            color = Color(0xFF101311),
            radius = radius * 0.95f,
            center = Offset(cx, cy),
            style = Stroke(width = 1.6f)
        )
    }

    // Rubber sidewall band
    drawCircle(color = palette.sidewall, radius = radius * 0.74f, center = Offset(cx, cy))

    // Brake disc & caliper hint inside barrel
    val rimR = radius * 0.56f
    drawCircle(color = Color(0xFF1A1F1C), radius = rimR, center = Offset(cx, cy))
    drawCircle(color = palette.brakeRotor.copy(alpha = 0.42f), radius = rimR * 0.72f, center = Offset(cx, cy))
    drawArc(
        color = Color(0xFFC25334).copy(alpha = 0.85f),
        startAngle = 135f,
        sweepAngle = 55f,
        useCenter = false,
        topLeft = Offset(cx - rimR * 0.62f, cy - rimR * 0.62f),
        size = Size(rimR * 1.24f, rimR * 1.24f),
        style = Stroke(width = 1.6f, cap = StrokeCap.Round)
    )

    // Metallic rim flange
    drawCircle(
        color = palette.alloy,
        radius = rimR,
        center = Offset(cx, cy),
        style = Stroke(width = 1.15f)
    )

    // Alloy spokes
    for (i in 0 until spokes) {
        val angle = (i * (360f / spokes) - 90f) * (Math.PI / 180.0)
        val sx = cx + (cos(angle) * rimR * 0.90f).toFloat()
        val sy = cy + (sin(angle) * rimR * 0.90f).toFloat()
        drawLine(
            color = palette.alloy,
            start = Offset(cx, cy),
            end = Offset(sx, sy),
            strokeWidth = 1.25f,
            cap = StrokeCap.Round
        )
    }

    // Center hub cap & lug ring
    drawCircle(color = palette.alloyDark, radius = radius * 0.17f, center = Offset(cx, cy))
    drawCircle(color = palette.alloy, radius = radius * 0.08f, center = Offset(cx, cy))
}

private fun DrawScope.drawGlassReflection(
    leftX: Float,
    topY: Float,
    bottomY: Float,
    slant: Float,
    gloss: Color
) {
    val p = Path().apply {
        moveTo(leftX, topY)
        lineTo(leftX + 14f, topY)
        lineTo(leftX + 14f - slant, bottomY)
        lineTo(leftX - slant, bottomY)
        close()
    }
    drawPath(p, color = gloss)
}

private fun DrawScope.drawRoofGear(
    startX: Float,
    endX: Float,
    roofY: Float,
    palette: RigPalette,
    gear: VehicleRigGear
) {
    when (gear) {
        VehicleRigGear.STOCK -> Unit
        VehicleRigGear.ROOF_RACK -> {
            val railY = roofY - 3.6f
            drawLine(
                color = palette.cladding,
                start = Offset(startX, railY),
                end = Offset(endX, railY),
                strokeWidth = 2.2f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = palette.cladding,
                start = Offset(startX + 6f, railY),
                end = Offset(startX + 6f, roofY + 0.5f),
                strokeWidth = 1.8f
            )
            drawLine(
                color = palette.cladding,
                start = Offset(endX - 6f, railY),
                end = Offset(endX - 6f, roofY + 0.5f),
                strokeWidth = 1.8f
            )
        }
        VehicleRigGear.EXPEDITION -> {
            // Flat expedition rack + stanchions + recovery cargo case + amber LED light bar
            val rackY = roofY - 4.2f
            drawLine(
                color = palette.cladding,
                start = Offset(startX + 4f, rackY + 2f),
                end = Offset(startX + 4f, roofY + 0.6f),
                strokeWidth = 2.0f
            )
            drawLine(
                color = palette.cladding,
                start = Offset(endX - 6f, rackY + 2f),
                end = Offset(endX - 6f, roofY + 0.6f),
                strokeWidth = 2.0f
            )
            drawRoundRect(
                color = palette.cladding,
                topLeft = Offset(startX - 2f, rackY),
                size = Size((endX - startX) + 4f, 3.0f),
                cornerRadius = CornerRadius(1.5f, 1.5f)
            )
            // Rooftop case / awning tube
            drawRoundRect(
                color = Color(0xFF424A45),
                topLeft = Offset(startX + 4f, rackY - 4.5f),
                size = Size((endX - startX) * 0.54f, 4.6f),
                cornerRadius = CornerRadius(1.8f, 1.8f)
            )
            drawLine(
                color = Color(0xFF7B847E),
                start = Offset(startX + 7f, rackY - 2.3f),
                end = Offset(startX + 4f + (endX - startX) * 0.48f, rackY - 2.3f),
                strokeWidth = 0.8f,
                cap = StrokeCap.Round
            )
            // Amber LED pod at front edge
            drawRoundRect(
                color = Color(0xFFFFC247),
                topLeft = Offset(endX - 3.5f, rackY - 2.4f),
                size = Size(5.5f, 3.0f),
                cornerRadius = CornerRadius(1.2f, 1.2f)
            )
        }
        VehicleRigGear.RALLY_TRIM -> {
            // Subtle roof air scoop / rally accent line
            drawRoundRect(
                color = palette.rallyAccent,
                topLeft = Offset(endX - 18f, roofY - 2.2f),
                size = Size(10f, 2.4f),
                cornerRadius = CornerRadius(1f, 1f)
            )
        }
    }
}

private fun DrawScope.drawDoorSeamsAndMirror(
    mirrorX: Float,
    mirrorY: Float,
    handleY: Float,
    frontHandleX: Float,
    rearHandleX: Float?,
    palette: RigPalette
) {
    // Wing mirror housing
    drawRoundRect(
        color = palette.pillarDark,
        topLeft = Offset(mirrorX, mirrorY),
        size = Size(6.4f, 4.2f),
        cornerRadius = CornerRadius(2f, 2f)
    )
    // Door handles
    drawRoundRect(
        color = palette.pillarDark.copy(alpha = 0.78f),
        topLeft = Offset(frontHandleX, handleY),
        size = Size(6.2f, 2.0f),
        cornerRadius = CornerRadius(1f, 1f)
    )
    if (rearHandleX != null) {
        drawRoundRect(
            color = palette.pillarDark.copy(alpha = 0.78f),
            topLeft = Offset(rearHandleX, handleY),
            size = Size(6.2f, 2.0f),
            cornerRadius = CornerRadius(1f, 1f)
        )
    }
}

private fun DrawScope.drawSuv4x4(palette: RigPalette, gear: VehicleRigGear) {
    val rearX = 60f
    val frontX = 162f
    val axleY = 73.5f
    val wheelR = 12.6f

    // Rear tailgate-mounted spare tire
    drawRoundRect(
        color = palette.tire,
        topLeft = Offset(20f, 39f),
        size = Size(8.5f, 24f),
        cornerRadius = CornerRadius(3.5f, 3.5f)
    )
    drawRoundRect(
        color = palette.cladding,
        topLeft = Offset(21.5f, 44f),
        size = Size(4.2f, 14f),
        cornerRadius = CornerRadius(1.5f, 1.5f)
    )

    drawRoofGear(41f, 128f, 22f, palette, gear)

    // Classic upright 4x4 silhouette (Defender / Land Cruiser proportions)
    val body = Path().apply {
        moveTo(29f, 68f)
        lineTo(27.5f, 46f)
        lineTo(35f, 25f)
        quadraticTo(36.5f, 22f, 40f, 22f)
        lineTo(126f, 22f)
        quadraticTo(130f, 22f, 132.5f, 25f)
        lineTo(148f, 42f)
        lineTo(185f, 43.5f)
        quadraticTo(191.5f, 44.5f, 192f, 51f)
        lineTo(192f, 68f)
        close()
    }
    drawPath(body, brush = palette.bodyBrush)
    drawPath(body, color = palette.outline, style = Stroke(width = 1.4f, join = StrokeJoin.Round))

    // Rear & Front bumpers + side rock slider between wheel wells
    drawRoundRect(color = palette.cladding, topLeft = Offset(25.5f, 64f), size = Size(16f, 6f), cornerRadius = CornerRadius(2f, 2f))
    drawRoundRect(color = palette.cladding, topLeft = Offset(179f, 64f), size = Size(15.5f, 6f), cornerRadius = CornerRadius(2f, 2f))
    drawRoundRect(color = palette.cladding, topLeft = Offset(76f, 67.5f), size = Size(70f, 3.6f), cornerRadius = CornerRadius(1.5f, 1.5f))

    // Beltline shoulder highlight
    drawLine(
        color = palette.upperHighlight.copy(alpha = 0.72f),
        start = Offset(30f, 46f),
        end = Offset(188f, 46.5f),
        strokeWidth = 1.6f,
        cap = StrokeCap.Round
    )

    if (gear == VehicleRigGear.RALLY_TRIM) {
        drawLine(color = palette.rallyAccent, start = Offset(32f, 57f), end = Offset(184f, 57f), strokeWidth = 2.4f, cap = StrokeCap.Round)
        drawRoundRect(color = palette.rallyAccent, topLeft = Offset(42f, 75f), size = Size(3.6f, 8.5f), cornerRadius = CornerRadius(1f, 1f))
    }

    // Clean 3-pane greenhouse DLO
    val glass = Path().apply {
        moveTo(39f, 26f)
        lineTo(124f, 26f)
        lineTo(142f, 41.5f)
        lineTo(34f, 41.5f)
        close()
    }
    drawPath(glass, brush = palette.glassBrush)
    drawGlassReflection(76f, 26f, 41.5f, 7f, palette.glassGloss)
    // C-pillar (body-colored) & B-pillar (gloss black)
    drawLine(color = palette.base, start = Offset(74f, 25f), end = Offset(75f, 42.5f), strokeWidth = 4.2f)
    drawLine(color = palette.pillarDark, start = Offset(104f, 25f), end = Offset(104f, 42.5f), strokeWidth = 3.2f)

    drawDoorSeamsAndMirror(137f, 40f, 49.5f, 108f, 78f, palette)

    // Lights
    drawRoundRect(color = palette.taillight, topLeft = Offset(27.5f, 46f), size = Size(4.2f, 9.5f), cornerRadius = CornerRadius(1.5f, 1.5f))
    drawRoundRect(color = palette.headlight, topLeft = Offset(183f, 46.5f), size = Size(8.5f, 6.2f), cornerRadius = CornerRadius(2f, 2f))

    drawWheelArchCutout(rearX, axleY, wheelR, palette, flared = true)
    drawWheelArchCutout(frontX, axleY, wheelR, palette, flared = true)
    drawWheel(rearX, axleY, wheelR, palette, spokes = 6, offroadTread = true)
    drawWheel(frontX, axleY, wheelR, palette, spokes = 6, offroadTread = true)
}

private fun DrawScope.drawCrossover(palette: RigPalette, gear: VehicleRigGear) {
    val rearX = 61f
    val frontX = 161f
    val axleY = 74.5f
    val wheelR = 11.6f

    drawRoofGear(52f, 124f, 25.5f, palette, gear)

    // Sculpted CUV silhouette: sloped tailgate, curved roofline, crisp nose
    val body = Path().apply {
        moveTo(29f, 69f)
        quadraticTo(27f, 54f, 33f, 43f)
        lineTo(44f, 28.5f)
        quadraticTo(48f, 25.5f, 58f, 25.5f)
        lineTo(116f, 25.5f)
        quadraticTo(126f, 26f, 134f, 32f)
        lineTo(150f, 43.5f)
        lineTo(185f, 48.5f)
        quadraticTo(192f, 51.5f, 192f, 60f)
        lineTo(191f, 69f)
        close()
    }
    drawPath(body, brush = palette.bodyBrush)
    drawPath(body, color = palette.outline, style = Stroke(width = 1.4f, join = StrokeJoin.Round))

    // CUV lower cladding skirt between wheel arches + corner bumpers
    drawRoundRect(color = palette.cladding, topLeft = Offset(28f, 65f), size = Size(16f, 5f), cornerRadius = CornerRadius(2f, 2f))
    drawRoundRect(color = palette.cladding, topLeft = Offset(177f, 65f), size = Size(15f, 5f), cornerRadius = CornerRadius(2f, 2f))
    drawRoundRect(color = palette.cladding, topLeft = Offset(76f, 67f), size = Size(70f, 4.2f), cornerRadius = CornerRadius(1.5f, 1.5f))

    // Sweeping beltline
    val belt = Path().apply {
        moveTo(33f, 47f)
        quadraticTo(110f, 43.5f, 185f, 49.5f)
    }
    drawPath(belt, color = palette.upperHighlight.copy(alpha = 0.72f), style = Stroke(width = 1.6f, cap = StrokeCap.Round))

    if (gear == VehicleRigGear.RALLY_TRIM) {
        drawLine(color = palette.rallyAccent, start = Offset(34f, 58f), end = Offset(182f, 59f), strokeWidth = 2.2f, cap = StrokeCap.Round)
    }

    // Tapered CUV DLO greenhouse
    val glass = Path().apply {
        moveTo(49f, 29f)
        lineTo(117f, 29f)
        quadraticTo(125f, 29.5f, 132f, 35f)
        lineTo(143f, 43f)
        lineTo(42f, 42.5f)
        close()
    }
    drawPath(glass, brush = palette.glassBrush)
    drawGlassReflection(86f, 29f, 43f, 8f, palette.glassGloss)
    drawLine(color = palette.pillarDark, start = Offset(94f, 28.5f), end = Offset(95f, 43.5f), strokeWidth = 3.5f)
    drawLine(color = palette.base, start = Offset(66f, 28.5f), end = Offset(63f, 43.5f), strokeWidth = 3.6f)

    drawDoorSeamsAndMirror(136f, 40.5f, 49.5f, 106f, 78f, palette)

    drawRoundRect(color = palette.taillight, topLeft = Offset(29f, 44.5f), size = Size(8.5f, 4.6f), cornerRadius = CornerRadius(2f, 2f))
    drawRoundRect(color = palette.headlight, topLeft = Offset(180f, 49.5f), size = Size(10.5f, 4.8f), cornerRadius = CornerRadius(2.5f, 2.5f))

    drawWheelArchCutout(rearX, axleY, wheelR, palette, flared = true)
    drawWheelArchCutout(frontX, axleY, wheelR, palette, flared = true)
    drawWheel(rearX, axleY, wheelR, palette, spokes = 5)
    drawWheel(frontX, axleY, wheelR, palette, spokes = 5)
}

private fun DrawScope.drawPickup(palette: RigPalette, gear: VehicleRigGear) {
    val rearX = 56f
    val frontX = 166f
    val axleY = 73.5f
    val wheelR = 12.6f

    if (gear == VehicleRigGear.ROOF_RACK || gear == VehicleRigGear.EXPEDITION) {
        // Chase / roll bar over bed
        val rackPath = Path().apply {
            moveTo(35f, 44f)
            lineTo(48f, 25.5f)
            lineTo(86f, 25.5f)
        }
        drawPath(rackPath, color = palette.cladding, style = Stroke(width = 2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    if (gear == VehicleRigGear.EXPEDITION) {
        // Overland bed rack cargo box + roof LED bar
        drawRoundRect(color = Color(0xFF424A45), topLeft = Offset(30f, 34.5f), size = Size(50f, 9f), cornerRadius = CornerRadius(2f, 2f))
        drawRoofGear(92f, 138f, 23f, palette, VehicleRigGear.EXPEDITION)
    } else if (gear == VehicleRigGear.ROOF_RACK) {
        drawRoofGear(92f, 138f, 23f, palette, VehicleRigGear.ROOF_RACK)
    }

    // Crew-cab pickup proportions (longer bed + true 4-door cab + bold front fascia)
    val body = Path().apply {
        moveTo(24f, 68f)
        lineTo(24f, 44f)
        lineTo(87f, 44f)
        lineTo(92f, 24.5f)
        quadraticTo(93.5f, 23f, 97f, 23f)
        lineTo(135f, 23f)
        quadraticTo(139f, 23f, 141.5f, 26f)
        lineTo(154f, 43f)
        lineTo(189f, 45f)
        quadraticTo(194f, 46.5f, 194f, 52f)
        lineTo(194f, 68f)
        close()
    }
    drawPath(body, brush = palette.bodyBrush)
    drawPath(body, color = palette.outline, style = Stroke(width = 1.4f, join = StrokeJoin.Round))

    // Bed-to-cab split line
    drawLine(
        color = palette.outline.copy(alpha = 0.65f),
        start = Offset(87.5f, 44f),
        end = Offset(87.5f, 66f),
        strokeWidth = 1.3f
    )

    // Rugged bumpers & running board between wheel arches
    drawRoundRect(color = palette.cladding, topLeft = Offset(22.5f, 64f), size = Size(16f, 6f), cornerRadius = CornerRadius(2f, 2f))
    drawRoundRect(color = palette.cladding, topLeft = Offset(182f, 64f), size = Size(14f, 6f), cornerRadius = CornerRadius(2f, 2f))
    drawRoundRect(color = palette.cladding, topLeft = Offset(72f, 67.5f), size = Size(78f, 3.8f), cornerRadius = CornerRadius(1.5f, 1.5f))

    drawLine(
        color = palette.upperHighlight.copy(alpha = 0.72f),
        start = Offset(26f, 47f),
        end = Offset(190f, 47.5f),
        strokeWidth = 1.6f,
        cap = StrokeCap.Round
    )

    if (gear == VehicleRigGear.RALLY_TRIM) {
        drawLine(color = palette.rallyAccent, start = Offset(26f, 57.5f), end = Offset(188f, 57.5f), strokeWidth = 2.4f, cap = StrokeCap.Round)
        drawRoundRect(color = palette.rallyAccent, topLeft = Offset(38f, 75f), size = Size(3.6f, 8.5f), cornerRadius = CornerRadius(1f, 1f))
    }

    // Crew-cab 2-window DLO
    val cabGlass = Path().apply {
        moveTo(95f, 26.5f)
        lineTo(134f, 26.5f)
        lineTo(148f, 42f)
        lineTo(92f, 42f)
        close()
    }
    drawPath(cabGlass, brush = palette.glassBrush)
    drawGlassReflection(112f, 26.5f, 42f, 6f, palette.glassGloss)
    drawLine(color = palette.pillarDark, start = Offset(114f, 26f), end = Offset(114f, 43f), strokeWidth = 3.2f)

    drawDoorSeamsAndMirror(143f, 40f, 49.5f, 121f, 97f, palette)

    drawRoundRect(color = palette.taillight, topLeft = Offset(24f, 46.5f), size = Size(4f, 9.5f), cornerRadius = CornerRadius(1.5f, 1.5f))
    drawRoundRect(color = palette.headlight, topLeft = Offset(185f, 47.5f), size = Size(8.5f, 6f), cornerRadius = CornerRadius(2f, 2f))

    drawWheelArchCutout(rearX, axleY, wheelR, palette, flared = true)
    drawWheelArchCutout(frontX, axleY, wheelR, palette, flared = true)
    drawWheel(rearX, axleY, wheelR, palette, spokes = 6, offroadTread = true)
    drawWheel(frontX, axleY, wheelR, palette, spokes = 6, offroadTread = true)
}

private fun DrawScope.drawSedan(palette: RigPalette, gear: VehicleRigGear) {
    val rearX = 60f
    val frontX = 160f
    val axleY = 75.5f
    val wheelR = 10.6f

    drawRoofGear(72f, 126f, 29f, palette, gear)

    // Classic 3-box Touring Sedan: distinct trunk deck, arched greenhouse, sculpted hood
    val body = Path().apply {
        moveTo(26f, 71f)
        lineTo(25f, 52f)
        quadraticTo(26f, 47f, 33f, 46.5f)
        lineTo(48f, 45.5f)
        lineTo(68f, 31f)
        quadraticTo(72f, 29f, 78f, 29f)
        lineTo(123f, 29f)
        quadraticTo(131f, 29.5f, 138f, 34f)
        lineTo(156f, 47f)
        lineTo(188f, 51f)
        quadraticTo(194f, 53.5f, 194f, 61f)
        lineTo(193f, 71f)
        close()
    }
    drawPath(body, brush = palette.bodyBrush)
    drawPath(body, color = palette.outline, style = Stroke(width = 1.4f, join = StrokeJoin.Round))

    // Crisp lower rocker trim
    drawRoundRect(color = palette.cladding, topLeft = Offset(74f, 69f), size = Size(72f, 3.2f), cornerRadius = CornerRadius(1.5f, 1.5f))

    // Character crease
    drawLine(
        color = palette.upperHighlight.copy(alpha = 0.72f),
        start = Offset(30f, 51f),
        end = Offset(186f, 53f),
        strokeWidth = 1.5f,
        cap = StrokeCap.Round
    )

    if (gear == VehicleRigGear.RALLY_TRIM) {
        drawLine(color = palette.rallyAccent, start = Offset(30f, 60f), end = Offset(186f, 61f), strokeWidth = 2.0f, cap = StrokeCap.Round)
    }

    // Swept Sedan greenhouse
    val glass = Path().apply {
        moveTo(73f, 32f)
        lineTo(123f, 32f)
        quadraticTo(130f, 32.5f, 137f, 37f)
        lineTo(150f, 46.5f)
        lineTo(54f, 46f)
        close()
    }
    drawPath(glass, brush = palette.glassBrush)
    drawGlassReflection(96f, 32f, 46.5f, 7f, palette.glassGloss)
    drawLine(color = palette.pillarDark, start = Offset(102f, 31.5f), end = Offset(103f, 47f), strokeWidth = 3.2f)

    drawDoorSeamsAndMirror(141f, 43.5f, 51.5f, 111f, 82f, palette)

    drawRoundRect(color = palette.taillight, topLeft = Offset(25.5f, 49.5f), size = Size(8.5f, 4.2f), cornerRadius = CornerRadius(2f, 2f))
    drawRoundRect(color = palette.headlight, topLeft = Offset(183f, 52.5f), size = Size(9.8f, 4.4f), cornerRadius = CornerRadius(2.2f, 2.2f))

    drawWheelArchCutout(rearX, axleY, wheelR, palette, flared = false)
    drawWheelArchCutout(frontX, axleY, wheelR, palette, flared = false)
    drawWheel(rearX, axleY, wheelR, palette, spokes = 7)
    drawWheel(frontX, axleY, wheelR, palette, spokes = 7)
}

private fun DrawScope.drawHatchback(palette: RigPalette, gear: VehicleRigGear) {
    val rearX = 63f
    val frontX = 158f
    val axleY = 75.5f
    val wheelR = 10.6f

    drawRoofGear(46f, 118f, 27.5f, palette, gear)

    // Nimble compact 5-door Hatchback (short rear overhang, upright hatch + roof spoiler)
    val body = Path().apply {
        moveTo(35f, 70.5f)
        quadraticTo(33f, 54f, 37f, 40f)
        lineTo(42f, 29f)
        quadraticTo(43.5f, 27.5f, 48f, 27.5f)
        lineTo(116f, 27.5f)
        quadraticTo(125f, 28f, 134f, 34f)
        lineTo(150f, 46f)
        lineTo(183f, 50.5f)
        quadraticTo(189f, 53f, 189f, 61f)
        lineTo(188f, 70.5f)
        close()
    }
    drawPath(body, brush = palette.bodyBrush)
    drawPath(body, color = palette.outline, style = Stroke(width = 1.4f, join = StrokeJoin.Round))

    // Rear hatch roof spoiler lip
    drawRoundRect(color = palette.pillarDark, topLeft = Offset(37f, 26.5f), size = Size(10f, 3.2f), cornerRadius = CornerRadius(1.5f, 1.5f))

    // Rocker trim
    drawRoundRect(color = palette.cladding, topLeft = Offset(77f, 68.5f), size = Size(67f, 3.5f), cornerRadius = CornerRadius(1.5f, 1.5f))

    drawLine(
        color = palette.upperHighlight.copy(alpha = 0.72f),
        start = Offset(39f, 49.5f),
        end = Offset(181f, 52.5f),
        strokeWidth = 1.5f,
        cap = StrokeCap.Round
    )

    if (gear == VehicleRigGear.RALLY_TRIM) {
        drawLine(color = palette.rallyAccent, start = Offset(38f, 59.5f), end = Offset(181f, 60.5f), strokeWidth = 2.1f, cap = StrokeCap.Round)
        drawRoundRect(color = palette.rallyAccent, topLeft = Offset(46f, 77f), size = Size(3.2f, 7.5f), cornerRadius = CornerRadius(1f, 1f))
    }

    val glass = Path().apply {
        moveTo(47f, 30.5f)
        lineTo(116f, 30.5f)
        quadraticTo(124f, 31f, 131f, 36.5f)
        lineTo(143f, 45.5f)
        lineTo(42f, 45f)
        close()
    }
    drawPath(glass, brush = palette.glassBrush)
    drawGlassReflection(85f, 30.5f, 45.5f, 6.5f, palette.glassGloss)
    drawLine(color = palette.pillarDark, start = Offset(92f, 30f), end = Offset(93f, 46f), strokeWidth = 3.2f)

    drawDoorSeamsAndMirror(136f, 42.5f, 51f, 102f, 74f, palette)

    drawRoundRect(color = palette.taillight, topLeft = Offset(35.5f, 47f), size = Size(7f, 5.2f), cornerRadius = CornerRadius(2f, 2f))
    drawRoundRect(color = palette.headlight, topLeft = Offset(178f, 52f), size = Size(9.5f, 4.5f), cornerRadius = CornerRadius(2.2f, 2.2f))

    drawWheelArchCutout(rearX, axleY, wheelR, palette, flared = false)
    drawWheelArchCutout(frontX, axleY, wheelR, palette, flared = false)
    drawWheel(rearX, axleY, wheelR, palette, spokes = 5)
    drawWheel(frontX, axleY, wheelR, palette, spokes = 5)
}

private fun DrawScope.drawCoupe(palette: RigPalette, gear: VehicleRigGear) {
    val rearX = 58f
    val frontX = 162f
    val axleY = 75.5f
    val wheelR = 11.0f

    drawRoofGear(76f, 118f, 31.5f, palette, gear)

    // Sleek Fastback Sport Coupe: low cabin, long sculpted hood, tapered rear deck + ducktail
    val body = Path().apply {
        moveTo(27f, 71f)
        lineTo(27f, 52f)
        lineTo(31f, 48.5f)
        lineTo(44f, 47f)
        quadraticTo(62f, 34f, 80f, 31.5f)
        lineTo(116f, 31.5f)
        quadraticTo(126f, 32f, 134f, 38f)
        lineTo(150f, 48.5f)
        lineTo(189f, 53f)
        quadraticTo(194f, 55.5f, 194f, 62f)
        lineTo(193f, 71f)
        close()
    }
    drawPath(body, brush = palette.bodyBrush)
    drawPath(body, color = palette.outline, style = Stroke(width = 1.4f, join = StrokeJoin.Round))

    // Integrated subtle ducktail lip
    val spoiler = Path().apply {
        moveTo(29f, 48.5f)
        lineTo(24.5f, 44.5f)
        lineTo(34f, 47f)
    }
    drawPath(spoiler, color = palette.cladding, style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))

    // Lower aero rocker blade
    drawRoundRect(color = palette.cladding, topLeft = Offset(72f, 69f), size = Size(76f, 3.2f), cornerRadius = CornerRadius(1.5f, 1.5f))

    // Sculpted rear haunch flare
    val haunch = Path().apply {
        moveTo(32f, 53f)
        quadraticTo(62f, 48f, 92f, 54f)
        lineTo(185f, 55f)
    }
    drawPath(haunch, color = palette.upperHighlight.copy(alpha = 0.72f), style = Stroke(width = 1.6f, cap = StrokeCap.Round))

    if (gear == VehicleRigGear.RALLY_TRIM) {
        drawLine(color = palette.rallyAccent, start = Offset(30f, 61.5f), end = Offset(186f, 62.5f), strokeWidth = 2.0f, cap = StrokeCap.Round)
    }

    // Frameless teardrop side glass
    val glass = Path().apply {
        moveTo(80f, 34.5f)
        lineTo(116f, 34.5f)
        quadraticTo(124f, 35f, 131f, 40.5f)
        lineTo(144f, 48f)
        lineTo(56f, 47.5f)
        close()
    }
    drawPath(glass, brush = palette.glassBrush)
    drawGlassReflection(96f, 34.5f, 48f, 6f, palette.glassGloss)
    drawLine(color = palette.pillarDark, start = Offset(104f, 34f), end = Offset(106f, 48.5f), strokeWidth = 2.6f)

    drawDoorSeamsAndMirror(136f, 45f, 53f, 94f, null, palette)

    drawRoundRect(color = palette.taillight, topLeft = Offset(27.5f, 51f), size = Size(9.5f, 3.8f), cornerRadius = CornerRadius(2f, 2f))
    drawRoundRect(color = palette.headlight, topLeft = Offset(183f, 54.5f), size = Size(10f, 3.8f), cornerRadius = CornerRadius(2f, 2f))

    drawWheelArchCutout(rearX, axleY, wheelR, palette, flared = false)
    drawWheelArchCutout(frontX, axleY, wheelR, palette, flared = false)
    drawWheel(rearX, axleY, wheelR, palette, spokes = 8)
    drawWheel(frontX, axleY, wheelR, palette, spokes = 8)
}

private fun DrawScope.drawVan(palette: RigPalette, gear: VehicleRigGear) {
    val rearX = 57f
    val frontX = 165f
    val axleY = 74f
    val wheelR = 11.8f

    drawRoofGear(36f, 146f, 21.5f, palette, gear)

    // Modern Camper / Overland Van silhouette
    val body = Path().apply {
        moveTo(27f, 69.5f)
        lineTo(27f, 27f)
        quadraticTo(27.5f, 21.5f, 34f, 21.5f)
        lineTo(143f, 21.5f)
        quadraticTo(152f, 22f, 158f, 28f)
        lineTo(175f, 47f)
        lineTo(188f, 51.5f)
        quadraticTo(193f, 54f, 193f, 61f)
        lineTo(192f, 69.5f)
        close()
    }
    drawPath(body, brush = palette.bodyBrush)
    drawPath(body, color = palette.outline, style = Stroke(width = 1.4f, join = StrokeJoin.Round))

    // Lower cladding & bumpers
    drawRoundRect(color = palette.cladding, topLeft = Offset(26f, 65f), size = Size(16f, 5.5f), cornerRadius = CornerRadius(2f, 2f))
    drawRoundRect(color = palette.cladding, topLeft = Offset(179f, 65f), size = Size(14.5f, 5.5f), cornerRadius = CornerRadius(2f, 2f))
    drawRoundRect(color = palette.cladding, topLeft = Offset(72f, 67f), size = Size(78f, 4.0f), cornerRadius = CornerRadius(1.5f, 1.5f))

    // Sliding door rail groove + shoulder line
    drawLine(
        color = palette.upperHighlight.copy(alpha = 0.72f),
        start = Offset(30f, 47f),
        end = Offset(187f, 50f),
        strokeWidth = 1.5f,
        cap = StrokeCap.Round
    )
    drawLine(color = palette.outline.copy(alpha = 0.45f), start = Offset(128f, 26f), end = Offset(128f, 67f), strokeWidth = 1.1f)

    if (gear == VehicleRigGear.RALLY_TRIM) {
        drawLine(color = palette.rallyAccent, start = Offset(30f, 57.5f), end = Offset(184f, 59f), strokeWidth = 2.2f, cap = StrokeCap.Round)
    }

    // Cab front window + rear camper lounge window
    val frontWin = Path().apply {
        moveTo(132f, 26f)
        lineTo(148f, 26f)
        quadraticTo(153f, 26.5f, 157f, 31f)
        lineTo(170f, 45.5f)
        lineTo(132f, 45.5f)
        close()
    }
    drawPath(frontWin, brush = palette.glassBrush)
    drawGlassReflection(142f, 26f, 45.5f, 5f, palette.glassGloss)

    drawRoundRect(
        brush = palette.glassBrush,
        topLeft = Offset(44f, 26.5f),
        size = Size(76f, 18.5f),
        cornerRadius = CornerRadius(3.5f, 3.5f)
    )
    drawGlassReflection(66f, 26.5f, 45f, 5f, palette.glassGloss)

    drawDoorSeamsAndMirror(163f, 43f, 51.5f, 134f, 114f, palette)

    drawRoundRect(color = palette.taillight, topLeft = Offset(27f, 46.5f), size = Size(3.8f, 11f), cornerRadius = CornerRadius(1.5f, 1.5f))
    drawRoundRect(color = palette.headlight, topLeft = Offset(183f, 52.5f), size = Size(8.5f, 5.5f), cornerRadius = CornerRadius(2f, 2f))

    drawWheelArchCutout(rearX, axleY, wheelR, palette, flared = false)
    drawWheelArchCutout(frontX, axleY, wheelR, palette, flared = false)
    drawWheel(rearX, axleY, wheelR, palette, spokes = 6)
    drawWheel(frontX, axleY, wheelR, palette, spokes = 6)
}

private fun DrawScope.drawMoto(palette: RigPalette, gear: VehicleRigGear) {
    val rearX = 60f
    val frontX = 160f
    val axleY = 71.5f
    val wheelR = 14.5f

    drawWheel(rearX, axleY, wheelR, palette, spokes = 8, offroadTread = true)
    drawWheel(frontX, axleY, wheelR, palette, spokes = 8, offroadTread = true)

    // Rear swingarm + chain guard
    drawLine(
        color = Color(0xFF5D6560),
        start = Offset(rearX, axleY),
        end = Offset(102f, 60f),
        strokeWidth = 4.2f,
        cap = StrokeCap.Round
    )
    // Upswept stainless exhaust can
    val muffler = Path().apply {
        moveTo(52f, 56f)
        lineTo(94f, 66f)
        lineTo(96f, 69.5f)
        lineTo(50f, 61f)
        close()
    }
    drawPath(muffler, color = palette.alloy)
    drawPath(muffler, color = palette.outline, style = Stroke(width = 1.0f, join = StrokeJoin.Round))

    // Engine cases + cylinder block
    drawRoundRect(
        color = Color(0xFF393F3B),
        topLeft = Offset(94f, 51f),
        size = Size(32f, 18f),
        cornerRadius = CornerRadius(4f, 4f)
    )
    drawRoundRect(
        color = Color(0xFF666E69),
        topLeft = Offset(102f, 45f),
        size = Size(16f, 9f),
        cornerRadius = CornerRadius(2f, 2f)
    )

    // Trellis frame tubes + rear subframe
    drawLine(color = palette.cladding, start = Offset(72f, 46f), end = Offset(104f, 52f), strokeWidth = 2.8f, cap = StrokeCap.Round)
    drawLine(color = palette.cladding, start = Offset(102f, 45f), end = Offset(136f, 34f), strokeWidth = 3.2f, cap = StrokeCap.Round)

    // Inverted USD front forks + high adventure front fender
    drawLine(
        color = palette.alloy,
        start = Offset(135f, 30f),
        end = Offset(frontX, axleY),
        strokeWidth = 3.8f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = Color(0xFFD6B254),
        start = Offset(137f, 34f),
        end = Offset(146f, 48f),
        strokeWidth = 4.4f,
        cap = StrokeCap.Round
    )
    val frontFender = Path().apply {
        moveTo(143f, 49f)
        quadraticTo(160f, 45f, 174f, 51f)
    }
    drawPath(frontFender, color = palette.base, style = Stroke(width = 3.4f, cap = StrokeCap.Round))

    // Optional Expedition/Rack Adventure Pannier + Top Tail Box
    if (gear == VehicleRigGear.ROOF_RACK || gear == VehicleRigGear.EXPEDITION) {
        drawRoundRect(
            color = palette.cladding,
            topLeft = Offset(45f, 41f),
            size = Size(24f, 17f),
            cornerRadius = CornerRadius(3.5f, 3.5f)
        )
        drawRoundRect(
            color = palette.alloy.copy(alpha = 0.65f),
            topLeft = Offset(48f, 44f),
            size = Size(18f, 2.5f),
            cornerRadius = CornerRadius(1f, 1f)
        )
        if (gear == VehicleRigGear.EXPEDITION) {
            drawRoundRect(
                color = Color(0xFF424A45),
                topLeft = Offset(44f, 28f),
                size = Size(19f, 11.5f),
                cornerRadius = CornerRadius(3f, 3f)
            )
        }
    }

    // Tail cowl + sculpted fuel tank + ADV beak fairing
    val body = Path().apply {
        moveTo(56f, 42f)
        lineTo(96f, 45f)
        quadraticTo(114f, 29f, 134f, 32f)
        lineTo(154f, 39.5f)
        lineTo(136f, 49f)
        lineTo(100f, 49.5f)
        lineTo(58f, 45.5f)
        close()
    }
    drawPath(body, brush = palette.bodyBrush)
    drawPath(body, color = palette.outline, style = Stroke(width = 1.4f, join = StrokeJoin.Round))

    if (gear == VehicleRigGear.RALLY_TRIM) {
        drawLine(color = palette.rallyAccent, start = Offset(112f, 38f), end = Offset(146f, 41f), strokeWidth = 2.2f, cap = StrokeCap.Round)
    }

    // Two-up ergonomic saddle
    val seat = Path().apply {
        moveTo(64f, 41.5f)
        lineTo(84f, 42f)
        quadraticTo(96f, 45f, 108f, 41.5f)
        lineTo(106f, 46.5f)
        lineTo(66f, 44.5f)
        close()
    }
    drawPath(seat, color = palette.pillarDark)

    // Handlebar + handguard + tall windscreen + LED headlight
    drawLine(color = palette.pillarDark, start = Offset(128f, 27.5f), end = Offset(137f, 25f), strokeWidth = 3.0f, cap = StrokeCap.Round)
    drawLine(
        color = Color(0xFF9AC4D0).copy(alpha = 0.88f),
        start = Offset(137f, 33f),
        end = Offset(144f, 20f),
        strokeWidth = 2.6f,
        cap = StrokeCap.Round
    )
    drawCircle(color = palette.headlight, radius = 3.4f, center = Offset(149f, 39.5f))
    drawRoundRect(color = palette.taillight, topLeft = Offset(55f, 42f), size = Size(4.5f, 3.0f), cornerRadius = CornerRadius(1.5f, 1.5f))
}
