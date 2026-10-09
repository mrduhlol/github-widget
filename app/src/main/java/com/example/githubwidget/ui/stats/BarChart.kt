package com.example.githubwidget.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.githubwidget.ui.theme.AppTheme
import java.text.NumberFormat

/**
 * Simple vertical bars with a label under each. The [highlight] bar is drawn
 * in the accent color with its value on top; the rest are muted.
 */
@Composable
internal fun BarChart(
    values: List<Int>,
    labels: List<String>,
    highlight: Int?,
    description: String,
    modifier: Modifier = Modifier,
    barAreaHeight: Dp = 120.dp,
    spacing: Dp = 8.dp,
) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val numbers = remember { NumberFormat.getIntegerInstance() }
    val accent = MaterialTheme.colorScheme.primary
    val muted = accent.copy(alpha = if (AppTheme.colors.isDark) 0.22f else 0.18f)
    val grow = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { grow.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }

    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
            values.forEachIndexed { i, v ->
                val isTop = i == highlight
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.height(22.dp), contentAlignment = Alignment.BottomCenter) {
                        if (isTop) {
                            Text(
                                numbers.format(v),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = AppTheme.colors.textPrimary,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.wrapContentWidth(unbounded = true),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth().height(barAreaHeight), contentAlignment = Alignment.BottomCenter) {
                        val h = maxOf(4.dp, barAreaHeight * (v.toFloat() / max) * grow.value)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(h)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                                .background(if (isTop) accent else muted),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
            labels.forEachIndexed { i, label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f).wrapContentWidth(unbounded = true),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, letterSpacing = 0.sp),
                    fontWeight = if (i == highlight) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (i == highlight) AppTheme.colors.textPrimary else AppTheme.colors.textTertiary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
