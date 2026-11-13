package com.example.golden

import com.example.data.ocr.UnderlineDetector
import java.awt.BasicStroke
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.text.Normalizer
import javax.imageio.ImageIO

/**
 * 추출 품질 기준 자료(golden) 읽기 · 일치율 · 밑줄 검출 스파이크 측정. 규격 = docs/quality/GOLDEN-SET.md.
 * 사진·정답은 레포 밖 폴더에만 있다. 이 코드는 그 폴더를 읽고 결과를 그 폴더의 `_results/`에 쓴다.
 */
object GoldenSet {

    // ---------- 정답 파일 ----------

    data class Mark(val line: Int, val kind: String, val text: String)
    data class Note(val place: String, val text: String)
    data class Decoy(val kind: String, val line: Int?)

    data class Golden(
        val id: String,
        val header: Map<String, String>,
        val page: List<String>,
        val marks: List<Mark>,
        val memo: List<Note>,
        val decoys: List<Decoy>
    ) {
        val rotate: Int get() = header["rotate"]?.trim()?.toIntOrNull() ?: 0
        val photo: String? get() = header["photo"]?.trim()?.takeIf { it.isNotEmpty() }
        val wholePage: Boolean get() = header["whole_page"]?.trim() == "yes"
        /** 표기한 글(밑줄·형광펜 모두), 사진에 보이는 순서. 줄 끝에서 끊긴 말은 두 어절로 남는다. */
        fun marksText(kind: String? = null): String =
            marks.filter { kind == null || it.kind == kind }.joinToString(" ") { it.text }
        fun memoText(): String = memo.joinToString(" ") { it.text }
        fun pageText(): String = page.joinToString(" ")
    }

    fun parse(id: String, text: String): Golden {
        val header = linkedMapOf<String, String>()
        val page = mutableListOf<String>()
        val marks = mutableListOf<Mark>()
        val memo = mutableListOf<Note>()
        val decoys = mutableListOf<Decoy>()
        var section = ""
        for ((n, raw) in text.removePrefix("﻿").lines().withIndex()) {
            val trimmed = raw.trimEnd()
            // 줄 머리 `\` = 이스케이프: 본문이 `#`·`[`로 시작해도 주석·절 머리로 읽지 않는다(`\` 한 글자만 떼고 그대로).
            val escaped = trimmed.trimStart().startsWith("\\")
            val line = if (escaped) trimmed.trimStart().substring(1) else trimmed
            if (!escaped) {
                if (line.isBlank() || line.trimStart().startsWith("#")) continue
                if (line.startsWith("[") && line.endsWith("]")) { section = line.substring(1, line.length - 1).trim(); continue }
            } else if (line.isBlank()) continue
            fun cols(k: Int) = line.split('\t', limit = k).map { it.trim() }
            when (section) {
                "" -> {
                    val i = line.indexOf(':')
                    require(i > 0) { "$id:${n + 1} 머리 줄은 «이름: 값»" }
                    header[line.substring(0, i).trim()] = line.substring(i + 1).substringBefore(" #").trim()
                }
                "page" -> page += line.trim()
                "marks" -> {
                    val c = cols(3)
                    require(c.size == 3) { "$id:${n + 1} [marks]는 «줄번호<탭>종류<탭>글»" }
                    val ln = c[0].toIntOrNull() ?: error("$id:${n + 1} 줄번호")
                    require(c[1] == "underline" || c[1] == "highlight") { "$id:${n + 1} 종류는 underline|highlight" }
                    marks += Mark(ln, c[1], c[2])
                }
                "memo" -> {
                    val c = cols(2)
                    memo += if (c.size == 2) Note(c[0], c[1]) else Note("", c[0])
                }
                "decoys" -> {
                    val c = cols(2)
                    decoys += Decoy(c[0], c.getOrNull(1)?.toIntOrNull())
                }
                else -> error("$id:${n + 1} 모르는 절 [$section]")
            }
        }
        for (m in marks) require(m.line in 1..page.size) { "$id: [marks] 줄번호 ${m.line}이 [page] 범위 밖" }
        return Golden(id, header, page, marks, memo, decoys)
    }

    // ---------- 일치율 ----------

