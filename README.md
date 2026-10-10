# GH-widgets

**Current version: v3.0.2** — rebuilt from scratch: a simpler app, a cleaner widget, and one-tap setup.

Your GitHub contribution graph, live on your Android home screen. Pick a look in a few taps — no sign-in, no token, just your username.

<p align="center">
  <a href="https://github.com/mrduhlol/github-widget/releases/latest/download/GH-widget.apk">
    <img height="64" alt="Download GH-widgets for Android" src="https://img.shields.io/badge/GET_GH%E2%80%90WIDGETS_FOR_ANDROID-238636?style=for-the-badge&logo=android&logoColor=white" />
  </a>
  <br />
  <sub>Tap the button on your phone — the APK installs straight from your files.</sub>
  <br /><br />
  <img alt="Latest release" src="https://img.shields.io/github/v/release/mrduhlol/github-widget?style=flat-square&label=version&color=1F6FEB" />
  <img alt="APK size" src="https://img.shields.io/badge/size-%7E5.6_MB-0D1117?style=flat-square" />
  <img alt="Android" src="https://img.shields.io/badge/android-8.0%2B-39D353?style=flat-square" />
  <img alt="License" src="https://img.shields.io/badge/free-no_sign%E2%80%90in-8B949E?style=flat-square" />
</p>

## Features

- **Set up in under a minute** — type your username, confirm it's you, tap *Add to home screen*. Done.
- **What you see is what you get** — the app preview is drawn by the same code as the widget
- **Fits any size** — the graph fills the widget; make it wider to see more weeks
- **3 styles** — Classic (name, graph, stats), Graph only, Numbers (big stats + small graph)
- **9 color palettes** plus *Match wallpaper* (Android 12+) and your own custom color
- **Background** — Auto (follows your phone's light/dark mode), Dark, Light or Black, with adjustable see-through
- **Graph options** — how much history, square shape, week start, month names
- **Choose what's shown** — name, photo, total, streak, today's count
- **Stats** — full-year graph you can tap, streaks, best day, weekly and monthly charts, plain-language insights
- **Share your year** as an image
- **Works offline** — the last graph stays on screen and refreshes automatically (every 1–12 hours)
- No sign-in, no token — just your username

## Install

1. Tap **DOWNLOAD APK** above on your phone
2. Open the downloaded file
3. Allow **Install unknown apps** when asked
4. Tap **Install**

## Use

1. Open **GH Widgets** and tap **Get started**
2. Type your GitHub username (or paste your profile link) and confirm it's you
3. Tap **Add to home screen** — your phone asks where to put it
4. Change colors and style anytime in the **Widget** tab; the widget updates instantly
5. Tap the widget to open the app and get the latest data

## Development

- Kotlin + Jetpack Compose (Material 3); WorkManager for background refresh
- The widget is a single bitmap from `widget/WidgetRenderer.kt` — the in-app preview uses the same renderer
- `./gradlew testDebugUnitTest` renders sample widgets to `app/build/widget-renders/` (Robolectric) for visual review without a device
