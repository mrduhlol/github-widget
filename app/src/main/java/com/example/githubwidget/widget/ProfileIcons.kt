package com.example.githubwidget.widget

import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.PathParser
import java.net.URI

/** Lucide 0.468.0 paths (ISC license); additional simple social outlines use the same stroke. */
internal object ProfileIcons {
    private val paths = mapOf(
        "github" to PathParser.createPathFromPathData("M15 22v-4a4.8 4.8 0 0 0-1-3.5c3 0 6-2 6-5.5.08-1.25-.27-2.48-1-3.5.28-1.15.28-2.35 0-3.5 0 0-1 0-3 1.5-2.64-.5-5.36-.5-8 0C6 2 5 2 5 2c-.3 1.15-.3 2.35 0 3.5A5.403 5.403 0 0 0 4 9c0 3.5 3 5.5 6 5.5-.39.49-.68 1.05-.85 1.65-.17.6-.22 1.23-.15 1.85v4 M9 18c-4.51 2-5-2-7-2")!!,
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
            else -> "globe"
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
        val save = canvas.save()
        canvas.translate(x - size/2, y - size/2)
        canvas.scale(size/24f, size/24f)
        canvas.drawPath(paths.getValue(kind(url)), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 1.7f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        })
        canvas.restoreToCount(save)
    }
}
