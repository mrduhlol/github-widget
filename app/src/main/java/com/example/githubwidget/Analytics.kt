package com.example.githubwidget

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Central analytics for V1.8 — every number on the Activity screen,
 * achievements and the share card comes from here. No UI code inside.
 * All calculations run on plain date/count data (fast, reusable).
 */
data class AnalyticsData(
    val total: Int,
    val thisWeek: Int,
    val thisMonth: Int,
    val activeDays: Int,
    val avgPerActiveDay: Double,
    val avgPerDay: Double,
    /** Totals per weekday, index 0 = Sunday .. 6 = Saturday. */
    val weekdayTotals: IntArray,
    /** Most active weekday index, or -1 when there is no activity. */
    val mostActiveWeekday: Int,
    /** e.g. "Mar 2026", or "" when fewer than two months of data exist. */
    val bestMonthLabel: String,
    val currentStreak: Int,
    val longestStreak: Int,
    val last30Active: Int,
    val last30Total: Int,
    /** -1 when the dataset is too short to compare periods. */
    val prev30Total: Int
)

object Analytics {

    private val DAY_FMT = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val MONTH_FMT = SimpleDateFormat("MMM yyyy", Locale.US)
    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun compute(days: List<Day>, total: Int): AnalyticsData {
        data class Dated(val time: Long, val count: Int, val cal: Calendar)

        val dated = days.mapNotNull { d ->
            try {
                val parsed = DAY_FMT.parse(d.date) ?: return@mapNotNull null
                val cal = Calendar.getInstance()
                cal.time = parsed
                Dated(parsed.time, d.count, cal)
            } catch (_: Exception) {
                null
            }
        }.sortedBy { it.time }

        if (dated.isEmpty()) {
            return Analytics(total, 0, 0, 0, 0.0, 0.0, IntArray(7), -1, "", 0, 0, 0, 0, -1)
        }

        val latest = dated.last().time
        fun since(daysBack: Long): Long = latest - daysBack * DAY_MS

        val latestCal = dated.last().cal
        val latestYear = latestCal.get(Calendar.YEAR)
        val latestMonth = latestCal.get(Calendar.MONTH)

        var thisWeek = 0
        var thisMonth = 0
        var active = 0
        val weekdays = IntArray(7)
        val months = HashMap<Int, Int>()
        var last30Total = 0
        var last30Active = 0
        var prev30Total = 0
        var prevDays = 0

        for (e in dated) {
            val c = e.count
            if (c > 0) active++
            val dow = e.cal.get(Calendar.DAY_OF_WEEK) - 1
            weekdays[dow] += c
            val monthKey = e.cal.get(Calendar.YEAR) * 12 + e.cal.get(Calendar.MONTH)
            months[monthKey] = (months[monthKey] ?: 0) + c
            if (e.time >= since(6)) thisWeek += c
            if (e.cal.get(Calendar.YEAR) == latestYear && e.cal.get(Calendar.MONTH) == latestMonth) {
                thisMonth += c
            }
            if (e.time >= since(29)) {
                last30Total += c
                if (c > 0) last30Active++
            } else if (e.time >= since(59)) {
                prev30Total += c
                prevDays++
            }
        }

        var bestDow = -1
        var bestDowCount = 0
        weekdays.forEachIndexed { i, v ->
            if (v > bestDowCount) {
                bestDowCount = v
                bestDow = i
            }
        }
        if (active < 7) bestDow = -1

        var bestMonthLabel = ""
        if (months.size >= 2) {
            val best = months.maxByOrNull { it.value }
            if (best != null && best.value > 0) {
                val cal = Calendar.getInstance()
                cal.set(Calendar.YEAR, best.key / 12)
                cal.set(Calendar.MONTH, best.key % 12)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                bestMonthLabel = MONTH_FMT.format(cal.time)
            }
        }

        val streaks = Stats.compute(days)
        val avgActive = if (active > 0) total.toDouble() / active else 0.0
        val avgDay = if (dated.isNotEmpty()) total.toDouble() / dated.size else 0.0
        val hasPrev = (latest - dated.first().time) >= 59 * DAY_MS && prevDays > 0

        return Analytics(
            total = total,
            thisWeek = thisWeek,
            thisMonth = thisMonth,
            activeDays = active,
            avgPerActiveDay = avgActive,
            avgPerDay = avgDay,
            weekdayTotals = weekdays,
            mostActiveWeekday = bestDow,
            bestMonthLabel = bestMonthLabel,
            currentStreak = streaks.currentStreak,
            longestStreak = streaks.longestStreak,
            last30Active = last30Active,
            last30Total = last30Total,
            prev30Total = if (hasPrev) prev30Total else -1
        )
    }

