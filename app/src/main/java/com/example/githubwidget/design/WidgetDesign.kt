package com.example.githubwidget.design

import org.json.JSONObject

/** What the widget shows besides the graph. */
enum class WidgetLayout(val label: String, val description: String, val isWeeklyGoal: Boolean = false) {
    CLASSIC("Classic", "Name, graph and stats"),
    GRAPH("Graph only", "Just the squares"),
    NUMBERS("Numbers", "Big stats, small graph"),
    STREAK("Streak", "Current streak and today’s status"),
    WEEKLY_GOAL("Weekly Goal · Arc", "A circular gauge for your weekly target", true),
    WEEKLY_GOAL_METER("Weekly Goal · Meter", "A segmented bar that fills toward your target", true),
    WEEKLY_GOAL_WEEK("Weekly Goal · Week", "Daily contribution bars from Monday to Sunday", true),
    WEEKLY_GOAL_ASCENT("Weekly Goal · Ascent", "A stepped climb toward your weekly target", true),
    PROFILE("Profile card", "Your GitHub ID · landscape or portrait"),
    PR_INBOX("PR Inbox", "Open pull requests awaiting your review"),
    REVIEW_NEXT("Up Next", "The next pull request awaiting your review"),
    REVIEW_QUEUE("Review Queue", "A short list of pull requests awaiting your review");

    val isReviewWidget get() = this == PR_INBOX || hasReviewColors
    val hasReviewColors get() = this == REVIEW_NEXT || this == REVIEW_QUEUE
}

/** Card background. AUTO follows the phone's light/dark setting. */
enum class Background(val label: String) {
    AUTO("Auto"),
    DARK("Dark"),
    LIGHT("Light"),
    BLACK("Black"),
}

/** How much history the graph shows. AUTO fits as many weeks as the widget has room for. */
enum class Range(val label: String, val weeks: Int?) {
    AUTO("Fit", null),
    MONTH_3("3 months", 13),
    MONTH_6("6 months", 26),
    YEAR("1 year", 53),
}

enum class CellShape(val label: String, val radiusFraction: Float) {
    SQUARE("Square", 0.12f),
    ROUNDED("Rounded", 0.3f),
    CIRCLE("Circle", 0.5f),
}

enum class Corners(val label: String, val dp: Float) {
    SUBTLE("Subtle", 10f),
    ROUND("Round", 22f),
    EXTRA("Extra", 32f),
}

enum class ProfileTypeface(val label: String) {
    MONO("Monospace"),
    SANS("Sans"),
    CONDENSED("Condensed"),
}

enum class AuroraColor(val label: String, val accent: Int) {
    EMERALD("Emerald", 0xFF55E68A.toInt()),
    BLUE("Blue", 0xFF75B8FF.toInt()),
    VIOLET("Violet", 0xFFC3A0FF.toInt()),
    ROSE("Rose", 0xFFFF91B4.toInt()),
    AMBER("Amber", 0xFFFFCB77.toInt()),
}

enum class ReviewColor(val label: String, val paper: Int, val ink: Int, val surface: Int, val highlight: Int) {
    SAND("Sand", 0xFFE8BB8B.toInt(), 0xFF261C16.toInt(), 0xFF1E201E.toInt(), 0xFF34312A.toInt()),
    JADE("Jade", 0xFFA7D7B8.toInt(), 0xFF152A21.toInt(), 0xFF18251F.toInt(), 0xFF233C2E.toInt()),
    INDIGO("Indigo", 0xFFBAC5F3.toInt(), 0xFF191D38.toInt(), 0xFF202236.toInt(), 0xFF303452.toInt()),
    ROSE("Rose", 0xFFE6B7C7.toInt(), 0xFF351923.toInt(), 0xFF2A1D24.toInt(), 0xFF452D3A.toInt()),
}

enum class ProfileCount(val label: String) {
    FOLLOWERS("Followers"),
    FOLLOWING("Following"),
    REPOS("Repos"),
}

/**
 * Shared appearance settings. Dedicated widget providers retain their own layout
 * while using the same connected profile, colors and font as the in-app preview.
 */
