package com.example

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ADR-003: 화면 문구가 앱이 실제로 하는 일과 어긋나지 않게 고정한다.
 * 가짜 백업·복원(Q7)처럼 "동작 없이 성공을 말하는" 문구가 프로덕션 소스에 다시 들어오면 실패한다.
 * 소스 grep 방식은 GeminiKeyPolicyTest.geminiClient_isNotReferencedByAnyProductionPath와 같다.
 */
class HonestCopyTest {

  /** 프로덕션 소스와 리소스(문구가 strings.xml로 옮겨져도 잡히도록). */
  private fun mainSources(): List<File> {
    val mainSrc = File("src/main")
    assertTrue("expected app/src/main to exist", mainSrc.isDirectory)
    return mainSrc.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }.toList()
  }

  @Test
  fun noFakeBackupOrRestoreCopyInProductionSources() {
    // 제거된 가짜 기능의 문구. 실제 백업/복원 구현이 들어오는 PR에서만 이 목록을 줄일 수 있다.
    val forbidden = listOf(
      "클라우드 백업 및 복원",
      "데이터 백업 및 복원",
      "클라우드 동기화",
      "동기화 실행",
      "백업 동기화가 안전하게 완료",
      "복원 진행하기",
      "클라우드 아카이브",
      "iCloud",
      "runSimulatedProgress"
    )
    val offenders = mainSources().flatMap { file ->
      val text = file.readText()
      forbidden.filter { text.contains(it) }.map { "${file.path}: \"$it\"" }
    }
    assertTrue("fake backup/restore copy found:\n" + offenders.joinToString("\n"), offenders.isEmpty())
  }

  /** ADR-003 Q6: 기억 서랍은 LLM 생성도, 타사 앱 연동도 하지 않는다. 그렇게 읽히는 단어를 app/src/main에서 금지한다. */
  @Test
  fun noOverstatedFeatureVocabularyInProductionSources() {
    val forbidden = listOf(
      Regex("\\bRAG\\b", RegexOption.IGNORE_CASE), Regex("Obsidian", RegexOption.IGNORE_CASE), Regex("옵시디언"),
      Regex("Logseq", RegexOption.IGNORE_CASE), Regex("뇌세포"), Regex("브레인 링킹"), Regex("매칭 연동률"),
      Regex("연결 성공 지식"), Regex("지능형 기억"), Regex("v1\\.2\\.0")
    )
    val offenders = mainSources().flatMap { file ->
      val text = file.readText()
      forbidden.filter { it.containsMatchIn(text) }.map { "${file.path}: /${it.pattern}/" }
    }
    assertTrue("overstated vocabulary found:\n" + offenders.joinToString("\n"), offenders.isEmpty())
  }
}
