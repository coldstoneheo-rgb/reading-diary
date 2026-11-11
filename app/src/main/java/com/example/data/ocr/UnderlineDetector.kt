package com.example.data.ocr

/**
 * 밑줄·형광펜 검출 스파이크(설계 = 11-11 REPORT §11의 A + C). 순수 Kotlin — 안드로이드·OpenCV 의존 0, JVM에서 테스트한다.
 * 아직 화면·ViewModel에 연결하지 않는다. 판정은 레포 밖 기준 자료(docs/quality/GOLDEN-SET.md)의 일치율로 한다.
 *
 * A(밑줄): 각 글줄 상자 아래 «띠»(다음 줄 위까지)에서 열마다 종이보다 충분히 어두운 «가는» 획이 있는지 보고,
 * 그런 열이 글자 1.2개 폭 이상 이어지면 밑줄 구간으로 본다. 문턱은 같은 줄 글자 획의 어둡기(Δ_text)에 대한 비율이라
 * 사진마다 밝기가 달라도 같은 값을 쓴다. 뒷장 비침은 연필·볼펜 밑줄보다 옅어 이 비율에서 걸러진다(p.135 실측 0건).
 * 글자(다음 줄 글자 윗부분 등)는 세로로 두꺼워 «가는 획» 조건에서 빠진다.
 *
 * C(형광펜): 어절 상자 안에서 채도가 높고 밝은 픽셀의 비율.
 */
object UnderlineDetector {

