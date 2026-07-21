package com.twocircle.bike.feature.social.ui.feed

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twocircle.bike.domain.model.Coord
import kotlinx.coroutines.launch

data class FeedItem(
    val id: String,
    val authorName: String,
    val timeAgo: String,
    val title: String,
    val distance: String,
    val duration: String,
    val ascent: String,
    val points: List<Coord>,
    var kudosCount: Int,
    var isKudosGiven: Boolean = false,
    val commentsCount: Int,
)

@Composable
fun FeedCard(
    item: FeedItem,
    onKudosClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header: Avatar + Author + Time
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = item.authorName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = item.timeAgo,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }

            // Track mini preview
            MiniTrackPreview(points = item.points)

            // Title & Telemetry
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${item.distance}  •  ${item.duration}  •  ↑ ${item.ascent}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }

            // Action row: Kudos & Comments
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val haptic = LocalHapticFeedback.current
                val scope = rememberCoroutineScope()
                val heartColor by animateColorAsState(
                    targetValue = if (item.isKudosGiven) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    animationSpec = spring(),
                    label = "KudosColorAnimation"
                )

                val burstScale = remember { Animatable(0f) }
                val burstAlpha = remember { Animatable(0f) }

                LaunchedEffect(item.isKudosGiven) {
                    if (item.isKudosGiven) {
                        burstScale.snapTo(0f)
                        burstAlpha.snapTo(1f)
                        scope.launch {
                            burstScale.animateTo(2.5f, tween(400, easing = FastOutSlowInEasing))
                        }
                        scope.launch {
                            burstAlpha.animateTo(0f, tween(400))
                        }
                    }
                }

                Box(contentAlignment = Alignment.Center) {
                    if (burstScale.value > 0.01f) {
                        Canvas(
                            modifier = Modifier
                                .size(36.dp)
                                .graphicsLayer {
                                    scaleX = burstScale.value
                                    scaleY = burstScale.value
                                    alpha = burstAlpha.value
                                }
                        ) {
                            drawCircle(
                                color = Color(0xFFE91E63).copy(alpha = 0.4f),
                                radius = 14.dp.toPx(),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onKudosClick()
                            }
                            .padding(vertical = 4.dp),
                    ) {
                        Icon(
                            imageVector = if (item.isKudosGiven) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = null,
                            tint = heartColor,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${item.kudosCount}",
                            style = MaterialTheme.typography.labelLarge,
                            color = heartColor,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${item.commentsCount}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}
