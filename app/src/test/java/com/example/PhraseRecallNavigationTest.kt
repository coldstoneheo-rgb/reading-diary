package com.example

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.data.knowledge.RecallFilter
import com.example.data.ocr.OcrOutcome
import com.example.data.ocr.TextExtractor
import com.example.ui.viewmodel.ReadingViewModel
import com.example.ui.viewmodel.Screen
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 문구 회상 내비게이션(PHRASE-RECALL-SPEC P1): 기억 서랍 검색어를 이어받고,
 * «이 책 보기»에서 뒤로 오면 떠날 때의 검색어·필터가 그대로 돌아온다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhraseRecallNavigationTest {

  private val app: Application get() = ApplicationProvider.getApplicationContext()

  private object NoCallExtractor : TextExtractor {
    override suspend fun extract(bitmap: Bitmap): OcrOutcome = error("not used")
  }

  // DB는 건드리지 않는다. 테스트 끝에 DB를 닫으면 VM 초기화 코루틴(IO)이 닫힌 DB를 다시 열려다 예외를 내고,
  // 그 예외가 다음 테스트 클래스의 runTest에 «UncaughtExceptionsBeforeTest»로 새어 나간다(OcrDiaryEditSaveTest와 같은 방식).

  private fun vm() = ReadingViewModel(app, NoCallExtractor, NoCallExtractor) { false }

  @Test
  fun openPhraseRecall_carriesTrimmedQueryFromKnowledgeDrawer() {
    val vm = vm()
    vm.navigateTo(Screen.KnowledgeDrawer)
    vm.openPhraseRecall("  세계 ")
    assertEquals(Screen.PhraseRecall("세계"), vm.currentScreenState.value)
    vm.navigateBack()
    assertEquals(Screen.KnowledgeDrawer, vm.currentScreenState.value)
  }

  @Test
  fun openBook_thenBack_restoresQueryAndFilter() {
    val vm = vm()
    vm.openPhraseRecall()
    val filter = RecallFilter(bookIds = setOf(1), fromMillis = 1_000L, toMillis = 2_000L, pageRange = 10..50)
    vm.openBookFromPhraseRecall(bookId = 1, query = "그리움", filter = filter)
    assertEquals(Screen.BookDetail(1), vm.currentScreenState.value)
    vm.navigateBack()
    assertEquals(Screen.PhraseRecall("그리움", filter), vm.currentScreenState.value)
    vm.navigateBack()
    assertEquals(Screen.Dashboard, vm.currentScreenState.value)
  }
}
