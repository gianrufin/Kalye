package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.data.model.DistanceUnit
import com.example.data.model.GeoUtils
import com.example.data.model.RoutePoint
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ElevationProfileChart(
    points: List<RoutePoint>,
    distanceUnit: DistanceUnit,
    modifier: Modifier = Modifier
) {
    val profileData = remember(points) {
        if (points.isEmpty()) emptyList()
        else {
            var cumulativeDist = 0.0
            buildList {
                points.forEachIndexed { idx, pt ->
                    if (idx > 0) {
                        cumulativeDist += GeoUtils.haversineMeters(
                            points[idx - 1].latitude,
                            points[idx - 1].longitude,
                            pt.latitude,
                            pt.longitude
                        )
                    }
                    add(cumulativeDist to pt.altitudeMeters)
                }
            }
        }
    }

    var scrubbedIndex by remember(points) { mutableStateOf<Int?>(null) }

    val primaryColor = MaterialTheme.colorScheme.secondary
    val accentColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = surfaceColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ALTITUDE PROFILE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (profileData.isNotEmpty()) {
                    val activeIdx = scrubbedIndex ?: profileData.lastIndex
                    val (distM, altM) = profileData[activeIdx.coerceIn(0, profileData.lastIndex)]
                    val gradeStr = if (activeIdx > 0) {
                        val prev = profileData[activeIdx - 1]
                        val dDist = (distM - prev.first).coerceAtLeast(1.0)
                        val grade = ((altM - prev.second) / dDist * 100.0).coerceIn(-45.0, 45.0)
                        String.format(Locale.US, " • %+.1f%%", grade)
                    } else ""
                    Text(
                        text = "${GeoUtils.formatElevation(altM, distanceUnit)} @ ${GeoUtils.formatDistance(distM, distanceUnit)}$gradeStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (profileData.size < 2) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Record at least 2 GPS points to render altitude profile",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val minEle = remember(profileData) { profileData.minOf { it.second } }
                val maxEle = remember(profileData) { profileData.maxOf { it.second } }
                val totalDist = remember(profileData) { profileData.last().first.coerceAtLeast(1.0) }
                val eleSpan = (maxEle - minEle).coerceAtLeast(15.0)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(145.dp)
                        .pointerInput(profileData) {
                            detectTapGestures { offset ->
                                val frac = (offset.x / size.width).coerceIn(0f, 1f)
                                scrubbedIndex = (frac * (profileData.size - 1)).roundToInt()
                            }
                        }
                        .pointerInput(profileData) {
                            detectDragGestures(
                                onDragEnd = { scrubbedIndex = null },
                                onDragCancel = { scrubbedIndex = null }
                            ) { change, _ ->
                                val frac = (change.position.x / size.width).coerceIn(0f, 1f)
                                scrubbedIndex = (frac * (profileData.size - 1)).roundToInt()
                            }
                        }
                ) {
                    val topPad = 14f
                    val bottomPad = 14f
                    val usableH = (size.height - topPad - bottomPad).coerceAtLeast(20f)

                    // Horizontal contour reference lines
                    for (i in 0..2) {
                        val y = topPad + usableH * (i / 2f)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                        )
                    }

                    val linePath = Path()
                    val fillPath = Path()

                    profileData.forEachIndexed { idx, (dist, ele) ->
                        val x = ((dist / totalDist) * size.width).toFloat()
                        val y = topPad + ((1.0 - (ele - minEle) / eleSpan) * usableH).toFloat()
                        if (idx == 0) {
                            linePath.moveTo(x, y)
                            fillPath.moveTo(x, size.height)
                            fillPath.lineTo(x, y)
                        } else {
                            linePath.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }
                        if (idx == profileData.lastIndex) {
                            fillPath.lineTo(x, size.height)
                            fillPath.close()
                        }
                    }

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.36f),
                                primaryColor.copy(alpha = 0.02f)
                            )
                        )
                    )
                    drawPath(
                        path = linePath,
                        color = primaryColor,
                        style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    scrubbedIndex?.let { idx ->
                        val clamped = idx.coerceIn(0, profileData.lastIndex)
                        val (dist, ele) = profileData[clamped]
                        val sx = ((dist / totalDist) * size.width).toFloat()
                        val sy = topPad + ((1.0 - (ele - minEle) / eleSpan) * usableH).toFloat()
                        drawLine(
                            color = accentColor,
                            start = Offset(sx, 0f),
                            end = Offset(sx, size.height),
                            strokeWidth = 2.5f
                        )
                        drawCircle(Color.White, radius = 9f, center = Offset(sx, sy))
                        drawCircle(accentColor, radius = 6f, center = Offset(sx, sy))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Min ${GeoUtils.formatElevation(minEle, distanceUnit)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Touch & drag chart to inspect elevation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Peak ${GeoUtils.formatElevation(maxEle, distanceUnit)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}
