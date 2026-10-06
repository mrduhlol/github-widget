package com.example.githubwidget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
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

    /**
     * GitHub avatar, disk-cached per user so cards work offline.
     * Call off the main thread — it may hit the network on first use.
     */
    fun fetchAvatar(context: Context, username: String): Bitmap? {
        val clean = username.trim().trimStart('@')
        if (clean.isEmpty()) return null
        return try {
            val dir = File(context.cacheDir, "avatars").apply { mkdirs() }
            val file = File(dir, "$clean.png")
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else {
                val url = URL("https://github.com/$clean.png?size=256")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "github-widget-android/1.0")
                }
                try {
                    if (conn.responseCode !in 200..299) return null
                    val bmp = conn.inputStream.use { BitmapFactory.decodeStream(it) }
                        ?: return null
                    try {
                        FileOutputStream(file).use { out ->
                            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                    } catch (_: Exception) {
                        // Memory copy still works even if the disk write fails.
                    }
                    // Keep the cache small: newest 20 avatars only.
                    dir.listFiles()
                        ?.sortedByDescending { it.lastModified() }
                        ?.drop(20)
                        ?.forEach { it.delete() }
                    bmp
                } finally {
                    conn.disconnect()
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Perfect-circle crop with a crisp accent ring. Never returns null. */
    private fun circleAvatar(src: Bitmap?, size: Int, ringColor: Int): Bitmap? {
        if (src == null || src.isRecycled) return null
        val side = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(
            src,
            (src.width - side) / 2, (src.height - side) / 2, side, side
        )
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val path = Path().apply {
            addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(square, null, RectF(0f, 0f, size.toFloat(), size.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
        if (!square.isRecycled) square.recycle()
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (size * 0.035f).coerceAtLeast(4f)
            color = ringColor
        }
        val inset = ring.strokeWidth / 2f
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - inset, ring)
        return out
    }

    fun render(
        result: ContributionsResult,
        theme: GraphTheme,
        opts: Options,
        cornerRadius: Float,
        gapScale: Float,
        customLevels: IntArray? = null,
        avatar: Bitmap? = null
    ): Bitmap {
        val w = 1080
        val pad = 72
        val bg = if (opts.dark) Color.parseColor("#0D1117") else Color.WHITE
        val primary = if (opts.dark) Color.parseColor("#F0F6FC") else Color.parseColor("#1F2328")
        val muted = if (opts.dark) Color.parseColor("#8B949E") else Color.parseColor("#59636E")
        val accent = if (opts.dark) theme.accent else Color.parseColor("#1F883D")
        val levels = customLevels
            ?: if (opts.dark) theme.levels else LIGHT_LEVELS
        val emptyBorder = if (opts.dark) Color.parseColor("#30363D") else Color.parseColor("#D0D7DE")

        val stats = Stats.compute(result.days)
        val records = Achievements.records(result.days)
        val total = NumberFormat.getInstance(Locale.US).format(result.totalLastYear)
        val avatarBmp = circleAvatar(avatar, 176, accent)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primary
            textSize = 74f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = 40f
        }
        val bigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            textSize = 62f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val bigPaint2 = Paint(bigPaint).apply { color = primary }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = 32f
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            alpha = 90
            strokeWidth = 3f
        }
        val footPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = 30f
        }

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
        val graphH = graph.height * (graphTargetW / graph.width)

        val headerH = if (opts.showUsername) 220 else 100
        var h = pad * 2 + headerH + 30 // header + divider gap
        h += (40 + graphH).toInt()
        if (opts.showTotal) h += 150
        if (opts.showStreak) h += 76
        h += 40 + 60 // footer gap + footer

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(bg)

        var y = pad.toFloat()
        if (opts.showUsername) {
            if (avatarBmp != null) {
                canvas.drawBitmap(avatarBmp, pad.toFloat(), y, null)
                if (!avatarBmp.isRecycled) avatarBmp.recycle()
            }
            val tx = pad + if (avatarBmp != null || avatar != null) 216 else 0
            canvas.drawText("@${result.username}", tx.toFloat(), y + 92f, titlePaint)
            canvas.drawText("GitHub contributions", tx.toFloat(), y + 152f, subPaint)
            y += 220f
        } else {
            canvas.drawText("GitHub contributions", pad.toFloat(), y + 70f, titlePaint)
            y += 100f
        }

        canvas.drawLine(pad.toFloat(), y, (w - pad).toFloat(), y, linePaint)
        y += 40f

        val dst = RectF(pad.toFloat(), y, pad + graphTargetW, y + graphH)
        canvas.drawBitmap(graph, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        graph.recycle()
        y += graphH + 56f

        if (opts.showTotal) {
            // Three-up stat band: total | streak | active days.
            val colW = graphTargetW / 3f
            val values = listOf(
                total to "contributions",
                "${stats.currentStreak}" to "day streak",
                "${stats.activeDays}" to "active days"
            )
            values.forEachIndexed { i, (v, label) ->
                val x = pad + i * colW
                val paint = if (i == 0) bigPaint else bigPaint2
                canvas.drawText(v, x, y, paint)
                canvas.drawText(label, x, y + 48f, labelPaint)
            }
            y += 150f
        }
        if (opts.showStreak) {
            val extras = ArrayList<String>()
            if (records.bestCount > 0) {
                extras.add("Best day ${records.bestCount}")
            }
            if (records.longestStreak > 0) {
                extras.add("${records.longestStreak}-day best streak")
            }
            if (records.bestMonth.isNotEmpty()) {
                extras.add(records.bestMonth)
            }
            if (extras.isNotEmpty()) {
                canvas.drawText(extras.joinToString("  •  "), pad.toFloat(), y, subPaint)
                y += 76f
            }
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
