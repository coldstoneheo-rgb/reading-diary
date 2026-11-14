package com.example.golden

import com.example.data.ocr.UnderlineDetector
import com.example.data.ocr.UnderlinePreselect

/**
 * 앱 방식 어절 판정으로 밑줄 검출을 잰다. [GoldenSet.runSpike]는 정답 글을 글자 폭 비례로 줄 상자에 나눠 어절 위치를 짐작하지만,
 * 앱은 인식 엔진이 준 **어절 상자 그대로** [UnderlinePreselect.select]의 `selected`를 쓴다(밑줄 구간과 절반 이상 겹침).
 * 여기서는 그 `selected`를 그대로 받고, 엔진 어절 ↔ 정답 어절을 글자 맞춤(편집 거리 역추적)으로 이어 채점한다.
 *
 * 채점 단위는 정답 어절(지금 표와 같은 분모 271). 정답 어절 하나는 «맞춰진 글자의 절반 이상이 미리 선택된 엔진 어절 글자»일 때
 * 미리 선택된 것으로 본다 — 엔진이 두 정답 어절을 한 어절로 붙이면 화면에서도 둘이 함께 선택되므로 둘 다 선택으로 센다.
 * 정답 줄에 대응되지 않은 엔진 줄(머리말 · 쪽 번호 · 여백 메모 등 = 본문 밖)에서 미리 선택된 엔진 어절은 따로 센다.
 */
object AppWordMeasure {

    /** 줄 제외 후보(측정 전용 — 앱에는 아직 없다). [lines] 앱이 묶은 글줄, [i] 볼 줄, [h] 비트맵 높이. */
    data class LineFilter(val name: String, val drop: (lines: List<UnderlinePreselect.Line>, i: Int, h: Int) -> Boolean)

    private fun byY(lines: List<UnderlinePreselect.Line>) = lines.indices.sortedBy { lines[it].box.top }
    private fun medianWidth(lines: List<UnderlinePreselect.Line>) = lines.map { it.box.width }.sorted()[lines.size / 2]

    /** 맨 위 · 맨 아래 줄인가(세로 순서). */
    private fun isEdge(lines: List<UnderlinePreselect.Line>, i: Int): Boolean {
        val o = byY(lines)
        return i == o.first() || i == o.last()
    }

    /** 맨 위 · 맨 아래 줄이 이웃 줄과 중앙 줄 간격의 1.5배 넘게 떨어졌는가. */
    private fun isDetachedEdge(lines: List<UnderlinePreselect.Line>, i: Int): Boolean {
        if (lines.size < 3) return false
        val o = byY(lines)
        val gaps = o.zipWithNext { a, b -> lines[b].box.top - lines[a].box.top }
        val med = gaps.sorted()[gaps.size / 2]
        return when (i) {
            o.first() -> gaps.first() > 1.5 * med
            o.last() -> gaps.last() > 1.5 * med
            else -> false
        }
    }

    val FILTERS: List<LineFilter> = listOf(
        LineFilter("제외 없음(지금 앱)") { _, _, _ -> false },
        LineFilter("맨 위 · 맨 아래 줄 제외") { l, i, _ -> isEdge(l, i) },
        LineFilter("맨 위 · 맨 아래 줄 중 폭 < 중앙 줄 폭 50%만 제외") { l, i, _ -> isEdge(l, i) && l[i].box.width < 0.5 * medianWidth(l) },
        LineFilter("맨 위 · 맨 아래 줄 중 이웃과 간격 > 1.5 × 중앙 간격만 제외") { l, i, _ -> isDetachedEdge(l, i) },
        LineFilter("쪽 높이 위 8% · 아래 8% 안에 걸친 줄 제외") { l, i, h -> l[i].box.top < 0.08 * h || l[i].box.bottom > 0.92 * h },
        LineFilter("어절 2개 이하 줄 제외") { l, i, _ -> l[i].words.size <= 2 }
    )

    data class Result(
        val tp: Int, val fp: Int, val fn: Int,
        /** 미끼 줄(비침 등)에서 나온 오검출 수 — 종류별. */
        val decoyFp: Map<String, Int>,
        /** 본문 밖 검출 구간 수(검출기 단계 — [GoldenSet.SpikeResult.offBodyRuns]와 같은 셈). */
        val offBodyRuns: Int,
        /** 본문 밖 줄에서 미리 선택된 엔진 어절 수 — 사용자 화면에 «선택됨»으로 뜰 본문 밖 어절. */
        val offBodySelected: Int,
        /** 본문 밖 미리 선택 어절의 줄 위치(앱 비트맵 좌표 · 글 없음). */
        val offBodyWhere: List<String>
    )