    fun weekdayName(index: Int): String = when (index) {
        0 -> "Sunday"
        1 -> "Monday"
        2 -> "Tuesday"
        3 -> "Wednesday"
        4 -> "Thursday"
        5 -> "Friday"
        else -> "Saturday"
    }

    /**
     * Short, data-backed insight lines. Empty when there is too little
     * history — never invents claims.
     */
    fun insights(a: AnalyticsData): List<String> {
        val out = ArrayList<String>()
        if (a.activeDays < 5) {
            out.add("Not enough activity yet for detailed insights.")
            return out
        }
        if (a.currentStreak >= 2) {
            out.add("You're currently on a ${a.currentStreak}-day streak.")
        }
        if (a.mostActiveWeekday >= 0) {
            out.add("${weekdayName(a.mostActiveWeekday)} is your most active day.")
        }
        if (a.last30Active > 0) {
            out.add("You've contributed on ${a.last30Active} of the last 30 days.")
        }
        if (a.bestMonthLabel.isNotEmpty()) {
            out.add("Your most productive month was ${a.bestMonthLabel}.")
        }
        if (a.prev30Total >= 0) {
            val diff = a.last30Total - a.prev30Total
            when {
                diff > 0 -> out.add("Activity is up by $diff contributions vs the previous 30 days.")
                diff < 0 -> out.add("Activity is down by ${-diff} contributions vs the previous 30 days.")
                else -> out.add("Activity is steady vs the previous 30 days.")
            }
        }
        if (a.longestStreak >= 7) {
            out.add("Your longest streak is ${a.longestStreak} days.")
        }
        return out
    }
}

data class Achievement(
    val id: String,
    val title: String,
    val desc: String,
    val unlocked: Boolean
)

object Achievements {

    val MILESTONES = listOf(7, 14, 30, 50, 100, 365)

    /** Highest streak milestone reached, or 0. */
    fun milestoneReached(longestStreak: Int): Int =
        MILESTONES.filter { longestStreak >= it }.maxOrNull() ?: 0

    /** Next streak milestone to aim for, or -1 when all are reached. */
    fun nextMilestone(longestStreak: Int): Int =
        MILESTONES.firstOrNull { longestStreak < it } ?: -1

    fun evaluate(total: Int, a: AnalyticsData): List<Achievement> {
        val hasAny = a.activeDays > 0
        return listOf(
            Achievement(
                "first", "First contribution",
                "Make your first contribution.", hasAny
            ),
            Achievement(
                "streak7", "7-day streak",
                "Contribute 7 days in a row.", a.longestStreak >= 7
            ),
            Achievement(
                "streak30", "30-day streak",
                "Contribute 30 days in a row.", a.longestStreak >= 30
            ),
            Achievement(
                "total100", "100 contributions",
                "Reach 100 contributions.", total >= 100
            ),
            Achievement(
                "total500", "500 contributions",
                "Reach 500 contributions.", total >= 500
            ),
            Achievement(
                "total1000", "1,000 contributions",
                "Reach 1,000 contributions.", total >= 1000
            ),
            Achievement(
                "active100", "100 active days",
                "Contribute on 100 different days.", a.activeDays >= 100
            ),
            Achievement(
                "consistent", "Consistent month",
                "Active on 20+ of the last 30 days.", a.last30Active >= 20
            )
        )
    }
}
