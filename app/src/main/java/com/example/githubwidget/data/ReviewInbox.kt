package com.example.githubwidget.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class ReviewRequest(
    val title: String,
    val repository: String,
    val number: String,
    val url: String,
    val excerpt: String? = null,
)

data class ReviewResults(val total: Int, val requests: List<ReviewRequest>, val fetchedAt: Long) {
    internal companion object {
        private val pullUrl = Regex("https://github\\.com/([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)/pull/([1-9][0-9]*)")
        private val whitespace = Regex("\\s+")
        private const val EXCERPT_LIMIT = 220

        fun fromJson(json: JSONObject, fetchedAt: Long): ReviewResults {
            val items = json.getJSONArray("items")
            val requests = (0 until items.length()).mapNotNull { index ->
                val item = items.getJSONObject(index)
                val url = item.getString("html_url")
                val match = pullUrl.matchEntire(url) ?: return@mapNotNull null
                val body = (item.opt("body_text") as? String).orEmpty().replace(whitespace, " ").trim()
                val excerpt = when {
                    body.isEmpty() -> null
                    body.codePointCount(0, body.length) <= EXCERPT_LIMIT -> body
                    else -> body.substring(0, body.offsetByCodePoints(0, EXCERPT_LIMIT - 1)).trimEnd() + "…"
                }
                ReviewRequest(item.getString("title"), match.groupValues[1], match.groupValues[2], url, excerpt)
            }
            return ReviewResults(json.getInt("total_count"), requests, fetchedAt)
        }
    }
}

data class ReviewInboxState(val username: String, val results: ReviewResults? = null, val failed: Boolean = false)

internal object ReviewInbox {
    private const val MIN_REFRESH_MS = 60_000L
    private val lock = Mutex()
    private val current = MutableStateFlow<ReviewInboxState?>(null)
    val state = current.asStateFlow()

    private fun prefs(context: Context) = context.getSharedPreferences("review_inbox", Context.MODE_PRIVATE)

    fun cached(context: Context, username: String): ReviewInboxState {
        current.value?.takeIf { it.username == username }?.let { return it }
        val results = runCatching {
            val preferences = prefs(context)
            if (preferences.getString("username", null) != username) return@runCatching null
            val json = JSONObject(preferences.getString("results", null) ?: return@runCatching null)
            ReviewResults.fromJson(json, json.getLong("fetchedAt"))
        }.getOrNull()
        return ReviewInboxState(username, results)
    }

    suspend fun refresh(context: Context, username: String) {
        if (!GitHubApi.isValidUsername(username)) return
        lock.withLock {
            if (Repository.username.value != username) return@withLock
            val previous = cached(context, username)
            if (!previous.failed && previous.results?.let { System.currentTimeMillis() - it.fetchedAt < MIN_REFRESH_MS } == true) {
                current.value = previous
                return@withLock
            }
            current.value = previous.copy(failed = false)
            try {
                val data = withContext(Dispatchers.IO) { GitHubApi.fetchReviewRequests(username) }
                if (Repository.username.value != username) return@withLock
                val items = JSONArray()
                data.requests.forEach {
                    items.put(JSONObject().put("html_url", it.url).put("title", it.title).put("body_text", it.excerpt))
                }
                val json = JSONObject().put("items", items).put("total_count", data.total).put("fetchedAt", data.fetchedAt)
                prefs(context).edit().putString("username", username).putString("results", json.toString()).apply()
                current.value = ReviewInboxState(username, data)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (Repository.username.value == username) current.value = previous.copy(failed = true)
            }
        }
    }
}
