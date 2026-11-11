package com.example.golden

import java.io.File

/**
 * 기기 상자 덤프(#55, 머리 `#` 줄이 있는 TSV)를 측정에 넣기 전에 «조용히 틀린 입력»을 걸러낸다.
 * ⓐ `NOT EXACT`(반전 · EXIF 미러 · 원본 크기 모름) ⓑ 사진 방향이 덤프와 180° 어긋남 ⓒ 덤프 ↔ 기준 사진 짝이 틀림.
 * 짝은 사람이 이름을 바꾸지 않아도 **덤프 글 ↔ 정답 본문의 글자 겹침**으로 자동으로 짓는다.
 */
object DumpGuard {

    /** 덤프로 보는 기준: 머리에 앱이 세운 각도가 있다(Tesseract 상자에는 없다). */
    fun isDeviceDump(tsv: String): Boolean = GoldenSet.dumpRotate(tsv) != null

    /** ⓐ 머리 `# coords:` 줄에 NOT EXACT 표시가 있나. */
    fun notExact(tsv: String): Boolean = tsv.lineSequence().any { it.startsWith("# coords:") && it.contains("NOT EXACT") }

    /** 덤프의 어절 글을 이어 붙인 것(신뢰도 -1 = 통과). */
    fun text(tsv: String): String = GoldenSet.readWords(tsv).joinToString(" ") { it.text }

    /** ⓒ 덤프 글과 정답 본문의 글자 겹침(Dice, 0..1). 머리글·쪽번호·메모가 섞여도 본문이 대부분이라 맞는 짝은 높다. */
    fun textMatch(tsv: String, g: GoldenSet.Golden): Double = GoldenSet.dice(g.pageText(), text(tsv))

    const val MIN_MATCH = 0.5

    data class Pairing(val file: File, val match: Double, val candidates: Int)

    /**
     * ⓒ 이름이 `<id>.tsv`가 아닌 덤프 파일들을 기준 사진에 짝짓는다. 파일마다 글자 겹침이 가장 큰 기준 사진을 고르고
     * ([MIN_MATCH] 이상만), 한 기준 사진에 여러 파일이 오면(같은 사진을 다시 인식) 이름순 마지막(나중 덤프)을 쓴다.
     */
    fun pair(goldens: List<GoldenSet.Golden>, files: List<File>): Map<String, Pairing> {
        val best = files.mapNotNull { f ->
            val tsv = f.readText()
            goldens.map { it.id to textMatch(tsv, it) }.maxByOrNull { it.second }
                ?.takeIf { it.second >= MIN_MATCH }?.let { (id, m) -> Triple(id, f, m) }
        }
        return best.groupBy { it.first }.mapValues { (_, xs) ->
            val last = xs.maxBy { it.second.name }
            Pairing(last.second, last.third, xs.size)
        }
    }

    /**
     * ⓑ 상자와 사진의 줄 맞춤 점수: 위아래로 이웃한 줄 상자 사이 «틈»의 밝기 − 줄 상자 안(가운데 60%)의 밝기, 평균.
     * 상자가 사진의 글줄 위에 제대로 놓이면 크게 양수, 180° 어긋나면 0 근처. 비교할 줄 쌍이 없으면 null.
     */
    fun alignment(gray: IntArray, w: Int, h: Int, lines: List<GoldenSet.Line>): Double? {
        val sorted = lines.map { it.box }.sortedBy { it.top }
        var sum = 0.0
        var n = 0
        for (i in 0 until sorted.size - 1) {
            val a = sorted[i]
            val b = sorted[i + 1]
            val l = maxOf(a.left, b.left, 0)
            val r = minOf(a.right, b.right, w)
            if (r - l < 4 || b.top - a.bottom < 2) continue
            val pad = (a.height * 0.2).toInt()
            val inside = mean(gray, w, h, l, r, a.top + pad, a.bottom - pad) ?: continue
            val gap = mean(gray, w, h, l, r, a.bottom, b.top) ?: continue
            sum += gap - inside
            n++
        }
        return if (n == 0) null else sum / n
    }

    private fun mean(gray: IntArray, w: Int, h: Int, l: Int, r: Int, t: Int, b: Int): Double? {
        val tt = t.coerceIn(0, h)
        val bb = b.coerceIn(0, h)
        if (bb <= tt || r <= l) return null
        var s = 0L
        var c = 0
        var y = tt
        while (y < bb) {
            var x = l
            while (x < r) { s += gray[y * w + x]; c++; x += 2 }
            y++
        }
        return if (c == 0) null else s.toDouble() / c
    }
}
