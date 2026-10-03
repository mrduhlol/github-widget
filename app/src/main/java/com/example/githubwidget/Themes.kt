package com.example.githubwidget

import android.graphics.Color

/**
 * Graph color themes. levels[0] is the "empty day" cell, levels[1..4] the
 * intensity ramp. accent tints the total counter + app highlights.
 */
data class GraphTheme(
    val id: String,
    val name: String,
    val accent: Int,
    val levels: IntArray
)

object Themes {

    val GREEN = GraphTheme(
        "green", "Matrix",
        Color.parseColor("#39D353"),
        intArrayOf(
            Color.parseColor("#161B22"),
            Color.parseColor("#0E4429"),
            Color.parseColor("#006D32"),
            Color.parseColor("#26A641"),
            Color.parseColor("#39D353")
        )
    )

    val PURPLE = GraphTheme(
        "purple", "Nebula",
        Color.parseColor("#A371F7"),
        intArrayOf(
            Color.parseColor("#161B22"),
            Color.parseColor("#241A4B"),
            Color.parseColor("#432C93"),
            Color.parseColor("#6E40C9"),
            Color.parseColor("#A371F7")
        )
    )

    val BLUE = GraphTheme(
        "blue", "Abyss",
        Color.parseColor("#58A6FF"),
        intArrayOf(
            Color.parseColor("#161B22"),
            Color.parseColor("#0A2A5E"),
            Color.parseColor("#0B54C4"),
            Color.parseColor("#2F81F7"),
            Color.parseColor("#79C0FF")
        )
    )

    val ORANGE = GraphTheme(
        "orange", "Ember",
        Color.parseColor("#F0883E"),
        intArrayOf(
            Color.parseColor("#161B22"),
            Color.parseColor("#4D1A08"),
            Color.parseColor("#9E2F1C"),
            Color.parseColor("#E85D26"),
            Color.parseColor("#FF9E69")
        )
    )

    val ROSE = GraphTheme(
        "rose", "Neon",
        Color.parseColor("#F778BA"),
        intArrayOf(
            Color.parseColor("#161B22"),
            Color.parseColor("#4E0D30"),
            Color.parseColor("#A40E4C"),
            Color.parseColor("#E85A9B"),
            Color.parseColor("#FF9ECF")
        )
    )

    val ALL = listOf(GREEN, PURPLE, BLUE, ORANGE, ROSE)

    const val CUSTOM_ID = "custom"

    fun get(id: String?): GraphTheme = ALL.find { it.id == id } ?: GREEN

    /** Resolve the active theme, building a ramp for a user-picked custom color. */
    fun resolve(themeId: String?, customColor: Int): GraphTheme =
        if (themeId == CUSTOM_ID) fromBaseColor(customColor) else get(themeId)

    /**
     * Builds a 4-step intensity ramp from any base color by sweeping
     * brightness (V in HSV), keeping hue/saturation. Accent = base color.
     */
    fun fromBaseColor(base: Int): GraphTheme {
        val hsv = FloatArray(3)
        Color.colorToHSV(base, hsv)
        fun level(v: Float): Int {
            val c = hsv.clone()
            c[1] = (hsv[1] * 0.95f).coerceIn(0f, 1f)
            c[2] = v.coerceIn(0f, 1f)
            return Color.HSVToColor(c)
        }
        return GraphTheme(
            CUSTOM_ID, "Custom", base,
            intArrayOf(
                Color.parseColor("#161B22"),
                level(0.30f),
                level(0.50f),
                level(0.70f),
                level(0.92f)
            )
        )
    }

    fun toHex(color: Int): String =
        String.format("#%06X", 0xFFFFFF and color)

    /** Base card color #0D1117 with the given opacity (0..100). */
    fun cardColor(opacity: Int): Int {
        val a = (opacity.coerceIn(20, 100) * 255 / 100)
        return Color.argb(a, 0x0D, 0x11, 0x17)
    }
}
