package com.example.data.ocr

import android.content.Context
import android.graphics.Bitmap
import com.example.data.api.GeminiApiClient
import kotlinx.coroutines.CancellationException

/**
 * "정밀 분석" 경로: 사용자가 등록한 본인 Gemini 키로 사진을 Google Gemini에 보낸다(ADR-001 D, ADR-002 Q3).
 * 이 클래스를 호출하는 곳은 [com.example.ui.viewmodel.ReadingViewModel.processPreciseAnalysis] 하나이며,
 * 그 경로는 키 등록 + 사진 전송 동의가 모두 있을 때만 열린다. 기본 밑줄 인식 경로에서는 절대 쓰지 않는다.
 */
class GeminiTextExtractor(private val context: Context) : TextExtractor {
    override suspend fun extract(bitmap: Bitmap): OcrOutcome = try {
        val text = GeminiApiClient.extractUnderlinedText(context, bitmap)
        if (text.isBlank()) OcrOutcome.NoText else OcrOutcome.Text(text)
    } catch (e: CancellationException) {
        throw e
    } catch (e: IllegalStateException) {
        // 키 미등록/요청 실패 — GeminiApiClient가 사용자 문구를 담아 던진다
        OcrOutcome.Failed(e.message ?: "정밀 분석 요청이 실패했어요.")
    } catch (e: Exception) {
        OcrOutcome.Failed("정밀 분석 요청 중 오류가 났어요. 네트워크를 확인해 주세요.")
    }
}
