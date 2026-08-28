package com.example.specclash.ui.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Centered circular FAB that swaps the two devices' positions. The arrow icon
 * rotates 180° on tap to give a sense of inversion.
 */
@Composable
fun SwapButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (enabled) 180f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "swap-rotation",
    )
    Surface(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        color = MaterialTheme.colorScheme.primary,
        shape = CircleShape,
        shadowElevation = 4.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                contentDescription = "Swap A and B",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer { rotationZ = rotation },
            )
        }
    }
}
