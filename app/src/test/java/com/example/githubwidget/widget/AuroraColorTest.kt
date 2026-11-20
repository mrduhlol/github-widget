package com.example.githubwidget.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.githubwidget.data.Contributions
import com.example.githubwidget.data.Day
import com.example.githubwidget.data.UserData
import com.example.githubwidget.design.AuroraColor
import com.example.githubwidget.design.WidgetDesign
import com.example.githubwidget.design.WidgetLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AuroraColorTest {
    @Test fun savesAccentAndChangesOnlyTheActiveState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val today = LocalDate.now(ZoneOffset.UTC)
        val emerald = WidgetDesign(layout = WidgetLayout.STREAK)
        val blue = emerald.copy(auroraColor = AuroraColor.BLUE)
        assertEquals(blue, WidgetDesign.fromJson(blue.toJson()))
        assertEquals(AuroraColor.EMERALD, WidgetDesign.fromJson("""{"layout":"STREAK"}""").auroraColor)
        fun render(design: WidgetDesign, todayCount: Int, yesterdayCount: Int) =
            WidgetRenderer.render(context, design, UserData(
                Contributions("octocat", 3, listOf(
                    Day(today.minusDays(2), 1, 1),
                    Day(today.minusDays(1), yesterdayCount, yesterdayCount),
                    Day(today, todayCount, todayCount),
                )), null, System.currentTimeMillis(),
            ), null, 340f, 170f, dark = true)

        assertFalse(render(emerald, 1, 1).sameAs(render(blue, 1, 1)))
        assertTrue(render(emerald, 0, 1).sameAs(render(blue, 0, 1)))
        assertTrue(render(emerald, 0, 0).sameAs(render(blue, 0, 0)))
    }
}
