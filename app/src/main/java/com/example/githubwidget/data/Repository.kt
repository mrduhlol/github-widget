package com.example.githubwidget.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.githubwidget.design.NumberFont
import com.example.githubwidget.design.WidgetDesign
import com.example.githubwidget.widget.RefreshWorker
import com.example.githubwidget.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

sealed interface RefreshState {
    object Idle : RefreshState
    object Loading : RefreshState
    data class Failed(val message: String) : RefreshState
}

/** A looked-up account that hasn't been saved yet (onboarding "Is this you?"). */
data class Lookup(val data: UserData, val avatar: Bitmap?)

/**
 * Single source of truth for the app and the widget: the username, the cached
 * graph, the avatar, the widget design and the app settings.
 *
 * Everything is persisted locally, so the widget always has something to show
 * even when the phone is offline.
 */
@SuppressLint("StaticFieldLeak") // Holds only the Application context, never an Activity.
object Repository {

    /** Old installs stored the username here — keep reading it so updates stay signed in. */
    private const val PREFS = "github_widget_prefs"
    private const val KEY_USERNAME = "username"
    private const val KEY_DESIGN = "design_v3"
    private const val KEY_REFRESH_HOURS = "refresh_hours"
    private const val KEY_NUMBER_FONT = "number_font"
    private const val CACHE_FILE = "contributions_v3.json"
    private const val AVATAR_FILE = "avatar.png"

    val REFRESH_CHOICES = listOf(1, 3, 6, 12)
    private const val DEFAULT_REFRESH_HOURS = 3

    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fetchLock = Mutex()
    private var widgetJob: Job? = null

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _data = MutableStateFlow<UserData?>(null)
    val data: StateFlow<UserData?> = _data.asStateFlow()

    private val _avatar = MutableStateFlow<Bitmap?>(null)
    val avatar: StateFlow<Bitmap?> = _avatar.asStateFlow()

    private val _design = MutableStateFlow(WidgetDesign())
    val design: StateFlow<WidgetDesign> = _design.asStateFlow()

    private val _refreshHours = MutableStateFlow(DEFAULT_REFRESH_HOURS)
    val refreshHours: StateFlow<Int> = _refreshHours.asStateFlow()

    private val _numberFont = MutableStateFlow(NumberFont.SYSTEM)
    val numberFont: StateFlow<NumberFont> = _numberFont.asStateFlow()

    private val _refreshState = MutableStateFlow<RefreshState>(RefreshState.Idle)
    val refreshState: StateFlow<RefreshState> = _refreshState.asStateFlow()

    @Volatile
    private var initialized = false

