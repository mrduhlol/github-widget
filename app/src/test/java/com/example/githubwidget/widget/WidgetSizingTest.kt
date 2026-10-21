package com.example.githubwidget.widget

import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.util.SizeF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetSizingTest {
    private fun bounds() = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 540)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 90)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 180)
    }

    @Test
    fun `legacy portrait uses portrait width and height instead of unrelated maxima`() {
        assertEquals(SizeF(300f, 180f), WidgetUpdater.legacySize(bounds(), landscape = false))
    }

    @Test
    fun `legacy landscape uses landscape width and height`() {
        assertEquals(SizeF(540f, 90f), WidgetUpdater.legacySize(bounds(), landscape = true))
    }

    @Test
    fun `exact sizes retain every host aspect ratio regardless of list order`() {
        val expected = listOf(SizeF(540f, 90f), SizeF(300f, 180f), SizeF(460f, 320f))
        val options = bounds().apply {
            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, ArrayList(expected))
        }
        assertEquals(expected, WidgetUpdater.exactSizes(options))
    }

    @Test
    fun `duplicate and empty exact sizes do not create unnecessary bitmap variants`() {
        val options = bounds().apply {
            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(
                SizeF(300f, 180f), SizeF(0f, 180f), SizeF(300f, 180f),
            ))
        }
        assertEquals(listOf(SizeF(300f, 180f)), WidgetUpdater.exactSizes(options))
        assertTrue(WidgetUpdater.exactSizes(Bundle()).isEmpty())
    }

    @Test
    fun `missing orientation bounds fall back to the available size`() {
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 170)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 90)
        }
        assertEquals(SizeF(170f, 90f), WidgetUpdater.legacySize(options, false))
        assertEquals(SizeF(170f, 90f), WidgetUpdater.legacySize(options, true))
    }
    @Test
    fun `pixel width correction is preserved for portrait launcher reports`() {
        assertEquals(SizeF(320f, 176f), WidgetUpdater.normalizeHostSize(
            SizeF(640f, 176f), 360f, 800f, 2f, landscape = false,
        ))
    }

    @Test
    fun `landscape width is never capped to the current portrait display width`() {
        assertEquals(SizeF(640f, 176f), WidgetUpdater.normalizeHostSize(
            SizeF(640f, 176f), 360f, 800f, 2f, landscape = true,
        ))
    }

    @Test
    fun `unmatched exact host sizes can use either display orientation`() {
        assertEquals(SizeF(640f, 176f), WidgetUpdater.normalizeHostSize(
            SizeF(640f, 176f), 360f, 800f, 2f, landscape = null,
        ))
    }

    @Test
    fun `portrait width is valid even when app currently opens in landscape`() {
        assertEquals(SizeF(320f, 700f), WidgetUpdater.normalizeHostSize(
            SizeF(320f, 700f), 800f, 360f, 2f, landscape = false,
        ))
    }

}
