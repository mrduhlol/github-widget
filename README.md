# GH-widgets

Your GitHub contributions, on your Android home screen. Set up with your username—no sign-in or token needed.

<p align="center">
  <img src="docs/assets/app-icon.svg" width="96" height="96" alt="GH-widgets app icon" />
</p>

<p align="center">
  <a href="https://github.com/mrduhlol/github-widget/releases/latest/download/gh-widgets.apk">
    <img src="https://img.shields.io/badge/Download-Android_APK-22863A?style=for-the-badge&amp;logo=android&amp;logoColor=white" alt="Download APK for Android" />
  </a>
</p>

Choose a widget style, colors, background, and the stats you want to see. Resize it to fit your home screen, explore your activity in the app, or share your year as an image. Your last synced graph stays available offline.

## New in v3.8

- Add customizable Up Next and Review Queue widgets for public pull requests.
- Profile cards fit longer names and align link tap targets with their icons.
- Browse the widget catalog with clearer category dividers and updated previews.
- Newly pinned widgets can recover when the launcher omits the widget ID. If rendering fails, tap the widget to reopen the app.
- Fresh installs start without restored account data, settings, or cached activity.
- Jump to a date in the contribution heatmap.

## New in v3.7.1

- Removed manual reload controls from home-screen widgets. Widgets refresh automatically.
- Newly pinned widgets draw when the launcher confirms placement and show a loading card before the first draw.

## New in v3.7

- Browse widget thumbnails in the app’s gallery.
- Choose from four Weekly Goal styles: Arc, Meter, Week, and Ascent.
- Tap the contribution widget to open your stats directly.
- Streak widgets and GitHub ID cards in landscape and portrait.
- Choose which ID card details appear: photo, display name, followers, following, website, and individual public social links. Your username is always shown.
- Optional Doto dot-matrix numbers, with regular text for small labels.
- Widgets keep their own type and share the connected profile, colors, font, and visibility settings. Goals count contributions from Monday through Sunday.

Android 8.0 or newer. Open the signed `gh-widgets.apk` to install or update the app.

## Privacy & Data Sources

- **Public data only:** GH-widgets never asks for a GitHub password or OAuth token. Only the GitHub username you enter leaves your device.
- **Endpoints contacted:**
  - `https://github-contributions-api.jogruber.de` for public contribution calendar data.
  - `https://api.github.com` for public profile metadata, avatars, and public pull request review requests (private pull requests are not visible without authentication).
  - `https://api.github.com/repos/mrduhlol/github-widget/releases/latest` (at most once per day) to check for app updates.

## Release Signing & Migration

Release builds are signed exclusively via GitHub Actions Secrets (`RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`) and never use a committed keystore. If you are upgrading from an older build signed with the retired repository key, uninstall the old app once before installing the new signed APK. See [SECURITY.md](SECURITY.md) for details.

<p align="center">
  <img src="docs/screenshots/welcome.png" width="240" alt="GH-widgets first-run guide with a labeled example widget" />
  <img src="docs/screenshots/widget.png" width="240" alt="Widget editor with live preview and appearance controls" />
  <img src="docs/screenshots/stats.png" width="240" alt="GitHub activity statistics in GH-widgets" />
</p>
