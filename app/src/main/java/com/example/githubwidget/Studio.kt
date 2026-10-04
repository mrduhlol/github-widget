package com.example.githubwidget

import android.graphics.Color
import org.json.JSONObject

/**
 * V2 Studio: backgrounds, palettes, text and look import/export.
 *
 * Background settings are GLOBAL (shared by every widget) — per-widget
 * files would explode with image URIs, so this is a deliberate limit.
 * Username + cached data live elsewhere and are never touched here.
 */
object Studio {

    private const val NAME = "gh_studio"

    // Background types
    const val BG_SOLID = "solid"
    const val BG_GRADIENT = "gradient"
    const val BG_IMAGE = "image"
    const val BG_TRANSPARENT = "transparent"

    const val SCALE_FILL = "fill"
    const val SCALE_FIT = "fit"

    private const val KEY_BG_TYPE = "bg_type"
    private const val KEY_BG_COLOR = "bg_color"
    private const val KEY_GRAD_2 = "grad_2"
    private const val KEY_GRAD_3 = "grad_3"
    private const val KEY_GRAD_RADIAL = "grad_radial"
    private const val KEY_GRAD_ANGLE = "grad_angle"
    private const val KEY_IMAGE_URI = "image_uri"
    private const val KEY_IMAGE_SCALE = "image_scale"
    private const val KEY_BG_OPACITY = "bg_opacity"
    private const val KEY_OVERLAY = "overlay"
    private const val KEY_OVERLAY_OPACITY = "overlay_opacity"
    private const val KEY_BLUR = "blur"
    private const val KEY_IMAGE_GRAPH = "image_graph"
    private const val KEY_LEVELS = "levels"
    private const val KEY_CELL_SIZE = "cell_size"
    private const val KEY_TEXT_SIZE = "text_size"
    private const val KEY_CUSTOM_LABEL = "custom_label"
    private const val KEY_SHOW_ACTIVE = "show_active"

    private fun prefs(context: android.content.Context) =
        context.getSharedPreferences(NAME, android.content.Context.MODE_PRIVATE)

    // ---------- background ----------

    fun getBgType(context: android.content.Context): String =
        prefs(context).getString(KEY_BG_TYPE, BG_SOLID) ?: BG_SOLID

    fun setBgType(context: android.content.Context, type: String) {
        prefs(context).edit().putString(KEY_BG_TYPE, type).apply()
    }

    fun getBgColor(context: android.content.Context): Int =
        prefs(context).getInt(KEY_BG_COLOR, Color.parseColor("#0D1117"))

    fun setBgColor(context: android.content.Context, color: Int) {
        prefs(context).edit().putInt(KEY_BG_COLOR, color).apply()
    }

    fun getGrad2(context: android.content.Context): Int =
        prefs(context).getInt(KEY_GRAD_2, Color.parseColor("#1F6FEB"))

    fun setGrad2(context: android.content.Context, color: Int) {
        prefs(context).edit().putInt(KEY_GRAD_2, color).apply()
    }

    /** Third gradient stop, or null when the gradient uses two colors. */
    fun getGrad3(context: android.content.Context): Int? {
        val p = prefs(context)
        return if (p.contains(KEY_GRAD_3)) p.getInt(KEY_GRAD_3, 0) else null
    }

    fun setGrad3(context: android.content.Context, color: Int?) {
        val e = prefs(context).edit()
        if (color == null) e.remove(KEY_GRAD_3) else e.putInt(KEY_GRAD_3, color)
        e.apply()
    }

    fun isGradRadial(context: android.content.Context): Boolean =
        prefs(context).getBoolean(KEY_GRAD_RADIAL, false)

    fun setGradRadial(context: android.content.Context, radial: Boolean) {
        prefs(context).edit().putBoolean(KEY_GRAD_RADIAL, radial).apply()
    }

    /** Linear gradient direction in degrees (0, 45, 90, 135). */
    fun getGradAngle(context: android.content.Context): Int =
        prefs(context).getInt(KEY_GRAD_ANGLE, 135)

    fun setGradAngle(context: android.content.Context, angle: Int) {
        prefs(context).edit().putInt(KEY_GRAD_ANGLE, angle).apply()
    }

    fun getImageUri(context: android.content.Context): String =
        prefs(context).getString(KEY_IMAGE_URI, "") ?: ""

