package com.example

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Book
import com.example.data.Bookcase
import com.example.data.Diary
import com.example.data.export.DocumentTree
import com.example.data.export.MarkdownExport
import com.example.data.ocr.OcrOutcome
import com.example.data.ocr.TextExtractor
import com.example.ui.viewmodel.ExportState
import com.example.ui.viewmodel.ReadingViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** STAGE2-PLAN ②: FakeDocumentTree로 created/skipped 집계 · 쓰기 실패 → Error · 기존 이름으로는 createAndWrite가 한 번도 불리지 않는다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReadingViewModelExportTest {

  private val app: Application get() = ApplicationProvider.getApplicationContext()

  private object NoCallExtractor : TextExtractor {
    override suspend fun extract(bitmap: Bitmap): OcrOutcome = error("not used")
  }

  /** 메모리 폴더. [failOn]이면 그 이름의 쓰기에서 실패한다. [renameTo]면 프로바이더가 이름을 바꾼 것처럼 돌려준다. */
  private class FakeDocumentTree(
    initial: Set<String>,
    private val failOn: String? = null,
    private val renameTo: Map<String, String> = emptyMap()
  ) : DocumentTree {
    val files = LinkedHashMap<String, String>().apply { initial.forEach { put(it, "ORIGINAL") } }
    val createCalls = mutableListOf<String>()
    private val initialNames = initial
    override fun existingNames(): Set<String> = initialNames
    override fun createAndWrite(fileName: String, mimeType: String, content: String): String? {
      createCalls += fileName
      if (fileName == failOn) return null
      val actual = renameTo[fileName] ?: fileName
      check(actual !in files) { "would overwrite $actual" }
      files[actual] = content
      return actual
    }
  }

  private fun resetDatabase() {
    val field = AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
    (field.get(null) as AppDatabase?)?.close()
    field.set(null, null)
    app.deleteDatabase("reading_diary_db")
  }

  @Before fun setUp() {
    resetDatabase()
    runBlocking {
      val db = AppDatabase.getDatabase(app)
      val caseId = db.bookcaseDao().insert(Bookcase(name = "테스트", isSystem = false)).toInt()
      val a = db.bookDao().insert(Book(title = "가", author = "김", totalPages = 100, bookcaseId = caseId, status = "READING")).toInt()
      val b = db.bookDao().insert(Book(title = "나", author = "이", totalPages = 100, bookcaseId = caseId, status = "READING")).toInt()
      db.diaryDao().insert(Diary(bookId = a, page = 3, selectedText = "바다가 보였다", notes = ""))
      db.diaryDao().insert(Diary(bookId = b, page = 7, selectedText = "바다는 넓다", notes = ""))
    }
  }

  @After fun tearDown() = resetDatabase()

  private fun viewModel() = ReadingViewModel(app, NoCallExtractor, NoCallExtractor) { false }

  private fun awaitState(vm: ReadingViewModel, timeoutMs: Long = 10_000): ExportState {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      shadowOf(Looper.getMainLooper()).idle()
      val s = vm.exportState.value
      if (s is ExportState.Done || s is ExportState.Error) return s
      Thread.sleep(20)
    }
    error("export did not finish: ${vm.exportState.value}")
  }

  @Test
  fun emptyFolder_createsBookNotesAndIndex() {
    val tree = FakeDocumentTree(emptySet())
    val vm = viewModel()
    vm.exportMarkdown(tree, "2026-11-11")
    val done = awaitState(vm) as ExportState.Done
    assertEquals(listOf("가.md", "나.md", MarkdownExport.INDEX_NAME + ".md"), done.created)
    assertTrue(done.skipped.isEmpty())
    assertTrue(tree.files.getValue("가.md").contains("> 바다가 보였다"))
    assertTrue(tree.files.getValue(MarkdownExport.INDEX_NAME + ".md").contains("[[나]]"))
  }

  @Test
  fun existingNames_areSkipped_andNeverPassedToCreateAndWrite() {
    val existing = setOf("가.md", MarkdownExport.INDEX_NAME + ".md")
    val tree = FakeDocumentTree(existing)
    val vm = viewModel()
    vm.exportMarkdown(tree, "2026-11-11")
    val done = awaitState(vm) as ExportState.Done
    assertEquals(listOf("나.md"), done.created)
    assertEquals(listOf("가.md", MarkdownExport.INDEX_NAME + ".md"), done.skipped)
    assertTrue("existing names must never be written: ${tree.createCalls}", tree.createCalls.none { it in existing })
    assertEquals("ORIGINAL", tree.files.getValue("가.md"))
  }

  @Test
  fun writeFailure_stopsWithError_listingFilesAlreadyCreated() {
    val tree = FakeDocumentTree(emptySet(), failOn = "나.md")
    val vm = viewModel()
    vm.exportMarkdown(tree, "2026-11-11")
    val error = awaitState(vm) as ExportState.Error
    assertEquals("나.md", error.failedFile)
    assertEquals(listOf("가.md"), error.created)
    assertEquals(listOf("가.md", "나.md"), tree.createCalls)
  }

  @Test
  fun folderAccessFailure_isErrorWithoutFile() {
    val tree = object : DocumentTree {
      override fun existingNames(): Set<String> = throw SecurityException("no access")
      override fun createAndWrite(fileName: String, mimeType: String, content: String): String? = error("must not be called")
    }
    val vm = viewModel()
    vm.exportMarkdown(tree, "2026-11-11")
    val error = awaitState(vm) as ExportState.Error
    assertEquals(null, error.failedFile)
    assertTrue(error.created.isEmpty())
  }

  @Test
  fun providerRename_isReported() {
    val tree = FakeDocumentTree(emptySet(), renameTo = mapOf("가.md" to "가 (1).md"))
    val vm = viewModel()
    vm.exportMarkdown(tree, "2026-11-11")
    val done = awaitState(vm) as ExportState.Done
    assertEquals(listOf("가.md" to "가 (1).md"), done.renamed)
    assertTrue("가 (1).md" in done.created)
    vm.dismissExportResult()
    assertEquals(ExportState.Idle, vm.exportState.value)
  }
}
