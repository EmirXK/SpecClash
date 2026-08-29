package com.example.specclash.ui.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.specclash.domain.SpecComparator.FullComparisonVerdict
import com.example.specclash.domain.SpecComparator.ValueAnalysis
import com.example.specclash.domain.SpecComparator.Winner
import com.example.specclash.ui.util.ShareUtils
import kotlinx.coroutines.launch

/**
 * Bottom card that summarises the weighted comparison verdict:
 *  * Side-by-side composite hardware scores (0-100).
 *  * Outright hardware leader with trophy badge.
 *  * Value-for-Money champion (when prices are available).
 *  * Headline + trade-off summary.
 *  * Top-3 advantage bullets per device.
 *  * Price disclaimer footer.
 */
@Composable
fun VerdictCard(
    verdict: FullComparisonVerdict,
    accentA: Color,
    accentB: Color,
    modifier: Modifier = Modifier,
    isPriceACustom: Boolean = false,
    isPriceBCustom: Boolean = false,
    onEditPriceA: (() -> Unit)? = null,
    onEditPriceB: (() -> Unit)? = null,
) {
    val leaderAccent = when (verdict.winner) {
        Winner.A -> accentA
        Winner.B -> accentB
        Winner.TIE -> MaterialTheme.colorScheme.tertiary
    }
    val leaderName = when (verdict.winner) {
        Winner.A -> verdict.nameA
        Winner.B -> verdict.nameB
        Winner.TIE -> null
    }
    val value = verdict.value
    val hasValue = value != null && value.valueWinner != null

    val graphicsLayer = rememberGraphicsLayer()
    val shareScope = rememberCoroutineScope()
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        accentA.copy(alpha = 0.15f),
                        MaterialTheme.colorScheme.surface,
                        accentB.copy(alpha = 0.15f),
                    ),
                )
            )
            .drawWithContent {
                // Record the card's drawing commands into an offscreen layer
                // so the Share button can rasterize it to a PNG on demand.
                graphicsLayer.record { this@drawWithContent.drawContent() }
                drawLayer(graphicsLayer)
            }
            .padding(16.dp),
    ) {
        Column {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    tint = leaderAccent,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Hardware Verdict",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        shareScope.launch {
                            val bitmap = graphicsLayer.toImageBitmap()
                            ShareUtils.shareBitmap(
                                context = context,
                                imageBitmap = bitmap,
                                chooserTitle = "Share ${verdict.nameA} vs ${verdict.nameB}",
                            )
                        }
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Share verdict as image",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = verdict.headline,
                style = MaterialTheme.typography.headlineSmall,
                color = leaderAccent,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = verdict.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            // Composite scores side by side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ScoreBlock(
                    label = verdict.nameA,
                    score = verdict.overallScoreA,
                    accent = accentA,
                    isLeader = verdict.winner == Winner.A,
                    modifier = Modifier.weight(1f),
                )
                ScoreBlock(
                    label = verdict.nameB,
                    score = verdict.overallScoreB,
                    accent = accentB,
                    isLeader = verdict.winner == Winner.B,
                    modifier = Modifier.weight(1f),
                )
            }
            // Outright champion explicit line.
            Spacer(Modifier.height(12.dp))
            OutrightChampionLine(verdict, accentA, accentB)
            // Value-for-Money section.
            if (hasValue) {
                Spacer(Modifier.height(12.dp))
                ValueForMoneySection(
                    value = value!!,
                    accentA = accentA,
                    accentB = accentB,
                    isPriceACustom = isPriceACustom,
                    isPriceBCustom = isPriceBCustom,
                    onEditPriceA = onEditPriceA,
                    onEditPriceB = onEditPriceB,
                )
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Value-for-money analysis is unavailable \u2014 price data is missing for one or both devices.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(16.dp))
            // Top-3 advantage bullets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AdvantagesColumn(
                    title = verdict.nameA,
                    bullets = verdict.advantagesA,
                    accent = accentA,
                    modifier = Modifier.weight(1f),
                )
                AdvantagesColumn(
                    title = verdict.nameB,
                    bullets = verdict.advantagesB,
                    accent = accentB,
                    modifier = Modifier.weight(1f),
                )
            }
            if (leaderName == null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Both devices finish within striking distance of each other.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(12.dp))
            PriceDisclaimerFooter()
        }
    }
}

