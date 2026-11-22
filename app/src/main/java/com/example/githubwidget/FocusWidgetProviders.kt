package com.example.githubwidget

/** Distinct picker entries; lifecycle and refresh are shared with the original provider. */
class StreakWidgetProvider : ContributionWidgetProvider()
class WeeklyGoalWidgetProvider : ContributionWidgetProvider()
class WeeklyGoalMeterWidgetProvider : ContributionWidgetProvider()
class WeeklyGoalWeekWidgetProvider : ContributionWidgetProvider()
class WeeklyGoalAscentWidgetProvider : ContributionWidgetProvider()
class ProfileWidgetProvider : ContributionWidgetProvider()
class PortraitProfileWidgetProvider : ContributionWidgetProvider()
open class ReviewInboxWidgetProvider : ContributionWidgetProvider()
class ReviewNextWidgetProvider : ReviewInboxWidgetProvider()
class ReviewQueueWidgetProvider : ReviewInboxWidgetProvider()
