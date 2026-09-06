package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ADR-004 1단계 ③. Room 스키마 JSON이 **현재 DB 버전과 함께** 커밋되어 있는지 지킨다.
 *
 * 이 앱은 아직 `fallbackToDestructiveMigration()`을 쓴다. 누군가 `version`만 올리고 마이그레이션을 붙이지 않으면
 * 기존 설치의 데이터가 조용히 전부 삭제된다. 그때 필요한 것이 "이전 버전이 정확히 어떤 스키마였는가"인데,
 * 그 정본은 버전을 올리기 **전에** 커밋되어 있어야만 쓸 수 있다. 이 테스트가 그 순서를 강제한다.
 */
class RoomSchemaExportTest {

  private val versionRegex = Regex("""@Database\([^)]*version\s*=\s*(\d+)""", RegexOption.DOT_MATCHES_ALL)

  private fun declaredVersion(): Int {
    val source = File("src/main/java/com/example/data/AppDatabase.kt")
    assertTrue("expected AppDatabase.kt at ${source.absolutePath}", source.isFile)
    val match = versionRegex.find(source.readText())
    assertTrue("could not read `version = N` from the @Database annotation", match != null)
    return match!!.groupValues[1].toInt()
  }

  @Test
  fun schemaJsonForTheCurrentVersionIsCommitted() {
    val version = declaredVersion()
    val schema = File("schemas/com.example.data.AppDatabase/$version.json")
    assertTrue(
      "DB 버전을 $version 으로 올렸다면 ${schema.path} 도 함께 커밋해야 한다. " +
        "`gradle :app:kspDebugKotlin` 을 돌리면 생성된다. 마이그레이션 없이 버전만 올리면 기존 사용자 데이터가 삭제된다.",
      schema.isFile
    )
    val text = schema.readText()
    assertTrue("$version.json 의 version 필드가 $version 이어야 한다", text.contains("\"version\": $version"))
  }

  @Test
  fun exportedSchemaMatchesTheEntitiesWeShip() {
    val text = File("schemas/com.example.data.AppDatabase/${declaredVersion()}.json").readText()
    listOf("bookcases", "books", "diaries").forEach {
      assertTrue("스키마에 $it 테이블이 없다", text.contains("\"tableName\": \"$it\""))
    }
    // 일기 본문 컬럼이 사라지면 사용자 기록이 사라진다는 뜻이다.
    listOf("selectedText", "notes", "createdAt").forEach {
      assertTrue("diaries 에 $it 컬럼이 없다", text.contains("\"columnName\": \"$it\""))
    }
  }

  @Test
  fun onlyOneSchemaVersionIsCommitted_soMigrationDebtIsVisible() {
    val dir = File("schemas/com.example.data.AppDatabase")
    val versions = dir.listFiles { f -> f.extension == "json" }.orEmpty().map { it.nameWithoutExtension }.sorted()
    // 버전이 둘 이상이면 마이그레이션이 실제로 필요하다는 신호다. 그때 이 테스트를 고치면서 Migration 을 함께 넣는다.
    assertEquals("스키마 버전이 늘었다면 Migration 과 fallbackToDestructiveMigration 제거가 같은 PR에 있어야 한다", 1, versions.size)
  }
}
