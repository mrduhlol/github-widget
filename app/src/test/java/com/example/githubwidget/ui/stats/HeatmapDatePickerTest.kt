package com.example.githubwidget.ui.stats

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.githubwidget.data.Day
import com.example.githubwidget.ui.theme.GhTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w400dp-h800dp")
class HeatmapDatePickerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun showsContributionCountForChosenDayAndKeepsItOnCancel() {
        val days = listOf(Day(LocalDate.of(2026, 5, 14), 7, 3), Day(LocalDate.of(2026, 5, 15), 0, 0))
        compose.setContent {
            GhTheme { YearHeatmap(days, List(5) { Color.Green }, 0.2f, weekStartsMonday = true) }
        }
        compose.onNodeWithText("Find date").performClick()
        compose.onNodeWithText("Thursday, May 14, 2026", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Show day").performClick()
        compose.onNodeWithText("7 contributions", substring = true).assertExists()
        compose.onNodeWithText("Find date").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("7 contributions", substring = true).assertExists()
    }
}
