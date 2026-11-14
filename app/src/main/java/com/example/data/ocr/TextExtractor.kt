package com.example.data.ocr

import android.graphics.Bitmap

/**
 * 이미지에서 텍스트를 추출하는 엔진의 경계. ViewModel은 이 인터페이스만 본다(ADR-002).
 * 구현체 교체(ML Kit ↔ 다른 엔진 ↔ 테스트용 Fake)가 ViewModel·화면에 영향을 주지 않는다.
 */
interface TextExtractor {
    suspend fun extract(bitmap: Bitmap): OcrOutcome
}

/** 추출 결과. 가짜 문장으로 채우지 않고 세 상태를 그대로 드러낸다. */
sealed class OcrOutcome {
    /**
     * 인식된 텍스트(비어 있지 않음). [lines] = 엔진이 준 줄·어절 상자(입력 비트맵 좌표, SPIKE §4-1).
     * 상자를 주지 않는 엔진(정밀 분석 등)은 빈 목록 — 그때는 밑줄 미리 선택을 하지 않는다.
     */
    data class Text(val text: String, val lines: List<OcrLine> = emptyList()) : OcrOutcome()

    /** 인식은 됐지만 글자를 찾지 못함. */
    object NoText : OcrOutcome()

    /** 엔진이 동작하지 못함(모델 미준비, Play 서비스 없음, 내부 오류 등). 사용자에게 보여줄 메시지 포함. */
    data class Failed(val message: String) : OcrOutcome()
}

/** 인식된 어절 하나와 그 상자(입력 비트맵 좌표, right·bottom 미포함). */
data class OcrWord(val text: String, val box: UnderlineDetector.Box)

/** 인식된 글줄 하나. [box]는 엔진이 상자를 주지 않으면 null. [words]는 상자가 있는 어절만. */
data class OcrLine(val text: String, val box: UnderlineDetector.Box?, val words: List<OcrWord>)
