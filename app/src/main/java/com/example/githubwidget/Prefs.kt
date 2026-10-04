package com.example.githubwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color

object Prefs {
    private const val NAME = "github_widget_prefs"
    private const val KEY_USERNAME = "username"
    private const val KEY_THEME = "theme"
    private const val KEY_OPACITY = "opacity"
    private const val KEY_CUSTOM_COLOR = "custom_color"

    fun getUsername(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_USERNAME, "") ?: ""

    fun setUsername(context: Context, username: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_USERNAME, username.trim()).apply()
    }

    fun getThemeId(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME, "green") ?: "green"

    fun setThemeId(context: Context, id: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME, id).apply()
    }

    /** Card opacity 20..100 (percent). */
    fun getOpacity(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getInt(KEY_OPACITY, 100).coerceIn(20, 100)

    fun setOpacity(context: Context, opacity: Int) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_OPACITY, opacity.coerceIn(20, 100)).apply()
    }

    /** User-picked custom graph color (used when theme == "custom"). */
    fun getCustomColor(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getInt(KEY_CUSTOM_COLOR, Color.parseColor("#39D353"))

    fun setCustomColor(context: Context, color: Int) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_CUSTOM_COLOR, color).apply()
    }

    fun requestRefresh(context: Context) {
        broadcast(context, ContributionWidgetProvider.ACTION_REFRESH)
    }

    /**
     * Repaints widgets from cache only — no network. Use for look-only
     * changes (theme, style, transparency) so they apply instantly offline.
     */
    fun requestRepaint(context: Context) {
        broadcast(context, ContributionWidgetProvider.ACTION_REPAINT)
    }

    private fun broadcast(context: Context, action: String) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, ContributionWidgetProvider::class.java))
        val intent = Intent(context, ContributionWidgetProvider::class.java).apply {
            this.action = action
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        context.sendBroadcast(intent)
    }
}
