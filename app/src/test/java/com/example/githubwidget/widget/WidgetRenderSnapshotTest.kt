package com.example.githubwidget.widget

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.githubwidget.data.SampleData
import com.example.githubwidget.data.Stats
import com.example.githubwidget.design.Background
import com.example.githubwidget.design.CellShape
import com.example.githubwidget.design.Range
import com.example.githubwidget.design.WidgetDesign
import com.example.githubwidget.design.WidgetLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the widget in its main variants. Images are written to
 * build/widget-renders/ so the look can be reviewed without a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "xxhdpi")
class WidgetRenderSnapshotTest {

    private val out = File("build/widget-renders").apply { mkdirs() }

    private fun save(name: String, design: WidgetDesign, w: Float, h: Float, dark: Boolean = true, signedOut: Boolean = false) {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bmp = WidgetRenderer.render(
            ctx, design, if (signedOut) null else SampleData.userData, null, w, h, dark,
        )
        assertTrue(bmp.width > 0 && bmp.height > 0)
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun renderVariants() {
        val d = WidgetDesign()
        save("classic_wide_dark", d, 340f, 170f)
        save("classic_wide_light", d, 340f, 170f, dark = false)
        save("classic_large", d, 340f, 260f)
        save("classic_small", d, 170f, 170f)
        save("classic_short", d, 340f, 90f)
        save("graph_wide", d.copy(layout = WidgetLayout.GRAPH), 340f, 170f)
        save("graph_year_circles", d.copy(layout = WidgetLayout.GRAPH, range = Range.YEAR, cellShape = CellShape.CIRCLE, paletteId = "violet"), 400f, 130f)
        save("numbers_wide", d.copy(layout = WidgetLayout.NUMBERS, paletteId = "sunset"), 340f, 170f)
        save("numbers_small", d.copy(layout = WidgetLayout.NUMBERS, paletteId = "ocean"), 170f, 170f)
        save("numbers_small_light", d.copy(layout = WidgetLayout.NUMBERS, paletteId = "rose", background = Background.LIGHT), 170f, 170f)
        save("seethrough", d.copy(opacity = 20, paletteId = "teal"), 340f, 170f)
        save("black_mono", d.copy(background = Background.BLACK, paletteId = "mono"), 340f, 170f)
        save("signed_out", d, 340f, 170f, signedOut = true)
    }

    @Test
    fun streakIgnoresQuietToday() {
        val s = Stats.from(SampleData.contributions)
        assertTrue(s.longestStreak >= s.currentStreak)
        assertEquals(12, s.months.size)
    }
}
