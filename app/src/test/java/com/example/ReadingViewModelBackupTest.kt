package com.example

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Book
import com.example.data.Bookcase
import com.example.data.ocr.OcrOutcome
import com.example.data.ocr.TextExtractor
import com.example.ui.viewmodel.BackupState
import com.example.ui.viewmodel.ReadingViewModel
import kotlinx.coroutines.flow.first
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** P5 ViewModel 경계: 만들기 → Created(수), 복원 → Restored, 스트림을 못 열면 Error이고 기록 불변. 사진은 Fake 저장소. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReadingViewModelBackupTest {

  private val app: Application get() = ApplicationProvider.getApplicationContext()

  private object NoCallExtractor : TextExtractor {
    override suspend fun extract(bitmap: Bitmap): OcrOutcome = error("not used")
  }

  private fun resetDatabase() {
    val field = AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
    (field.get(null) as AppDatabase?)?.close()
    field.set(null, null)
    app.deleteDatabase(AppDatabase.DB_NAME)
  }

  private fun db() = AppDatabase.getDatabase(app)
  private fun titles() = runBlocking { db().bookDao().getAllBooks().first().map { it.title }.sorted() }

  @Before fun setUp() {
    resetDatabase()
    runBlocking {
      val caseId = db().bookcaseDao().insert(Bookcase(name = "테스트")).toInt()
      db().bookDao().insert(Book(title = "가", author = "김", totalPages = 10, bookcaseId = caseId, status = "READING"))
    }
  }

  @After fun tearDown() = resetDatabase()

  private fun viewModel() = ReadingViewModel(app, NoCallExtractor, NoCallExtractor, FakeDiaryPhotoStore()) { false }

  private fun awaitResult(vm: ReadingViewModel): BackupState {
    val deadline = System.currentTimeMillis() + 15_000
    while (System.currentTimeMillis() < deadline) {
      shadowOf(Looper.getMainLooper()).idle()
      val s = vm.backupState.value
      if (s !is BackupState.Running && s != BackupState.Idle) return s
      Thread.sleep(20)
    }
    error("backup did not finish: ${vm.backupState.value}")
  }

  @Test
  fun createThenRestore_replacesRecords() {
    val vm = viewModel()
    val out = ByteArrayOutputStream()
    vm.createBackup { out }
    val created = awaitResult(vm) as BackupState.Created
    assertEquals(1, created.books)
    vm.dismissBackupResult()

    runBlocking { db().bookDao().insert(Book(title = "나중에 넣은 책", author = "", totalPages = 1, bookcaseId = db().bookcaseDao().getAllBookcasesOneShot().first().id, status = "TO_READ")) }
    assertEquals(listOf("가", "나중에 넣은 책"), titles())

    vm.restoreBackup { ByteArrayInputStream(out.toByteArray()) }
    val restored = awaitResult(vm) as BackupState.Restored
    assertEquals(1, restored.books)
    assertEquals(listOf("가"), titles())
  }

  @Test
  fun unopenableStreams_areErrors_andRecordsUnchanged() {
    val vm = viewModel()
    vm.createBackup { null }
    assertTrue(awaitResult(vm) is BackupState.Error)
    vm.dismissBackupResult()
    vm.restoreBackup { null }
    assertTrue((awaitResult(vm) as BackupState.Error).restoring)
    vm.dismissBackupResult()
    vm.restoreBackup { ByteArrayInputStream("not a zip".toByteArray()) }
    val error = awaitResult(vm) as BackupState.Error
    assertTrue(error.message, error.message.contains("지금 기록은 그대로"))
    assertEquals(listOf("가"), titles())
  }
}
