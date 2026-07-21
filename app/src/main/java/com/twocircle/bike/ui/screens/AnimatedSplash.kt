package com.twocircle.bike.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twocircle.bike.designsystem.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Brand splash animation v2 — dual circle glyph scale + bounce + tagline fade in.
 */
@Composable
fun AnimatedSplash(
    onAnimationComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Phase 1: brand glyph scale with bounce
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            )
        }
        // Phase 2: text fade in
        launch {
            alpha.animateTo(1f, tween(400))
        }
        // Hold for 800ms
        delay(900)
        onAnimationComplete()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        this.alpha = alpha.value
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(140.dp)) {
                    // Green left circle
                    drawCircle(
                        color = Color(0xFF4CAF50),
                        radius = 32.dp.toPx(),
                        center = Offset(size.width * 0.35f, size.height * 0.5f),
                        style = Stroke(width = 5.dp.toPx()),
                    )
                    // Blue right circle
                    drawCircle(
                        color = Color(0xFF2C5F7E),
                        radius = 32.dp.toPx(),
                        center = Offset(size.width * 0.65f, size.height * 0.5f),
                        style = Stroke(width = 5.dp.toPx()),
                    )
                    // Connecting line
                    drawLine(
                        color = Color(0xFFE0E4E8),
                        start = Offset(size.width * 0.38f, size.height * 0.5f),
                        end = Offset(size.width * 0.62f, size.height * 0.5f),
                        strokeWidth = 4.dp.toPx(),
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "2circle",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.graphicsLayer { this.alpha = alpha.value },
            )
            Text(
                text = stringResource(R.string.splash_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.graphicsLayer { this.alpha = alpha.value },
            )
        }
    }
}
