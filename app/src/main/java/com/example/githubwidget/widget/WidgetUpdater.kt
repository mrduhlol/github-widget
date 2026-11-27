package com.example.githubwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.example.githubwidget.MainActivity
import com.example.githubwidget.R
import com.example.githubwidget.data.RefreshState
import com.example.githubwidget.data.Repository
import com.example.githubwidget.data.ReviewInbox
import com.example.githubwidget.data.ReviewRequest
import com.example.githubwidget.design.Background
import com.example.githubwidget.design.WidgetLayout

/** Renders the current design + data into every placed widget. */
object WidgetUpdater {

    private const val PREFS = "gh_widget_ids"
    private const val KEY_DRAWN = "drawn"
    private val renderLock = Any()
    private data class RenderKey(val layout: WidgetLayout, val width: Float, val height: Float, val dark: Boolean, val pixelBudget: Int)

    fun widgetIds(context: Context): IntArray =
        WidgetKind.entries.flatMap { kind ->
            AppWidgetManager.getInstance(context).getAppWidgetIds(kind.component(context)).toList()
        }.toIntArray()

    fun hasWidgets(context: Context) = widgetIds(context).isNotEmpty()

    internal fun streakIds(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(WidgetKind.STREAK.component(context))

    internal fun reviewInboxIds(context: Context): IntArray =
        AppWidgetManager.getInstance(context).let { manager ->
            WidgetKind.entries.filter { it.layout?.isReviewWidget == true }.flatMap { kind ->
                manager.getAppWidgetIds(kind.component(context)).asIterable()
            }.toIntArray()
        }

    fun updateAll(context: Context) = update(context, widgetIds(context))

    fun update(context: Context, ids: IntArray) {
        if (ids.isEmpty()) return
        Repository.init(context)
        val mgr = AppWidgetManager.getInstance(context)
        var hasStreak = false
        var hasUser = false
        // Serialize complete render passes from snapshot capture through AppWidgetManager
        // publication so an older pass can never overwrite a newer account's widget.
        synchronized(renderLock) {
            val account = Repository.snapshot()
            hasUser = account.hasUser
            // Widgets of the same size look identical, so each bitmap is drawn once per pass.
            val drawn = HashMap<RenderKey, Bitmap>()
            for (id in ids) {
                val info = mgr.getAppWidgetInfo(id) ?: continue
                val kind = WidgetKind.entries.firstOrNull { it.component(context) == info.provider } ?: continue
                if (kind == WidgetKind.STREAK) hasStreak = true
                runCatching {
                    val density = context.resources.displayMetrics.density
                    val fallback = SizeF(info.minWidth / density, info.minHeight / density)
                    mgr.updateAppWidget(id, build(context, mgr.getAppWidgetOptions(id), drawn, kind, account, fallback = fallback))
                    markDrawn(context, id)
                }
                    .onFailure {
                        Log.e("GHWidget", "Could not render widget $id", it)
                        // Replace the launcher's initial loading view with a way back into the app.
                        runCatching {
                            val fallback = RemoteViews(context.packageName, R.layout.widget_loading)
                            fallback.setTextViewText(R.id.widget_loading_text, "Couldn't display widget. Tap to open GH Widgets.")
                            fallback.setOnClickPendingIntent(R.id.widget_loading_text, PendingIntent.getActivity(
                                context, 0, Intent(context, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                            ))
                            mgr.updateAppWidget(id, fallback)
                        }.onFailure { fallbackError -> Log.e("GHWidget", "Could not show widget recovery view $id", fallbackError) }
                    }
            }
        }
        if (hasStreak && hasUser) StreakRolloverWorker.schedule(context)
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
    internal fun legacySize(options: Bundle?, landscape: Boolean, fallback: SizeF = SizeF(320f, 160f)): SizeF {
        fun positive(key: String, fallback: Float) =
            options?.getInt(key)?.takeIf { it > 0 }?.toFloat() ?: fallback
        val portraitW = positive(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
            positive(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, fallback.width.takeIf { it > 0f } ?: 320f))
        val landscapeH = positive(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
            positive(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, fallback.height.takeIf { it > 0f } ?: 160f))
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

    private fun usesDualThemeBitmaps(layout: WidgetLayout, background: Background): Boolean =
        background == Background.AUTO && layout != WidgetLayout.STREAK && !layout.hasReviewColors

    private fun build(
        context: Context,
        options: Bundle?,
        drawn: MutableMap<RenderKey, Bitmap>,
        kind: WidgetKind,
        account: Repository.AccountSnapshot,
        fallback: SizeF = SizeF(320f, 160f),
    ): RemoteViews {
        val sizes = exactSizes(options)
        val variantCount = if (sizes.isEmpty()) 2 else sizes.size
        val dm = context.resources.displayMetrics
        val shared = Repository.design.value
        val layout = kind.resolvedLayout(shared.layout, shared.contributionLayout)
        val colorVariants = if (usesDualThemeBitmaps(layout, shared.background)) 2 else 1
        // All size/color bitmaps share one RemoteViews memory allowance. Leave
        // headroom under the host's 6 * screen pixels budget (ARGB uses 4 bytes).
        val screenPixels = dm.widthPixels.toLong() * dm.heightPixels
        val pixelBudget = (screenPixels / (variantCount * colorVariants))
            .coerceIn(1L, 1_100_000L).toInt()
        val portrait = legacySize(options, false, fallback)
        val landscape = legacySize(options, true, fallback)
        fun views(size: SizeF, landscapeMode: Boolean?): RemoteViews {
            val normalized = normalizeHostSize(
                size, dm.widthPixels / dm.density, dm.heightPixels / dm.density, dm.density, landscapeMode,
            )
            if (normalized != size) {
                Log.w("GHWidget", "Corrected launcher bitmap size $size to $normalized; host key retained")
            }
            return buildSize(context, normalized, drawn, pixelBudget, kind, account)
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
        account: Repository.AccountSnapshot,
    ): RemoteViews {
        val shared = Repository.design.value
        val design = shared.copy(layout = kind.resolvedLayout(shared.layout, shared.contributionLayout))
        val data = account.data
        val inbox = if (design.layout.isReviewWidget) ReviewInbox.cached(context, account.username) else null
        val placeholder = when {
            (data == null || kind == WidgetKind.STREAK) && account.hasUser && account.refreshState is RefreshState.Failed ->
                WidgetRenderer.Placeholder.FAILED
            data != null -> WidgetRenderer.Placeholder.NONE
            account.hasUser -> WidgetRenderer.Placeholder.LOADING
            else -> WidgetRenderer.Placeholder.SIGNED_OUT
        }
        fun render(dark: Boolean) = drawn.getOrPut(RenderKey(design.layout, size.width, size.height, dark, pixelBudget)) {
            WidgetRenderer.render(
                context, design, data, account.avatar, size.width, size.height, dark, placeholder,
                Repository.numberFont.value, maxPixels = pixelBudget, inbox = inbox,
            )
        }

        val dualTheme = usesDualThemeBitmaps(design.layout, design.background)
        val views = RemoteViews(
            context.packageName,
            if (dualTheme) R.layout.widget_root else R.layout.widget_root_single,
        )
        // Only send two bitmaps when Auto background needs distinct light and dark renders.
        if (dualTheme) {
            views.setImageViewBitmap(R.id.widget_image_light, render(dark = false))
            views.setImageViewBitmap(R.id.widget_image_dark, render(dark = true))
        } else {
            views.setImageViewBitmap(R.id.widget_image_light, render(dark = true))
        }
        val queueRows = if (kind == WidgetKind.REVIEW_QUEUE) ReviewCardPainter.queueRows(inbox, size.height) else emptyList()
        val latestReview = when (kind) {
            WidgetKind.REVIEW_NEXT -> ReviewCardPainter.orderedRequests(inbox).firstOrNull()
            WidgetKind.REVIEW_QUEUE -> null
            else -> inbox?.results?.requests?.firstOrNull()
        }
        val reviewLabel = design.layout.label
        val desc = if (kind == WidgetKind.STREAK) StreakWidgetPainter.snapshot(data, placeholder).description() else inbox?.let {
            val total = it.results?.total
            when {
                total != null -> "$reviewLabel. $total public review requests." +
                    (latestReview?.let { request ->
                        " Open ${request.title}, ${request.repository}." +
                            (request.excerpt?.let { " Description: $it" } ?: "")
                    } ?: "") +
                    if (it.failed) " Showing the last check." else ""
                it.failed -> "$reviewLabel. Couldn't check for public review requests."
                it.username.isBlank() -> "$reviewLabel. Tap to connect a GitHub profile."
                else -> "$reviewLabel. Checking for public review requests."
            }
        } ?: data?.let { FocusWidgetPainter.accessibilityText(design, it) } ?: when (placeholder) {
            WidgetRenderer.Placeholder.FAILED -> "${design.layout.label}. Couldn't load GitHub activity."
            WidgetRenderer.Placeholder.LOADING -> "${design.layout.label}. Loading GitHub activity."
            else -> "${design.layout.label}. Tap to set up."
        }
        val openStats = kind == WidgetKind.CONTRIBUTIONS && data != null
        val contentDescription = if (openStats) {
            "$desc ${context.getString(R.string.widget_contributions_stats_hint)}"
        } else {
            desc
        }
        views.setContentDescription(R.id.widget_image_light, contentDescription)
        if (dualTheme) views.setContentDescription(R.id.widget_image_dark, contentDescription)

        val open = PendingIntent.getActivity(
            context, 100 + kind.ordinal,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_WIDGET_LAYOUT, design.layout.name)
                .putExtra(MainActivity.EXTRA_WIDGET_PORTRAIT, kind == WidgetKind.PORTRAIT_PROFILE)
                .putExtra(MainActivity.EXTRA_WIDGET_STATS, openStats)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        fun openReview(request: ReviewRequest): PendingIntent = PendingIntent.getActivity(
            context, 0, Intent(Intent.ACTION_VIEW, android.net.Uri.parse(request.url)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_root, latestReview?.let(::openReview) ?: open)
        if (kind == WidgetKind.REVIEW_QUEUE) {
            views.removeAllViews(R.id.widget_review_rows)
            views.setViewVisibility(R.id.widget_review_rows, if (queueRows.isEmpty()) View.GONE else View.VISIBLE)
            if (queueRows.isNotEmpty()) {
                // The painter and the overlay both give the header and each row equal height.
                views.addView(R.id.widget_review_rows, RemoteViews(context.packageName, R.layout.widget_review_row))
                queueRows.forEach { request ->
                    val row = RemoteViews(context.packageName, R.layout.widget_review_row)
                    row.setContentDescription(R.id.widget_review_row, "Open ${request.title}, ${request.repository} #${request.number}")
                    row.setOnClickPendingIntent(R.id.widget_review_row, openReview(request))
                    views.addView(R.id.widget_review_rows, row)
                }
            }
        }
        views.removeAllViews(R.id.widget_connections)
        if (design.layout == WidgetLayout.PROFILE && data != null) {
            val pad = WidgetRenderer.contentPaddingDp(size.width, size.height)
            val dock = FocusWidgetPainter.profileDock(size.width - 2 * pad, size.height - 2 * pad, design, data)
            views.setViewVisibility(R.id.widget_connections, if (dock.isEmpty()) View.GONE else View.VISIBLE)
            val px = (pad * context.resources.displayMetrics.density).toInt()
            val bottomPad = (pad - FocusWidgetPainter.PROFILE_FOOTER_OFFSET).coerceAtLeast(0f)
            views.setViewPadding(R.id.widget_connections, px, 0, px,
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
        return views
    }

    private fun markDrawn(context: Context, id: Int) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = p.getStringSet(KEY_DRAWN, emptySet()).orEmpty()
        val key = id.toString()
        if (key !in set) p.edit().putStringSet(KEY_DRAWN, set + key).apply()
    }

    /** True if this widget has been drawn before, i.e. the user is re-configuring it. */
    fun wasDrawn(context: Context, id: Int): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_DRAWN, emptySet())!!.contains(id.toString())

    fun forget(context: Context, ids: IntArray) {
        synchronized(renderLock) {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val set = p.getStringSet(KEY_DRAWN, emptySet())!!.toMutableSet()
            if (set.removeAll(ids.map { it.toString() }.toSet())) p.edit().putStringSet(KEY_DRAWN, set).apply()
        }
    }
}
