package com.example.githubwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
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
    private data class RenderKey(val width: Float, val height: Float, val dark: Boolean, val pixelBudget: Int)

    fun widgetIds(context: Context): IntArray =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, ContributionWidgetProvider::class.java))

    fun hasWidgets(context: Context) = widgetIds(context).isNotEmpty()

    fun updateAll(context: Context) = update(context, widgetIds(context))

    fun update(context: Context, ids: IntArray) {
        if (ids.isEmpty()) return
        Repository.init(context)
        val mgr = AppWidgetManager.getInstance(context)
        // Widgets of the same size look identical, so each bitmap is drawn once per pass.
        // Peggy card/text colors are applied inside WidgetRenderer; graph levels
        // stay on the Palettes ramps.
        val drawn = HashMap<RenderKey, Bitmap>()
        for (id in ids) {
            runCatching { mgr.updateAppWidget(id, build(context, mgr.getAppWidgetOptions(id), drawn)) }
        }
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

    private fun build(context: Context, options: Bundle?, drawn: MutableMap<RenderKey, Bitmap>): RemoteViews {
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
            return buildSize(context, normalized, drawn, pixelBudget)
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
    ): RemoteViews {
        val design = Repository.design.value
        val data = Repository.data.value
        val placeholder = when {
            data != null -> WidgetRenderer.Placeholder.NONE
            Repository.hasUser -> WidgetRenderer.Placeholder.LOADING
            else -> WidgetRenderer.Placeholder.SIGNED_OUT
        }
        fun render(dark: Boolean) = drawn.getOrPut(RenderKey(size.width, size.height, dark, pixelBudget)) {
            WidgetRenderer.render(
                context, design, data, Repository.avatar.value, size.width, size.height, dark, placeholder,
                Repository.numberFont.value, maxPixels = pixelBudget,
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

        val refresh = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, ContributionWidgetProvider::class.java).setAction(ContributionWidgetProvider.ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
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
