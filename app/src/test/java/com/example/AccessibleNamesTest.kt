package com.example

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Book
import com.example.data.Bookcase
import com.example.data.Diary
import kotlinx.coroutines.runBlocking
import com.example.data.ocr.OcrOutcome
import com.example.data.ocr.TextExtractor
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StatisticsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ReadingViewModel
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 화면 낭독기가 읽을 이름이 없는 누름 대상 0(2026-11-28 r8Debug 에뮬레이터 덤프에서 찾은 곳).
 * 이름 = 합쳐진 의미 노드의 글자 또는 내용 설명. 상세 «기록하기» 버튼은 BookDetailDensityTest가 본다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AccessibleNamesTest {

  @get:Rule val composeTestRule = createComposeRule()

  private object NoCallExtractor : TextExtractor {
    override suspend fun extract(bitmap: Bitmap): OcrOutcome = error("not used")
  }

  private fun vm() = ReadingViewModel(
    ApplicationProvider.getApplicationContext<Application>(), NoCallExtractor, NoCallExtractor, FakeDiaryPhotoStore()
  ) { false }

  private fun names(n: SemanticsNode): List<String> =
    n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
      n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()

  /** 누를 수 있는(클릭 · 토글) 노드 가운데 이름이 빈 것. */
  private fun unnamedTargets(): List<String> = composeTestRule.onAllNodes(
    SemanticsMatcher("clickable or toggleable") {
      SemanticsActions.OnClick in it.config || SemanticsProperties.ToggleableState in it.config
    }
  ).fetchSemanticsNodes()
    // app/src/debug의 개발 도구(OcrBoxDumpSection · 태그 debug_)는 release · r8Debug에 들어가지 않는다.
    .filterNot { it.config.getOrNull(SemanticsProperties.TestTag).orEmpty().startsWith("debug_") }
    .filter { names(it).all(String::isBlank) }
    .map { "${it.config.getOrNull(SemanticsProperties.TestTag).orEmpty()} ${it.boundsInRoot}" }

  @Test
  fun settings_geminiConsentCheckbox_hasAName() {
    composeTestRule.setContent { MyApplicationTheme { SettingsScreen(vm()) } }
    val box = composeTestRule.onNodeWithTag("gemini_consent_checkbox").fetchSemanticsNode()
    assertTrue("동의 체크박스 이름: ${names(box)}", names(box).any { it.contains("동의") })
    val unnamed = unnamedTargets()
    assertTrue("이름 없는 누름 대상: $unnamed", unnamed.isEmpty())
  }

  @Test
  fun statistics_hasNoUnnamedTargets_andTheGridReadsAsOneSummary() {
    // 지어낸 견본: 30일 전 같은 날 기록 2개 → «기록한 날 1일 · 기록 2개».
    runBlocking {
      val db = AppDatabase.getDatabase(ApplicationProvider.getApplicationContext<Application>())
      val caseId = db.bookcaseDao().insert(Bookcase(name = "견본 책장")).toInt()
      val bookId = db.bookDao().insert(Book(title = "지어낸 책", author = "지은이", totalPages = 100, bookcaseId = caseId, status = "READING", startDate = "2026-01-01 09:00")).toInt()
      val day = System.currentTimeMillis() - 30L * 86_400_000L
      db.diaryDao().insert(Diary(bookId = bookId, page = 1, selectedText = "견본 문장 하나", notes = "", createdAt = day))
      db.diaryDao().insert(Diary(bookId = bookId, page = 2, selectedText = "견본 문장 둘", notes = "", createdAt = day + 60_000L))
    }
    composeTestRule.setContent { MyApplicationTheme { StatisticsScreen(vm()) } }
    composeTestRule.waitUntil(10_000) {
      composeTestRule.onAllNodesWithTag("reading_activity_grid").fetchSemanticsNodes().firstOrNull()
        ?.let { names(it).any { n -> n.contains("기록 2개") } } == true
    }
    val unnamed = unnamedTargets()
    assertTrue("이름 없는 누름 대상 ${unnamed.size}: ${unnamed.take(3)}", unnamed.isEmpty())
    val grid = composeTestRule.onNodeWithTag("reading_activity_grid").fetchSemanticsNode()
    assertTrue("잔디 묶음 설명: ${names(grid)}", names(grid).any { it.contains("기록한 날 1일 · 기록 2개") })
  }
}
