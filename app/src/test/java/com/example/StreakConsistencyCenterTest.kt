package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.local.database.entity.DailyRetentionStateEntity
import com.example.data.local.database.entity.RetentionStreakStatus
import com.example.ui.components.StreakConsistencyContent
import com.example.ui.theme.ReadMateTheme
import com.example.ui.util.StreakInfo
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StreakConsistencyCenterTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun streakConsistencyContent_rendersHeroBannerAndCalendarAndStats() {
        var closeClicked = false
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val nowMillis = today.atTime(12, 0).atZone(zoneId).toInstant().toEpochMilli()

        val sampleTimestamps = listOf(
            nowMillis,
            nowMillis + 1000,
            nowMillis + 2000
        )

        composeTestRule.setContent {
            ReadMateTheme {
                StreakConsistencyContent(
                    streakInfo = StreakInfo(
                        activeStreakCount = 7,
                        todaySnipsCount = 3,
                        isTodayValid = true,
                        requiredSnipsPerDay = 3
                    ),
                    readingTimestamps = sampleTimestamps,
                    dailyRetentionState = DailyRetentionStateEntity(
                        date = today.toString(),
                        quizzesCompleted = true,
                        streakStatus = RetentionStreakStatus.ACTIVE,
                        streakDays = 7
                    ),
                    allRetentionStates = emptyList(),
                    onClose = { closeClicked = true }
                )
            }
        }

        // Verify Title and Close button
        composeTestRule.onNodeWithText("CONSISTENCY CENTER").assertIsDisplayed()
        composeTestRule.onNodeWithTag("close_streak_sheet_button").assertIsDisplayed()

        // Verify Hero Streak Banner
        composeTestRule.onNodeWithTag("hero_streak_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hero_streak_counter").assertIsDisplayed()
        composeTestRule.onNodeWithText("DAY STREAK").assertIsDisplayed()
        composeTestRule.onNodeWithText("One full week of relentless focus.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hero_freeze_status").assertIsDisplayed()

        // Verify Calendar Card and Weekdays
        composeTestRule.onNodeWithTag("streak_calendar_card").assertExists()
        composeTestRule.onNodeWithText("SU").assertExists()
        composeTestRule.onNodeWithText("MO").assertExists()
        composeTestRule.onNodeWithText("TU").assertExists()
        composeTestRule.onNodeWithText("WE").assertExists()
        composeTestRule.onNodeWithText("TH").assertExists()
        composeTestRule.onNodeWithText("FR").assertExists()
        composeTestRule.onNodeWithText("SA").assertExists()

        // Verify Performance Stats
        composeTestRule.onNodeWithTag("performance_stats_pod").assertExists()
        composeTestRule.onNodeWithText("LONGEST STREAK").assertExists()
        composeTestRule.onNodeWithText("TOTAL SESSIONS").assertExists()
        composeTestRule.onNodeWithText("CONSISTENCY").assertExists()

        // Test month navigation
        composeTestRule.onNodeWithTag("prev_month_button").performClick()
        composeTestRule.onNodeWithTag("next_month_button").performClick()

        // Test close action
        composeTestRule.onNodeWithTag("close_streak_sheet_button").performClick()
        assertTrue(closeClicked)
    }
}
