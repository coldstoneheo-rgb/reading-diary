package com.example

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.DiaryPhoto
import com.example.data.PhotoEdit
import com.example.data.PhotoSlot
import com.example.data.ReadingRepository
import com.example.data.photo.DiaryPhotoStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Room v2 → v3 마이그레이션(명세 PHRASE-RECALL-SPEC-2026-11 §3.4 M1~M7, ADR-006).
 *
 * 방식 = 명세 :206 대안. Robolectric 단위 테스트에서는 test 소스셋 assets가 MigrationTestHelper의 assets에 들어가지 않았다
 * (실측: «Missing file: com.example.data.AppDatabase/2.json»). 그래서 커밋된 `2.json`을 파일로 읽어 v2 DB를 손수 만든다 —
 * ① createSql의 `${TABLE_NAME}` 치환 ② setupQueries로 room_master_table + v2 identityHash ③ `PRAGMA user_version = 2`.
 * 그 파일을 앱과 같은 빌더([AppDatabase.builder])로 열면 Room이 MIGRATION_2_3을 돌리고, 컴파일된 v3 스키마와 테이블·컬럼·
 * 외래키·인덱스를 대조한다(불일치면 열 때 예외). M6은 RoomSchemaExportTest.migrationDebtIsVisible_whenMoreThanOneSchemaVersionExists.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MigrationTest {

  private val dbName = "migration-test.db"
  private val context: Context get() = ApplicationProvider.getApplicationContext()
  private val schemaDir = File("schemas/com.example.data.AppDatabase")
  private val opened = mutableListOf<AppDatabase>()

  @Before
  fun setUp() {
    context.deleteDatabase(dbName)
  }

  @After
  fun tearDown() {
    opened.forEach { it.close() }
    context.deleteDatabase(dbName)
  }

  private fun schema(version: Int): JSONObject = JSONObject(File(schemaDir, "$version.json").readText()).getJSONObject("database")

  /** 2.json 그대로 v2 DB 파일을 만든다(명세 :206 필수 3단계). */
  private fun createV2(seed: (SQLiteDatabase) -> Unit = {}) {
    val file = context.getDatabasePath(dbName).apply { parentFile!!.mkdirs() }
    val db = SQLiteDatabase.openOrCreateDatabase(file, null)
    try {
      val v2 = schema(2)
      val entities = v2.getJSONArray("entities")
      for (i in 0 until entities.length()) {
        val e = entities.getJSONObject(i)
        val table = e.getString("tableName")
        db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table)) // ①
        val indices = e.optJSONArray("indices")
        if (indices != null) for (j in 0 until indices.length()) {
          db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
        }
      }
      val setup = v2.getJSONArray("setupQueries")
      for (i in 0 until setup.length()) db.execSQL(setup.getString(i)) // ② room_master_table + v2 identityHash
      db.version = 2 // ③ PRAGMA user_version = 2
      seed(db)
    } finally {
      db.close()
    }
  }

  /** M1 데이터: 책장 2 · 책 3 · 일기 5(한글·이모지·빈 메모·과거 createdAt). */
  private fun seedRecords(db: SQLiteDatabase) {
    db.execSQL("INSERT INTO bookcases (id, name, isSystem) VALUES (1, '소설', 1), (2, '내 책장 📚', 0)")
    db.execSQL(
      "INSERT INTO books (id, title, author, totalPages, currentPage, coverUrl, bookcaseId, status, startDate, endDate, rating) VALUES " +
        "(1, '진달래꽃', '김소월', 120, 30, '', 1, 'READING', '2026-01-02', NULL, 4), " +
        "(2, '하늘과 바람과 별과 시', '윤동주', 200, 200, 'https://example.com/c.jpg', 1, 'COMPLETED', '2025-12-01', '2026-01-10', 5), " +
        "(3, 'Emoji 책 ✨', '', 0, 0, '', 2, 'TO_READ', '', NULL, 0)"
    )
    listOf(
      arrayOf<Any>(1, 1, 10, "나 보기가 역겨워 가실 때에는", "그리움을 적다", 1_000_000_000_000L),
      arrayOf<Any>(2, 1, 11, "말없이 고이 보내 드리우리다", "", 1_700_000_000_000L),
      arrayOf<Any>(3, 2, 5, "죽는 날까지 하늘을 우러러", "#윤동주 🌟 메모", 1_750_000_000_000L),
      arrayOf<Any>(4, 2, 6, "한 점 부끄럼이 없기를", "줄바꿈\n두 줄", 1_760_000_000_000L),
      arrayOf<Any>(5, 3, 0, "😀 이모지만 있는 구절", "", 0L),
    ).forEach { db.execSQL("INSERT INTO diaries (id, bookId, page, selectedText, notes, createdAt) VALUES (?, ?, ?, ?, ?, ?)", it) }
  }

  /** 행 바이트 대조용 스냅샷: 텍스트는 hex()로 비교한다. */
  private fun snapshot(query: (String) -> Cursor): List<String> {
    val out = mutableListOf<String>()
    fun dump(sql: String, prefix: String) = query(sql).use { c ->
      while (c.moveToNext()) out += prefix + (0 until c.columnCount).joinToString("|") { i -> if (c.isNull(i)) "NULL" else c.getString(i) }
    }
    dump("SELECT id, hex(name), isSystem FROM bookcases ORDER BY id", "bc:")
    dump("SELECT id, hex(title), hex(author), totalPages, currentPage, hex(coverUrl), bookcaseId, hex(status), hex(startDate), endDate, rating FROM books ORDER BY id", "bk:")
    dump("SELECT id, bookId, page, hex(selectedText), hex(notes), createdAt FROM diaries ORDER BY id", "dy:")
    return out
  }

  /** 앱과 같은 빌더 설정으로 연다 → 마이그레이션 실행 + v3 스키마 검증. */
  private fun openWithAppBuilder(): AppDatabase = AppDatabase.builder(context, dbName).build().also { opened += it }

  private fun raw(db: AppDatabase): SupportSQLiteDatabase = db.openHelper.writableDatabase

  private fun count(db: SupportSQLiteDatabase, table: String): Int =
    db.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

  private fun sqliteMasterSql(db: SupportSQLiteDatabase, name: String): String? =
    db.query("SELECT sql FROM sqlite_master WHERE name = ?", arrayOf<Any>(name)).use { if (it.moveToFirst()) it.getString(0) else null }

  @Test
  fun v2FixtureIsRecognizedAsVersion2() {
    createV2()
    val db = SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READONLY)
    try {
      assertEquals(2, db.version)
      db.rawQuery("SELECT identity_hash FROM room_master_table WHERE id = 42", null).use {
        assertTrue(it.moveToFirst())
        assertEquals(schema(2).getString("identityHash"), it.getString(0))
      }
    } finally {
      db.close()
    }
  }

  @Test
  fun m1_v2WithRecords_migratesAndMatches3Json() {
    createV2(::seedRecords)
    val db = raw(openWithAppBuilder()) // Room이 v3 스키마 검증에 실패하면 여기서 예외
    assertEquals(3, db.version)
    val v3 = schema(3)
    db.query("SELECT identity_hash FROM room_master_table WHERE id = 42").use {
      assertTrue(it.moveToFirst())
      assertEquals(v3.getString("identityHash"), it.getString(0))
    }
    val photos = (0 until v3.getJSONArray("entities").length()).map { v3.getJSONArray("entities").getJSONObject(it) }
      .single { it.getString("tableName") == "diary_photos" }
    assertEquals(photos.getString("createSql").replace("CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}`", "CREATE TABLE `diary_photos`"),
      sqliteMasterSql(db, "diary_photos"))
    assertEquals(photos.getJSONArray("indices").getJSONObject(0).getString("createSql")
      .replace("CREATE INDEX IF NOT EXISTS", "CREATE INDEX").replace("\${TABLE_NAME}", "diary_photos"),
      sqliteMasterSql(db, "index_diary_photos_diaryId"))
  }

  @Test
  fun m2_rowsAreByteIdentical_diariesKeepSixColumns_noPhotos() {
    var before = emptyList<String>()
    createV2 { db -> seedRecords(db); before = snapshot { sql -> db.rawQuery(sql, null) } }
    val db = raw(openWithAppBuilder())

    assertEquals("책장 2 + 책 3 + 일기 5", 10, before.size)
    assertEquals(before, snapshot { sql -> db.query(sql) })
    val columns = db.query("PRAGMA table_info(diaries)").use { c ->
      generateSequence { if (c.moveToNext()) c.getString(c.getColumnIndexOrThrow("name")) else null }.toList()
    }
    assertEquals(listOf("id", "bookId", "page", "selectedText", "notes", "createdAt"), columns)
    assertEquals(0, count(db, "diary_photos"))
  }

  @Test
  fun m3_foreignKeysHold_andBookDeleteCascadesToPhotos() {
    createV2(::seedRecords)
    val db = raw(openWithAppBuilder())
    db.query("PRAGMA foreign_keys").use { it.moveToFirst(); assertEquals("Room이 외래키를 켠다", 1, it.getInt(0)) }
    db.query("PRAGMA foreign_key_check").use { assertEquals("외래키 위반 0행", 0, it.count) }

    db.execSQL("INSERT INTO diary_photos (diaryId, path, sortOrder, createdAt) VALUES (1, 'a.jpg', 0, 1), (2, 'b.jpg', 0, 1), (3, 'c.jpg', 0, 1)")
    db.execSQL("DELETE FROM books WHERE id = 1")

    assertEquals("책 1의 일기 2건 CASCADE", 3, count(db, "diaries"))
    db.query("SELECT path FROM diary_photos").use { c ->
      assertEquals(1, c.count)
      assertTrue(c.moveToFirst())
      assertEquals("일기 → 사진 CASCADE: 책 2의 사진만 남는다", "c.jpg", c.getString(0))
    }
    val v3 = File(schemaDir, "3.json").readText()
    assertEquals("ON DELETE CASCADE 3개(books→bookcases, diaries→books, diary_photos→diaries)", 3, Regex("ON DELETE CASCADE").findAll(v3).count())
  }

  @Test
  fun m4_realBuilderOpensMigratedFile_withAllDiaries() = runBlocking {
    createV2(::seedRecords)
    val db = openWithAppBuilder()
    assertEquals(5, db.diaryDao().getAllDiaries().first().size)
    assertEquals(3, db.bookDao().getAllBooks().first().size)
  }

  @Test
  fun m5_editKeepsPhotos_setReordersAndDeletes_emptySetClears() = runBlocking {
    createV2(::seedRecords)
    val db = openWithAppBuilder()
    val deleted = mutableListOf<String>()
    val store = object : DiaryPhotoStore {
      override suspend fun save(source: android.net.Uri, rotationDegrees: Float, flipped: Boolean): String? = error("unused")
      override fun fileFor(relativePath: String): File? = null
      override suspend fun delete(relativePath: String) { deleted += relativePath }
      override suspend fun sweep(referenced: Set<String>): Int = 0
    }
    val repo = ReadingRepository(db, store)
    db.diaryPhotoDao().insertAll(listOf("a", "b", "c").mapIndexed { i, p -> DiaryPhoto(diaryId = 1, path = p, sortOrder = i) })
    val original = db.diaryDao().getAllDiaries().first().single { it.id == 1 }
    val ids = db.diaryPhotoDao().photosFor(1).associate { it.path to it.id }

    // (1) 텍스트만 고친 편집 + Unchanged → 사진 3행 그대로(REPLACE 편집이었다면 CASCADE로 0행 — F6)
    assertEquals(1, repo.saveDiary(original.copy(selectedText = "고친 문장"), PhotoEdit.Unchanged))
    assertEquals(listOf("a", "b", "c"), db.diaryPhotoDao().photosFor(1).map { it.path })
    assertEquals("고친 문장", db.diaryDao().getAllDiaries().first().single { it.id == 1 }.selectedText)
    assertEquals(emptyList<String>(), deleted)

    // (2) Set([Existing(b), New(x)]) → b(0)·x(1), a·c 행·파일 삭제. b는 원래 id 유지
    repo.saveDiary(original, PhotoEdit.Set(listOf(PhotoSlot.Existing(ids.getValue("b")), PhotoSlot.New("x"))))
    val afterSet = db.diaryPhotoDao().photosFor(1)
    assertEquals(listOf("b" to 0, "x" to 1), afterSet.map { it.path to it.sortOrder })
    assertEquals(ids.getValue("b"), afterSet.first().id)
    assertEquals(listOf("a", "c"), deleted.sorted())

    // (3) Set(emptyList()) → 0행, b·x 파일 삭제
    deleted.clear()
    repo.saveDiary(original, PhotoEdit.Set(emptyList()))
    assertEquals(0, db.diaryPhotoDao().photosFor(1).size)
    assertEquals(listOf("b", "x"), deleted.sorted())
    assertEquals("일기 수는 그대로", 5, db.diaryDao().getAllDiaries().first().size)
  }

  @Test
  fun m7_emptyV2Database_migrates() {
    createV2 { it.execSQL("INSERT INTO bookcases (name, isSystem) VALUES ('기본 책장', 1)") }
    val db = raw(openWithAppBuilder())
    assertEquals(1, count(db, "bookcases"))
    assertEquals(0, count(db, "diaries"))
    assertEquals(0, count(db, "diary_photos"))
  }
}
