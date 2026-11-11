package com.example.golden

import org.junit.Test
import java.io.File

/**
 * 측정 진입점. 레포 밖 기준 자료 폴더를 환경 변수로 받는다(없거나 비면 «자료 0장»으로 통과):
 *
 *   RD_GOLDEN_DIR='D:\사진\reading-diary\golden' RD_SPIKE_OUT='D:\사진\reading-diary\spike' \
 *     gradle :app:testDebugUnitTest --tests com.example.golden.GoldenMeasureTest --rerun
 *
 * 결과는 표준 출력과 `<RD_GOLDEN_DIR>/_results/measure.md`에 쓴다. 수치로 실패시키지 않는다(재기만 한다).
 */
class GoldenMeasureTest {
  @Test
  fun measure() {
    val path = System.getenv("RD_GOLDEN_DIR")?.takeIf { it.isNotBlank() }
    if (path == null) {
      println("RD_GOLDEN_DIR 없음 — 자료 0장")
      return
    }
    val dir = File(path)
    if (!dir.isDirectory) {
      println("RD_GOLDEN_DIR 폴더 없음($path) — 자료 0장")
      return
    }
    val spikeOut = System.getenv("RD_SPIKE_OUT")?.takeIf { it.isNotBlank() }?.let(::File)
    val report = GoldenMeasure.run(dir, spikeOut)
    println(report)
    File(dir, "_results").apply { mkdirs() }.resolve("measure.md").writeText(report)
  }
}
