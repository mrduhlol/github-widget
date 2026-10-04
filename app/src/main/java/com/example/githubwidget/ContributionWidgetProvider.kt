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

class ContributionWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.example.githubwidget.ACTION_REFRESH"

        /** Guards against overlapping refreshes of the same widget. */
        private val inFlight = mutableSetOf<Int>()

        @Synchronized
        private fun beginRefresh(widgetId: Int): Boolean {
            if (inFlight.contains(widgetId)) return false
            inFlight.add(widgetId)
            return true
        }

        @Synchronized
        private fun endRefresh(widgetId: Int) {
            inFlight.remove(widgetId)
        }
    }

    /** How much to show, based on the widget's current minimum width. */
    private data class SizeClass(
        val weeks: Int,
        val monthLabels: Boolean,
        val showFooter: Boolean,
        val shortSubtitle: Boolean
    )

    private fun sizeClass(mgr: AppWidgetManager, widgetId: Int): SizeClass {
        return try {
            val opts = mgr.getAppWidgetOptions(widgetId)
            val minW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            when {
                minW < 180 -> SizeClass(weeks = 12, monthLabels = false, showFooter = false, shortSubtitle = true)
                minW < 320 -> SizeClass(weeks = 26, monthLabels = false, showFooter = true, shortSubtitle = false)
                else -> SizeClass(weeks = 40, monthLabels = true, showFooter = true, shortSubtitle = false)
            }
        } catch (_: Exception) {
            SizeClass(weeks = 26, monthLabels = false, showFooter = true, shortSubtitle = false)
        }
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            paintCached(context, mgr, id)
            refreshInBackground(context, mgr, id)
        }
    }

    /** Re-render when the user resizes the widget on the homescreen. */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        mgr: AppWidgetManager,
        widgetId: Int,
        newOptions: Bundle
    ) {
        paintCached(context, mgr, widgetId)
        refreshInBackground(context, mgr, widgetId)
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

    private fun baseViews(context: Context, widgetId: Int): RemoteViews {
        val (openPi, refreshPi) = pendingIntents(context, widgetId)
        val views = RemoteViews(context.packageName, R.layout.widget_contribution)
        val bg = WidgetBg.render(bgColor = Themes.cardColor(Prefs.getOpacity(context)))
        views.setImageViewBitmap(R.id.widget_bg, bg)
        views.setTextColor(R.id.widget_total, currentTheme(context).accent)
        views.setOnClickPendingIntent(R.id.widget_header, openPi)
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshPi)
        return views
    }

    private fun paintResult(
        context: Context,
        views: RemoteViews,
        result: ContributionsResult,
        size: SizeClass,
        footer: String
    ) {
        val theme = currentTheme(context)
        val bitmap: Bitmap = GraphRenderer.render(
            result.days,
            colors = theme.levels,
            maxWeeks = size.weeks,
            showMonthLabels = size.monthLabels
        )
        val stats = Stats.compute(result.days)
        val today = result.days.lastOrNull()
        views.setTextViewText(R.id.widget_title, "@${result.username}")
        views.setTextViewText(
            R.id.widget_subtitle,
            if (size.shortSubtitle) {
                "Today: ${today?.count ?: 0} • ${stats.currentStreak}d streak"
            } else {
                "Today: ${today?.count ?: 0} • ${stats.currentStreak}d streak • ${result.totalLastYear}/yr"
            }
        )
        views.setTextViewText(R.id.widget_total, "${result.totalLastYear}")
        views.setImageViewBitmap(R.id.widget_graph, bitmap)
        views.setContentDescription(
            R.id.widget_graph,
            "@${result.username}: ${result.totalLastYear} contributions in the last year, " +
                "${stats.currentStreak} day streak."
        )
        views.setViewVisibility(R.id.widget_graph, View.VISIBLE)
        views.setViewVisibility(R.id.widget_total, View.VISIBLE)
        if (size.showFooter) {
            views.setViewVisibility(R.id.widget_updated, View.VISIBLE)
            views.setTextViewText(R.id.widget_updated, footer)
        } else {
            views.setViewVisibility(R.id.widget_updated, View.GONE)
        }
    }

    /**
     * Paint instantly from cache (or a clean placeholder). Never touches network,
     * so the widget is never blank and works fully offline.
     */
    private fun paintCached(context: Context, mgr: AppWidgetManager, widgetId: Int) {
        val views = baseViews(context, widgetId)
        val username = Prefs.getUsername(context)
        val size = sizeClass(mgr, widgetId)

        if (username.isBlank()) {
            views.setTextViewText(R.id.widget_title, "Tap to open GH-widgets")
            views.setTextViewText(R.id.widget_subtitle, "Enter your GitHub username")
            views.setViewVisibility(R.id.widget_graph, View.GONE)
            views.setViewVisibility(R.id.widget_total, View.GONE)
            views.setViewVisibility(R.id.widget_updated, View.GONE)
            mgr.updateAppWidget(widgetId, views)
            return
        }

        val cached = Cache.load(context)
        if (cached != null && cached.first.username.equals(username, ignoreCase = true)) {
            val (result, ts) = cached
            paintResult(context, views, result, size, "Last updated ${TimeAgo.format(ts)}")
        } else {
            views.setTextViewText(R.id.widget_title, "@$username")
            views.setTextViewText(R.id.widget_subtitle, "Loading contributions…")
            views.setViewVisibility(R.id.widget_graph, View.GONE)
            views.setViewVisibility(R.id.widget_total, View.GONE)
            views.setViewVisibility(R.id.widget_updated, View.GONE)
        }
        mgr.updateAppWidget(widgetId, views)
    }

    /**
     * One background fetch per widget; concurrent refreshes of the same widget
     * are skipped. Failures keep the cached graph on screen.
     */
    private fun refreshInBackground(context: Context, mgr: AppWidgetManager, widgetId: Int) {
        val username = Prefs.getUsername(context)
        if (username.isBlank() || !beginRefresh(widgetId)) return

        val pending = goAsync()
        Thread {
            try {
                val result = GithubApi.fetch(username)
                Cache.save(context, result)
                val views = baseViews(context, widgetId)
                val size = sizeClass(mgr, widgetId)
                paintResult(context, views, result, size, "Updated ${TimeAgo.format(System.currentTimeMillis())} • tap refresh icon to refresh")
                mgr.updateAppWidget(widgetId, views)
            } catch (e: UserNotFoundException) {
                val views = baseViews(context, widgetId)
                views.setTextViewText(R.id.widget_title, "@$username")
                views.setTextViewText(R.id.widget_subtitle, "User not found — tap to fix")
                mgr.updateAppWidget(widgetId, views)
            } catch (_: Exception) {
                // Offline or server trouble: keep whatever is on screen (cache
                // if we have it) and note the data's age instead of an error dump.
                val cached = Cache.load(context)
                if (cached != null && cached.first.username.equals(username, ignoreCase = true)) {
                    val views = baseViews(context, widgetId)
                    val size = sizeClass(mgr, widgetId)
                    paintResult(
                        context, views, cached.first, size,
                        "Last updated ${TimeAgo.format(cached.second)} • tap refresh icon to retry"
                    )
                    mgr.updateAppWidget(widgetId, views)
                } else {
                    val views = baseViews(context, widgetId)
                    views.setTextViewText(R.id.widget_title, "@$username")
                    views.setTextViewText(R.id.widget_subtitle, "No connection — tap refresh icon to retry")
                    mgr.updateAppWidget(widgetId, views)
                }
            } finally {
                endRefresh(widgetId)
                pending.finish()
            }
        }.start()
    }
}