    private const val EDGE_PUNCT = ".,?!;:·…\"'“”‘’()[]「」『』<>-—~"
    /** 정답의 «확신 없는 글자» 표기. 어느 글자와도 같다고 본다. */
    const val UNSURE = "{?}"
    private const val WILD = '�'

    fun normalize(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFC).replace(UNSURE, WILD.toString())

    /** 어절 = 공백으로 갈린 덩어리, 앞뒤 문장부호 제거. */
    fun words(s: String): List<String> =
        normalize(s).split(Regex("\\s+")).map { it.trim { c -> c in EDGE_PUNCT } }.filter { it.isNotEmpty() }

    private fun same(a: String, b: String): Boolean =
        a.length == b.length && a.indices.all { a[it] == b[it] || a[it] == WILD || b[it] == WILD }

    data class Score(val tp: Int, val predicted: Int, val truth: Int, val cer: Double?) {
        val precision: Double? get() = if (predicted == 0) null else tp.toDouble() / predicted
        val recall: Double? get() = if (truth == 0) null else tp.toDouble() / truth
    }

    /** 어절 정밀도·재현율(다중집합) + 글자 오류율(공백 뺀 편집 거리 / 정답 글자 수). */
    fun score(predictedText: String, truthText: String): Score {
        val pred = words(predictedText)
        val left = words(truthText).toMutableList()
        val truthCount = left.size
        var tp = 0
        for (p in pred) {
            val i = left.indexOfFirst { same(it, p) }
            if (i >= 0) { tp++; left.removeAt(i) }
        }
        val a = normalize(predictedText).filterNot { it.isWhitespace() }
        val b = normalize(truthText).filterNot { it.isWhitespace() }
        val cer = if (b.isEmpty()) null else editDistance(a, b).toDouble() / b.length
        return Score(tp, pred.size, truthCount, cer)
    }

