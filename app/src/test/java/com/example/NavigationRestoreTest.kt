package com.example

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.data.ocr.OcrOutcome
import com.example.data.ocr.TextExtractor
import com.example.data.photo.CameraTempFiles
import com.example.ui.viewmodel.ReadingViewModel
import com.example.ui.viewmodel.Screen
import com.example.ui.viewmodel.ScreenCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 화면 위치 저장·복원 규칙과 «이번 촬영으로 채워진 파일» 판정. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NavigationRestoreTest {

  @get:Rule val folder = TemporaryFolder()

  private object NoCallExtractor : TextExtractor {
    override suspend fun extract(bitmap: Bitmap): OcrOutcome = error("not used")
  }

  private fun vm() = ReadingViewModel(
    ApplicationProvider.getApplicationContext<Application>(), NoCallExtractor, NoCallExtractor, FakeDiaryPhotoStore()
  ) { false }

  @Test
  fun codec_roundTripsEveryScreen() {
    listOf(
      Screen.Dashboard, Screen.BookDetail(3), Screen.AddEditBook(), Screen.AddEditBook(3), Screen.AddEditBook(startWithSearch = true),
      Screen.AddDiary(3), Screen.AddDiary(3, 9), Screen.Settings, Screen.Statistics, Screen.KnowledgeDrawer,
      Screen.PhraseRecall(), Screen.PhraseRecall("그리움|세계")
    ).forEach { assertEquals(it, ScreenCodec.decode(ScreenCodec.encode(it))) }
  }

  @Test
  fun codec_rejectsUnknownOrBrokenValues() {
    assertNull(ScreenCodec.decode("nope"))
    assertNull(ScreenCodec.decode("diary|x|"))
    assertNull(ScreenCodec.decode("book"))
  }

  @Test
  fun restore_setsCurrentAndBackStack() {
    val first = vm()
    first.navigateTo(Screen.BookDetail(3))
    first.navigateTo(Screen.AddDiary(3))

    val second = vm()
    second.restoreNavigation(first.navigationSnapshot())

    assertEquals(Screen.AddDiary(3), second.currentScreenState.value)
    second.navigateBack()
    assertEquals(Screen.BookDetail(3), second.currentScreenState.value)
    second.navigateBack()
    assertEquals(Screen.Dashboard, second.currentScreenState.value)
  }

  @Test
  fun restore_appliesOnlyOnce_soRotationKeepsCurrentPosition() {
    val model = vm()
    model.restoreNavigation(null)
    model.navigateTo(Screen.Settings)

    model.restoreNavigation(listOf("dashboard", "book|3"))

    assertEquals(Screen.Settings, model.currentScreenState.value)
  }

  @Test
  fun restore_ignoresBrokenSnapshot() {
    val model = vm()
    model.restoreNavigation(listOf("book|3", "nope"))
    assertEquals(Screen.Dashboard, model.currentScreenState.value)
  }

  @Test
  fun hasNewPhoto_needsBytesFreshTimeAndCameraFileName() {
    val dir = folder.root
    val name = CameraTempFiles.PREFIX + "a" + CameraTempFiles.SUFFIX
    val file = dir.resolve(name)
    val start = 1_000_000_500L
    assertFalse("파일 없음", CameraTempFiles.hasNewPhoto(dir, name, start))

    file.createNewFile()
    file.setLastModified(start + 1_000)
    assertFalse("빈 파일", CameraTempFiles.hasNewPhoto(dir, name, start))

    file.writeBytes(byteArrayOf(1))
    file.setLastModified(start + 1_000)
    assertTrue(CameraTempFiles.hasNewPhoto(dir, name, start))

    file.setLastModified(1_000_000_000L)
    assertTrue("같은 초면 이번 촬영으로 본다", CameraTempFiles.hasNewPhoto(dir, name, start))

    file.setLastModified(start - 1_000)
    assertFalse("촬영 시작 전 파일", CameraTempFiles.hasNewPhoto(dir, name, start))

    file.setLastModified(start + 1_000)
    assertFalse("시작 시각이 복원되지 않음", CameraTempFiles.hasNewPhoto(dir, name, 0L))
    assertFalse("파일 이름 없음", CameraTempFiles.hasNewPhoto(dir, null, start))

    val other = dir.resolve("other.jpg")
    other.writeBytes(byteArrayOf(1))
    assertFalse("촬영 임시 파일이 아님", CameraTempFiles.hasNewPhoto(dir, "other.jpg", 1L))
  }
}
