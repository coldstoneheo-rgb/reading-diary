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

  private fun viewModel(
    extractor: TextExtractor,
    precise: TextExtractor = FakeExtractor(OcrOutcome.Text("precise"))
  ): ReadingViewModel =
    ReadingViewModel(ApplicationProvider.getApplicationContext<Application>(), extractor, precise)

  @Test
  fun defaultOcr_neverTouchesPreciseExtractor() = runTest(dispatcher) {
    val precise = FakeExtractor(OcrOutcome.Text("precise"))
    val vm = viewModel(FakeExtractor(OcrOutcome.Text("on-device")), precise)

    vm.processUnderlineOcr(bitmap)
    advanceUntilIdle()

    assertEquals(OcrState.Success("on-device"), vm.ocrState.value)
    assertEquals(0, precise.calls)
  }

  @Test
  fun preciseAnalysis_refusedWithoutKeyAndConsent_extractorNotCalled() = runTest(dispatcher) {
    val precise = FakeExtractor(OcrOutcome.Text("precise"))
    val vm = viewModel(FakeExtractor(OcrOutcome.NoText), precise)
    val uri = android.net.Uri.fromFile(java.io.File(ApplicationProvider.getApplicationContext<Application>().cacheDir, "x.png"))

    // Robolectric에서는 암호화 저장소가 없어 키가 등록되지 않은 상태 = 동의만 있어도 거부돼야 한다
    vm.setGeminiPhotoConsent(true)
    vm.processPreciseAnalysis(uri, 0f, false, 0f, 0f, 1f, 1f)
    advanceUntilIdle()

    assertEquals(OcrState.Error(ReadingViewModel.CONSENT_REQUIRED_MESSAGE), vm.preciseOcrState.value)
    assertEquals(0, precise.calls)
    assertEquals(OcrState.Idle, vm.ocrState.value) // 기본 경로 상태는 건드리지 않는다
  }

  @Test
  fun clearGeminiApiKey_alsoRevokesConsent() {
    val vm = viewModel(FakeExtractor(OcrOutcome.NoText))
    vm.setGeminiPhotoConsent(true)
    assertEquals(true, vm.geminiPhotoConsent.value)

    vm.clearGeminiApiKey()

    assertEquals(false, vm.geminiPhotoConsent.value)
    assertEquals(false, vm.geminiKeyRegistered.value)
    assertEquals(false, vm.preciseAnalysisAvailable.value)
  }

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
  fun uriEntryPoint_missingFile_becomesDecodeError_withoutCallingExtractor() {
    // 실제 화면이 쓰는 진입점. 디코드 실패는 추출기를 부르지 않고 DECODE_ERROR_MESSAGE로 끝난다.
    val fake = FakeExtractor(OcrOutcome.Text("should not be used"))
    val vm = viewModel(fake)
    val missing = android.net.Uri.fromFile(java.io.File(ApplicationProvider.getApplicationContext<Application>().cacheDir, "missing.png"))

    kotlinx.coroutines.runBlocking {
      vm.processUnderlineOcr(missing, 0f, false, 0f, 0f, 1f, 1f)
      // Dispatchers.Default 디코드가 끝날 때까지 상태를 기다린다
      val deadline = System.currentTimeMillis() + 5_000
      while (vm.ocrState.value !is OcrState.Error && System.currentTimeMillis() < deadline) {
        dispatcher.scheduler.advanceUntilIdle()
        kotlinx.coroutines.delay(20)
      }
    }

    assertEquals(OcrState.Error(ReadingViewModel.DECODE_ERROR_MESSAGE), vm.ocrState.value)
    assertEquals(0, fake.calls)
  }

  @Test
  fun resetOcrState_cancelsInFlightJob_soStaleResultNeverLands() = runTest(dispatcher) {
    val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
    val slow = object : TextExtractor {
      override suspend fun extract(bitmap: Bitmap): OcrOutcome { gate.await(); return OcrOutcome.Text("stale") }
    }
    val vm = viewModel(slow)

    vm.processUnderlineOcr(bitmap)
    dispatcher.scheduler.runCurrent()
    assertEquals(OcrState.Processing, vm.ocrState.value)

    vm.resetOcrState() // 화면 이탈
    gate.complete(Unit)
    advanceUntilIdle()

    assertEquals(OcrState.Idle, vm.ocrState.value)
  }

  @Test
  fun engineFailure_becomesErrorWithEngineMessage() = runTest(dispatcher) {
    val vm = viewModel(FakeExtractor(OcrOutcome.Failed("모델 준비 중")))

    vm.processUnderlineOcr(bitmap)
    advanceUntilIdle()

    assertEquals(OcrState.Error("모델 준비 중"), vm.ocrState.value)
  }
}
