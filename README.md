# GitHub Contributions — Android Homescreen Widget

Native Kotlin app that shows your GitHub green contribution graph right on your Android homescreen.

## 📥 Download APK

- **Latest release:** [app-debug.apk](https://github.com/mrduhlol/github-widget/releases/latest/download/app-debug.apk)
  (tagged `v*` releases — if you see 404, no release is published yet, use the option below)
- **Latest CI build:** [Actions → Build APK → newest run → Artifacts → app-debug](https://github.com/mrduhlol/github-widget/actions/workflows/build-apk.yml)

> On your phone: download the APK → open it → allow "Install unknown apps" → Install → open app → enter GitHub username.

![widget](docs/widget.png)

## Features

- **Homescreen widget** (4×2, resizable): username, today count, last-year total, green graph (last ~26 weeks), auto-refresh every 6h + manual ⟳ button
- **Config app**: enter GitHub username once, one-tap "Add widget to homescreen" (Android 8+ pin)
- **No token needed**: uses free `github-contributions-api.jogruber.de` API
- **Offline-friendly**: shows error state, retries on tap
- Small APK, no Retrofit/Glance — just `HttpURLConnection` + `Canvas` bitmap rendering

## How it works

1. `MainActivity` saves username to `SharedPreferences` (`Prefs.kt`)
2. `ContributionWidgetProvider` fires on `APPWIDGET_UPDATE` / `ACTION_REFRESH`
3. `GithubApi.fetch()` GETs `https://github-contributions-api.jogruber.de/v4/<user>?y=last`
4. `GraphRenderer.render()` draws 7×N green squares into a `Bitmap`
5. `RemoteViews.setImageViewBitmap()` puts the graph into `widget_contribution.xml`

## Build & install

### Option A — Android Studio (easiest)
1. Open this folder in Android Studio (Hedgehog or newer, JDK 17)
2. Let Gradle sync
3. Run ▶ on a physical phone (widgets don't show well on some emulators)
4. In the app, enter your GitHub username → Save
5. Long-press homescreen → **Widgets** → **GitHub Contributions** → drag it out
   - Or tap "Add widget to homescreen" in the app

### Option B — Command line
```powershell
# Needs Android SDK + JDK 17 (uses system Gradle, no wrapper jar required)
gradle assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

> Tip: every push to `main` also builds the APK in CI — grab it from
> [Actions artifacts](https://github.com/mrduhlol/github-widget/actions/workflows/build-apk.yml)
> without building locally. Push a tag like `v1.0` to publish it under
> [Releases](https://github.com/mrduhlol/github-widget/releases).

## Set your username

Open the app → type username (without `@`) → **Save & refresh widget**.
The app immediately shows that user's contribution graph below the buttons —
same bitmap the widget puts on your homescreen.
Tap the widget header anytime to reopen the app. Tap ⟳ on the widget to force refresh.

## Files

```
app/src/main/java/com/example/githubwidget/
  MainActivity.kt               # username config screen + pin widget
  Prefs.kt                      # SharedPreferences + refresh helper
  ContributionWidgetProvider.kt # AppWidgetProvider, fetch + bitmap update
  GithubContributions.kt        # GithubApi + GraphRenderer (Canvas)
app/src/main/res/
  layout/activity_main.xml
  layout/widget_contribution.xml
  xml/widget_info.xml           # 6h updatePeriod, 4x2 cells
  drawable/widget_bg.xml        # dark GitHub-style card
```

## Customize

- **Colors**: `GithubContributions.kt` → `GraphRenderer.COLORS`
- **Weeks shown**: `takeLast(26)` → e.g. `takeLast(53)` for full year (smaller squares)
- **Refresh interval**: `widget_info.xml` → `updatePeriodMillis` (min ~30 min enforced by Android)
- **API**: swap `GithubApi.fetch()` URL if you self-host the contributions API

## Troubleshooting

| Problem | Fix |
|---|---|
| Widget says "Tap to set username" | Open app, enter username, Save |
| "Couldn't load" | Check internet, username spelling (case-insensitive), tap ⟳ |
| Widget not in picker | Reinstall app, restart launcher; some launchers hide widgets in work profile |
| Graph looks squished | Resize widget wider (long-press → drag handles) |

## License

MIT — do what you want.
