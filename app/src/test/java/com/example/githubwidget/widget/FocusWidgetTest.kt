package com.example.githubwidget.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.githubwidget.data.*
import com.example.githubwidget.design.*
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FocusWidgetTest {
    @Test fun weeklyGoalStylesKeepDistinctProvidersAndRenders() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val today = LocalDate.now()
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val user = data(listOf(Day(monday.minusDays(1), 100, 4), Day(today, 14, 3),
            Day(today.plusDays(1), 100, 4)))
        val styles = WidgetLayout.entries.filter { it.isWeeklyGoal }
        assertEquals(4, styles.size)
        assertEquals(4, styles.map { WidgetKind.forLayout(it).component(context) }.distinct().size)
        assertEquals(WidgetLayout.WEEKLY_GOAL, WidgetDesign.fromJson("""{"layout":"WEEKLY_GOAL"}""").layout)
        for ((width, height) in listOf(340f to 170f, 170f to 170f)) {
            val renders = styles.map { style ->
                val design = WidgetDesign(layout = style, weeklyGoal = 20)
                assertEquals(style, WidgetKind.forLayout(style).resolvedLayout(WidgetLayout.CLASSIC))
                assertTrue(FocusWidgetPainter.accessibilityText(design, user).contains("14 of 20"))
                WidgetRenderer.render(context, design, user, null, width, height, true)
            }
            for (index in renders.indices) {
                assertTrue(renders.take(index).none { it.sameAs(renders[index]) })
            }
        }
    }

    @Test fun streakCalendarStatesFollowUtcAndPreserveTheOpenDay() {
        val today = LocalDate.of(2026, 11, 20)
        val now = today.atStartOfDay().toInstant(ZoneOffset.UTC)
        val none = WidgetRenderer.Placeholder.NONE
        fun snapshot(vararg counts: Int, at: Instant = now): StreakWidgetPainter.Snapshot {
            val days = counts.mapIndexed { index, count -> Day(today.minusDays(index.toLong()), count, 0) }
            return StreakWidgetPainter.snapshot(data(days).copy(fetchedAt = at.toEpochMilli()), none, today)
        }
        val active = snapshot(4, 2, 1, 0)
        assertEquals(StreakWidgetPainter.Snapshot(StreakWidgetPainter.State.ACTIVE, 3), active)
        assertEquals(StreakWidgetPainter.Snapshot(StreakWidgetPainter.State.PENDING, 2), snapshot(0, 2, 1, 0))
        assertEquals(StreakWidgetPainter.Snapshot(StreakWidgetPainter.State.BROKEN, 0), snapshot(0, 0, 1))
        assertEquals(StreakWidgetPainter.Snapshot(StreakWidgetPainter.State.ZERO, 0), snapshot(0, 0, 0))
        assertEquals(StreakWidgetPainter.Snapshot(StreakWidgetPainter.State.CHECKING), snapshot(0, 0, 1, at = now.minusSeconds(1)))
        assertEquals(StreakWidgetPainter.Snapshot(StreakWidgetPainter.State.CHECKING), snapshot(0))
        assertEquals(StreakWidgetPainter.Snapshot(StreakWidgetPainter.State.ACTIVE, 2, lowerBound = true), snapshot(1, 1))
        val roll = data(listOf(Day(today.minusDays(1), 2, 0), Day(today.minusDays(2), 0, 0)))
            .copy(fetchedAt = now.minusSeconds(60).toEpochMilli())
        assertEquals(StreakWidgetPainter.Snapshot(StreakWidgetPainter.State.PENDING, 1),
            StreakWidgetPainter.snapshot(roll, none, today))
        assertEquals(StreakWidgetPainter.Snapshot(StreakWidgetPainter.State.FAILED, 1),
            StreakWidgetPainter.snapshot(roll, WidgetRenderer.Placeholder.FAILED, today))
        assertEquals(StreakWidgetPainter.State.CHECKING,
            StreakWidgetPainter.snapshot(null, WidgetRenderer.Placeholder.LOADING, today).state)
        assertNull(StreakWidgetPainter.snapshot(null, WidgetRenderer.Placeholder.FAILED, today).count)
        assertEquals(now, StreakRolloverWorker.nextBoundary(now.minusSeconds(1)))
        assertEquals(now.plusSeconds(86400), StreakRolloverWorker.nextBoundary(now))
    }

    @Test fun renderAdaptiveStreakStatesWithThemeContrast() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val design = WidgetDesign(layout = WidgetLayout.STREAK)
        val today = LocalDate.now(ZoneOffset.UTC)
        val out = File("build/widget-renders").apply { mkdirs() }
        fun withDays(first: Int, count: Int): UserData = data(
            (0..count + 1).map { index -> Day(today.minusDays(index.toLong()),
                if (index == 0) first else if (index <= count) 1 + (index * 7) % 5 else 0, 0) }
        )
        val active = withDays(1, 141)
        val pending = withDays(0, 142)
        val broken = data(listOf(Day(today, 0, 0), Day(today.minusDays(1), 0, 0), Day(today.minusDays(2), 3, 0)))
        val longRun = data((0 until 366).map { Day(today.minusDays(it.toLong()), 1, 1) })
        assertEquals(StreakWidgetPainter.Snapshot(StreakWidgetPainter.State.ACTIVE, 366, lowerBound = true),
            StreakWidgetPainter.snapshot(longRun, WidgetRenderer.Placeholder.NONE, today))
        WidgetRenderer.render(context, design, longRun, null, 110f, 60f, dark = true)
        assertEquals(142, StreakWidgetPainter.snapshot(active, WidgetRenderer.Placeholder.NONE, today).count)
        val sizes = listOf(170f to 170f, 340f to 170f, 340f to 260f, 170f to 340f, 110f to 60f)
        for ((width, height) in sizes) {
            for ((label, user) in listOf("active" to active, "pending" to pending, "broken" to broken)) {
                val rendered = WidgetRenderer.render(context, design, user, null, width, height, dark = true)
                val light = WidgetRenderer.render(context, design.copy(background = Background.LIGHT), user, null,
                    width, height, dark = false)
                assertTrue(rendered.sameAs(light))
                assertEquals(255, android.graphics.Color.alpha(rendered.getPixel(rendered.width / 2, 5)))
                File(out, "streak_redesign_${label}_${width.toInt()}x${height.toInt()}.png")
                    .outputStream().use { assertTrue(rendered.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            }
        }
        for ((label, user, placeholder) in listOf(
            Triple("zero", withDays(0, 0), WidgetRenderer.Placeholder.NONE),
            Triple("loading", null, WidgetRenderer.Placeholder.LOADING),
            Triple("failed", null, WidgetRenderer.Placeholder.FAILED),
        )) {
            val rendered = WidgetRenderer.render(context, design, user, null, 340f, 170f, true, placeholder)
            File(out, "streak_redesign_${label}_340x170.png")
                .outputStream().use { assertTrue(rendered.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        }
        for (ink in listOf(StreakWidgetPainter.ACTIVE, StreakWidgetPainter.MUTED, android.graphics.Color.WHITE)) {
            assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(ink, StreakWidgetPainter.SURFACE) >= 4.5)
        }
    }

    private val today = LocalDate.of(2026, 11, 17)
    private fun data(days: List<Day> = listOf(Day(today.minusDays(1), 4, 2), Day(today, 0, 0))) = UserData(
        Contributions("octocat", 1234, days),
        Profile("octocat", "The Octocat", "", 120, 42, "https://example.com",
            listOf(ProfileLink("Mastodon", "https://mastodon.social/@octocat"), ProfileLink("X", "https://x.com/octocat")), publicRepos = 28),
        System.currentTimeMillis(),
    )

    @Test fun metricsUseCalendarDaysAndMondayWeeks() {
        val user = data(listOf(Day(today.minusDays(2), 8, 3), Day(today.minusDays(1), 4, 2), Day(today, 0, 0)))
        val a = FocusWidgetPainter.activity(user, today)
        assertEquals(2, a.streak)
        assertEquals(0, a.today)
        assertEquals(4, a.week) // Sunday is outside this calendar week.
        assertEquals(0, FocusWidgetPainter.activity(user, today.plusDays(2)).streak)
        assertEquals(0, FocusWidgetPainter.activity(user, today.plusDays(7)).week)
    }

    @Test fun dedicatedProvidersRetainTheirType() {
        assertEquals(WidgetLayout.STREAK, WidgetKind.STREAK.resolvedLayout(WidgetLayout.PROFILE))
        assertEquals(WidgetLayout.PROFILE, WidgetKind.PORTRAIT_PROFILE.resolvedLayout(WidgetLayout.WEEKLY_GOAL))
        assertEquals(WidgetKind.PR_INBOX, WidgetKind.forLayout(WidgetLayout.PR_INBOX))
        assertEquals(WidgetLayout.PR_INBOX, WidgetKind.PR_INBOX.resolvedLayout(WidgetLayout.CLASSIC))
        assertEquals(WidgetLayout.CLASSIC, WidgetKind.CONTRIBUTIONS.resolvedLayout(WidgetLayout.PROFILE))
        assertEquals(WidgetLayout.GRAPH, WidgetKind.CONTRIBUTIONS.resolvedLayout(WidgetLayout.GRAPH))
        assertEquals(WidgetLayout.GRAPH, WidgetKind.CONTRIBUTIONS.resolvedLayout(WidgetLayout.PROFILE, WidgetLayout.GRAPH))
    }

    @Test fun reviewInboxUsesGitHubPullRequestLinksAndPreservesTheSearchTotal() {
        val results = ReviewResults.fromJson(JSONObject("""{
            "total_count": 17,
            "items": [
                {"html_url":"https://github.com/octocat/Hello-World/pull/42","title":"Improve docs","body_text":"Clarify the setup steps.\n\nAdd an example."},
                {"html_url":"https://github.com/octocat/Hello-World/issues/43","title":"Not a pull request"}
            ]
        }"""), fetchedAt = 10L)
        assertEquals(17, results.total)
        assertEquals(listOf(ReviewRequest("Improve docs", "octocat/Hello-World", "42",
            "https://github.com/octocat/Hello-World/pull/42", "Clarify the setup steps. Add an example.")), results.requests)
    }

    @Test fun settingsSurviveRoundTripAndOldInstalls() {
        val design = WidgetDesign(layout = WidgetLayout.PROFILE, weeklyGoal = 50,
            profileShowName = false, profileShowAvatar = false, profileShowFollowers = false,
            profileShowFollowing = false, profileShowGitHub = false, profileShowWebsite = false,
            profileShowSocials = false, profileShowHandle = false, profileShowRepos = false,
            profileTypeface = ProfileTypeface.CONDENSED, profileStreakReplaces = ProfileCount.FOLLOWING,
            profileHiddenLinks = setOf("https://example.com"))
        assertEquals(design, WidgetDesign.fromJson(design.toJson()))
        assertEquals(WidgetDesign(), WidgetDesign.fromJson("{}"))
        assertEquals(WidgetDesign(), WidgetDesign.fromJson("""{"profileShowProject":true,"profileProject":"Legacy status"}"""))
        assertEquals(1, WidgetDesign.fromJson("{\"weeklyGoal\":0}").weeklyGoal)
    }

    @Test fun profileChoicesHideCountsAndLinksFromAccessibilityToo() {
        val design = WidgetDesign(layout = WidgetLayout.PROFILE, profileShowFollowers = false,
            profileShowFollowing = false, profileShowWebsite = false, profileShowSocials = false)
        val description = FocusWidgetPainter.accessibilityText(design, data())
        assertTrue(description.contains("@octocat"))
        assertFalse(description.contains("followers"))
        assertFalse(description.contains("following"))
        assertEquals(listOf("https://github.com/octocat"), FocusWidgetPainter.profileLinks(design, data()).map { it.url })
        assertTrue(FocusWidgetPainter.profileLinks(design.copy(profileShowGitHub = false), data()).isEmpty())
        val hidden = WidgetDesign(layout = WidgetLayout.PROFILE, profileHiddenLinks = setOf("https://x.com/octocat"))
        assertFalse(FocusWidgetPainter.profileLinks(hidden, data()).any { it.url == "https://x.com/octocat" })
        assertNull(GitHubApi.webUrl("javascript:alert(1)"))
        assertNull(GitHubApi.webUrl("file:///etc/passwd"))
        assertEquals("https://example.com", GitHubApi.webUrl("example.com"))
    }

    @Test fun absentSocialAccountDoesNotCreateAPhantomConnection() {
        assertNull(GitHubApi.twitterProfileUrl(null))
        assertNull(GitHubApi.twitterProfileUrl("null"))
        assertNull(GitHubApi.twitterProfileUrl(""))
        assertNull(GitHubApi.twitterProfileUrl("invalid/handle"))
        assertEquals("https://x.com/octocat", GitHubApi.twitterProfileUrl("octocat"))
    }

    @Test fun profileDockKeepsTouchTargetsAndRoutesOverflow() {
        val design = WidgetDesign(layout = WidgetLayout.PROFILE)
        val wide = FocusWidgetPainter.profileDock(308f, 138f, design, data())
        assertEquals(4, wide.size)
        assertTrue(wide.all { it.url != null })
        val portrait = FocusWidgetPainter.profileDock(146f, 316f, design, data())
        assertEquals(3, portrait.size)
        assertNull(portrait.last().url)
        assertEquals("All connections", portrait.last().label)
        assertTrue(146f / portrait.size >= 48f)
        assertTrue(FocusWidgetPainter.profileDock(86f, 36f, design, data()).isEmpty())
        val hidden = design.copy(profileHiddenLinks = wide.mapNotNull { it.url }.toSet())
        assertTrue(FocusWidgetPainter.profileDock(308f, 138f, hidden, data()).isEmpty())
        val text = FocusWidgetPainter.accessibilityText(design.copy(profileShowHandle = false,
            profileShowRepos = false), data())
        assertFalse(text.split(". ").contains("@octocat"))
        assertFalse(text.contains("public repositories"))
    }

    @Test fun selectedCountsChangeTheProfileRender() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val design = WidgetDesign(layout = WidgetLayout.PROFILE)
        val user = data()
        val shown = WidgetRenderer.render(context, design, user, null, 340f, 170f, true)
        val hidden = WidgetRenderer.render(context, design.copy(profileShowFollowers = false,
            profileShowFollowing = false, profileShowRepos = false), user, null, 340f, 170f, true)
        assertFalse("Selected metrics must change the rendered card", shown.sameAs(hidden))
    }

    @Test fun profileTypefaceAndStreakReplacementReachTheRenderer() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val yesterday = LocalDate.now().minusDays(1)
        val user = data(listOf(Day(yesterday, 2, 1), Day(yesterday.minusDays(1), 3, 1)))
        val design = WidgetDesign(layout = WidgetLayout.PROFILE)
        fun render(settings: WidgetDesign) = WidgetRenderer.render(context, settings, user, null, 340f, 170f, true)
        val original = render(design)
        assertFalse(original.sameAs(render(design.copy(profileTypeface = ProfileTypeface.SANS))))

        val withStreak = design.copy(profileStreakReplaces = ProfileCount.FOLLOWING)
        assertFalse(original.sameAs(render(withStreak)))
        val description = FocusWidgetPainter.accessibilityText(withStreak, user)
        assertTrue(description.contains("2 days contribution streak"))
        assertTrue(description.contains("120 followers"))
        assertTrue(description.contains("28 public repositories"))
        assertFalse(description.contains("42 following"))
    }

    @Test fun renderNewWidgetsAcrossSizesAndFonts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val avatar = android.graphics.BitmapFactory.decodeFile("../docs/assets/avatar.png")
        val out = File("build/widget-renders").apply { mkdirs() }
        for (layout in listOf(WidgetLayout.STREAK, WidgetLayout.WEEKLY_GOAL, WidgetLayout.PROFILE)) {
            for ((width, height) in listOf(170f to 90f, 170f to 170f, 340f to 170f, 170f to 340f, 110f to 60f)) {
                for (dark in listOf(false, true)) {
                    val bmp = WidgetRenderer.render(context, WidgetDesign(layout = layout), data(), avatar,
                        width, height, dark, font = NumberFont.DOT_MATRIX, maxPixels = 200_000)
                    assertTrue(bmp.width * bmp.height <= 200_000)
                    assertEquals(width / height, bmp.width.toFloat() / bmp.height, 0.04f)
                    File(out, "${layout.name.lowercase()}_${width.toInt()}x${height.toInt()}_${if(dark) "dark" else "light"}.png")
                        .outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
        }
        for ((width, height) in listOf(170f to 170f, 340f to 170f, 340f to 260f, 170f to 340f)) {
            for (dark in listOf(false, true)) {
                val bitmap = WidgetRenderer.render(context, WidgetDesign(layout = WidgetLayout.PROFILE), data(), avatar,
                    width, height, dark, font = NumberFont.INTER, maxPixels = 200_000)
                File(out, "profile_studio_${width.toInt()}x${height.toInt()}_${if(dark) "dark" else "light"}.png")
                    .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        val extreme = data().copy(contributions = data().contributions.copy(username = "a".repeat(39)), profile = data().profile!!.copy(name = "A very long GitHub display name that must truncate", login = "a".repeat(39)))
        WidgetRenderer.render(context, WidgetDesign(layout = WidgetLayout.PROFILE), extreme, null, 110f, 60f, false)
        WidgetRenderer.render(context, WidgetDesign(layout = WidgetLayout.WEEKLY_GOAL, weeklyGoal = 1), data(), null, 170f, 170f, true)
    }

    @Test fun renderReviewInboxWithPendingRequests() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val design = WidgetDesign(layout = WidgetLayout.PR_INBOX)
        val request = ReviewRequest("Fix search result ordering", "octocat/hello-world", "42",
            "https://github.com/octocat/hello-world/pull/42",
            "Sort exact matches first and keep results stable when two items have the same score.")
        val results = ReviewResults(3, listOf(request), 0L)
        val out = File("build/widget-renders").apply { mkdirs() }
        for (dark in listOf(false, true)) {
            val populated = WidgetRenderer.render(context, design, null, null, 340f, 170f, dark,
                inbox = ReviewInboxState("octocat", results))
            val empty = WidgetRenderer.render(context, design, null, null, 340f, 170f, dark,
                inbox = ReviewInboxState("octocat", results.copy(total = 0, requests = emptyList())))
            assertFalse(populated.sameAs(empty))
            val withoutExcerpt = WidgetRenderer.render(context, design, null, null, 340f, 170f, dark,
                inbox = ReviewInboxState("octocat", results.copy(requests = listOf(request.copy(excerpt = null)))))
            assertFalse(populated.sameAs(withoutExcerpt))
            File(out, "pr_inbox_340x170_${if (dark) "dark" else "light"}.png")
                .outputStream().use { assertTrue(populated.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        }
    }

    @Test fun reviewVariantsKeepTheirColorsAndOrderAcrossWidgetSizes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val results = ReviewResults.fromJson(JSONObject("""{
            "total_count": 3,
            "items": [
                {"html_url":"https://github.com/octocat/widgets/pull/2","title":"Second","created_at":"2026-01-02T00:00:00Z"},
                {"html_url":"https://github.com/octocat/widgets/pull/1","title":"First","created_at":"2026-01-01T00:00:00Z","user":{"login":"octocat"}},
                {"html_url":"https://github.com/octocat/widgets/pull/3","title":"Cached without a date"}
            ]
        }"""), fetchedAt = 0L)
        val inbox = ReviewInboxState("octocat", results)
        assertEquals("octocat", ReviewCardPainter.orderedRequests(inbox).first().author)
        assertEquals(listOf("1", "2", "3"), ReviewCardPainter.queueRows(inbox, 196f).map { it.number })
        assertEquals(listOf("1"), ReviewCardPainter.queueRows(inbox, 60f).map { it.number })
        assertEquals(ReviewColor.SAND, WidgetDesign.fromJson("""{"layout":"PR_INBOX"}""").reviewColor)
        val out = File("build/widget-renders").apply { mkdirs() }
        for (style in listOf(WidgetLayout.REVIEW_NEXT, WidgetLayout.REVIEW_QUEUE)) {
            val design = WidgetDesign(layout = style, reviewColor = ReviewColor.INDIGO)
            assertEquals(style, WidgetKind.forLayout(style).resolvedLayout(WidgetLayout.CLASSIC))
            assertEquals(design, WidgetDesign.fromJson(design.toJson()))
            val height = if (style == WidgetLayout.REVIEW_QUEUE) 220f else 170f
            val indigo = WidgetRenderer.render(context, design, null, null, 340f, height, true, inbox = ReviewCardPainter.example)
            val sand = WidgetRenderer.render(context, design.copy(reviewColor = ReviewColor.SAND), null, null,
                340f, height, true, inbox = ReviewCardPainter.example)
            assertFalse(indigo.sameAs(sand))
            File(out, "${style.name.lowercase()}_indigo.png").outputStream().use {
                assertTrue(indigo.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        }
    }
}
