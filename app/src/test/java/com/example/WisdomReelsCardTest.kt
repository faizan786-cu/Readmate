package com.example

import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.data.local.database.entity.WisdomQuote
import com.example.ui.screens.reels.ImmersiveMonochromeReelCard
import com.example.ui.theme.ReadMateTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WisdomReelsCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun wisdomReelsCard_rendersTopLeftBrandMarkAndFullWidthFooter() {
        val sampleQuote = WisdomQuote(
            id = 42L,
            bookId = 1L,
            bookTitle = "The Psychology of Money",
            author = "Morgan Housel",
            chapterId = 3L,
            chapterNumber = 3,
            chapterTitle = "Never Enough",
            englishQuote = "Intellectual brilliance in one's profession does not guarantee rational behavior or emotional intelligence in managing one's personal life.",
            romanUrduPunchline = "Aap professional life mein kitne hi genius kyun na hon, zaroori nahi ke aapki decision-making smart ho.",
            themeTag = "EMOTIONAL_DISCIPLINE",
            isFavorite = false
        )

        composeTestRule.setContent {
            ReadMateTheme {
                val graphicsLayer = rememberGraphicsLayer()
                ImmersiveMonochromeReelCard(
                    quote = sampleQuote,
                    graphicsLayer = graphicsLayer,
                    onTranslateWord = { _, _ -> }
                )
            }
        }

        // 1. Verify Top-Left Brand Mark & Label exists
        composeTestRule.onNodeWithTag("wisdom_card_brand_mark").assertExists()
        composeTestRule.onNodeWithText("READMATE").assertExists()

        // 2. Verify English Quote & Urdu Punchline exist
        composeTestRule.onNodeWithTag("wisdom_card_english_quote").assertExists()
        composeTestRule.onNodeWithTag("wisdom_card_urdu_punchline").assertExists()

        // 3. Verify Dedicated Footer with Author and Full Book Source
        composeTestRule.onNodeWithTag("wisdom_card_footer").assertExists()
        composeTestRule.onNodeWithTag("wisdom_card_author").assertExists()
        composeTestRule.onNodeWithText("Morgan Housel").assertExists()

        composeTestRule.onNodeWithTag("wisdom_card_book_source").assertExists()
        composeTestRule.onNodeWithText("The Psychology of Money • Ch. 3").assertExists()
    }
}
