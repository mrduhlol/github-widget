package com.example.githubwidget.widget

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.animation.PathInterpolator
import com.example.githubwidget.data.Day
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Draws the contribution grid: 7 rows (days) by N columns (weeks), newest week
 * on the right. Shared by the widget, the in-app preview and the share image.
 */
object GraphPainter {

    /** Gap between cells as a fraction of the cell size. */
    const val GAP = 0.22f
    private const val COLUMN_SWEEP_MS = 320L
    private const val ROW_STAGGER_MS = 6L
    private const val POP_PEAK_MS = 126L
    private const val POP_DURATION_MS = 230L
    private const val POP_START_SCALE = 0.68f
    private const val POP_PEAK_SCALE = 1.15f
    internal const val REPLAY_DURATION_MS = COLUMN_SWEEP_MS + 6 * ROW_STAGGER_MS + POP_DURATION_MS
    private val popEaseOut by lazy { PathInterpolator(0.23f, 1f, 0.32f, 1f) }

    data class Spec(
        val levels: IntArray,
        val radiusFraction: Float,
        val weekStartsMonday: Boolean,
        /** Fixed number of weeks, or null to fit as many as the space allows. */
        val weeks: Int?,
        /** Upper bound for the cell size in px when fitting. */
        val maxCellPx: Float,
        val monthLabelPaint: Paint? = null,
        val monthLabelHeightPx: Float = 0f,
        val replayElapsedMs: Long? = null,
    )

    /** First day of the week containing [date]. */
    fun weekStart(date: LocalDate, monday: Boolean): LocalDate {
        val first = if (monday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
        val offset = (date.dayOfWeek.value - first.value + 7) % 7
        return date.minusDays(offset.toLong())
    }

    /** Cell size that fits 7 rows into [height]. */
    fun cellForHeight(height: Float) = height / (7 + 6 * GAP)

    /** How many weeks of cells of size [cell] fit across [width]. */
    fun weeksForWidth(width: Float, cell: Float) =
        ((width + cell * GAP) / (cell * (1 + GAP))).toInt().coerceIn(1, 53)

    /** Draws into [area]; returns the rect the grid actually occupies. */
    fun draw(canvas: Canvas, area: RectF, days: List<Day>, spec: Spec): RectF {
        val labelH = if (spec.monthLabelPaint != null) spec.monthLabelHeightPx else 0f
        val gridH = area.height() - labelH
        if (area.width() <= 0f || gridH <= 0f) return RectF(area.left, area.top, area.left, area.top)
        var cell = cellForHeight(gridH)
        val weeks: Int
        if (spec.weeks != null) {
            weeks = spec.weeks.coerceIn(1, 53)
            cell = minOf(cell, area.width() / (weeks + (weeks - 1) * GAP))
        } else {
            cell = minOf(cell, spec.maxCellPx)
            weeks = weeksForWidth(area.width(), cell)
            // Stretch slightly so the grid fills the width edge to edge.
            cell = minOf(cellForHeight(gridH), area.width() / (weeks + (weeks - 1) * GAP))
        }
        val gap = cell * GAP
        val gridW = weeks * cell + (weeks - 1) * gap
        val totalH = labelH + 7 * cell + 6 * gap
        val left = area.left + (area.width() - gridW) / 2f
        val top = area.top + (area.height() - totalH) / 2f
        val gridTop = top + labelH

        val byDate = HashMap<LocalDate, Day>(days.size * 2)
        days.forEach { byDate[it.date] = it }
        val today = days.lastOrNull()?.date ?: LocalDate.now()
        val firstWeek = weekStart(today, spec.weekStartsMonday).minusWeeks((weeks - 1).toLong())
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = cell * spec.radiusFraction
        val rect = RectF()

        var lastLabelEnd = -Float.MAX_VALUE
        var prevMonth = -1
        for (col in 0 until weeks) {
            val colStart = firstWeek.plusWeeks(col.toLong())
            val x = left + col * (cell + gap)
            val columnDelay = col * COLUMN_SWEEP_MS / (weeks - 1).coerceAtLeast(1)

            spec.monthLabelPaint?.let { p ->
                // Like GitHub: label the first week that starts in a new month.
                val month = colStart.monthValue
                if (month != prevMonth) {
                    if (prevMonth != -1 || colStart.dayOfMonth <= 7) {
                        val label = colStart.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        val w = p.measureText(label)
                        if (x > lastLabelEnd + cell && x + w <= left + gridW + cell) {
                            canvas.drawText(label, x, top + labelH * 0.66f, p)
                            lastLabelEnd = x + w
                        }
                    }
                    prevMonth = month
                }
            }

            for (row in 0..6) {
                val date = colStart.plusDays(row.toLong())
                if (date.isAfter(today)) break
                val level = (byDate[date]?.level ?: 0).coerceIn(0, 4)
                val y = gridTop + row * (cell + gap)
                rect.set(x, y, x + cell, y + cell)
                val age = spec.replayElapsedMs?.minus(columnDelay + row * ROW_STAGGER_MS)
                if (level == 0 || age == null || age >= POP_DURATION_MS) {
                    fill.color = spec.levels[level]
                    canvas.drawRoundRect(rect, radius, radius, fill)
                } else {
                    fill.color = spec.levels[0]
                    canvas.drawRoundRect(rect, radius, radius, fill)
                    if (age > 0) {
                        val entrance = popEaseOut.getInterpolation((age.toFloat() / POP_PEAK_MS).coerceAtMost(1f))
                        val scale = if (age <= POP_PEAK_MS) {
                            POP_START_SCALE + (POP_PEAK_SCALE - POP_START_SCALE) * entrance
                        } else {
                            val settle = popEaseOut.getInterpolation((age - POP_PEAK_MS).toFloat() / (POP_DURATION_MS - POP_PEAK_MS))
                            1f + (POP_PEAK_SCALE - 1f) * (1f - settle)
                        }
                        val inset = cell * (1f - scale) / 2
                        rect.inset(inset, inset)
                        fill.color = spec.levels[level]
                        fill.alpha = (fill.alpha * entrance).toInt()
                        canvas.drawRoundRect(rect, radius * scale, radius * scale, fill)
                    }
                }
            }
        }
        return RectF(left, top, left + gridW, top + totalH)
    }
}
