package com.example.githubwidget.design

import android.content.Context
import android.os.Build
import androidx.core.graphics.ColorUtils

/**
 * A graph color scheme. Each palette is one accent color; the 5-step ramp is
 * derived against the card background so it reads well on dark *and* light.
 * GitHub green uses GitHub's own hand-tuned ramps.
 */
data class Palette(
    val id: String,
    val name: String,
    val accent: Int,
    /** Optional hand-tuned levels 1..4 for dark and light cards. */
    val darkRamp: IntArray? = null,
    val lightRamp: IntArray? = null,
)

/** Resolved colors for one render: card, text and the 5 graph levels. */
data class ColorSet(
    val card: Int,
    val border: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val accent: Int,
    /** levels[0] = no contributions, levels[4] = busiest days. */
    val levels: IntArray,
    val isDark: Boolean,
)

object Palettes {
    const val DEFAULT_ID = "github"
    const val CUSTOM_ID = "custom"
    const val WALLPAPER_ID = "wallpaper"

    private fun c(hex: Long) = hex.toInt()

    val presets = listOf(
        Palette(
            "github", "GitHub", c(0xFF39D353),
            darkRamp = intArrayOf(c(0xFF0E4429), c(0xFF006D32), c(0xFF26A641), c(0xFF39D353)),
            lightRamp = intArrayOf(c(0xFF9BE9A8), c(0xFF40C463), c(0xFF30A14E), c(0xFF216E39)),
        ),
        Palette("ocean", "Ocean", c(0xFF388BFD)),
        Palette("violet", "Violet", c(0xFFA371F7)),
        Palette("rose", "Rose", c(0xFFF778BA)),
        Palette("sunset", "Sunset", c(0xFFFB8F44)),
        Palette("gold", "Gold", c(0xFFE3B341)),
        Palette("teal", "Teal", c(0xFF2DD4BF)),
        Palette("crimson", "Crimson", c(0xFFF85149)),
        Palette("mono", "Mono", c(0xFF9AA4B2)),
    )

    /** "Match wallpaper" uses Android 12+ dynamic colors. */
    val supportsWallpaper: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun palette(context: Context, design: WidgetDesign, dark: Boolean): Palette = when (design.paletteId) {
        CUSTOM_ID -> Palette(CUSTOM_ID, "Custom", design.customColor)
        WALLPAPER_ID -> wallpaper(context, dark) ?: presets.first()
        else -> presets.firstOrNull { it.id == design.paletteId } ?: presets.first()
    }

    private fun wallpaper(context: Context, dark: Boolean): Palette? {
        if (!supportsWallpaper) return null
        val res = if (dark) android.R.color.system_accent1_300 else android.R.color.system_accent1_600
        return Palette(WALLPAPER_ID, "Wallpaper", context.getColor(res))
    }

    /** Resolves every color for one render of [design] on a [dark] or light card. */
    fun colors(context: Context, design: WidgetDesign, dark: Boolean): ColorSet {
        val card = when {
            design.background == Background.BLACK -> c(0xFF000000)
            dark -> c(0xFF0D1117)
            else -> c(0xFFFFFFFF)
        }
        val see = design.opacity < 45
        val textPrimary = if (dark) c(0xFFF0F6FC) else c(0xFF1F2328)
        val textSecondary = if (dark) c(0xFF9198A1) else c(0xFF59636E)
        // On a see-through card the empty squares must still be visible on any wallpaper.
        val empty = when {
            see && dark -> 0x33FFFFFF
            see -> 0x26000000
            design.background == Background.BLACK -> c(0xFF161B22)
            dark -> c(0xFF151B23)
            else -> c(0xFFEFF2F5)
        }
        val p = palette(context, design, dark)
        val ramp = (if (dark) p.darkRamp else p.lightRamp)?.takeUnless { see }
            ?: generateRamp(p.accent, if (see) card else empty, dark)
        val accent = if (p.id == DEFAULT_ID && !dark) c(0xFF1A7F37) else p.accent
        return ColorSet(
            card = card,
            border = if (dark) 0x1FFFFFFF else 0x1A1F2328,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            accent = accent,
            levels = intArrayOf(empty, ramp[0], ramp[1], ramp[2], ramp[3]),
            isDark = dark,
        )
    }

    /** Four steps from faint to full accent, blended over the card color. */
    fun generateRamp(accent: Int, base: Int, dark: Boolean): IntArray {
        val solidBase = ColorUtils.setAlphaComponent(base, 255)
        val steps = if (dark) floatArrayOf(0.30f, 0.52f, 0.76f, 1f) else floatArrayOf(0.35f, 0.58f, 0.80f, 1f)
        return IntArray(4) { i ->
            val top = if (!dark && i == 3) ColorUtils.blendARGB(accent, 0xFF000000.toInt(), 0.18f) else accent
            ColorUtils.blendARGB(solidBase, top, steps[i])
        }
    }
}
