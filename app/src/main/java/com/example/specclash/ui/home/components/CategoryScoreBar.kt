package com.example.specclash.ui.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.specclash.domain.SpecComparator.Category
import com.example.specclash.domain.SpecComparator.CategoryScore
import com.example.specclash.domain.SpecComparator.Winner

/**
 * Relative-ratio dual score bar for one category.
 */
@Composable
fun CategoryScoreBar(
    score: CategoryScore,
    accentA: Color,
    accentB: Color,
    modifier: Modifier = Modifier,
) {
    val maxScore = score.maxScore.coerceAtLeast(100.0)
    val pctA = (score.scoreA / maxScore).toFloat().coerceIn(0f, 1f)
    val pctB = (score.scoreB / maxScore).toFloat().coerceIn(0f, 1f)
    val animatedA by animateFloatAsState(targetValue = pctA, animationSpec = tween(600), label = "bar-a")
    val animatedB by animateFloatAsState(targetValue = pctB, animationSpec = tween(600), label = "bar-b")
    val labelColor = when (score.winner) {
        Winner.A -> accentA
        Winner.B -> accentB
        Winner.TIE -> MaterialTheme.colorScheme.tertiary
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = score.category.emoji, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(6.dp))
            Text(
                text = score.category.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${score.scoreA.toInt()}% vs ${score.scoreB.toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(8.dp))
        ScoreTrack(animatedA, accentA)
        Spacer(Modifier.height(4.dp))
        ScoreTrack(animatedB, accentB)
        Spacer(Modifier.height(8.dp))
        Text(
            text = score.ratioText,
            style = MaterialTheme.typography.bodySmall,
            color = labelColor,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
private fun ScoreTrack(progress: Float, accent: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .clip(RoundedCornerShape(5.dp))
                .background(accent),
        )
    }
}

/**
 * Compact category score bar for the header of a section.
 */
@Composable
fun CategoryHeaderScore(
    category: Category,
    scoreA: Double,
    scoreB: Double,
    winner: Winner,
    accentA: Color,
    accentB: Color,
    modifier: Modifier = Modifier,
) {
    val max = maxOf(scoreA, scoreB, 100.0)
    val pctA = (scoreA / max).toFloat().coerceIn(0f, 1f)
    val pctB = (scoreB / max).toFloat().coerceIn(0f, 1f)
    val labelColor = when (winner) {
        Winner.A -> accentA
        Winner.B -> accentB
        Winner.TIE -> MaterialTheme.colorScheme.tertiary
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = category.emoji, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(6.dp))
            Text(
                text = category.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${scoreA.toInt()}% / ${scoreB.toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(4.dp))
        ScoreTrack(pctA, accentA)
        Spacer(Modifier.height(2.dp))
        ScoreTrack(pctB, accentB)
    }
}
