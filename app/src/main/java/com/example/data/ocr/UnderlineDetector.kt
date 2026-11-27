package com.example.data.ocr

/**
 * 밑줄·형광펜 검출 스파이크(설계 = 11-11 REPORT §11의 A + C). 순수 Kotlin — 안드로이드·OpenCV 의존 0, JVM에서 테스트한다.
 * 앱은 [UnderlinePreselect]로만 부른다. 판정은 레포 밖 기준 자료(docs/quality/GOLDEN-SET.md)의 일치율로 한다.
 *
 * A(밑줄): 각 글줄 상자 아래 «띠»(다음 줄 위까지)에서 열마다 종이보다 충분히 어두운 «가는» 획이 있는지 보고,
 * 그런 열이 글자 1.2개 폭 이상 이어지고 그 안에 뚜렷한 열이 충분하면(두 단계 문턱) 밑줄 구간으로 본다. 문턱은 같은 줄 글자 획의 어둡기(Δ_text)에 대한 비율이라
 * 사진마다 밝기가 달라도 같은 값을 쓴다. 뒷장 비침은 연필·볼펜 밑줄보다 옅어 이 비율에서 걸러진다(기준 사진 1번 실측 0건).
 * 글자(다음 줄 글자 윗부분 등)는 세로로 두꺼워 «가는 획» 조건에서 빠진다.
 * 기운 줄은 호출하는 쪽이 줄마다 [lineSlope](어절 상자 아래 끝의 직선 맞춤)를 넘겨 띠를 기울여 읽는다 — 기본 띠 값(K1a)은
 * 기울기를 넘긴다는 전제로 고른 값이다.
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
        /**
         * 띠의 열이 «어둡다» = (종이 − 그 열 최솟값) ≥ darkRatio × Δ_text. 구간을 잇는 낮은 문턱이고, 구간을 받아들일지는
         * [seedRatio] · [seedFraction]이 정한다(두 단계 문턱). 0.23 = 2026-11-25 기준 사진 11장(앱 해상도 · 앱 어절 판정)에서
         * «오검출 ≤ 5 · 비침 0 · 본문 밖 선택 ≤ 3 · 본문 줄 안 정답 밖 선택 ≤ 3 · 장별 맞음 하락 0»을 지키는 가장 낮은 단계
         * (0.22는 정답 밖 선택 4). 한 단계 문턱이던 2026-11-14 값은 0.28.
         */
        val darkRatio: Double = 0.23,
        /** 뚜렷한 열 = 어두운 열(가는 획 포함) 가운데 (종이 − 그 열 최솟값) ≥ seedRatio × Δ_text. */
        val seedRatio: Double = 0.28,
        /** 구간 가로 폭 가운데 뚜렷한 열([seedRatio])이 이 비율 이상일 때만 밑줄 구간으로 받아들인다. 0 = 한 단계 문턱(검사 안 함). */
        val seedFraction: Double = 0.2,
        /**
         * 띠 안에서 어두운 행([darkRatio] 기준)이 이보다 많으면(× H) 글자 획으로 보고 버린다. 낮은 문턱으로 세므로 종이가 고르지 않게
         * 어두운 곳(제본 그늘, 종이보다 0.23Δ 이상)에서는 뚜렷한 밑줄도 버릴 수 있다 — 0.28로 세면 본문 밖 선택이 상한 3을 넘어(레포 밖 포트 3 → 5) 낮은 문턱을 쓴다.
         */
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
        val wordCoverage: Double = 0.5,
        /**
         * 줄 글자 획 어둡기(Δ_text = 종이 − 줄 2백분위)가 이보다 작으면 그 줄은 건너뛴다(0..255 회색조 단위).
         * 글자가 없는 빈 상자·잡티 상자에서 Δ_text가 1 근처가 되면 종이 결까지 «어둡다»가 되어 오검출이 나기 때문.
         */
        val minDeltaText: Int = 40,
        /**
         * 띠 시작 = 줄 아래 + 이 비율 × H. null이면 [bandStartRatio](음수 = 줄 상자 아래 끝보다 위에서 시작).
         * 기본 −0.02 = 기기 ML Kit 줄 상자 아래 끝이 글자 잉크 아래 끝보다 아래에 있어서(같은 장 실측 — K1a, SPIKE §1-0-1).
         */
        val bandTopRatio: Double? = -0.02,
        /**
         * 띠 끝 = 다음 줄 위 − 이 비율 × H. null이면 [bandStartRatio](음수 = 다음 줄 위 끝을 조금 넘어간다).
         * 기본 −0.025 = 기기 ML Kit 줄 상자 위 끝이 잉크보다 위에 있어서(K1a). 기울기([detect]의 slopes)와 함께 쓴다 —
         * 기울기 없이 띠만 넓히면 기운 줄에서 다음 줄 윗획을 밑줄로 잡는다(한 단계 문턱 0.28 때 11장 정밀도 90%).
         */
        val nextLinePadRatio: Double? = -0.025,
        /**
         * 열마다 세로 3픽셀 평균(종이 결 · 잡티 거르기)은 줄 높이 H(px)가 이 값 이상일 때만 쓰고, 낮으면 1픽셀을 그대로 읽는다.
         * 앱은 긴 변 ≤ 2048(기준 사진의 1/2)에서 부르는데, 거기서는 2~3픽셀 연필 선이 1픽셀 남짓이 되어 3행 평균에 묽어진다
         * (SPIKE §1-0-3: 평균을 끄면 앱 해상도 재현율 64% → 69%). 연필 선 두께가 줄 높이에 비례한다고 보고 H를 배율의 대리값으로 쓴다.
         * 60 = 원본 해상도 본문 줄(H ≈ 100)은 그대로 두고
         * 앱 해상도 본문 줄(대부분 H < 60, 일부 55~59)만 1픽셀로 읽는 값. 잰 값(한 단계 문턱 0.28 때 · 두 단계 문턱에서는 0만 다시 쟀고(SPIKE §1-0-5) 경계값은 다시 재지 않음 · 앱 해상도 맞음/오검출/놓침): 50 → 178/5/93 ·
         * 55 → 184/5/87 · 60 · 65 → 188/5/83 — 60은 앱 맞음 188을 얻는 잰 범위(60~65)의 아래 끝이다(원본은 50~65 모두 장별 같음).
         * 70은 재지 않았다(75 · 90은 커밋 전 실험 코드의 참고값: 원본 본문 밖 오검출 9 → 12 · 14).
         * 앱 쪽 여유는 약 5px(더 크게 찍은 사진은 H가 60을 넘어 다시 평균된다).
         * 1픽셀 모드에서도 띠 양 끝 1행은 읽지 않는다(3행 평균용 여백) — 앱 해상도에서 K1a 띠 확장은 사실상 0이다(기준 자료 11장: 원본 192/7/79 불변 · 앱 174 → 188어절). 0 = 늘 3픽셀.
         */
        val smoothMinHeight: Int = 60,
        /**
         * 열별 종이 밝기(HO-2026-1127 검출기 수리): «어둡다»를 띠 전체 종이(중앙값) 대신 **그 열 둘레의 종이**에 대해 잰다.
         * 열마다 띠 안 세로 중앙값(밑줄 획은 띠 높이의 일부라 중앙값은 종이)을 구하고, 가로 ±[localPaperRadiusRatio] × H 안에서
         * 그 중앙값들의 중앙값을 그 열의 종이로 쓴다. 조명이 줄 왼쪽 → 오른쪽으로 기울어 띠 끝이 어두운 사진에서 줄 끝 어절이
         * 밑줄로 켜지던 과선택을 막는다. 0 = 끔(띠 전체 중앙값 하나 — 수리 전 동작).
         */
        val localPaperRadiusRatio: Double = 3.0,
        /** 열별 종이의 세로 분위(0.5 = 중앙값). 띠가 낮아 밑줄 · 표시 획이 띠 높이의 큰 몫을 차지해도 종이를 고르도록 높일 수 있다. */
        val localPaperQuantile: Double = 0.75
    )

    /** [lineIndex]번 줄 아래에서 찾은 밑줄 구간(가로 픽셀 [left, right)). */
    data class Run(val lineIndex: Int, val left: Int, val right: Int)

    /**
     * [gray]는 0..255 회색조, 행 우선([width]×[height]). [lines]는 글줄 상자(순서 무관 — 위→아래로 정렬해 쓴다).
     * 돌려주는 [Run.lineIndex]는 [lines]의 원래 순번.
     * [slopes]가 있으면 [lines]와 같은 순번으로 줄 기울기(dy/dx, [lineSlope])를 받아 띠를 그 기울기로 따라 읽는다
     * (줄 가로 가운데 기준, 0이면 수평). null이면 모든 줄을 수평으로 읽는다 — 기본 띠(K1a)와 null을 함께 쓰면
     * 기준 사진 11장에서 정밀도 90.5% · 비침 오검출 4로 떨어진다(한 단계 문턱 0.28 때 값). 앱에서 부를 때는 반드시 기울기를 넘긴다.
     * [slopes]는 기본값이 없다 — 기울기를 «빠뜨리는» 호출은 컴파일되지 않고, 수평으로 읽으려면 `null`을 적어야 한다(측정 · 테스트만).
     * 앱 경로는 [UnderlinePreselect.select]만 이 함수를 부른다.
     */
    fun detect(
        gray: IntArray, width: Int, height: Int, lines: List<Box>, params: Params = Params(), slopes: DoubleArray?
    ): List<Run> {
        require(gray.size == width * height) { "gray size" }
        require(slopes == null || slopes.size == lines.size) { "slopes size" }
        val order = lines.indices.sortedBy { lines[it].top }
        val runs = mutableListOf<Run>()
        for ((k, idx) in order.withIndex()) {
            val line = lines[idx]
            val h = line.height
            if (h <= 0 || line.width <= 0) continue
            val x0 = (line.left - h / 2).coerceIn(0, width)
            val x1 = (line.right + h / 2).coerceIn(0, width)
            if (x1 - x0 < 2) continue
            // 띠를 막는 «다음 줄» = 아래에 있으면서 띠의 가로 범위와 겹치는 첫 줄. 옆 단·여백 메모처럼 가로로 비켜 난 줄은 띠를 자르지 않는다.
            val nextTop = order.drop(k + 1).map { lines[it] }
                .firstOrNull { it.left < x1 && it.right > x0 }?.top ?: Int.MAX_VALUE
            val y0 = (line.bottom + ((params.bandTopRatio ?: params.bandStartRatio) * h).toInt()).coerceIn(0, height)
            val y1 = minOf(
                nextTop.toLong() - ((params.nextLinePadRatio ?: params.bandStartRatio) * h).toLong(),
                line.bottom.toLong() + (params.bandMaxRatio * h).toLong(),
                height.toLong()
            ).toInt()
            if (y1 - y0 < 6) continue

            val paper = percentile(sample(gray, width, line.left.coerceIn(0, width), y0, line.right.coerceIn(0, width), y1, 3), 0.5)
            val ink = percentile(sample(gray, width, line.left.coerceIn(0, width), line.top.coerceIn(0, height),
                line.right.coerceIn(0, width), line.bottom.coerceIn(0, height), 2), 0.02)
            if (paper - ink < params.minDeltaText) continue
            val deltaText = (paper - ink).toDouble()
            val maxThick = params.maxThicknessRatio * h

            val slope = slopes?.get(idx) ?: 0.0
            val cx = (line.left + line.right) / 2.0
            val smooth = h >= params.smoothMinHeight
            val colPaper = if (params.localPaperRadiusRatio > 0) localPaper(gray, width, height, x0, x1, y0, y1, slope, cx, (params.localPaperRadiusRatio * h).toInt().coerceAtLeast(1), params.localPaperQuantile) else null
            val dark = BooleanArray(x1 - x0)
            val seed = BooleanArray(x1 - x0)
            for (x in x0 until x1) {
                val pp = colPaper?.get(x - x0)?.toDouble() ?: paper.toDouble()
                var min = Double.MAX_VALUE
                var thick = 0
                // 기운 줄: 띠를 줄 가운데에서 벗어난 만큼 위아래로 옮겨 읽는다(반올림은 짝수 쪽 — 측정 포트와 같은 값).
                val off = if (slope == 0.0) 0 else Math.rint((x - cx) * slope).toInt()
                for (y in (y0 + 1) until (y1 - 1)) {
                    val yy = y + off
                    // 세로 3픽셀 평균(줄 높이 ≥ smoothMinHeight일 때만) — 종이 결·잡티 한 점을 획으로 잡지 않는다.
                    // 사진 밖으로 옮겨지면 종이(255)로 본다.
                    val v = if (yy < 1 || yy > height - 2) 255.0
                    else if (!smooth) gray[yy * width + x].toDouble()
                    else (gray[(yy - 1) * width + x] + gray[yy * width + x] + gray[(yy + 1) * width + x]) / 3.0
                    if (v < min) min = v
                    if ((pp - v) / deltaText >= params.darkRatio) thick++
                }
                dark[x - x0] = (pp - min) / deltaText >= params.darkRatio && thick <= maxThick
                seed[x - x0] = dark[x - x0] && (pp - min) / deltaText >= params.seedRatio
            }

            val gap = params.gapRatio * h
            val minLen = params.minRunRatio * h
            var start = -1
            var last = -1
            fun flush() {
                if (start < 0 || last - start + 1 < minLen) return
                val seeds = (start..last).count { seed[it] }
                if (seeds >= params.seedFraction * (last - start + 1)) runs += Run(idx, x0 + start, x0 + last + 1)
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

    /**
     * 줄 기울기(dy/dx): 같은 줄 어절 상자 아래 끝을 어절 가로 가운데에 대해 최소제곱 직선으로 맞춘 기울기.
     * 어절이 3개 미만이거나 |기울기| ≥ [maxAbs]이면(줄 묶기 실수로 보고) 0.
     */
    fun lineSlope(words: List<Box>, maxAbs: Double = 0.15): Double {
        if (words.size < 3) return 0.0
        val xs = words.map { (it.left + it.right) / 2.0 }
        val ys = words.map { it.bottom.toDouble() }
        val mx = xs.average()
        val my = ys.average()
        var sxy = 0.0
        var sxx = 0.0
        for (i in xs.indices) { sxy += (xs[i] - mx) * (ys[i] - my); sxx += (xs[i] - mx) * (xs[i] - mx) }
        if (sxx == 0.0) return 0.0
        val s = sxy / sxx
        return if (kotlin.math.abs(s) < maxAbs) s else 0.0
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
     * 상자가 사진 밖으로 삐져나가면(엔진 상자가 가장자리를 넘는 일이 있다) 사진 안쪽 부분만 센다. 안쪽이 0이면 false.
     * 한계: 비율만 보므로 붉은·푸른 컬러 인쇄 글자가 상자의 [minFraction] 이상을 덮으면(굵은 큰 글씨) 형광펜으로 본다.
     */
    fun highlightedWords(
        argb: IntArray, width: Int, height: Int, words: List<Box>,
        minSaturation: Double = 0.25, minValue: Int = 120, minFraction: Double = 0.4
    ): BooleanArray {
        require(argb.size == width * height) { "argb size" }
        return BooleanArray(words.size) { i -> highlighted(argb, width, height, words[i], minSaturation, minValue, minFraction) }
    }

    private fun highlighted(
        argb: IntArray, width: Int, height: Int, b: Box,
        minSaturation: Double, minValue: Int, minFraction: Double
    ): Boolean {
        val l = b.left.coerceIn(0, width)
        val r = b.right.coerceIn(0, width)
        val t = b.top.coerceIn(0, height)
        val bt = b.bottom.coerceIn(0, height)
        var colored = 0
        var total = 0
        for (y in t until bt) for (x in l until r) {
            val p = argb[y * width + x]
            val red = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val bl = p and 0xFF
            val max = maxOf(red, g, bl)
            val min = minOf(red, g, bl)
            total++
            if (max >= minValue && max > 0 && (max - min).toDouble() / max >= minSaturation) colored++
        }
        return total > 0 && colored.toDouble() / total >= minFraction
    }

    /** ARGB → 회색조(0..255, ITU-R BT.601 가중치). */
    fun toGray(argb: IntArray): IntArray = IntArray(argb.size) {
        val p = argb[it]
        (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
    }

    /**
     * 열별 종이 밝기([Params.localPaperRadiusRatio]): 열마다 띠 안(기울기를 따라 옮긴 행) 세로 [q] 분위 → 가로 ±[radius] 열의 중앙값.
     * 사진 밖으로 옮겨진 행은 건너뛴다(값이 하나도 없으면 255).
     */
    private fun localPaper(gray: IntArray, width: Int, height: Int, x0: Int, x1: Int, y0: Int, y1: Int, slope: Double, cx: Double, radius: Int, q: Double): IntArray {
        val col = IntArray(x1 - x0)
        val buf = IntArray(maxOf(1, y1 - y0))
        for (x in x0 until x1) {
            val off = if (slope == 0.0) 0 else Math.rint((x - cx) * slope).toInt()
            var n = 0
            for (y in y0 until y1) {
                val yy = y + off
                if (yy in 0 until height) buf[n++] = gray[yy * width + x]
            }
            col[x - x0] = if (n == 0) 255 else buf.copyOf(n).also { it.sort() }[((n - 1) * q).toInt()]
        }
        val out = IntArray(col.size)
        for (i in col.indices) {
            val a = maxOf(0, i - radius)
            val b = minOf(col.size, i + radius + 1)
            out[i] = col.copyOfRange(a, b).also { it.sort() }[(b - a - 1) / 2]
        }
        return out
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
