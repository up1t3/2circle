package com.twocircle.bike.feature.tracks.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.twocircle.bike.domain.model.Coord

/**
 * Lightweight preview of a recorded track as a 2D polyline on a dark canvas.
 *
 * We deliberately do NOT use MapLibre here — for a small detail-screen preview the SDK
 * is overkill (style loading, GL context, lifecycle). A Canvas-drawn normalised polyline
 * is enough to convey the shape, costs nothing in dependencies, and renders instantly.
 *
 * The path is normalised to fill the canvas while preserving aspect ratio (so the track
 * shape isn't stretched). No map tiles under it — the dark background stands in for
 * "this is a map-like surface" without the bandwidth/storage cost.
 */
@Composable
fun TrackPreviewMap(
    points: List<Coord>,
    modifier: Modifier = Modifier,
) {
    if (points.size < 2) return

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF101418)),
    ) {
        val coords = points.map { it.lat to it.lon }
        val minLat = coords.minOf { it.first }
        val maxLat = coords.maxOf { it.first }
        val minLon = coords.minOf { it.second }
        val maxLon = coords.maxOf { it.second }

        // Preserve aspect ratio: scale uniformly by the smaller axis-span ratio.
        val latRange = (maxLat - minLat).coerceAtLeast(1e-6)
        val lonRange = (maxLon - minLon).coerceAtLeast(1e-6)
        // Compute in Double (mixing Float size with Double geo ranges), then collapse to Float.
        val scaleX = size.width.toDouble() / lonRange
        val scaleY = size.height.toDouble() / latRange
        val scale = (minOf(scaleX, scaleY) * 0.85)
        val offsetX = ((size.width - lonRange * scale) / 2.0).toFloat()
        val offsetY = ((size.height - latRange * scale) / 2.0).toFloat()
        val scaleF = scale.toFloat()

        fun project(c: Coord): Offset {
            val px: Float = offsetX + ((c.lon - minLon) * scaleF).toFloat()
            // Invert Y: latitude grows north (up), screen Y grows down.
            val py: Float = size.height - (offsetY + ((c.lat - minLat) * scaleF).toFloat())
            return Offset.Zero.copy(x = px, y = py)
        }

        val path = Path().apply {
            val first = project(points.first())
            moveTo(first.x, first.y)
            points.drop(1).forEach { p ->
                val o = project(p)
                lineTo(o.x, o.y)
            }
        }

        // Subtle glow under the line for readability on the dark background.
        drawPath(
            path = path,
            color = Color(0x5566BB6A),
            style = Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        // Crisp line on top.
        drawPath(
            path = path,
            color = Color(0xFF66BB6A),
            style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // Start marker.
        val start = project(points.first())
        drawCircle(color = Color(0xFF29B6F6), radius = 6f, center = start)
        // End marker.
        val end = project(points.last())
        drawCircle(color = Color(0xFFEF5350), radius = 6f, center = end)
    }
}
