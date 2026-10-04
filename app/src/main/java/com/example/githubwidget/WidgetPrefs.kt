package com.example.githubwidget

/**
 * Central store for all widget appearance/content customization (V1.7).
 *
 * Username + cached data live elsewhere (Prefs / Cache) and are never
 * touched here, so Reset only restores the look — never the data.
 * Every getter has a default that reproduces the V1.6 look, so existing
 * users keep their configuration when upgrading.
 */
object WidgetPrefs {

    private const val NAME = "gh_widget_style"

    // Styles
    const val STYLE_CLASSIC = "classic"
    const val STYLE_MINIMAL = "minimal"
    const val STYLE_COMPACT = "compact"
    const val STYLE_TERMINAL = "terminal"
    const val STYLE_GLASS = "glass"
    val STYLES = listOf(STYLE_CLASSIC, STYLE_MINIMAL, STYLE_COMPACT, STYLE_TERMINAL, STYLE_GLASS)
    val STYLE_NAMES = mapOf(
        STYLE_CLASSIC to "Classic",
        STYLE_MINIMAL to "Minimal",
        STYLE_COMPACT to "Compact",
        STYLE_TERMINAL to "Terminal",
        STYLE_GLASS to "Glass"
    )

    // Graph ranges (user-facing) -> weeks drawn
    const val RANGE_3M = "3m"
    const val RANGE_6M = "6m"
    const val RANGE_12M = "12m"

    // Cell shapes
    const val SHAPE_SQUARE = "square"
    const val SHAPE_ROUNDED = "rounded"
    const val SHAPE_SOFT = "soft"
    const val SHAPE_CIRCLE = "circle"

    private const val KEY_STYLE = "style"
    private const val KEY_RANGE = "range"
    private const val KEY_SHAPE = "shape"
    private const val KEY_SPACING = "spacing"
    private const val KEY_CORNERS = "corners"
    private const val KEY_SHOW_TOTAL = "show_total"
    private const val KEY_SHOW_STREAK = "show_streak"
    private const val KEY_SHOW_LONGEST = "show_longest"
    private const val KEY_SHOW_UPDATED = "show_updated"

    private fun prefs(context: android.content.Context) =
        context.getSharedPreferences(NAME, android.content.Context.MODE_PRIVATE)

    fun getStyle(context: android.content.Context): String =
        prefs(context).getString(KEY_STYLE, STYLE_CLASSIC) ?: STYLE_CLASSIC

    fun setStyle(context: android.content.Context, style: String) {
        prefs(context).edit().putString(KEY_STYLE, style).apply()
    }

    fun getRange(context: android.content.Context): String =
        prefs(context).getString(KEY_RANGE, RANGE_6M) ?: RANGE_6M

    fun setRange(context: android.content.Context, range: String) {
        prefs(context).edit().putString(KEY_RANGE, range).apply()
    }

    /** Weeks for a range choice, before widget-size adaptation. */
    fun rangeWeeks(range: String): Int = when (range) {
        RANGE_3M -> 13
        RANGE_12M -> 52
        else -> 26
    }

    fun getShape(context: android.content.Context): String =
        prefs(context).getString(KEY_SHAPE, SHAPE_ROUNDED) ?: SHAPE_ROUNDED

    fun setShape(context: android.content.Context, shape: String) {
        prefs(context).edit().putString(KEY_SHAPE, shape).apply()
    }

    /** Cell corner radius as a fraction of the cell size. */
    fun shapeRadiusFactor(shape: String): Float = when (shape) {
        SHAPE_SQUARE -> 0.08f
        SHAPE_SOFT -> 0.5f
        else -> 0.3f
    }

    /** Cell gap multiplier. 1.0 reproduces the V1.6 spacing. */
    fun getSpacing(context: android.content.Context): Float =
        prefs(context).getFloat(KEY_SPACING, 1f).coerceIn(0.5f, 1.5f)

    fun setSpacing(context: android.content.Context, spacing: Float) {
        prefs(context).edit().putFloat(KEY_SPACING, spacing.coerceIn(0.5f, 1.5f)).apply()
    }

    /** Card corner radius in dp, 0 (sharp) .. 28 (soft). 16 matches V1.6. */
    fun getCorners(context: android.content.Context): Int =
        prefs(context).getInt(KEY_CORNERS, 16).coerceIn(0, 28)

    fun setCorners(context: android.content.Context, dp: Int) {
        prefs(context).edit().putInt(KEY_CORNERS, dp.coerceIn(0, 28)).apply()
    }

