# Six features for GH-widgets — design

Date: 2026-11-16
Status: draft for approval

## Intent and why

GH-widgets is a home-screen-first Android app: a GitHub contribution graph as a widget, set up with a username only, working offline, with no sign-in or cloud. The current product (v3.6) is a capable but passive graph. These six features were chosen to make it more useful and more "sticky" while staying inside the product's hard constraints:

1. **Streak-at-risk and milestone notifications** — turn a passive graph into a reason to come back.
2. **Multiple widgets with per-widget username/style** — the home screen is the product; let it hold more than one.
3. **Compact widget sizes** — give people a reason to keep a widget on a crowded launcher.
4. **Weekly contribution goal with progress** — give daily numbers meaning.
5. **Widget tap zones** — make the widget feel like an app, not a picture.
6. **Expanded shareables** — streak and weekly cards alongside the existing year image.

## Goals

- All six features work with **username-only setup**, **offline**, and **no server-side component**.
- No new external dependencies beyond what is already in `app/build.gradle.kts` (WorkManager, Compose Material3, `material-icons-core`).
- Existing unit tests stay green; new logic for goals, per-widget config, notification decisions, and share types gets unit tests where feasible.
- Backwards compatibility: an unconfigured widget and an existing install keep working exactly as today.

## Non-goals

- No accounts, sign-in, tokens, cloud sync, or server-side features (explicit product constraint).
- No new remote data source; all notification logic derives from the already-cached contribution graph.
- No rewriting the widget renderer from scratch — extend it.

## Anti-goals (what would make this a failure)

- A notification that fires when the streak is actually safe (false alarm) because of stale data.
- A per-widget config system that breaks the existing single-widget install or loses the global design.
- A compact layout that renders an unreadable mess at its target size.

## Constraints discovered in the current code

- Config is **global**: `Repository.kt` holds one `WidgetDesign`, one username, one `UserData`, one avatar, one font, one refresh interval in `github_widget_prefs`. The only per-id state is a `drawn` StringSet in `WidgetUpdater.kt` (`gh_widget_ids`).
- `appWidgetId` is dropped in `WidgetUpdater.update` and never reaches `buildSize`/`WidgetRenderer.render`.
- `widget_info.xml`: `minWidth=250dp minHeight=110dp minResizeWidth=110dp minResizeHeight=60dp`, free horizontal|vertical resize, `updatePeriodMillis=0`, `configuration_optional|reconfigurable`.
- Widget has two click zones today: root → MainActivity (request code 0), refresh button → `ACTION_REFRESH` broadcast (request code 1). Both `FLAG_IMMUTABLE`.
- No notification channel, no `POST_NOTIFICATIONS` permission, no boot receiver, no deep-link routing; MainActivity is `singleTask` and does not override `onNewIntent`.
- `Stats.from(...)` already computes `currentStreak`, `thisWeek`, etc.; data freshness is `UserData.fetchedAt`.
- `ShareImage` is a single fixed 1080×1350 layout with a hardcoded filename/caption; no card-type abstraction.
- Navigation is `rememberSaveable` tab state in `AppRoot.kt`; there is no NavController.
- SDK: minSdk 26, targetSdk 34, so API 33+ notification runtime permission must be handled.

## The design

### Feature 1 — Streak-at-risk and milestone notifications

**New files:** `data/StreakNotifier.kt` (decision + posting), `widget/StreakReminderWorker.kt` (periodic checker).
**Edits:** `App.kt` (create channel), `AndroidManifest.xml` (`POST_NOTIFICATIONS`), `SettingsScreen.kt` (toggle + permission request), `Repository.kt` (persist toggle, notified-date/milestone state), `RefreshWorker.kt` (call milestone check after successful refresh).

