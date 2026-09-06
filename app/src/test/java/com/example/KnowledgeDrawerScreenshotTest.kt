package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextInput
import com.example.data.Book
import com.example.data.Diary
import com.example.data.knowledge.KeywordLinks
import com.example.ui.screens.KnowledgeDrawerContent
import com.example.data.knowledge.MatchKind
import com.example.ui.screens.buildMarkdown
import com.example.data.knowledge.matchKind
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
 * 기억 서랍 화면의 스크린샷 기준(ADR-003 4단계) + 검색 근거 라벨·마크다운 순수 함수 테스트.
 * 날짜 표시가 호스트 타임존·로케일에 흔들리지 않도록 테스트 동안 기본 TimeZone=UTC, Locale=KOREA로 고정한다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class KnowledgeDrawerScreenshotTest {

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

  private val noonUtc = 1_780_000_000_000L + 12L * 3600 * 1000 // 2026-05-29T08:26:40Z. 어떤 시간대로 그려도 같은 날짜가 되도록 TZ를 UTC로 고정한다
  private val books = listOf(
    Book(id = 1, title = "데미안", author = "헤르만 헤세", totalPages = 240, bookcaseId = 1, status = "READING"),
    Book(id = 2, title = "사피엔스", author = "유발 하라리", totalPages = 630, bookcaseId = 2, status = "TO_READ")
  )
  private val diaries = listOf(
    Diary(id = 10, bookId = 1, page = 92, selectedText = "새는 알에서 나오려고 투쟁한다. 알은 세계이다.", notes = "성장에는 깨뜨림이 필요하다.", createdAt = noonUtc),
    Diary(id = 11, bookId = 2, page = 31, selectedText = "인간은 상상 속의 세계를 공유함으로써 협동한다.", notes = "", createdAt = noonUtc)
  )

  @Test
  fun knowledgeDrawer_withRecords_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme { KnowledgeDrawerContent(books = books, diaries = diaries, sharedWords = KeywordLinks.build(books, diaries), onBack = {}) }
    }
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/knowledge_drawer_records.png")
  }

  @Test
  fun knowledgeDrawer_empty_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme { KnowledgeDrawerContent(books = books, diaries = emptyList(), sharedWords = emptyList(), onBack = {}) }
    }
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/knowledge_drawer_empty.png")
  }

  @Test
  fun knowledgeDrawer_singleBook_showsHowToGetAConnection_screenshot() {
    val oneBook = diaries.filter { it.bookId == 1 }
    composeTestRule.setContent {
      MyApplicationTheme { KnowledgeDrawerContent(books = books, diaries = oneBook, sharedWords = KeywordLinks.build(books, oneBook), onBack = {}) }
    }
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/knowledge_drawer_single_book.png")
  }

  @Test
  fun knowledgeDrawer_twoBooksWithoutSharedWord_screenshot() {
    val noOverlap = listOf(
      diaries[0],
      Diary(id = 12, bookId = 2, page = 5, selectedText = "돈은 인격체다.", notes = "", createdAt = noonUtc)
    )
    val links = KeywordLinks.build(books, noOverlap)
    assertTrue(links.isEmpty())
    composeTestRule.setContent {
      MyApplicationTheme { KnowledgeDrawerContent(books = books, diaries = noOverlap, sharedWords = links, onBack = {}) }
    }
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/knowledge_drawer_two_books_no_link.png")
  }

  @Test
  fun knowledgeDrawer_searchAcrossTwoBooks_screenshot() {
    // 오너 비전(ADR-004): A 책을 떠올리며 검색했는데 B 책에서도 그때 쓴 내 메모가 나오는 화면.
    composeTestRule.setContent {
      MyApplicationTheme { KnowledgeDrawerContent(books = books, diaries = diaries, sharedWords = KeywordLinks.build(books, diaries), onBack = {}) }
    }
    composeTestRule.onNodeWithTag("knowledgedrawer_search_field").performTextInput("세계")
    composeTestRule.onNodeWithTag("knowledgedrawer_compare_header").assertIsDisplayed()
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/knowledge_drawer_cross_book_search.png")
  }

  @Test
  fun fixture_hasExactlyOneCrossBookConnection_onTheWordWorld() {
    // 화면 기준 이미지가 무엇을 보여주는지 데이터로 고정한다: "세계"만 데미안↔사피엔스에 함께 나온다.
    val links = KeywordLinks.build(books, diaries)
    assertEquals(listOf("세계"), links.map { it.word })
    assertEquals(setOf(1, 2), links[0].bookIds)
  }

  @Test
  fun matchKind_reportsWhereTheQueryMatched_orNull() {
    val d = diaries[0]
    assertNull(matchKind(d, "데미안", ""))
    assertEquals(MatchKind.TEXT, matchKind(d, "데미안", "세계"))
    assertEquals(MatchKind.NOTES, matchKind(d, "데미안", "깨뜨림"))
    assertEquals(MatchKind.TITLE, matchKind(d, "데미안", "데미"))
    assertEquals(MatchKind.TITLE_AND_TEXT, matchKind(d.copy(selectedText = "데미안을 읽다"), "데미안", "데미안"))
    assertNull(matchKind(d, "데미안", "없는말"))
  }

  @Test
  fun buildMarkdown_usesWikiLinkTitles_andNoThirdPartyAppNames() {
    val md = buildMarkdown(books, diaries)
    assertTrue(md.startsWith("# 나만의 기억 서랍"))
    assertTrue(md.contains("## [[데미안]] - p.92"))
    assertTrue(md.contains("- 나의 생각:\n  성장에는 깨뜨림이 필요하다."))
    assertFalse(md.contains("Obsidian"))
    assertFalse(md.contains("Logseq"))
  }
}
