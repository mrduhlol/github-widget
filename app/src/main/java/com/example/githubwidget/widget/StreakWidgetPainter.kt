package com.example.githubwidget.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import androidx.core.graphics.ColorUtils
import com.example.githubwidget.data.UserData
import com.example.githubwidget.design.NumberFont
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

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
        val compact = heightDp < 100f
        val wide = widthDp >= 230f && heightDp < 210f
        val tall = heightDp >= 220f
        val accent = if (snapshot.state == State.ACTIVE) ACTIVE else MUTED
        val ink = Paint(Paint.ANTI_ALIAS_FLAG)
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT }
        fun caption(value: String, x: Float, centerY: Float, width: Float, size: Float, color: Int) {
            text.textSize = dp(size)
            text.color = color
            canvas.drawText(WidgetRenderer.ellipsize(value, text, width), x,
                centerY - (text.ascent() + text.descent()) / 2f, text)
        }

        val headerY = area.top + dp(if (compact) 5f else 8f)
        val mark = if (compact) 10f else 15f
        val headerInset = dp(mark + 7f)
        ProfileIcons.draw(canvas, "https://github.com", area.left + dp(mark / 2f), headerY, dp(mark), MUTED)
        caption(if (compact) "Streak" else "GitHub streak", area.left + headerInset, headerY,
            area.width() - headerInset - dp(28f), if (compact) 9f else 12f, MUTED)

        val footerY = area.bottom - dp(if (compact) 4f else 8f)
        val dividerY = footerY - dp(if (compact) 8f else 14f)
        val numberTop = headerY + dp(if (compact) 6f else 15f)
        val numberBottom = dividerY - dp(if (compact) 2f else 7f)
        val numberHeight = numberBottom - numberTop
        val digits = snapshot.count?.let {
            NumberFormat.getIntegerInstance().format(it) + if (snapshot.lowerBound) "+" else ""
        } ?: "—"
        if (numberHeight > 0f) {
            val preferredSize = when {
                compact -> 35f
                wide -> 82f
                tall && widthDp >= 230f -> 118f
                tall -> 90f
                else -> 67f
            }
            val numberWidth = area.width() - dp(if (compact) 26f else if (snapshot.count != null) 43f else 6f)
            val number = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = NumberFont.typeface(context, if (font == NumberFont.DOT_MATRIX) NumberFont.SYSTEM else font, 700)
                color = Color.WHITE
                textSize = dp(preferredSize)
            }
            val bounds = Rect()
            number.getTextBounds(digits, 0, digits.length, bounds)
            if (bounds.width() > 0 && bounds.height() > 0 && numberWidth > 0f) {
                number.textSize *= minOf(1f, numberWidth / bounds.width(), numberHeight / bounds.height())
                number.getTextBounds(digits, 0, digits.length, bounds)
                val baseline = (numberTop + numberBottom) / 2f - bounds.exactCenterY()
                val x = area.left + dp(1f)
                canvas.drawText(digits, x - bounds.left, baseline, number)

                if (snapshot.count != null && !compact) {
                    val unitX = x + bounds.width() + dp(10f)
                    caption(if (snapshot.count == 1) "day" else "days", unitX,
                        baseline + bounds.bottom - dp(6f), area.right - unitX, 13f, MUTED)
                }
            }
        }

        if (!compact) {
            ink.color = BORDER
            ink.strokeWidth = dp(.75f)
            canvas.drawLine(area.left, dividerY, area.right, dividerY, ink)
        }
        val status = when (snapshot.state) {
            State.ACTIVE -> "Active today"
            State.PENDING -> if (compact) "Pending" else "No activity today"
            State.BROKEN -> "Streak ended"
            State.ZERO -> "No streak yet"
            State.CHECKING -> "Checking…"
            State.FAILED -> "Update failed"
            State.SETUP -> "Set up GitHub"
        }
        ink.color = accent
        val dot = dp(if (compact) 2f else 2.5f)
        canvas.drawCircle(area.left + dot, footerY, dot, ink)
        val statusInset = dp(if (compact) 9f else 12f)
        val footer = if (compact && snapshot.count != null) {
            val unit = if (snapshot.count == 1) "day" else "days"
            "$unit · $status"
        } else status
        caption(footer, area.left + statusInset, footerY, area.width() - statusInset,
            if (compact) 8.5f else 11f, if (snapshot.state == State.ACTIVE) ACTIVE else MUTED)
        canvas.restoreToCount(clip)
    }

    fun drawSurface(canvas: Canvas, area: RectF, radius: Float, scale: Float, opacity: Int) {
        val alpha = opacity * 255 / 100
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(SURFACE, alpha)
        }
        canvas.drawRoundRect(area, radius, radius, ink)
        if (opacity < 30) return
        val stroke = scale
        val edge = RectF(area).apply { inset(stroke / 2f, stroke / 2f) }
        ink.color = ColorUtils.setAlphaComponent(BORDER, alpha)
        ink.style = Paint.Style.STROKE
        ink.strokeWidth = stroke
        canvas.drawRoundRect(edge, radius - stroke / 2f, radius - stroke / 2f, ink)
    }
}
