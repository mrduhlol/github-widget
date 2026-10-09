package com.example.githubwidget.ui.stats

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import com.example.githubwidget.data.Stats
import com.example.githubwidget.data.UserData
import com.example.githubwidget.design.Background
import com.example.githubwidget.design.Palettes
import com.example.githubwidget.design.WidgetDesign
import com.example.githubwidget.widget.GraphPainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Builds the "Share your year" picture and hands it to the Android share sheet. */
internal object ShareImage {

    private const val W = 1080
    private const val H = 1350

    private const val BG_TOP = 0xFF0D1117.toInt()
    private const val BG_BOTTOM = 0xFF05070A.toInt()
    private const val TILE = 0xFF161B22.toInt()
    private const val TILE_BORDER = 0xFF262C36.toInt()
    private const val EMPTY = 0xFF21262D.toInt()
    private const val WHITE = 0xFFF0F6FC.toInt()
    private const val GRAY = 0xFF9198A1.toInt()
    private const val DIM = 0xFF656D76.toInt()

    /** Renders, saves and opens the share sheet. Returns false if anything went wrong. */
    suspend fun share(context: Context, data: UserData, avatar: Bitmap?, design: WidgetDesign): Boolean {
        val file = runCatching {
            val bmp = withContext(Dispatchers.Default) { render(context, data, avatar, design) }
            withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "shared").apply { mkdirs() }
                File(dir, "my-github-year.png").also { f ->
                    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bmp.recycle()
                }
            }
        }.getOrNull() ?: return false

        return runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val total = NumberFormat.getIntegerInstance().format(data.contributions.totalLastYear)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "My year on GitHub: $total contributions.")
                clipData = ClipData.newRawUri(null, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(send, "Share your year")
            if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }.isSuccess
    }

    fun render(context: Context, data: UserData, avatar: Bitmap?, design: WidgetDesign): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val colors = Palettes.colors(context, design.copy(background = Background.DARK, opacity = 100), true)
        val accent = colors.accent
        val stats = Stats.from(data.contributions)
        val numbers = NumberFormat.getIntegerInstance()
        val pad = 72f

        // Background with a soft glow in the user's color.
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, H.toFloat(), BG_TOP, BG_BOTTOM, Shader.TileMode.CLAMP)
        })
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                W * 0.88f, 40f, 760f,
                ColorUtils.setAlphaComponent(accent, 64), 0x00000000, Shader.TileMode.CLAMP,
            )
        })

        // Header: avatar, name, @login.
        val login = data.profile?.login ?: data.contributions.username
        val name = data.profile?.displayName ?: login
        val avSize = 128f
        val avTop = 96f
        drawAvatar(c, avatar, name, pad, avTop, avSize, accent)
        val textX = pad + avSize + 32f
        val maxText = W - textX - pad
        val namePaint = text(54f, WHITE, bold = true)
        c.drawText(ellipsize(name, namePaint, maxText), textX, avTop + 62f, namePaint)
        val loginPaint = text(34f, GRAY)
        c.drawText(ellipsize("@$login", loginPaint, maxText), textX, avTop + 110f, loginPaint)

        // The big number.
        val totalPaint = text(168f, WHITE, bold = true).apply { letterSpacing = -0.02f }
        c.drawText(numbers.format(stats.total), pad - 6f, 470f, totalPaint)
        val subPaint = text(40f, GRAY)
        c.drawText(
            if (stats.total == 1) "contribution in the last year" else "contributions in the last year",
            pad, 532f, subPaint,
        )

        // The year graph on its own card.
        val graphCard = RectF(pad, 596f, W - pad, 832f)
        drawTile(c, graphCard, 36f)
        val levels = colors.levels.copyOf().also { it[0] = EMPTY }
        GraphPainter.draw(
            c,
            RectF(graphCard.left + 36f, graphCard.top + 28f, graphCard.right - 36f, graphCard.bottom - 28f),
            data.contributions.days,
            GraphPainter.Spec(
                levels = levels,
                radiusFraction = design.cellShape.radiusFraction,
                weekStartsMonday = design.weekStartsMonday,
                weeks = 53,
                maxCellPx = 40f,
                monthLabelPaint = text(24f, GRAY),
                monthLabelHeightPx = 44f,
            ),
        )

        // Four stat tiles.
        val gap = 24f
        val tileW = (W - 2 * pad - gap) / 2f
        val tileH = 170f
        val shortDate = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
        val tiles = listOf(
            Triple("Current streak", numbers.format(stats.currentStreak), if (stats.currentStreak == 1) " day" else " days"),
            Triple("Longest streak", numbers.format(stats.longestStreak), if (stats.longestStreak == 1) " day" else " days"),
            Triple(
                "Best day",
                numbers.format(stats.bestDay?.count ?: 0),
                stats.bestDay?.let { "  on ${it.date.format(shortDate)}" } ?: "",
            ),
            Triple("Active days", numbers.format(stats.activeDays), " of ${numbers.format(stats.trackedDays)}"),
        )
        val labelPaint = text(30f, GRAY)
        val valuePaint = text(64f, WHITE, bold = true)
        val suffixPaint = text(32f, GRAY)
        tiles.forEachIndexed { i, (label, value, suffix) ->
            val x = pad + (i % 2) * (tileW + gap)
            val y = 880f + (i / 2) * (tileH + gap)
            val r = RectF(x, y, x + tileW, y + tileH)
            drawTile(c, r, 32f)
            c.drawText(label, x + 36f, y + 60f, labelPaint)
            c.drawText(value, x + 36f, y + 138f, valuePaint)
            val vw = valuePaint.measureText(value)
            val room = tileW - 72f - vw
            if (suffix.isNotEmpty() && room > 40f) {
                c.drawText(ellipsize(suffix, suffixPaint, room), x + 36f + vw, y + 138f, suffixPaint)
            }
        }

        // Footer: a tiny 2×2 graph mark and the app name.
        val footer = text(28f, DIM)
        val label = "Made with GH Widgets"
        val markSize = 28f
        val total = markSize + 14f + footer.measureText(label)
        val fx = (W - total) / 2f
        val fy = 1286f
        val sq = (markSize - 4f) / 2f
        val markPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val markColors = intArrayOf(colors.levels[2], colors.levels[4], colors.levels[4], colors.levels[3])
        for (i in 0..3) {
            markPaint.color = markColors[i]
            val mx = fx + (i % 2) * (sq + 4f)
            val my = fy - markSize + 4f + (i / 2) * (sq + 4f)
            c.drawRoundRect(RectF(mx, my, mx + sq, my + sq), 3f, 3f, markPaint)
        }
        c.drawText(label, fx + markSize + 14f, fy, footer)
        return bmp
    }

    private fun drawTile(c: Canvas, r: RectF, radius: Float) {
        c.drawRoundRect(r, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TILE })
        c.drawRoundRect(r, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TILE_BORDER
            style = Paint.Style.STROKE
            strokeWidth = 2f
        })
    }

    private fun drawAvatar(c: Canvas, avatar: Bitmap?, name: String, x: Float, y: Float, size: Float, accent: Int) {
        val cx = x + size / 2f
        val cy = y + size / 2f
        val radius = size / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (avatar != null && !avatar.isRecycled) {
            val shader = BitmapShader(avatar, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val scale = size / minOf(avatar.width, avatar.height)
            shader.setLocalMatrix(Matrix().apply {
                setScale(scale, scale)
                postTranslate(
                    x - (avatar.width * scale - size) / 2f,
                    y - (avatar.height * scale - size) / 2f,
                )
            })
            paint.shader = shader
            c.drawCircle(cx, cy, radius, paint)
        } else {
            paint.color = ColorUtils.setAlphaComponent(accent, 60)
            c.drawCircle(cx, cy, radius, paint)
            val letter = text(56f, accent, bold = true).apply { textAlign = Paint.Align.CENTER }
            val initial = name.take(1).uppercase()
            c.drawText(initial, cx, cy - (letter.descent() + letter.ascent()) / 2f, letter)
        }
        c.drawCircle(cx, cy, radius - 1.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = 0x33FFFFFF
        })
    }

    private fun text(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL)
    }

    private fun ellipsize(s: String, paint: TextPaint, width: Float): String =
        TextUtils.ellipsize(s, paint, width, TextUtils.TruncateAt.END).toString()
}
