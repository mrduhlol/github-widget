package com.example.githubwidget.ui.stats

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.githubwidget.data.Day
import com.example.githubwidget.ui.theme.AppTheme
import com.example.githubwidget.widget.GraphPainter
import kotlinx.coroutines.flow.first
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

private const val WEEKS = 53
private val CELL = 13.dp
private val GAP = 3.dp
private val MONTH_ROW = 18.dp
private val DAY_COLUMN = 30.dp

/**
 * A full year of squares, newest week on the right. Scrolls sideways (it starts
 * scrolled to today) and tapping a square tells you about that day.
 */
@Composable
internal fun YearHeatmap(
    days: List<Day>,
    levels: List<Color>,
    radiusFraction: Float,
    weekStartsMonday: Boolean,
    modifier: Modifier = Modifier,
) {
    val today = days.lastOrNull()?.date ?: LocalDate.now()
    val firstWeek = remember(today, weekStartsMonday) {
        GraphPainter.weekStart(today, weekStartsMonday).minusWeeks((WEEKS - 1).toLong())
    }
    val byDate = remember(days) { days.associateBy { it.date } }
    var selected by remember(days, weekStartsMonday) { mutableStateOf<LocalDate?>(null) }

    val tertiary = AppTheme.colors.textTertiary
    val ring = AppTheme.colors.textPrimary
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = tertiary, fontSize = 11.sp, fontWeight = FontWeight.Medium)

    // Month names above the first column of each month, never crowding each other.
    val monthLayouts = remember(firstWeek, labelStyle) {
        val marks = mutableListOf<Pair<Int, String>>()
        var prev = -1
        for (col in 0 until WEEKS) {
            val month = firstWeek.plusWeeks(col.toLong()).plusDays(6).month
            if (month.value != prev) {
                marks += col to month.getDisplayName(JTextStyle.SHORT, Locale.getDefault()).trimEnd('.')
                prev = month.value
            }
        }
        if (marks.size > 1 && marks[1].first - marks[0].first < 3) marks.removeAt(0)
        marks.map { (col, label) -> col to measurer.measure(label, labelStyle) }
    }
    val dayLayouts = remember(weekStartsMonday, labelStyle) {
        val rows = if (weekStartsMonday) listOf(0, 2, 4) else listOf(1, 3, 5)
        rows.map { row ->
            val dow = GraphPainter.weekStart(today, weekStartsMonday).plusDays(row.toLong()).dayOfWeek
            row to measurer.measure(dow.getDisplayName(JTextStyle.SHORT, Locale.getDefault()).trimEnd('.'), labelStyle)
        }
    }

    val scroll = rememberScrollState()
    LaunchedEffect(Unit) {
        // Jump to the newest week once the grid has been measured.
        val max = snapshotFlow { scroll.maxValue }.first { it != Int.MAX_VALUE }
        scroll.scrollTo(max)
    }

    val gridHeight = MONTH_ROW + CELL * 7 + GAP * 6
    val gridWidth = CELL * WEEKS + GAP * (WEEKS - 1)

    Column(modifier.fillMaxWidth()) {
        SelectedDayLine(selected, byDate)
        Spacer(Modifier.height(14.dp))
        Row {
            Canvas(Modifier.width(DAY_COLUMN).height(gridHeight)) {
                val cell = CELL.toPx()
                val step = cell + GAP.toPx()
                val top = MONTH_ROW.toPx()
                dayLayouts.forEach { (row, layout) ->
                    val y = top + row * step + (cell - layout.size.height) / 2f
                    drawText(layout, topLeft = Offset(0f, y))
                }
            }
            Box(Modifier.weight(1f).horizontalScroll(scroll)) {
                Canvas(
                    Modifier
                        .width(gridWidth)
                        .height(gridHeight)
                        .semantics { contentDescription = "Contribution squares for the last year. Tap a square to see that day." }
                        .pointerInput(firstWeek, today) {
                            detectTapGestures { pos ->
                                val step = (CELL + GAP).toPx()
                                val col = (pos.x / step).toInt()
                                val row = ((pos.y - MONTH_ROW.toPx()) / step).toInt()
                                if (pos.y < MONTH_ROW.toPx() || col !in 0 until WEEKS || row !in 0..6) return@detectTapGestures
                                val date = firstWeek.plusWeeks(col.toLong()).plusDays(row.toLong())
                                if (date.isAfter(today)) return@detectTapGestures
                                selected = if (selected == date) null else date
                            }
                        },
                ) {
                    val cell = CELL.toPx()
                    val step = cell + GAP.toPx()
                    val top = MONTH_ROW.toPx()
                    val radius = CornerRadius(cell * radiusFraction)
                    monthLayouts.forEach { (col, layout) ->
                        drawText(layout, topLeft = Offset(col * step, 0f))
                    }
                    for (col in 0 until WEEKS) {
                        val start = firstWeek.plusWeeks(col.toLong())
                        for (row in 0..6) {
                            val date = start.plusDays(row.toLong())
                            if (date.isAfter(today)) break
                            val level = (byDate[date]?.level ?: 0).coerceIn(0, 4)
                            val origin = Offset(col * step, top + row * step)
                            drawRoundRect(levels[level], origin, Size(cell, cell), radius)
                            if (date == selected) {
                                val w = 1.5.dp.toPx()
                                drawRoundRect(
                                    ring,
                                    origin - Offset(w, w),
                                    Size(cell + 2 * w, cell + 2 * w),
                                    CornerRadius(cell * radiusFraction + w),
                                    style = Stroke(w),
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Legend(levels, radiusFraction, Modifier.align(Alignment.End))
    }
}

@Composable
private fun SelectedDayLine(selected: LocalDate?, byDate: Map<LocalDate, Day>) {
    val formatter = remember { DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()) }
    val numbers = remember { NumberFormat.getIntegerInstance() }
    AnimatedContent(
        targetState = selected,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "selectedDay",
        modifier = Modifier.heightIn(min = 20.dp),
    ) { date ->
        if (date == null) {
            Text(
                "Tap any square to see that day",
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textTertiary,
            )
        } else {
            val count = byDate[date]?.count ?: 0
            val what = when (count) {
                0 -> "No contributions"
                1 -> "1 contribution"
                else -> "${numbers.format(count)} contributions"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    date.format(formatter),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.colors.textPrimary,
                )
                Text(
                    "  ·  $what",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun Legend(levels: List<Color>, radiusFraction: Float, modifier: Modifier = Modifier) {
    Row(
        modifier.clearAndSetSemantics { contentDescription = "Lighter squares mean fewer contributions, darker squares mean more." },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Less", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textTertiary)
        Spacer(Modifier.width(2.dp))
        levels.forEach {
            Box(Modifier.size(11.dp).clip(RoundedCornerShape(11.dp * radiusFraction)).background(it))
        }
        Spacer(Modifier.width(2.dp))
        Text("More", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textTertiary)
    }
}
