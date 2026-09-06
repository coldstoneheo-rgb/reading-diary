package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ADR-004 1단계 ③. Room 스키마 JSON이 **현재 DB 버전과 함께** 커밋되어 있고, 그 버전 안에서 **드리프트하지 않는지** 지킨다.
 *
 * 이 앱은 아직 `fallbackToDestructiveMigration()`을 쓴다. 두 가지 사고를 막아야 한다.
 * 1. `version`만 올리고 마이그레이션을 안 붙이면 기존 설치의 데이터가 조용히 전부 삭제된다.
 *    그때 필요한 "이전 버전의 정확한 스키마"는 버전을 올리기 **전에** 커밋되어 있어야만 쓸 수 있다.
 * 2. **버전을 그대로 둔 채 엔티티를 바꾸면** KSP가 `2.json`을 조용히 덮어쓰고, 기존 기기에서는
 *    identityHash 불일치로 DB를 열 때 크래시한다. `fallbackToDestructiveMigration`도 이건 구제하지 못한다.
 *    신규 설치에서는 재현되지 않아 수동 테스트를 통과하므로, 여기서 리터럴로 고정해 컴파일 타임에 잡는다.
 */
class RoomSchemaExportTest {

  /** 애노테이션 인자에 `AutoMigration(...)` 같은 괄호가 들어와도 버전을 읽도록 lazy dot-all을 쓴다. */
  private val versionRegex = Regex("""@Database\((?s).*?version\s*=\s*(\d+)""")

  private val appDatabaseSource = File("src/main/java/com/example/data/AppDatabase.kt")
  private val schemaDir = File("schemas/com.example.data.AppDatabase")

  private fun declaredVersion(): Int {
    assertTrue("expected AppDatabase.kt at ${appDatabaseSource.absolutePath}", appDatabaseSource.isFile)
    val match = versionRegex.find(appDatabaseSource.readText())
    assertTrue("could not read `version = N` from the @Database annotation", match != null)
    return match!!.groupValues[1].toInt()
  }

  private fun schemaText(): String {
    val version = declaredVersion()
    val schema = File(schemaDir, "$version.json")
    assertTrue(
      "DB 버전을 $version 으로 올렸다면 ${schema.path} 도 함께 커밋해야 한다. " +
        "`gradle :app:kspDebugKotlin` 을 돌리면 생성된다. 마이그레이션 없이 버전만 올리면 기존 사용자 데이터가 삭제된다.",
      schema.isFile
    )
    return schema.readText()
  }

  @Test
  fun schemaJsonForTheCurrentVersionIsCommitted() {
    val version = declaredVersion()
    assertTrue("$version.json 의 version 필드가 $version 이어야 한다", schemaText().contains("\"version\": $version"))
  }

  /**
   * 버전 내 드리프트 탐지. 엔티티를 바꾸면 identityHash가 바뀌어 여기서 먼저 실패한다.
   * **이 값을 그냥 갱신하지 말 것.** 실패했다면 선택지는 둘뿐이다:
   * (a) 엔티티 변경을 되돌린다, (b) `version`을 올리고 `Migration`을 붙이며 `fallbackToDestructiveMigration`을 제거한다.
   */
  @Test
  fun schemaDoesNotDriftWithinTheSameVersion() {
    assertTrue(
      "엔티티가 바뀌어 v${declaredVersion()} 스키마가 달라졌다. 버전을 올리고 Migration 을 붙이거나 변경을 되돌려라. " +
        "이 해시를 갱신하는 것으로 해결하지 말 것 — 기존 기기는 DB 를 열 때 크래시한다.",
      schemaText().contains("\"identityHash\": \"37116ccbab73af381a7228fbf099a9db\"")
    )
  }

  @Test
  fun exportedSchemaKeepsTheColumnsThatHoldUserRecords() {
    val text = schemaText()
    // 테이블 소속까지 확인하려면 createSql 을 통째로 본다. 파일 어딘가에 컬럼명만 있는 것으로는 부족하다.
    assertTrue(
      "diaries 의 사용자 기록 컬럼(selectedText·notes·createdAt)이 사라졌다",
      text.contains("`selectedText` TEXT NOT NULL, `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL")
    )
    // 책·책장을 지우면 하위 기록도 함께 지워진다는 계약. 이게 깨지면 고아 행이 남는다.
    assertEquals(
      "ON DELETE CASCADE 외래키가 2개(books→bookcases, diaries→books)여야 한다",
      2,
      Regex("ON DELETE CASCADE").findAll(text).count()
    )
  }

  @Test
  fun migrationDebtIsVisible_whenMoreThanOneSchemaVersionExists() {
    val versions = schemaDir.listFiles { f -> f.extension == "json" }.orEmpty()
    if (versions.size <= 1) return // 아직 마이그레이션이 필요하지 않다
    // 버전이 둘 이상이면 실제 마이그레이션이 있다는 뜻이다. 파괴적 폴백이 남아 있으면 안 된다.
    assertTrue(
      "스키마 버전이 ${versions.size}개인데 fallbackToDestructiveMigration 이 남아 있다. " +
        "마이그레이션 실패가 조용한 전체 삭제가 된다.",
      !appDatabaseSource.readText().contains("fallbackToDestructiveMigration")
    )
  }
}
