package com.example.debugtools

import kotlin.math.roundToInt

/**
 * 디버그 빌드 전용(src/debug). ML Kit 줄·어절 상자를 기준 자료 측정(docs/quality/GOLDEN-SET.md §4)이 읽는
 * Tesseract `tsv` 열로 옮긴다. 순수 함수 — 안드로이드 의존 0, JVM에서 테스트한다.
 *
 * 좌표는 OCR 입력 비트맵(다운샘플 + 자르기)이 아니라 **앱이 세운 원본 해상도** 좌표로 되돌려 적는다:
 * 원본 픽셀 → EXIF 회전 → 사용자 회전 → 다운샘플(긴 변 ≤ 2048) → 자르기 를 거꾸로 — 자르기 오프셋을 더하고 배율을 곱한다.
 * 측정 코드(#54 GoldenSet)는 EXIF를 읽지 않고 원본 픽셀을 정답 머리 `rotate:`만큼 돌린다. 그래서 좌표가 맞는 조건은
 * `rotate ≡ EXIF 각도 + 사용자 회전 (mod 360)` 이고 EXIF 미러·사용자 반전이 없을 때다. 머리 `# expects golden rotate:`에 그 값을 적는다.
 */
object OcrBoxDumpTsv {

    /** 비트맵 좌표 사각형. right·bottom 미포함. */
    data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int)

    data class Element(val text: String, val box: Rect?)
    data class Line(val text: String, val box: Rect?, val elements: List<Element>)
    data class Block(val box: Rect?, val lines: List<Line>)

    /**
     * OCR 입력이 어떻게 만들어졌나. 크기는 픽셀.
     * @param sourceWidth/sourceHeight EXIF 반영 원본 크기(사용자 회전 전)
     * @param rotationDegrees 사용자 회전(시계 방향)
     * @param crop 자르기 비율 l,t,r,b(0..1)
     * @param bitmapWidth/bitmapHeight ML Kit에 넣은 비트맵(자른 뒤) 크기
     * @param exifOrientation EXIF Orientation 태그 값(1 = 정상). 측정 쪽 `rotate:` 대조용
     * @param exifDegrees/exifMirrored 그 태그의 회전 각도 · 미러 성분(BitmapDecoding.exifDegrees/exifMirrored)
     * @param sourceKnown 원본 크기를 읽었나. false면 비트맵 크기로 대신해 좌표가 원본과 어긋난다(NOT EXACT)
     */
    data class Input(
        val sourceWidth: Int, val sourceHeight: Int,
        val rotationDegrees: Float, val flipped: Boolean,
        val cropLeft: Float, val cropTop: Float, val cropRight: Float, val cropBottom: Float,
        val bitmapWidth: Int, val bitmapHeight: Int,
        val exifOrientation: Int = 1, val exifDegrees: Int = 0, val exifMirrored: Boolean = false,
        val sourceKnown: Boolean = true
    )

    /** 비트맵 좌표 → 원본 좌표 변환(자르기 오프셋 [offsetX]/[offsetY]는 다운샘플 좌표, 배율 [scaleX]/[scaleY]). */
    data class Mapping(
        val offsetX: Int, val offsetY: Int, val scaleX: Double, val scaleY: Double,
        /** 원본 좌표로 되돌릴 수 있나. 90° 배수가 아닌 회전 · 반전 · EXIF 미러 · 원본 크기 모름이면 false(좌표는 적지만 측정과 어긋난다). */
        val exact: Boolean,
        val uprightWidth: Int, val uprightHeight: Int
    ) {
        fun map(r: Rect): Rect = Rect(
            ((offsetX + r.left) * scaleX).roundToInt(), ((offsetY + r.top) * scaleY).roundToInt(),
            ((offsetX + r.right) * scaleX).roundToInt(), ((offsetY + r.bottom) * scaleY).roundToInt()
        )
    }

    fun mapping(i: Input): Mapping {
        val quarter = ((i.rotationDegrees % 360f + 360f) % 360f)
        val rightAngle = quarter % 90f == 0f
        val swap = rightAngle && (quarter == 90f || quarter == 270f)
        val uw = if (swap) i.sourceHeight else i.sourceWidth
        val uh = if (swap) i.sourceWidth else i.sourceHeight
        // 자르기 전 다운샘플 비트맵 크기: 비트맵 ÷ 자르기 비율. 기본 틀(사진 전체)이면 비트맵 그대로라 오차 0.
        val cw = (i.cropRight - i.cropLeft).coerceAtLeast(1e-6f)
        val ch = (i.cropBottom - i.cropTop).coerceAtLeast(1e-6f)
        val tw = if (cw >= 0.9999f) i.bitmapWidth else (i.bitmapWidth / cw).roundToInt()
        val th = if (ch >= 0.9999f) i.bitmapHeight else (i.bitmapHeight / ch).roundToInt()
        // BitmapDecoding.cropRect와 같은 내림.
        val ox = (i.cropLeft * tw).toInt().coerceIn(0, (tw - 1).coerceAtLeast(0))
        val oy = (i.cropTop * th).toInt().coerceIn(0, (th - 1).coerceAtLeast(0))
        return Mapping(
            ox, oy,
            if (tw > 0) uw.toDouble() / tw else 1.0,
            if (th > 0) uh.toDouble() / th else 1.0,
            exact = rightAngle && !i.flipped && !i.exifMirrored && i.sourceKnown,
            uprightWidth = uw, uprightHeight = uh
        )
    }

    /** 측정 쪽 정답 머리 `rotate:`가 이 값이어야 좌표가 맞는다(EXIF 각도 + 사용자 회전, 0..359). */
    fun expectedGoldenRotate(i: Input): Int = ((i.exifDegrees + i.rotationDegrees.roundToInt()) % 360 + 360) % 360

    const val COLUMNS = "level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext"

    /**
     * TSV 한 파일. 머리 `#` 줄(측정 코드는 열이 12개 아닌 줄을 건너뛴다) → 열 이름 → 블록(2)·줄(4)·어절(5) 행.
     * conf는 모름 = -1(측정 코드는 «신뢰도 없음 = 통과»로 읽는다). 상자 없는 요소는 건너뛴다.
     */
    fun format(blocks: List<Block>, input: Input, createdAt: String, engine: String): String {
        val m = mapping(input)
        val sb = StringBuilder()
        sb.append("# source: $engine\n")
        sb.append("# created: $createdAt\n")
        sb.append("# coords: upright source pixels ${m.uprightWidth}x${m.uprightHeight}${if (m.exact) "" else " (NOT EXACT: non-right-angle rotation, flip, EXIF mirror or unknown source size)"}\n")
        sb.append("# exif: orientation ${input.exifOrientation} (deg ${input.exifDegrees}, mirror ${input.exifMirrored})\n")
        sb.append("# expects golden rotate: ${expectedGoldenRotate(input)}\n")
        sb.append("# input: source ${input.sourceWidth}x${input.sourceHeight} rotate ${fmt(input.rotationDegrees)} flip ${input.flipped} ")
        sb.append("crop ${fmt(input.cropLeft)},${fmt(input.cropTop)},${fmt(input.cropRight)},${fmt(input.cropBottom)} ")
        sb.append("bitmap ${input.bitmapWidth}x${input.bitmapHeight}\n")
        sb.append("# mapping: offset ${m.offsetX},${m.offsetY} scale ${fmt(m.scaleX)},${fmt(m.scaleY)}\n")
        sb.append(COLUMNS).append('\n')
        for ((bi, b) in blocks.withIndex()) {
            val bn = bi + 1
            b.box?.let { row(sb, 2, bn, 0, 0, m.map(it), "") }
            for ((li, l) in b.lines.withIndex()) {
                val ln = li + 1
                l.box?.let { row(sb, 4, bn, ln, 0, m.map(it), "") }
                for ((ei, e) in l.elements.withIndex()) {
                    val box = e.box ?: continue
                    row(sb, 5, bn, ln, ei + 1, m.map(box), clean(e.text))
                }
            }
        }
        return sb.toString()
    }

    private fun row(sb: StringBuilder, level: Int, block: Int, line: Int, word: Int, r: Rect, text: String) {
        sb.append(level).append("\t1\t").append(block).append("\t1\t").append(line).append('\t').append(word).append('\t')
            .append(r.left).append('\t').append(r.top).append('\t').append(r.right - r.left).append('\t').append(r.bottom - r.top)
            .append("\t-1\t").append(text).append('\n')
    }

    /** 탭·줄바꿈이 열을 깨지 않게 공백으로. */
    fun clean(s: String): String = s.replace(Regex("[\\t\\r\\n]+"), " ").trim()

    private fun fmt(v: Float): String = if (v == v.toInt().toFloat()) v.toInt().toString() else "%.4f".format(java.util.Locale.ROOT, v)
    private fun fmt(v: Double): String = "%.4f".format(java.util.Locale.ROOT, v)
}
