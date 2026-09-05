package com.example

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.data.ocr.OcrOutcome
import com.example.data.ocr.TextExtractor
import com.example.ui.viewmodel.OcrState
import com.example.ui.viewmodel.ReadingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ADR-002 불변식: ViewModel은 TextExtractor 인터페이스만 보며, 결과를 가짜 문장으로 채우지 않는다.
 * Gemini 경로는 어디서도 호출되지 않는다(ViewModel이 GeminiApiClient를 참조하지 않는다).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReadingViewModelOcrTest {

  private val dispatcher = StandardTestDispatcher()

  @Before fun setUp() { Dispatchers.setMain(dispatcher) }
  @After fun tearDown() { Dispatchers.resetMain() }

  private class FakeExtractor(private val outcome: OcrOutcome) : TextExtractor {
    var calls = 0
    override suspend fun extract(bitmap: Bitmap): OcrOutcome { calls++; return outcome }
  }

  private fun viewModel(extractor: TextExtractor): ReadingViewModel =
    ReadingViewModel(ApplicationProvider.getApplicationContext<Application>(), extractor)

  private val bitmap: Bitmap get() = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

  @Test
  fun recognizedText_becomesSuccessVerbatim() = runTest(dispatcher) {
    val fake = FakeExtractor(OcrOutcome.Text("태어나려는 자는 하나의 세계를 깨뜨려야 한다."))
    val vm = viewModel(fake)

    vm.processUnderlineOcr(bitmap)
    advanceUntilIdle()

    assertEquals(OcrState.Success("태어나려는 자는 하나의 세계를 깨뜨려야 한다."), vm.ocrState.value)
    assertEquals(1, fake.calls)
  }

  @Test
  fun noText_becomesErrorWithGuidance_notFakeQuote() = runTest(dispatcher) {
    val vm = viewModel(FakeExtractor(OcrOutcome.NoText))

    vm.processUnderlineOcr(bitmap)
    advanceUntilIdle()

    val state = vm.ocrState.value
    assertTrue(state is OcrState.Error)
    val message = (state as OcrState.Error).message
    assertTrue(message.contains("찾지 못했"))
    assertTrue(!message.contains("아브락사스"))
  }

  @Test
  fun engineFailure_becomesErrorWithEngineMessage() = runTest(dispatcher) {
    val vm = viewModel(FakeExtractor(OcrOutcome.Failed("모델 준비 중")))

    vm.processUnderlineOcr(bitmap)
    advanceUntilIdle()

    assertEquals(OcrState.Error("모델 준비 중"), vm.ocrState.value)
  }
}
