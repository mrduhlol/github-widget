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

/** Thrown when GitHub has no such user (HTTP 404) — shown as friendly text, never raw. */
class UserNotFoundException(username: String) :
    Exception("No GitHub user found for '@$username'. Check the spelling.")

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
            if (code == 404) throw UserNotFoundException(clean)
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) throw RuntimeException("Server returned HTTP $code.")
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

    private val MONTHS = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    /**
     * Draws a 7-row x N-col contribution grid into a bitmap.
     *
     * @param maxWeeks newest N weeks to draw (older weeks are dropped)
     * @param showMonthLabels month names above the first column of each month
     */
    fun render(
        days: List<Day>,
        scale: Float = 3f,
        colors: IntArray? = null,
        maxWeeks: Int = 26,
        showMonthLabels: Boolean = false
    ): Bitmap {
        if (days.isEmpty()) throw IllegalArgumentException("No days to render")
        val palette = colors ?: Themes.GREEN.levels

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        data class Dated(val cal: Calendar, val day: Day)

        // Parse + sort once, then bucket into Sunday-start week columns.
        val dated = days.mapNotNull { d ->
            try {
                val parsed = sdf.parse(d.date) ?: return@mapNotNull null
                val cal = Calendar.getInstance()
                cal.time = parsed
                Dated(cal, d)
            } catch (_: Exception) {
                null
            }
        }.sortedBy { it.cal.timeInMillis }

        val weeks = ArrayList<MutableMap<Int, Day>>()
        val weekMonth = ArrayList<Int>()
        for (e in dated) {
            val row = e.cal.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sunday..6=Saturday
            val last = weeks.lastOrNull()
            if (last == null || (row == 0 && last.isNotEmpty())) {
                weeks.add(mutableMapOf())
                weekMonth.add(e.cal.get(Calendar.MONTH))
            }
            weeks.last()[row] = e.day
        }

        val keep = maxWeeks.coerceAtLeast(4)
        val shown = if (weeks.size > keep) weeks.takeLast(keep) else weeks
        val shownMonths = if (weeks.size > keep) weekMonth.takeLast(keep) else weekMonth

        val gap = (2f * scale)
        val cell = (10f * scale)
        val radius = (3f * scale)
        val pad = (8f * scale)
        val labelStrip = if (showMonthLabels) (13f * scale) else 0f

        val w = (pad * 2 + shown.size * cell + (shown.size - 1) * gap).toInt()
        val h = (pad * 2 + labelStrip + 7 * cell + 6 * gap).toInt()
        val gridTop = pad + labelStrip

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT)

        if (showMonthLabels) {
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#8B949E")
                textSize = 9f * scale
            }
            var prevMonth = -1
            shownMonths.forEachIndexed { col, month ->
                if (month != prevMonth) {
                    val x = pad + col * (cell + gap)
                    canvas.drawText(MONTHS[month], x, pad + labelStrip - (3f * scale), labelPaint)
                    prevMonth = month
                }
            }
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f * scale
            color = Color.parseColor("#30363D")
        }

        shown.forEachIndexed { col, week ->
            for (row in 0..6) {
                val left = pad + col * (cell + gap)
                val top = gridTop + row * (cell + gap)
                val rect = RectF(left, top, left + cell, top + cell)
                val level = week[row]?.level?.coerceIn(0, 4) ?: 0
                paint.color = palette[level]
                canvas.drawRoundRect(rect, radius, radius, paint)
                if (level == 0) canvas.drawRoundRect(rect, radius, radius, stroke)
            }
        }
        return bmp
    }
}

/**
 * Draws the widget card background (rounded rect + hairline border) as a
 * bitmap, so card opacity can change without fading the text on top.
 * Set it on an ImageView behind the content with scaleType="fitXY".
 */
object WidgetBg {

    fun render(width: Int = 1024, height: Int = 512, bgColor: Int, cornerDp: Float = 48f): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
        canvas.drawRoundRect(rect, cornerDp, cornerDp, fill)
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.parseColor("#30363D")
        }
        canvas.drawRoundRect(rect, cornerDp, cornerDp, border)
        return bmp
    }
}

data class ContributionStats(
    val currentStreak: Int,
    val longestStreak: Int,
    val bestCount: Int,
    val activeDays: Int
)

/** Streaks, best day and active days — computed offline from the day list. */
object Stats {

    fun compute(days: List<Day>): ContributionStats {
        val sorted = days.sortedBy { it.date }
        var longest = 0
        var run = 0
        var active = 0
        var best = 0
        for (d in sorted) {
            if (d.count > 0) {
                run++
                active++
                if (d.count > best) best = d.count
            } else {
                run = 0
            }
            if (run > longest) longest = run
        }
        // Current streak from the newest day; a blank today doesn't kill
        // yesterday's streak (you just haven't committed yet today).
        var cur = 0
        var i = sorted.size - 1
        if (i >= 0 && sorted[i].count == 0) i--
        while (i >= 0 && sorted[i].count > 0) {
            cur++
            i--
        }
        return ContributionStats(cur, longest, best, active)
    }
}
