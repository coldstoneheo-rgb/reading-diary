package com.example

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.example.data.Book
import com.example.data.Diary
import com.example.data.knowledge.KeywordLinks
import com.example.data.knowledge.RecallFilter
import com.example.ui.screens.PhraseRecallContent
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale
import java.util.TimeZone

/**
 * 문구 회상 화면 스크린샷 기준(PHRASE-RECALL-SPEC §3.2 P1). [KnowledgeDrawerScreenshotTest]와 같은 고정(UTC·KOREA, Pixel8, sdk 36).
 * 검색 상태는 initialQuery로 그린다 — performTextInput은 포커스·캐럿을 남겨 기준 이미지가 흔들린다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class PhraseRecallScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var savedTimeZone: TimeZone
  private lateinit var savedLocale: Locale

  @Before
  fun pinTimeZoneAndLocale() {
    savedTimeZone = TimeZone.getDefault(); savedLocale = Locale.getDefault()
    TimeZone.setDefault(TimeZone.getTimeZone("UTC")); Locale.setDefault(Locale.KOREA)
  }

  @After
  fun restoreTimeZoneAndLocale() {
    TimeZone.setDefault(savedTimeZone); Locale.setDefault(savedLocale)
  }

  private val day = 86_400_000L
  private val noonUtc = 1_780_000_000_000L + 12L * 3600 * 1000
  private val books = listOf(
    Book(id = 1, title = "데미안", author = "헤르만 헤세", totalPages = 240, bookcaseId = 1, status = "READING"),
    Book(id = 2, title = "사피엔스", author = "유발 하라리", totalPages = 630, bookcaseId = 2, status = "TO_READ")
  )
  private val diaries = listOf(
    Diary(id = 10, bookId = 1, page = 92, selectedText = "새는 알에서 나오려고 투쟁한다. 알은 세계이다. 태어나려는 자는 하나의 세계를 깨뜨려야 한다.", notes = "성장에는 깨뜨림이 필요하다.", createdAt = noonUtc),
    Diary(id = 11, bookId = 2, page = 31, selectedText = "인간은 상상 속의 세계를 공유함으로써 협동한다.", notes = "", createdAt = noonUtc - 20 * day),
    Diary(id = 12, bookId = 1, page = 15, selectedText = "나는 내 속에서 저절로 우러나오는 것, 바로 그것을 살아보려 했다.", notes = "세계보다 나", createdAt = noonUtc - 40 * day)
  )

  @Test
  fun phraseRecall_results_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme {
        PhraseRecallContent(
          books = books, diaries = diaries, sharedWords = KeywordLinks.build(books, diaries),
          onOpenBook = {}, onBack = {}, initialQuery = "세계"
        )
      }
    }
    composeTestRule.onNodeWithTag("phraserecall_search_field").assertIsDisplayed()
    composeTestRule.onNodeWithTag("phraserecall_filter_book").assertIsDisplayed()
    composeTestRule.onNodeWithTag("phraserecall_filter_period").assertIsDisplayed()
    composeTestRule.onAllNodesWithTag("phraserecall_filter_page").assertCountEquals(0) // 책 1권을 고를 때만
    composeTestRule.onAllNodesWithTag("phraserecall_result").assertCountEquals(3)
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/phrase_recall_results.png")
  }

  @Test
  fun phraseRecall_empty_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme {
        PhraseRecallContent(
          books = books, diaries = diaries, sharedWords = KeywordLinks.build(books, diaries),
          onOpenBook = {}, onBack = {}, initialQuery = "고양이"
        )
      }
    }
    composeTestRule.onAllNodesWithTag("phraserecall_result").assertCountEquals(0)
    composeTestRule.onNodeWithTag("phraserecall_empty").assertIsDisplayed()
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/phrase_recall_empty.png")
  }

  @Test
  fun pageFilter_appearsOnlyWithExactlyOneBook() {
    composeTestRule.setContent {
      MyApplicationTheme {
        PhraseRecallContent(
          books = books, diaries = diaries, sharedWords = emptyList(),
          onOpenBook = {}, onBack = {}, initialFilter = RecallFilter(bookIds = setOf(1), pageRange = 10..50)
        )
      }
    }
    composeTestRule.onNodeWithTag("phraserecall_filter_page").assertIsDisplayed()
    composeTestRule.onAllNodesWithTag("phraserecall_result").assertCountEquals(1) // 데미안 p.15만
  }
}
