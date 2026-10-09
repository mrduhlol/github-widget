package com.example.githubwidget

import android.graphics.drawable.GradientDrawable
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Spectrum-style color picker: a horizontal rainbow hue bar on top,
 * a shade grid (saturation x brightness) for the chosen hue below,
 * live preview + hex readout. No new dependencies.
 */
object ColorPickerDialog {

    /** Horizontal rainbow hue bar (0..360). */
    private class SpectrumBar(
        context: Context,
        initialHue: Float,
        val onHue: (Float) -> Unit
    ) : View(context) {

        var hue = initialHue
        private val paint = Paint()
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            color = Color.WHITE
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0 || h <= 0) return
            paint.shader = LinearGradient(
                0f, 0f, w, 0f,
                intArrayOf(
                    Color.RED, Color.YELLOW, Color.GREEN,
                    Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED
                ),
                null, Shader.TileMode.CLAMP
            )
            val r = h / 2f
            canvas.drawRoundRect(0f, 0f, w, h, r, r, paint)
            val x = (hue / 360f * w).coerceIn(r, w - r)
            canvas.drawCircle(x, h / 2f, r - 5f, ring)
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_MOVE) {
                var h = (e.x / width.coerceAtLeast(1) * 360f).coerceIn(0f, 360f)
                if (h >= 360f) h = 0f
                hue = h
                onHue(h)
                invalidate()
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            return super.onTouchEvent(e)
        }
    }

    /** Shade grid for one hue: columns = saturation, rows = brightness. */
    private class ShadeGrid(
        context: Context,
        val cols: Int = 6,
        val rows: Int = 4,
        val onPick: (sat: Float, value: Float) -> Unit
    ) : View(context) {

        var hue = 0f
        private val paint = Paint()
        private val hsv = FloatArray(3)

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0 || h <= 0) return
            val cw = w / cols
            val ch = h / rows
            hsv[0] = hue
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    hsv[1] = 0.25f + 0.75f * (c / (cols - 1f))
                    hsv[2] = 1f - 0.65f * (r / (rows - 1f))
                    paint.color = Color.HSVToColor(hsv)
                    canvas.drawRect(c * cw, r * ch, (c + 1) * cw, (r + 1) * ch, paint)
                }
            }
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_MOVE) {
                val c = (e.x / width.coerceAtLeast(1) * cols).toInt().coerceIn(0, cols - 1)
                val r = (e.y / height.coerceAtLeast(1) * rows).toInt().coerceIn(0, rows - 1)
                onPick(
                    0.25f + 0.75f * (c / (cols - 1f)),
                    1f - 0.65f * (r / (rows - 1f))
                )
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            return super.onTouchEvent(e)
        }
    }

    fun show(context: Context, initial: Int, onPick: (Int) -> Unit) {
        val start = FloatArray(3)
        Color.colorToHSV(initial, start)
        var hue = start[0]
        var sat = start[1]
        var value = start[2]
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(0))
        }
        val preview = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(64)
            )
        }
        val hex = TextView(context).apply {
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setPadding(0, dp(8), 0, dp(12))
        }

        fun current(): Int = Color.HSVToColor(floatArrayOf(hue, sat, value))

        fun refresh() {
            val c = current()
            preview.background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(c)
                setStroke(dp(1), ContextCompat.getColor(context, R.color.stroke_strong))
            }
            hex.text = Themes.toHex(c)
        }

        val grid = ShadeGrid(context) { s, v ->
            sat = s
            value = v
            refresh()
        }
        grid.hue = hue
        grid.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(192)
        ).apply { topMargin = dp(12) }

        val bar = SpectrumBar(context, hue) { h ->
            hue = h
            grid.hue = h
            grid.invalidate()
            refresh()
        }
        bar.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(44)
        )

        box.addView(preview)
        box.addView(hex)
        box.addView(bar)
        box.addView(grid)
        refresh()

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Pick a color")
            .setView(box)
            .setPositiveButton("Use color") { _, _ -> onPick(current()) }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.window?.setBackgroundDrawable(GradientDrawable().apply {
            cornerRadius = dp(24).toFloat()
            setColor(ContextCompat.getColor(context, R.color.surface))
            setStroke(dp(1), ContextCompat.getColor(context, R.color.stroke))
        })
        dialog.show()
        val accent = ContextCompat.getColor(context, R.color.accent)
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.apply { setTextColor(accent); minHeight = dp(48) }
        dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)?.apply {
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary)); minHeight = dp(48)
        }
    }
}
