package com.example.githubwidget.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/** Guards size normalization against bogus launcher reports that stretch the drawing. */
class WidgetSizeClampTest {

    @Test
    fun `sizes within the display pass through`() {
        assertEquals(320f to 176f, WidgetUpdater.normalizeSizeToDisplay(320f, 176f, 360f, 800f, 2f))
    }

    @Test
    fun `pixel reports are converted back to dp`() {
        // Seen on a 2x-density phone: the live 320dp-wide widget reported as 640.
        assertEquals(320f to 176f, WidgetUpdater.normalizeSizeToDisplay(640f, 176f, 360f, 800f, 2f))
    }

    @Test
    fun `a size right at the display edge is kept`() {
        assertEquals(360f to 800f, WidgetUpdater.normalizeSizeToDisplay(360f, 800f, 360f, 800f, 2f))
    }

    @Test
    fun `degenerate values fall back to 1dp`() {
        assertEquals(1f to 1f, WidgetUpdater.normalizeSizeToDisplay(0f, -5f, 360f, 800f, 2f))
        assertEquals(1f to 1f, WidgetUpdater.normalizeSizeToDisplay(10f, 10f, 0f, 0f, 2f))
    }
}