    fun editDistance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                val sub = if (a[i - 1] == b[j - 1] || a[i - 1] == WILD || b[j - 1] == WILD) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + sub)
            }
            prev = cur
        }
        return prev[b.length]
    }

    // ---------- 상자(엔진 교체 가능: boxes/<출처>/<id>.tsv, Tesseract TSV 열) ----------

    data class Word(val box: UnderlineDetector.Box, val conf: Double, val text: String)
    data class Line(val box: UnderlineDetector.Box, val words: List<Word>) {
        val text: String get() = words.joinToString(" ") { it.text }
    }

    /** 기기 덤프 머리 `# expects golden rotate: N` — 없으면 null(Tesseract 등). */
    fun dumpRotate(tsv: String): Int? =
        Regex("^# expects golden rotate: (\\d+)", RegexOption.MULTILINE).find(tsv)?.groupValues?.get(1)?.toIntOrNull()

    /** 기기 덤프 머리 `# coords: upright source pixels WxH` — 좌표계 크기. 없으면 null. */
    fun dumpSize(tsv: String): Pair<Int, Int>? =
        Regex("^# coords: upright source pixels (\\d+)x(\\d+)", RegexOption.MULTILINE).find(tsv)?.let {
            it.groupValues[1].toInt() to it.groupValues[2].toInt()
        }

    /** Tesseract `tsv` 출력(또는 같은 열로 옮긴 다른 엔진의 상자). level 5(어절)만 쓴다. conf < 0은 «신뢰도 없음» = 통과. */
    fun readWords(tsv: String, minConf: Double = 40.0): List<Word> {
        val out = mutableListOf<Word>()
        for (line in tsv.lines().drop(1)) {
            val c = line.split('\t')
            if (c.size < 12 || c[0].trim() != "5") continue
            val text = c[11].trim()
            if (text.isEmpty()) continue
            val conf = c[10].toDoubleOrNull() ?: continue
            if (conf >= 0.0 && conf < minConf) continue
            val l = c[6].toInt(); val t = c[7].toInt(); val w = c[8].toInt(); val h = c[9].toInt()
            out += Word(UnderlineDetector.Box(l, t, l + w, t + h), conf, text)
        }
        if (out.isEmpty()) return out
        val mh = median(out.map { it.box.height })
        // 줄 높이와 동떨어진 상자(잡티·여러 줄을 합친 상자)는 뺀다.
        return out.filter { it.box.height >= 0.4 * mh && it.box.height <= 2.4 * mh }
    }

    /**
     * 어절 상자를 글줄로 묶는다(세로 중심이 줄 중앙값에서 글자 높이 절반 안). 줄 아래 끝은 밑줄과 합쳐져 부푼 상자를
     * 피하려고 «위 끝 중앙값 + 높이 30백분위»로 잡는다.
     */
    fun clusterLines(words: List<Word>): List<Line> {
        val sorted = words.sortedBy { (it.box.top + it.box.bottom) / 2.0 }
        val groups = mutableListOf<MutableList<Word>>()
        for (w in sorted) {
            val cy = (w.box.top + w.box.bottom) / 2.0
            val g = groups.lastOrNull()
            if (g != null && cy - medianD(g.map { (it.box.top + it.box.bottom) / 2.0 }) < 0.5 * median(g.map { it.box.height })) g += w
            else groups += mutableListOf(w)
        }
        return groups.map { g ->
            val ws = g.sortedBy { it.box.left }
            val top = median(ws.map { it.box.top })
            val hs = ws.map { it.box.height }.sorted()
            val h = hs[(hs.size * 0.3).toInt().coerceAtMost(hs.size - 1)]
            Line(UnderlineDetector.Box(ws.minOf { it.box.left }, top, ws.maxOf { it.box.right }, top + h), ws)
        }
    }

    /**
     * 정답 [page] 줄마다 글자 겹침(Dice)이 가장 큰 상자 줄을 순서대로 고른다. 못 찾으면 null.
     * 측정 편향: 앞에서부터 욕심껏(greedy) 고르고 되돌아보지 않는다. 앞 줄이 한 줄 아래 상자를 잘못 가져가면 그 뒤 줄들이
     * 대응을 잃어 «줄 대응 실패»(상자 출처 탓)로 세어진다 — 상자 출처 비교에서 기운 페이지 쪽이 불리하게 나올 수 있다.
     */
    fun matchLines(page: List<String>, lines: List<Line>, minDice: Double = 0.5): List<Int?> {
        var from = 0
        return page.map { g ->
            val best = (from until lines.size).maxByOrNull { dice(g, lines[it].text) }
            if (best != null && dice(g, lines[best].text) >= minDice) { from = best + 1; best } else null
        }
    }

    fun dice(a: String, b: String): Double {
        val ca = normalize(a).filterNot { it.isWhitespace() }.groupingBy { it }.eachCount()
        val cb = normalize(b).filterNot { it.isWhitespace() }.groupingBy { it }.eachCount()
        val inter = ca.entries.sumOf { (k, v) -> minOf(v, cb[k] ?: 0) }
        val total = ca.values.sum() + cb.values.sum()
        return if (total == 0) 0.0 else 2.0 * inter / total
    }

    /**
     * 글자 폭 비례로 줄 상자 안 어절의 가로 범위를 나눈다(한글 1 · 공백 0.75 · 문장부호 0.45 · 그 밖 0.6).
     * 측정 편향: [lineText]는 정답 본문 줄인데 [box]는 엔진 줄 상자다. 엔진이 같은 높이의 여백 메모·쪽번호를 그 줄에 합치면
     * 상자가 넓어져 어절 범위가 통째로 밀린다(맞은 밑줄이 옆 어절로 배정 → 오검출 1 + 미검출 1). 기기 상자로 갈면 어절 상자를
     * 직접 쓰는 길(제품화 PR 2)이 이 편향을 없앤다.
     */
    fun tokenRanges(lineText: String, box: UnderlineDetector.Box): List<IntRange> {
        fun weight(c: Char) = when {
            c.isWhitespace() -> 0.75
            c in EDGE_PUNCT -> 0.45
            c in '가'..'힣' -> 1.0 // 한글 음절
            else -> 0.6
        }
        val t = normalize(lineText).trim().replace(Regex("\\s+"), " ")
        val total = t.sumOf { weight(it) }
        if (total == 0.0) return emptyList()
        val scale = box.width / total
        val out = mutableListOf<IntRange>()
        var x = box.left.toDouble()
        var start = -1.0
        for (c in t) {
            val w = weight(c) * scale
            if (c == ' ') { if (start >= 0) out += start.toInt() until x.toInt(); start = -1.0 }
            else if (start < 0) start = x
            x += w
        }
        if (start >= 0) out += start.toInt() until x.toInt()
        return out
    }

    /**
     * [marks] 조각을 [page] 줄의 어절 번호로. 조각은 그 줄의 연속 어절이어야 한다.
     * 측정 편향: 같은 줄에 같은 조각이 두 번 나오면 첫 자리로 본다(뒤 자리에 친 밑줄이면 정답 어절 위치가 틀린다).
     * 정답 작성 때 그런 줄은 조각을 앞뒤 어절까지 늘려 하나로 좁힌다.
     */
    fun markedTokens(g: Golden, kind: String? = null): Set<Pair<Int, Int>> {
        val out = mutableSetOf<Pair<Int, Int>>()
        for (m in g.marks.filter { kind == null || it.kind == kind }) {
            val lineTokens = normalize(g.page[m.line - 1]).trim().split(Regex("\\s+"))
            val frag = normalize(m.text).trim().split(Regex("\\s+"))
            val at = (0..lineTokens.size - frag.size).firstOrNull { s -> frag.indices.all { same(lineTokens[s + it], frag[it]) } }
                ?: error("${g.id}: [marks] 줄 ${m.line}의 «${m.text}»가 [page] 줄 안에 어절 단위로 없다")
            for (k in frag.indices) out += m.line to (at + k)
        }
        return out
    }

    // ---------- 측정 ----------

    data class SpikeResult(
        val boxSource: String,
        val tp: Int, val fp: Int, val fn: Int,
        val matchedLines: Int, val pageLines: Int,
        val offBodyRuns: List<String>,
        val decoyFp: Map<String, Int>,
        val falsePositives: List<String>, val falseNegatives: List<String>,
        val text: String,
        /** 미검출 가운데 상자 줄을 못 찾은(정답 줄 ↔ 상자 줄 대응 실패) 줄의 몫 — 검출기가 아니라 상자 출처 탓. */
        val fnUnmatchedLine: Int = 0
    )

    fun loadGray(photo: File, rotate: Int): Triple<IntArray, Int, Int> {
        var img = ImageIO.read(photo) ?: error("사진을 읽지 못함: ${photo.name}")
        repeat(((rotate % 360 + 360) % 360) / 90) { img = rotate90(img) }
        val w = img.width; val h = img.height
        val argb = img.getRGB(0, 0, w, h, null, 0, w)
        return Triple(UnderlineDetector.toGray(argb), w, h)
    }

    private fun rotate90(src: BufferedImage): BufferedImage {
        val out = BufferedImage(src.height, src.width, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until src.height) for (x in 0 until src.width) out.setRGB(src.height - 1 - y, x, src.getRGB(x, y))
        return out
    }

    fun runSpike(
        g: Golden, gray: IntArray, w: Int, h: Int, boxSource: String, tsv: String,
        params: UnderlineDetector.Params = UnderlineDetector.Params(), overlayOut: File? = null,
        /** true면 줄마다 어절 상자로 맞춘 기울기([UnderlineDetector.lineSlope])를 검출기에 넣는다. false = 수평(기존 경로). */
        useSlope: Boolean = false
    ): SpikeResult {
        val lines = clusterLines(readWords(tsv))
        val slopes = if (useSlope) DoubleArray(lines.size) { UnderlineDetector.lineSlope(lines[it].words.map { wd -> wd.box }) } else null
        val runs = UnderlineDetector.detect(gray, w, h, lines.map { it.box }, params, slopes)
        val match = matchLines(g.page, lines)
        val truth = markedTokens(g, "underline")
        val predicted = mutableSetOf<Pair<Int, Int>>()
        val ranges = mutableMapOf<Int, Pair<UnderlineDetector.Box, List<IntRange>>>()
        for ((i, li) in match.withIndex()) {
            if (li == null) continue
            val r = tokenRanges(g.page[i], lines[li].box)
            ranges[i + 1] = lines[li].box to r
            val lineRuns = runs.filter { it.lineIndex == li }
            for ((k, range) in r.withIndex()) {
                if (UnderlineDetector.coverage(range.first, range.last + 1, lineRuns) >= params.wordCoverage) predicted += (i + 1) to k
            }
        }
        val body = match.filterNotNull().toSet()
        val offBody = runs.filter { it.lineIndex !in body }.map {
            val l = lines[it.lineIndex]
            "줄 y=${l.box.top}..${l.box.bottom} «${l.text.take(20)}» x=${it.left}..${it.right}"
        }
        fun tokenName(p: Pair<Int, Int>) = "${p.first}:${normalize(g.page[p.first - 1]).trim().split(Regex("\\s+")).getOrNull(p.second)}"
        val fpSet = predicted - truth
        val fnSet = truth - predicted
        val decoyFp = g.decoys.filter { it.line != null }.groupBy { it.kind }.mapValues { (_, ds) ->
            ds.sumOf { d -> fpSet.count { it.first == d.line } }
        }
        val text = predicted.sortedWith(compareBy({ it.first }, { it.second })).joinToString(" ") {
            normalize(g.page[it.first - 1]).trim().split(Regex("\\s+"))[it.second]
        }
        overlayOut?.let { drawOverlay(gray, w, h, lines, runs, ranges, truth, predicted, it) }
        return SpikeResult(
            boxSource, (predicted intersect truth).size, fpSet.size, fnSet.size,
            match.count { it != null }, g.page.size, offBody, decoyFp,
            fpSet.sortedWith(compareBy({ it.first }, { it.second })).map(::tokenName),
            fnSet.sortedWith(compareBy({ it.first }, { it.second })).map(::tokenName),
            text,
            fnSet.count { match[it.first - 1] == null }
        )
    }

    /** 실패 사례 확인용: 줄 상자(파랑) · 검출 구간(빨강) · 정답 어절(초록) · 오검출(주황)·미검출(보라) 어절. 긴 변 1600px로 줄임. */
    private fun drawOverlay(
        gray: IntArray, w: Int, h: Int, lines: List<Line>, runs: List<UnderlineDetector.Run>,
        ranges: Map<Int, Pair<UnderlineDetector.Box, List<IntRange>>>, truth: Set<Pair<Int, Int>>, predicted: Set<Pair<Int, Int>>,
        out: File
    ) {
        val s = 1600.0 / maxOf(w, h)
        val ow = (w * s).toInt(); val oh = (h * s).toInt()
        val img = BufferedImage(ow, oh, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until oh) for (x in 0 until ow) {
            val v = gray[(y / s).toInt().coerceAtMost(h - 1) * w + (x / s).toInt().coerceAtMost(w - 1)]
            img.setRGB(x, y, (v shl 16) or (v shl 8) or v)
        }
        val gr = img.createGraphics()
        gr.stroke = BasicStroke(2f)
        fun rect(b: UnderlineDetector.Box, c: Color) {
            gr.color = c; gr.drawRect((b.left * s).toInt(), (b.top * s).toInt(), (b.width * s).toInt(), (b.height * s).toInt())
        }
        lines.forEach { rect(it.box, Color(60, 120, 255)) }
        for (r in runs) {
            val l = lines[r.lineIndex].box
            gr.color = Color.RED
            val y = ((l.bottom + l.height * 0.3) * s).toInt()
            gr.fillRect((r.left * s).toInt(), y, ((r.right - r.left) * s).toInt(), 3)
        }
        for ((ln, lineAndRanges) in ranges) {
            val (lineBox, rs) = lineAndRanges
            for ((k, rr) in rs.withIndex()) {
                val key = ln to k
                val c = when {
                    key in truth && key in predicted -> Color(0, 160, 0)
                    key in predicted -> Color(255, 140, 0)
                    key in truth -> Color(160, 0, 200)
                    else -> null
                } ?: continue
                rect(UnderlineDetector.Box(rr.first, lineBox.top, rr.last + 1, lineBox.bottom), c)
            }
        }
        gr.dispose()
        out.parentFile?.mkdirs()
        ImageIO.write(img, "png", out)
    }

    private fun median(v: List<Int>): Int = v.sorted()[v.size / 2]
    private fun medianD(v: List<Double>): Double = v.sorted()[v.size / 2]
}
