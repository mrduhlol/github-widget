package com.example.githubwidget.widget

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.PathParser
import kotlin.random.Random

internal object ProfileTexture {
    private const val CONTOUR_WIDTH = 160f
    private const val CONTOUR_HEIGHT = 128f

    // Separate ridges and saddles, with their open ends beyond the right edge of the card.
    private val contours = listOf(
        "M16 2 C48 2 65 3 77 18 S89 45 108 47 C128 50 140 31 169 34",
        "M9 8 C42 6 65 12 75 29 C87 49 99 56 115 55 S145 37 169 41",
        "M3 16 C34 13 61 17 73 36 C88 56 102 66 119 62 S146 46 169 49",
        "M4 24 C34 19 54 23 70 43 C88 63 104 74 123 69 C140 64 151 53 169 57",
        "M10 32 C35 25 54 31 65 47 C79 65 98 81 119 78 C137 76 148 62 169 65",
        "M-3 98 C25 98 32 61 57 59 C79 57 86 86 111 87 C134 90 148 70 169 73",
        "M-2 108 C22 104 39 70 58 69 C76 67 83 93 110 96 C136 100 149 79 169 82",
        "M11 119 C31 105 43 80 57 79 C74 77 79 100 101 105 C130 112 148 91 169 92",
        "M26 131 C39 113 44 93 58 90 C71 88 74 111 96 119 C125 128 145 101 169 102",
        "M42 135 C47 116 47 104 58 102 C67 100 70 117 83 125 C98 137 124 130 137 122 S155 110 169 112",
        "M54 132 C50 124 52 113 58 112 C63 111 66 122 69 128 C72 136 62 140 54 132 Z",
        "M119 136 C136 133 146 116 168 120",
    ).map { requireNotNull(PathParser.createPathFromPathData(it)) }

    private val grain by lazy {
        val size = 128
        val random = Random(42)
        val pixels = IntArray(size * size) {
            val tone = random.nextInt(-10, 11)
            if (tone < 0) Color.argb(-tone, 0, 0, 0) else Color.argb(tone, 255, 255, 255)
        }
        Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    fun drawSurface(canvas: Canvas, bounds: RectF, base: Int, dark: Boolean, opacity: Int, scale: Float) {
        // Composite the material as one surface so the opacity setting also fades the grain.
        val layer = if (opacity < 100) canvas.saveLayerAlpha(bounds, opacity * 255 / 100) else canvas.save()
        val highlight = ColorUtils.blendARGB(base, Color.WHITE, if (dark) .035f else .25f)
        val shadow = ColorUtils.blendARGB(base, Color.BLACK, if (dark) .34f else .023f)
        val paint = Paint().apply {
            shader = LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                intArrayOf(highlight, base, shadow), floatArrayOf(0f, .46f, 1f), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(bounds, paint)

        val grainScale = (scale * .66f).coerceAtLeast(1f)
        paint.shader = BitmapShader(grain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            setLocalMatrix(Matrix().apply { setScale(grainScale, grainScale) })
        }
        paint.alpha = if (dark) 175 else 109
        canvas.drawRect(bounds, paint)
        canvas.restoreToCount(layer)
    }

    fun drawContours(canvas: Canvas, bounds: RectF, ink: Int, dark: Boolean, scale: Float) {
        val transform = Matrix().apply {
            setScale(bounds.width() / CONTOUR_WIDTH, bounds.height() / CONTOUR_HEIGHT)
            postTranslate(bounds.left, bounds.top)
        }
        val color = ink and 0x00FFFFFF
        val edgeAlpha = if (dark) 0x3C000000 else 0x24000000
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = .7f * scale
            shader = LinearGradient(bounds.left, bounds.top, bounds.right, bounds.top,
                intArrayOf(color, color or 0x08000000, color or edgeAlpha),
                floatArrayOf(0f, .36f, 1f), Shader.TileMode.CLAMP)
        }
        val path = Path()
        val clip = canvas.save()
        canvas.clipRect(bounds)
        contours.forEachIndexed { index, contour ->
            contour.transform(transform, path)
            paint.alpha = if (index in 3..8) 255 else 146
            canvas.drawPath(path, paint)
        }
        canvas.restoreToCount(clip)
    }
}
