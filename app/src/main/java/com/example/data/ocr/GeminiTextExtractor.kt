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
    } catch (e: OutOfMemoryError) {
        // JPEG 압축·Base64·JSON 직렬화가 큰 임시 복사본을 만든다. Error라 일반 catch를 우회하므로 여기서 받는다
        OcrOutcome.Failed("메모리가 부족해 정밀 분석을 진행하지 못했어요. 더 작은 영역을 잘라 다시 시도해 주세요.")
    }
}
