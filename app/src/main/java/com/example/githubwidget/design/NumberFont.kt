package com.example.githubwidget.design

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import java.util.concurrent.ConcurrentHashMap

/**
 * The typeface for the app's numbers, the widget text and the share image.
 * Only sans faces, so numbers stay readable at display size. The variable
 * fonts are bundled under assets/fonts and cover every weight from one file.
 */
enum class NumberFont(val label: String, val asset: String?) {
    SYSTEM("System", null),
    INTER("Inter", "fonts/Inter-Variable.ttf"),
    SPACE_GROTESK("Space Grotesk", "fonts/SpaceGrotesk-Variable.ttf"),
    MANROPE("Manrope", "fonts/Manrope-Variable.ttf"),
    OUTFIT("Outfit", "fonts/Outfit-Variable.ttf"),
    FIGTREE("Figtree", "fonts/Figtree-Variable.ttf"),
    PLUS_JAKARTA("Plus Jakarta", "fonts/PlusJakartaSans-Variable.ttf"),
    ;

    companion object {
        fun fromName(name: String?): NumberFont = entries.firstOrNull { it.name == name } ?: SYSTEM

        private val cache = ConcurrentHashMap<Pair<NumberFont, Int>, Typeface>()

        /**
         * A typeface at [weight] (100–900) for canvas drawing. Cached, because
         * the widget asks for the same few faces many times per render. Falls
         * back to the system sans if the asset can't load.
         */
        fun typeface(context: Context, font: NumberFont, weight: Int): Typeface =
            cache.getOrPut(font to weight) { load(context, font, weight) }

        private fun load(context: Context, font: NumberFont, weight: Int): Typeface {
            val asset = font.asset
            if (asset != null) {
                runCatching {
                    return Typeface.Builder(context.assets, asset)
                        .setFontVariationSettings("'wght' $weight")
                        .build()
                }
            }
            return systemTypeface(weight)
        }

        private fun systemTypeface(weight: Int): Typeface {
            val base = Typeface.create("sans-serif", Typeface.NORMAL)
            return if (Build.VERSION.SDK_INT >= 28) {
                Typeface.create(base, weight, false)
            } else if (weight >= 600) {
                Typeface.create(base, Typeface.BOLD)
            } else {
                base
            }
        }
    }
}
