package com.twocircle.bike.feature.social.ui.feed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.twocircle.bike.domain.model.Coord

/**
 * Lightweight 2D track preview for feed cards — no MapLibre GL context required.
 */
@Composable
fun MiniTrackPreview(
    points: List<Coord>,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E252D))
    ) {
        if (points.size < 2) return@Canvas

        val minLat = points.minOf { it.lat }
        val maxLat = points.maxOf { it.lat }
        val minLon = points.minOf { it.lon }
        val maxLon = points.maxOf { it.lon }

        val latRange = (maxLat - minLat).coerceAtLeast(0.001)
        val lonRange = (maxLon - minLon).coerceAtLeast(0.001)

        val path = Path()
        points.forEachIndexed { i, pt ->
            val x = (((pt.lon - minLon) / lonRange) * 0.85 + 0.075).toFloat() * size.width
            val y = ((1.0 - ((pt.lat - minLat) / latRange)) * 0.85 + 0.075).toFloat() * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        // Glow path under
        drawPath(
            path = path,
            color = Color(0x444CAF50),
            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        // Clean line on top
        drawPath(
            path = path,
            color = Color(0xFF4CAF50),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
