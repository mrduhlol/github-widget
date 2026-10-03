package com.example.githubwidget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class Day(val date: String, val count: Int, val level: Int)

data class ContributionsResult(
    val username: String,
    val totalLastYear: Int,
    val days: List<Day>
)

/**
 * Fetches from the free, no-token API:
 *   https://github-contributions-api.jogruber.de/v4/<user>?y=last
 */
object GithubApi {

    fun fetch(username: String): ContributionsResult {
        val clean = username.trim().trimStart('@')
        require(clean.isNotEmpty()) { "Username is empty" }
        val url = URL("https://github-contributions-api.jogruber.de/v4/$clean?y=last")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "github-widget-android/1.0")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) throw RuntimeException("HTTP $code: $body")
            return parse(clean, JSONObject(body))
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(username: String, root: JSONObject): ContributionsResult {
        val total = root.optJSONObject("total")?.optInt("lastYear", 0) ?: 0
        val arr = root.optJSONArray("contributions") ?: throw RuntimeException("Bad API response")
        val days = ArrayList<Day>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            days.add(Day(o.getString("date"), o.optInt("count", 0), o.optInt("level", 0)))
        }
        return ContributionsResult(username, total, days)
    }
}

object GraphRenderer {

    // GitHub dark-mode-ish greens, works on light + dark homescreens.
    private val COLORS = intArrayOf(
        Color.parseColor("#161B22"), // 0 - empty (with border drawn separately)
        Color.parseColor("#0E4429"),
        Color.parseColor("#006D32"),
        Color.parseColor("#26A641"),
        Color.parseColor("#39D353")
    )

    /**
     * Draws a 7-row x N-col contribution grid into a bitmap.
     * Width scales with weeks (~53). Caller should put it in an ImageView
     * with adjustViewBounds + fixed height.
     */
    fun render(days: List<Day>, scale: Float = 3f): Bitmap {
        if (days.isEmpty()) throw IllegalArgumentException("No days to render")

        // Group days into week columns starting Sunday, like GitHub.
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()

        // Bucket by week index.
        data class Cell(val row: Int, val day: Day)
        val weeks = ArrayList<MutableMap<Int, Cell>>()
        var weekOffset = -1
        var firstSundaySeen = false

        // Sort by date just in case.
        val sorted = days.sortedBy { it.date }
        for (d in sorted) {
            val date = try { sdf.parse(d.date) } catch (_: Exception) { null } ?: continue
            cal.time = date
            val dow = cal.get(Calendar.DAY_OF_WEEK) // 1=Sunday..7=Saturday
            val row = dow - 1
            if (!firstSundaySeen) {
                // Start a new week at the first day; pad later during draw.
                weeks.add(mutableMapOf())
                weekOffset = weeks.size - 1
                firstSundaySeen = true
                weeks[weekOffset][row] = Cell(row, d)
                if (row == 6) { /* week complete, next day starts new week */ }
            } else {
                val last = weeks.last()
                if (row == 0 && last.isNotEmpty()) {
                    weeks.add(mutableMapOf())
                }
                weeks.last()[row] = Cell(row, d)
            }
        }
        // Drop the partial leading week handling: just draw what we have, max last 26 weeks
        // so the widget stays readable on a phone. 26 weeks x 7 = ~half year.
        val shown = if (weeks.size > 26) weeks.takeLast(26) else weeks

        val gap = (2f * scale)
        val cell = (10f * scale)
        val radius = (3f * scale)
        val pad = (8f * scale)

        val w = (pad * 2 + shown.size * cell + (shown.size - 1) * gap).toInt()
        val h = (pad * 2 + 7 * cell + 6 * gap).toInt()

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f * scale
            color = Color.parseColor("#30363D")
        }

        shown.forEachIndexed { col, week ->
            for (row in 0..6) {
                val left = pad + col * (cell + gap)
                val top = pad + row * (cell + gap)
                val rect = RectF(left, top, left + cell, top + cell)
                val level = week[row]?.day?.level?.coerceIn(0, 4) ?: 0
                paint.color = COLORS[level]
                canvas.drawRoundRect(rect, radius, radius, paint)
                if (level == 0) canvas.drawRoundRect(rect, radius, radius, stroke)
            }
        }
        return bmp
    }
}
