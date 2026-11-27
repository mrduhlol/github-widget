package com.example.githubwidget.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.githubwidget.design.NumberFont
import com.example.githubwidget.design.WidgetDesign
import com.example.githubwidget.widget.RefreshWorker
import com.example.githubwidget.widget.StreakRolloverWorker
import com.example.githubwidget.widget.WidgetUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

    internal enum class RefreshOutcome {
        UPDATED,
        SKIPPED,
        TRANSIENT_FAILURE,
        PERMANENT_FAILURE,
    }

    /** Old installs stored the username here — keep reading it so updates stay signed in. */
    private const val PREFS = "github_widget_prefs"
    private const val KEY_USERNAME = "username"
    private const val KEY_AVATAR_USERNAME = "avatar_username"
    private const val KEY_SNAPSHOT_ID = "snapshot_id"
    private const val KEY_DESIGN = "design_v3"
    private const val KEY_REFRESH_HOURS = "refresh_hours"
    private const val KEY_NUMBER_FONT = "number_font"
    private const val CACHE_FILE = "contributions_v3.json"
    private const val AVATAR_FILE = "avatar.png"
    private const val SNAPSHOT_PREFIX = "snapshot_"

    val REFRESH_CHOICES = listOf(1, 3, 6, 12)
    private const val DEFAULT_REFRESH_HOURS = 3

    internal data class AccountSnapshot(
        val username: String,
        val data: UserData?,
        val avatar: Bitmap?,
        val refreshState: RefreshState,
    ) {
        val hasUser: Boolean get() = username.isNotBlank()
    }

    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fetchLock = Mutex()
    private val storageLock = Mutex()
    private val accountLock = Any()
    private val widgetJobLock = Any()
    private var widgetJob: Job? = null
    private var _sessionGeneration: Long = 0L
    private var _snapshotRevision: Long = 0L
    private var _nextSnapshotId: Long = 0L
    internal var onSignInPersistHook: (suspend () -> Unit)? = null
    internal var onBeforeSnapshotWriteHook: ((cacheFile: File, avatarFile: File) -> Unit)? = null
    internal var onAfterCacheWriteHook: ((avatarFile: File) -> Unit)? = null
    internal var onRetryRequestedHook: ((replace: Boolean) -> Unit)? = null
    internal var lastSignInPersistJob: Job? = null

    internal val sessionGeneration: Long
        get() = synchronized(accountLock) { _sessionGeneration }

    internal fun isSessionActive(generation: Long, username: String): Boolean = synchronized(accountLock) {
        _sessionGeneration == generation && _username.value.equals(username, ignoreCase = true)
    }

    internal fun isRevisionCurrent(revision: Long): Boolean = synchronized(accountLock) {
        _snapshotRevision == revision
    }

    internal fun snapshot(): AccountSnapshot = synchronized(accountLock) {
        AccountSnapshot(_username.value, _data.value, _avatar.value, _refreshState.value)
    }

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

    /** Safe to call many times (Application, widget provider, worker). */
    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        app = context.applicationContext
        val prefs = prefs()
        val storedUser = prefs.getString(KEY_USERNAME, "").orEmpty()
        val snapshotId = prefs.getString(KEY_SNAPSHOT_ID, null)
        val cachedData = readCache(snapshotId)?.takeIf { it.contributions.username.equals(storedUser, ignoreCase = true) }
        val avatarOwner = prefs.getString(KEY_AVATAR_USERNAME, null)
        val avatarFile = if (snapshotId != null) {
            File(app.filesDir, "${SNAPSHOT_PREFIX}${snapshotId}.png")
        } else {
            File(app.filesDir, AVATAR_FILE)
        }
        val cachedAvatar = if (storedUser.isNotBlank() && avatarOwner.equals(storedUser, ignoreCase = true)) {
            runCatching { BitmapFactory.decodeFile(avatarFile.path) }.getOrNull()
        } else {
            null
        }
        synchronized(accountLock) {
            _sessionGeneration++
            _snapshotRevision++
            _username.value = storedUser
            _data.value = cachedData
            _avatar.value = cachedAvatar
            _refreshState.value = RefreshState.Idle
        }
        _design.value = WidgetDesign.fromJson(prefs.getString(KEY_DESIGN, null))
        _refreshHours.value = prefs.getInt(KEY_REFRESH_HOURS, DEFAULT_REFRESH_HOURS)
        _numberFont.value = NumberFont.fromName(prefs.getString(KEY_NUMBER_FONT, null))
        initialized = true
    }

    @Synchronized
    internal fun resetForTest() {
        initialized = false
        onSignInPersistHook = null
        onBeforeSnapshotWriteHook = null
        onAfterCacheWriteHook = null
        onRetryRequestedHook = null
        lastSignInPersistJob?.cancel()
        lastSignInPersistJob = null
        synchronized(widgetJobLock) {
            widgetJob?.cancel()
            widgetJob = null
        }
        synchronized(accountLock) {
            _sessionGeneration++
            _snapshotRevision++
            _username.value = ""
            _data.value = null
            _avatar.value = null
            _refreshState.value = RefreshState.Idle
        }
        _design.value = WidgetDesign()
        _refreshHours.value = DEFAULT_REFRESH_HOURS
        _numberFont.value = NumberFont.SYSTEM
    }

    val hasUser: Boolean get() = _username.value.isNotBlank()

    // ---------------------------------------------------------------- account

    /**
     * Fetches an account without saving it, so the user can confirm it's them.
     *
     * Runs the GitHub profile lookup and the third-party contribution lookup in
     * parallel so a temporary outage in either service doesn't block setup when
     * the other confirms the account exists.
     */
    internal suspend fun lookup(
        rawName: String,
        fetchContributions: (String) -> Contributions = GitHubApi::fetchContributions,
        fetchProfile: (String) -> Profile = GitHubApi::fetchProfile,
        fetchAvatar: (String) -> Bitmap? = { GitHubApi.fetchAvatar(it) },
    ): Result<Lookup> = withContext(Dispatchers.IO) {
        val name = GitHubApi.cleanUsername(rawName)
        if (!GitHubApi.isValidUsername(name)) {
            return@withContext Result.failure(
                IllegalArgumentException("That doesn't look like a GitHub username. It's the name after github.com/ in your profile link.")
            )
        }
        try {
            val lookup = coroutineScope {
                val contributionsDeferred = async {
                    try {
                        Result.success(fetchContributions(name))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }
                val profileDeferred = async {
                    try {
                        fetchProfile(name)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null // Profile metadata is optional, including when GitHub rate-limits it.
                    }
                }
                val contributionsResult = contributionsDeferred.await()
                val profile = profileDeferred.await()
                val contributions = contributionsResult.getOrElse { error ->
                    // If GitHub itself confirmed the profile exists, allow setup to complete
                    // with an empty graph and mark fetchedAt = 0 so background refresh retries.
                    if (profile != null) {
                        Contributions(profile.login, 0, emptyList())
                    } else {
                        throw error
                    }
                }
                val avatar = profile?.let { fetchAvatar(it.avatarUrl) }
                val fetchedAt = if (contributionsResult.isSuccess) System.currentTimeMillis() else 0L
                Lookup(
                    UserData(
                        contributions.copy(username = profile?.login ?: contributions.username),
                        profile,
                        fetchedAt,
                    ),
                    avatar,
                )
            }
            Result.success(lookup)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e as? FetchError ?: FetchError.Offline)
        }
    }

    internal fun snapshotRevision(): Long = synchronized(accountLock) { _snapshotRevision }

    internal suspend fun persistSnapshot(revision: Long, login: String, data: UserData, avatar: Bitmap?): Boolean {
        return storageLock.withLock {
            if (!isRevisionCurrent(revision)) return@withLock false
            writeSnapshot(login, data, avatar) { isRevisionCurrent(revision) }
        }
    }

    /** Saves a confirmed account and updates every widget right away. */
    fun signIn(lookup: Lookup) {
        val login = lookup.data.contributions.username
        val (previous, generation, revision) = synchronized(accountLock) {
            val prev = _username.value
            _sessionGeneration++
            _snapshotRevision++
            _username.value = login
            _data.value = lookup.data
            _avatar.value = lookup.avatar
            _refreshState.value = RefreshState.Idle
            Triple(prev, _sessionGeneration, _snapshotRevision)
        }
        if (!previous.equals(login, ignoreCase = true)) {
            ReviewInbox.clear(app)
        }
        lastSignInPersistJob = scope.launch {
            onSignInPersistHook?.invoke()
            val saved = persistSnapshot(revision, login, lookup.data, lookup.avatar)
            if (!saved && isRevisionCurrent(revision)) {
                synchronized(accountLock) {
                    if (isRevisionCurrent(revision)) {
                        _refreshState.value = RefreshState.Failed("Couldn't save offline data. Will retry automatically.")
                    }
                }
                enqueueRetry(replace = true)
            }
            if (isSessionActive(generation, login)) {
                WidgetUpdater.updateAll(app)
            }
        }
        RefreshWorker.schedule(app, _refreshHours.value)
        if (lookup.data.fetchedAt == 0L || WidgetUpdater.reviewInboxIds(app).isNotEmpty()) {
            enqueueRetry(replace = true)
        }
    }

    private fun enqueueRetry(replace: Boolean) {
        onRetryRequestedHook?.invoke(replace)
        runCatching { RefreshWorker.runOnce(app, replace = replace) }
    }

    /** Forgets the account and its cached data. The design is kept. */
    fun signOut() {
        synchronized(widgetJobLock) {
            widgetJob?.cancel()
            widgetJob = null
        }
        val (generation, revision, prefsCleared) = synchronized(accountLock) {
            _sessionGeneration++
            _snapshotRevision++
            val cleared = prefs().edit()
                .remove(KEY_USERNAME)
                .remove(KEY_AVATAR_USERNAME)
                .remove(KEY_SNAPSHOT_ID)
                .commit()
            _username.value = ""
            _data.value = null
            _avatar.value = null
            _refreshState.value = RefreshState.Idle
            Triple(_sessionGeneration, _snapshotRevision, cleared)
        }
        RefreshWorker.cancel(app)
        StreakRolloverWorker.cancel(app)
        ReviewInbox.clear(app)
        scope.launch {
            if (prefsCleared) {
                storageLock.withLock {
                    if (!isRevisionCurrent(revision)) return@withLock
                    cleanupSnapshotFiles(keepSnapshotId = null)
                }
            }
            if (sessionGeneration == generation) {
                WidgetUpdater.updateAll(app)
            }
        }
    }

    // ---------------------------------------------------------------- refresh

    /** True when cached data is missing, older than [maxAgeMinutes], or missing a recoverable avatar. */
    fun isStale(maxAgeMinutes: Long = 30): Boolean {
        val current = _data.value ?: return true
        if (current.profile != null && _avatar.value == null) return true
        return System.currentTimeMillis() - current.fetchedAt > maxAgeMinutes * 60_000
    }

    /** Fire-and-forget refresh used by the UI (pull to refresh, app open). */
    fun refreshAsync() {
        scope.launch { refresh() }
    }

    /** Renders a single widget off the main thread (used by WidgetConfigActivity). */
    internal fun updateWidgetAsync(context: Context, id: Int) {
        val appContext = context.applicationContext
        scope.launch {
            WidgetUpdater.update(appContext, intArrayOf(id))
        }
    }

    /**
     * Downloads fresh data and repaints the widgets. On failure the old data
     * stays on screen and [refreshState] carries a friendly message.
     */
    suspend fun refresh(): Boolean = refreshWithOutcome(updateWidgets = true) == RefreshOutcome.UPDATED

    internal suspend fun refreshWithOutcome(
        updateWidgets: Boolean = true,
        fetchContributions: (String) -> Contributions = GitHubApi::fetchContributions,
        fetchProfile: (String) -> Profile = GitHubApi::fetchProfile,
        fetchAvatar: (String) -> Bitmap? = { GitHubApi.fetchAvatar(it) },
    ): RefreshOutcome = fetchLock.withLock {
        val (name, generation, old, missingAvatar) = synchronized(accountLock) {
            val u = _username.value
            val g = _sessionGeneration
            if (u.isNotBlank()) _refreshState.value = RefreshState.Loading
            RefreshSnapshot(u, g, _data.value?.takeIf { it.contributions.username.equals(u, ignoreCase = true) }, _avatar.value == null)
        }
        if (name.isBlank()) return RefreshOutcome.SKIPPED
        try {
            val c = withContext(Dispatchers.IO) { fetchContributions(name) }
            // Re-fetch the profile at most daily; it rarely changes and is rate limited.
            val profileFresh = old?.profile != null && System.currentTimeMillis() - old.profile.fetchedAt < 24 * 3_600_000L
            val profile = if (profileFresh) old?.profile else withContext(Dispatchers.IO) {
                try {
                    fetchProfile(name)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            } ?: old?.profile
            val needAvatar = missingAvatar || !profileFresh
            val freshAvatar = if (needAvatar && profile != null) {
                withContext(Dispatchers.IO) { fetchAvatar(profile.avatarUrl) }
            } else {
                null
            }
            val committed = storageLock.withLock {
                val fresh = UserData(c, profile, System.currentTimeMillis())
                val committedAvatar = synchronized(accountLock) {
                    if (!isSessionActive(generation, name)) return@withLock false
                    freshAvatar ?: _avatar.value
                }
                var sessionStale = false
                val published = writeSnapshot(name, fresh, committedAvatar) {
                    if (!isSessionActive(generation, name)) {
                        sessionStale = true
                        false
                    } else {
                        true
                    }
                }
                if (sessionStale) return@withLock false
                if (!published) {
                    throw java.io.IOException("Failed to persist account snapshot")
                }
                synchronized(accountLock) {
                    if (!isSessionActive(generation, name)) return@synchronized false
                    _snapshotRevision++
                    _data.value = fresh
                    if (freshAvatar != null) _avatar.value = freshAvatar
                    _refreshState.value = RefreshState.Idle
                    true
                }
            }
            if (!committed) return RefreshOutcome.SKIPPED
            if (updateWidgets && isSessionActive(generation, name)) WidgetUpdater.updateAll(app)
            RefreshOutcome.UPDATED
        } catch (e: CancellationException) {
            synchronized(accountLock) {
                if (isSessionActive(generation, name) && _refreshState.value is RefreshState.Loading) {
                    _refreshState.value = RefreshState.Idle
                }
            }
            throw e
        } catch (e: Exception) {
            val stillActive = synchronized(accountLock) {
                if (!isSessionActive(generation, name)) return@synchronized false
                _refreshState.value = RefreshState.Failed(
                    (e as? FetchError)?.message ?: FetchError.Offline.message
                )
                true
            }
            if (!stillActive) return RefreshOutcome.SKIPPED
            if (updateWidgets) {
                if (_data.value == null) WidgetUpdater.updateAll(app)
                else WidgetUpdater.update(app, WidgetUpdater.streakIds(app))
            }
            if (e is FetchError.NotFound) RefreshOutcome.PERMANENT_FAILURE else RefreshOutcome.TRANSIENT_FAILURE
        }
    }

    private data class RefreshSnapshot(
        val username: String,
        val generation: Long,
        val oldData: UserData?,
        val missingAvatar: Boolean,
    )

    fun clearError() {
        synchronized(accountLock) {
            if (_refreshState.value is RefreshState.Failed) _refreshState.value = RefreshState.Idle
        }
    }

    // ---------------------------------------------------------------- design & settings

    fun updateDesign(transform: (WidgetDesign) -> WidgetDesign) {
        val changed = transform(_design.value)
        val next = if (changed.layout in listOf(com.example.githubwidget.design.WidgetLayout.CLASSIC, com.example.githubwidget.design.WidgetLayout.GRAPH, com.example.githubwidget.design.WidgetLayout.NUMBERS)) {
            changed.copy(contributionLayout = changed.layout)
        } else changed
        if (next == _design.value) return
        _design.value = next
        prefs().edit().putString(KEY_DESIGN, next.toJson()).apply()
        // Coalesce rapid changes (sliders) into one widget repaint.
        synchronized(widgetJobLock) {
            widgetJob?.cancel()
            widgetJob = scope.launch {
                delay(250)
                WidgetUpdater.updateAll(app)
            }
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

    /**
     * Writes both the contribution graph and avatar into new generation-scoped files, then
     * rechecks [shouldCommit] inside [accountLock] and atomically commits username, snapshot ID,
     * and avatar owner in one SharedPreferences transaction before cleaning up older files.
     */
    private fun writeSnapshot(
        owner: String,
        d: UserData,
        bmp: Bitmap?,
        shouldCommit: () -> Boolean,
    ): Boolean {
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
            root.put("profile", JSONObject().put("login", it.login).put("name", it.name ?: "").put("avatar", it.avatarUrl)
                .put("publicRepos", it.publicRepos).put("profileFetchedAt", it.fetchedAt).put("followers", it.followers).put("following", it.following).put("website", it.website)
                .put("socialLinks", org.json.JSONArray().apply {
                    it.socialLinks.forEach { link -> put(JSONObject().put("label", link.label).put("url", link.url)) }
                }))
        }
        val snapshotId = synchronized(accountLock) {
            _nextSnapshotId++
            "${System.currentTimeMillis()}_${_nextSnapshotId}"
        }
        val cacheFile = File(app.filesDir, "${SNAPSHOT_PREFIX}${snapshotId}.json")
        val avatarFile = File(app.filesDir, "${SNAPSHOT_PREFIX}${snapshotId}.png")
        onBeforeSnapshotWriteHook?.invoke(cacheFile, avatarFile)

        val wroteFiles = runCatching {
            if (cacheFile.isDirectory) return@runCatching false
            cacheFile.writeText(root.toString())
            onAfterCacheWriteHook?.invoke(avatarFile)
            if (bmp != null) {
                if (avatarFile.isDirectory) return@runCatching false
                val compressed = avatarFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                if (!compressed) return@runCatching false
            }
            true
        }.getOrDefault(false)
        if (!wroteFiles) {
            if (!cacheFile.isDirectory) cacheFile.delete()
            if (!avatarFile.isDirectory) avatarFile.delete()
            return false
        }

        val committed = synchronized(accountLock) {
            if (!shouldCommit()) return@synchronized false
            prefs().edit().apply {
                putString(KEY_USERNAME, owner)
                putString(KEY_SNAPSHOT_ID, snapshotId)
                if (bmp != null) putString(KEY_AVATAR_USERNAME, owner) else remove(KEY_AVATAR_USERNAME)
            }.commit()
        }
        if (!committed) {
            cacheFile.delete()
            avatarFile.delete()
            return false
        }

        cleanupSnapshotFiles(keepSnapshotId = snapshotId)
        return true
    }

    private fun cleanupSnapshotFiles(keepSnapshotId: String?) {
        val keepJson = keepSnapshotId?.let { "${SNAPSHOT_PREFIX}${it}.json" }
        val keepPng = keepSnapshotId?.let { "${SNAPSHOT_PREFIX}${it}.png" }
        app.filesDir.listFiles()?.forEach { file ->
            val name = file.name
            val isLegacy = name == CACHE_FILE || name == AVATAR_FILE || name == "$CACHE_FILE.tmp" || name == "$AVATAR_FILE.tmp"
            val isOldSnapshot = name.startsWith(SNAPSHOT_PREFIX) && name != keepJson && name != keepPng
            if ((isLegacy || isOldSnapshot) && !file.isDirectory) {
                file.delete()
            }
        }
    }

    private fun readCache(snapshotId: String?): UserData? = runCatching {
        val file = if (snapshotId != null) {
            File(app.filesDir, "${SNAPSHOT_PREFIX}${snapshotId}.json")
        } else {
            File(app.filesDir, CACHE_FILE)
        }
        if (!file.exists()) return if (snapshotId == null) readLegacyCache() else null
        val root = JSONObject(file.readText())
        val arr = root.getJSONArray("days")
        val days = List(arr.length()) { i ->
            val e = arr.getJSONArray(i)
            Day(LocalDate.parse(e.getString(0)), e.getInt(1), e.getInt(2))
        }
        val p = root.optJSONObject("profile")
        UserData(
            Contributions(root.getString("username"), root.optInt("total"), days),
            p?.let {
                val links = it.optJSONArray("socialLinks")
                Profile(it.getString("login"), it.optString("name").ifBlank { null }, it.getString("avatar"),
                    publicRepos = if (it.has("publicRepos") && !it.isNull("publicRepos")) it.optInt("publicRepos") else null,
                    followers = if (it.has("followers") && !it.isNull("followers")) it.optInt("followers") else null,
                    following = if (it.has("following") && !it.isNull("following")) it.optInt("following") else null,
                    website = GitHubApi.webUrl(it.optString("website")),
                    fetchedAt = it.optLong("profileFetchedAt"),
                    socialLinks = (0 until (links?.length() ?: 0)).mapNotNull { i ->
                        val link = links!!.getJSONObject(i)
                        GitHubApi.webUrl(link.optString("url"))?.let { url -> ProfileLink(link.optString("label"), url) }
                    },
                )
            },
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
}
