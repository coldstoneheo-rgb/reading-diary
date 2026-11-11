package com.example.golden

import java.io.File
import java.util.Locale

/**
 * 기준 자료 폴더 하나를 재서 마크다운 표를 돌려준다(규격 = docs/quality/GOLDEN-SET.md).
 * - 엔진 출력: `outputs/<엔진>/<id>.<대상>.txt` (대상 = marks · underline · highlight · memo · page) ↔ 정답의 같은 대상.
 * - 밑줄 검출 스파이크: 사진 + `boxes/<출처>/<id>.tsv`가 있으면 출처마다 한 번. 오버레이 PNG는 [spikeOut]에.
 * 자료가 0장이어도 실패하지 않고 «자료 0장»을 적는다.
 * 결과는 둘로 나눈다: [Report.table] = 수치 표(PR·메시지에 옮겨도 되는 것) · [Report.details] = 오검출·미검출 어절과
 * OCR 줄 글(사용자 책 본문 조각 — 레포 밖 `_results/details.md`에만, PR·메시지에 옮기지 않는다).
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

    data class Report(val table: String, val details: String)

    fun run(dir: File, spikeOut: File? = null): Report {
        val sb = StringBuilder("# 기준 자료 측정 — <기준 자료 폴더>(경로는 싣지 않는다 — 이 표는 PR·메시지에 옮길 수 있다)\n\n")
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".golden.txt") }?.sortedBy { it.name }.orEmpty()
        if (files.isEmpty()) {
            sb.append("자료 0장\n")
            return Report(sb.toString(), "")
        }
        sb.append("자료 ${files.size}장\n\n")
        for (f in files) {
            // 주석(#) 줄의 설명 문구는 세지 않는다.
            val unsure = f.readText().lines().filterNot { it.trimStart().startsWith("#") }
                .sumOf { Regex(Regex.escape(GoldenSet.UNSURE)).findAll(it).count() }
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
        sb.append("| 자료 | 상자 출처 | 정밀도 | 재현율 | 재현율(대응된 줄만) | 맞음/오검출/미검출(그중 줄 대응 실패) | 본문 줄 대응 | 미끼 줄 오검출 | 본문 밖 오검출 구간 |\n|---|---|---|---|---|---|---|---|---|\n")
        val details = StringBuilder("# 측정 상세 — 레포·PR·메시지에 옮기지 않는다(본문 조각)\n")
        val sources = File(dir, "boxes").listFiles { f -> f.isDirectory }?.sortedBy { it.name }.orEmpty()
        var spikeRows = 0
        // 이름이 <id>.tsv가 아닌 기기 덤프(ocr-dump-NNN-…tsv)는 글 포함률로 짝을 짓는다(ⓒ, 사람 손 이름 바꾸기 없음).
        // 머리 없는 상자(Tesseract 등)는 자동 짝에 넣지 않는다 — 전처럼 <id>.tsv 이름일 때만 쓴다.
        val ids = goldens.map { it.id }.toSet()
        val autoPairs = sources.associateWith { src ->
            val dumps = src.listFiles { f -> f.isFile && f.name.endsWith(".tsv") && f.name.removeSuffix(".tsv") !in ids }.orEmpty()
                .filter { DumpGuard.isDeviceDump(it.readText()) }.sortedBy { it.name }
            DumpGuard.pair(goldens, dumps)
        }
        for (g in goldens) {
            val photo = g.photo?.let { File(dir, it) }?.takeIf { it.isFile } ?: continue
            val boxed = sources.mapNotNull { src ->
                File(src, "${g.id}.tsv").takeIf { it.isFile }?.let { Triple(src, it, "") }
                    ?: autoPairs[src]?.paired?.get(g.id)?.let { p ->
                        Triple(src, p.file, " (짝 자동 ${p.file.name} 글 일치 ${pct(p.match)}${if (p.dropped.isNotEmpty()) " · 탈락 ${p.dropped.joinToString(", ")}" else ""})")
                    }
            }
            if (boxed.isEmpty()) continue
            // 사진 한 장의 회색조는 방향별로 이 장 안에서만 들고 있다(장이 바뀌면 버린다).
            val loaded = mutableMapOf<Int, Triple<IntArray, Int, Int>>()
            for ((src, file, pairNote) in boxed) {
                val tsv = file.readText()
                // 기기 덤프는 앱이 세운 각도(EXIF + 사용자 회전)를 머리에 적는다.
                val rotate = GoldenSet.dumpRotate(tsv) ?: g.rotate
                val (gray, w, h) = loaded.getOrPut(rotate) { GoldenSet.loadGray(photo, rotate) }
                // 기기 덤프는 조용히 틀린 입력을 표에서 빼고 사유를 적는다(ⓐ 반전·미러 ⓑ 방향 ⓒ 짝 · 크기).
                val excluded = mutableListOf<String>()
                var rotateNote = ""
                if (DumpGuard.isDeviceDump(tsv)) {
                    if (DumpGuard.notExact(tsv)) excluded += "NOT EXACT(반전·EXIF 미러·원본 크기 모름)"
                    GoldenSet.dumpSize(tsv)?.takeIf { it != (w to h) }?.let { excluded += "크기 ${it.first}x${it.second}≠사진 ${w}x$h" }
                    val match = DumpGuard.textMatch(tsv, g)
                    if (match < DumpGuard.MIN_MATCH) excluded += "짝 의심(글 일치 ${pct(match)})"
                    // ⓑ 덤프 각도와 정답 rotate:가 같으면 두 출처가 따로 같은 방향을 말하므로 믿는다.
                    // 다르면(전송 중 EXIF가 빠짐 등) 두 방향에서 상자가 글줄에 얼마나 맞는지 비교해 분명할 때만 쓴다.
                    if (excluded.isEmpty() && rotate != g.rotate) {
                        val (og, ow, oh) = loaded.getOrPut(g.rotate) { GoldenSet.loadGray(photo, g.rotate) }
                        val lines = GoldenSet.clusterLines(GoldenSet.readWords(tsv))
                        val dumpSide = DumpGuard.alignment(gray, w, h, lines)
                        val goldenSide = if (ow == w && oh == h) DumpGuard.alignment(og, ow, oh, lines) else null
                        when (DumpGuard.decide(dumpSide, goldenSide)) {
                            DumpGuard.Verdict.FIRST -> rotateNote = " (rotate $rotate · 정답 ${g.rotate} — 상자 정렬로 $rotate 확인)"
                            DumpGuard.Verdict.SECOND -> excluded += "방향 의심(정답 rotate ${g.rotate} 쪽이 글줄에 맞음)"
                            DumpGuard.Verdict.UNDECIDED -> excluded += "방향 판정 불가(rotate $rotate≠정답 ${g.rotate})"
                        }
                    }
                }
                val note = pairNote + rotateNote
                if (excluded.isNotEmpty()) {
                    sb.append("| ${g.id} | ⚠제외 ${src.name}$note — ${excluded.joinToString(" · ")} | — | — | — | — | — | — | — |\n")
                    spikeRows++
                    continue
                }
                val r = GoldenSet.runSpike(
                    g, gray, w, h, src.name + note, tsv,
                    overlayOut = spikeOut?.let { File(it, "${g.id}.${src.name}.overlay.png") }
                )
                val p = if (r.tp + r.fp == 0) null else r.tp.toDouble() / (r.tp + r.fp)
                val rc = if (r.tp + r.fn == 0) null else r.tp.toDouble() / (r.tp + r.fn)
                val decoy = if (r.decoyFp.isEmpty()) "—" else r.decoyFp.entries.joinToString(" · ") { "${it.key} ${it.value}" }
                val inLine = r.tp + r.fn - r.fnUnmatchedLine
                val rcMatched = if (inLine == 0) null else r.tp.toDouble() / inLine
                sb.append("| ${g.id} | ${r.boxSource} | ${pct(p)} | ${pct(rc)} | ${pct(rcMatched)} | ${r.tp}/${r.fp}/${r.fn}(${r.fnUnmatchedLine}) | ${r.matchedLines}/${r.pageLines} | $decoy | ${r.offBodyRuns.size} |\n")
                details.append("\n### ${g.id} · ${r.boxSource}\n\n")
                details.append("- 오검출 어절: ${r.falsePositives.ifEmpty { listOf("0") }.joinToString(", ")}\n")
                details.append("- 미검출 어절: ${r.falseNegatives.ifEmpty { listOf("0") }.joinToString(", ")}\n")
                details.append("- 본문 밖 구간: ${r.offBodyRuns.ifEmpty { listOf("0") }.joinToString("; ")}\n")
                val us = GoldenSet.score(r.text, g.marksText("underline"))
                details.append("- 고른 어절을 글로 이으면(정답 글자 기준): 어절 정밀도 ${pct(us.precision)} · 재현율 ${pct(us.recall)} · 글자 오류율 ${pct(us.cer)}\n")
                spikeRows++
            }
        }
        // 짝을 못 지은 덤프도 표에 드러낸다(조용히 사라지지 않게).
        for ((src, res) in autoPairs) for ((f, why) in res.unpaired) {
            sb.append("| — | ⚠짝 없음 ${src.name}: ${f.name} — $why | — | — | — | — | — | — | — |\n")
            spikeRows++
        }
        if (spikeRows == 0) sb.append("| — | 사진+상자 0벌 | | | | | | | |\n")
        sb.append("\n«⚠» 행은 수치가 없고 합계에서 빠진다 — 합계 분모는 «⚠» 없는 행의 맞음+오검출 · 맞음+미검출만 더한다(뺀 장 수를 합계 옆에 적는다).\n")
        sb.append("\n어절별 상세 = 같은 폴더 `_results/details.md`(레포 밖에만).\n")
        return Report(sb.toString(), details.toString())
    }
}
