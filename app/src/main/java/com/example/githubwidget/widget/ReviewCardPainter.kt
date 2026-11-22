package com.example.githubwidget.widget

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.ColorUtils
import com.example.githubwidget.data.ReviewInboxState
import com.example.githubwidget.data.ReviewRequest
import com.example.githubwidget.data.ReviewResults
import com.example.githubwidget.design.WidgetDesign
import com.example.githubwidget.design.WidgetLayout
import java.time.Instant

internal object ReviewCardPainter {
    fun orderedRequests(inbox: ReviewInboxState?) =
        inbox?.results?.requests.orEmpty().sortedBy { it.openedAt ?: Instant.MAX }

    fun queueRows(inbox: ReviewInboxState?, height: Float) =
        orderedRequests(inbox).take((height / 48f).toInt().minus(1).coerceIn(1, 3))

    val example by lazy {
        val now = Instant.now()
        val requests = listOf(
            ReviewRequest("Keep the graph visible after rotation", "octocat/github-widget", "128",
                "https://github.com/octocat/github-widget/pull/128", author = "Maya", openedAt = now.minusSeconds(86_400)),
            ReviewRequest("Add keyboard shortcuts", "octocat/dashboard", "76",
                "https://github.com/octocat/dashboard/pull/76", author = "Dev", openedAt = now.minusSeconds(18_000)),
            ReviewRequest("Make setup easier to follow", "octocat/docs", "42",
                "https://github.com/octocat/docs/pull/42", author = "Sara", openedAt = now.minusSeconds(7_200)),
        )
        ReviewInboxState("octocat", ReviewResults(requests.size, requests, now.toEpochMilli()))
    }

    fun draw(canvas: Canvas, scale: Float, width: Float, height: Float, design: WidgetDesign, inbox: ReviewInboxState?) {
        if (width < 1f || height < 1f) return
        val save = canvas.save()
        canvas.scale(scale, scale)
        try {
            Card(canvas, width, height, design, inbox).draw()
        } finally {
            canvas.restoreToCount(save)
        }
    }

    private class Card(
        val canvas: Canvas,
        val width: Float,
        val height: Float,
        val design: WidgetDesign,
        val inbox: ReviewInboxState?,
    ) {
        val palette = design.reviewColor
        val next = design.layout == WidgetLayout.REVIEW_NEXT
        val textColor = if (next) palette.ink else 0xFFF6EBDD.toInt()
        val quiet = if (next) palette.ink else ColorUtils.blendARGB(textColor, palette.surface, .23f)
        val pad = minOf(width * .09f, height * .13f, 18f)
        val right = width - pad
        val interior = right - pad
        val now = Instant.now()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        fun draw() {
            val radius = minOf(20f, height * .2f, width * .2f)
            val bounds = RectF(0f, 0f, width, height)
            canvas.clipPath(Path().apply { addRoundRect(bounds, radius, radius, Path.Direction.CW) })
            canvas.drawColor(if (next) palette.paper else palette.surface)
            if (next) {
                paint.color = ColorUtils.setAlphaComponent(0xFFFFFFFF.toInt(), 26)
                paint.strokeWidth = 1f
                var x = -height
                while (x < width) {
                    canvas.drawLine(x, height, x + height, 0f, paint)
                    x += 15f
                }
            }
            val requests = orderedRequests(inbox)
            if (requests.isEmpty()) {
                line(design.layout.label, pad, pad + 13f, interior, 13f, bold = true)
                val status = when {
                    inbox == null || inbox.username.isBlank() -> "Connect a GitHub profile"
                    inbox.failed && inbox.results == null -> "Couldn’t check GitHub"
                    inbox.results == null -> "Checking GitHub…"
                    inbox.results.total == 0 -> "All caught up"
                    else -> "Open GitHub to see requests"
                }
                line(status, pad, maxOf(height * .64f, pad + 30f), interior, 17f)
            } else if (next) {
                drawNext(requests.first(), inbox?.results?.total ?: requests.size)
            } else {
                drawQueue(queueRows(inbox, height))
            }
        }

        private fun drawNext(request: ReviewRequest, total: Int) {
            val header = pad + 12f
            val badge = "$total to review"
            val badgeWidth = minOf(interior * .44f, 88f)
            line("Up next", pad, header, interior - badgeWidth - 5f, 13f, bold = true)
            if (badgeWidth >= 48f) {
                paint.color = ColorUtils.setAlphaComponent(palette.ink, 115)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                canvas.drawRoundRect(right - badgeWidth, header - 13f, right, header + 5f, 4f, 4f, paint)
                paint.style = Paint.Style.FILL
            }
            line(badge, right - badgeWidth + 4f, header, badgeWidth - 9f, 11f, alignRight = true)

            val compact = height < 125f
            val personY = if (compact) header + 18f else maxOf(header + 25f, height * .35f)
            val author = request.author?.takeIf { height >= 96f }
            if (author != null) {
                initials(author, pad + 10f, personY - 4f, 10f, palette.ink, palette.paper)
                val label = if (compact) "$author · review requested" else "$author is waiting for your review"
                line(label, pad + 26f, personY, interior - 26f, 11f)
            }
            val top = if (author == null) header + 9f else personY + 8f
            val footer = height - pad
            val showFooter = height >= 88f
            val titleBottom = footer - if (showFooter) 15f else 0f
            val availableHeight = titleBottom - top
            if (availableHeight >= 12f) {
                val lines = if (compact) 1 else 2
                val titleSize = minOf(32f, width * .095f, availableHeight / (lines * 1.17f)).coerceAtLeast(11f)
                val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = Typeface.SERIF
                    textSize = titleSize
                    color = textColor
                }
                val layout = StaticLayout.Builder.obtain(request.title, 0, request.title.length, titlePaint, interior.toInt().coerceAtLeast(1))
                    .setIncludePad(false)
                    .setMaxLines(lines)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .build()
                canvas.save()
                canvas.translate(pad, top)
                layout.draw(canvas)
                canvas.restore()
            }
            if (showFooter) {
                paint.color = ColorUtils.setAlphaComponent(palette.ink, 90)
                paint.strokeWidth = 1f
                canvas.drawLine(pad, footer - 12f, right, footer - 12f, paint)
                val age = age(request)?.let { "Opened $it ago" }
                val repoWidth = if (age == null) interior else interior * .58f
                line(repo(request), pad, footer, repoWidth, 10f)
                age?.let { line(it, pad + repoWidth + 4f, footer, interior - repoWidth - 4f, 10f, alignRight = true) }
            }
        }

