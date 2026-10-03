package com.example.githubwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
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

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                ?: mgr.getAppWidgetIds(
                    android.content.ComponentName(context, ContributionWidgetProvider::class.java)
                )
            onUpdate(context, mgr, ids)
        }
    }

    private fun updateOne(context: Context, mgr: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_contribution)
        val username = Prefs.getUsername(context)

        // Click on header opens the app to change username.
        val openApp = Intent(context, MainActivity::class.java)
        val openPi = PendingIntent.getActivity(
            context, widgetId, openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_header, openPi)

        // Refresh button.
        val refresh = Intent(context, ContributionWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
        }
        val refreshPi = PendingIntent.getBroadcast(
            context, widgetId, refresh,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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
                val result = GithubApi.fetch(username)
                val bitmap: Bitmap = GraphRenderer.render(result.days)
                val fresh = RemoteViews(context.packageName, R.layout.widget_contribution)
                fresh.setOnClickPendingIntent(R.id.widget_header, openPi)
                fresh.setOnClickPendingIntent(R.id.widget_refresh, refreshPi)
                fresh.setTextViewText(R.id.widget_title, "@${result.username}")
                val today = result.days.lastOrNull()
                fresh.setTextViewText(
                    R.id.widget_subtitle,
                    "Today: ${today?.count ?: 0} • ${result.totalLastYear} in last year"
                )
                fresh.setTextViewText(R.id.widget_total, "${result.totalLastYear}")
                fresh.setImageViewBitmap(R.id.widget_graph, bitmap)
                val ts = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date())
                fresh.setTextViewText(R.id.widget_updated, "Updated $ts • tap ⟳ to refresh")
                // Cache for offline: store total + updated label only (bitmap is redrawn next time).
                mgr.updateAppWidget(widgetId, fresh)
            } catch (e: Exception) {
                val err = RemoteViews(context.packageName, R.layout.widget_contribution)
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
