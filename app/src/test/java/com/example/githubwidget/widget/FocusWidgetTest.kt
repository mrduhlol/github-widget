package com.example.githubwidget.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.githubwidget.data.*
import com.example.githubwidget.design.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FocusWidgetTest {
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
        assertEquals(WidgetLayout.CLASSIC, WidgetKind.CONTRIBUTIONS.resolvedLayout(WidgetLayout.PROFILE))
        assertEquals(WidgetLayout.GRAPH, WidgetKind.CONTRIBUTIONS.resolvedLayout(WidgetLayout.GRAPH))
        assertEquals(WidgetLayout.GRAPH, WidgetKind.CONTRIBUTIONS.resolvedLayout(WidgetLayout.PROFILE, WidgetLayout.GRAPH))
    }

    @Test fun settingsSurviveRoundTripAndOldInstalls() {
        val design = WidgetDesign(layout = WidgetLayout.PROFILE, weeklyGoal = 50,
            profileShowName = false, profileShowAvatar = false, profileShowFollowers = false,
            profileShowFollowing = false, profileShowGitHub = false, profileShowWebsite = false,
            profileShowSocials = false, profileShowHandle = false, profileShowRepos = false,
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
        for ((width, height) in listOf(340f to 170f, 170f to 340f)) {
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
}
