package com.example.githubwidget.widget

import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.PathParser
import java.net.URI

/** Lucide 0.468.0 paths (ISC license); additional simple social outlines use the same stroke. */
internal object ProfileIcons {
    private val paths = mapOf(
        "users" to PathParser.createPathFromPathData("M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M13 7a4 4 0 1 1-8 0a4 4 0 0 1 8 0 M22 21v-2a4 4 0 0 0-3-4 M16 3a4 4 0 0 1 0 8")!!,
        "repo" to PathParser.createPathFromPathData("M5 19V5a2 2 0 0 1 2-2h12v18H7a2 2 0 0 1 0-4h12 M9 7h6")!!,
        "terminal" to PathParser.createPathFromPathData("M5 2h14a3 3 0 0 1 3 3v14a3 3 0 0 1-3 3H5a3 3 0 0 1-3-3V5a3 3 0 0 1 3-3Z M7 8l4 4-4 4 M13 16h4")!!,
        "github" to PathParser.createPathFromPathData("M12 0a12 12 0 0 0-3.79 23.39c.6.11.82-.26.82-.58v-2.23c-3.34.73-4.04-1.42-4.04-1.42-.55-1.39-1.33-1.76-1.33-1.76-1.09-.74.08-.73.08-.73 1.2.08 1.83 1.23 1.83 1.23 1.07 1.83 2.81 1.3 3.49.99.11-.78.42-1.3.76-1.6-2.67-.3-5.47-1.33-5.47-5.93 0-1.31.47-2.38 1.23-3.22-.12-.3-.53-1.52.12-3.17 0 0 1.01-.32 3.3 1.23a11.5 11.5 0 0 1 6 0c2.29-1.55 3.3-1.23 3.3-1.23.65 1.65.24 2.87.12 3.17.77.84 1.23 1.91 1.23 3.22 0 4.61-2.81 5.62-5.49 5.92.43.38.82 1.1.82 2.22v3.31c0 .32.22.69.83.57A12 12 0 0 0 12 0Z")!!,
        "linkedin" to PathParser.createPathFromPathData("M16 8a6 6 0 0 1 6 6v7h-4v-7a2 2 0 0 0-2-2 2 2 0 0 0-2 2v7h-4v-7a6 6 0 0 1 6-6z M2.0 9.0h4.0v12.0h-4.0Z M2.0 4.0a2.0 2.0 0 1 0 4.0 0a2.0 2.0 0 1 0 -4.0 0")!!,
        "globe" to PathParser.createPathFromPathData("M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0 M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20 M2 12h20")!!,
        "link" to PathParser.createPathFromPathData("M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71 M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71")!!,
        "ellipsis" to PathParser.createPathFromPathData("M11.0 12.0a1.0 1.0 0 1 0 2.0 0a1.0 1.0 0 1 0 -2.0 0 M18.0 12.0a1.0 1.0 0 1 0 2.0 0a1.0 1.0 0 1 0 -2.0 0 M4.0 12.0a1.0 1.0 0 1 0 2.0 0a1.0 1.0 0 1 0 -2.0 0")!!,
        "instagram" to PathParser.createPathFromPathData("M7 2h10a5 5 0 0 1 5 5v10a5 5 0 0 1-5 5H7a5 5 0 0 1-5-5V7a5 5 0 0 1 5-5Z M16 12a4 4 0 1 1-8 0a4 4 0 1 1 8 0 M17.5 6.5h.01")!!,
        "youtube" to PathParser.createPathFromPathData("M5 4h14a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H5a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3Z M10 8l6 4-6 4Z")!!,
        "x" to PathParser.createPathFromPathData("M4 3h4l12 18h-4Z M20 3 4 21")!!
    )
    private fun kind(url: String?): String {
        if (url == null) return "ellipsis"
        val host = runCatching { URI(url).host?.lowercase()?.removePrefix("www.") }.getOrNull()
        return when (host) {
            "github.com" -> "github"
            "linkedin.com" -> "linkedin"
            "x.com", "twitter.com" -> "x"
            "instagram.com" -> "instagram"
            "youtube.com", "youtu.be" -> "youtube"
            else -> "link"
        }
    }
    fun label(url: String): String = when (kind(url)) {
        "github" -> "GitHub"
        "linkedin" -> "LinkedIn"
        "x" -> "X"
        "instagram" -> "Instagram"
        "youtube" -> "YouTube"
        else -> runCatching { URI(url).host?.removePrefix("www.") }.getOrNull() ?: "Website"
    }
    fun draw(canvas: Canvas, url: String?, x: Float, y: Float, size: Float, color: Int) {
        drawSymbol(canvas, kind(url), x, y, size, color)
    }

    fun drawSymbol(canvas: Canvas, symbol: String, x: Float, y: Float, size: Float, color: Int) {
        val save = canvas.save()
        canvas.translate(x - size/2, y - size/2)
        canvas.scale(size/24f, size/24f)
        canvas.drawPath(paths.getValue(symbol), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = if (symbol == "github") Paint.Style.FILL else Paint.Style.STROKE
            strokeWidth = 1.7f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        })
        canvas.restoreToCount(save)
    }
}