data class WidgetDesign(
    val paletteId: String = Palettes.DEFAULT_ID,
    /** Base color used when [paletteId] is [Palettes.CUSTOM_ID]. */
    val customColor: Int = 0xFF39D353.toInt(),
    val layout: WidgetLayout = WidgetLayout.CLASSIC,
    val contributionLayout: WidgetLayout = WidgetLayout.CLASSIC,
    val background: Background = Background.AUTO,
    /** 0 = fully see-through, 100 = solid. */
    val opacity: Int = 100,
    val corners: Corners = Corners.ROUND,
    val range: Range = Range.AUTO,
    val cellShape: CellShape = CellShape.ROUNDED,
    val showName: Boolean = true,
    val showAvatar: Boolean = true,
    val showTotal: Boolean = true,
    val showStreak: Boolean = true,
    val showToday: Boolean = true,
    val showMonths: Boolean = true,
    val weekStartsMonday: Boolean = false,
    val weeklyGoal: Int = 20,
    val auroraColor: AuroraColor = AuroraColor.EMERALD,
    val reviewColor: ReviewColor = ReviewColor.SAND,
    val profileShowName: Boolean = true,
    val profileShowHandle: Boolean = true,
    val profileShowRepos: Boolean = true,
    val profileShowAvatar: Boolean = true,
    val profileShowFollowers: Boolean = true,
    val profileShowFollowing: Boolean = true,
    val profileShowGitHub: Boolean = true,
    val profileShowWebsite: Boolean = true,
    val profileShowSocials: Boolean = true,
    val profileHiddenLinks: Set<String> = emptySet(),
    val profileTypeface: ProfileTypeface = ProfileTypeface.MONO,
    val profileStreakReplaces: ProfileCount? = null,
) {
    fun toJson(): String = JSONObject()
        .put("palette", paletteId)
        .put("customColor", customColor)
        .put("layout", layout.name)
        .put("contributionLayout", contributionLayout.name)
        .put("background", background.name)
        .put("opacity", opacity)
        .put("corners", corners.name)
        .put("range", range.name)
        .put("cellShape", cellShape.name)
        .put("showName", showName)
        .put("showAvatar", showAvatar)
        .put("showTotal", showTotal)
        .put("showStreak", showStreak)
        .put("showToday", showToday)
        .put("showMonths", showMonths)
        .put("weekStartsMonday", weekStartsMonday)
        .put("weeklyGoal", weeklyGoal)
        .put("auroraColor", auroraColor.name)
        .put("reviewColor", reviewColor.name)
        .put("profileShowName", profileShowName)
        .put("profileShowHandle", profileShowHandle)
        .put("profileShowRepos", profileShowRepos)
        .put("profileShowAvatar", profileShowAvatar)
        .put("profileShowFollowers", profileShowFollowers)
        .put("profileShowFollowing", profileShowFollowing)
        .put("profileShowGitHub", profileShowGitHub)
        .put("profileShowWebsite", profileShowWebsite)
        .put("profileShowSocials", profileShowSocials)
        .put("profileHiddenLinks", org.json.JSONArray(profileHiddenLinks.sorted()))
        .put("profileTypeface", profileTypeface.name)
        .put("profileStreakReplaces", profileStreakReplaces?.name)
        .toString()

    companion object {
        /** Lenient: unknown or missing fields fall back to defaults. */
        fun fromJson(json: String?): WidgetDesign {
            if (json.isNullOrBlank()) return WidgetDesign()
            return try {
                val o = JSONObject(json)
                val d = WidgetDesign()
                WidgetDesign(
                    paletteId = o.optString("palette", d.paletteId),
                    customColor = o.optInt("customColor", d.customColor),
                    layout = enumOr(o.optString("layout"), d.layout),
                    contributionLayout = enumOr(o.optString("contributionLayout", o.optString("layout")), d.contributionLayout),
                    background = enumOr(o.optString("background"), d.background),
                    opacity = o.optInt("opacity", d.opacity).coerceIn(0, 100),
                    corners = enumOr(o.optString("corners"), d.corners),
                    range = enumOr(o.optString("range"), d.range),
                    cellShape = enumOr(o.optString("cellShape"), d.cellShape),
                    showName = o.optBoolean("showName", d.showName),
                    showAvatar = o.optBoolean("showAvatar", d.showAvatar),
                    showTotal = o.optBoolean("showTotal", d.showTotal),
                    showStreak = o.optBoolean("showStreak", d.showStreak),
                    showToday = o.optBoolean("showToday", d.showToday),
                    showMonths = o.optBoolean("showMonths", d.showMonths),
                    weekStartsMonday = o.optBoolean("weekStartsMonday", d.weekStartsMonday),
                    auroraColor = enumOr(o.optString("auroraColor"), d.auroraColor),
                    reviewColor = enumOr(o.optString("reviewColor"), d.reviewColor),
                    weeklyGoal = o.optInt("weeklyGoal", d.weeklyGoal).coerceIn(1, 10000),
                    profileShowName = o.optBoolean("profileShowName", d.profileShowName),
                    profileShowHandle = o.optBoolean("profileShowHandle", d.profileShowHandle),
                    profileShowRepos = o.optBoolean("profileShowRepos", d.profileShowRepos),
                    profileShowAvatar = o.optBoolean("profileShowAvatar", d.profileShowAvatar),
                    profileShowFollowers = o.optBoolean("profileShowFollowers", d.profileShowFollowers),
                    profileShowFollowing = o.optBoolean("profileShowFollowing", d.profileShowFollowing),
                    profileShowGitHub = o.optBoolean("profileShowGitHub", d.profileShowGitHub),
                    profileShowWebsite = o.optBoolean("profileShowWebsite", d.profileShowWebsite),
                    profileShowSocials = o.optBoolean("profileShowSocials", d.profileShowSocials),
                    profileHiddenLinks = o.optJSONArray("profileHiddenLinks")?.let { links ->
                        (0 until links.length()).map { links.optString(it) }.toSet()
                    } ?: emptySet(),
                    profileTypeface = enumOr(o.optString("profileTypeface"), d.profileTypeface),
                    profileStreakReplaces = ProfileCount.entries.firstOrNull { it.name == o.optString("profileStreakReplaces") },
                )
            } catch (_: Exception) {
                WidgetDesign()
            }
        }

        private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
            enumValues<E>().firstOrNull { it.name == name } ?: fallback
    }
}