        private fun drawQueue(requests: List<ReviewRequest>) {
            val row = height / (requests.size + 1)
            val baseline = row * .56f + 4f
            line("Review Queue", pad, baseline, interior * .65f, 14f, bold = true)
            if (inbox?.failed == true) {
                line("Last check", pad + interior * .65f, baseline, interior * .35f, 10f, alignRight = true)
            }
            requests.forEachIndexed { index, request ->
                val top = (index + 1) * row
                val middle = top + row / 2f
                if (index == 0) {
                    paint.color = palette.highlight
                    canvas.drawRoundRect(pad * .52f, top + 2f, width - pad * .52f, top + row - 2f, 10f, 10f, paint)
                } else {
                    paint.color = ColorUtils.setAlphaComponent(palette.paper, 55)
                    paint.strokeWidth = 1f
                    canvas.drawLine(pad, top, right, top, paint)
                }
                val hasAvatar = interior >= 118f && row >= 30f
                val left = if (hasAvatar) pad + 31f else pad
                if (hasAvatar) initials(request.author ?: request.repository.substringAfter('/'),
                    pad + 11f, middle, 11f, palette.highlight, palette.paper)
                val age = age(request)
                val ageWidth = if (age == null) 14f else 38f
                val contentWidth = right - left - ageWidth - 4f
                line(request.title, left, middle + if (row >= 30f) -1f else 4f, contentWidth, 12f, bold = true)
                if (row >= 30f) line(repo(request), left, middle + 12f, contentWidth, 10f, color = quiet)
                line(age?.let { "$it ↗" } ?: "↗", right - ageWidth, middle + 4f, ageWidth, 10f,
                    color = palette.paper, alignRight = true)
            }
        }

        private fun initials(author: String, x: Float, y: Float, radius: Float, background: Int, foreground: Int) {
            paint.color = background
            canvas.drawCircle(x, y, radius, paint)
            val initials = author.take(2).uppercase(java.util.Locale.ROOT)
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textSize = 9f
            paint.color = foreground
            canvas.drawText(initials, x - paint.measureText(initials) / 2f, y + 3f, paint)
        }

        private fun repo(request: ReviewRequest) = "${request.repository.substringAfter('/')} #${request.number}"

        private fun age(request: ReviewRequest): String? {
            val opened = request.openedAt ?: return null
            val minutes = ((now.epochSecond - opened.epochSecond) / 60).coerceAtLeast(1)
            return when {
                minutes >= 1_440 -> "${minutes / 1_440}d"
                minutes >= 60 -> "${minutes / 60}h"
                else -> "${minutes}m"
            }
        }

        private fun line(value: String, x: Float, y: Float, space: Float, size: Float, color: Int = textColor,
                         bold: Boolean = false, alignRight: Boolean = false) {
            if (space <= 0f || y > height) return
            paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            paint.textSize = size
            paint.color = color
            val shown = WidgetRenderer.ellipsize(value, paint, space)
            canvas.drawText(shown, if (alignRight) x + space - paint.measureText(shown) else x, y, paint)
        }
    }
}
