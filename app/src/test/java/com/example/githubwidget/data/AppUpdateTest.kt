package com.example.githubwidget.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {

    @Test
    fun comparesSemanticVersionsCorrectly() {
        assertTrue(AppUpdate.isNewer("v3.9", "3.8"))
        assertTrue(AppUpdate.isNewer("3.8.1", "3.8"))
        assertTrue(AppUpdate.isNewer("v4.0", "3.9.9"))
        assertFalse(AppUpdate.isNewer("v3.8", "3.8"))
        assertFalse(AppUpdate.isNewer("3.7.9", "3.8"))
    }
}
