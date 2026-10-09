package com.example.githubwidget.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Everything the Stats screen and the widget show, computed offline from the day list. */
data class Stats(
    val total: Int,
    val today: Int,
    val thisWeek: Int,
    val thisMonth: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val activeDays: Int,
    val trackedDays: Int,
    val bestDay: Day?,
    /** Totals per weekday, Monday first (index 0 = Monday .. 6 = Sunday). */
    val weekdayTotals: IntArray,
    /** Totals for the last 12 calendar months, oldest first. */
    val months: List<Pair<YearMonth, Int>>,
    val last30: Int,
    /** null when there isn't a full previous 30-day window to compare against. */
    val previous30: Int?,
) {
    val busiestWeekday: DayOfWeek?
        get() = if (activeDays < 7) null
        else DayOfWeek.of(weekdayTotals.indices.maxBy { weekdayTotals[it] } + 1)

    val averagePerActiveDay: Double
        get() = if (activeDays == 0) 0.0 else total.toDouble() / activeDays

    companion object {
        fun from(c: Contributions): Stats {
            val days = c.days
            val today = days.lastOrNull()?.date ?: LocalDate.now()

            var longest = 0
            var run = 0
            var active = 0
            var best: Day? = null
            val weekdays = IntArray(7)
            val monthTotals = HashMap<YearMonth, Int>()
            for (d in days) {
                if (d.count > 0) {
                    run++
                    active++
                    if (best == null || d.count > best.count) best = d
                } else {
                    run = 0
                }
                longest = maxOf(longest, run)
                weekdays[d.date.dayOfWeek.value - 1] += d.count
                val ym = YearMonth.from(d.date)
                monthTotals[ym] = (monthTotals[ym] ?: 0) + d.count
            }

            // A quiet "today" doesn't break yesterday's streak — the day isn't over yet.
            var current = 0
            var i = days.lastIndex
            if (i >= 0 && days[i].count == 0) i--
            while (i >= 0 && days[i].count > 0) {
                current++
                i--
            }

            val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
            val thisMonth = YearMonth.from(today)
            val last30Start = today.minusDays(29)
            val prev30Start = today.minusDays(59)

            val months = (11 downTo 0).map { back ->
                val ym = thisMonth.minusMonths(back.toLong())
                ym to (monthTotals[ym] ?: 0)
            }

            return Stats(
                total = c.totalLastYear,
                today = days.lastOrNull()?.takeIf { it.date == today }?.count ?: 0,
                thisWeek = days.filter { !it.date.isBefore(weekStart) }.sumOf { it.count },
                thisMonth = monthTotals[thisMonth] ?: 0,
                currentStreak = current,
                longestStreak = longest,
                activeDays = active,
                trackedDays = days.size,
                bestDay = best,
                weekdayTotals = weekdays,
                months = months,
                last30 = days.filter { !it.date.isBefore(last30Start) }.sumOf { it.count },
                previous30 = if (days.isNotEmpty() && !days.first().date.isAfter(prev30Start)) {
                    days.filter { !it.date.isBefore(prev30Start) && it.date.isBefore(last30Start) }
                        .sumOf { it.count }
                } else null,
            )
        }
    }
}
