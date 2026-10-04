package com.example.githubwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import java.text.NumberFormat
import java.util.Locale

class ContributionWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.example.githubwidget.ACTION_REFRESH"
        const val ACTION_REPAINT = "com.example.githubwidget.ACTION_REPAINT"

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
        val maxWeeks: Int,
        val monthLabels: Boolean,
        val showFooter: Boolean,
        val shortSubtitle: Boolean
    )

    private fun sizeClass(mgr: AppWidgetManager, widgetId: Int): SizeClass {
        return try {
            val opts = mgr.getAppWidgetOptions(widgetId)
            val minW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            when {
                minW < 180 -> SizeClass(maxWeeks = 12, monthLabels = false, showFooter = false, shortSubtitle = true)
                minW < 320 -> SizeClass(maxWeeks = 26, monthLabels = false, showFooter = true, shortSubtitle = false)
                else -> SizeClass(maxWeeks = 52, monthLabels = true, showFooter = true, shortSubtitle = false)
            }
        } catch (_: Exception) {
            SizeClass(maxWeeks = 26, monthLabels = false, showFooter = true, shortSubtitle = false)
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

    /** Per-widget files are removed with the widget — never touch another one. */
    override fun onDeleted(context: Context, ids: IntArray) {
        for (id in ids) {
            WidgetInstance.clear(context, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val mgr = AppWidgetManager.getInstance(context)
        when (intent.action) {
            ACTION_REFRESH -> {
                val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                    ?: mgr.getAppWidgetIds(ComponentName(context, ContributionWidgetProvider::class.java))
                Toast.makeText(context, "Refreshing…", Toast.LENGTH_SHORT).show()
                onUpdate(context, mgr, ids)
            }
            ACTION_REPAINT -> {
                // Look-only change: repaint from cache, fetch only if no cache yet.
                val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                    ?: mgr.getAppWidgetIds(ComponentName(context, ContributionWidgetProvider::class.java))
                val username = Prefs.getUsername(context)
                for (id in ids) {
                    val cached = Cache.load(context)
                    if (cached != null && cached.first.username.equals(username, ignoreCase = true)) {
                        paintCached(context, mgr, id)
                    } else {
                        paintCached(context, mgr, id)
                        refreshInBackground(context, mgr, id)
                    }
                }
            }
        }
    }

    private fun activityPi(context: Context, widgetId: Int, code: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(
            context, widgetId * 10 + code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /**
     * Tap map (predictable everywhere):
     * header -> GitHub profile (or app when no username yet)
     * graph  -> activity/analytics screen
     * total  -> this widget's own configuration
     * footer -> app home (customize defaults)
     * refresh icon -> manual refresh
     */
    private fun bindTaps(context: Context, views: RemoteViews, widgetId: Int, username: String) {
        val profileIntent = if (username.isBlank()) {
            Intent(context, MainActivity::class.java)
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/$username"))
        }
        views.setOnClickPendingIntent(
            R.id.widget_header, activityPi(context, widgetId, 1, profileIntent)
        )
        views.setOnClickPendingIntent(
            R.id.widget_graph,
            activityPi(context, widgetId, 2, Intent(context, ActivityActivity::class.java))
        )
        views.setOnClickPendingIntent(
            R.id.widget_total,
            activityPi(
                context, widgetId, 3,
                Intent(context, WidgetConfigActivity::class.java).putExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId
                )
            )
        )
        views.setOnClickPendingIntent(
            R.id.widget_updated,
            activityPi(context, widgetId, 4, Intent(context, MainActivity::class.java))
        )
        val refresh = Intent(context, ContributionWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
        }
        views.setOnClickPendingIntent(
            R.id.widget_refresh,
            PendingIntent.getBroadcast(
                context, widgetId * 10 + 5, refresh,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    private fun currentTheme(context: Context, widgetId: Int): GraphTheme =
        Themes.resolve(
            WidgetInstance.getThemeId(context, widgetId),
            WidgetInstance.getCustomColor(context, widgetId)
        )

    private fun baseViews(context: Context, widgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_contribution)
        val style = WidgetInstance.getStyle(context, widgetId)
        val opacity = WidgetInstance.getOpacity(context, widgetId)
        // Glass style floats at reduced opacity with a lighter rim.
        val alpha = if (style == WidgetPrefs.STYLE_GLASS) minOf(opacity, 45) else opacity
        val border = if (style == WidgetPrefs.STYLE_GLASS) {
            Color.parseColor("#8B949E")
        } else {
            Color.parseColor("#30363D")
        }
        val cornerPx = WidgetInstance.getCorners(context, widgetId) * 3f
        val bg = WidgetBg.render(
            bgColor = Themes.cardColor(alpha),
            cornerDp = cornerPx,
            borderColor = border
        )
        views.setImageViewBitmap(R.id.widget_bg, bg)
        views.setTextColor(R.id.widget_total, currentTheme(context, widgetId).accent)
        bindTaps(context, views, widgetId, Prefs.getUsername(context))
        return views
    }

    private fun formatTotal(total: Int): String =
        NumberFormat.getInstance(Locale.US).format(total)

    private fun isOnline(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true // If we can't tell, try the fetch — failure paths keep the cache.
        }
    }

    private fun paintResult(
        context: Context,
        widgetId: Int,
        views: RemoteViews,
        result: ContributionsResult,
        size: SizeClass,
        footer: String
    ) {
        val theme = currentTheme(context, widgetId)
        val style = WidgetInstance.getStyle(context, widgetId)
        val weeks = minOf(WidgetPrefs.rangeWeeks(WidgetInstance.getRange(context, widgetId)), size.maxWeeks)
        val bitmap: Bitmap = GraphRenderer.render(
            result.days,
            colors = theme.levels,
            maxWeeks = weeks,
            showMonthLabels = size.monthLabels,
            cornerRadius = WidgetPrefs.shapeRadiusFactor(WidgetInstance.getShape(context, widgetId)),
            gapScale = WidgetInstance.getSpacing(context, widgetId)
        )
        val stats = Stats.compute(result.days)
        val today = result.days.lastOrNull()?.count ?: 0
        val total = formatTotal(result.totalLastYear)

        views.setTextViewText(R.id.widget_title, "@${result.username}")
        views.setImageViewBitmap(R.id.widget_graph, bitmap)
        views.setContentDescription(
            R.id.widget_graph,
            "@${result.username}: $total contributions in the last year, " +
                "${stats.currentStreak} day streak. Tap for activity details."
        )
        views.setViewVisibility(R.id.widget_graph, View.VISIBLE)

        when (style) {
            WidgetPrefs.STYLE_MINIMAL -> {
                // Graph only.
                views.setViewVisibility(R.id.widget_subtitle, View.GONE)
                views.setViewVisibility(R.id.widget_total, View.GONE)
                views.setViewVisibility(R.id.widget_updated, View.GONE)
            }
            WidgetPrefs.STYLE_TERMINAL -> {
                // Count-first, no big number.
                val parts = ArrayList<String>()
                parts.add("$total contributions")
                parts.add("today $today")
                if (WidgetInstance.showStreak(context, widgetId)) parts.add("${stats.currentStreak}d streak")
                if (WidgetInstance.showLongest(context, widgetId)) parts.add("${stats.longestStreak}d longest")
                views.setViewVisibility(R.id.widget_subtitle, View.VISIBLE)
                views.setTextViewText(R.id.widget_subtitle, parts.joinToString(" • "))
                views.setViewVisibility(R.id.widget_total, View.GONE)
                setFooter(views, size, context, widgetId, stats, footer)
            }
            else -> {
                val parts = ArrayList<String>()
                parts.add("Today: $today")
                if (WidgetInstance.showStreak(context, widgetId)) parts.add("${stats.currentStreak}d streak")
                if (WidgetInstance.showTotal(context, widgetId)) parts.add("$total/yr")
                views.setViewVisibility(R.id.widget_subtitle, View.VISIBLE)
                views.setTextViewText(
                    R.id.widget_subtitle,
                    if (size.shortSubtitle && parts.size > 2) {
                        "${parts[0]} • ${parts[1]}"
                    } else {
                        parts.joinToString(" • ")
                    }
                )
                if (WidgetInstance.showTotal(context, widgetId)) {
                    views.setViewVisibility(R.id.widget_total, View.VISIBLE)
                    views.setTextViewText(R.id.widget_total, total)
                } else {
                    views.setViewVisibility(R.id.widget_total, View.GONE)
                }
                setFooter(views, size, context, widgetId, stats, footer)
            }
        }
    }

    private fun setFooter(
        views: RemoteViews,
        size: SizeClass,
        context: Context,
        widgetId: Int,
        stats: ContributionStats,
        footer: String
    ) {
        val show = WidgetInstance.showUpdated(context, widgetId) && size.showFooter
        if (!show) {
            views.setViewVisibility(R.id.widget_updated, View.GONE)
            return
        }
        var text = footer
        if (WidgetInstance.showLongest(context, widgetId)) {
            text = text.replace(" • tap refresh icon", " • ${stats.longestStreak}d longest • tap refresh icon")
                .replace("Last updated", "Updated")
        }
        views.setViewVisibility(R.id.widget_updated, View.VISIBLE)
        views.setTextViewText(R.id.widget_updated, text)
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
            views.setViewVisibility(R.id.widget_subtitle, View.VISIBLE)
            views.setViewVisibility(R.id.widget_graph, View.GONE)
            views.setViewVisibility(R.id.widget_total, View.GONE)
            views.setViewVisibility(R.id.widget_updated, View.GONE)
            mgr.updateAppWidget(widgetId, views)
            return
        }

        val cached = Cache.load(context)
        if (cached != null && cached.first.username.equals(username, ignoreCase = true)) {
            val (result, ts) = cached
            paintResult(widgetId = widgetId, context = context, views = views, result = result, size = size, footer = "Last updated ${TimeAgo.format(ts)}")
        } else {
            views.setTextViewText(R.id.widget_title, "@$username")
            views.setTextViewText(R.id.widget_subtitle, "Loading contributions…")
            views.setViewVisibility(R.id.widget_subtitle, View.VISIBLE)
            views.setViewVisibility(R.id.widget_graph, View.GONE)
            views.setViewVisibility(R.id.widget_total, View.GONE)
            views.setViewVisibility(R.id.widget_updated, View.GONE)
        }
        mgr.updateAppWidget(widgetId, views)
    }

    /**
     * One background fetch per widget; concurrent refreshes of the same widget
     * are skipped. Offline means no request at all — the cache stays on screen.
     * Failures keep the cached graph.
     */
    private fun refreshInBackground(context: Context, mgr: AppWidgetManager, widgetId: Int) {
        val username = Prefs.getUsername(context)
        if (username.isBlank() || !beginRefresh(widgetId)) return
        if (!isOnline(context)) {
            endRefresh(widgetId)
            return
        }

        val pending = goAsync()
        Thread {
            try {
                val result = GithubApi.fetch(username)
                Cache.save(context, result)
                val views = baseViews(context, widgetId)
                val size = sizeClass(mgr, widgetId)
                paintResult(context, widgetId, views, result, size, "Updated ${TimeAgo.format(System.currentTimeMillis())} • tap refresh icon to refresh")
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
                        widgetId, context, views, cached.first, size,
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