    /** 픽셀 좌표 사각형. right·bottom은 포함하지 않는다. */
    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }

    /** 문턱은 모두 그 줄의 글자 높이(H) 또는 글자 획 어둡기(Δ_text)에 대한 비율. */
    data class Params(
        /** 띠의 열이 «어둡다» = (종이 − 그 열 최솟값) ≥ darkRatio × Δ_text. */
        val darkRatio: Double = 0.3,
        /** 띠 안에서 어두운 행이 이보다 많으면(× H) 글자 획으로 보고 버린다. */
        val maxThicknessRatio: Double = 0.25,
        /** 어두운 열 사이 이만큼(× H)까지의 빈틈은 이어 붙인다(흐린 연필·물결선). */
        val gapRatio: Double = 0.3,
        /** 이보다 짧은(× H) 구간은 버린다(점·쉼표·획 끝). */
        val minRunRatio: Double = 1.2,
        /** 띠 시작 = 줄 아래 + 이 비율 × H(글자 받침 끝 여유). */
        val bandStartRatio: Double = 0.08,
        /** 띠 최대 높이(× H). 다음 줄이 없을 때의 아래 한계이기도 하다. */
        val bandMaxRatio: Double = 1.0,
        /** 어절이 밑줄 구간과 이 비율 이상 겹치면 «밑줄 친 어절». */
        val wordCoverage: Double = 0.5
    )

    /** [lineIndex]번 줄 아래에서 찾은 밑줄 구간(가로 픽셀 [left, right)). */
    data class Run(val lineIndex: Int, val left: Int, val right: Int)

    /**
     * [gray]는 0..255 회색조, 행 우선([width]×[height]). [lines]는 글줄 상자(순서 무관 — 위→아래로 정렬해 쓴다).
     * 돌려주는 [Run.lineIndex]는 [lines]의 원래 순번.
     */
    fun detect(gray: IntArray, width: Int, height: Int, lines: List<Box>, params: Params = Params()): List<Run> {
        require(gray.size == width * height) { "gray size" }
        val order = lines.indices.sortedBy { lines[it].top }
        val runs = mutableListOf<Run>()
        for ((k, idx) in order.withIndex()) {
            val line = lines[idx]
            val h = line.height
            if (h <= 0 || line.width <= 0) continue
            val nextTop = order.getOrNull(k + 1)?.let { lines[it].top } ?: Int.MAX_VALUE
            val y0 = (line.bottom + (params.bandStartRatio * h).toInt()).coerceIn(0, height)
            val y1 = minOf(
                nextTop.toLong() - (params.bandStartRatio * h).toLong(),
                line.bottom.toLong() + (params.bandMaxRatio * h).toLong(),
                height.toLong()
            ).toInt()
            if (y1 - y0 < 6) continue
            val x0 = (line.left - h / 2).coerceIn(0, width)
            val x1 = (line.right + h / 2).coerceIn(0, width)
            if (x1 - x0 < 2) continue

            val paper = percentile(sample(gray, width, line.left.coerceIn(0, width), y0, line.right.coerceIn(0, width), y1, 3), 0.5)
            val ink = percentile(sample(gray, width, line.left.coerceIn(0, width), line.top.coerceIn(0, height),
                line.right.coerceIn(0, width), line.bottom.coerceIn(0, height), 2), 0.02)
            val deltaText = maxOf(1, paper - ink).toDouble()
            val maxThick = params.maxThicknessRatio * h

            val dark = BooleanArray(x1 - x0)
            for (x in x0 until x1) {
                var min = Double.MAX_VALUE
                var thick = 0
                for (y in (y0 + 1) until (y1 - 1)) {
                    // 세로 3픽셀 평균 — 종이 결·잡티 한 점을 획으로 잡지 않는다.
                    val v = (gray[(y - 1) * width + x] + gray[y * width + x] + gray[(y + 1) * width + x]) / 3.0
                    if (v < min) min = v
                    if ((paper - v) / deltaText >= params.darkRatio) thick++
                }
                dark[x - x0] = (paper - min) / deltaText >= params.darkRatio && thick <= maxThick
            }

            val gap = params.gapRatio * h
            val minLen = params.minRunRatio * h
            var start = -1
            var last = -1
            fun flush() {
                if (start >= 0 && last - start + 1 >= minLen) runs += Run(idx, x0 + start, x0 + last + 1)
            }
            for (i in dark.indices) {
                if (!dark[i]) continue
                if (start < 0 || i - last > gap) {
                    flush()
                    start = i
                }
                last = i
            }
            flush()
        }
        return runs
    }

    /** [words]의 각 상자가 같은 줄([wordLine])의 밑줄 구간과 [Params.wordCoverage] 이상 겹치는지. */
    fun underlinedWords(words: List<Box>, wordLine: List<Int>, runs: List<Run>, params: Params = Params()): BooleanArray {
        require(words.size == wordLine.size)
        return BooleanArray(words.size) { i -> coverage(words[i].left, words[i].right, runs.filter { it.lineIndex == wordLine[i] }) >= params.wordCoverage }
    }

    /** [left, right) 가운데 [runs]가 덮는 비율(0..1). */
    fun coverage(left: Int, right: Int, runs: List<Run>): Double {
        if (right <= left) return 0.0
        var covered = 0
        for (x in left until right) if (runs.any { x >= it.left && x < it.right }) covered++
        return covered.toDouble() / (right - left)
    }

    /**
     * 형광펜(C): [argb] 사진에서 상자 안 픽셀 가운데 «밝고 채도 높은» 비율이 [minFraction] 이상이면 칠한 것으로 본다.
     * 채도 = (max − min) / max, 밝기 = max. 검은 글자 획(밝기 낮음)과 흰 종이(채도 낮음)는 세지 않는다.
     */
    fun highlightedWords(
        argb: IntArray, width: Int, words: List<Box>,
        minSaturation: Double = 0.25, minValue: Int = 120, minFraction: Double = 0.4
    ): BooleanArray = BooleanArray(words.size) { i ->
        val b = words[i]
        var colored = 0
        var total = 0
        for (y in b.top until b.bottom) for (x in b.left until b.right) {
            val p = argb[y * width + x]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val bl = p and 0xFF
            val max = maxOf(r, g, bl)
            val min = minOf(r, g, bl)
            total++
            if (max >= minValue && max > 0 && (max - min).toDouble() / max >= minSaturation) colored++
        }
        total > 0 && colored.toDouble() / total >= minFraction
    }

    /** ARGB → 회색조(0..255, ITU-R BT.601 가중치). */
    fun toGray(argb: IntArray): IntArray = IntArray(argb.size) {
        val p = argb[it]
        (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
    }

    private fun sample(gray: IntArray, width: Int, l: Int, t: Int, r: Int, b: Int, step: Int): IntArray {
        val out = ArrayList<Int>()
        var y = t
        while (y < b) {
            var x = l
            while (x < r) { out += gray[y * width + x]; x += step }
            y++
        }
        return out.toIntArray()
    }

    private fun percentile(values: IntArray, q: Double): Int {
        if (values.isEmpty()) return 255
        values.sort()
        return values[((values.size - 1) * q).toInt()]
    }
}
