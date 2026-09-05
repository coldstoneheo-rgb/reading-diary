package com.example.data.ocr

/**
 * 인식 엔진이 돌려준 블록/줄을 사람이 읽는 순서의 문자열로 합친다(ADR-002 Q4).
 * ML Kit의 [com.google.mlkit.vision.text.Text]에 의존하지 않는 자체 표현이라 JVM 단위 테스트가 가능하다.
 */
data class OcrBlock(
    val lines: List<String>,
    /** 블록 바운딩박스의 위쪽 y(픽셀). 정렬 기준 1. */
    val top: Int,
    /** 블록 바운딩박스의 왼쪽 x(픽셀). 정렬 기준 2. */
    val left: Int
)

object OcrTextAssembler {
    /**
     * 규칙(회의 합의):
     * - 블록은 top → left 순으로 정렬한다(엔진이 읽기 순서를 보장하지 않으므로).
     * - 블록 안의 줄은 공백 하나로 잇는다(한국어 책은 어절 중간에서도 줄이 바뀌므로 줄바꿈을 살리지 않는다).
     * - 블록 사이는 빈 줄 하나로 나눈다.
     * - 신뢰도 필터·띄어쓰기 보정은 하지 않는다. 사용자가 저장 전 편집한다.
     */
    fun assemble(blocks: List<OcrBlock>): String =
        blocks
            .sortedWith(compareBy({ it.top }, { it.left }))
            .map { block -> block.lines.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ") }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")
}
