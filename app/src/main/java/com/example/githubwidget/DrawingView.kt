package com.example.githubwidget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/** A single drawable action. Re-rendered from scratch on undo/redo. */
sealed interface DrawAction {
    data class Stroke(val pts: List<PointF>, val color: Int, val width: Float, val erase: Boolean) : DrawAction
    data class Shape(val kind: Int, val a: PointF, val b: PointF, val color: Int, val width: Float) : DrawAction
    data class Fill(val color: Int) : DrawAction
}

/** Touch canvas for the Drawing Studio. Fixed square bitmap, fits the view. */
class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        const val TOOL_BRUSH = 0
        const val TOOL_ERASER = 1
        const val TOOL_HIGHLIGHT = 2
        const val TOOL_LINE = 3
        const val TOOL_RECT = 4
        const val TOOL_CIRCLE = 5
        const val TOOL_FILL = 6
        const val SIZE = 1024
    }

    var tool: Int = TOOL_BRUSH
    var color: Int = Color.parseColor("#39D353")
    var strokeWidth: Float = 24f

    private val actions = ArrayList<DrawAction>()
    private val undone = ArrayList<DrawAction>()
    private var bitmap: Bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
    private var current: MutableList<PointF>? = null

    var onChange: (() -> Unit)? = null

    val canUndo: Boolean get() = actions.isNotEmpty()
    val canRedo: Boolean get() = undone.isNotEmpty()
    val isEmpty: Boolean get() = actions.isEmpty()

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    /** Flood fill over near-identical pixels, starting at (sx, sy). */
    private fun fillAt(pixels: IntArray, w: Int, h: Int, sx: Int, sy: Int, color: Int) {
        if (sx !in 0 until w || sy !in 0 until h) return
        val target = pixels[sy * w + sx]
        if (target == color) return
        fun close(a: Int, b: Int): Boolean {
            return kotlin.math.abs(Color.red(a) - Color.red(b)) < 32 &&
                kotlin.math.abs(Color.green(a) - Color.green(b)) < 32 &&
                kotlin.math.abs(Color.blue(a) - Color.blue(b)) < 32 &&
                kotlin.math.abs(Color.alpha(a) - Color.alpha(b)) < 32
        }
        val stack = ArrayDeque<Int>()
        stack.add(sy * w + sx)
        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            if (!close(pixels[i], target)) continue
            pixels[i] = color
            val x = i % w
            val y = i / w
            if (x > 0) stack.add(i - 1)
            if (x < w - 1) stack.add(i + 1)
            if (y > 0) stack.add(i - w)
            if (y < h - 1) stack.add(i + w)
        }
    }

    fun undo() {
        if (actions.isEmpty()) return
        undone.add(actions.removeAt(actions.size - 1))
        rerenderFromHistory()
    }

    fun redo() {
        if (undone.isEmpty()) return
        actions.add(undone.removeAt(undone.size - 1))
        rerenderFromHistory()
    }

    private fun rerenderFromHistory() {
        // Rebuild without flood-fill's pixel dependency problem: fills replay
        // from their recorded seed.
        val fresh = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(fresh)
        for (a in actions) {
            when (a) {
                is DrawAction.Fill -> {
                    val pixels = IntArray(SIZE * SIZE)
                    fresh.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
                    val s = fillSeedFor(a)
                    fillAt(pixels, SIZE, SIZE, s.x.toInt(), s.y.toInt(), a.color)
                    fresh.setPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
                }
                is DrawAction.Stroke -> {
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        this.color = a.color
                        strokeWidth = a.width
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                        if (a.erase) {
                            xfermode = android.graphics.PorterDuffXfermode(
                                android.graphics.PorterDuff.Mode.CLEAR
                            )
                        }
                    }
                    val path = Path()
                    a.pts.forEachIndexed { i, pt ->
                        if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                    }
                    c.drawPath(path, p)
                }
                is DrawAction.Shape -> {
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        this.color = a.color
                        strokeWidth = a.width
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                    }
                    when (a.kind) {
                        TOOL_LINE -> c.drawLine(a.a.x, a.a.y, a.b.x, a.b.y, p)
                        TOOL_RECT -> c.drawRect(a.a.x, a.a.y, a.b.x, a.b.y, p)
                        else -> {
                            val r = kotlin.math.hypot(a.b.x - a.a.x, a.b.y - a.a.y)
                            c.drawCircle(a.a.x, a.a.y, r, p)
                        }
                    }
                }
            }
        }
        bitmap.recycle()
        bitmap = fresh
        invalidate()
        onChange?.invoke()
    }

    private val fillSeeds = HashMap<DrawAction.Fill, PointF>()

    private fun fillSeedFor(a: DrawAction.Fill): PointF =
        fillSeeds[a] ?: PointF(SIZE / 2f, SIZE / 2f)

    fun clear() {
        actions.clear()
        undone.clear()
        fillSeeds.clear()
        val fresh = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        bitmap.recycle()
        bitmap = fresh
        invalidate()
        onChange?.invoke()
    }

    fun export(): Bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)

    private fun toBitmap(p: PointF): PointF {
        val s = SIZE / minOf(width, height).toFloat()
        val ox = (width - minOf(width, height)) / 2f
        val oy = (height - minOf(width, height)) / 2f
        return PointF((p.x - ox) * s, (p.y - oy) * s)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val bp = toBitmap(PointF(e.x, e.y))
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                if (tool == TOOL_FILL) {
                    val seed = PointF(
                        bp.x.coerceIn(0f, (SIZE - 1).toFloat()),
                        bp.y.coerceIn(0f, (SIZE - 1).toFloat())
                    )
                    val action = DrawAction.Fill(color)
                    fillSeeds[action] = seed
                    actions.add(action)
                    undone.clear()
                    // Apply directly on the live bitmap for immediacy.
                    val pixels = IntArray(SIZE * SIZE)
                    bitmap.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
                    fillAt(pixels, SIZE, SIZE, seed.x.toInt(), seed.y.toInt(), color)
                    bitmap.setPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
                    invalidate()
                    onChange?.invoke()
                    return true
                }
                current = mutableListOf(bp)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                current?.add(bp)
                // Live preview: draw the in-progress stroke on top.
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val pts = current ?: return true
                current = null
                if (e.action == MotionEvent.ACTION_CANCEL || pts.isEmpty()) {
                    invalidate()
                    return true
                }
                val first = pts.first()
                val last = pts.last()
                val width = when (tool) {
                    TOOL_HIGHLIGHT -> strokeWidth * 2.5f
                    else -> strokeWidth
                }
                val paintColor = when (tool) {
                    TOOL_ERASER -> Color.TRANSPARENT
                    TOOL_HIGHLIGHT -> Color.argb(90, Color.red(color), Color.green(color), Color.blue(color))
                    else -> color
                }
                when (tool) {
                    TOOL_LINE, TOOL_RECT, TOOL_CIRCLE ->
                        actions.add(DrawAction.Shape(tool, first, last, paintColor, width))
                    else -> actions.add(
                        DrawAction.Stroke(
                            pts.toList(), paintColor, width,
                            erase = tool == TOOL_ERASER
                        )
                    )
                }
                undone.clear()
                rerenderFromHistory()
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val side = minOf(width, height).toFloat()
        val ox = (width - side) / 2f
        val oy = (height - side) / 2f
        // Checkerboard for transparency.
        val check = Paint().apply { color = Color.parseColor("#0D1117") }
        canvas.drawRect(ox, oy, ox + side, oy + side, check)
        canvas.drawBitmap(
            bitmap, null,
            android.graphics.RectF(ox, oy, ox + side, oy + side),
            bitmapPaint
        )
        // In-progress stroke preview.
        val pts = current
        if (pts != null && pts.size > 1) {
            val s = SIZE / side
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                strokeWidth = strokeWidth / s
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val path = Path()
            pts.forEachIndexed { i, pt ->
                val x = ox + pt.x / s
                val y = oy + pt.y / s
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, p)
        }
    }
}
