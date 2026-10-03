package com.example.githubwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

object Prefs {
    private const val NAME = "github_widget_prefs"
    private const val KEY_USERNAME = "username"
    private const val KEY_THEME = "theme"
    private const val KEY_OPACITY = "opacity"

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

    fun requestRefresh(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, ContributionWidgetProvider::class.java))
        val intent = Intent(context, ContributionWidgetProvider::class.java).apply {
            action = ContributionWidgetProvider.ACTION_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        context.sendBroadcast(intent)
    }
}