Behavior:
- **At-risk:** a periodic (1-hour) WorkManager worker checks, only during local time 21:00–23:59, whether (a) reminders are enabled and permission granted, (b) cached data was fetched today (`fetchedAt` within 6 hours), (c) `Stats.currentStreak > 0`, and (d) `Stats.today == 0`. If all hold and no at-risk notification was sent for today, post "Keep your N-day streak alive — contribute today." Store the last notified local date in prefs so it fires at most once/day.
- **Milestone:** after a successful refresh, if `currentStreak` increased and crossed one of 7, 14, 30, 50, 100, 180, 365 (or a multiple of 100), post "N-day streak!" once per milestone value; persist the last celebrated streak.
- Channel `streaks` (importance default), id `streak_notifications`. Permission requested from the Settings toggle on API 33+; if denied, the toggle is disabled with a short explanation. No notification on API<33 permission needed.
- All notification text is plain and non-alarmist; no sound/vibration beyond channel default.

*My call:* use WorkManager rather than exact alarms (no new alarm/boot permissions, consistent with the project's "no new infra" stance). The 6-hour freshness guard prevents false alarms from stale cache; if data is too old, no notification is sent. The checker is online-constrained so it won't run pointlessly offline.

### Feature 2 — Multiple widgets, per-widget username/style

**New files:** `data/WidgetConfig.kt` (model + JSON), `data/WidgetConfigStore.kt` (per-id prefs), `ui/widgetconfig/WidgetConfigScreen.kt` (Compose setup UI).
**Edits:** `WidgetConfigActivity.kt` (host the config UI, save per-id config), `WidgetUpdater.kt` (thread `appWidgetId`, resolve config, cache key), `ContributionWidgetProvider.kt` (refresh all configured usernames), `Repository.kt` (per-username cached data access), `RefreshWorker.kt` (refresh all distinct usernames), `DesignScreen.kt`/`SettingsScreen.kt` (entry point to configure this widget).

Behavior:
- New prefs file `gh_widget_configs`, key `config_<appWidgetId>` → JSON `{ username: String?, layout: String?, useGlobalDesign: Boolean }`. Null username / global flag means "fall back to the app's global settings" so existing widgets are unchanged.
- `WidgetConfigActivity` becomes a real screen: username field (prefilled from global, validated with the existing `Repository.lookup`), style choice (the existing layouts plus the two compact ones from feature 3), "Create widget"/"Save". On finish it saves the per-id config and paints that widget.
- `WidgetUpdater.update(context, ids)` passes each id into `build`/`buildSize`; `buildSize` resolves `WidgetConfigStore.get(id)` then uses per-id username/design, fetching cached data via `Repository.cachedDataFor(username)`. The bitmap cache key is extended with the resolved config identity so two differently-configured widgets don't share a bitmap.
- `RefreshWorker` gathers the distinct usernames from all placed widget ids plus the global user and refreshes each; failures keep cached data.
- Because this is opt-in via the config screen, an install that never opens it behaves exactly as before.

*My call:* keep global state as the default and layer per-id overrides on top, rather than migrating everything to per-widget state. This protects existing users and keeps the change incremental. Per-username data is cached in files named `contributions_<hash>.json` + `avatar_<hash>.png`.

### Feature 3 — Compact widget sizes

**Edits:** `WidgetDesign.kt` (two new `WidgetLayout` values: `STREAK`, `TODAY`), `WidgetRenderer.kt` (render paths), `DesignScreen.kt` (layout choices), `widget_info.xml` (lower `minResizeWidth`/`minResizeHeight` to 60dp/60dp so a ~1×1/2×1 slot is reachable), `WidgetRenderSnapshotTest.kt` (snapshots at ~60×60 and ~160×60dp).

Behavior:
- `STREAK`: a compact tile showing the current streak number and a small flame — readable at ~60×60dp.
- `TODAY`: a wide short strip showing "Today: N" plus the weekly goal progress line (feature 4) — readable at ~160×60dp.
- Both fall back to a minimal "Tap to set up" placeholder when signed out, and degrade to a single centered number at extremely small sizes.
- Existing layouts unchanged; this only adds options.

*My call:* implement compact formats as new layouts on the single resizable receiver rather than a second AppWidgetProvider — fewer moving parts, and the renderer already adapts to arbitrary dp sizes. The min-resize reduction is safe because compact layouts have dedicated tiny-size rendering.

### Feature 4 — Weekly contribution goal

**Edits:** `Repository.kt` (`weeklyGoal` key + StateFlow + setter, default 0 = off), `SettingsScreen.kt` (goal stepper in a SettingsGroup: Off / 5 / 10 / 20 / 50 / 100), `WidgetDesign.kt` (`showGoal` boolean, default false), `DesignScreen.kt` (toggle), `WidgetRenderer.kt` (thin progress line when enabled), `StatsScreen.kt` (new "Weekly goal" card with a circular progress ring using `Stats.thisWeek`, `LinearProgressIndicator`/`CircularProgressIndicator` from Material3), `data/Stats.kt` (no change needed — `thisWeek` exists).

Behavior:
- Goal 0 means off: no widget line, no Stats card.
- Widget: when `showGoal && weeklyGoal > 0`, draw a thin rounded progress track in the reserved header/footer area, clamped 0–100%, with the text `thisWeek/goal` when there is room.
- Stats: a card titled "Weekly goal" with a circular ring, `thisWeek` of `goal`, and a short line ("On track" / "N to go" / "Goal met"). Uses the same number font and palette as the rest of Stats.

*My call:* the ring lives in Stats (where there is room and Material3 provides it); the widget gets a slim progress bar plus optional count. This keeps the widget readable and the goal visible at a glance.

### Feature 5 — Widget tap zones

**Edits:** `res/layout/widget_root.xml` (+ night variant) (add two invisible overlay views: `zone_avatar` top-start over the header, `zone_stats` covering the lower area), `WidgetUpdater.kt` (distinct PendingIntents/request codes 2 and 3; root keeps 0), `widget/IntentRouter.kt` (new: a `MutableSharedFlow<String>` carrying `"stats"` / `"widget"` / `"profile"`), `MainActivity.kt` (override `onNewIntent`, emit target; also handle create), `AppRoot.kt` (collect router, set initial tab), `ContributionWidgetProvider.kt` (ACTION_* constants only if needed).

Behavior:
- Whole widget (root): opens the app on the **Stats** tab (previously opened Widget tab). This is the "tap graph → stats" behavior.
- Avatar/header zone: opens the contributor's GitHub profile in the browser via `ACTION_VIEW` (`https://github.com/<username>`), resolved to the per-widget username when configured. Uses a distinct PendingIntent (request code 2).
- Lower/stats zone: opens the app on the **Stats** tab with a distinct PendingIntent (request code 3) — effect is the same as root but gives a dedicated, correctly positioned target for the streak/stat area.
- Refresh button stays unchanged (request code 1).
- Because MainActivity is `singleTask`, `onNewIntent` reads the `target` extra and emits it; `AppRoot` sets `tab` via `LaunchedEffect`. The browser target is handled entirely by a `PendingIntent.getActivity(ACTION_VIEW, Uri...)` so no in-app routing is needed for it.

*My call:* use a small in-process `IntentRouter` flow rather than introducing Navigation Compose — the app's navigation is intentionally just three tabs, and a flow is the least invasive way to drive it from an intent. Overlay zones are approximate (the widget is one bitmap), positioned to match the renderer's reserved header/footer areas.

### Feature 6 — Expanded shareables

**Edits:** `ShareImage.kt` (introduce `ShareCardType { YEAR, STREAK, WEEK }`; factor background/avatar/footer helpers; add `renderStreak` and `renderWeek`; distinct filenames, captions, chooser titles), `StatsScreen.kt` (`ShareCard` gets a `SegmentedChoice` for type), `res/xml/filepaths.xml` (unchanged — reuse the `shared` cache path with distinct filenames).

Behavior:
- **Year** (existing) — unchanged.
- **Streak** — big current-streak number with a flame motif, longest streak, a small year graph, and the same footer. Caption: "My N-day GitHub streak."
- **Week** — this week's count, weekly goal progress (if set), and the weekday bar chart. Caption: "My GitHub week: N contributions."
- Each type writes to `shared/<type>-<timestamp>.png` to avoid stale overwrite races, and uses the existing FileProvider authority.

*My call:* keep the same 1080×1350 canvas for all three types so the sharing pipeline and file provider need no structural changes; add the type selector as a segmented control above the existing button.

## Sequencing (builder's order, to be confirmed in the plan)

1. Feature 4 (goal) — smallest, isolated, no structural change.
2. Feature 1 (notifications) — new infra but contained.
3. Feature 3 (compact layouts) — renderer + tests.
4. Feature 6 (shareables) — UI + image code.
5. Feature 5 (tap zones) — requires intent routing.
6. Feature 2 (per-widget config) — largest structural change; do last so its cache/config changes land on a stable base.

## Resolved questions (builder check — my calls)

These were the ambiguities a builder would otherwise guess at. Each is settled here as my call so it does not change what gets built:

1. **First graph for a non-global widget username:** at config time, persist the `Repository.lookup` result into the per-user cache files immediately, so the new widget renders offline from the first paint. `lookup` today is fetch-without-save; add a `saveForUser(username, lookup)` and use it from `WidgetConfigScreen`. No blank-widget-until-refresh window.
2. **Which accounts notifications watch:** all configured usernames plus the global account, with per-username notified-date/milestone state. A second account on the home screen is still "the user's" streak; notifications should cover it.
3. **Tapped widget's stats:** show the tapped widget's username. `IntentRouter` carries `username`; `StatsScreen` accepts an optional data override and reads `Repository.cachedDataFor(username)` without mutating global state (so other widgets are untouched).
4. **"Per-widget style" model:** replace the vague `useGlobalDesign` flag with `WidgetConfig(username: String?, design: WidgetDesign?)`. A null design means "inherit global", so palette/opacity/background/font can be overridden per widget with a full snapshot when provided. The in-app preview stays honest because it renders the exact design.
5. **Widget add flow:** `WidgetConfigActivity` stays strictly opt-in via `configuration_optional`; it offers an explicit "Use app settings" skip that saves no override. Existing adds and installs behave exactly as today. If no global username exists, the config screen shows the username field inline (no coupling to onboarding).
6. **Reminders default + milestone baseline:** reminders default **off** (no permission prompt on upgrade, preserves existing behavior), with a one-time discoverability hint in Stats. On sign-in/config of a username, initialize the milestone baseline to that user's current streak so a first sync never fires a burst of milestone notifications.
7. **Sign-out / onDeleted:** sign-out keeps per-user cache files (other widgets may still reference them) and only clears global state; `onDeleted` removes that widget's config and deletes a per-user cache only when no remaining widget (or global user) references that username.
8. **Compact layouts and tap zones:** compact `STREAK`/`TODAY` layouts are single-zone — root tap → Stats — with a small 24dp refresh target at top-end and no browser-profile zone (the 48dp avatar overlay would cover the whole widget). Normal layouts keep all three zones.
9. **Week shareable chart:** use this week's 7 daily bars (derived from `contributions.days` for the current Monday-based week), not the year-long `Stats.weekdayTotals`. It is a weekly summary, so it should show the week.

## What is left to the builder

- Exact code structure, naming, and Kotlin idioms, following surrounding files.
- `WidgetConfigScreen` should reuse `SegmentedChoice`, `PrimaryButton`, and `Field` from `Components.kt` rather than inventing new controls.
- Test cases and fixture values for the new logic.
- Bump `versionCode`/`versionName` in `app/build.gradle.kts` and update `README.md`/`PRODUCT.md` capability text as part of the build.