    fun setImageUri(context: android.content.Context, uri: String) {
        prefs(context).edit().putString(KEY_IMAGE_URI, uri).apply()
    }

    fun getImageScale(context: android.content.Context): String =
        prefs(context).getString(KEY_IMAGE_SCALE, SCALE_FILL) ?: SCALE_FILL

    fun setImageScale(context: android.content.Context, mode: String) {
        prefs(context).edit().putString(KEY_IMAGE_SCALE, mode).apply()
    }

    /**
     * Background-layer opacity 0..100. Independent from graph and text.
     * Migrates from the V1 card opacity so updaters keep their look.
     */
    fun getBgOpacity(context: android.content.Context): Int {
        val p = prefs(context)
        return if (p.contains(KEY_BG_OPACITY)) {
            p.getInt(KEY_BG_OPACITY, 100).coerceIn(0, 100)
        } else {
            Prefs.getOpacity(context)
        }
    }

    fun setBgOpacity(context: android.content.Context, opacity: Int) {
        prefs(context).edit().putInt(KEY_BG_OPACITY, opacity.coerceIn(0, 100)).apply()
    }

    fun getOverlay(context: android.content.Context): Int =
        prefs(context).getInt(KEY_OVERLAY, Color.BLACK)

    fun setOverlay(context: android.content.Context, color: Int) {
        prefs(context).edit().putInt(KEY_OVERLAY, color).apply()
    }

    /** Overlay opacity 0 (off) .. 80. */
    fun getOverlayOpacity(context: android.content.Context): Int =
        prefs(context).getInt(KEY_OVERLAY_OPACITY, 0).coerceIn(0, 80)

    fun setOverlayOpacity(context: android.content.Context, opacity: Int) {
        prefs(context).edit().putInt(KEY_OVERLAY_OPACITY, opacity.coerceIn(0, 80)).apply()
    }

    /** Background blur strength 0 (off) .. 25. */
    fun getBlur(context: android.content.Context): Int =
        prefs(context).getInt(KEY_BLUR, 0).coerceIn(0, 25)

    fun setBlur(context: android.content.Context, blur: Int) {
        prefs(context).edit().putInt(KEY_BLUR, blur.coerceIn(0, 25)).apply()
    }

    fun imageThroughGraph(context: android.content.Context): Boolean =
        prefs(context).getBoolean(KEY_IMAGE_GRAPH, false)

