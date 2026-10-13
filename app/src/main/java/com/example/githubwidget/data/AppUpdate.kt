package com.example.githubwidget.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Info about a newer release on GitHub, if one exists. */
data class AppUpdateInfo(
    val latestVersion: String,
    val downloadUrl: String,
    val pageUrl: String,
    val notes: String?,
)

/**
 * Checks GitHub Releases for a newer APK. Same networking style as [GitHubApi]:
 * plain HttpURLConnection, no new dependencies. The app is sideloaded from
 * GitHub Releases (not the Play Store), so Play's in-app updates don't apply —
 * instead we link the user to the new APK.
 */
object AppUpdate {

    private const val API_URL = "https://api.github.com/repos/mrduhlol/github-widget/releases/latest"

    suspend fun check(context: Context): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val current = currentVersion(context) ?: return@runCatching null
            val conn = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 12_000
                setRequestProperty("User-Agent", "gh-widgets-android/3")
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            val body = try {
                if (conn.responseCode !in 200..299) throw FetchError.Server(conn.responseCode)
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
            val root = JSONObject(body)
            val latest = root.optString("tag_name").trim().takeIf { it.isNotBlank() } ?: return@runCatching null
            if (!isNewer(latest, current)) return@runCatching null
            val pageUrl = root.optString("html_url").takeIf { it.isNotBlank() }
                ?: "https://github.com/mrduhlol/github-widget/releases/latest"
            AppUpdateInfo(
                latestVersion = latest.trimStart('v', 'V'),
                downloadUrl = apkUrl(root) ?: pageUrl,
                pageUrl = pageUrl,
                notes = root.optString("body").takeIf { it.isNotBlank() },
            )
        }
    }

    fun currentVersion(context: Context): String? = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()

    // ---------------------------------------------------------- auto-check state

    private const val PREFS = "app_update_prefs"
    private const val KEY_LAST_CHECK = "last_check_at"
    private const val KEY_PROMPTED = "prompted_version"
    private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** True when enough time passed since the last auto-check (manual checks ignore this). */
    fun shouldAutoCheck(context: Context, now: Long = System.currentTimeMillis()): Boolean =
        now - prefs(context).getLong(KEY_LAST_CHECK, 0L) >= CHECK_INTERVAL_MS

    fun markChecked(context: Context, now: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_LAST_CHECK, now).apply()
    }

    /** True if the auto dialog was already shown/dismissed for this version. */
    fun wasPrompted(context: Context, version: String): Boolean =
        prefs(context).getString(KEY_PROMPTED, null) == version

    fun markPrompted(context: Context, version: String) {
        prefs(context).edit().putString(KEY_PROMPTED, version).apply()
    }

    /** Prefers a direct .apk asset; falls back to the release page. */
    private fun apkUrl(root: JSONObject): String? {
        val assets = root.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val url = assets.optJSONObject(i)?.optString("browser_download_url").orEmpty()
            if (url.endsWith(".apk", ignoreCase = true)) return url
        }
        return null
    }

    /** True when [latest] is a higher semantic version than [current]. */
    fun isNewer(latestRaw: String, currentRaw: String): Boolean =
        compareVersions(normalize(latestRaw), normalize(currentRaw)) > 0

    private fun normalize(v: String) = v.trim().trimStart('v', 'V')

    private fun compareVersions(a: String, b: String): Int {
        val aParts = a.split('.', '-', '+')
        val bParts = b.split('.', '-', '+')
        for (i in 0 until maxOf(aParts.size, bParts.size)) {
            val x = aParts.getOrNull(i)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
            val y = bParts.getOrNull(i)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
            if (x != y) return x.compareTo(y)
        }
        return 0
    }
}
