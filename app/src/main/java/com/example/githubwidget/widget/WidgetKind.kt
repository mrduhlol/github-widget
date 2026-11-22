package com.example.githubwidget.widget

import android.content.ComponentName
import android.content.Context
import com.example.githubwidget.*
import com.example.githubwidget.design.WidgetLayout

internal enum class WidgetKind(val provider: Class<*>, val layout: WidgetLayout?) {
    CONTRIBUTIONS(ContributionWidgetProvider::class.java, null),
    STREAK(StreakWidgetProvider::class.java, WidgetLayout.STREAK),
    WEEKLY_GOAL(WeeklyGoalWidgetProvider::class.java, WidgetLayout.WEEKLY_GOAL),
    PROFILE(ProfileWidgetProvider::class.java, WidgetLayout.PROFILE),
    PORTRAIT_PROFILE(PortraitProfileWidgetProvider::class.java, WidgetLayout.PROFILE),
    PR_INBOX(ReviewInboxWidgetProvider::class.java, WidgetLayout.PR_INBOX),
    WEEKLY_GOAL_METER(WeeklyGoalMeterWidgetProvider::class.java, WidgetLayout.WEEKLY_GOAL_METER),
    WEEKLY_GOAL_WEEK(WeeklyGoalWeekWidgetProvider::class.java, WidgetLayout.WEEKLY_GOAL_WEEK),
    WEEKLY_GOAL_ASCENT(WeeklyGoalAscentWidgetProvider::class.java, WidgetLayout.WEEKLY_GOAL_ASCENT),
    REVIEW_NEXT(ReviewNextWidgetProvider::class.java, WidgetLayout.REVIEW_NEXT),
    REVIEW_QUEUE(ReviewQueueWidgetProvider::class.java, WidgetLayout.REVIEW_QUEUE);

    fun component(context: Context) = ComponentName(context, provider)

    fun resolvedLayout(selected: WidgetLayout, contributions: WidgetLayout = WidgetLayout.CLASSIC): WidgetLayout = layout ?: when (selected) {
        WidgetLayout.CLASSIC, WidgetLayout.GRAPH, WidgetLayout.NUMBERS -> selected
        else -> contributions.takeIf { it in listOf(WidgetLayout.CLASSIC, WidgetLayout.GRAPH, WidgetLayout.NUMBERS) } ?: WidgetLayout.CLASSIC
    }

    companion object {
        fun forLayout(layout: WidgetLayout, portrait: Boolean = false) = when (layout) {
            WidgetLayout.STREAK -> STREAK
            WidgetLayout.WEEKLY_GOAL -> WEEKLY_GOAL
            WidgetLayout.WEEKLY_GOAL_METER -> WEEKLY_GOAL_METER
            WidgetLayout.WEEKLY_GOAL_WEEK -> WEEKLY_GOAL_WEEK
            WidgetLayout.WEEKLY_GOAL_ASCENT -> WEEKLY_GOAL_ASCENT
            WidgetLayout.PR_INBOX -> PR_INBOX
            WidgetLayout.REVIEW_NEXT -> REVIEW_NEXT
            WidgetLayout.REVIEW_QUEUE -> REVIEW_QUEUE
            WidgetLayout.PROFILE -> if (portrait) PORTRAIT_PROFILE else PROFILE
            else -> CONTRIBUTIONS
        }
    }
}
