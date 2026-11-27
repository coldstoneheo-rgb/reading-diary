package com.example

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R8 구성 고정(2번째 빌드 · 2026-11-27 r8Debug 에뮬레이터 실행 점검).
 * ML Kit는 등록기(…Registrar)를 리플렉션으로 인자 없는 생성자에서 만든다. R8 full mode가 그 생성자를 지우면
 * 디버그 빌드 · 단위 시험은 모두 통과하는데 출시 빌드에서만 글자 인식이 오류, 바코드 스캐너가 조용히 안 열린다.
 * 단위 시험으로는 R8 결과를 볼 수 없으므로 규칙 줄과 «실행 점검 빌드가 release와 같은 규칙을 읽는다»를 소스로 고정한다.
 */
class R8RulesPolicyTest {

  private val rules = File("proguard-rules.pro")
  private val gradle = File("build.gradle.kts")

  @Test
  fun keepsMlKitRegistrarConstructors() {
    val lines = rules.readLines().map { it.trim() }.filterNot { it.startsWith("#") }
    assertTrue("proguard-rules.pro에 ML Kit 등록기 생성자 유지 규칙이 없다", "-keep class com.google.mlkit.**Registrar { <init>(); }" in lines)
  }

  @Test
  fun r8DebugIsReleaseWithTheDebugKey() {
    val code = gradle.readText()
    val block = Regex("create\\(\"r8Debug\"\\)\\s*\\{([^}]*)\\}").find(code)?.groupValues?.get(1)
    assertTrue("r8Debug 빌드 타입이 없다", block != null)
    assertTrue("r8Debug는 release 구성을 그대로 이어받아야 한다", block!!.contains("initWith(getByName(\"release\"))"))
    assertTrue("r8Debug는 디버그 키로만 서명한다", block.contains("signingConfigs.getByName(\"debugConfig\")"))
    assertTrue("r8Debug에서 R8 · 규칙을 따로 바꾸지 않는다", !block.contains("isMinifyEnabled") && !block.contains("proguardFiles"))
  }
}
