package com.example.githubwidget.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import com.example.githubwidget.data.UserData
import com.example.githubwidget.design.NumberFont
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.min

internal object StreakWidgetPainter {
    const val SURFACE = 0xFF0D1117.toInt()
    const val BORDER = 0xFF30363D.toInt()
    const val ACTIVE = 0xFF3FB950.toInt()
    const val MUTED = 0xFF8B949E.toInt()

    enum class State(val label: String, val description: String) {
        ACTIVE("ACTIVE", "Contribution recorded today"),
        PENDING("PENDING", "No contribution today yet"),
        BROKEN("BROKEN", "The previous streak ended"),
        ZERO("NO STREAK", "No current contribution streak"),
        CHECKING("CHECKING", "Checking the contribution calendar"),
        FAILED("UPDATE FAILED", "Couldn't refresh the contribution calendar"),
        SETUP("SET UP", "Connect a GitHub profile"),
    }

    data class Snapshot(val state: State, val count: Int? = null, val lowerBound: Boolean = false) {
        fun description(): String {
            val days = count?.let { "${if (lowerBound) "At least " else ""}$it ${if (it == 1) "day" else "days"}." }
            return listOfNotNull("GitHub streak.", days, state.description + ".").joinToString(" ")
        }
    }

    /** The current run may end yesterday while today is still open on GitHub's UTC calendar. */
    fun snapshot(
        data: UserData?,
        placeholder: WidgetRenderer.Placeholder,
        today: LocalDate = LocalDate.now(ZoneOffset.UTC),
    ): Snapshot {
        val failed = placeholder == WidgetRenderer.Placeholder.FAILED
        val unknown = Snapshot(if (failed) State.FAILED else State.CHECKING)
        if (data == null) return if (placeholder == WidgetRenderer.Placeholder.SIGNED_OUT) Snapshot(State.SETUP) else unknown
        val counts = data.contributions.days.associate { it.date to it.count }
        val yesterday = today.minusDays(1)
        val todayCount = counts[today]
        val yesterdayCount = counts[yesterday]
        val active = (todayCount ?: 0) > 0
        if (!active && (yesterdayCount ?: 0) <= 0) {
            // Missing days or a cache from before rollover cannot establish a broken streak.
            val checkedDay = Instant.ofEpochMilli(data.fetchedAt).atOffset(ZoneOffset.UTC).toLocalDate()
            if (failed || todayCount == null || yesterdayCount == null || checkedDay.isBefore(today)) return unknown
            val hadStreak = counts.any { (date, count) -> date.isBefore(yesterday) && count > 0 }
            return Snapshot(if (hadStreak) State.BROKEN else State.ZERO, 0)
        }
        var day = if (active) today else yesterday
        var count = 0
        while ((counts[day] ?: 0) > 0) {
            count++
            day = day.minusDays(1)
        }
        val state = if (failed) State.FAILED else if (active) State.ACTIVE else State.PENDING
        return Snapshot(state, count, lowerBound = counts[day] == null)
    }