    fun setImageThroughGraph(context: android.content.Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_IMAGE_GRAPH, enabled).apply()
    }

    // ---------- palette ----------

    /**
     * Custom 5-level palette (empty/low/medium/high/max) or null to follow
     * the active theme. Saved as a comma-separated int list.
     */
    fun getLevels(context: android.content.Context): IntArray? {
        val raw = prefs(context).getString(KEY_LEVELS, null) ?: return null
        return try {
            val parts = raw.split(",").map { it.toInt() }
            if (parts.size == 5) parts.toIntArray() else null
        } catch (_: Exception) {
            null
        }
    }

    fun setLevels(context: android.content.Context, levels: IntArray?) {
        val e = prefs(context).edit()
        if (levels == null) e.remove(KEY_LEVELS)
        else e.putString(KEY_LEVELS, levels.joinToString(","))
        e.apply()
    }

    fun effectiveLevels(context: android.content.Context, theme: GraphTheme): IntArray =
        getLevels(context) ?: theme.levels

    // ---------- text & content ----------

    /** 0 = compact, 1 = normal, 2 = large. */
    fun getTextSize(context: android.content.Context): Int =
        prefs(context).getInt(KEY_TEXT_SIZE, 1).coerceIn(0, 2)

    fun setTextSize(context: android.content.Context, size: Int) {
        prefs(context).edit().putInt(KEY_TEXT_SIZE, size.coerceIn(0, 2)).apply()
    }

    fun textScale(size: Int): Float = when (size) {
        0 -> 0.85f
        2 -> 1.15f
        else -> 1f
    }

    fun getCustomLabel(context: android.content.Context): String =
        prefs(context).getString(KEY_CUSTOM_LABEL, "") ?: ""

    fun setCustomLabel(context: android.content.Context, label: String) {
        prefs(context).edit().putString(KEY_CUSTOM_LABEL, label.trim().take(48)).apply()
    }

    fun showActiveDays(context: android.content.Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_ACTIVE, false)

    fun setShowActiveDays(context: android.content.Context, show: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_ACTIVE, show).apply()
    }

    // ---------- reset (look only) ----------

    fun resetBackground(context: android.content.Context) {
        prefs(context).edit()
            .remove(KEY_BG_TYPE).remove(KEY_BG_COLOR)
            .remove(KEY_GRAD_2).remove(KEY_GRAD_3)
            .remove(KEY_GRAD_RADIAL).remove(KEY_GRAD_ANGLE)
            .remove(KEY_IMAGE_URI).remove(KEY_IMAGE_SCALE)
            .remove(KEY_BG_OPACITY)
            .remove(KEY_OVERLAY).remove(KEY_OVERLAY_OPACITY)
            .remove(KEY_BLUR).remove(KEY_IMAGE_GRAPH)
            .apply()
    }

    fun resetGraph(context: android.content.Context) {
        prefs(context).edit().remove(KEY_LEVELS).apply()
        WidgetPrefs.setShape(context, WidgetPrefs.SHAPE_ROUNDED)
        WidgetPrefs.setSpacing(context, 1f)
        WidgetPrefs.setRange(context, WidgetPrefs.RANGE_6M)
    }

    // ---------- import / export (look only, never username/cache) ----------

    fun exportLook(context: android.content.Context): String {
        val look = JSONObject()
        look.put("theme", Prefs.getThemeId(context))
        look.put("customColor", Prefs.getCustomColor(context))
        look.put("opacity", Prefs.getOpacity(context))
        look.put("style", WidgetPrefs.getStyle(context))
        look.put("range", WidgetPrefs.getRange(context))
        look.put("shape", WidgetPrefs.getShape(context))
        look.put("spacing", WidgetPrefs.getSpacing(context).toDouble())
        look.put("corners", WidgetPrefs.getCorners(context))
        look.put("showTotal", WidgetPrefs.showTotal(context))
        look.put("showStreak", WidgetPrefs.showStreak(context))
        look.put("showLongest", WidgetPrefs.showLongest(context))
        look.put("showUpdated", WidgetPrefs.showUpdated(context))
        val p = prefs(context)
        for (k in p.all.keys) {
            val v = p.all[k]
            when (v) {
                is Int -> look.put("studio_$k", v)
                is Boolean -> look.put("studio_$k", v)
                is String -> look.put("studio_$k", v)
                is Float -> look.put("studio_$k", v.toDouble())
            }
        }
        return JSONObject().put("gh_widgets_look", 1).put("look", look).toString()
    }

    /** Returns null on success, or a short error message. */
    fun importLook(context: android.content.Context, json: String): String? {
        return try {
            val look = JSONObject(json).getJSONObject("look")
            Prefs.setThemeId(context, look.optString("theme", "green"))
            Prefs.setCustomColor(context, look.optInt("customColor", Prefs.getCustomColor(context)))
            Prefs.setOpacity(context, look.optInt("opacity", 100))
            WidgetPrefs.setStyle(context, look.optString("style", WidgetPrefs.STYLE_CLASSIC))
            WidgetPrefs.setRange(context, look.optString("range", WidgetPrefs.RANGE_6M))
            WidgetPrefs.setShape(context, look.optString("shape", WidgetPrefs.SHAPE_ROUNDED))
            WidgetPrefs.setSpacing(context, look.optDouble("spacing", 1.0).toFloat())
            WidgetPrefs.setCorners(context, look.optInt("corners", 16))
            WidgetPrefs.setContent(
                context,
                look.optBoolean("showTotal", true),
                look.optBoolean("showStreak", true),
                look.optBoolean("showLongest", false),
                look.optBoolean("showUpdated", true)
            )
            val e = prefs(context).edit()
            val keys = look.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (!k.startsWith("studio_")) continue
                val name = k.removePrefix("studio_")
                val v = look.get(k)
                when (v) {
                    is Int -> e.putInt(name, v)
                    is Boolean -> e.putBoolean(name, v)
                    is String -> e.putString(name, v)
                    is Double -> e.putFloat(name, v.toFloat())
                }
            }
            e.apply()
            null
        } catch (_: Exception) {
            "That file is not a GH-widgets look."
        }
    }
}
