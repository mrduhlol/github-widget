package com.example.githubwidget.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import androidx.core.graphics.ColorUtils
import com.example.githubwidget.design.NumberFont
import com.example.githubwidget.design.WidgetDesign
import java.text.NumberFormat

/** Uses the same calendar snapshot at every widget size. */
internal object AuroraStreakPainter {
    fun draw(
        canvas: Canvas,
        scale: Float,
        context: Context,
        design: WidgetDesign,
        font: NumberFont,
        snapshot: StreakWidgetPainter.Snapshot,
    ) {
        val save = canvas.save()
        canvas.scale(scale, scale)
        val width = canvas.width / scale
        val height = canvas.height / scale
        val active = snapshot.state == StreakWidgetPainter.State.ACTIVE
        val primary = 0xFFF2F4F6.toInt()
        val muted = 0xFFABB4BE.toInt()
        val surface = 0xFF15181C.toInt()
        val accent = if (active) design.auroraColor.accent else muted
        val radius = minOf(design.corners.dp, width / 2, height / 2)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val bounds = RectF(0f, 0f, width, height)
        if (design.opacity > 0) {
            val material = canvas.saveLayerAlpha(bounds, design.opacity * 255 / 100)
            paint.shader = LinearGradient(0f, 0f, width, height, surface,
                ColorUtils.blendARGB(surface, accent, if (active) .13f else .04f), Shader.TileMode.CLAMP)
            canvas.drawRoundRect(bounds, radius, radius, paint)
            paint.shader = null
            if (design.opacity >= 30 && width > 1f && height > 1f) {
                paint.color = ColorUtils.blendARGB(surface, accent, .18f)
                paint.style = Paint.Style.STROKE
                canvas.drawRoundRect(RectF(.5f, .5f, width - .5f, height - .5f), radius, radius, paint)
                paint.style = Paint.Style.FILL
            }
            canvas.restoreToCount(material)
        }

        val pad = WidgetRenderer.contentPaddingDp(width, height)
        val contentWidth = width - 2 * pad
        val contentHeight = height - 2 * pad
        val compact = contentHeight < 64f
        val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = if (compact) 10f else 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val number = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primary
            textSize = (contentHeight * .46f).coerceIn(12f, 66f)
            typeface = NumberFont.typeface(context, font, 700)
        }
        val digits = snapshot.count?.let {
            NumberFormat.getIntegerInstance().format(it) + if (snapshot.lowerBound) "+" else ""
        } ?: "—"
        val unit = when (snapshot.count) { null -> ""; 1 -> " day"; else -> " days" }
        val status = when (snapshot.state) {
            StreakWidgetPainter.State.ACTIVE -> "Active today"
            StreakWidgetPainter.State.PENDING -> "Pending today"
            StreakWidgetPainter.State.BROKEN -> "Streak ended"
            else -> snapshot.state.label
        }
        val titleY = pad + label.textSize
        val valueY = if (compact) pad + contentHeight * .69f else pad + contentHeight * .71f
        val footerY = height - pad
        val maxNumberWidth = (contentWidth - label.measureText(unit)).coerceAtLeast(1f)
        number.textSize *= minOf(1f, maxNumberWidth / number.measureText(digits))
        canvas.drawText(WidgetRenderer.ellipsize("GitHub streak", label, contentWidth), pad, titleY, label)
        canvas.drawText(digits, pad, valueY, number)
        canvas.drawText(unit, pad + number.measureText(digits), valueY, label)
        label.color = accent
        canvas.drawText(WidgetRenderer.ellipsize(status, label, contentWidth), pad, footerY, label)
        canvas.restoreToCount(save)
    }
}
