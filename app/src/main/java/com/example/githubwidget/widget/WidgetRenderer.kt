package com.example.githubwidget.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextPaint
import androidx.core.graphics.ColorUtils
import com.example.githubwidget.data.Stats
import com.example.githubwidget.data.UserData
import com.example.githubwidget.data.SampleData
import com.example.githubwidget.design.Background
import com.example.githubwidget.design.NumberFont
import com.example.githubwidget.design.ColorSet
import com.example.githubwidget.design.Palettes
import com.example.githubwidget.design.WidgetDesign
import com.example.githubwidget.design.WidgetLayout
import java.text.NumberFormat
import kotlin.math.sqrt

/**
 * Paints the whole widget into one bitmap. The home-screen widget and the
 * in-app preview both use this, so they always look identical.
 *
 * Sizes are in dp; everything adapts to the space — small widgets drop
 * secondary text before the graph ever gets cramped.
 */
object WidgetRenderer {

    /** What to draw when there's no real data yet. */
    enum class Placeholder { NONE, SIGNED_OUT, LOADING }

    /** Keeps bitmaps under the RemoteViews memory budget on every device. */
    private const val MAX_PIXELS = 1_100_000f

    fun render(
        context: Context,
        design: WidgetDesign,
        data: UserData?,
        avatar: Bitmap?,
        widthDp: Float,
        heightDp: Float,
        dark: Boolean,
        placeholder: Placeholder = if (data == null) Placeholder.SIGNED_OUT else Placeholder.NONE,
        font: NumberFont = NumberFont.SYSTEM,
        maxPixels: Int = MAX_PIXELS.toInt(),
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        var scale = density
        val px = widthDp * heightDp * density * density
        val budget = maxPixels.coerceAtLeast(1).toFloat()
        if (px > budget) scale = density * sqrt(budget / px)
        val w = (widthDp * scale).toInt().coerceAtLeast(1)
        val h = (heightDp * scale).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val isDark = when (design.background) {
            Background.AUTO -> dark
            Background.LIGHT -> false
            Background.DARK, Background.BLACK -> true
        }
        val base = Palettes.colors(context, design, isDark)
        // Peggy convergence (widget side only): card + text follow the Peggy
        // theme (light card #FFFFFF, dark card #1A2145, black stays #000000).
        // Graph levels + accent stay on the Palettes ramps untouched.
        val peggyCard = when {
            design.background == Background.BLACK -> 0xFF000000.toInt()
            isDark -> 0xFF1A2145.toInt()
            else -> 0xFFFFFFFF.toInt()
        }
        val colors = base.copy(
            card = peggyCard,
            border = ColorUtils.setAlphaComponent(
                0xFFD4DCF5.toInt(),
                if (isDark) 51 else 110,
            ),
            textPrimary = if (isDark) 0xFFF2F4FF.toInt() else 0xFF1A2340.toInt(),
            textSecondary = if (isDark) 0xFFA8B1D1.toInt() else 0xFF5A6585.toInt(),
        )
        Painter(Canvas(bmp), scale, widthDp, heightDp, design, colors, context, font).draw(data, avatar, placeholder)
        return bmp
    }