    /** Safe to call many times (Application, widget provider, worker). Loads synchronously from disk. */
    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        app = context.applicationContext
        val prefs = prefs()
        _username.value = prefs.getString(KEY_USERNAME, "").orEmpty()
        _design.value = WidgetDesign.fromJson(prefs.getString(KEY_DESIGN, null))
        _refreshHours.value = prefs.getInt(KEY_REFRESH_HOURS, DEFAULT_REFRESH_HOURS)
        _numberFont.value = NumberFont.fromName(prefs.getString(KEY_NUMBER_FONT, null))
        _data.value = readCache()?.takeIf { it.contributions.username.equals(_username.value, true) }
        _avatar.value = runCatching { BitmapFactory.decodeFile(File(app.filesDir, AVATAR_FILE).path) }.getOrNull()
        initialized = true
    }

    val hasUser: Boolean get() = _username.value.isNotBlank()

    // ---------------------------------------------------------------- account

    /** Fetches an account without saving it, so the user can confirm it's them. */
    suspend fun lookup(rawName: String): Result<Lookup> = withContext(Dispatchers.IO) {
        val name = GitHubApi.cleanUsername(rawName)
        if (!GitHubApi.isValidUsername(name)) {
            return@withContext Result.failure(
                IllegalArgumentException("That doesn't look like a GitHub username. It's the name after github.com/ in your profile link.")
            )
        }
        runCatching {
            val contributions = async { GitHubApi.fetchContributions(name) }
            // The profile is a nice-to-have: if it fails (rate limit) we still continue.
            val profile = runCatching { GitHubApi.fetchProfile(name) }.getOrNull()
            val avatar = profile?.let { GitHubApi.fetchAvatar(it.avatarUrl) }
            val c = contributions.await()
            Lookup(
                UserData(c.copy(username = profile?.login ?: c.username), profile, System.currentTimeMillis()),
                avatar,
            )
        }.recoverCatching { throw if (it is FetchError) it else FetchError.Offline }
    }

    /** Saves a confirmed account and updates every widget right away. */
    fun signIn(lookup: Lookup) {
        val login = lookup.data.contributions.username
        prefs().edit().putString(KEY_USERNAME, login).apply()
        _username.value = login
        _data.value = lookup.data
        _avatar.value = lookup.avatar
        _refreshState.value = RefreshState.Idle
        scope.launch {
            writeCache(lookup.data)
            writeAvatar(lookup.avatar)
            WidgetUpdater.updateAll(app)
        }
        RefreshWorker.schedule(app, _refreshHours.value)
    }

    /** Forgets the account and its cached data. The design is kept. */
    fun signOut() {
        prefs().edit().remove(KEY_USERNAME).apply()
        _username.value = ""
        _data.value = null
        _avatar.value = null
        scope.launch {
            File(app.filesDir, CACHE_FILE).delete()
            File(app.filesDir, AVATAR_FILE).delete()
            WidgetUpdater.updateAll(app)
        }
    }

    // ---------------------------------------------------------------- refresh

    /** True when the cached data is older than [maxAgeMinutes] (or missing). */
    fun isStale(maxAgeMinutes: Long = 30): Boolean {
        val at = _data.value?.fetchedAt ?: return true
        return System.currentTimeMillis() - at > maxAgeMinutes * 60_000
    }

    /** Fire-and-forget refresh used by the UI (pull to refresh, app open). */
    fun refreshAsync() {
        scope.launch { refresh() }
    }

    /**
     * Downloads fresh data and repaints the widgets. On failure the old data
     * stays on screen and [refreshState] carries a friendly message.
     */
    suspend fun refresh(): Boolean = fetchLock.withLock {
        val name = _username.value
        if (name.isBlank()) return false
        _refreshState.value = RefreshState.Loading
        try {
            val c = withContext(Dispatchers.IO) { GitHubApi.fetchContributions(name) }
            // Re-fetch the profile at most daily; it rarely changes and is rate limited.
            val old = _data.value
            val profileFresh = old?.profile != null && System.currentTimeMillis() - old.fetchedAt < 24 * 3_600_000L
            val profile = if (profileFresh) old?.profile else withContext(Dispatchers.IO) {
                runCatching { GitHubApi.fetchProfile(name) }.getOrNull()
            } ?: old?.profile
            if (!profileFresh && profile != null) {
                withContext(Dispatchers.IO) { GitHubApi.fetchAvatar(profile.avatarUrl) }?.let {
                    _avatar.value = it
                    writeAvatar(it)
                }
            }
            if (_username.value != name) return false // account changed meanwhile
            val fresh = UserData(c, profile, System.currentTimeMillis())
            _data.value = fresh
            writeCache(fresh)
            _refreshState.value = RefreshState.Idle
            WidgetUpdater.updateAll(app)
            true
        } catch (e: Exception) {
            _refreshState.value = RefreshState.Failed(
                (e as? FetchError)?.message ?: FetchError.Offline.message
            )
            false
        }
    }

    fun clearError() {
        if (_refreshState.value is RefreshState.Failed) _refreshState.value = RefreshState.Idle
    }

    // ---------------------------------------------------------------- design & settings

    fun updateDesign(transform: (WidgetDesign) -> WidgetDesign) {
        val next = transform(_design.value)
        if (next == _design.value) return
        _design.value = next
        prefs().edit().putString(KEY_DESIGN, next.toJson()).apply()
        // Coalesce rapid changes (sliders) into one widget repaint.
        widgetJob?.cancel()
        widgetJob = scope.launch {
            delay(250)
            WidgetUpdater.updateAll(app)
        }
    }

    fun resetDesign() = updateDesign { WidgetDesign() }

    fun setNumberFont(font: NumberFont) {
        _numberFont.value = font
        prefs().edit().putString(KEY_NUMBER_FONT, font.name).apply()
        // The widgets draw their text in this font, so repaint them.
        scope.launch { WidgetUpdater.updateAll(app) }
    }

    fun setRefreshHours(hours: Int) {
        _refreshHours.value = hours
        prefs().edit().putInt(KEY_REFRESH_HOURS, hours).apply()
        if (hasUser) RefreshWorker.schedule(app, hours, replace = true)
    }

    // ---------------------------------------------------------------- storage

    private fun prefs() = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun writeCache(d: UserData) {
        val days = JSONArray()
        d.contributions.days.forEach {
            days.put(JSONArray().put(it.date.toString()).put(it.count).put(it.level))
        }
        val root = JSONObject()
            .put("username", d.contributions.username)
            .put("total", d.contributions.totalLastYear)
            .put("days", days)
            .put("fetchedAt", d.fetchedAt)
        d.profile?.let {
            root.put("profile", JSONObject().put("login", it.login).put("name", it.name ?: "").put("avatar", it.avatarUrl))
        }
        runCatching {
            val tmp = File(app.filesDir, "$CACHE_FILE.tmp")
            tmp.writeText(root.toString())
            tmp.renameTo(File(app.filesDir, CACHE_FILE))
        }
    }

    private fun readCache(): UserData? = runCatching {
        val file = File(app.filesDir, CACHE_FILE)
        if (!file.exists()) return readLegacyCache()
        val root = JSONObject(file.readText())
        val arr = root.getJSONArray("days")
        val days = List(arr.length()) { i ->
            val e = arr.getJSONArray(i)
            Day(LocalDate.parse(e.getString(0)), e.getInt(1), e.getInt(2))
        }
        val p = root.optJSONObject("profile")
        UserData(
            Contributions(root.getString("username"), root.optInt("total"), days),
            p?.let { Profile(it.getString("login"), it.optString("name").ifBlank { null }, it.getString("avatar")) },
            root.optLong("fetchedAt"),
        )
    }.getOrNull()

    /** Reads the v2 cache so upgraders see their graph instantly. */
    private fun readLegacyCache(): UserData? = runCatching {
        val p = app.getSharedPreferences("gh_widget_cache", Context.MODE_PRIVATE)
        val root = JSONObject(p.getString("data_json", null) ?: return null)
        val arr = root.getJSONArray("days")
        val days = List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Day(LocalDate.parse(o.getString("date")), o.optInt("count"), o.optInt("level"))
        }
        UserData(
            Contributions(root.getString("username"), root.optInt("total"), days),
            null,
            // Force a refresh soon so the profile and avatar get filled in.
            0L,
        )
    }.getOrNull()

    private fun writeAvatar(bmp: Bitmap?) {
        val f = File(app.filesDir, AVATAR_FILE)
        if (bmp == null) {
            f.delete()
            return
        }
        runCatching { f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
}
