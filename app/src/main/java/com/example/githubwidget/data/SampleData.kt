package com.example.githubwidget.data

import java.time.LocalDate
import kotlin.random.Random

/** A believable, deterministic year of activity for previews and illustrations. */
object SampleData {
    val contributions: Contributions by lazy {
        val rnd = Random(7)
        val today = LocalDate.now()
        val days = (370 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            val weekend = date.dayOfWeek.value >= 6
            val season = 0.55 + 0.45 * kotlin.math.sin(back / 40.0)
            val roll = rnd.nextDouble() * season
            val count = when {
                weekend && roll < 0.35 -> 0
                roll < 0.12 -> 0
                roll < 0.35 -> rnd.nextInt(1, 3)
                roll < 0.6 -> rnd.nextInt(3, 7)
                roll < 0.8 -> rnd.nextInt(7, 12)
                else -> rnd.nextInt(12, 20)
            }
            val level = when {
                count == 0 -> 0
                count < 3 -> 1
                count < 7 -> 2
                count < 12 -> 3
                else -> 4
            }
            Day(date, count, level)
        }
        Contributions("octocat", days.sumOf { it.count }, days)
    }

    val userData: UserData by lazy {
        UserData(contributions, Profile("octocat", "The Octocat", ""), System.currentTimeMillis())
    }
}