    fun draw(canvas: Canvas, area: RectF, scale: Float, context: Context, font: NumberFont,
             snapshot: Snapshot, widthDp: Float, heightDp: Float) {
        if (area.isEmpty) return
        val clip = canvas.save()
        canvas.clipRect(area)
        fun dp(value: Float) = scale * value
        val accent = if (snapshot.state == State.ACTIVE) ACTIVE else MUTED
        val digits = snapshot.count?.let {
            NumberFormat.getIntegerInstance().format(it) + if (snapshot.lowerBound) "+" else ""
        } ?: "—"
        val face = NumberFont.typeface(context, if (font == NumberFont.DOT_MATRIX) NumberFont.SYSTEM else font, 700)
        val number = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; color = Color.WHITE }
        val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; color = accent }
        val unit = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT; color = 0xFFAAB3BD.toInt() }

        fun centerText(text: String, paint: TextPaint, x: Float, y: Float, size: Float, width: Float) {
            if (width <= 0f) return
            paint.textSize = dp(size)
            val measured = paint.measureText(text)
            if (measured > width) paint.textSize *= width / measured
            val baseline = y - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText(text, x - paint.measureText(text) / 2f, baseline, paint)
        }

        fun mark(x: Float, y: Float, size: Float) =
            ProfileIcons.draw(canvas, "https://github.com", x, y, dp(size), 0xFFF0F6FC.toInt())

        fun days(x: Float, y: Float) {
            if (snapshot.count == null) return
            val word = if (snapshot.count == 1) "DAY" else "DAYS"
            unit.textSize = dp(10f)
            val gap = dp(6f)
            val flame = dp(8f)
            val width = unit.measureText(word)
            val left = x - (flame + gap + width) / 2f
            drawFlame(canvas, left, y - dp(5f), scale, accent)
            centerText(word, unit, left + flame + gap + width / 2f, y, 10f, width)
        }

        when {
            heightDp < 125f -> {
                val x = area.centerX()
                val space = area.height()
                mark(x, area.top + space * .11f, if (heightDp < 72f) 10f else 13f)
                centerText(digits, number, x, area.top + space * .48f, if (heightDp < 72f) 25f else 33f, area.width() - dp(12f))
                val y = area.top + space * .88f
                if (snapshot.count != null && widthDp >= 146f) {
                    days(x - dp(31f), y)
                    centerText(snapshot.state.label, label, x + dp(19f), y, 9f, area.width() * .52f)
                } else {
                    val status = if (snapshot.count != null) {
                        val unitLabel = if (snapshot.count == 1) "DAY" else "DAYS"
                        "$unitLabel · ${snapshot.state.label}"
                    } else snapshot.state.label
                    centerText(status, label, x, y, 8f, area.width())
                }
            }
            widthDp >= 215f && heightDp < 215f -> {
                val y = area.centerY()
                val divider = area.left + area.width() * .34f
                val left = (area.left + divider) / 2f
                val right = (divider + area.right) / 2f
                mark(left, y - dp(7f), 31f)
                centerText("STREAK", unit, left, y + dp(22f), 10f, divider - area.left)
                val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BORDER; strokeWidth = dp(1f) }
                canvas.drawLine(divider, y - dp(34f), divider, y + dp(34f), rule)
                centerText(digits, number, right, y - dp(17f), min(58f, heightDp * .46f), area.right - divider - dp(14f))
                days(right, y + dp(23f))
                centerText(snapshot.state.label, label, right, y + dp(42f), 10f, area.right - divider - dp(12f))
            }
            else -> {
                val x = area.centerX()
                val y = area.centerY()
                val large = heightDp >= 215f
                val spread = if (large) 1.45f else min(1f, area.height() / dp(124f))
                mark(x, y - dp(46f * spread), if (large) 28f else 19f)
                if (large) centerText("GITHUB STREAK", unit, x, y - dp(28f * spread), 11f, area.width())
                centerText(digits, number, x, y + dp(if (large) 9f else 1f), if (large) 78f else 52f, area.width() - dp(10f))
                days(x, y + dp(32f * spread))
                centerText(snapshot.state.label, label, x, y + dp(49f * spread), 10f, area.width())
            }
        }
        canvas.restoreToCount(clip)
    }

    private fun drawFlame(canvas: Canvas, x: Float, y: Float, scale: Float, color: Int) {
        val flame = Path().apply {
            moveTo(x + 4f * scale, y)
            cubicTo(x + 4f * scale, y + 3f * scale, x, y + 4f * scale, x, y + 7f * scale)
            cubicTo(x, y + 12f * scale, x + 8f * scale, y + 12f * scale, x + 8f * scale, y + 7f * scale)
            cubicTo(x + 8f * scale, y + 4f * scale, x + 6f * scale, y + 2f * scale, x + 4f * scale, y)
            close()
        }
        canvas.drawPath(flame, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }
}
