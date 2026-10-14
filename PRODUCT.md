# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Android users with a GitHub profile who want their contribution graph visible on their phone home screen. Single confirmed audience.

## Product Purpose

GH-widgets puts a GitHub contribution graph on the Android home screen as a widget. It exists so users see their activity at a glance without opening an app or signing in anywhere. Success means installs of the free APK through a professional download page.

## Positioning

No sign-in, no token, no account: the username alone sets everything up. Neighboring approaches require authentication or a companion service; this one is free, offline-capable, and done in under a minute.

## Operating Context

Native Android app (Kotlin, Jetpack Compose, WorkManager), Android 8.0 and newer. Distributed as a signed APK through GitHub releases (v3.1.1, about 11 MB). The widget refreshes on a user schedule of 1–12 hours and keeps showing the last graph offline. Web surface: a static showcase and download page in docs/, served over plain HTTP(S).

## Capabilities and Constraints

Confirmed: three widget styles (Classic, Graph only, Numbers); nine color palettes plus wallpaper-matching and custom color; name, photo, total, streak, and today toggles; streaks, best day, weekly and monthly charts, and plain-language insights; share-year image.

Constraints: Android-only APK distribution; refresh at most every 12 hours and at least hourly; explicitly undecided: nothing open — the scope above is the whole product. Never claim accounts, sign-in, cloud sync, or server-side features.

## Brand Commitments

Name GH-widgets. Voice line: "Free. No sign-in. Just your username." Visual anchor: GitHub-dark surfaces with contribution green. Source of truth and download channel: github.com/mrduhlol/github-widget.

## Evidence on Hand

- README.md (feature list, setup flow)
- app/build.gradle.kts (versionName 3.1.1, versionCode 20)
- docs/index.html + docs/assets (showcase site, promo clip, credited photos)
- videos/gh-widgets-promo/renders/video.mp4 (24s promo)
- Absence future work must not fabricate: no real app screenshots exist; interface fragments are drawn in code. No testimonials, reviews, or usage statistics exist.

## Product Principles

1. The username is the only thing ever asked for.
2. Everything useful works offline.
3. The home screen is the product surface, not a companion app.
4. Numbers shown are real or clearly labeled demo figures.
