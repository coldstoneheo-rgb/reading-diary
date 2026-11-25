package com.example.data.ocr

import com.example.data.ocr.UnderlineDetector.Box

/**
 * 앱이 밑줄 검출기를 부르는 유일한 입구(SPIKE §4-2 «미리 선택»). 순수 Kotlin — JVM에서 테스트하고,
 * 기준 자료 측정(GoldenSet.runSpike)도 이 함수를 그대로 불러 앱과 같은 값을 잰다.
 *
 * 흐름: 엔진 어절 상자 → 동떨어진 높이의 상자 빼기 → 글줄로 묶기 → 줄마다 기울기([UnderlineDetector.lineSlope]) →
 * [UnderlineDetector.detect] → 어절마다 «밑줄 구간과 절반 이상 겹침» 여부.
 *
 * **기울기는 항상 넘긴다.** 이 함수에는 기울기를 끄는 인자가 없다 — 기본 띠(K1a)는 기울기를 넘긴다는 전제로 고른 값이라
 * 기울기 없이 부르면 기준 사진 11장에서 정밀도 90.5% · 비침 오검출 4로 떨어진다(한 단계 문턱 0.28 때 값 — SPIKE §5).
 * 앱 코드에서 [UnderlineDetector.detect]를 직접 부르는 곳은 이 파일뿐이다(UnderlinePreselectTest.appPath_cannotCallTheDetectorWithoutSlope가 고정).
 */
object UnderlinePreselect {

    /** 묶은 글줄: 상자(위 끝 중앙값 + 높이 30백분위)와 그 줄 어절의 입력 순번(왼→오). */
    data class Line(val box: Box, val words: List<Int>)

    /**
     * @property selected 입력 어절 순번과 같은 길이. true = 밑줄 친 어절(미리 선택). 높이로 빠진 어절은 false.
     * @property lines 검출기에 넘긴 글줄(순번 = [UnderlineDetector.Run.lineIndex])
     * @property slopes 줄마다 넘긴 기울기([lines]와 같은 순번)
     */
    class Result(
        val selected: BooleanArray,
        val lines: List<Line>,
        val slopes: DoubleArray,
        val runs: List<UnderlineDetector.Run>
    )

    /** 인식 결과 어절 하나의 미리 선택 값. [lineIndex] = 엔진이 준 줄 순번(OcrOutcome.Text.lines). */
    data class Word(val text: String, val lineIndex: Int, val selected: Boolean)

    /** 인식 결과([lines], 같은 비트맵 좌표)의 어절마다 미리 선택 값 — 엔진 줄 순서 · 줄 안 어절 순서 그대로. */
    fun forLines(gray: IntArray, width: Int, height: Int, lines: List<OcrLine>): List<Word> {
        val flat = lines.withIndex().flatMap { (li, l) -> l.words.map { li to it } }
        if (flat.isEmpty()) return emptyList()
        val sel = select(gray, width, height, flat.map { it.second.box }).selected
        return flat.mapIndexed { i, (li, w) -> Word(w.text, li, sel[i]) }
    }

    /**
     * [gray]는 0..255 회색조, 행 우선([width]×[height]). [words]는 같은 비트맵 좌표의 어절 상자(엔진이 준 순서 그대로).
     */
    fun select(
        gray: IntArray, width: Int, height: Int, words: List<Box>,
        params: UnderlineDetector.Params = UnderlineDetector.Params()
    ): Result {
        val lines = clusterLines(words, keptWords(words))
        val slopes = DoubleArray(lines.size) { i -> UnderlineDetector.lineSlope(lines[i].words.map { words[it] }) }
        val runs = UnderlineDetector.detect(gray, width, height, lines.map { it.box }, params, slopes)
        val selected = BooleanArray(words.size)
        for ((li, line) in lines.withIndex()) {
            val lineRuns = runs.filter { it.lineIndex == li }
            if (lineRuns.isEmpty()) continue
            for (wi in line.words) {
                selected[wi] = UnderlineDetector.coverage(words[wi].left, words[wi].right, lineRuns) >= params.wordCoverage
            }
        }
        return Result(selected, lines, slopes, runs)
    }

    /** 줄 높이와 동떨어진 상자(잡티 · 여러 줄을 합친 상자)를 뺀 어절 순번: 높이가 중앙값의 0.4 ~ 2.4배인 것만. */
    fun keptWords(words: List<Box>): List<Int> {
        if (words.isEmpty()) return emptyList()
        val mh = median(words.map { it.height })
        return words.indices.filter { words[it].height >= 0.4 * mh && words[it].height <= 2.4 * mh }
    }

    /**
     * [kept] 어절을 글줄로 묶는다(세로 중심이 줄 중앙값에서 글자 높이 절반 안). 줄 아래 끝은 밑줄과 합쳐져 부푼 상자를
     * 피하려고 «위 끝 중앙값 + 높이 30백분위»로 잡는다. 엔진의 줄 묶음은 쓰지 않는다 — 측정(기준 자료)과 같은 값을 내기 위해.
     */
    fun clusterLines(words: List<Box>, kept: List<Int> = words.indices.toList()): List<Line> {
        fun cy(i: Int) = (words[i].top + words[i].bottom) / 2.0
        val sorted = kept.sortedBy { cy(it) }
        val groups = mutableListOf<MutableList<Int>>()
        for (i in sorted) {
            val g = groups.lastOrNull()
            if (g != null && cy(i) - medianD(g.map { cy(it) }) < 0.5 * median(g.map { words[it].height })) g += i
            else groups += mutableListOf(i)
        }
        return groups.map { g ->
            val ws = g.sortedBy { words[it].left }
            val top = median(ws.map { words[it].top })
            val hs = ws.map { words[it].height }.sorted()
            val h = hs[(hs.size * 0.3).toInt().coerceAtMost(hs.size - 1)]
            Line(Box(ws.minOf { words[it].left }, top, ws.maxOf { words[it].right }, top + h), ws)
        }
    }

    private fun median(v: List<Int>): Int = v.sorted()[v.size / 2]
    private fun medianD(v: List<Double>): Double = v.sorted()[v.size / 2]
}
