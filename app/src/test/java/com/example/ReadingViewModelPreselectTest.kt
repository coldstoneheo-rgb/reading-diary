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
import kotlinx.coroutines.test.advanceUntilIdle
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

  private fun viewModel(extractor: TextExtractor, precise: TextExtractor = extractor, keyPresent: Boolean = false): ReadingViewModel =
    ReadingViewModel(ApplicationProvider.getApplicationContext<Application>(), extractor, precise) { keyPresent }

  /** 화면이 실제로 쓰는 URI 경로용: 같은 쪽 그림을 PNG(무손실)로 저장. */
  private fun pageUri(name: String): android.net.Uri {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val file = java.io.File(app.cacheDir, name)
    file.outputStream().use { pageBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    return android.net.Uri.fromFile(file)
  }

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
  fun uriPath_usedByTheScreen_fillsPreselectBeforeTheBitmapIsRecycled() = runTest(dispatcher) {
    // 화면(OcrDiaryScreen)이 부르는 경로: 디코드 → 인식 → 결과 → 미리 선택 → 회수. 회수 뒤에 읽으면 예외가 삼켜져 null이 된다.
    val fake = FakeExtractor(OcrOutcome.Text("가 나 다\n라 마 바", lines))
    val vm = viewModel(fake)
    vm.processUnderlineOcr(pageUri("preselect-uri.png"), 0f, false, 0f, 0f, 1f, 1f)
    awaitUntil { vm.underlinePreselect.value != null }

    assertEquals(OcrState.Success("가 나 다\n라 마 바"), vm.ocrState.value)
    assertEquals(UnderlinePreselectTest.W to UnderlinePreselectTest.H, fake.seen)
    assertEquals(listOf(false, true, true, false, false, false), vm.underlinePreselect.value!!.map { it.selected })
  }

  @Test
  fun preciseAnalysis_neverFillsPreselect() = runTest(dispatcher) {
    val precise = FakeExtractor(OcrOutcome.Text("precise", lines))
    val vm = viewModel(FakeExtractor(OcrOutcome.Text("on-device")), precise, keyPresent = true)
    awaitUntil { vm.geminiKeyRegistered.value }
    vm.setGeminiPhotoConsent(true)
    awaitUntil { vm.preciseAnalysisAvailable.value }
    vm.processPreciseAnalysis(pageUri("preselect-precise.png"), 0f, false, 0f, 0f, 1f, 1f)
    awaitUntil { vm.preciseOcrState.value is OcrState.Success }

    assertEquals(OcrState.Success("precise"), vm.preciseOcrState.value)
    assertNull(vm.underlinePreselect.value) // 상자를 돌려줘도 정밀 분석 경로는 미리 선택을 하지 않는다
  }

  @Test
  fun whilePreselectRuns_afterTheResult_preciseAnalysisAndNewRecognitionAreAccepted() = runTest(dispatcher) {
    // 동작 변화 0: 결과 글이 나온 뒤에는 main처럼 정밀 분석 · 새 인식 요청을 받는다(미리 선택이 막지 않는다).
    val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
    val onDevice = FakeExtractor(OcrOutcome.Text("가 나 다\n라 마 바", lines))
    val precise = FakeExtractor(OcrOutcome.Text("precise"))
    val vm = viewModel(onDevice, precise, keyPresent = true)
    var started = 0
    vm.preselectRunner = { _, _ -> started++; gate.await(); emptyList() }
    awaitUntil { vm.geminiKeyRegistered.value }
    vm.setGeminiPhotoConsent(true)
    awaitUntil { vm.preciseAnalysisAvailable.value }

    vm.processUnderlineOcr(pageUri("preselect-busy.png"), 0f, false, 0f, 0f, 1f, 1f)
    awaitUntil { vm.ocrState.value is OcrState.Success && started == 1 }
    // 미리 선택이 막혀 있는 동안 정밀 분석 요청 → 실행된다.
    vm.processPreciseAnalysis(pageUri("preselect-busy-precise.png"), 0f, false, 0f, 0f, 1f, 1f)
    awaitUntil { vm.preciseOcrState.value is OcrState.Success }
    assertEquals(OcrState.Success("precise"), vm.preciseOcrState.value)
    assertNull(vm.underlinePreselect.value) // 정밀 분석을 시작하면 앞선 미리 선택은 비운다

    // 새 인식 요청도 받는다(두 번째 미리 선택이 시작된다).
    vm.processUnderlineOcr(pageUri("preselect-busy-2.png"), 0f, false, 0f, 0f, 1f, 1f)
    awaitUntil { started == 2 }
    assertEquals(2, started)
    gate.complete(Unit)
    awaitUntil { vm.underlinePreselect.value != null }
    assertEquals(emptyList<Any>(), vm.underlinePreselect.value)
  }

  @Test
  fun preciseAnalysisThenGateReleased_preselectStaysNull() = runTest(dispatcher) {
    // #64 재게이트 관찰 ①: 정밀 분석이 앞선 미리 선택을 취소한 뒤 그 계산이 풀려도(낡은 Job) 값을 쓰지 않는다.
    val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
    val vm = viewModel(FakeExtractor(OcrOutcome.Text("가 나 다
라 마 바", lines)), FakeExtractor(OcrOutcome.Text("precise")), keyPresent = true)
    var started = 0
    var finished = 0
    vm.preselectRunner = { _, _ -> started++; gate.await(); finished++; listOf(com.example.data.ocr.UnderlinePreselect.Word("가", 0, true)) }
    awaitUntil { vm.geminiKeyRegistered.value }
    vm.setGeminiPhotoConsent(true)
    awaitUntil { vm.preciseAnalysisAvailable.value }

    vm.processUnderlineOcr(pageUri("preselect-stale.png"), 0f, false, 0f, 0f, 1f, 1f)
    awaitUntil { vm.ocrState.value is OcrState.Success && started == 1 }
    vm.processPreciseAnalysis(pageUri("preselect-stale-precise.png"), 0f, false, 0f, 0f, 1f, 1f)
    awaitUntil { vm.preciseOcrState.value is OcrState.Success }
    gate.complete(Unit)
    advanceUntilIdle()
    assertEquals(0, finished) // 취소된 Job은 gate를 지나 값을 만들지 못한다
    assertNull(vm.underlinePreselect.value)
  }

  @Test
  fun resetWhilePreselectRuns_leavesNoPreselect() = runTest(dispatcher) {
    val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
    val vm = viewModel(FakeExtractor(OcrOutcome.Text("가 나 다\n라 마 바", lines)))
    var started = 0
    vm.preselectRunner = { _, _ -> started++; gate.await(); emptyList() }
    vm.processUnderlineOcr(pageUri("preselect-reset.png"), 0f, false, 0f, 0f, 1f, 1f)
    awaitUntil { started == 1 }
    vm.resetOcrState() // 결과는 나왔고 미리 선택 도중 화면 이탈
    gate.complete(Unit)
    repeat(20) { dispatcher.scheduler.advanceUntilIdle(); Thread.sleep(5) }
    assertNull(vm.underlinePreselect.value)
  }

  @Test
  fun cancelledRecognition_leavesNoPreselect() = runTest(dispatcher) {
    // 인식(추출) 도중 취소. 미리 선택 도중 취소는 resetWhilePreselectRuns_leavesNoPreselect.
    val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
    val gated = object : TextExtractor {
      override suspend fun extract(bitmap: Bitmap): OcrOutcome { gate.await(); return OcrOutcome.Text("늦은 결과", lines) }
    }
    val vm = viewModel(gated)
    vm.processUnderlineOcr(pageUri("preselect-cancel.png"), 0f, false, 0f, 0f, 1f, 1f)
    awaitUntil { vm.ocrState.value == OcrState.Processing }
    vm.resetOcrState() // 인식 도중 화면 이탈
    gate.complete(Unit)
    repeat(20) { dispatcher.scheduler.advanceUntilIdle(); Thread.sleep(5) }

    assertEquals(OcrState.Idle, vm.ocrState.value)
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
