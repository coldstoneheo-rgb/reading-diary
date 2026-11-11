package com.example.golden

import java.io.File

/**
 * 기기 상자 덤프(#55, 머리 `#` 줄이 있는 TSV)를 측정에 넣기 전에 «조용히 틀린 입력»을 걸러낸다.
 * ⓐ `NOT EXACT`(반전 · EXIF 미러 · 원본 크기 모름) ⓑ 사진 방향이 덤프와 어긋남 ⓒ 덤프 ↔ 기준 사진 짝이 틀림.
 * 짝은 사람이 이름을 바꾸지 않아도 **덤프 글의 두 글자 묶음이 정답 본문에 들어 있는 몫**(포함률)으로 짓는다.
 *
 * 문턱 근거(11-12 실측, 기준 사진 11장 · Tesseract 상자 글로 잼 — 기기 엔진보다 글이 나빠 보수적인 쪽):
 * 글이 나온 9장에서 맞는 짝 0.73~0.92 · 틀린 짝(같은 책 다른 쪽) 최대 0.16. 한 글자 Dice는 틀린 짝도 0.47까지 나와 쓰지 않는다.
 * 포함률은 덤프 쪽 분모라 미리보기에서 일부만 잘라 인식한 덤프도 맞는 짝이 높다.
 */
object DumpGuard {

    const val MIN_MATCH = 0.4
    /** 1등과 2등 기준 사진의 포함률 차이가 이보다 작으면 «짝 모호». */
    const val MIN_MARGIN = 0.2
    /** 덤프 글자가 이보다 적으면 짝을 판정하지 않는다. */
    const val MIN_CHARS = 20

    /** 덤프로 보는 기준: 머리에 앱이 세운 각도가 있다(Tesseract 상자에는 없다). */
    fun isDeviceDump(tsv: String): Boolean = GoldenSet.dumpRotate(tsv) != null

    /** ⓐ 머리 `# coords:` 줄에 NOT EXACT 표시가 있나. */
    fun notExact(tsv: String): Boolean = tsv.lineSequence().any { it.startsWith("# coords:") && it.contains("NOT EXACT") }

    /** 머리 `# created:` 값(정렬용, 없으면 ""). */
    fun created(tsv: String): String = tsv.lineSequence().firstOrNull { it.startsWith("# created:") }?.substringAfter(":")?.trim().orEmpty()

    /** 덤프의 어절 글을 이어 붙인 것(신뢰도 -1 = 통과). */
    fun text(tsv: String): String = GoldenSet.readWords(tsv).joinToString(" ") { it.text }

    private fun chars(s: String): List<Char> = GoldenSet.normalize(s).filterNot { it.isWhitespace() }.toList()

    private fun bigrams(s: String): Map<String, Int> {
        val c = chars(s)
        return (0 until (c.size - 1).coerceAtLeast(0)).map { "${c[it]}${c[it + 1]}" }.groupingBy { it }.eachCount()
    }

    /** ⓒ 덤프 글의 두 글자 묶음 가운데 정답 본문에도 있는 몫(다중집합, 0..1). */
    fun textMatch(tsv: String, g: GoldenSet.Golden): Double {
        val d = bigrams(text(tsv))
        val p = bigrams(g.pageText())
        val total = d.values.sum()
        return if (total == 0) 0.0 else d.entries.sumOf { (k, v) -> minOf(v, p[k] ?: 0) }.toDouble() / total
    }

    data class Pairing(val file: File, val match: Double, val dropped: List<String>)
    data class PairResult(val paired: Map<String, Pairing>, val unpaired: List<Pair<File, String>>)

    /**
     * ⓒ 기기 덤프 파일들을 기준 사진에 짝짓는다. 파일마다 포함률 1등 기준 사진을 고르되 [MIN_CHARS] · [MIN_MATCH] ·
     * [MIN_MARGIN]을 넘어야 한다. 한 기준 사진에 여러 파일이 오면 포함률이 가장 높은 것, 0.05 안으로 비슷하면 `# created:`가 나중인 것.
     * 짝을 못 지은 파일은 사유와 함께 [PairResult.unpaired]로 돌려준다(표에 드러내기 위해).
     */
    fun pair(goldens: List<GoldenSet.Golden>, files: List<File>): PairResult {
        val unpaired = mutableListOf<Pair<File, String>>()
        val hits = mutableListOf<Triple<String, File, Double>>()
        val createdOf = mutableMapOf<File, String>()
        for (f in files) {
            val tsv = f.readText()
            createdOf[f] = created(tsv)
            val n = chars(text(tsv)).size
            if (n < MIN_CHARS) { unpaired += f to "글자 ${n}개 — 판정 불가"; continue }
            val ranked = goldens.map { it.id to textMatch(tsv, it) }.sortedByDescending { it.second }
            val (bestId, best) = ranked.firstOrNull() ?: continue
            val second = ranked.getOrNull(1)?.second ?: 0.0
            when {
                best < MIN_MATCH -> unpaired += f to "최고 일치 ${pct(best)}($bestId) < ${pct(MIN_MATCH)}"
                best - second < MIN_MARGIN -> unpaired += f to "짝 모호 — 1등 $bestId ${pct(best)} · 2등 ${pct(second)}"
                else -> hits += Triple(bestId, f, best)
            }
        }
        val paired = hits.groupBy { it.first }.mapValues { (_, xs) ->
            val top = xs.maxOf { it.third }
            val chosen = xs.filter { it.third >= top - 0.05 }.maxBy { createdOf[it.second] ?: "" }
            Pairing(chosen.second, chosen.third, xs.filter { it !== chosen }.map { it.second.name })
        }
        return PairResult(paired, unpaired)
    }

    /**
     * ⓑ 상자와 사진의 줄 맞춤 점수: 위아래로 이웃한 줄 상자 사이 «틈»의 밝기 − 줄 상자 안(가운데 60%)의 밝기, 평균.
     * 상자가 사진의 글줄 위에 제대로 놓이면 크게 양수. 비교할 줄 쌍이 없으면 null.
     * 두 방향의 점수는 [decide]로만 비교한다(여유 폭 없이 크기만 보면 줄 간격이 고른 쪽에서 동전 던지기가 된다).
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

    enum class Verdict { FIRST, SECOND, UNDECIDED }

    /** 두 방향 점수 비교: 한쪽이 max(5, 0.3 × |다른 쪽|) 넘게 높을 때만 그쪽. 아니면 판정 불가. */
    fun decide(first: Double?, second: Double?): Verdict {
        if (first == null || second == null) return Verdict.UNDECIDED
        fun clearly(a: Double, b: Double) = a > b + maxOf(5.0, 0.3 * kotlin.math.abs(b))
        return when {
            clearly(first, second) -> Verdict.FIRST
            clearly(second, first) -> Verdict.SECOND
            else -> Verdict.UNDECIDED
        }
    }

    private fun pct(v: Double) = String.format(java.util.Locale.ROOT, "%.1f%%", v * 100)

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
