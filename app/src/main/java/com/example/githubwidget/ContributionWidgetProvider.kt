package com.example.githubwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ContributionWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.example.githubwidget.ACTION_REFRESH"
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            updateOne(context, mgr, id)
        }
    }

    /** Re-render when the user resizes the widget on the homescreen. */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        mgr: AppWidgetManager,
        widgetId: Int,
        newOptions: Bundle
    ) {
        updateOne(context, mgr, widgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                ?: mgr.getAppWidgetIds(ComponentName(context, ContributionWidgetProvider::class.java))
            onUpdate(context, mgr, ids)
        }
    }

    private fun pendingIntents(context: Context, widgetId: Int): Pair<PendingIntent, PendingIntent> {
        val openApp = Intent(context, MainActivity::class.java)
        val openPi = PendingIntent.getActivity(
            context, widgetId, openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val refresh = Intent(context, ContributionWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
        }
        val refreshPi = PendingIntent.getBroadcast(
            context, 10_000 + widgetId, refresh,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return openPi to refreshPi
    }

    private fun currentTheme(context: Context): GraphTheme =
        Themes.resolve(Prefs.getThemeId(context), Prefs.getCustomColor(context))

    private fun applyChrome(views: RemoteViews, context: Context) {
        val theme = currentTheme(context)
        val bg = WidgetBg.render(bgColor = Themes.cardColor(Prefs.getOpacity(context)))
        views.setImageViewBitmap(R.id.widget_bg, bg)
        views.setTextColor(R.id.widget_total, theme.accent)
    }

    private fun updateOne(context: Context, mgr: AppWidgetManager, widgetId: Int) {
        val (openPi, refreshPi) = pendingIntents(context, widgetId)
        val views = RemoteViews(context.packageName, R.layout.widget_contribution)
        val username = Prefs.getUsername(context)
        applyChrome(views, context)

        // Click on header opens the app to change username.
        views.setOnClickPendingIntent(R.id.widget_header, openPi)
        // Refresh button.
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshPi)

        if (username.isBlank()) {
            views.setTextViewText(R.id.widget_title, "Tap to set GitHub username")
            views.setTextViewText(R.id.widget_subtitle, "Open app → enter username")
            views.setViewVisibility(R.id.widget_graph, View.GONE)
            views.setViewVisibility(R.id.widget_total, View.GONE)
            mgr.updateAppWidget(widgetId, views)
            return
        }

        views.setViewVisibility(R.id.widget_graph, View.VISIBLE)
        views.setViewVisibility(R.id.widget_total, View.VISIBLE)
        views.setTextViewText(R.id.widget_title, "@$username")
        views.setTextViewText(R.id.widget_subtitle, "Loading…")
        mgr.updateAppWidget(widgetId, views)

        // Network off the main thread. goAsync() keeps the broadcast alive.
        val pending = goAsync()
        Thread {
            try {
                val theme = currentTheme(context)
                val result = GithubApi.fetch(username)
                val bitmap: Bitmap = GraphRenderer.render(result.days, colors = theme.levels)
                val fresh = RemoteViews(context.packageName, R.layout.widget_contribution)
                applyChrome(fresh, context)
                fresh.setOnClickPendingIntent(R.id.widget_header, openPi)
                fresh.setOnClickPendingIntent(R.id.widget_refresh, refreshPi)
                fresh.setTextViewText(R.id.widget_title, "@${result.username}")
                val today = result.days.lastOrNull()
                val stats = Stats.compute(result.days)
                fresh.setTextViewText(
                    R.id.widget_subtitle,
                    "Today: ${today?.count ?: 0} • ${stats.currentStreak}d streak • ${result.totalLastYear}/yr"
                )
                fresh.setTextViewText(R.id.widget_total, "${result.totalLastYear}")
                fresh.setImageViewBitmap(R.id.widget_graph, bitmap)
                val ts = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date())
                fresh.setTextViewText(R.id.widget_updated, "Updated $ts • tap ⟳ to refresh")
                mgr.updateAppWidget(widgetId, fresh)
            } catch (e: Exception) {
                val err = RemoteViews(context.packageName, R.layout.widget_contribution)
                applyChrome(err, context)
                err.setOnClickPendingIntent(R.id.widget_header, openPi)
                err.setOnClickPendingIntent(R.id.widget_refresh, refreshPi)
                err.setTextViewText(R.id.widget_title, "@$username")
                err.setTextViewText(R.id.widget_subtitle, "Couldn't load. Tap ⟳ to retry.")
                err.setTextViewText(R.id.widget_updated, (e.message ?: "Network error").take(80))
                mgr.updateAppWidget(widgetId, err)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
