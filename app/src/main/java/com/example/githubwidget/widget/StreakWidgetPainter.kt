package com.example.githubwidget.widget

import com.example.githubwidget.data.UserData
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
}