    fun showTotal(context: android.content.Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_TOTAL, true)

    fun showStreak(context: android.content.Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_STREAK, true)

    fun showLongest(context: android.content.Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_LONGEST, false)

    fun showUpdated(context: android.content.Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_UPDATED, true)

    fun setContent(
        context: android.content.Context,
        total: Boolean,
        streak: Boolean,
        longest: Boolean,
        updated: Boolean
    ) {
        prefs(context).edit()
            .putBoolean(KEY_SHOW_TOTAL, total)
            .putBoolean(KEY_SHOW_STREAK, streak)
            .putBoolean(KEY_SHOW_LONGEST, longest)
            .putBoolean(KEY_SHOW_UPDATED, updated)
            .apply()
    }

    /** Restores the look only — username and cached data are untouched. */
    fun resetToDefaults(context: android.content.Context) {
        Presets.apply(context, Presets.DEFAULT)
    }
}

/** One-tap looks. Each sets theme + style + graph + card + content at once. */
object Presets {

    const val DEFAULT = "default"
    const val MINIMAL = "minimal"
    const val DEVELOPER = "developer"
    const val NEON = "neon"

    val ALL = listOf(DEFAULT, MINIMAL, DEVELOPER, NEON)
    val NAMES = mapOf(
        DEFAULT to "Default",
        MINIMAL to "Minimal",
        DEVELOPER to "Developer",
        NEON to "Neon"
    )

    fun apply(context: android.content.Context, preset: String) {
        when (preset) {
            MINIMAL -> {
                Prefs.setThemeId(context, "green")
                WidgetPrefs.setStyle(context, WidgetPrefs.STYLE_MINIMAL)
                WidgetPrefs.setShape(context, WidgetPrefs.SHAPE_SOFT)
                WidgetPrefs.setSpacing(context, 1.5f)
                WidgetPrefs.setRange(context, WidgetPrefs.RANGE_6M)
                Prefs.setOpacity(context, 100)
                WidgetPrefs.setCorners(context, 20)
                WidgetPrefs.setContent(context, total = false, streak = false, longest = false, updated = false)
            }
            DEVELOPER -> {
                Prefs.setThemeId(context, "green")
                WidgetPrefs.setStyle(context, WidgetPrefs.STYLE_TERMINAL)
                WidgetPrefs.setShape(context, WidgetPrefs.SHAPE_SQUARE)
                WidgetPrefs.setSpacing(context, 0.5f)
                WidgetPrefs.setRange(context, WidgetPrefs.RANGE_12M)
                Prefs.setOpacity(context, 85)
                WidgetPrefs.setCorners(context, 4)
                WidgetPrefs.setContent(context, total = true, streak = true, longest = true, updated = true)
            }
            NEON -> {
                Prefs.setThemeId(context, "rose")
                WidgetPrefs.setStyle(context, WidgetPrefs.STYLE_GLASS)
                WidgetPrefs.setShape(context, WidgetPrefs.SHAPE_SOFT)
                WidgetPrefs.setSpacing(context, 1f)
                WidgetPrefs.setRange(context, WidgetPrefs.RANGE_6M)
                Prefs.setOpacity(context, 60)
                WidgetPrefs.setCorners(context, 24)
                WidgetPrefs.setContent(context, total = true, streak = true, longest = false, updated = true)
            }
            else -> {
                Prefs.setThemeId(context, "green")
                WidgetPrefs.setStyle(context, WidgetPrefs.STYLE_CLASSIC)
                WidgetPrefs.setShape(context, WidgetPrefs.SHAPE_ROUNDED)
                WidgetPrefs.setSpacing(context, 1f)
                WidgetPrefs.setRange(context, WidgetPrefs.RANGE_6M)
                Prefs.setOpacity(context, 100)
                WidgetPrefs.setCorners(context, 16)
                WidgetPrefs.setContent(context, total = true, streak = true, longest = false, updated = true)
            }
        }
    }

    /** Content toggles each style starts with (user can change them after). */
    fun styleContent(style: String): BooleanArray = when (style) {
        WidgetPrefs.STYLE_MINIMAL -> booleanArrayOf(false, false, false, false)
        WidgetPrefs.STYLE_COMPACT -> booleanArrayOf(true, true, false, false)
        WidgetPrefs.STYLE_TERMINAL -> booleanArrayOf(false, true, true, true)
        else -> booleanArrayOf(true, true, false, true)
    }
}
