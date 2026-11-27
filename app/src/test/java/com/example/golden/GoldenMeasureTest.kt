package com.example.golden

import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 측정 진입점. 레포 밖 기준 자료 폴더를 환경 변수로 받는다(없거나 비면 «자료 0장»으로 통과).
 * 여러 폴더를 `;`로 이어 주면(모든 OS — Windows 경로의 `:`와 겹치지 않게) 한 실행에서 차례로 잰다 — 기준 271 · 사용자 사진(틀 없음 · 사용자가
 * 실제로 자른 틀) · 테스터 견본을 늘 같이 재서, 틀 없는 값만 보고 실제 화면 값을 놓치는 일을 막는다(`scripts/measure-golden.sh`):
 *
 *   RD_GOLDEN_DIR='<폴더1>;<폴더2>' RD_SPIKE_OUT='<오버레이 폴더>' \
 *     gradle :app:testDebugUnitTest --tests com.example.golden.GoldenMeasureTest --rerun
 *
 * 표는 표준 출력과 `<폴더>/_results/measure.md`에, 어절별 상세는 `_results/details.md`(레포 밖에만)에 쓴다. 표 첫 줄에 잰 코드의
 * head SHA(`RD_HEAD_SHA`, 없으면 `git rev-parse`)와 실행 시각을 남긴다. 수치로 실패시키지 않는다(재기만 한다).
 */
class GoldenMeasureTest {
  @Test
  fun measure() {
    val raw = System.getenv("RD_GOLDEN_DIR")?.takeIf { it.isNotBlank() }
    if (raw == null) {
      println("RD_GOLDEN_DIR 없음 — 자료 0장")
      return
    }
    val stamp = "> 측정 실행: head `${headSha()}` · ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}\n\n"
    val spikeOut = System.getenv("RD_SPIKE_OUT")?.takeIf { it.isNotBlank() }?.let(::File)
    for (path in raw.split(";").map { it.trim() }.filter { it.isNotEmpty() }) {
      val dir = File(path)
      if (!dir.isDirectory) {
        println("RD_GOLDEN_DIR 폴더 없음($path) — 자료 0장")
        continue
      }
      val report = GoldenMeasure.run(dir, spikeOut)
      println(stamp + report.table) // 표만 — 상세(본문 조각)는 콘솔·빌드 로그에 남기지 않는다
      val out = File(dir, "_results").apply { mkdirs() }
      out.resolve("measure.md").writeText(stamp + report.table)
      out.resolve("details.md").writeText(stamp + report.details)
    }
  }

  private fun headSha(): String {
    System.getenv("RD_HEAD_SHA")?.takeIf { it.isNotBlank() }?.let { return it }
    return runCatching {
      val p = ProcessBuilder("git", "rev-parse", "--short", "HEAD").redirectErrorStream(true).start()
      val s = p.inputStream.bufferedReader().readText().trim()
      if (p.waitFor() != 0 || s.isEmpty()) return "모름"
      val st = ProcessBuilder("git", "status", "--porcelain", "--", "src").redirectErrorStream(true).start()
      val dirty = st.inputStream.bufferedReader().readText().isNotBlank()
      st.waitFor()
      if (dirty) "$s+커밋 안 한 변경" else s
    }.getOrDefault("모름")
  }
}