@Composable
private fun OutrightChampionLine(
    verdict: FullComparisonVerdict,
    accentA: Color,
    accentB: Color,
) {
    val leaderName: String? = when (verdict.winner) {
        Winner.A -> verdict.nameA
        Winner.B -> verdict.nameB
        Winner.TIE -> null
    }
    val accent = when (verdict.winner) {
        Winner.A -> accentA
        Winner.B -> accentB
        Winner.TIE -> MaterialTheme.colorScheme.tertiary
    }
    val text = when (verdict.winner) {
        Winner.A, Winner.B -> "\uD83C\uDFC6 Outright Hardware Leader: $leaderName"
        Winner.TIE -> "\uD83C\uDFC6 Outright Hardware: evenly matched"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.EmojiEvents,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = accent,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ValueForMoneySection(
    value: ValueAnalysis,
    accentA: Color,
    accentB: Color,
    isPriceACustom: Boolean = false,
    isPriceBCustom: Boolean = false,
    onEditPriceA: (() -> Unit)? = null,
    onEditPriceB: (() -> Unit)? = null,
) {
    val valueAccent = when (value.valueWinner) {
        Winner.A -> accentA
        Winner.B -> accentB
        else -> MaterialTheme.colorScheme.tertiary
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(valueAccent.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.MonetizationOn,
                contentDescription = null,
                tint = valueAccent,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Value for Money",
                style = MaterialTheme.typography.titleSmall,
                color = valueAccent,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PriceBadge(price = value.priceA, accent = accentA, isCustom = isPriceACustom, onClick = onEditPriceA)
            PriceBadge(price = value.priceB, accent = accentB, isCustom = isPriceBCustom, onClick = onEditPriceB)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Score per \$100 spent",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        // Animated value bars.
        val valueA = value.valueScoreA ?: 0.0
        val valueB = value.valueScoreB ?: 0.0
        val maxVal = maxOf(valueA, valueB, 0.001)
        val pctA by animateFloatAsState(
            targetValue = (valueA / maxVal).toFloat().coerceIn(0f, 1f),
            animationSpec = tween(700),
            label = "value-bar-a",
        )
        val pctB by animateFloatAsState(
            targetValue = (valueB / maxVal).toFloat().coerceIn(0f, 1f),
            animationSpec = tween(700),
            label = "value-bar-b",
        )
        ValueBar(
            label = "A",
            value = valueA,
            accent = accentA,
            progress = pctA,
        )
        Spacer(Modifier.height(4.dp))
        ValueBar(
            label = "B",
            value = valueB,
            accent = accentB,
            progress = pctB,
        )
        Spacer(Modifier.height(6.dp))
        // Best Value Pick line.
        val bestPickText = when (value.valueWinner) {
            Winner.A -> "\uD83D\uDCA1 Best Value Pick: A"
            Winner.B -> "\uD83D\uDCA1 Best Value Pick: B"
            Winner.TIE -> "\uD83D\uDCA1 Value-for-money: even matchup"
            null -> "\uD83D\uDCA1 Value-for-money unavailable"
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Lightbulb,
                contentDescription = null,
                tint = valueAccent,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = bestPickText,
                style = MaterialTheme.typography.labelMedium,
                color = valueAccent,
                fontWeight = FontWeight.SemiBold,
            )
        }
        // Optional advantage explanation.
        if (!value.valueAdvantageText.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = value.valueAdvantageText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ValueBar(label: String, value: Double, accent: Color, progress: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = String.format("%.1f", value),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun PriceDisclaimerFooter() {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "\u2139\uFE0F Note: Prices are approximate baseline estimates from GSMArena listings and vary by region, carrier, and retailer. Please verify local pricing.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScoreBlock(
    label: String,
    score: Double,
    accent: Color,
    isLeader: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = if (isLeader) 0.12f else 0.06f))
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = String.format("%.1f", score),
            style = MaterialTheme.typography.displaySmall,
            color = accent,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "/ 100 hardware score",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AdvantagesColumn(
    title: String,
    bullets: List<String>,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        bullets.take(3).forEach { bullet ->
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.padding(vertical = 2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp, end = 6.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
                Text(
                    text = bullet.removePrefix("+ ").removePrefix("- "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

