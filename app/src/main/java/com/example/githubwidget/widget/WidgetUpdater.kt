package com.example.githubwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import com.example.githubwidget.ContributionWidgetProvider
import com.example.githubwidget.MainActivity
import com.example.githubwidget.R
import com.example.githubwidget.data.Repository
import com.example.githubwidget.design.Background

/** Renders the current design + data into every placed widget. */
object WidgetUpdater {

    private const val PREFS = "gh_widget_ids"
    private const val KEY_DRAWN = "drawn"

    fun widgetIds(context: Context): IntArray =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, ContributionWidgetProvider::class.java))

    fun hasWidgets(context: Context) = widgetIds(context).isNotEmpty()

    fun updateAll(context: Context) = update(context, widgetIds(context))

    fun update(context: Context, ids: IntArray) {
        if (ids.isEmpty()) return
        Repository.init(context)
        val mgr = AppWidgetManager.getInstance(context)
        for (id in ids) {
            runCatching { mgr.updateAppWidget(id, build(context, mgr.getAppWidgetOptions(id))) }
        }
        markDrawn(context, ids)
    }

    /** Portrait size of the widget in dp; launchers report min width x max height for portrait. */
    fun sizeDp(options: Bundle?): SizeF {
        if (options == null) return SizeF(320f, 160f)
        val w = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val h = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        return if (w > 0 && h > 0) SizeF(w.toFloat(), h.toFloat()) else SizeF(320f, 160f)
    }

    private fun build(context: Context, options: Bundle?): RemoteViews {
        val size = sizeDp(options)
        val design = Repository.design.value
        val data = Repository.data.value
        val placeholder = when {
            data != null -> WidgetRenderer.Placeholder.NONE
            Repository.hasUser -> WidgetRenderer.Placeholder.LOADING
            else -> WidgetRenderer.Placeholder.SIGNED_OUT
        }
        fun render(dark: Boolean) = WidgetRenderer.render(
            context, design, data, Repository.avatar.value, size.width, size.height, dark, placeholder,
        )

        val views = RemoteViews(context.packageName, R.layout.widget_root)
        // The layout shows one image in light mode and the other in dark mode
        // (layout-night), so "Auto" follows the phone's theme instantly.
        if (design.background == Background.AUTO) {
            views.setImageViewBitmap(R.id.widget_image_light, render(dark = false))
            views.setImageViewBitmap(R.id.widget_image_dark, render(dark = true))
        } else {
            val bmp = render(dark = true) // background choice decides the colors
            views.setImageViewBitmap(R.id.widget_image_light, bmp)
            views.setImageViewBitmap(R.id.widget_image_dark, bmp)
        }
        val desc = data?.let { "GitHub contributions for ${it.contributions.username}: ${it.contributions.totalLastYear} in the last year" }
            ?: "GitHub contributions widget. Tap to set up."
        views.setContentDescription(R.id.widget_image_light, desc)
        views.setContentDescription(R.id.widget_image_dark, desc)

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_root, open)
        return views
    }

    private fun markDrawn(context: Context, ids: IntArray) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = p.getStringSet(KEY_DRAWN, emptySet())!!.toMutableSet()
        if (set.addAll(ids.map { it.toString() })) p.edit().putStringSet(KEY_DRAWN, set).apply()
    }

    /** True if this widget has been drawn before, i.e. the user is re-configuring it. */
    fun wasDrawn(context: Context, id: Int): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_DRAWN, emptySet())!!.contains(id.toString())

    fun forget(context: Context, ids: IntArray) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = p.getStringSet(KEY_DRAWN, emptySet())!!.toMutableSet()
        set.removeAll(ids.map { it.toString() }.toSet())
        p.edit().putStringSet(KEY_DRAWN, set).apply()
    }
}
