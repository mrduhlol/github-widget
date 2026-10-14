---
target: docs/index.html
total_score: 23
max_score: 32
na_heuristics: 7,10
p0_count: 2
p1_count: 3
target_identity: "file:/home/abhishek/github-widget/docs/index.html"
target_fingerprint: "sha256:03f7e139a26c3ae10922143f0b657f45b0f32c937ff21d94086f337b0e10e8d1"
target_path: /home/abhishek/github-widget/docs/index.html
timestamp: 2026-10-14T12-18-40Z
slug: docs-index-html
---
# Critique: docs/index.html (GH-widgets showcase)

## Heuristic scores (Persuade surface; 7 and 10 n/a)

| # | Heuristic | Score | Key Issue |
|---|-----------|-------|-----------|
| 1 | Visibility of System Status | 3 | No status for the conversion itself (APK download leaves the site) |
| 2 | Match System / Real World | 4 | Setup mirrors the real install mental model |
| 3 | User Control and Freedom | 3 | Demos reversible; band toggles confuse undo |
| 4 | Consistency and Standards | 2 | Toggle means two things; Nums vs Numbers labels |
| 5 | Error Prevention | 3 | No forms; sideload scare not pre-empted |
| 6 | Recognition Rather Than Recall | 3 | Shows not tells; must recall own handle |
| 7 | Flexibility and Efficiency | n/a | Single-visit Persuade, no power workflows |
| 8 | Aesthetic and Minimalist Design | 3 | Strong system; widget repeated 3x, 5 identical bands |
| 9 | Error Recovery | 2 | No fallback if APK blocked or video fails |
| 10 | Help and Documentation | n/a | Steps + bug link suffice for this surface |
| **Total** | | **23/32** | **Good (72%)** |

## Design Specificity Verdict

Authored for THIS product: the contribution-graph artifact, real figures (v3.1.1, 11 MB, refresh cadence, dp widths), and the no-sign-in voice cannot belong to another product. Diluters: two stock photos (sunlit tablet grid, unrelated phone screen) and a captionless promo video.

## Deterministic scan

6 warnings, all in docs/index.html: 1x low-contrast (button hover white on #2ea043 at 3.4:1); 5x cramped-padding on bands — defended as false positives (computed 16px+ padding; detector misread the var()). Browser evidence added two real mobile bugs the review missed: hero text touches viewport edges at 390px (media query zeroes horizontal padding), and ~38px of demo-panel content clips inside overflow:hidden at 390px. Tap targets pass, focus visible, states discernible, 0 console errors.

## Overall Impression

A proof-driven page whose strongest moment is flipping the configurators; the story is a trust gap at conversion plus mobile breakage. Gut: fun toy, long scroll, safe-but-unexplained download.

## What's Working

1. Real-size working demos as proof — users learn Classic vs Numbers by doing.
2. Voice-to-proof pairing — every claim carries its receipt in the same viewport.
3. Cohesive dark system — GitHub-at-night, distinct from generic SaaS pages.

## Priority Issues

**[P0] Mobile hero bleed + demo clipping (browser evidence).** At 390px the headline touches both edges and demo controls clip ~38px. Why: media query resets hero padding to 0; widget min-content overflows the 1fr track inside overflow:hidden. Fix: padding-top/bottom only on the mobile hero rule; min-width:0 plus smaller graph cells on tiny screens. Suggested command: $impeccable polish.

**[P0] Direct-APK trust gap at conversion.** Three CTAs deep-link the APK with only microcopy. Android sideload scares first-timers. Fix: 2-line installer reassurance under each CTA plus the All-releases link adjacent in hero. Suggested command: $impeccable clarify.

**[P1] Band toggles misuse switch affordance.** All five bands default open with on-state switches; turning one off reads as disabling the feature. Fix: chevron accordion or static sentences; reserve switches for the demo panel. Suggested command: $impeccable polish.

**[P1] Hero widget duplicates demo widget.** Identical configurator twice in two viewports stalls persuasion. Fix: static hero preview, single configurator in #widget. Suggested command: $impeccable layout.

**[P2] Setup feels faked on dev's own handle.** Non-editable field, no live input, photo promise never shown. Fix: real input that renames the step-3 widget; label figures as example. Suggested command: $impeccable delight.

**[P2] Width section cools pre-CTA momentum.** dp/pixel math serves builders, not downloaders. Fix: collapse to one line under Features. Suggested command: $impeccable distill.

## Persona Red Flags (primary action: download the APK)

**Jordan (first-timer):** ghost-link splits intent (code vs install); all examples @mrduhlol, no proof for sparse graphs; no install preview before the unknown-apps dialog.
**Riley (stress tester):** Numbers figure lacks context; Today toggle inconsistent across widgets; dots/toggles tight; video 404 would leave a black hole with no fallback text.
**Casey (mobile):** proof pushed below fold; no sticky CTA; demo controls wrap and clip; long scroll before Get GH-widgets.

## Minor Observations

- Setup field/confirm blocks render unstyled (no CSS definitions).
- Mobile H1 crowds the wordmark at 3 lines.
- Footer credits paragraph outweighs value props; shrink it.
- Video needs a "24s promo, no sound" caption.
- Skip link targets #widget, skipping the CTA.
- Ghost links ~4.1:1, borderline at small sizes.
- Duplicate version meta wording hero vs CTA.
- Wordmark vs headline plural drift (GH-widgets vs Get GH-widgets.).

## Questions to Consider

- If "just your username" is the magic, why can't visitors type theirs in the hero?
- Why does a dark-mode Android product sell itself with a sunlit iPad photo?
- If no sign-in is the trust anchor, why does the sideload ask get the least explanation?
