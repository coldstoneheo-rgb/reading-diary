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
    precise: TextExtractor = FakeExtractor(OcrOutcome.Text("precise")),
    keyPresent: Boolean = false
  ): ReadingViewModel =
    ReadingViewModel(ApplicationProvider.getApplicationContext<Application>(), extractor, precise) { keyPresent }

  @Test
  fun preciseAnalysis_withKeyAndConsent_callsPreciseExtractorOnly_andKeepsDefaultStateIdle() = runTest(dispatcher) {
    val onDevice = FakeExtractor(OcrOutcome.Text("on-device"))
    val precise = FakeExtractor(OcrOutcome.Text("precise"))
    val vm = viewModel(onDevice, precise, keyPresent = true)
    awaitUntil { vm.geminiKeyRegistered.value } // 키 존재 여부는 IO에서 로드된다
    vm.setGeminiPhotoConsent(true)
    awaitUntil { vm.preciseAnalysisAvailable.value }
    assertEquals(true, vm.preciseAnalysisAvailable.value)

    // 디코드 가능한 실제 PNG
    val app = ApplicationProvider.getApplicationContext<Application>()
    val file = java.io.File(app.cacheDir, "precise.png")
    file.outputStream().use { Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
    vm.processPreciseAnalysis(android.net.Uri.fromFile(file), 0f, false, 0f, 0f, 1f, 1f)
    awaitUntil { vm.preciseOcrState.value is OcrState.Success }

    assertEquals(OcrState.Success("precise"), vm.preciseOcrState.value)
    assertEquals(1, precise.calls)
    assertEquals(0, onDevice.calls)
    assertEquals(OcrState.Idle, vm.ocrState.value) // 결과는 편집창(기본 상태)에 자동 반영되지 않는다
  }

  @Test
  fun preciseAnalysis_refusedWithKeyButNoConsent_extractorNotCalled() = runTest(dispatcher) {
    val precise = FakeExtractor(OcrOutcome.Text("precise"))
    val vm = viewModel(FakeExtractor(OcrOutcome.NoText), precise, keyPresent = true)
    awaitUntil { vm.geminiKeyRegistered.value }
    val uri = android.net.Uri.fromFile(java.io.File(ApplicationProvider.getApplicationContext<Application>().cacheDir, "x.png"))

    vm.processPreciseAnalysis(uri, 0f, false, 0f, 0f, 1f, 1f) // 동의 없음
    advanceUntilIdle()

    assertEquals(OcrState.Error(ReadingViewModel.CONSENT_REQUIRED_MESSAGE), vm.preciseOcrState.value)
    assertEquals(0, precise.calls)
  }

  @Test
  fun consentRestoredWithoutKey_isRevokedAtStartup() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    app.getSharedPreferences("diary_general_settings", android.content.Context.MODE_PRIVATE)
      .edit().putBoolean("gemini_photo_consent", true).commit()

    val vm = viewModel(FakeExtractor(OcrOutcome.NoText), keyPresent = false)
    awaitUntil { !vm.geminiPhotoConsent.value }

    assertEquals(false, vm.geminiPhotoConsent.value)
  }

  @Test
  fun enterPreciseScope_keepsResultForSameOwner_dropsForOtherOwner() {
    val vm = viewModel(FakeExtractor(OcrOutcome.NoText))
    vm.enterPreciseScope("1/null")
    vm.preciseOcrState.value = OcrState.Success("paid result")

    vm.enterPreciseScope("1/null") // 회전 등 재생성
    assertEquals(OcrState.Success("paid result"), vm.preciseOcrState.value)

    vm.enterPreciseScope("2/null") // 다른 책
    assertEquals(OcrState.Idle, vm.preciseOcrState.value)
  }

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
  fun clearGeminiApiKey_failure_keepsRegistrationAndConsent() = runTest(dispatcher) {
    // Robolectric에는 AndroidKeyStore가 없어 암호화 저장소를 열 수 없다 → 삭제 실패 경로가 결정적으로 재현된다
    val vm = viewModel(FakeExtractor(OcrOutcome.NoText), keyPresent = true)
    awaitUntil { vm.geminiKeyRegistered.value }
    vm.setGeminiPhotoConsent(true)
    awaitUntil { vm.preciseAnalysisAvailable.value }
    assertEquals(true, vm.preciseAnalysisAvailable.value)

    var result: Boolean? = null
    vm.clearGeminiApiKey { result = it }
    awaitUntil { result != null }

    assertEquals(false, result)
    assertEquals(true, vm.geminiKeyRegistered.value)   // 실패했으므로 등록 상태 유지
    assertEquals(true, vm.geminiPhotoConsent.value)    // 동의도 유지
  }

  private val bitmap: Bitmap get() = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

  /** IO 스레드(실 디스패처)와 테스트 Main 디스패처를 오가는 작업을 기다린다. */
  private fun awaitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (!condition() && System.currentTimeMillis() < deadline) {
      dispatcher.scheduler.advanceUntilIdle()
      Thread.sleep(20)
    }
  }

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
