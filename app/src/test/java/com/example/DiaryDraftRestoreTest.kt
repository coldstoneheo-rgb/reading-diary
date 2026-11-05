package com.example

import android.app.Application
import android.os.Bundle
import android.os.Looper
import android.os.Parcel
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Book
import com.example.data.Bookcase
import com.example.ui.viewmodel.ReadingViewModel
import com.example.ui.viewmodel.Screen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * 화면 복원이 저장된 초안을 되살려 기록이 2건이 되는 경로(R4 재게이트).
 * 저장 번들을 찍는 시점을 고정하려고 전역 사진 뮤텍스를 잡아 저장을 커밋 앞에 세운다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DiaryDraftRestoreTest {

  @get:Rule val composeTestRule = createEmptyComposeRule()

  private val app: Application get() = ApplicationProvider.getApplicationContext()
  private val db: AppDatabase get() = AppDatabase.getDatabase(app)
  private var bookId = 0
  private val openActivities = mutableListOf<ActivityController<MainActivity>>()
  private var heldMutex: Mutex? = null

  @Before
  fun setUp() = runBlocking {
    val caseId = db.bookcaseDao().insert(Bookcase(name = "초안 테스트")).toInt()
    bookId = db.bookDao().insert(Book(title = "초안 테스트 책", author = "", totalPages = 100, bookcaseId = caseId, status = "READING")).toInt()
  }

  @After
  fun tearDown() {
    heldMutex?.unlock()
    composeTestRule.mainClock.autoAdvance = true
    openActivities.forEach { it.pause().stop().destroy() }
    shadowOf(Looper.getMainLooper()).idle()
  }

  private fun vm(controller: ActivityController<MainActivity>) = ViewModelProvider(controller.get())[ReadingViewModel::class.java]
  private fun start() = Robolectric.buildActivity(MainActivity::class.java).setup().also { openActivities += it }
  private fun records() = runBlocking { db.diaryDao().getAllDiaries().first() }.filter { it.bookId == bookId }
  private fun tagShown(tag: String) = composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
  private fun textShown(text: String) = composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

  private fun pump(condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + 10_000
    while (!condition() && System.currentTimeMillis() < deadline) {
      shadowOf(Looper.getMainLooper()).idle()
      Thread.sleep(20)
    }
    assertTrue("condition not met within 10s", condition())
  }

  private fun settle(condition: () -> Boolean) {
    composeTestRule.waitUntil(10_000) {
      shadowOf(Looper.getMainLooper()).idle()
      condition()
    }
    composeTestRule.waitForIdle()
  }

  private fun openFilledForm(controller: ActivityController<MainActivity>, text: String) {
    val model = vm(controller)
    model.navigateTo(Screen.BookDetail(bookId))
    model.navigateTo(Screen.AddDiary(bookId))
    settle { tagShown("diary_page_input") }
    composeTestRule.onNodeWithTag("diary_page_input").performScrollTo().performTextReplacement("7")
    composeTestRule.onNodeWithTag("diary_extracted_text_input").performScrollTo().performTextReplacement(text)
    composeTestRule.waitForIdle()
  }

  private fun clickSave() =
    composeTestRule.onNodeWithTag("save_diary_submit_button").performScrollTo().performClick()

  /** 저장이 커밋 앞에서 기다리게 한다. private이라 리플렉션으로 잡는다. */
  private fun holdSaves() {
    val field = ReadingViewModel::class.java.getDeclaredField("photoIoMutex").apply { isAccessible = true }
    val mutex = field.get(null) as Mutex
    val deadline = System.currentTimeMillis() + 10_000
    while (!mutex.tryLock()) {
      assertTrue("사진 뮤텍스를 잡지 못했다", System.currentTimeMillis() < deadline)
      shadowOf(Looper.getMainLooper()).idle()
      Thread.sleep(20)
    }
    heldMutex = mutex
  }

  private fun releaseSaves() {
    heldMutex?.unlock()
    heldMutex = null
  }

  private fun capture(controller: ActivityController<MainActivity>): Bundle {
    val saved = Bundle()
    controller.pause().stop().saveInstanceState(saved)
    val parcel = Parcel.obtain()
    try {
      parcel.writeBundle(saved)
      parcel.setDataPosition(0)
      return parcel.readBundle(javaClass.classLoader)!!
    } finally {
      parcel.recycle()
    }
  }

  private fun destroy(controller: ActivityController<MainActivity>) {
    controller.destroy()
    openActivities -= controller
    shadowOf(Looper.getMainLooper()).idle()
  }

  private fun restore(bundle: Bundle) = Robolectric.buildActivity(MainActivity::class.java).setup(bundle).also {
    openActivities += it
    shadowOf(Looper.getMainLooper()).idle()
  }

  /** ⓐ 저장 중에 홈으로 나가 번들이 찍히고, 저장이 끝난 뒤 프로세스가 죽는다. */
  @Test
  fun bundleTakenWhileSaving_restoredAfterCommit_doesNotCreateSecondRecord() {
    val first = start()
    openFilledForm(first, "한 번만 저장될 문장")
    holdSaves()
    clickSave()

    val bundle = capture(first)
    releaseSaves()
    pump { records().size == 1 }
    pump { vm(first).currentScreenState.value == Screen.BookDetail(bookId) }
    destroy(first)

    val second = restore(bundle)
    // 수리 전에는 초안이 되살아난다. 그 상태라면 사용자가 다시 저장한다.
    composeTestRule.waitForIdle()
    pump { vm(second).currentScreenState.value != Screen.AddDiary(bookId) || tagShown("save_diary_submit_button") }
    pump {
      shadowOf(Looper.getMainLooper()).idle()
      vm(second).currentScreenState.value == Screen.BookDetail(bookId) || textShown("한 번만 저장될 문장")
    }
    if (tagShown("save_diary_submit_button") && vm(second).currentScreenState.value == Screen.AddDiary(bookId)) {
      clickSave()
      composeTestRule.waitForIdle()
      pump { !vm(second).diarySaving.value }
    }

    assertEquals("기록은 1건이어야 한다", 1, records().size)
    assertEquals(Screen.BookDetail(bookId), vm(second).currentScreenState.value)
  }

  /** ⓑ 저장이 끝나 나가는 전환 중에 번들이 찍힌다. 위치는 BookDetail, 폼 값은 번들에 들어간다. */
  @Test
  fun bundleTakenDuringExitTransition_nextNewRecordStartsEmpty() {
    val first = start()
    openFilledForm(first, "이미 저장한 문장")
    // 스크롤은 멈춘 시계에서 끝나지 않는다. 저장을 세워 둔 채 누른 뒤 시계를 멈추고 놓아 준다.
    holdSaves()
    clickSave()
    composeTestRule.mainClock.autoAdvance = false
    releaseSaves()
    pump { records().size == 1 && vm(first).currentScreenState.value == Screen.BookDetail(bookId) }
    composeTestRule.mainClock.advanceTimeByFrame()
    composeTestRule.mainClock.advanceTimeByFrame()
    shadowOf(Looper.getMainLooper()).idle()
    assertTrue("전제: 나가는 기록 화면이 아직 구성에 있다", tagShown("save_diary_submit_button"))

    val bundle = capture(first)
    composeTestRule.mainClock.autoAdvance = true
    destroy(first)

    val second = restore(bundle)
    assertEquals(Screen.BookDetail(bookId), vm(second).currentScreenState.value)
    vm(second).navigateTo(Screen.AddDiary(bookId))
    settle { tagShown("diary_extracted_text_input") }

    assertFalse("옛 초안이 새 기록에 들어오면 안 된다", textShown("이미 저장한 문장"))
  }

  /** 커밋 전에 프로세스가 죽으면 기록이 없으니 초안을 잃지 않는다. */
  @Test
  fun processDiesBeforeCommit_keepsDraft_andSavesOnce() {
    val first = start()
    val openedFrom = System.currentTimeMillis()
    openFilledForm(first, "아직 저장 안 된 문장")
    val openedUntil = System.currentTimeMillis()
    Thread.sleep(50) // 저장 시점에 시각을 새로 찍으면 아래 범위를 벗어난다
    holdSaves()
    clickSave()

    val bundle = capture(first)
    destroy(first)
    releaseSaves()
    assertTrue(records().isEmpty())

    val second = restore(bundle)
    settle { textShown("아직 저장 안 된 문장") }
    assertEquals(Screen.AddDiary(bookId), vm(second).currentScreenState.value)

    clickSave()
    pump { records().size == 1 }
    pump { vm(second).currentScreenState.value == Screen.BookDetail(bookId) }
    assertEquals(1, records().size)
    // 복원 판정은 책 + 작성 시각의 정확 일치다. 작성 시각은 저장·복원 때가 아니라 폼을 처음 연 때의 값이어야 한다.
    assertTrue("작성 시각이 초안의 값이어야 한다", records().single().createdAt in openedFrom..openedUntil)
  }
}