    /**
     * [tsv]는 [gray]와 같은 좌표의 엔진 어절 상자(기기 덤프). 앱과 같은 입력: 어절 상자 전부(신뢰도 · 높이 거르기는 select 안의 규칙만).
     */
    fun run(
        g: GoldenSet.Golden, gray: IntArray, w: Int, h: Int, tsv: String,
        params: UnderlineDetector.Params = UnderlineDetector.Params(),
        filter: LineFilter = FILTERS.first()
    ): Result {
        val words = GoldenSet.readWords(tsv, minConf = Double.NEGATIVE_INFINITY, filterHeight = false)
        val res = UnderlinePreselect.select(gray, w, h, words.map { it.box }, params)
        val lines = res.lines
        val dropped = lines.indices.filter { filter.drop(lines, it, h) }.toSet()
        val selected = res.selected.copyOf()
        for (li in dropped) for (wi in lines[li].words) selected[wi] = false

        val glines = lines.map { l -> GoldenSet.Line(l.box, l.words.map { words[it] }) }
        val match = GoldenSet.matchLines(g.page, glines)
        val truth = GoldenSet.markedTokens(g, "underline")
        val predicted = mutableSetOf<Pair<Int, Int>>()
        for ((i, li) in match.withIndex()) {
            if (li == null) continue
            val gold = GoldenSet.normalize(g.page[i]).trim().split(Regex("\\s+"))
            val eng = lines[li].words
            val chosen = tokensSelected(gold, eng.map { words[it].text }, eng.map { selected[it] })
            for (k in chosen) predicted += (i + 1) to k
        }
        val body = match.filterNotNull().toSet()
        val offRuns = res.runs.count { it.lineIndex !in body && it.lineIndex !in dropped }
        val offWhere = mutableListOf<String>()
        var offSel = 0
        for ((li, l) in lines.withIndex()) {
            if (li in body) continue
            val n = l.words.count { selected[it] }
            if (n == 0) continue
            offSel += n
            offWhere += "줄 y=${l.box.top}..${l.box.bottom} x=${l.box.left}..${l.box.right} 선택 $n"
        }
        val fpSet = predicted - truth
        val decoyFp = g.decoys.filter { it.line != null }.groupBy { it.kind }.mapValues { (_, ds) ->
            ds.sumOf { d -> fpSet.count { it.first == d.line } }
        }
        return Result((predicted intersect truth).size, fpSet.size, (truth - predicted).size, decoyFp, offRuns, offSel, offWhere)
    }

    /**
     * 정답 줄 어절 [gold]와 엔진 줄 어절 [engine](미리 선택 [sel])을 공백 뺀 글자로 맞춰(편집 거리 역추적 — 같음 · 바꿈만 짝),
     * 맞춰진 글자의 절반 이상이 선택된 엔진 어절 글자인 정답 어절 번호를 돌려준다. 맞춰진 글자가 0이면 선택 아님.
     */
    fun tokensSelected(gold: List<String>, engine: List<String>, sel: List<Boolean>): Set<Int> {
        val a = gold.flatMapIndexed { k, t -> GoldenSet.normalize(t).filterNot { it.isWhitespace() }.map { it to k } }
        val b = engine.flatMapIndexed { k, t -> GoldenSet.normalize(t).filterNot { it.isWhitespace() }.map { it to k } }
        val n = a.size; val m = b.size
        val d = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) d[i][0] = i
        for (j in 0..m) d[0][j] = j
        for (i in 1..n) for (j in 1..m) {
            val c = if (a[i - 1].first == b[j - 1].first) 0 else 1
            d[i][j] = minOf(d[i - 1][j - 1] + c, d[i - 1][j] + 1, d[i][j - 1] + 1)
        }
        val aligned = IntArray(gold.size)
        val hit = IntArray(gold.size)
        var i = n; var j = m
        while (i > 0 && j > 0) {
            val c = if (a[i - 1].first == b[j - 1].first) 0 else 1
            when (d[i][j]) {
                d[i - 1][j - 1] + c -> {
                    val k = a[i - 1].second
                    aligned[k]++
                    if (sel[b[j - 1].second]) hit[k]++
                    i--; j--
                }
                d[i - 1][j] + 1 -> i--
                else -> j--
            }
        }
        return gold.indices.filter { aligned[it] > 0 && 2 * hit[it] >= aligned[it] }.toSet()
    }
}
