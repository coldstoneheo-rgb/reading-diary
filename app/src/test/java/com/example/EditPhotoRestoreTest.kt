package com.example

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.os.Parcel
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor as CoilInterceptor
import coil.request.SuccessResult
import com.example.data.AppDatabase
import com.example.data.Book
import com.example.data.Bookcase
import com.example.data.Diary
import com.example.data.DiaryPhoto
import com.example.data.photo.InternalDiaryPhotoStore
import com.example.ui.viewmodel.ReadingViewModel
import com.example.ui.viewmodel.Screen
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 편집 사진 초안이 번들에 남은 채 커밋되고 프로세스가 죽은 뒤 복원되는 경로(TASK-2026-1106 ①).
 * 복원된 재저장이 이미 커밋된 사진을 지우면 안 된다. 실제 저장소·실제 DB·실제 파일을 쓰고,
 * «원본 읽기 실패»는 원본 파일을 지워 만든다(촬영 임시 파일 삭제·갤러리 권한 소멸에 해당).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class EditPhotoRestoreTest {

  @get:Rule val composeTestRule = createEmptyComposeRule()

  private val app: Application get() = ApplicationProvider.getApplicationContext()
  private val db: AppDatabase get() = AppDatabase.getDatabase(app)
  // 앱이 쓰는 폴더. 기본 ViewModel 팩토리는 정적이라 ViewModel의 Application이 이 테스트의 것과 다를 수 있어 ViewModel 기준으로 잡는다.
  private lateinit var store: InternalDiaryPhotoStore
  private val openActivities = mutableListOf<ActivityController<MainActivity>>()
  private var heldMutex: Mutex? = null
  private var bookId = 0
  private var diaryId = 0
  private lateinit var source: File

  @Before
  fun setUp() = runBlocking {
    val caseId = db.bookcaseDao().insert(Bookcase(name = "편집 복원 테스트")).toInt()
    bookId = db.bookDao().insert(Book(title = "편집 복원 책", author = "", totalPages = 100, bookcaseId = caseId, status = "READING")).toInt()
    diaryId = db.diaryDao().insert(Diary(bookId = bookId, page = 7, selectedText = "처음 문장", notes = "")).toInt()
    db.diaryPhotoDao().insertAll(listOf(DiaryPhoto(diaryId = diaryId, path = "kept.jpg", sortOrder = 0)))
    source = File(app.cacheDir, "picked_${System.nanoTime()}.png")
    source.outputStream().use { out ->
      Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    // 전역 ImageLoader가 앞 테스트의 메인 루퍼에 묶이지 않게 디코드 없이 단색을 돌려준다.
    Coil.setImageLoader(
      ImageLoader.Builder(app).allowHardware(false).components {
        add(CoilInterceptor { chain ->
          SuccessResult(drawable = ColorDrawable(Color.GRAY), request = chain.request, dataSource = DataSource.MEMORY)
        })
      }.build()
    )
  }

  @After
  fun tearDown() {
    heldMutex?.unlock()
    openActivities.forEach { it.pause().stop().destroy() }
    shadowOf(Looper.getMainLooper()).idle()
    Coil.reset()
    source.delete()
    if (::store.isInitialized) store.root.deleteRecursively()
  }

  private fun vm(controller: ActivityController<MainActivity>) = ViewModelProvider(controller.get())[ReadingViewModel::class.java]
  private fun rows() = runBlocking { db.diaryPhotoDao().photosFor(diaryId) }
  private fun tagShown(tag: String) = composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

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

  private fun openEdit(): ActivityController<MainActivity> {
    val controller = Robolectric.buildActivity(MainActivity::class.java).setup().also { openActivities += it }
    store = InternalDiaryPhotoStore(vm(controller).getApplication<Application>())
    store.root.mkdirs()
    File(store.root, "kept.jpg").writeBytes(byteArrayOf(1))
    vm(controller).navigateTo(Screen.BookDetail(bookId))
    vm(controller).navigateTo(Screen.AddDiary(bookId, diaryId))
    settle { tagShown("diary_photo_add") && tagShown("diary_photo_0") }
    return controller
  }

  /** 갤러리가 [source]를 골라 돌려준다. */
  private fun deliverGalleryResult(controller: ActivityController<MainActivity>) {
    composeTestRule.waitForIdle()
    shadowOf(Looper.getMainLooper()).idle()
    val started = shadowOf(controller.get()).nextStartedActivityForResult ?: error("갤러리가 뜨지 않았다")
    assertTrue(
      controller.get().activityResultRegistry.dispatchResult(started.requestCode, Activity.RESULT_OK, Intent().setData(Uri.fromFile(source)))
    )
    composeTestRule.waitForIdle()
  }

  private fun addPhoto(controller: ActivityController<MainActivity>) {
    composeTestRule.onNodeWithTag("diary_photo_add").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("diary_photo_from_gallery").performClick()
    deliverGalleryResult(controller)
    settle { tagShown("diary_photo_1") }
  }

  private fun replaceFirstPhoto(controller: ActivityController<MainActivity>) {
    composeTestRule.onNodeWithTag("diary_photo_add").performScrollTo()
    composeTestRule.onNodeWithTag("diary_photo_replace_0").performClick()
    composeTestRule.onNodeWithTag("diary_photo_from_gallery").performClick()
    deliverGalleryResult(controller)
  }

  /** 사진 칸이 아니라 화면 위쪽 촬영·선택 영역에 띄운 사진. 저장 때 함께 보관된다. */
  private fun showPhotoOnScreen(controller: ActivityController<MainActivity>) {
    composeTestRule.onAllNodesWithText("사진 선택").onFirst().performScrollTo().performClick()
    deliverGalleryResult(controller)
  }

  private fun clickSave() =
    composeTestRule.onNodeWithTag("save_diary_submit_button").performScrollTo().performClick()

  /** 저장이 커밋 앞에서 기다리게 한다. private이라 리플렉션으로 잡는다. */
  private fun holdSaves() {
    val mutex = ReadingViewModel::class.java.getDeclaredField("photoIoMutex").apply { isAccessible = true }.get(null) as Mutex
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

  /** 저장 중 홈 이탈로 커밋 전 번들이 찍힌다 → 백그라운드 커밋 → 프로세스 사망. 반환 = 복원에 쓸 번들. */
  private fun saveLeaveCommitDie(controller: ActivityController<MainActivity>, committedRows: Int): Bundle {
    holdSaves()
    clickSave()
    val bundle = capture(controller)
    releaseSaves()
    pump { !vm(controller).diarySaving.value && rows().size == committedRows }
    destroy(controller)
    return bundle
  }

  /** 복원된 폼에서 사용자가 그대로 다시 저장한다. */
  private fun restoreAndSaveAgain(bundle: Bundle, beforeSave: () -> Unit = {}) {
    val second = Robolectric.buildActivity(MainActivity::class.java).setup(bundle).also { openActivities += it }
    shadowOf(Looper.getMainLooper()).idle()
    settle { tagShown("diary_photo_add") }
    assertEquals(Screen.AddDiary(bookId, diaryId), vm(second).currentScreenState.value)
    beforeSave()
    clickSave()
    pump { vm(second).currentScreenState.value == Screen.BookDetail(bookId) }
    pump { !vm(second).diarySaving.value }
  }

  private fun assertCommittedSurvived(committed: List<DiaryPhoto>) {
    assertEquals("커밋된 사진 행이 그대로여야 한다", committed, rows())
    committed.forEach { assertNotNull("커밋된 파일이 남아야 한다: ${it.path}", store.fileFor(it.path)) }
  }

  @Test
  fun addedPhoto_committedThenRestored_sourceUnreadable_keepsCommittedPhoto() {
    val first = openEdit()
    addPhoto(first)
    val bundle = saveLeaveCommitDie(first, committedRows = 2)
    val committed = rows()
    source.delete()

    restoreAndSaveAgain(bundle)

    assertCommittedSurvived(committed)
  }

  @Test
  fun replacedPhoto_committedThenRestored_sourceUnreadable_keepsCommittedPhoto() {
    val first = openEdit()
    replaceFirstPhoto(first)
    holdSaves()
    clickSave()
    val bundle = capture(first)
    releaseSaves()
    pump { !vm(first).diarySaving.value && rows().singleOrNull()?.path != "kept.jpg" }
    destroy(first)
    val committed = rows()
    assertEquals(1, committed.size)
    source.delete()

    restoreAndSaveAgain(bundle)

    assertCommittedSurvived(committed)
  }

  /** LC 추적 밖에서 찾은 같은 모양의 경로: 화면에 띄운 사진도 재저장 때 다시 새 사진으로 들어간다. */
  @Test
  fun shownPhoto_committedThenRestored_sourceUnreadable_keepsCommittedPhoto() {
    val first = openEdit()
    showPhotoOnScreen(first)
    val bundle = saveLeaveCommitDie(first, committedRows = 2)
    val committed = rows()
    source.delete()

    restoreAndSaveAgain(bundle)

    assertCommittedSurvived(committed)
  }

  /** 원본을 읽을 수 있어도 커밋본을 지우고 같은 사진을 다시 쓰지 않는다. */
  @Test
  fun addedPhoto_committedThenRestored_sourceReadable_doesNotRewritePhoto() {
    val first = openEdit()
    addPhoto(first)
    val bundle = saveLeaveCommitDie(first, committedRows = 2)
    val committed = rows()

    restoreAndSaveAgain(bundle)

    assertCommittedSurvived(committed)
  }

  /** 화면의 사진도 복원 뒤 다시 들어가 같은 사진이 또 쓰이면 안 된다. */
  @Test
  fun shownPhoto_committedThenRestored_sourceReadable_doesNotRewritePhoto() {
    val first = openEdit()
    showPhotoOnScreen(first)
    val bundle = saveLeaveCommitDie(first, committedRows = 2)
    val committed = rows()

    restoreAndSaveAgain(bundle)

    assertCommittedSurvived(committed)
  }

  /** 재동기화가 지나치면 커밋 전 사망의 초안을 버린다. DB가 기준 그대로면 초안을 지켜 저장한다. */
  @Test
  fun addedPhoto_processDiesBeforeCommit_keepsDraft_andSavesIt() {
    val first = openEdit()
    addPhoto(first)
    holdSaves()
    clickSave()
    val bundle = capture(first)
    destroy(first)
    releaseSaves()
    assertEquals("커밋 전이라 행은 그대로다", listOf("kept.jpg"), rows().map { it.path })

    restoreAndSaveAgain(bundle) {
      assertTrue("초안 슬롯이 그대로 복원돼야 한다", tagShown("diary_photo_0") && tagShown("diary_photo_1"))
    }

    assertEquals("초안의 새 사진이 저장돼야 한다", 2, rows().size)
    assertEquals("kept.jpg", rows().first().path)
    rows().forEach { assertNotNull("파일이 있어야 한다: ${it.path} / 폴더: ${store.root.list()?.toList()}", store.fileFor(it.path)) }
  }
}
