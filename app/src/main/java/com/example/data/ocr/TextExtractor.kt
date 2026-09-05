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
    /** 인식된 텍스트(비어 있지 않음). */
    data class Text(val text: String) : OcrOutcome()

    /** 인식은 됐지만 글자를 찾지 못함. */
    object NoText : OcrOutcome()

    /** 엔진이 동작하지 못함(모델 미준비, Play 서비스 없음, 내부 오류 등). 사용자에게 보여줄 메시지 포함. */
    data class Failed(val message: String) : OcrOutcome()
}
