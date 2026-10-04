package com.example.githubwidget

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local cache for the last successful contribution fetch.
 *
 * Flow: API -> save() -> app + widget read instantly, even offline.
 * A background refresh then updates the cache + UI when network works.
 */
object Cache {

    private const val NAME = "gh_widget_cache"
    private const val KEY_DATA = "data_json"
    private const val KEY_TIME = "fetched_at"

    fun save(context: android.content.Context, result: ContributionsResult) {
        val days = JSONArray()
        for (d in result.days) {
            days.put(
                JSONObject()
                    .put("date", d.date)
                    .put("count", d.count)
                    .put("level", d.level)
            )
        }
        val root = JSONObject()
            .put("username", result.username)
            .put("total", result.totalLastYear)
            .put("days", days)
        context.getSharedPreferences(NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DATA, root.toString())
            .putLong(KEY_TIME, System.currentTimeMillis())
            .apply()
    }

    /** Returns the cached dataset + fetch timestamp, or null if nothing cached. */
    fun load(context: android.content.Context): Pair<ContributionsResult, Long>? {
        val prefs = context.getSharedPreferences(NAME, android.content.Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_DATA, null) ?: return null
        return try {
            val root = JSONObject(raw)
            val arr = root.getJSONArray("days")
            val days = ArrayList<Day>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                days.add(Day(o.getString("date"), o.optInt("count", 0), o.optInt("level", 0)))
            }
            val result = ContributionsResult(
                root.getString("username"),
                root.optInt("total", 0),
                days
            )
            result to prefs.getLong(KEY_TIME, 0L)
        } catch (_: Exception) {
            null
        }
    }
}

/** Human-readable relative timestamps: "just now", "8 min ago", "2h ago". */
object TimeAgo {

    fun format(ts: Long): String {
        if (ts <= 0L) return "never"
        val mins = ((System.currentTimeMillis() - ts) / 60000).coerceAtLeast(0)
        return when {
            mins < 1 -> "just now"
            mins < 60 -> "$mins min ago"
            mins < 60 * 24 -> "${mins / 60}h ago"
            mins < 60 * 24 * 30 -> "${mins / (60 * 24)}d ago"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ts))
        }
    }
}
