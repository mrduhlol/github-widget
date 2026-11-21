package com.example.githubwidget.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import androidx.core.graphics.ColorUtils
import com.example.githubwidget.design.NumberFont
import com.example.githubwidget.design.WidgetDesign
import java.text.NumberFormat

/** Aurora uses the same calendar snapshot as the rest of the streak widget. */
internal object AuroraStreakPainter {
    fun draw(canvas: Canvas, scale: Float, context: Context, design: WidgetDesign,
             font: NumberFont, snapshot: StreakWidgetPainter.Snapshot) {
        val save = canvas.save()
        canvas.scale(scale, scale)
        val width = canvas.width / scale
        val height = canvas.height / scale
        val active = snapshot.state == StreakWidgetPainter.State.ACTIVE
        val accent = if (active) design.auroraColor.accent else 0xFFADBACC.toInt()
        val primary = 0xFFEDF4FB.toInt()
        val muted = 0xFFABB8C7.toInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val bounds = RectF(0f, 0f, width, height)
        val radius = (height * .16f).coerceAtMost(18f)
        if (design.opacity > 0) {
            val material = canvas.saveLayerAlpha(bounds, design.opacity * 255 / 100)
            canvas.clipPath(Path().apply { addRoundRect(bounds, radius, radius, Path.Direction.CW) })
            paint.shader = LinearGradient(0f, 0f, width, height,
                intArrayOf(0xFF19282F.toInt(), 0xFF0D171B.toInt(), 0xFF14222B.toInt()),
                floatArrayOf(0f, .58f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(bounds, paint)
            paint.shader = RadialGradient(width, height * 1.04f, height * .68f,
                ColorUtils.setAlphaComponent(accent, 155), ColorUtils.setAlphaComponent(accent, 0),
                Shader.TileMode.CLAMP)
            for (start in listOf(.42f, .68f)) {
                val wave = Path().apply {
                    moveTo(width * start, height)
                    cubicTo(width * .84f, height, width * .94f, height * .75f, width, height * start)
                    lineTo(width, height)
                    close()
                }
                canvas.drawPath(wave, paint)
            }
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            paint.color = ColorUtils.blendARGB(0xFF2D3B46.toInt(), accent, .29f)
            canvas.drawRoundRect(RectF(.5f, .5f, width - .5f, height - .5f), radius, radius, paint)
            paint.style = Paint.Style.FILL
            canvas.restoreToCount(material)
        }

        val text = TextPaint(Paint.ANTI_ALIAS_FLAG)
        fun line(value: String, x: Float, y: Float, size: Float, available: Float, color: Int,
                 face: Typeface = Typeface.DEFAULT, center: Boolean = false): Float {
            if (available <= 0f) return 0f
            text.typeface = face
            text.textSize = size
            text.color = color
            val measured = text.measureText(value)
            if (measured > available) text.textSize *= available / measured
            val actual = text.measureText(value)
            canvas.drawText(value, if (center) x - actual / 2f else x, y, text)
            return actual
        }

        val digits = snapshot.count?.let {
            NumberFormat.getIntegerInstance().format(it) + if (snapshot.lowerBound) "+" else ""
        } ?: "—"
        val countFont = NumberFont.typeface(context, font, 700)
        val status = when (snapshot.state) {
            StreakWidgetPainter.State.ACTIVE -> "Active today"
            StreakWidgetPainter.State.PENDING -> if (height < 80f) "Pending" else "No contribution today"
            StreakWidgetPainter.State.BROKEN -> "Streak ended"
            else -> snapshot.state.label
        }
        val compact = height < 100f
        val wide = width >= 230f && !compact
        val x = if (wide) width * .32f else width / 2f
        val countY = if (compact) height * .65f else height * .64f
        if (wide) {
            ProfileIcons.draw(canvas, "https://github.com", width * .14f, height * .5f, 38f, primary)
            paint.color = 0xFF344650.toInt()
            paint.strokeWidth = 1f
            canvas.drawLine(width * .25f, height * .23f, width * .25f, height * .77f, paint)
            line("GitHub streak", x, height * .29f, 14f, width - x - 24f, muted, Typeface.DEFAULT_BOLD)
        } else {
            ProfileIcons.draw(canvas, "https://github.com", x, height * .22f, if (compact) 12f else 28f, primary)
        }
        val unit = if (snapshot.count == 1) "day" else "days"
        val pillWidth = 66f
        val countSpace = if (wide) width - x - pillWidth - 24f else width - 24f
        val used = line(digits, x, countY, if (compact) 26f else minOf(74f, height * .39f),
            countSpace, primary, countFont, center = !wide)
        if (wide && snapshot.count != null) {
            val left = x + used + 10f
            val pill = RectF(left, countY - 25f, left + pillWidth, countY + 1f)
            paint.color = ColorUtils.setAlphaComponent(accent, 42)
            canvas.drawRoundRect(pill, 13f, 13f, paint)
            paint.color = ColorUtils.setAlphaComponent(accent, 140)
            paint.style = Paint.Style.STROKE
            canvas.drawRoundRect(pill, 13f, 13f, paint)
            paint.style = Paint.Style.FILL
            val flame = Path().apply {
                moveTo(left + 14f, countY - 18f)
                cubicTo(left + 14f, countY - 14f, left + 10f, countY - 13f, left + 10f, countY - 10f)
                cubicTo(left + 10f, countY - 5f, left + 19f, countY - 5f, left + 19f, countY - 10f)
                quadTo(left + 19f, countY - 14f, left + 14f, countY - 18f)
                close()
            }
            paint.color = accent
            canvas.drawPath(flame, paint)
            line(unit, pill.centerX() + 7f, countY - 8f, 13f, pillWidth - 25f, accent,
                Typeface.DEFAULT_BOLD, center = true)
        }
        val footer = if (snapshot.count != null && !wide) "$unit · $status" else status
        val footerY = height * .83f
        if (wide) {
            paint.color = accent
            canvas.drawCircle(x + 3f, footerY - 3.5f, 2.5f, paint)
        }
        line(footer, if (wide) x + 12f else x, footerY, if (compact) 9f else 12f,
            if (wide) width - x - 24f else width - 18f, accent, center = !wide)
        canvas.restoreToCount(save)
    }
}
