# GH-widgets Six Features Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add streak notifications, per-widget config/usernames, compact sizes, a weekly goal, widget tap zones, and extra share cards to GH-widgets without breaking its username-only/offline/no-cloud contract.

**Architecture:** Keep the existing global `Repository` as the default source of truth and layer small, opt-in overrides on top: per-widget config in a new id-keyed SharedPreferences store, per-username data cache files, a pure decision layer for notifications driven by the existing `RefreshWorker`, and a `SharedFlow` router that turns widget taps into tab changes in the existing three-tab `AppRoot`. The single bitmap renderer is extended with two compact layouts and a goal line; the share image object is extended with a card type.

**Tech Stack:** Kotlin, Android SDK (minSdk 26 / target 34), Jetpack Compose Material3, WorkManager 2.9.1, Coroutines, SharedPreferences, `Canvas` rendering, Robolectric + JUnit4 unit tests. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-11-16-six-features-design.md`

## Global Constraints

- minSdk 26, targetSdk/compileSdk 34, versionCode 26, versionName 3.6 in `app/build.gradle.kts`.
- No accounts, sign-in, tokens, cloud sync, or server-side features; username-only setup; all useful behavior works offline.
- Config persistence is SharedPreferences (`github_widget_prefs` for global, new `gh_widget_configs` for per-widget); data cache is JSON/PNG files in `filesDir`.
- No new Gradle dependencies; only `material-icons-core` is available, so new UI icons must be custom paths in `AppIcons.kt` or existing core icons.
- Strings are inline literals in Compose code (existing convention); no stringResource migration.
- Widget metadata in `res/xml/widget_info.xml` currently `minResizeWidth=110dp minResizeHeight=60dp`, `configuration_optional|reconfigurable`.
- All existing widget-sizing tests must stay green; new render/size logic gets snapshots.
- Test command: `./gradlew testDebugUnitTest`; render snapshots land in `app/build/widget-renders/`.

## Review Focus

- **Stale cache producing a false "streak at risk" notification** — a reasonable user expects no alarm when today's data is unknown. Test: `atRiskDecision` returns `shouldNotify=false` when `fetchedAt` is older than 6 hours (Task 3).
- **First sync after upgrade firing a burst of milestone notifications** — a user with a 40-day streak expects at most one congrats, not seven. Test: `milestoneDecision` returns null and initializes the baseline via `baselineFor` (Task 3).
- **Existing single-widget install changing behavior** — a user who never opens the new config screen expects the widget to look and act exactly as before. Test: `WidgetConfigStore.get` returns null and `WidgetUpdater` falls back to global design/username (Task 8 + Task 11).
- **Two widgets for different usernames sharing one cached bitmap** — each widget must show its own graph. Test: render-cache key includes the resolved username/design identity (Task 11).
- **Compact layouts at 60×60dp being unreadable** — a 1×1 widget must show a legible streak number. Test: snapshots at 60×60 and 160×60dp (Task 5).

---

### Task 1: Weekly goal — persistence and stats card

**Files:**
- Modify: `app/src/main/java/com/example/githubwidget/data/Repository.kt` (near keys at :49-58, state at :60-87, setters at :214-240)
- Modify: `app/src/main/java/com/example/githubwidget/ui/stats/StatsScreen.kt` (new keyed item near :180-183; card near :418)
- Modify: `app/src/main/java/com/example/githubwidget/ui/settings/SettingsScreen.kt` (new SettingsGroup near :211)

**Interfaces:**
- Consumes: `Stats.from(...).thisWeek` (`data/Stats.kt:11,80`); `Repository.REFRESH_CHOICES` pattern for persisted settings.
- Produces: `Repository.weeklyGoal: StateFlow<Int>`, `Repository.setWeeklyGoal(goal: Int)`; 0 means off. `StatsScreen` shows a "Weekly goal" card only when `weeklyGoal > 0`.

- [ ] **Step 1: Write failing test for goal persistence** in `app/src/test/java/com/example/githubwidget/data/RepositoryGoalTest.kt`: with a fresh Robolectric context, `Repository.init(ctx)` then `setWeeklyGoal(20)` and assert `weeklyGoal.value == 20`; re-init a fresh `Repository` state and assert it reads 20 back from prefs. Also assert default is 0.

- [ ] **Step 2: Run it to confirm it fails.** `./gradlew testDebugUnitTest --tests '*RepositoryGoalTest'` — expect compile failure on missing members.

- [ ] **Step 3: Implement in `Repository.kt`**: add `KEY_WEEKLY_GOAL = "weekly_goal"`, a `MutableStateFlow<Int>` loaded in `init()`, and `setWeeklyGoal` persisting to `github_widget_prefs`. Goal choices exposed to UI as `listOf(0, 5, 10, 20, 50, 100)`.

- [ ] **Step 4: Add the Settings goal row** in `SettingsScreen.kt`: a `SettingsGroup("Weekly goal")` with `SegmentedChoice` over `Repository.WEEKLY_GOAL_CHOICES`, labels "Off/5/10/20/50/100", calling `Repository.setWeeklyGoal`.

- [ ] **Step 5: Add the Stats goal card** in `StatsScreen.kt` when `goal > 0`: a `SectionCard("Weekly goal")` with a `CircularProgressIndicator(progress = { (stats.thisWeek.toFloat() / goal).coerceIn(0f, 1f) })`, the text `thisWeek/goal`, and a short line ("Goal met" / "N to go" / "On track"). Use `AppTheme.colors` and `numberFontFamily` like neighboring cards.

- [ ] **Step 6: Run tests and commit.** `./gradlew testDebugUnitTest`; commit `feat: add weekly goal setting and stats card`.

### Task 2: Weekly goal — widget progress line

**Files:**
- Modify: `app/src/main/java/com/example/githubwidget/design/WidgetDesign.kt` (fields at :44-62, JSON at :63-108)
- Modify: `app/src/main/java/com/example/githubwidget/ui/design/DesignScreen.kt` (toggles near :187-235)
- Modify: `app/src/main/java/com/example/githubwidget/widget/WidgetRenderer.kt` (draw header/footer near :170-220)
- Test: `app/src/test/java/com/example/githubwidget/widget/WidgetRenderSnapshotTest.kt`

**Interfaces:**
- Consumes: `Repository.weeklyGoal`, `Stats.thisWeek`, existing `WidgetDesign` JSON conventions.
- Produces: `WidgetDesign.showGoal: Boolean = false` (JSON key `showGoal`, lenient `fromJson`), and a goal progress line rendered when `showGoal && weeklyGoal > 0`.

- [ ] **Step 1: Write a failing render test** in `WidgetRenderSnapshotTest.kt`: render a CLASSIC widget with `design.copy(showGoal = true)`, `weeklyGoal = 20`, `stats.thisWeek = 8`, and assert a non-empty progress region exists in the lower card area (compare pixel diff against the same design with `showGoal = false`). Add a JSON round-trip assertion that `showGoal=true` survives `toJson`/`fromJson`.

- [ ] **Step 2: Run it to confirm it fails.**

- [ ] **Step 3: Implement**: add `showGoal` to `WidgetDesign` with default false, persist/parse it in `toJson`/`fromJson`, add a toggle in `DesignScreen`, and in `WidgetRenderer` draw a thin rounded progress track (clamped 0–1) in the reserved header/footer area with optional `thisWeek/goal` text when width allows.

- [ ] **Step 4: Run tests and commit.** `feat: show weekly goal progress in widget`.

### Task 3: Notification decision layer

**Files:**
- Create: `app/src/main/java/com/example/githubwidget/data/StreakNotifier.kt`
- Test: `app/src/test/java/com/example/githubwidget/data/StreakNotifierTest.kt`

**Interfaces:**
- Consumes: `UserData.fetchedAt`, `Stats.from(c).currentStreak/today`, `LocalDate`.
- Produces:
  - `data class AtRiskResult(val shouldNotify: Boolean, val streak: Int)`
  - `fun atRiskDecision(data: UserData?, nowMillis: Long, localDate: LocalDate, lastNotifiedDate: String?, remindersEnabled: Boolean, permissionGranted: Boolean, freshnessWindowMs: Long = 6 * 60 * 60 * 1000): AtRiskResult`
  - `fun milestoneDecision(before: Int, after: Int, lastCelebrated: Int): Int?` (thresholds 7,14,30,50,100,180,365 then every 100)
  - `fun baselineFor(streak: Int): Int` (returns the highest threshold <= streak, for initializing at sign-in)

- [ ] **Step 1: Write failing tests** covering: enabled+permission+fresh+today==0+streak>0+not-yet-notified → shouldNotify true; stale `fetchedAt` → false; today>0 → false; already notified today → false; reminders off → false; no permission → false; `milestoneDecision(39, 40, 30) == 40`; `(0, 40, 0) == null` (no baseline); `baselineFor(40) == 30`; `(99, 100, 30) == 100`; `(100, 101, 100) == null`.

- [ ] **Step 2: Run to confirm failure.**

- [ ] **Step 3: Implement pure functions** in `StreakNotifier.kt`. `atRiskDecision` converts `nowMillis` and `localDate` to compare `fetchedAt` freshness, formats `localDate.toString()` for the notified-date check, uses `Stats.from(data.contributions)`. Keep posting/channel code out of this file for now.

- [ ] **Step 4: Run tests and commit.** `feat: add streak notification decision logic`.

### Task 4: Notification delivery, channel, permission, worker, settings toggle

**Files:**
- Modify: `app/src/main/java/com/example/githubwidget/App.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/example/githubwidget/data/Repository.kt`
- Modify: `app/src/main/java/com/example/githubwidget/widget/RefreshWorker.kt`
- Create: `app/src/main/java/com/example/githubwidget/widget/StreakReminderWorker.kt`
- Modify: `app/src/main/java/com/example/githubwidget/ui/settings/SettingsScreen.kt`

**Interfaces:**
- Consumes: Task 3 `atRiskDecision`, `milestoneDecision`, `baselineFor`; `Repository.data`, `Repository.setRemindersEnabled`.
- Produces: `Repository.remindersEnabled: StateFlow<Boolean>` (default false), `StreakNotifier.createChannel(context)`, `StreakNotifier.postAtRisk(context, username, streak)`, `StreakNotifier.postMilestone(context, username, streak)`, `StreakReminderWorker.schedule(context)`.

- [ ] **Step 1: Add the permission/channel plumbing**: `POST_NOTIFICATIONS` in the manifest; `StreakNotifier.createChannel` called from `App.onCreate` (API 26+ `NotificationChannel` id `streaks`); `postAtRisk`/`postMilestone` using `NotificationCompat`-free `Notification.Builder` (core available) with a `PendingIntent` to MainActivity.

- [ ] **Step 2: Add `remindersEnabled`** to `Repository` (default false, key `reminders_enabled`), plus prefs helpers for `lastAtRiskDate_<username>` and `lastCelebrated_<username>`; initialize the baseline to `baselineFor(Stats.currentStreak)` in `signIn` and in the per-user save path.

- [ ] **Step 3: Add the Settings toggle** that requests `POST_NOTIFICATIONS` via `rememberLauncherForActivityResult` on API 33+ when turning on; if denied, show a short explanation and keep it off.

- [ ] **Step 4: Create `StreakReminderWorker`** — a `CoroutineWorker` with a 1-hour periodic schedule (unique work `streak_reminder`), online constraint; on run, for each configured username plus the global user, call `atRiskDecision` and post if needed, recording the notified date. Call `StreakReminderWorker.schedule` from `Repository.signIn` and when reminders are toggled on; cancel when toggled off.

- [ ] **Step 5: Hook milestones** into `RefreshWorker.doWork`/`Repository.refresh`: capture the streak before a successful refresh, after refresh call `milestoneDecision`, post and persist `lastCelebrated` if non-null.

- [ ] **Step 6: Run tests and commit.** `./gradlew testDebugUnitTest`; `feat: deliver streak notifications`.

### Task 5: Compact widget layouts

**Files:**
- Modify: `app/src/main/java/com/example/githubwidget/design/WidgetDesign.kt` (enum at :6-10)
- Modify: `app/src/main/java/com/example/githubwidget/widget/WidgetRenderer.kt` (dispatch at :112-122, draw methods)
- Modify: `app/src/main/java/com/example/githubwidget/ui/design/DesignScreen.kt` (layout choices at :393)
- Modify: `app/src/main/res/xml/widget_info.xml`
- Test: `WidgetRenderSnapshotTest.kt`

**Interfaces:**
- Consumes: `WidgetLayout` enum, `RenderSpec`, `Palettes`.
- Produces: `WidgetLayout.STREAK` and `WidgetLayout.TODAY` values; render paths legible at 60×60dp and 160×60dp respectively.

- [ ] **Step 1: Write failing snapshot tests** rendering STREAK at 60×60dp and TODAY at 160×60dp, asserting bitmap within the existing `maxPixels` budget, non-empty pixels, and that the streak number area is present (pixel-density assertion in the center for STREAK).

- [ ] **Step 2: Run to confirm failure.**

- [ ] **Step 3: Implement**: add the two enum values, dispatch in `WidgetRenderer.Painter.draw`, draw STREAK as a centered large number + small flame glyph (text-only fallback at tiny sizes) and TODAY as "Today: N" plus the goal line. Add them to `DesignScreen` layout choices and lower `minResizeWidth`/`minResizeHeight` to 60dp.

- [ ] **Step 4: Run tests and commit.** `feat: add compact streak and today widget layouts`.

### Task 6: Expanded shareables

**Files:**
- Modify: `app/src/main/java/com/example/githubwidget/ui/stats/ShareImage.kt`
- Modify: `app/src/main/java/com/example/githubwidget/ui/stats/StatsScreen.kt` (`ShareCard` at :559-600)
- Test: `app/src/test/java/com/example/githubwidget/ui/stats/ShareImageRenderTest.kt`

**Interfaces:**
- Consumes: `Stats.from(...)`, `GraphPainter`, `NumberFont`, `Palettes`, `FileProvider` authority.
- Produces: `enum class ShareCardType { YEAR, STREAK, WEEK }`; `ShareImage.share(context, data, avatar, design, numberFont = SYSTEM, type: ShareCardType = YEAR): Boolean`; `ShareImage.render(..., type)`: Bitmap. Distinct filenames `shared/streak-<ts>.png`, `shared/week-<ts>.png`, captions and chooser titles per type.

- [ ] **Step 1: Write failing render tests** for STREAK and WEEK that render and assert distinct bitmaps from YEAR and from each other (hash/size comparison), and that each stays within 1080×1350.

- [ ] **Step 2: Run to confirm failure.**

- [ ] **Step 3: Implement**: add the enum, thread `type` through `share`/`render`, factor common background/avatar/footer into helpers, add `renderStreak` (big streak number, longest streak, small year graph) and `renderWeek` (this week's 7 daily bars from `contributions.days`, goal progress when set). Keep `filepaths.xml` unchanged.

- [ ] **Step 4: Add a `SegmentedChoice` in `ShareCard`** above the existing button to select the type; pass it to `ShareImage.share`.

- [ ] **Step 5: Run tests and commit.** `feat: add streak and weekly share cards`.

### Task 7: Widget tap zones and intent routing

**Files:**
- Create: `app/src/main/java/com/example/githubwidget/widget/IntentRouter.kt`
- Modify: `app/src/main/res/layout/widget_root.xml`, `app/src/main/res/layout-night/widget_root.xml`
- Modify: `app/src/main/java/com/example/githubwidget/widget/WidgetUpdater.kt` (PendingIntents at :199-211)
- Modify: `app/src/main/java/com/example/githubwidget/MainActivity.kt`
- Modify: `app/src/main/java/com/example/githubwidget/ui/AppRoot.kt` (`tab` at :77, content switch at :217-228)
- Modify: `app/src/main/java/com/example/githubwidget/ui/stats/StatsScreen.kt` (accept optional data override)

**Interfaces:**
- Consumes: `Tab` enum from `AppRoot`, `Repository.cachedDataFor` (Task 9), per-widget username (Task 8).
- Produces: `data class NavEvent(val tab: Tab, val username: String?)`; `IntentRouter.emit(event)` / `IntentRouter.events: SharedFlow<NavEvent>`; `StatsScreen(username: String? = null)`.

- [ ] **Step 1: Add `IntentRouter`** with a `MutableSharedFlow<NavEvent>(extraBufferCapacity = 1)`; `MainActivity.onCreate` reads `intent.extras` and overrides `onNewIntent` to emit.

- [ ] **Step 2: In `AppRoot`**, collect `IntentRouter.events` in a `LaunchedEffect` and set `tab` (and a `statsUsername` state) from it.

- [ ] **Step 3: In `StatsScreen`**, add an optional `username` parameter; when non-null, render from `Repository.cachedDataFor(username)` instead of global data without mutating global state.

- [ ] **Step 4: Add overlay zones** `zone_avatar` (top-start, ~48dp) and `zone_stats` (lower area) to both widget layout files; wire distinct PendingIntents in `WidgetUpdater` with request codes 2 (avatar → `ACTION_VIEW` profile URL) and 3 (stats → MainActivity with `target=stats` + `username` extra). Root keeps request code 0 but now targets Stats. For compact layouts, hide `zone_avatar` and shrink the refresh target to 24dp.

- [ ] **Step 5: Manual/verification**: `./gradlew assembleDebug` must compile; run existing unit tests; commit `feat: add widget tap zones`.

### Task 8: Per-widget config model and store

**Files:**
- Create: `app/src/main/java/com/example/githubwidget/data/WidgetConfig.kt`
- Create: `app/src/main/java/com/example/githubwidget/data/WidgetConfigStore.kt`
- Test: `app/src/test/java/com/example/githubwidget/data/WidgetConfigTest.kt`, `WidgetConfigStoreTest.kt`

**Interfaces:**
- Consumes: `WidgetDesign.toJson()/fromJson()`.
- Produces: `data class WidgetConfig(val username: String?, val design: WidgetDesign?)` with JSON round-trip; `WidgetConfigStore.get(context, id): WidgetConfig?`, `save(context, id, config)`, `remove(context, id)`, `ids(context): Set<Int>`, backed by prefs file `gh_widget_configs` key `config_<id>`.

- [ ] **Step 1: Write failing tests**: JSON round-trip with null design and with a full design; store save/get/remove and `ids()` using a Robolectric context.

- [ ] **Step 2: Run to confirm failure.**

- [ ] **Step 3: Implement** both files following `Repository`/`WidgetDesign` JSON conventions (lenient parsing, defaults on missing fields).

- [ ] **Step 4: Run tests and commit.** `feat: add per-widget config store`.

### Task 9: Per-username cache and multi-user refresh

**Files:**
- Modify: `app/src/main/java/com/example/githubwidget/data/Repository.kt`
- Modify: `app/src/main/java/com/example/githubwidget/widget/RefreshWorker.kt`
- Modify: `app/src/main/java/com/example/githubwidget/widget/ContributionWidgetProvider.kt`
- Test: `app/src/test/java/com/example/githubwidget/data/RepositoryMultiUserTest.kt`

**Interfaces:**
- Consumes: WidgetConfigStore (Task 8), `GitHubApi` fetch functions, `Lookup` onboarding model.
- Produces: `Repository.cachedDataFor(username: String): UserData?`, `Repository.saveForUser(username: String, lookup: Lookup)`, `Repository.allUsernames(): Set<String>` (global user + all widget-config usernames), `Repository.avatarFor(username: String): Bitmap?`, `Repository.refreshUser(username: String): Boolean` (fetch + persist one user, keeping old data on failure).

- [ ] **Step 1: Write failing tests**: `saveForUser` writes a per-user cache file and `cachedDataFor` reads it after a fresh `init`; `allUsernames` returns the union; `cachedDataFor` for an unknown user returns null.

- [ ] **Step 2: Run to confirm failure.**

- [ ] **Step 3: Implement** hashed filenames (`contributions_<hash>.json`, `avatar_<hash>.png`), save-with-atomic-rename like the existing cache, and an in-memory map loaded in `init()`.

- [ ] **Step 4: Update `RefreshWorker`** to iterate `Repository.allUsernames()` and refresh each via `Repository.refreshUser(username)`, keeping old data on failure. Update `ContributionWidgetProvider` so `ACTION_REFRESH` triggers the same multi-user refresh.

- [ ] **Step 5: Run tests and commit.** `feat: cache data per widget username`.

### Task 10: Widget config screen and activity

**Files:**
- Modify: `app/src/main/java/com/example/githubwidget/WidgetConfigActivity.kt`
- Create: `app/src/main/java/com/example/githubwidget/ui/widgetconfig/WidgetConfigScreen.kt`

**Interfaces:**
- Consumes: `WidgetConfigStore`, `Repository.lookup`, `Repository.saveForUser`, `WidgetLayout` choices incl. compact, reusable `Field`/`SegmentedChoice`/`PrimaryButton` from `Components.kt`.
- Produces: a Compose screen that saves a `WidgetConfig` for `EXTRA_APPWIDGET_ID` and repaints that widget.

- [ ] **Step 1: In `WidgetConfigActivity`**, read `EXTRA_APPWIDGET_ID`, set content to `WidgetConfigScreen(id, onDone = { save; WidgetUpdater.update(...); setResult(OK); finish() })`, and always offer a "Use app settings" skip that saves no override.

- [ ] **Step 2: Build `WidgetConfigScreen`**: username field (prefilled from global, validated via `Repository.lookup` with the existing error messages), layout segmented choice, optional "Customize appearance" toggle that opens a minimal palette/background subset by reusing existing controls, and Create/Save button. Show an inline error from `FetchError` without leaving the screen.

- [ ] **Step 3: Verification**: `./gradlew assembleDebug` compiles; commit `feat: add per-widget configuration screen`.

### Task 11: Thread appWidgetId through widget rendering

**Files:**
- Modify: `app/src/main/java/com/example/githubwidget/widget/WidgetUpdater.kt`
- Test: `app/src/test/java/com/example/githubwidget/widget/WidgetConfigRenderTest.kt`

**Interfaces:**
- Consumes: `WidgetConfigStore.get(id)`, `Repository.cachedDataFor`, `Repository.cachedDataFor(username)`.
- Produces: `WidgetUpdater.update(context, ids)` resolves each id's config and renders with the resolved username/design; the bitmap cache key includes the resolved identity.

- [ ] **Step 1: Write a failing test** that two ids with different resolved usernames produce different bitmaps, and that an id with no config produces the same bitmap as the global render (fallback).

- [ ] **Step 2: Run to confirm failure.**

- [ ] **Step 3: Implement**: pass `id` into `build`/`buildSize`; resolve config (`username ?: Repository.username`, `design ?: Repository.design`); read `cachedDataFor(username)` and `avatarFor(username)`; extend `RenderKey` (or the bitmap map key) with the resolved username + design JSON hash so per-id bitmaps never collide. Keep the ≤16 size variants logic untouched.

- [ ] **Step 4: Wire per-id config editing** from `DesignScreen`/`SettingsScreen`: an "Edit this widget" action that launches `WidgetConfigActivity` with the chosen id (use the most recent drawn id from `WidgetConfigStore.ids()`).

- [ ] **Step 5: Run tests and commit.** `feat: render widgets from per-widget config`.

### Task 12: Cleanup, version bump, docs, full verification

**Files:**
- Modify: `Repository.kt` (`signOut`), `ContributionWidgetProvider.kt` (`onDeleted`), `app/build.gradle.kts`, `README.md`, `PRODUCT.md`

- [ ] **Step 1: Cleanup rules**: sign-out keeps per-user cache files and only clears global state; `onDeleted(id)` removes that widget's config and deletes a per-user cache only when no remaining widget config or global username references that username (implement a `Repository.pruneUnusedUserCaches(context)`).

- [ ] **Step 2: Bump** `versionCode` to 27 and `versionName` to "4.0" in `app/build.gradle.kts`.

- [ ] **Step 3: Update** `README.md` and `PRODUCT.md` capability text to mention the six new features, keeping the "Free. No sign-in. Just your username." line and all anti-goals intact.

- [ ] **Step 4: Full verification**: `./gradlew testDebugUnitTest` and `./gradlew assembleRelease` both pass; inspect new snapshots in `app/build/widget-renders/`.

- [ ] **Step 5: Commit** `feat: release v4.0 with six new features`.
