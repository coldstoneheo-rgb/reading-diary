package com.example.golden

import com.example.data.ocr.UnderlineDetector
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

    /** 검출기 설정 하나(설정 비교 표의 한 행). */
    data class Variant(val name: String, val params: UnderlineDetector.Params, val useSlope: Boolean)

    /** 보정 전(2026-11-13까지) 검출기: 문턱 0.3Δ · 띠 줄 아래 +0.08H ~ 다음 줄 위 −0.08H · 수평. */
    val LEGACY = UnderlineDetector.Params(darkRatio = 0.3, bandTopRatio = null, nextLinePadRatio = null, smoothMinHeight = 0,
        seedFraction = 0.0)

    /** 2026-11-15(#65)부터 2026-11-25까지의 기본값: 한 단계 문턱 0.28. */
    val SINGLE_028 = UnderlineDetector.Params(darkRatio = 0.28, seedFraction = 0.0)

    /**
     * 설정 비교 표(기기 덤프 상자만, 장 합계). 첫 행 = 보정 전, 이어서 K1a(띠를 기기 ML Kit 줄 상자 모양에 맞춘 지금 기본 띠)
     * + K2(줄 기울기)에 문턱(darkRatio)을 단계로 바꾼 행. 지금 기본값 행은 위 장별 표의 기기 덤프 행 합과 같다.
     */
    val VARIANTS: List<Variant> = listOf(Variant("보정 전(문턱 0.3 · 띠 ±0.08H · 수평)", LEGACY, false)) +
        listOf(0.3, 0.29, 0.28, 0.27, 0.26, 0.25, 0.22, 0.2).map { d ->
            Variant("K1a+K2 · 한 단계 문턱 $d${if (d == 0.28) " = 직전 기본값" else ""}", SINGLE_028.copy(darkRatio = d), true)
        } + Variant("K1a+K2 · 두 단계 문턱 = 지금 기본값", UnderlineDetector.Params(), true)

    /**
     * 앱 해상도 행: 기기 덤프 머리의 배율(원본 ÷ 앱 비트맵, 보통 2)만큼 사진을 줄이고(칸 평균) 상자를 나눠 지금 기본값으로 잰다.
     * 앱은 긴 변 ≤ 2048로 줄인 비트맵에서 검출기를 부른다 — 띠 경계의 정수 자르기(H < 50px · H < 40px)가 원본 해상도와 달라진다.
     * 줄이는 방법(칸 평균)은 기기 디코더(inSampleSize)와 같지 않다 — 근사.
     */
    val APP_RES = listOf(
        Variant("지금 기본값 · 앱 해상도(덤프 배율로 축소 · 칸 평균)", UnderlineDetector.Params(), true),
        Variant("직전 기본값(한 단계 0.28) · 앱 해상도", SINGLE_028, true)
    )

    /** 앱 어절 판정(앱 해상도)으로 나란히 재는 검출기 설정 — 낮은 문턱 · 뚜렷한 열 몫을 바꾼 행. */
    val APP_WORD_PARAMS: List<Pair<String, UnderlineDetector.Params>> = listOf(
        "직전 기본값(한 단계 0.28)" to SINGLE_028,
        "한 단계 0.23" to SINGLE_028.copy(darkRatio = 0.23),
        "지금 기본값 · 세로 3픽셀 평균 늘" to UnderlineDetector.Params(smoothMinHeight = 0),
        // 검출기 수리(HO-2026-1127): 열별 종이 밝기(창 반경 × 세로 분위)
        "수리 전(띠 전체 종이)" to UnderlineDetector.Params(localPaperRadiusRatio = 0.0)
    ) + listOf(1.5, 3.0).flatMap { r ->
        listOf(0.5, 0.75, 0.9).map { q ->
            val p = UnderlineDetector.Params(localPaperRadiusRatio = r, localPaperQuantile = q)
            "열별 종이 ±${r}H · 세로 분위 $q${if (p == UnderlineDetector.Params()) " = 지금 기본값" else ""}" to p
        }
    } + listOf(0.25, 0.24, 0.23, 0.22, 0.21).flatMap { d ->
        listOf(0.15, 0.2, 0.25, 0.3).map { sf ->
            val p = UnderlineDetector.Params(darkRatio = d, seedFraction = sf)
            "두 단계 $d · 뚜렷한 열 $sf${if (p == UnderlineDetector.Params()) " = 지금 기본값" else ""}" to p
        }
    }

    private class Tally { var tp = 0; var fp = 0; var fn = 0; var bleed = 0; var off = 0; val per = mutableListOf<String>() }

    private class WordTally {
        var tp = 0; var fp = 0; var fn = 0; var bleed = 0; var offRuns = 0; var offSel = 0; var inBody = 0; var unmatchedLines = 0
        val per = mutableListOf<String>(); val offPer = mutableListOf<String>()
        fun add(id: String, r: AppWordMeasure.Result) {
            tp += r.tp; fp += r.fp; fn += r.fn; bleed += r.decoyFp["bleed"] ?: 0; offRuns += r.offBodyRuns; offSel += r.offBodySelected
            inBody += r.inBodyUnmatchedSelected; unmatchedLines += r.unmatchedPageLines
            per += "$id:${r.tp}/${r.fp}/${r.fn}"; offPer += "$id:${r.offBodySelected}"
        }
    }

    /** a / (a + b) — 정밀도(b = 오검출) · 재현율(b = 놓침). 분모 0이면 null. */
    private fun prec(a: Int, b: Int): Double? = if (a + b == 0) null else a.toDouble() / (a + b)

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
        val tallies = (VARIANTS + APP_RES).associateWith { Tally() }
        val wordTallies = linkedMapOf<String, WordTally>()
        val paramTallies = linkedMapOf<String, WordTally>()
        val sideBySide = mutableListOf<String>()
        val excludedFromVariants = sortedSetOf<String>()
        val variantIds = sortedSetOf<String>()
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
                    if (DumpGuard.isDeviceDump(tsv)) excludedFromVariants += g.id
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
                if (DumpGuard.isDeviceDump(tsv)) variantIds += g.id
                if (DumpGuard.isDeviceDump(tsv)) for (v in VARIANTS) {
                    val vr = if (v.params == UnderlineDetector.Params() && v.useSlope) r
                    else GoldenSet.runSpike(g, gray, w, h, src.name, tsv, v.params, useSlope = v.useSlope)
                    tallies.getValue(v).apply {
                        tp += vr.tp; fp += vr.fp; fn += vr.fn; bleed += vr.decoyFp["bleed"] ?: 0; off += vr.offBodyRuns.size
                        per += "${g.id.removePrefix("g")}:${vr.tp}/${vr.fp}/${vr.fn}"
                    }
                }
                // 앱 해상도 행의 본문 밖 구간(앱 비트맵 좌표 · 상세 파일에만) — 직전 기본값과 견줘 어디가 늘었나 보려고.
                val appOff = mutableListOf<String>()
                var appCur: GoldenSet.SpikeResult? = null
                if (DumpGuard.isDeviceDump(tsv)) for (v in APP_RES) {
                    val f = GoldenSet.dumpScale(tsv)
                    val (sg, sw, sh) = GoldenSet.shrink(gray, w, h, f)
                    val vr = GoldenSet.runSpike(g, sg, sw, sh, src.name, GoldenSet.scaleBoxes(tsv, f), v.params)
                    tallies.getValue(v).apply {
                        tp += vr.tp; fp += vr.fp; fn += vr.fn; bleed += vr.decoyFp["bleed"] ?: 0; off += vr.offBodyRuns.size
                        per += "${g.id.removePrefix("g")}:${vr.tp}/${vr.fp}/${vr.fn}"
                    }
                    appOff += "${v.name}: ${vr.offBodyRuns.size}${if (vr.offBodyRuns.isEmpty()) "" else " — " + vr.offBodyRuns.joinToString("; ")}"
                    if (v == APP_RES.first()) appCur = vr
                }
                // 앱 방식 어절 판정(엔진 어절 상자 + UnderlinePreselect.select의 selected) — 원본 해상도 1행 + 앱 해상도 × 줄 제외 후보.
                val appWordOff = mutableListOf<String>()
                if (DumpGuard.isDeviceDump(tsv)) {
                    val id = g.id.removePrefix("g")
                    val ow = AppWordMeasure.run(g, gray, w, h, tsv)
                    wordTallies.getOrPut("원본 해상도(참고) · 제외 없음") { WordTally() }.add(id, ow)
                    val f = GoldenSet.dumpScale(tsv)
                    val (sg, sw, sh) = GoldenSet.shrink(gray, w, h, f)
                    val stsv = GoldenSet.scaleBoxes(tsv, f)
                    for ((name, p) in APP_WORD_PARAMS) {
                        paramTallies.getOrPut(name) { WordTally() }.add(id, AppWordMeasure.run(g, sg, sw, sh, stsv, p))
                    }
                    for (flt in AppWordMeasure.FILTERS) {
                        val aw = AppWordMeasure.run(g, sg, sw, sh, stsv, filter = flt)
                        wordTallies.getOrPut("앱 해상도(근사) · ${flt.name}") { WordTally() }.add(id, aw)
                        if (flt == AppWordMeasure.FILTERS.first()) {
                            val c = appCur
                            sideBySide += "| ${g.id} | ${c?.let { "${it.tp}/${it.fp}/${it.fn}" } ?: "—"} | ${aw.tp}/${aw.fp}/${aw.fn} | " +
                                "${pct(prec(aw.tp, aw.fp))} | ${pct(prec(aw.tp, aw.fn))} | ${aw.decoyFp["bleed"] ?: 0} | ${c?.offBodyRuns?.size ?: "—"} | ${aw.offBodyRuns} | ${aw.offBodySelected} | ${aw.inBodyUnmatchedSelected} | ${aw.unmatchedPageLines} |\n"
                            appWordOff += aw.offBodyWhere
                        }
                    }
                }
                details.append("\n### ${g.id} · ${r.boxSource}\n\n")
                details.append("- 오검출 어절: ${r.falsePositives.ifEmpty { listOf("0") }.joinToString(", ")}\n")
                details.append("- 미검출 어절: ${r.falseNegatives.ifEmpty { listOf("0") }.joinToString(", ")}\n")
                details.append("- 본문 밖 구간: ${r.offBodyRuns.ifEmpty { listOf("0") }.joinToString("; ")}\n")
                for (line in appOff) details.append("- 본문 밖 구간(앱 해상도 · $line)\n")
                for (line in appWordOff) details.append("- 본문 밖 미리 선택(앱 해상도 · 앱 어절 판정): $line\n")
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
        val pairs = tallies.values.first().per.size
        if (pairs > 0) {
            val skipped = if (excludedFromVariants.isEmpty()) "" else " · «⚠» 제외 ${excludedFromVariants.joinToString(", ")}"
            // 한 장에 기기 덤프 출처가 둘 이상이면 «장 × 출처» 벌이 장 수보다 많다 — 둘을 따로 적는다.
            val unit = if (pairs == variantIds.size) "${pairs}장 합계" else "${variantIds.size}장 · ${pairs}벌(장 × 출처) 합계"
            sb.append("\n## 검출기 설정 비교(기기 덤프 상자 · $unit$skipped)\n\n")
            sb.append("| 설정 | 정밀도 | 재현율 | 맞음/오검출/미검출 | 비침 오검출 | 본문 밖 오검출 구간 | 장별 맞음/오검출/미검출 |\n|---|---|---|---|---|---|---|\n")
            for ((v, t) in tallies) {
                val p = if (t.tp + t.fp == 0) null else t.tp.toDouble() / (t.tp + t.fp)
                val rc = if (t.tp + t.fn == 0) null else t.tp.toDouble() / (t.tp + t.fn)
                sb.append("| ${v.name} | ${pct(p)} | ${pct(rc)} | ${t.tp}/${t.fp}/${t.fn} | ${t.bleed} | ${t.off} | ${t.per.joinToString(" ")} |\n")
            }
        }
        if (wordTallies.isNotEmpty()) {
            sb.append("\n## 앱 어절 판정(엔진 어절 상자 · `UnderlinePreselect.select`의 미리 선택 값 그대로) — 제품 기준 수치\n\n")
            sb.append("엔진 어절 ↔ 정답 어절은 글자 맞춤(편집 거리)으로 잇는다. 정답 어절은 맞춰진 글자의 절반 이상이 미리 선택된 엔진 어절이면 선택으로 센다. ")
            sb.append("«본문 밖 미리 선택» = 정답 줄에 대응되지 않은 엔진 줄에서 미리 선택된 엔진 어절 수(사용자 화면에 선택됨으로 뜰 어절). «본문 줄 안 정답 밖 미리 선택» = 본문 줄에 묶였지만 정답 글자와 하나도 맞춰지지 않은 미리 선택 엔진 어절(같은 높이 여백 메모 · 자필 등 — 화면엔 선택됨). 앱 해상도는 2×2 칸 평균 · 상자 정수 나눗셈의 근사. 줄 제외 후보는 측정 전용이다(앱에 없음).\n\n")
            sb.append("| 해상도 · 줄 제외 | 정밀도 | 재현율 | 맞음/오검출/놓침 | 비침 오검출 | 본문 밖 구간 | 본문 밖 미리 선택 어절 | 본문 줄 안 정답 밖 미리 선택 어절 | 대응 실패 정답 줄 | 장별 맞음/오검출/놓침 | 장별 본문 밖 미리 선택 |\n|---|---|---|---|---|---|---|---|---|---|---|\n")
            for ((name, t) in wordTallies) {
                sb.append("| $name | ${pct(prec(t.tp, t.fp))} | ${pct(prec(t.tp, t.fn))} | ${t.tp}/${t.fp}/${t.fn} | ${t.bleed} | ${t.offRuns} | ${t.offSel} | ${t.inBody} | ${t.unmatchedLines} | ${t.per.joinToString(" ")} | ${t.offPer.joinToString(" ")} |\n")
            }
            sb.append("\n### 검출기 설정 비교(앱 해상도 · 앱 어절 판정 · 줄 제외 없음)\n\n")
            sb.append("| 설정 | 정밀도 | 재현율 | 맞음/오검출/놓침 | 비침 | 본문 밖 구간 | 본문 밖 선택 | 본문 줄 안 정답 밖 선택 | 장별 맞음/오검출/놓침 |\n|---|---|---|---|---|---|---|---|---|\n")
            for ((name, t) in paramTallies) {
                sb.append("| $name | ${pct(prec(t.tp, t.fp))} | ${pct(prec(t.tp, t.fn))} | ${t.tp}/${t.fp}/${t.fn} | ${t.bleed} | ${t.offRuns} | ${t.offSel} | ${t.inBody} | ${t.per.joinToString(" ")} |\n")
            }
            sb.append("\n### 장별 나란히(앱 해상도 · 줄 제외 없음): 글자 폭 비례 판정(위 «앱 해상도» 행) ↔ 앱 어절 판정\n\n")
            sb.append("| 자료 | 글자 폭 판정 맞음/오검출/놓침 | 앱 어절 판정 맞음/오검출/놓침 | 정밀도 | 재현율 | 비침 | 글자 폭 판정 본문 밖 구간 | 앱 판정 본문 밖 구간 | 본문 밖 미리 선택 어절 | 본문 줄 안 정답 밖 미리 선택 | 대응 실패 정답 줄 |\n|---|---|---|---|---|---|---|---|---|---|---|\n")
            for (row in sideBySide) sb.append(row)
        }
        sb.append("\n«⚠» 행은 수치가 없고 합계에서 빠진다 — 합계 분모는 «⚠» 없는 행의 맞음+오검출 · 맞음+미검출만 더한다(뺀 장 수를 합계 옆에 적는다).\n")
        sb.append("\n어절별 상세 = 같은 폴더 `_results/details.md`(레포 밖에만).\n")
        return Report(sb.toString(), details.toString())
    }
}
