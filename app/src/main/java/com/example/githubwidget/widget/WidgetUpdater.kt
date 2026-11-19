package com.example.githubwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.example.githubwidget.design.WidgetLayout
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import com.example.githubwidget.ContributionWidgetProvider
import com.example.githubwidget.MainActivity
import com.example.githubwidget.R
import com.example.githubwidget.data.Repository
import com.example.githubwidget.data.RefreshState
import com.example.githubwidget.data.ReviewInbox
import com.example.githubwidget.design.Background

/** Renders the current design + data into every placed widget. */
object WidgetUpdater {

    private const val PREFS = "gh_widget_ids"
    private const val KEY_DRAWN = "drawn"
    private data class RenderKey(val layout: WidgetLayout, val width: Float, val height: Float, val dark: Boolean, val pixelBudget: Int)

    fun widgetIds(context: Context): IntArray =
        WidgetKind.entries.flatMap { kind ->
            AppWidgetManager.getInstance(context).getAppWidgetIds(kind.component(context)).toList()
        }.toIntArray()

    fun hasWidgets(context: Context) = widgetIds(context).isNotEmpty()

    internal fun streakIds(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(WidgetKind.STREAK.component(context))

    internal fun reviewInboxIds(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(WidgetKind.PR_INBOX.component(context))

    fun updateAll(context: Context) = update(context, widgetIds(context))

    fun update(context: Context, ids: IntArray) {
        if (ids.isEmpty()) return
        Repository.init(context)
        val mgr = AppWidgetManager.getInstance(context)
        // Widgets of the same size look identical, so each bitmap is drawn once per pass.
        val drawn = HashMap<RenderKey, Bitmap>()
        var hasStreak = false
        for (id in ids) {
            val component = mgr.getAppWidgetInfo(id)?.provider
            val kind = WidgetKind.entries.firstOrNull { it.component(context) == component } ?: WidgetKind.CONTRIBUTIONS
            if (kind == WidgetKind.STREAK) hasStreak = true
            runCatching { mgr.updateAppWidget(id, build(context, mgr.getAppWidgetOptions(id), drawn, kind)) }
                .onFailure { Log.e("GHWidget", "Could not render widget $id", it) }
        }
        if (hasStreak && Repository.hasUser) StreakRolloverWorker.schedule(context)
        markDrawn(context, ids)
    }

    /** Current orientation size for callers that need a single preview. */
    fun sizeDp(context: Context, options: Bundle?): SizeF {
        val sizes = exactSizes(options)
        if (sizes.isNotEmpty()) {
            val fallback = legacySize(options, isLandscape(context))
            // OPTION_APPWIDGET_SIZES is unordered; choose by bounds, never list position.
            return sizes.minBy { kotlin.math.abs(it.width - fallback.width) + kotlin.math.abs(it.height - fallback.height) }
        }
        return legacySize(options, isLandscape(context))
    }

    private fun isLandscape(context: Context) =
        context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    /** Android 12+ supplies all sizes the host can use, including foldable states. */
    internal fun exactSizes(options: Bundle?): List<SizeF> {
        if (options == null || android.os.Build.VERSION.SDK_INT < 31) return emptyList()
        @Suppress("DEPRECATION")
        val sizes: ArrayList<SizeF>? = if (android.os.Build.VERSION.SDK_INT >= 33) {
            options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
        } else {
            options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES)
        }
        return sizes.orEmpty().filter {
            it.width.isFinite() && it.height.isFinite() && it.width > 0 && it.height > 0
        }.distinct().take(16) // RemoteViews supports at most 16 size variants.
    }

    /** Width and height bounds belong to opposite orientations, not one rectangle. */
    internal fun legacySize(options: Bundle?, landscape: Boolean): SizeF {
        fun positive(key: String, fallback: Float) =
            options?.getInt(key)?.takeIf { it > 0 }?.toFloat() ?: fallback
        val portraitW = positive(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320f)
        val landscapeH = positive(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 160f)
        val landscapeW = positive(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, portraitW)
        val portraitH = positive(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, landscapeH)
        return if (landscape) SizeF(landscapeW, landscapeH) else SizeF(portraitW, portraitH)
    }

    /**
     * Corrects a launcher that reports raw pixels instead of dp, then caps the
     * result to the display. A widget cannot exceed its screen. Floors at 1dp
     * so degenerate options cannot blank the drawing.
     */
    internal fun normalizeSizeToDisplay(
        w: Float,
        h: Float,
        maxW: Float,
        maxH: Float,
        density: Float,
    ): Pair<Float, Float> {
        val safeDensity = density.takeIf { it.isFinite() && it > 0f } ?: 1f
        val candidateW = if (w > maxW && w / safeDensity <= maxW) w / safeDensity else w
        val candidateH = if (h > maxH && h / safeDensity <= maxH) h / safeDensity else h
        return candidateW.coerceIn(1f, maxOf(1f, maxW)) to
            candidateH.coerceIn(1f, maxOf(1f, maxH))
    }

    /** Use the expected host orientation, not the currently open activity's width. */
    internal fun normalizeHostSize(
        size: SizeF,
        displayWidth: Float,
        displayHeight: Float,
        density: Float,
        landscape: Boolean?,
    ): SizeF {
        val short = minOf(displayWidth, displayHeight)
        val long = maxOf(displayWidth, displayHeight)
        // Unmatched exact sizes may describe a foldable state or another screen
        // orientation. Only reject dimensions exceeding either screen orientation.
        val maxWidth = if (landscape == false) short else long
        val maxHeight = if (landscape == true) short else long
        val (width, height) = normalizeSizeToDisplay(size.width, size.height, maxWidth, maxHeight, density)
        return SizeF(width, height)
    }

    private fun build(context: Context, options: Bundle?, drawn: MutableMap<RenderKey, Bitmap>, kind: WidgetKind): RemoteViews {
        val sizes = exactSizes(options)
        val variantCount = if (sizes.isEmpty()) 2 else sizes.size
        val dm = context.resources.displayMetrics
        val colorVariants = if (Repository.design.value.background == Background.AUTO) 2 else 1
        // All size/color bitmaps share one RemoteViews memory allowance. Leave
        // headroom under the host's 6 * screen pixels budget (ARGB uses 4 bytes).
        val pixelBudget = (dm.widthPixels.toLong() * dm.heightPixels / (variantCount * colorVariants))
            .coerceIn(1L, 1_100_000L).toInt()
        val portrait = legacySize(options, false)
        val landscape = legacySize(options, true)
        fun views(size: SizeF, landscapeMode: Boolean?): RemoteViews {
            val normalized = normalizeHostSize(
                size, dm.widthPixels / dm.density, dm.heightPixels / dm.density, dm.density, landscapeMode,
            )
            if (normalized != size) {
                Log.w("GHWidget", "Corrected launcher bitmap size $size to $normalized; host key retained")
            }
            return buildSize(context, normalized, drawn, pixelBudget, kind)
        }
        return if (android.os.Build.VERSION.SDK_INT >= 31 && sizes.isNotEmpty()) {
            RemoteViews(sizes.associateWith { size ->
                // Legacy bounds are integer dp; exact host dimensions can be fractional.
                fun matches(bounds: SizeF) = kotlin.math.abs(size.width - bounds.width) <= 1f &&
                    kotlin.math.abs(size.height - bounds.height) <= 1f
                val landscapeMode = when {
                    matches(portrait) && matches(landscape) -> isLandscape(context)
                    matches(portrait) -> false
                    matches(landscape) -> true
                    else -> null
                }
                views(size, landscapeMode)
            })
        } else {
            // Android 8–11 hosts select the appropriate variant when rotated.
            RemoteViews(views(landscape, true), views(portrait, false))
        }
    }

    private fun buildSize(
        context: Context,
        size: SizeF,
        drawn: MutableMap<RenderKey, Bitmap>,
        pixelBudget: Int,
        kind: WidgetKind,
    ): RemoteViews {
        val shared = Repository.design.value
        val design = shared.copy(layout = kind.resolvedLayout(shared.layout, shared.contributionLayout))
        val data = Repository.data.value
        val inbox = if (kind == WidgetKind.PR_INBOX) ReviewInbox.cached(context, Repository.username.value) else null
        val placeholder = when {
            kind == WidgetKind.STREAK && Repository.hasUser && Repository.refreshState.value is RefreshState.Failed ->
                WidgetRenderer.Placeholder.FAILED
            data != null -> WidgetRenderer.Placeholder.NONE
            Repository.hasUser -> WidgetRenderer.Placeholder.LOADING
            else -> WidgetRenderer.Placeholder.SIGNED_OUT
        }
        fun render(dark: Boolean) = drawn.getOrPut(RenderKey(design.layout, size.width, size.height, dark, pixelBudget)) {
            WidgetRenderer.render(
                context, design, data, Repository.avatar.value, size.width, size.height, dark, placeholder,
                Repository.numberFont.value, maxPixels = pixelBudget, inbox = inbox,
            )
        }

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
        val latestReview = inbox?.results?.requests?.firstOrNull()
        val desc = if (kind == WidgetKind.STREAK) StreakWidgetPainter.snapshot(data, placeholder).description() else inbox?.let {
            val total = it.results?.total
            when {
                total != null -> "PR Inbox. $total public review requests." +
                    (latestReview?.let { request ->
                        " Open ${request.title}, ${request.repository}." +
                            (request.excerpt?.let { " Description: $it" } ?: "")
                    } ?: "") +
                    if (it.failed) " Showing the last check." else ""
                it.failed -> "PR Inbox. Couldn't check for review requests."
                it.username.isBlank() -> "PR Inbox. Tap to connect a GitHub profile."
                else -> "PR Inbox. Checking for public review requests."
            }
        } ?: data?.let { FocusWidgetPainter.accessibilityText(design, it) }
            ?: "GitHub contributions widget. Tap to set up."
        views.setContentDescription(R.id.widget_image_light, desc)
        views.setContentDescription(R.id.widget_image_dark, desc)

        val open = PendingIntent.getActivity(
            context, 100 + kind.ordinal,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_WIDGET_LAYOUT, design.layout.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openReview = latestReview?.let { request ->
            PendingIntent.getActivity(context, 0,
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(request.url)),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        views.setOnClickPendingIntent(R.id.widget_root, openReview ?: open)
        views.removeAllViews(R.id.widget_connections)
        if (design.layout == WidgetLayout.PROFILE && data != null) {
            val pad = if (size.width < 180 || size.height < 100) 12f else 16f
            val dock = FocusWidgetPainter.profileDock(size.width - 2 * pad, size.height - 2 * pad, design, data)
            views.setViewVisibility(R.id.widget_connections, if (dock.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE)
            val px = (pad * context.resources.displayMetrics.density).toInt()
            val rightPad = (size.width - pad - dock.size * FocusWidgetPainter.PROFILE_LINK_SIZE).coerceAtLeast(pad)
            val bottomPad = pad - FocusWidgetPainter.PROFILE_FOOTER_OFFSET
            views.setViewPadding(R.id.widget_connections, px, 0,
                (rightPad * context.resources.displayMetrics.density).toInt(),
                (bottomPad * context.resources.displayMetrics.density).toInt())
            dock.forEach { item ->
                val button = RemoteViews(context.packageName, R.layout.widget_connection)
                button.setContentDescription(R.id.widget_connection, item.url?.let { "Open ${ProfileIcons.label(it)}: $it" } ?: item.label)
                val action = item.url?.let { url ->
                    PendingIntent.getActivity(context, 0,
                        Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                } ?: open
                button.setOnClickPendingIntent(R.id.widget_connection, action)
                views.addView(R.id.widget_connections, button)
            }
        }


        val refresh = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, ContributionWidgetProvider::class.java).setAction(ContributionWidgetProvider.ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setViewVisibility(R.id.widget_refresh_icon,
            if (kind == WidgetKind.PR_INBOX || kind == WidgetKind.STREAK) android.view.View.INVISIBLE else android.view.View.VISIBLE)
        views.setOnClickPendingIntent(R.id.widget_refresh, refresh)
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
