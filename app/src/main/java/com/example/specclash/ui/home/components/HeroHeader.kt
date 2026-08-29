package com.example.specclash.ui.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.specclash.domain.PhoneSpec
import com.example.specclash.domain.SpecComparator.PriceInfo

/**
 * Hero section at the top of the comparison: side-by-side high-res renders of
 * the two selected phones, with their names underneath. The image swap
 * animates when the user toggles A/B.
 */
@Composable
fun HeroHeader(
    specA: PhoneSpec?,
    specB: PhoneSpec?,
    accentA: Color,
    accentB: Color,
    modifier: Modifier = Modifier,
    priceA: PriceInfo? = null,
    priceB: PriceInfo? = null,
    isPriceACustom: Boolean = false,
    isPriceBCustom: Boolean = false,
    onEditPriceA: (() -> Unit)? = null,
    onEditPriceB: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(20.dp)),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Subtle background gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                accentA.copy(alpha = 0.10f),
                                Color.Transparent,
                                accentB.copy(alpha = 0.10f),
                            ),
                        )
                    )
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HeroPane(
                    spec = specA,
                    accent = accentA,
                    label = "A",
                    price = priceA,
                    isPriceCustom = isPriceACustom,
                    onEditPrice = onEditPriceA,
                    modifier = Modifier.weight(1f),
                )
                HeroPane(
                    spec = specB,
                    accent = accentB,
                    label = "B",
                    price = priceB,
                    isPriceCustom = isPriceBCustom,
                    onEditPrice = onEditPriceB,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HeroPane(
    spec: PhoneSpec?,
    accent: Color,
    label: String,
    price: PriceInfo?,
    modifier: Modifier = Modifier,
    isPriceCustom: Boolean = false,
    onEditPrice: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LetterLabel(label, accent)
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            if (spec == null) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .shimmer(),
                )
            } else {
                AnimatedContent(
                    targetState = spec.image,
                    transitionSpec = {
                        (fadeIn(tween(300)) togetherWith fadeOut(tween(300)))
                    },
                    label = "hero-image",
                ) { url ->
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(ctx)
                            .data(url)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Device ${spec.name}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = spec?.name ?: "—",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        // Price badge
        Spacer(Modifier.height(4.dp))
        PriceBadge(price = price, accent = accent, isCustom = isPriceCustom, onClick = onEditPrice)
    }
}

@Composable
private fun LetterLabel(label: String, accent: Color) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Compact pill chip that surfaces the device's price.
 *  * Priced    -> "\uD83D\uDCB0 $689" or "~\u20AC100" in primary/secondary container styling.
 *  * Unpriced  -> muted "Price N/A".
 *  * Custom    -> the domain layer appends " (Custom)" to [PriceInfo.formattedDisplay]
 *    when a manual override is active; an edit-pencil icon renders whenever
 *    [onClick] is supplied so the badge is discoverable as tappable.
 */
@Composable
internal fun PriceBadge(
    price: PriceInfo?,
    accent: Color,
    isCustom: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isPriced = price != null && price.amountUsd > 0.0
    val chipColor = if (isPriced) accent else MaterialTheme.colorScheme.onSurfaceVariant
    val chipBg = when {
        isCustom -> accent.copy(alpha = 0.28f)
        isPriced -> accent.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val text = when {
        isPriced && price != null -> "\uD83D\uDCB0 ${price.formattedDisplay}"
        else -> "Price N/A"
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(chipBg)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClickLabel = "Edit price", onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = chipColor,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (onClick != null) {
            Spacer(Modifier.width(3.dp))
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "Edit price",
                tint = chipColor,
                modifier = Modifier.size(11.dp),
            )
        }
    }
}
