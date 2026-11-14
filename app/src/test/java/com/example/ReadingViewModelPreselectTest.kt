package com.example

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.data.ocr.OcrLine
import com.example.data.ocr.OcrOutcome
import com.example.data.ocr.OcrWord
import com.example.data.ocr.TextExtractor
import com.example.ui.viewmodel.OcrState
import com.example.ui.viewmodel.ReadingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * SPIKE §4-1 · §4-2: 가짜 추출기가 줄·어절 상자를 돌려주면, ViewModel이 인식에 쓴 그 비트맵으로 밑줄 미리 선택을 채운다.
 * 결과 글(ocrState)은 상자가 있든 없든 같다(동작 변화 0).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class ReadingViewModelPreselectTest {

  private val dispatcher = StandardTestDispatcher()

  @Before fun setUp() { Dispatchers.setMain(dispatcher) }
  @After fun tearDown() { Dispatchers.resetMain() }

  /** 받은 비트맵 크기를 적어 두는 가짜 추출기. */
  private class FakeExtractor(private val outcome: OcrOutcome) : TextExtractor {
    var seen: Pair<Int, Int>? = null
    override suspend fun extract(bitmap: Bitmap): OcrOutcome { seen = bitmap.width to bitmap.height; return outcome }
  }

  private fun viewModel(extractor: TextExtractor): ReadingViewModel =
    ReadingViewModel(ApplicationProvider.getApplicationContext<Application>(), extractor, extractor) { false }

  private fun pageBitmap(): Bitmap {
    val gray = UnderlinePreselectTest.page()
    val argb = IntArray(gray.size) { (0xFF shl 24) or (gray[it] shl 16) or (gray[it] shl 8) or gray[it] }
    return Bitmap.createBitmap(UnderlinePreselectTest.W, UnderlinePreselectTest.H, Bitmap.Config.ARGB_8888).apply {
      setPixels(argb, 0, UnderlinePreselectTest.W, 0, 0, UnderlinePreselectTest.W, UnderlinePreselectTest.H)
    }
  }

  private val lines = listOf(
    OcrLine("가 나 다", null, UnderlinePreselectTest.line1Words.mapIndexed { i, b -> OcrWord("가나다"[i].toString(), b) }),
    OcrLine("라 마 바", null, UnderlinePreselectTest.line2Words.mapIndexed { i, b -> OcrWord("라마바"[i].toString(), b) })
  )

  private suspend fun awaitUntil(cond: () -> Boolean) {
    repeat(500) {
      if (cond()) return
      dispatcher.scheduler.advanceUntilIdle()
      Thread.sleep(5)
    }
  }

  @Test
  fun textWithBoxes_fillsPreselectFromTheSameBitmap_andKeepsTheResultText() = runTest(dispatcher) {
    val fake = FakeExtractor(OcrOutcome.Text("가 나 다\n라 마 바", lines))
    val vm = viewModel(fake)
    vm.processUnderlineOcr(pageBitmap())
    awaitUntil { vm.underlinePreselect.value != null }

    assertEquals(OcrState.Success("가 나 다\n라 마 바"), vm.ocrState.value)
    assertEquals(UnderlinePreselectTest.W to UnderlinePreselectTest.H, fake.seen)
    val words = vm.underlinePreselect.value!!
    assertEquals(listOf("가", "나", "다", "라", "마", "바"), words.map { it.text })
    assertEquals(listOf(false, true, true, false, false, false), words.map { it.selected })

    vm.resetOcrState()
    assertNull(vm.underlinePreselect.value)
  }

  @Test
  fun textWithoutBoxes_leavesPreselectEmpty_andTheSameResult() = runTest(dispatcher) {
    val vm = viewModel(FakeExtractor(OcrOutcome.Text("가 나 다\n라 마 바")))
    vm.processUnderlineOcr(pageBitmap())
    awaitUntil { vm.ocrState.value is OcrState.Success }

    assertEquals(OcrState.Success("가 나 다\n라 마 바"), vm.ocrState.value)
    assertNull(vm.underlinePreselect.value)
  }
}
