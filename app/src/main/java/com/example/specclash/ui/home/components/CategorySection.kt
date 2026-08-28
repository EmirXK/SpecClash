package com.example.specclash.ui.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.specclash.domain.SpecComparator
import com.example.specclash.domain.SpecComparator.CategoryScore
import com.example.specclash.domain.SpecComparator.Comparison
import com.example.specclash.domain.SpecComparator.Winner

/**
 * One collapsible category section: header + list of [SpecRow]s.
 * `rows` should already be filtered by the parent if the user enabled
 * "Show differences only".
 */
@Composable
fun CategorySection(
    title: String,
    rows: List<SpecRowData>,
    accentA: Color,
    accentB: Color,
    initiallyExpanded: Boolean = true,
    modifier: Modifier = Modifier,
    categoryScore: CategoryScore? = null,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = tween(200),
        label = "category-rotate",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = rows.size.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
            Spacer(Modifier.size(8.dp))
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(rotation),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(200)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(200)),
        ) {
            Column {
                if (categoryScore != null) {
                    CategoryHeaderScore(
                        category = categoryScore.category,
                        scoreA = categoryScore.scoreA,
                        scoreB = categoryScore.scoreB,
                        winner = categoryScore.winner,
                        accentA = accentA,
                        accentB = accentB,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    )
                }
                rows.forEachIndexed { index, row ->
                    SpecRow(
                        data = row,
                        accentA = accentA,
                        accentB = accentB,
                    )
                    if (index < rows.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        )
                    }
                }
            }
        }
    }
}

data class SpecRowData(
    val key: String,
    val valueA: String?,
    val valueB: String?,
    val comparison: Comparison,
)

/**
 * Single comparison row: [key] on the left, then value A, then value B.
 * The winner's cell gets a tinted background + a delta text underneath.
 */
@Composable
fun SpecRow(
    data: SpecRowData,
    accentA: Color,
    accentB: Color,
    modifier: Modifier = Modifier,
) {
    val highlightA = when (data.comparison.winner) {
        Winner.A -> accentA
        else -> Color.Transparent
    }
    val highlightB = when (data.comparison.winner) {
        Winner.B -> accentB
        else -> Color.Transparent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(0.9f)
                .padding(end = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = data.key,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }
        ValueCell(
            value = data.valueA,
            highlight = highlightA,
            accent = accentA,
            delta = if (data.comparison.winner == Winner.A) data.comparison.deltaText else null,
            modifier = Modifier.weight(1f),
            alignEnd = false,
        )
        Spacer(Modifier.size(6.dp))
        ValueCell(
            value = data.valueB,
            highlight = highlightB,
            accent = accentB,
            delta = if (data.comparison.winner == Winner.B) data.comparison.deltaText else null,
            modifier = Modifier.weight(1f),
            alignEnd = true,
        )
    }
}

@Composable
private fun ValueCell(
    value: String?,
    highlight: Color,
    accent: Color,
    delta: String?,
    modifier: Modifier = Modifier,
    alignEnd: Boolean,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(highlight.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
            Text(
                text = value?.takeIf { it.isNotBlank() } ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
                // Bulleted multi-lens / multi-line values (e.g. a Quad rear
                // camera breakdown) must never be clipped - every lens has
                // to stay visible, however many lines that takes.
                maxLines = if (value?.contains('\n') == true) Int.MAX_VALUE else 4,
            )
            if (delta != null && highlight != Color.Transparent) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = delta,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
