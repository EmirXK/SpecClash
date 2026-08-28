package com.example.specclash.ui.home.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Subtle shimmer overlay used for skeleton loading placeholders.
 */
fun Modifier.shimmer(
    base: Color = Color(0xFF1A1E27),
    highlight: Color = Color(0xFF2A3140),
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-translate",
    )
    this.background(base).drawWithCache {
        val brush = Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = Offset(translate - 500f, 0f),
            end = Offset(translate, size.height),
        )
        onDrawWithContent {
            drawContent()
            drawRect(brush = brush)
        }
    }
}
