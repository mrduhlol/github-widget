package com.example.githubwidget.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

/**
 * Network access. No sign-in or token needed:
 *  - contributions: https://github-contributions-api.jogruber.de (public graph data)
 *  - profile:       https://api.github.com/users/<name> (name + avatar)
 *
 * All functions block — call them from Dispatchers.IO.
 */
object GitHubApi {

    private const val USER_AGENT = "gh-widgets-android/3"

    /** Strips spaces, a leading "@" and a pasted profile URL down to the bare login. */
    fun cleanUsername(raw: String): String =
        raw.trim()
            .removePrefix("https://").removePrefix("http://")
            .removePrefix("www.").removePrefix("github.com/")
            .trimStart('@')
            .substringBefore('/')
            .substringBefore('?')
            .trim()

    /** GitHub logins: letters, digits and single hyphens, max 39 chars. */
    fun isValidUsername(name: String): Boolean =
        name.length in 1..39 && name.matches(Regex("^[A-Za-z0-9](?:[A-Za-z0-9]|-(?=[A-Za-z0-9]))*$"))

    fun fetchContributions(username: String): Contributions {
        val body = get("https://github-contributions-api.jogruber.de/v4/${encode(username)}?y=last", username)
        val root = JSONObject(body)
        val total = root.optJSONObject("total")?.optInt("lastYear", 0) ?: 0
        val arr = root.optJSONArray("contributions") ?: throw FetchError.Server(200)
        val today = LocalDate.now()
        val days = ArrayList<Day>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val date = runCatching { LocalDate.parse(o.getString("date")) }.getOrNull() ?: continue
            // The API runs on UTC; never show days that haven't happened locally yet.
            if (date.isAfter(today)) continue
            days.add(Day(date, o.optInt("count", 0), o.optInt("level", 0).coerceIn(0, 4)))
        }
        days.sortBy { it.date }
        return Contributions(username, total, days)
    }

    /** Accept only web links before displaying or opening public profile metadata. */
    fun webUrl(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() && it != "null" } ?: return null
        val candidate = if ("://" in value) value else "https://$value"
        val uri = runCatching { java.net.URI(candidate) }.getOrNull() ?: return null
        return candidate.takeIf { uri.scheme?.lowercase() in listOf("https", "http") && !uri.host.isNullOrBlank() && uri.userInfo == null }
    }

    internal fun twitterProfileUrl(username: String?): String? = username
        ?.takeIf { it != "null" && it.matches(Regex("[A-Za-z0-9_]{1,15}")) }
        ?.let { "https://x.com/$it" }

    fun fetchProfile(username: String): Profile {
        val o = JSONObject(get("https://api.github.com/users/${encode(username)}", username))
        val links = runCatching {
            val accounts = org.json.JSONArray(get("https://api.github.com/users/${encode(username)}/social_accounts", username))
            (0 until accounts.length()).mapNotNull { i ->
                val account = accounts.getJSONObject(i)
                webUrl(account.optString("url"))?.let { ProfileLink(account.optString("provider", "Social"), it) }
            }
        }.getOrDefault(emptyList()).toMutableList()
        twitterProfileUrl(o.optString("twitter_username"))?.let { url ->
            if (links.none { link -> link.url == url }) links.add(ProfileLink("X", url))
        }
        return Profile(
            login = o.optString("login", username),
            name = o.optString("name").takeIf { it.isNotBlank() && it != "null" },
            avatarUrl = o.optString("avatar_url", "https://github.com/${encode(username)}.png"),
            publicRepos = if (o.has("public_repos") && !o.isNull("public_repos")) o.optInt("public_repos").coerceAtLeast(0) else null,
            followers = if (o.has("followers") && !o.isNull("followers")) o.optInt("followers").coerceAtLeast(0) else null,
            following = if (o.has("following") && !o.isNull("following")) o.optInt("following").coerceAtLeast(0) else null,
            website = webUrl(o.optString("blog")),
            socialLinks = links.distinctBy { it.url }.take(8),
            fetchedAt = System.currentTimeMillis(),
        )
    }

    private fun reviewQuery(username: String) = "is:pr is:open review-requested:$username"

    fun reviewSearchUrl(username: String) =
        "https://github.com/search?type=pullrequests&q=${encode(reviewQuery(username))}"

    internal fun fetchReviewRequests(username: String): ReviewResults {
        require(isValidUsername(username))
        val query = encode(reviewQuery(username))
        val response = get("https://api.github.com/search/issues?q=$query&sort=updated&order=desc&per_page=8", username)
        val json = JSONObject(response)
        if (json.optBoolean("incomplete_results")) throw FetchError.Server(200)
        return ReviewResults.fromJson(json, System.currentTimeMillis())
    }

    /** Downloads a square avatar; null on any failure (the UI falls back to initials). */
    fun fetchAvatar(avatarUrl: String, sizePx: Int = 160): Bitmap? = try {
        val sep = if (avatarUrl.contains('?')) '&' else '?'
        val conn = open("$avatarUrl${sep}s=$sizePx")
        try {
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.use { BitmapFactory.decodeStream(it) }
        } finally {
            conn.disconnect()
        }
    } catch (_: Exception) {
        null
    }

    private fun encode(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", USER_AGENT)
        }

    private fun get(url: String, username: String): String {
        val conn = try {
            open(url)
        } catch (_: IOException) {
            throw FetchError.Offline
        }
        try {
            val code = try {
                conn.responseCode
            } catch (_: IOException) {
                throw FetchError.Offline
            }
            when {
                code == 404 -> throw FetchError.NotFound(username)
                code == 403 || code == 429 -> throw FetchError.RateLimited
                code !in 200..299 -> throw FetchError.Server(code)
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: FetchError) {
            throw e
        } catch (_: IOException) {
            throw FetchError.Offline
        } finally {
            conn.disconnect()
        }
    }
}