    private class Painter(
        val canvas: Canvas,
        val s: Float,
        val wDp: Float,
        val hDp: Float,
        val design: WidgetDesign,
        val colors: ColorSet,
        val context: Context,
        val font: NumberFont,
    ) {
        val numbers: NumberFormat = NumberFormat.getIntegerInstance()
        val seeThrough = design.opacity < 45
        fun dp(v: Float) = v * s

        fun text(sizeSp: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = dp(sizeSp)
            this.color = color
            typeface = NumberFont.typeface(context, font, if (bold) 700 else 400)
            if (seeThrough) setShadowLayer(dp(3f), 0f, dp(1f), if (colors.isDark) 0x99000000.toInt() else 0x66FFFFFF)
        }

        fun medium(sizeSp: Float, color: Int) = text(sizeSp, color).apply {
            typeface = NumberFont.typeface(context, font, 500)
        }

        fun draw(data: UserData?, avatar: Bitmap?, placeholder: Placeholder) {
            drawCard()
            val pad = dp(if (wDp < 180 || hDp < 100) 12f else 16f)
            val area = RectF(pad, pad, canvas.width - pad, canvas.height - pad)
            when {
                data == null -> drawPlaceholder(area, placeholder)
                design.layout == WidgetLayout.GRAPH -> drawGraphOnly(area, data)
                design.layout == WidgetLayout.NUMBERS -> drawNumbers(area, data)
                else -> drawClassic(area, data, avatar)
            }
        }

        // ------------------------------------------------------------ card

        fun drawCard() {
            val r = dp(design.corners.dp)
            val rect = RectF(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat())
            if (design.opacity > 0) {
                val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = ColorUtils.setAlphaComponent(colors.card, design.opacity * 255 / 100)
                }
                canvas.drawRoundRect(rect, r, r, fill)
            }
            if (design.opacity >= 30) {
                val half = dp(0.5f)
                rect.inset(half, half)
                val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = dp(1f)
                    color = colors.border
                }
                canvas.drawRoundRect(rect, r, r, stroke)
            }
        }

        // ------------------------------------------------------------ graph

        fun graph(area: RectF, data: UserData, months: Boolean, maxCellDp: Float = 13f): RectF {
            val labelPaint = if (months) medium(9.5f, colors.textSecondary) else null
            return GraphPainter.draw(
                canvas, area, data.contributions.days,
                GraphPainter.Spec(
                    levels = colors.levels,
                    radiusFraction = design.cellShape.radiusFraction,
                    weekStartsMonday = design.weekStartsMonday,
                    weeks = design.range.weeks,
                    maxCellPx = dp(maxCellDp),
                    monthLabelPaint = labelPaint,
                    monthLabelHeightPx = if (months) dp(15f) else 0f,
                ),
            )
        }

        /** Month labels only when the grid would still have comfortable cells. */
        fun roomForMonths(gridHeight: Float) = design.showMonths && gridHeight >= dp(72f)

        // ------------------------------------------------------------ layouts

        fun drawClassic(area: RectF, data: UserData, avatar: Bitmap?) {
            val stats = Stats.from(data.contributions)
            val wantHeader = design.showName || design.showAvatar || design.showTotal
            val wantFooter = design.showStreak || design.showToday
            val headerH = dp(26f)
            val footerH = dp(16f)
            val gap = dp(10f)
            // Drop text rows before the graph gets too small to read.
            val minGrid = dp(46f)
            var header = wantHeader
            var footer = wantFooter
            fun gridH() = area.height() - (if (header) headerH + gap else 0f) - (if (footer) footerH + gap * 0.8f else 0f)
            if (gridH() < minGrid) footer = false
            if (gridH() < minGrid) header = false

            var top = area.top
            if (header) {
                drawHeader(RectF(area.left, top, area.right, top + headerH), data, avatar, stats)
                top += headerH + gap
            }
            val bottom = if (footer) area.bottom - footerH - gap * 0.8f else area.bottom
            val gridArea = RectF(area.left, top, area.right, bottom)
            val grid = graph(gridArea, data, roomForMonths(gridArea.height()))
            if (footer) {
                drawFooter(RectF(grid.left, area.bottom - footerH, grid.right, area.bottom), stats, data.fetchedAt)
            }
        }

        fun drawGraphOnly(area: RectF, data: UserData) {
            graph(area, data, roomForMonths(area.height()), maxCellDp = 16f)
        }

        fun drawNumbers(area: RectF, data: UserData) {
            val stats = Stats.from(data.contributions)
            if (area.width() < dp(150f) && area.height() < dp(72f)) {
                drawBigStats(area, stats, compact = true)
                return
            }
            val wide = area.width() > area.height() * 1.55f
            if (wide) {
                val colW = (area.width() * 0.36f).coerceIn(dp(96f), dp(170f))
                drawBigStats(RectF(area.left, area.top, area.left + colW, area.bottom), stats, compact = area.height() < dp(100f))
                val g = RectF(area.left + colW + dp(12f), area.top, area.right, area.bottom)
                graph(g, data, roomForMonths(g.height()) && g.height() > dp(100f))
            } else {
                val statsH = (area.height() * 0.48f).coerceAtMost(dp(92f))
                drawBigStats(RectF(area.left, area.top, area.right, area.top + statsH), stats, compact = true)
                val g = RectF(area.left, area.top + statsH + dp(8f), area.right, area.bottom)
                if (g.height() > dp(24f)) graph(g, data, false)
            }
        }

        // ------------------------------------------------------------ pieces

        fun drawHeader(r: RectF, data: UserData, avatar: Bitmap?, stats: Stats) {
            var left = r.left
            val cy = r.centerY()
            if (design.showAvatar) {
                val size = dp(24f)
                drawAvatar(avatar, data, left, cy - size / 2, size)
                left += size + dp(8f)
            }
            // Keep clear of the refresh icon overlaid at top-end inside the card (20dp + margins).
            var right = r.right - dp(28f)
            if (design.showTotal) {
                val num = text(16f, colors.accent, bold = true)
                val numStr = numbers.format(stats.total)
                val label = text(11f, colors.textSecondary)
                val labelStr = if (r.width() > dp(220f)) " contributions" else ""
                val labelW = label.measureText(labelStr)
                val numW = num.measureText(numStr)
                val base = cy + num.textSize * 0.36f
                canvas.drawText(labelStr, right - labelW, base, label)
                canvas.drawText(numStr, right - labelW - numW, base, num)
                right -= labelW + numW + dp(10f)
            }
            if (design.showName) {
                val p = medium(14f, colors.textPrimary)
                val name = data.profile?.displayName ?: data.contributions.username
                val avail = right - left
                if (avail > dp(24f)) {
                    val shown = ellipsize(name, p, avail)
                    canvas.drawText(shown, left, cy + p.textSize * 0.36f, p)
                }
            }
        }

        fun drawFooter(r: RectF, stats: Stats, fetchedAt: Long) {
            // Refresh icon now lives inside the header (top-end), so the footer gets full width.
            val end = r.right
            val parts = buildList {
                if (design.showStreak) add(streakText(stats.currentStreak))
                if (design.showToday) add(todayText(stats.today))
            }
            val p = text(11f, colors.textSecondary)
            val base = r.centerY() + p.textSize * 0.36f
            var x = r.left
            val dot = Paint(Paint.ANTI_ALIAS_FLAG)
            parts.forEachIndexed { i, s ->
                val active = if (i == 0 && design.showStreak) stats.currentStreak > 0 else stats.today > 0
                dot.color = if (active) colors.accent else colors.levels[0]
                val d = dp(3.5f)
                canvas.drawCircle(x + d, r.centerY(), d, dot)
                x += d * 2 + dp(5f)
                val shown = ellipsize(s, p, end - x)
                canvas.drawText(shown, x, base, p)
                x += p.measureText(shown) + dp(14f)
            }
            // Small "last updated" note after the stats, only when it fits.
            if (fetchedAt > 0) {
                val note = text(9.5f, colors.textSecondary)
                val noteStr = updatedAgo(fetchedAt)
                if (end - x > note.measureText(noteStr) + dp(4f)) {
                    canvas.drawText(noteStr, x, r.centerY() + note.textSize * 0.36f, note)
                    x += note.measureText(noteStr) + dp(14f)
                }
            }
            // "Less ▪▪▪▪▪ More" legend on the right when there's plenty of room.
            val legendW = dp(5 * 10f + 4 * 3f)
            val labelP = text(10f, colors.textSecondary)
            val lessW = labelP.measureText("Less ")
            val moreW = labelP.measureText(" More")
            val total = lessW + legendW + moreW
            if (end - x > total + dp(8f)) {
                var lx = end - total
                canvas.drawText("Less", lx, base, labelP)
                lx += lessW
                val cell = dp(10f)
                val cp = Paint(Paint.ANTI_ALIAS_FLAG)
                val cr = cell * design.cellShape.radiusFraction
                for (lvl in 0..4) {
                    cp.color = colors.levels[lvl]
                    val top = r.centerY() - cell / 2
                    canvas.drawRoundRect(RectF(lx, top, lx + cell, top + cell), cr, cr, cp)
                    lx += cell + dp(3f)
                }
                canvas.drawText("More", lx + dp(1f), base, labelP)
            }
        }

        fun drawBigStats(r: RectF, stats: Stats, compact: Boolean) {
            val bigSize = if (compact) (r.height() / s * 0.34f).coerceIn(18f, 30f) else 30f
            val big = text(bigSize, colors.accent, bold = true)
            val label = text(11f, colors.textSecondary)
            val small = medium(13f, colors.textPrimary)

            val showTotal = design.showTotal || (!design.showStreak && !design.showToday)
            val showLabel = r.height() >= dp(40f)
            val lines = buildList {
                if (!showTotal || r.height() >= dp(72f)) {
                    if (design.showStreak) add(streakText(stats.currentStreak))
                    if (design.showToday) add(todayText(stats.today))
                }
            }
            // Measure the block so it can be vertically centred.
            val bigH = if (showTotal) big.textSize + (if (showLabel) dp(2f) + label.textSize else 0f) else 0f
            val lineH = small.textSize * 1.45f
            val lineCount = if (compact) minOf(lines.size, 1) else lines.size
            val block = bigH + (if (showTotal && lines.isNotEmpty()) dp(10f) else 0f) + lineCount * lineH
            var y = r.top + ((r.height() - block) / 2f).coerceAtLeast(0f)

            if (showTotal) {
                y += big.textSize * 0.9f
                val total = numbers.format(stats.total)
                canvas.drawText(total, r.left, y, big.fit(r.width(), total))
                if (showLabel) {
                    y += dp(2f) + label.textSize
                    canvas.drawText("contributions this year", r.left, y, label.fit(r.width(), "contributions this year"))
                }
                y += dp(10f)
            }
            if (compact && lines.isNotEmpty()) {
                // One line on small cards: "5 day streak · 1 today", or just the first if it won't fit.
                val joined = lines.joinToString("  ·  ")
                val t = if (small.measureText(joined) <= r.width()) joined else ellipsize(lines[0], small, r.width())
                y += small.textSize
                canvas.drawText(t, r.left, y, small)
            } else {
                lines.forEach {
                    y += lineH
                    canvas.drawText(ellipsize(it, small, r.width()), r.left, y - lineH * 0.3f, small)
                }
            }
        }

        fun TextPaint.fit(width: Float, s: String): TextPaint {
            while (measureText(s) > width && textSize > dp(8f)) textSize -= dp(0.5f)
            return this
        }

        fun drawAvatar(avatar: Bitmap?, data: UserData, x: Float, y: Float, size: Float) {
            val rect = RectF(x, y, x + size, y + size)
            if (avatar != null) {
                val shader = BitmapShader(avatar, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                val m = Matrix()
                m.setRectToRect(
                    RectF(0f, 0f, avatar.width.toFloat(), avatar.height.toFloat()), rect, Matrix.ScaleToFit.FILL,
                )
                shader.setLocalMatrix(m)
                canvas.drawOval(rect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader })
            } else {
                canvas.drawOval(rect, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.levels[2] })
                val letter = (data.profile?.displayName ?: data.contributions.username).take(1).uppercase()
                val p = text(12f, colors.textPrimary, bold = true).apply { textAlign = Paint.Align.CENTER }
                canvas.drawText(letter, rect.centerX(), rect.centerY() + p.textSize * 0.36f, p)
            }
        }

        fun drawPlaceholder(area: RectF, placeholder: Placeholder) {
            val title = if (placeholder == Placeholder.LOADING) "Loading your graph…" else "Tap to set up"
            val sub = if (placeholder == Placeholder.LOADING) "This only takes a moment" else "Connect your GitHub in one step"
            val t = medium(14f, colors.textPrimary)
            val st = text(11f, colors.textSecondary)
            val showText = area.height() > dp(70f)
            var top = area.top
            if (showText) {
                canvas.drawText(ellipsize(title, t, area.width()), area.left, top + t.textSize, t)
                canvas.drawText(ellipsize(sub, st, area.width()), area.left, top + t.textSize + dp(6f) + st.textSize, st)
                top += t.textSize + st.textSize + dp(16f)
            }
            // A faded sample graph hints at what's coming.
            val faded = colors.copy(levels = colors.levels.map { ColorUtils.setAlphaComponent(it, 90) }.toIntArray())
            val g = RectF(area.left, top, area.right, area.bottom)
            if (g.height() > dp(20f)) {
                GraphPainter.draw(
                    canvas, g, SampleData.contributions.days,
                    GraphPainter.Spec(
                        levels = faded.levels,
                        radiusFraction = design.cellShape.radiusFraction,
                        weekStartsMonday = design.weekStartsMonday,
                        weeks = null,
                        maxCellPx = dp(12f),
                    ),
                )
            }
        }
    }

    /** Shortens [text] with "…" until it fits [width] px; empty if not even "…" fits. */
    fun ellipsize(text: String, paint: Paint, width: Float): String {
        if (paint.measureText(text) <= width) return text
        var end = text.length
        while (end > 0) {
            val candidate = text.substring(0, end).trimEnd() + "…"
            if (paint.measureText(candidate) <= width) return candidate
            end--
        }
        return ""
    }

    fun streakText(days: Int) = when (days) {
        0 -> "No streak yet"
        1 -> "1 day streak"
        else -> "$days day streak"
    }

    fun todayText(count: Int) = when (count) {
        0 -> "None today"
        1 -> "1 today"
        else -> "$count today"
    }

    fun updatedAgo(fetchedAt: Long, now: Long = System.currentTimeMillis()): String {
        val minutes = ((now - fetchedAt) / 60_000).coerceAtLeast(0)
        return when {
            minutes < 1 -> "Updated just now"
            minutes < 60 -> "Updated $minutes min ago"
            minutes < 24 * 60 -> (minutes / 60).toInt().let { if (it == 1) "Updated 1 hour ago" else "Updated $it hours ago" }
            minutes < 48 * 60 -> "Updated yesterday"
            else -> "Updated on " + java.time.Instant.ofEpochMilli(fetchedAt)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                .format(java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.getDefault()))
        }
    }
}
