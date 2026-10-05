package com.example.githubwidget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.util.Locale

/** Shareable contribution card: renders stats + graph into one image. */
object ShareCard {

    data class Options(
        val dark: Boolean,
        val showUsername: Boolean,
        val showTotal: Boolean,
        val showStreak: Boolean
    )

    /** Starting points for the share dialog — toggles only, never the theme. */
    data class CardTemplate(
        val id: String,
        val name: String,
        val dark: Boolean,
        val showUsername: Boolean,
        val showTotal: Boolean,
        val showStreak: Boolean
    )

    val TEMPLATES = listOf(
        CardTemplate("github", "GitHub", dark = true, showUsername = true, showTotal = true, showStreak = true),
        CardTemplate("minimal", "Minimal", dark = true, showUsername = false, showTotal = true, showStreak = false),
        CardTemplate("terminal", "Terminal", dark = true, showUsername = true, showTotal = true, showStreak = true),
        CardTemplate("neon", "Neon", dark = true, showUsername = true, showTotal = true, showStreak = true),
        CardTemplate("poster", "Poster", dark = false, showUsername = true, showTotal = true, showStreak = false)
    )

    private val LIGHT_LEVELS = intArrayOf(
        Color.parseColor("#EBEDF0"),
        Color.parseColor("#9BE9A8"),
        Color.parseColor("#40C463"),
        Color.parseColor("#30A14E"),
        Color.parseColor("#216E39")
    )

    fun render(
        result: ContributionsResult,
        theme: GraphTheme,
        opts: Options,
        cornerRadius: Float,
        gapScale: Float
    ): Bitmap {
        val w = 1080
        val pad = 72
        val bg = if (opts.dark) Color.parseColor("#0D1117") else Color.WHITE
        val primary = if (opts.dark) Color.parseColor("#F0F6FC") else Color.parseColor("#1F2328")
        val muted = if (opts.dark) Color.parseColor("#8B949E") else Color.parseColor("#59636E")
        val levels = if (opts.dark) theme.levels else LIGHT_LEVELS
        val emptyBorder = if (opts.dark) Color.parseColor("#30363D") else Color.parseColor("#D0D7DE")

        val stats = Stats.compute(result.days)
        val total = NumberFormat.getInstance(Locale.US).format(result.totalLastYear)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primary
            textSize = 68f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = 40f
        }
        val statPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.accent.takeIf { opts.dark } ?: Color.parseColor("#1F883D")
            textSize = 54f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val statPaint2 = Paint(statPaint).apply { color = primary }
        val footPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = 32f
        }

        // Graph sized to fit the card width.
        val graph = GraphRenderer.render(
            result.days,
            scale = 3f,
            colors = levels,
            maxWeeks = 26,
            showMonthLabels = true,
            cornerRadius = cornerRadius,
            gapScale = gapScale,
            emptyBorder = emptyBorder
        )
        val graphTargetW = (w - pad * 2).toFloat()
        val graphScale = graphTargetW / graph.width
        val graphH = graph.height * graphScale

        var h = pad * 2 + 100 + 60 // title + subtitle
        h += (40 + graphH).toInt()
        if (opts.showTotal) h += 90
        if (opts.showStreak) h += 90
        h += 40 + 60 // footer gap + footer

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(bg)

        var y = pad + 80f
        if (opts.showUsername) {
            canvas.drawText("@${result.username}", pad.toFloat(), y, titlePaint)
            y += 62f
            canvas.drawText("GitHub Contributions", pad.toFloat(), y, subPaint)
            y += 40f
        } else {
            canvas.drawText("GitHub Contributions", pad.toFloat(), y, titlePaint)
            y += 62f
        }

        val dst = RectF(pad.toFloat(), y, pad + graphTargetW, y + graphH)
        canvas.drawBitmap(graph, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        graph.recycle()
        y += graphH + 56f

        if (opts.showTotal) {
            canvas.drawText("$total contributions", pad.toFloat(), y, statPaint)
            y += 90f
        }
        if (opts.showStreak) {
            canvas.drawText(
                "${stats.currentStreak} day streak • best ${stats.longestStreak}",
                pad.toFloat(), y, statPaint2
            )
            y += 90f
        }
        canvas.drawText("Made with GH-widgets", pad.toFloat(), y + 40f, footPaint)
        return bmp
    }

    /** Writes the card to cache (wiping older cards) and opens the share sheet. */
    fun share(context: Context, bitmap: Bitmap) {
        val dir = File(context.cacheDir, "share")
        dir.deleteRecursively()
        dir.mkdirs()
        val file = File(dir, "gh-widgets-card.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share contribution card"))
    }
}
