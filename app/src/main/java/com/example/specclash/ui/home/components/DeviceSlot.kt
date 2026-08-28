package com.example.specclash.ui.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.specclash.domain.SearchDevice

@Composable
fun DeviceSlot(
    label: String,
    device: SearchDevice?,
    loading: Boolean,
    accent: Color,
    onClick: () -> Unit,
    onClear: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(150.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (device != null) accent.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (device != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(70.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(accent.copy(alpha = 0.35f), Color.Transparent),
                                radius = 70f,
                            ),
                        )
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    if (device != null && onClear != null) {
                        IconButton(
                            onClick = onClear,
                            modifier = Modifier.size(20.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Clear $label",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
                if (device != null) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        text = "Tap to choose",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (loading) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(200)),
                            exit = fadeOut(tween(200)),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = accent,
                                strokeWidth = 2.5.dp,
                            )
                        }
                    }
                    if (!loading && device != null) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(200)),
                            exit = fadeOut(tween(200)),
                        ) {
                            val ctx = androidx.compose.ui.platform.LocalContext.current
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(ctx)
                                    .data(device?.thumbnail)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Device thumbnail",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                    if (!loading && device == null) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(200)),
                            exit = fadeOut(tween(200)),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Smartphone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
