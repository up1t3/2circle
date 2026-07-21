package com.twocircle.bike.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Universal Shimmer Box primitive for skeleton loading states.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -300f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerX",
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    ),
                    start = Offset(x, 0f),
                    end = Offset(x + 300f, 0f),
                ),
            ),
    )
}

/**
 * Skeleton placeholder row for ride/track lists.
 */
@Composable
fun RideSkeletonRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ShimmerBox(Modifier.size(80.dp), cornerRadius = 12.dp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ShimmerBox(Modifier.fillMaxWidth(0.6f).height(18.dp))
            ShimmerBox(Modifier.fillMaxWidth(0.4f).height(14.dp))
            ShimmerBox(Modifier.fillMaxWidth(0.8f).height(12.dp))
        }
    }
}

/**
 * Skeleton placeholder card for social feed.
 */
@Composable
fun FeedCardSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShimmerBox(Modifier.size(36.dp), cornerRadius = 18.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ShimmerBox(Modifier.fillMaxWidth(0.4f).height(14.dp))
                ShimmerBox(Modifier.fillMaxWidth(0.25f).height(10.dp))
            }
        }
        ShimmerBox(Modifier.fillMaxWidth().height(160.dp), cornerRadius = 12.dp)
        ShimmerBox(Modifier.fillMaxWidth(0.7f).height(16.dp))
        ShimmerBox(Modifier.fillMaxWidth(0.5f).height(12.dp))
    }
}
