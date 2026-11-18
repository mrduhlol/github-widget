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

    // Open curves from the sketch. The inner contours enter through the top and turn right.
    private val contours = listOf(
        "M18 28 C8 36 4 46 11 53 C19 62 24 73 22 88 C22 97 17 98 11 100 C3 103 11 113 38 115",
        "M43 19 C27 17 34 33 36 44 C42 56 28 60 32 64 C39 68 47 77 65 79 C84 81 62 89 72 98 C78 104 91 104 97 105",
        "M65 -4 C57 6 53 17 60 25 C65 32 73 33 71 44 C70 56 63 67 73 70 C80 73 87 70 82 79 C80 83 77 91 82 92 C92 94 107 91 121 94",
        "M88 7 C79 14 79 22 84 30 C89 38 96 41 91 48 C88 53 84 60 88 64 C94 70 110 79 119 77 C134 73 139 83 156 83",
        "M114 -3 C102 6 101 20 106 28 C111 33 113 42 109 47 C104 52 115 51 121 52 C129 53 129 60 136 64 C141 69 149 71 154 72",
        "M123 -4 C113 3 112 16 116 24 C120 32 118 39 116 45 C114 50 125 48 131 50 C137 52 138 59 144 62 C150 65 155 65 164 68",
        "M133 -4 C124 3 127 11 129 19 C132 30 121 31 122 36 C122 45 125 47 133 46 C141 44 144 49 146 54 C148 59 155 54 165 60",
        "M143 -3 C135 3 134 12 136 19 C138 27 131 31 131 36 C131 42 141 38 147 42 C152 46 153 49 159 48 C163 47 166 48 171 51",
        "M152 -3 C143 4 142 9 143 16 C144 22 136 27 139 28 C148 27 152 35 158 38 C162 40 166 36 170 37",
        "M161 -3 C155 2 151 8 152 15 C153 21 146 23 153 25 C160 27 164 32 171 29",
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
            strokeCap = Paint.Cap.ROUND
            shader = LinearGradient(bounds.left, bounds.top, bounds.right, bounds.top,
                intArrayOf(color, color or (if (dark) 0x1B000000 else 0x10000000), color or edgeAlpha),
                floatArrayOf(.18f, .40f, 1f), Shader.TileMode.CLAMP)
        }
        val path = Path()
        val clip = canvas.save()
        canvas.clipRect(bounds)
        contours.forEachIndexed { index, contour ->
            contour.transform(transform, path)
            paint.alpha = if (index < 2) 154 else 255
            canvas.drawPath(path, paint)
        }
        canvas.restoreToCount(clip)
    }
}
