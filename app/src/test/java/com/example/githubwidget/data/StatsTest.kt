package com.example.githubwidget.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StatsTest {

    @Test
    fun staleCacheDoesNotFabricateTodayOrCurrentStreak() {
        val today = LocalDate.of(2026, 11, 20)
        // Last cached activity was 4 days ago.
        val days = listOf(
            Day(today.minusDays(6), 2, 1),
            Day(today.minusDays(5), 3, 2),
            Day(today.minusDays(4), 5, 3),
        )
        val stats = Stats.from(Contributions("octocat", 10, days), today = today)
        assertEquals(0, stats.today)
        assertEquals(0, stats.currentStreak)
        assertEquals(3, stats.longestStreak)
    }

    @Test
    fun missingDatesBreakCurrentAndLongestStreaks() {
        val today = LocalDate.of(2026, 11, 20)
        // Gap on today - 2 (omitted from list rather than count = 0).
        val days = listOf(
            Day(today.minusDays(4), 1, 1),
            Day(today.minusDays(3), 2, 1),
            Day(today.minusDays(1), 4, 2),
            Day(today, 3, 2),
        )
        val stats = Stats.from(Contributions("octocat", 10, days), today = today)
        assertEquals(2, stats.currentStreak)
        assertEquals(2, stats.longestStreak)
        assertEquals(3, stats.today)
    }

    @Test
    fun quietTodayPreservesYesterdaysStreak() {
        val today = LocalDate.of(2026, 11, 20)
        val days = listOf(
            Day(today.minusDays(3), 1, 1),
            Day(today.minusDays(2), 2, 1),
            Day(today.minusDays(1), 4, 2),
            Day(today, 0, 0),
        )
        val stats = Stats.from(Contributions("octocat", 7, days), today = today)
        assertEquals(0, stats.today)
        assertEquals(3, stats.currentStreak)
        assertEquals(3, stats.longestStreak)
    }
}
