package com.example.githubwidget

/**
 * Per-widget overrides (V1.9). Each widget instance can keep its own look;
 * anything never configured falls back to the global settings, so V1.8
 * users and unconfigured widgets behave exactly as before.
 *
 * Username intentionally stays global — no multi-account system.
 * Cleanup happens in the provider's onDeleted().
 */
object WidgetInstance {

    private const val KEY_CUSTOM = "custom"
    private const val KEY_THEME = "theme"
    private const val KEY_STYLE = "style"
    private const val KEY_OPACITY = "opacity"
    private const val KEY_RANGE = "range"
    private const val KEY_SHAPE = "shape"
    private const val KEY_SPACING = "spacing"
    private const val KEY_CORNERS = "corners"
    private const val KEY_SHOW_TOTAL = "show_total"
    private const val KEY_SHOW_STREAK = "show_streak"
    private const val KEY_SHOW_LONGEST = "show_longest"
    private const val KEY_SHOW_UPDATED = "show_updated"
    private const val KEY_CUSTOM_COLOR = "custom_color"

    private fun prefs(context: android.content.Context, widgetId: Int) =
        context.getSharedPreferences(
            "gh_widget_$widgetId",
            android.content.Context.MODE_PRIVATE
        )

    fun hasCustom(context: android.content.Context, widgetId: Int): Boolean =
        prefs(context, widgetId).getBoolean(KEY_CUSTOM, false)

    fun markCustom(context: android.content.Context, widgetId: Int) {
        prefs(context, widgetId).edit().putBoolean(KEY_CUSTOM, true).apply()
    }

    /** Removes all per-widget data (called when the widget is deleted). */
    fun clear(context: android.content.Context, widgetId: Int) {
        context.deleteSharedPreferences("gh_widget_$widgetId")
    }

    fun getThemeId(context: android.content.Context, widgetId: Int): String {
        val p = prefs(context, widgetId)
        return if (hasCustom(context, widgetId) && p.contains(KEY_THEME)) {
            p.getString(KEY_THEME, "green") ?: "green"
        } else {
            Prefs.getThemeId(context)
        }
    }

    fun getStyle(context: android.content.Context, widgetId: Int): String {
        val p = prefs(context, widgetId)
        return if (hasCustom(context, widgetId) && p.contains(KEY_STYLE)) {
            p.getString(KEY_STYLE, WidgetPrefs.STYLE_CLASSIC) ?: WidgetPrefs.STYLE_CLASSIC
        } else {
            WidgetPrefs.getStyle(context)
        }
    }

    fun getOpacity(context: android.content.Context, widgetId: Int): Int {
        val p = prefs(context, widgetId)
        return if (hasCustom(context, widgetId) && p.contains(KEY_OPACITY)) {
            p.getInt(KEY_OPACITY, 100).coerceIn(20, 100)
        } else {
            Prefs.getOpacity(context)
        }
    }

    fun getRange(context: android.content.Context, widgetId: Int): String {
        val p = prefs(context, widgetId)
        return if (hasCustom(context, widgetId) && p.contains(KEY_RANGE)) {
            p.getString(KEY_RANGE, WidgetPrefs.RANGE_6M) ?: WidgetPrefs.RANGE_6M
        } else {
            WidgetPrefs.getRange(context)
        }
    }

    fun getShape(context: android.content.Context, widgetId: Int): String {
        val p = prefs(context, widgetId)
        return if (hasCustom(context, widgetId) && p.contains(KEY_SHAPE)) {
            p.getString(KEY_SHAPE, WidgetPrefs.SHAPE_ROUNDED) ?: WidgetPrefs.SHAPE_ROUNDED
        } else {
            WidgetPrefs.getShape(context)
        }
    }

    fun getSpacing(context: android.content.Context, widgetId: Int): Float {
        val p = prefs(context, widgetId)
        return if (hasCustom(context, widgetId) && p.contains(KEY_SPACING)) {
            p.getFloat(KEY_SPACING, 1f).coerceIn(0.5f, 1.5f)
        } else {
            WidgetPrefs.getSpacing(context)
        }
    }

    fun getCorners(context: android.content.Context, widgetId: Int): Int {
        val p = prefs(context, widgetId)
        return if (hasCustom(context, widgetId) && p.contains(KEY_CORNERS)) {
            p.getInt(KEY_CORNERS, 16).coerceIn(0, 28)
        } else {
            WidgetPrefs.getCorners(context)
        }
    }

    fun getCustomColor(context: android.content.Context, widgetId: Int): Int {
        val p = prefs(context, widgetId)
        return if (hasCustom(context, widgetId) && p.contains(KEY_CUSTOM_COLOR)) {
            p.getInt(KEY_CUSTOM_COLOR, Prefs.getCustomColor(context))
        } else {
            Prefs.getCustomColor(context)
        }
    }

    fun showTotal(context: android.content.Context, widgetId: Int): Boolean {
        val p = prefs(context, widgetId)
        return if (hasCustom(context, widgetId) && p.contains(KEY_SHOW_TOTAL)) {
            p.getBoolean(KEY_SHOW_TOTAL, true)
        } else {
            WidgetPrefs.showTotal(context)
        }
    }

    fun showStreak(context: android.content.Context, widgetId: Int): Boolean {
        val p = prefs(context, widgetId)
        return if (hasCustom(context, widgetId) && p.contains(KEY_SHOW_STREAK)) {
            p.getBoolean(KEY_SHOW_STREAK, true)
        } else {
            WidgetPrefs.showStreak(context)
        }
    }

    fun showLongest(context: android.content.Context, widgetId: Int): Boolean {
        val p = prefs(context, widgetId)
        return if (hasCustom(context, widgetId) && p.contains(KEY_SHOW_LONGEST)) {
            p.getBoolean(KEY_SHOW_LONGEST, false)
        } else {
            WidgetPrefs.showLongest(context)
        }
    }

    fun showUpdated(context: android.content.Context, widgetId: Int): Boolean {
        val p = prefs(context, widgetId)
        return if (hasCustom(context, widgetId) && p.contains(KEY_SHOW_UPDATED)) {
            p.getBoolean(KEY_SHOW_UPDATED, true)
        } else {
            WidgetPrefs.showUpdated(context)
        }
    }

    /** Writes one widget's full look (called by the config screen). */
    fun save(
        context: android.content.Context,
        widgetId: Int,
        themeId: String,
        style: String,
        opacity: Int,
        range: String,
        shape: String,
        spacing: Float,
        corners: Int,
        customColor: Int,
        showTotal: Boolean,
        showStreak: Boolean,
        showLongest: Boolean,
        showUpdated: Boolean
    ) {
        prefs(context, widgetId).edit()
            .putBoolean(KEY_CUSTOM, true)
            .putString(KEY_THEME, themeId)
            .putString(KEY_STYLE, style)
            .putInt(KEY_OPACITY, opacity.coerceIn(20, 100))
            .putString(KEY_RANGE, range)
            .putString(KEY_SHAPE, shape)
            .putFloat(KEY_SPACING, spacing.coerceIn(0.5f, 1.5f))
            .putInt(KEY_CORNERS, corners.coerceIn(0, 28))
            .putInt(KEY_CUSTOM_COLOR, customColor)
            .putBoolean(KEY_SHOW_TOTAL, showTotal)
            .putBoolean(KEY_SHOW_STREAK, showStreak)
            .putBoolean(KEY_SHOW_LONGEST, showLongest)
            .putBoolean(KEY_SHOW_UPDATED, showUpdated)
            .apply()
    }
}
