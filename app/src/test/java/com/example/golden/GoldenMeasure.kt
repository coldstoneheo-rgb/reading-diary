package com.example.golden

import java.io.File
import java.util.Locale

/**
 * 기준 자료 폴더 하나를 재서 마크다운 표를 돌려준다(규격 = docs/quality/GOLDEN-SET.md).
 * - 엔진 출력: `outputs/<엔진>/<id>.<대상>.txt` (대상 = marks · underline · highlight · memo · page) ↔ 정답의 같은 대상.
 * - 밑줄 검출 스파이크: 사진 + `boxes/<출처>/<id>.tsv`가 있으면 출처마다 한 번. 오버레이 PNG는 [spikeOut]에.
 * 자료가 0장이어도 실패하지 않고 «자료 0장»을 적는다.
 */
object GoldenMeasure {

    val TARGETS = listOf("marks", "underline", "highlight", "memo", "page")

    fun truthFor(g: GoldenSet.Golden, target: String): String = when (target) {
        "marks" -> g.marksText()
        "underline" -> g.marksText("underline")
        "highlight" -> g.marksText("highlight")
        "memo" -> g.memoText()
        "page" -> g.pageText()
        else -> error(target)
    }

    private fun pct(v: Double?) = v?.let { String.format(Locale.ROOT, "%.1f%%", it * 100) } ?: "—"

    fun run(dir: File, spikeOut: File? = null): String {
        val sb = StringBuilder("# 기준 자료 측정 — ${dir.path}\n\n")
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".golden.txt") }?.sortedBy { it.name }.orEmpty()
        if (files.isEmpty()) {
            sb.append("자료 0장\n")
            return sb.toString()
        }
        sb.append("자료 ${files.size}장\n\n")
        for (f in files) {
            val unsure = Regex(Regex.escape(GoldenSet.UNSURE)).findAll(f.readText()).count()
            sb.append("- ${f.name.removeSuffix(".golden.txt")}: 정답 미확정 글자 ${unsure}개\n")
        }
        sb.append("\n## 엔진 출력 ↔ 정답\n\n")
        sb.append("| 자료 | 엔진 | 대상 | 어절 정밀도 | 어절 재현율 | 글자 오류율 | 맞은/낸/정답 어절 |\n|---|---|---|---|---|---|---|\n")
        val goldens = files.map { GoldenSet.parse(it.name.removeSuffix(".golden.txt"), it.readText()) }
        val engines = File(dir, "outputs").listFiles { f -> f.isDirectory }?.sortedBy { it.name }.orEmpty()
        var rows = 0
        for (g in goldens) for (e in engines) for (t in TARGETS) {
            val out = File(e, "${g.id}.$t.txt")
            if (!out.isFile) continue
            val truth = truthFor(g, t)
            if (truth.isBlank()) continue
            val s = GoldenSet.score(out.readText(), truth)
            sb.append("| ${g.id} | ${e.name} | $t | ${pct(s.precision)} | ${pct(s.recall)} | ${pct(s.cer)} | ${s.tp}/${s.predicted}/${s.truth} |\n")
            rows++
        }
        if (rows == 0) sb.append("| — | 엔진 출력 0개 | | | | | |\n")

        sb.append("\n## 밑줄 검출 스파이크(어절 단위, 상자 출처별 — 기기 ML Kit 상자와 다를 수 있음)\n\n")
        sb.append("| 자료 | 상자 출처 | 정밀도 | 재현율 | 맞음/오검출/미검출 | 본문 줄 대응 | 미끼 줄 오검출 | 본문 밖 오검출 구간 |\n|---|---|---|---|---|---|---|---|\n")
        val details = StringBuilder()
        val sources = File(dir, "boxes").listFiles { f -> f.isDirectory }?.sortedBy { it.name }.orEmpty()
        var spikeRows = 0
        for (g in goldens) {
            val photo = g.photo?.let { File(dir, it) }?.takeIf { it.isFile } ?: continue
            val boxed = sources.filter { File(it, "${g.id}.tsv").isFile }
            if (boxed.isEmpty()) continue
            val (gray, w, h) = GoldenSet.loadGray(photo, g.rotate)
            for (src in boxed) {
                val r = GoldenSet.runSpike(
                    g, gray, w, h, src.name, File(src, "${g.id}.tsv").readText(),
                    overlayOut = spikeOut?.let { File(it, "${g.id}.${src.name}.overlay.png") }
                )
                val p = if (r.tp + r.fp == 0) null else r.tp.toDouble() / (r.tp + r.fp)
                val rc = if (r.tp + r.fn == 0) null else r.tp.toDouble() / (r.tp + r.fn)
                val decoy = if (r.decoyFp.isEmpty()) "—" else r.decoyFp.entries.joinToString(" · ") { "${it.key} ${it.value}" }
                sb.append("| ${g.id} | ${r.boxSource} | ${pct(p)} | ${pct(rc)} | ${r.tp}/${r.fp}/${r.fn} | ${r.matchedLines}/${r.pageLines} | $decoy | ${r.offBodyRuns.size} |\n")
                details.append("\n### ${g.id} · ${r.boxSource}\n\n")
                details.append("- 오검출 어절: ${r.falsePositives.ifEmpty { listOf("0") }.joinToString(", ")}\n")
                details.append("- 미검출 어절: ${r.falseNegatives.ifEmpty { listOf("0") }.joinToString(", ")}\n")
                details.append("- 본문 밖 구간: ${r.offBodyRuns.ifEmpty { listOf("0") }.joinToString("; ")}\n")
                val us = GoldenSet.score(r.text, g.marksText("underline"))
                details.append("- 고른 어절을 글로 이으면(정답 글자 기준): 어절 정밀도 ${pct(us.precision)} · 재현율 ${pct(us.recall)} · 글자 오류율 ${pct(us.cer)}\n")
                spikeRows++
            }
        }
        if (spikeRows == 0) sb.append("| — | 사진+상자 0벌 | | | | | | |\n")
        sb.append(details)
        return sb.toString()
    }
}
