package com.example

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Book
import com.example.data.Bookcase
import com.example.data.Diary
import com.example.data.DiaryPhoto
import com.example.data.photo.CameraTempFiles
import com.example.data.photo.RecallPhoto
import com.example.data.ocr.OcrOutcome
import com.example.data.ocr.TextExtractor
import com.example.ui.viewmodel.PhotoInput
import com.example.ui.viewmodel.PhotoInputSlot
import com.example.ui.viewmodel.ReadingViewModel
import com.example.ui.viewmodel.Screen
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * 명세 §3.5 ViewModel 저장 경로. AppDatabase는 프로세스 싱글턴이라 닫지 않고, 테스트마다 새 책을 만들어 그 id로만 본다.
 * (DB를 닫으면 VM 초기화 코루틴 예외가 다음 테스트 클래스로 번진다.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReadingViewModelPhotoSaveTest {

  private val app: Application get() = ApplicationProvider.getApplicationContext()
  private val db: AppDatabase get() = AppDatabase.getDatabase(app)
  private val source = Uri.parse("content://photos/source")
  private val broken = Uri.parse("content://photos/broken")

  private object NoCallExtractor : TextExtractor {
    override suspend fun extract(bitmap: Bitmap): OcrOutcome = error("not used")
  }

  private var bookId = 0
  private val collectors = mutableListOf<Job>()

  @Before
  fun setUp() = runBlocking {
    val caseId = db.bookcaseDao().insert(Bookcase(name = "사진 저장 테스트")).toInt()
    bookId = db.bookDao().insert(Book(title = "사진 책", author = "", totalPages = 100, bookcaseId = caseId, status = "READING")).toInt()
  }

  @After
  fun tearDown() = collectors.forEach { it.cancel() }

  private fun vm(store: FakeDiaryPhotoStore) = ReadingViewModel(app, NoCallExtractor, NoCallExtractor, store) { false }

  private fun awaitUntil(timeoutMs: Long = 10_000, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (!condition() && System.currentTimeMillis() < deadline) {
      shadowOf(Looper.getMainLooper()).idle()
      Thread.sleep(20)
    }
    assertTrue("condition not met within ${timeoutMs}ms", condition())
  }

  /** 저장이 끝날 때까지 기다리고 저장하지 못한 사진 수를 돌려준다. */
  private fun save(vm: ReadingViewModel, photos: PhotoInput, id: Int? = null, text: String = "문장"): Int {
    var failed: Int? = null
    vm.saveDiaryWithPhotos(bookId, 1, text, "메모", id = id, createdAt = 1_000L, photos = photos) { failed = it }
    awaitUntil { failed != null }
    return failed!!
  }

  private fun diaryWithPhotos(vararg paths: String): Int = runBlocking {
    val d = db.diaryDao().insert(Diary(bookId = bookId, page = 1, selectedText = "처음 문장", notes = "")).toInt()
    db.diaryPhotoDao().insertAll(paths.mapIndexed { i, p -> DiaryPhoto(diaryId = d, path = p, sortOrder = i) })
    d
  }

  private fun rows(diaryId: Int) = runBlocking { db.diaryPhotoDao().photosFor(diaryId) }

  private fun diariesOfBook() = runBlocking { db.diaryDao().getAllDiaries().first() }.filter { it.bookId == bookId }

  @Test
  fun newRecord_keepsPhoto_withoutAnyOption() {
    val store = FakeDiaryPhotoStore()

    assertEquals(0, save(vm(store), PhotoInput.Slots(listOf(PhotoInputSlot.New(source)))))

    val diary = diariesOfBook().single()
    assertEquals(listOf("new0.jpg"), rows(diary.id).map { it.path })
    assertEquals(0, rows(diary.id).single().sortOrder)
  }

  /** LC 관찰 3: Repository만 보는 M5는 VM이 REPLACE insert로 되돌아가는 회귀를 못 잡는다. */
  @Test
  fun editThroughViewModel_textOnly_keepsPhotoRows() {
    val store = FakeDiaryPhotoStore().apply { seed("a.jpg", "b.jpg", "c.jpg") }
    val d = diaryWithPhotos("a.jpg", "b.jpg", "c.jpg")
    val before = rows(d)
    val vm = vm(store)

    save(vm, PhotoInput.Unchanged, id = d, text = "고친 문장")

    assertEquals(before, rows(d))
    assertEquals("고친 문장", diariesOfBook().single { it.id == d }.selectedText)
    assertTrue(store.deleted.isEmpty())

    // P3부터 있던 saveDiary 경로도 같다.
    var done = false
    vm.saveDiary(bookId, 2, "또 고친 문장", "", id = d, createdAt = 1_000L) { done = true }
    awaitUntil { done }
    assertEquals(before, rows(d))
  }

  @Test
  fun editSlots_keepReplaceDeleteAdd_followListOrder() {
    val store = FakeDiaryPhotoStore().apply { seed("a.jpg", "b.jpg", "c.jpg") }
    val d = diaryWithPhotos("a.jpg", "b.jpg", "c.jpg")
    val (a, b, _) = rows(d)

    save(
      vm(store),
      PhotoInput.Slots(
        listOf(
          PhotoInputSlot.Replace(b.id, source), // b 교체
          PhotoInputSlot.Existing(a.id), // a 유지(순서는 뒤로)
          PhotoInputSlot.New(source) // 추가, c는 뺀다
        )
      ),
      id = d
    )

    val after = rows(d)
    assertEquals(listOf("new0.jpg", "a.jpg", "new1.jpg"), after.map { it.path })
    assertEquals(listOf(0, 1, 2), after.map { it.sortOrder })
    assertEquals(a.id, after[1].id)
    assertEquals(setOf("b.jpg", "c.jpg"), store.deleted.toSet())
  }

  @Test
  fun emptySlots_clearsPhotos() {
    val store = FakeDiaryPhotoStore().apply { seed("a.jpg") }
    val d = diaryWithPhotos("a.jpg")

    save(vm(store), PhotoInput.Slots(emptyList()), id = d)

    assertTrue(rows(d).isEmpty())
    assertEquals(listOf("a.jpg"), store.deleted)
  }

  /** 커밋 뒤 파일 삭제가 실패해도 저장은 끝난 것이다. 실패로 알리면 사용자가 저장된 기록을 다시 쓴다. 남은 파일은 sweep이 걷는다. */
  @Test
  fun commitThenFileDeleteFails_reportsSuccess_andTriesEveryFile() {
    val store = FakeDiaryPhotoStore(failDelete = true).apply { seed("a.jpg", "b.jpg") }
    val d = diaryWithPhotos("a.jpg", "b.jpg")
    val vm = vm(store)
    var failedCalled = false
    var completed = false

    vm.saveDiaryWithPhotos(bookId, 1, "문장", "", id = d, createdAt = 1_000L, photos = PhotoInput.Slots(emptyList()), onFailure = { failedCalled = true }) { completed = true }
    awaitUntil { completed || failedCalled }

    assertFalse("커밋됐는데 실패로 알리면 안 된다", failedCalled)
    assertTrue(rows(d).isEmpty())
    assertEquals(listOf("a.jpg", "b.jpg"), store.deleted)
  }

  /** 교체할 사진을 못 읽어도 기존 사진을 잃지 않는다. */
  @Test
  fun replaceFailure_keepsOldPhoto_andReportsFailure() {
    val store = FakeDiaryPhotoStore(failFor = setOf(broken)).apply { seed("a.jpg") }
    val d = diaryWithPhotos("a.jpg")
    val a = rows(d).single()

    val failed = save(vm(store), PhotoInput.Slots(listOf(PhotoInputSlot.Replace(a.id, broken))), id = d)

    assertEquals(1, failed)
    assertEquals(listOf(a), rows(d))
    assertTrue(store.deleted.isEmpty())
  }

  @Test
  fun newPhotoFailure_stillSavesText() {
    val store = FakeDiaryPhotoStore(failFor = setOf(broken))

    assertEquals(1, save(vm(store), PhotoInput.Slots(listOf(PhotoInputSlot.New(broken))), text = "글은 남는다"))

    val diary = diariesOfBook().single()
    assertEquals("글은 남는다", diary.selectedText)
    assertTrue(rows(diary.id).isEmpty())
  }

  @Test
  fun doubleTapWhileSaving_createsOneRecord() {
    val gate = CompletableDeferred<Unit>()
    val store = FakeDiaryPhotoStore(gate = gate)
    val vm = vm(store)
    var completions = 0
    val slots = PhotoInput.Slots(listOf(PhotoInputSlot.New(source)))

    vm.saveDiaryWithPhotos(bookId, 1, "한 번", "", photos = slots) { completions++ }
    assertTrue(vm.diarySaving.value)
    vm.saveDiaryWithPhotos(bookId, 1, "두 번", "", photos = slots) { completions++ }
    gate.complete(Unit)
    awaitUntil { completions > 0 }

    assertFalse(vm.diarySaving.value)
    assertEquals(1, completions)
    assertEquals(listOf("한 번"), diariesOfBook().map { it.selectedText })
  }

  /** P4 선행(LC 11-03): 저장 중 sweep이 돌아도 행이 커밋되기 전의 새 파일을 지우지 않는다. */
  @Test
  fun sweepDuringSave_waitsForCommit_andKeepsNewFile() {
    val gate = CompletableDeferred<Unit>()
    val store = FakeDiaryPhotoStore(gate = gate)
    val vm = vm(store)
    var completed = false
    var swept = false

    vm.saveDiaryWithPhotos(bookId, 1, "문장", "", photos = PhotoInput.Slots(listOf(PhotoInputSlot.New(source)))) { completed = true }
    collectors += CoroutineScope(Dispatchers.Main).launch { vm.sweepOrphanPhotos(); swept = true }
    repeat(5) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(20) }
    assertFalse("저장이 끝나기 전에는 이 sweep이 끝나지 않는다", swept)

    gate.complete(Unit)
    awaitUntil { completed && swept }

    assertTrue("새 파일이 참조 목록에 있다", store.sweepRefs.last().contains("new0.jpg"))
    assertTrue("새 파일은 남는다", "new0.jpg" in store.files)
  }

  @Test
  fun sweep_removesOnlyUnreferencedFiles() {
    val store = FakeDiaryPhotoStore().apply { seed("kept.jpg", "orphan.jpg") }
    diaryWithPhotos("kept.jpg")
    val vm = vm(store)

    assertEquals(1, runBlocking { vm.sweepOrphanPhotos() })

    assertEquals(setOf("kept.jpg"), store.files.toSet())
  }

  /** P4 선행: 저장이 끝나기 전에 화면을 떠나도 임시 파일을 먼저 지우지 않는다. 그 사이 새로 찍은 파일은 남긴다. */
  @Test
  fun leavingWhileSaving_defersTempDelete_andSparesLaterCapture() {
    val cache = app.cacheDir
    val temp = File.createTempFile(CameraTempFiles.PREFIX, CameraTempFiles.SUFFIX, cache)
    val gate = CompletableDeferred<Unit>()
    val store = FakeDiaryPhotoStore(gate = gate)
    val vm = vm(store)
    var completed = false

    vm.saveDiaryWithPhotos(bookId, 1, "문장", "", photos = PhotoInput.Slots(listOf(PhotoInputSlot.New(Uri.fromFile(temp))))) { completed = true }
    vm.releaseCameraTempFiles(cache)
    assertTrue("저장 중에는 남는다", temp.exists())
    val later = File.createTempFile(CameraTempFiles.PREFIX, CameraTempFiles.SUFFIX, cache)

    gate.complete(Unit)
    awaitUntil { completed && !temp.exists() }

    assertTrue("떠난 뒤 찍은 파일은 지우지 않는다", later.exists())
    later.delete()
  }

  @Test
  fun leavingWithoutSave_deletesTempNow() {
    val temp = File.createTempFile(CameraTempFiles.PREFIX, CameraTempFiles.SUFFIX, app.cacheDir)

    vm(FakeDiaryPhotoStore()).releaseCameraTempFiles(app.cacheDir)

    assertFalse(temp.exists())
  }

  /** dangling: 행은 있고 파일이 없으면 file = null, 행은 그대로 둔다. */
  @Test
  fun recallPhotos_followSortOrder_andMarkDangling() {
    val store = FakeDiaryPhotoStore().apply { seed("here.jpg") }
    val d = diaryWithPhotos("here.jpg", "gone.jpg")
    val vm = vm(store)
    collectors += CoroutineScope(Dispatchers.Main).launch { vm.recallPhotos.collect { } }

    var photos: List<RecallPhoto>? = null
    awaitUntil { photos = vm.recallPhotos.value[d]; photos?.size == 2 }

    assertNotNull(photos!![0].file)
    assertNull(photos!![1].file)
    assertEquals(2, rows(d).size)
    assertEquals(rows(d).map { it.id }, photos!!.map { it.photoId })
  }

  /** 예시 기록에 사용자가 붙인 사진은 «예시 지우기»로 사라지지 않는다. 그 책은 내 책으로 남고 나머지 예시만 지워진다. */
  @Test
  fun deleteSampleBooks_keepsSampleWithUserPhoto() {
    val store = FakeDiaryPhotoStore().apply { seed("sample.jpg") }
    val vm = vm(store)
    var added = -1
    vm.insertSampleBooks { added = it }
    awaitUntil { added >= 0 }
    assertEquals(2, added)
    val marks = app.getSharedPreferences(com.example.data.SampleBooks.PREFS_NAME, Context.MODE_PRIVATE)
      .getStringSet(com.example.data.SampleBooks.KEY, emptySet()).orEmpty()
    val sampleBookId = marks.first().substringBefore('|').toInt()
    val diaryId = runBlocking { db.diaryDao().getAllDiaries().first() }.first { it.bookId == sampleBookId }.id
    runBlocking { db.diaryPhotoDao().insertAll(listOf(DiaryPhoto(diaryId = diaryId, path = "sample.jpg", sortOrder = 0))) }

    var removed = -1
    var kept = -1
    vm.deleteSampleBooks { r, k -> removed = r; kept = k }
    awaitUntil { removed >= 0 }

    assertEquals(1, removed)
    assertEquals(1, kept)
    assertTrue("사진 파일을 지우면 안 된다: ${store.deleted}", store.deleted.isEmpty())
    assertEquals(listOf("sample.jpg"), rows(diaryId).map { it.path })
  }

  /** 저장 중 뒤로 가면 저장 완료가 다른 화면을 닫지 않는다. */
  @Test
  fun navigateBackIfCurrent_onlyClosesTheScreenThatStartedTheSave() {
    val vm = vm(FakeDiaryPhotoStore())
    vm.navigateTo(Screen.BookDetail(bookId))
    val saving = Screen.AddDiary(bookId)
    vm.navigateTo(saving)

    vm.navigateBack() // 저장이 끝나기 전에 사용자가 뒤로 갔다
    val reopened = Screen.AddDiary(bookId) // 같은 책의 새 기록 화면을 다시 열었다
    vm.navigateTo(reopened)
    vm.navigateBackIfCurrent(saving)
    assertEquals("다시 연 화면은 그대로", reopened, vm.currentScreenState.value)
    assertTrue(vm.currentScreenState.value === reopened)

    vm.navigateBackIfCurrent(reopened)
    assertEquals(Screen.BookDetail(bookId), vm.currentScreenState.value)
  }
}
