---
workflow: product-launch-video
flow: automation
storyboard: "no"
message: "Your GitHub graph on your home screen in under a minute — free, no sign-in, just your username."
destination: linkedin
aspect: 1280x960
language: en
length: 30s
angle: setup-in-a-minute
audience: Android users with a GitHub profile (LinkedIn viewers)
narration: "yes"
---

## Intent

30-second professional promo for GH-widgets with a quick app tour, posted on LinkedIn at 4:3. Market the product (promo with tour beats, not a show-it-as-is capture). Angle: setup in under a minute — type your username, confirm it's you, add to home screen. Tone: professional, confident, trust-building. End card shows the website link.

## Customizations

- English voiceover, full narration (~30s, ~75 words).
- Live captions, styled to match brand, always legible (keep-out band respected).
- Soft background music bed, ducked/carved under voice.
- 3D camera angles and depth (tilt/push/rotate on widget mockups, seek-safe GSAP).
- Professional finish: GitHub-dark surfaces with contribution green.
- End card: gh.abhishek-a.in as the website link, plus GitHub releases download note.

## Notes

- Accuracy (PRODUCT.md + README.md + build.gradle.kts source of truth): name GH-widgets; v3.1.4; ~5.6 MB; Android 8.0+; free; no sign-in, no token, username only; 3 styles (Classic, Graph only, Numbers); 9 palettes + Match wallpaper (Android 12+) + custom color; name/photo/total/streak/today toggles; streaks, best day, weekly/monthly charts, plain-language insights; share-year image; works offline, refresh every 1–12 hours, last graph stays on screen.
- Never claim accounts, sign-in, cloud sync, or server-side features. No testimonials, reviews, or usage stats (none exist). Demo contribution figures (e.g. 1,248 / 23 / 31) must read as demo.
- Integration check: user confirmed 4:3 (1280x960) for LinkedIn even though the route normally recommends 1:1 for feed — locked per explicit request.
- Auth: HeyGen not signed in — use local engines (Kokoro voice, MusicGen/BGM local). Do not silently drop voice/music/captions.
- Deferred asks: none — one-shot automation. Caption identity and voice picked at build with one-line reasons.
