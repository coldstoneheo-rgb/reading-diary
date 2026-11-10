package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Book
import com.example.data.Bookcase
import com.example.data.Diary
import com.example.data.DiaryPhoto
import com.example.data.backup.BackupArchive
import com.example.data.backup.BackupManifest
import com.example.data.backup.BackupRestore
import com.example.data.backup.RestoreOutcome
import com.example.data.photo.InternalDiaryPhotoStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 명세 §3.6 P5 테스트 7항: 왕복 바이트 동일 · 경로 탈출 거부 · manifest 없음/손상 거부 · 높은 Room 버전 거부 ·
 * 복원 중 실패 시 기존 기록 불변 · 키 저장소 파일이 zip에 없음 · 사진 빠진 zip → dangling. + 손상 DB·수 불일치 거부.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupArchiveTest {

  private val app: Application get() = ApplicationProvider.getApplicationContext()
  private lateinit var store: InternalDiaryPhotoStore
  private val scratch = mutableListOf<File>()

  private fun resetDatabase() {
    val field = AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
    (field.get(null) as AppDatabase?)?.close()
    field.set(null, null)
    app.deleteDatabase(AppDatabase.DB_NAME)
  }

  /** 새 DB를 열고 onCreate 콜백의 기본 책장 5개가 들어올 때까지 기다린다(복원과 경쟁하지 않게). */
  private fun freshDb(): AppDatabase {
    val db = AppDatabase.getDatabase(app)
    val deadline = System.currentTimeMillis() + 10_000
    while (runBlocking { db.bookcaseDao().getCount() } < 5 && System.currentTimeMillis() < deadline) Thread.sleep(20)
    assertEquals(5, runBlocking { db.bookcaseDao().getCount() })
    return db
  }

  @Before fun setUp() {
    resetDatabase()
    store = InternalDiaryPhotoStore(app)
    store.root.deleteRecursively()
  }

  @After fun tearDown() {
    resetDatabase()
    store.root.deleteRecursively()
    scratch.forEach { it.deleteRecursively() }
  }

  private fun tempDir(): File = kotlin.io.path.createTempDirectory("backup").toFile().also { scratch += it }

  private fun writePhoto(name: String, seed: Int): ByteArray {
    val bytes = ByteArray(5000) { ((it * 31 + seed) % 251).toByte() }
    store.root.mkdirs()
    File(store.root, name).writeBytes(bytes)
    return bytes
  }

  private data class Snapshot(val bookcases: List<Bookcase>, val books: List<Book>, val diaries: List<Diary>, val photos: List<DiaryPhoto>)

  private fun snapshot(db: AppDatabase): Snapshot = runBlocking {
    val dao = db.backupDao()
    Snapshot(dao.bookcases(), dao.books(), dao.diaries(), dao.photos())
  }

  /** 책장 1(사용자) · 책 2 · 일기 3 · 사진 행 3(그중 2개는 같은 일기). 사진 파일 3장. */
  private fun seed(db: AppDatabase, prefix: String): Map<String, ByteArray> = runBlocking {
    val caseId = db.bookcaseDao().insert(Bookcase(name = "$prefix 책장")).toInt()
    val b1 = db.bookDao().insert(Book(title = "$prefix 하나", author = "김", totalPages = 200, currentPage = 50, bookcaseId = caseId, status = "READING", rating = 3)).toInt()
    val b2 = db.bookDao().insert(Book(title = "$prefix 둘", author = "이", totalPages = 100, bookcaseId = caseId, status = "COMPLETED", endDate = "2026-11-01")).toInt()
    val d1 = db.diaryDao().insert(Diary(bookId = b1, page = 10, selectedText = "$prefix 구절\n둘째 줄", notes = "#태그", createdAt = 111L)).toInt()
    val d2 = db.diaryDao().insert(Diary(bookId = b1, page = 20, selectedText = "$prefix 다른 구절", notes = "", createdAt = 222L)).toInt()
    db.diaryDao().insert(Diary(bookId = b2, page = 5, selectedText = "$prefix 마지막", notes = "메모", createdAt = 333L))
    val names = listOf("$prefix-a.jpg", "$prefix-b.jpg", "$prefix-c.jpg")
    db.diaryPhotoDao().insertAll(
      listOf(DiaryPhoto(diaryId = d1, path = names[0], sortOrder = 0, createdAt = 1L), DiaryPhoto(diaryId = d1, path = names[1], sortOrder = 1, createdAt = 2L), DiaryPhoto(diaryId = d2, path = names[2], sortOrder = 0, createdAt = 3L))
    )
    names.mapIndexed { i, n -> n to writePhoto(n, i + prefix.length) }.toMap()
  }

  private fun backup(db: AppDatabase): ByteArray {
    val out = ByteArrayOutputStream()
    runBlocking { BackupRestore(app, db, store).createBackup(out, now = 1_000L) }
    return out.toByteArray()
  }

  private fun entries(zip: ByteArray): Map<String, ByteArray> {
    val map = LinkedHashMap<String, ByteArray>()
    ZipInputStream(ByteArrayInputStream(zip)).use { z ->
      while (true) { val e = z.nextEntry ?: break; map[e.name] = z.readBytes() }
    }
    return map
  }

  private fun zipOf(entries: Map<String, ByteArray>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { z -> entries.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
    return out.toByteArray()
  }

  private fun manifestJson(roomVersion: Int = 3) =
    BackupManifest(1, roomVersion, 1L, 0, 0, 0, 0, 0).toJson().toByteArray()

  private fun restore(db: AppDatabase, zip: ByteArray, beforeCommit: suspend () -> Unit = {}): RestoreOutcome =
    runBlocking { BackupRestore(app, db, store).restore(ByteArrayInputStream(zip), beforeCommit) }

  // 1. 왕복
  @Test
  fun roundTrip_restoresIdenticalRowsAndPhotoBytesIntoEmptyApp() {
    val db = freshDb()
    val photos = seed(db, "orig")
    val before = snapshot(db)
    val zip = backup(db)

    // 앱 데이터 삭제와 같은 상태: DB 파일·사진 폴더 모두 없음
    resetDatabase()
    store.root.deleteRecursively()
    val fresh = freshDb()

    val outcome = restore(fresh, zip)
    assertEquals(RestoreOutcome.Restored(books = 2, diaries = 3, photoRows = 3, photoFiles = 3), outcome)
    assertEquals(before, snapshot(fresh))
    photos.forEach { (name, bytes) -> assertArrayEquals(name, bytes, store.fileFor(name)!!.readBytes()) }
  }

  // 2. 경로 탈출
  @Test
  fun unsafeEntryPaths_areRejectedWithoutWritingOutside() {
    val db = freshDb()
    seed(db, "old")
    val before = snapshot(db)
    for (evil in listOf("../evil.jpg", "photos/../../evil.jpg", "/abs/evil.jpg", "photos\\..\\evil.jpg", "C:/evil.jpg", "photos/sub/evil.jpg")) {
      val zip = zipOf(linkedMapOf(BackupArchive.MANIFEST to manifestJson(), evil to byteArrayOf(1, 2, 3)))
      val outcome = restore(db, zip)
      assertTrue("$evil → $outcome", outcome is RestoreOutcome.Rejected)
      assertFalse(File(app.cacheDir, "evil.jpg").exists())
      assertFalse(File(app.cacheDir.parentFile, "evil.jpg").exists())
    }
    assertEquals(before, snapshot(db))
    assertTrue(BackupArchive.isSafeName("photos/a.jpg"))
    assertFalse(BackupArchive.isSafeName("photos/../a.jpg"))
  }

  // 3. manifest 없음 / 손상
  @Test
  fun missingOrCorruptManifest_isRejected() {
    val db = freshDb()
    val good = entries(backup(db))
    val dir1 = tempDir()
    val noManifest = zipOf(good.filterKeys { it != BackupArchive.MANIFEST })
    assertEquals(BackupArchive.Result.Invalid(BackupArchive.Reason.NO_MANIFEST), BackupArchive.extract(ByteArrayInputStream(noManifest), dir1, 3, 2))
    val dir2 = tempDir()
    val corrupt = zipOf(good + (BackupArchive.MANIFEST to "{ not json".toByteArray()))
    assertEquals(BackupArchive.Result.Invalid(BackupArchive.Reason.BAD_MANIFEST), BackupArchive.extract(ByteArrayInputStream(corrupt), dir2, 3, 2))
    val dir3 = tempDir()
    assertEquals(BackupArchive.Result.Invalid(BackupArchive.Reason.NOT_ZIP), BackupArchive.extract(ByteArrayInputStream("plain text".toByteArray()), dir3, 3, 2))
  }

  // 4. 앱보다 높은 Room 버전
  @Test
  fun newerRoomVersion_isRejected_andRecordsUnchanged() {
    val db = freshDb()
    seed(db, "old")
    val before = snapshot(db)
    val good = entries(backup(db))
    val newer = zipOf(good + (BackupArchive.MANIFEST to manifestJson(roomVersion = BackupRestore.ROOM_VERSION + 1)))
    assertEquals(RestoreOutcome.Rejected(BackupArchive.Reason.NEWER_VERSION), restore(db, newer))
    assertEquals(before, snapshot(db))
  }

  // 5. 복원 중 실패 → 기존 기록·사진 불변, 이번에 들여온 사진은 지움
  @Test
  fun failureInsideReplaceTransaction_leavesExistingRecordsAndPhotosUntouched() {
    val db = freshDb()
    seed(db, "bak")
    val zip = backup(db)
    resetDatabase(); store.root.deleteRecursively()
    val current = freshDb()
    val currentPhotos = seed(current, "cur")
    val before = snapshot(current)

    val outcome = restore(current, zip) { throw IllegalStateException("injected") }
    assertEquals(RestoreOutcome.Failed, outcome)
    assertEquals(before, snapshot(current))
    currentPhotos.forEach { (name, bytes) -> assertArrayEquals(bytes, store.fileFor(name)!!.readBytes()) }
    assertEquals(currentPhotos.keys, store.root.listFiles()!!.map { it.name }.toSet())
  }

  @Test
  fun corruptDatabaseOrCountMismatch_isRejected_andRecordsUnchanged() {
    val db = freshDb()
    seed(db, "old")
    val before = snapshot(db)
    val good = entries(backup(db))
    val badDb = zipOf(good + (BackupArchive.DB_ENTRY to ByteArray(4096) { 7 }))
    assertTrue(restore(db, badDb) is RestoreOutcome.Rejected)
    val manifest = BackupManifest.parse(String(good.getValue(BackupArchive.MANIFEST)))!!
    val mismatch = zipOf(good + (BackupArchive.MANIFEST to manifest.copy(diaries = manifest.diaries + 1).toJson().toByteArray()))
    assertEquals(RestoreOutcome.Rejected(BackupArchive.Reason.BAD_MANIFEST), restore(db, mismatch))
    assertEquals(before, snapshot(db))
  }

  // 6. 키 저장소·설정 파일이 zip에 없다
  @Test
  fun zipContainsOnlyManifestDatabaseAndPhotos() {
    val db = freshDb()
    seed(db, "key")
    // 설정 파일이 실제로 있어도 들어가지 않는다
    app.getSharedPreferences("diary_general_settings", 0).edit().putBoolean("x", true).commit()
    val names = entries(backup(db)).keys
    assertTrue(names.all { it == BackupArchive.MANIFEST || it == BackupArchive.DB_ENTRY || it.startsWith(BackupArchive.PHOTO_DIR) })
    assertTrue(names.none { it.contains("shared_prefs") || it.contains("secure", ignoreCase = true) || it.endsWith(".xml") })
    assertEquals(3, names.count { it.startsWith(BackupArchive.PHOTO_DIR) })
  }

  // 7. 사진 빠진 zip → 행은 복원, 파일은 «사진 없음»
  @Test
  fun zipMissingAPhoto_restoresRowAsDangling() {
    val db = freshDb()
    seed(db, "orig")
    val good = entries(backup(db))
    val dropped = "photos/orig-b.jpg"
    assertTrue(dropped in good.keys)
    val zip = zipOf(good - dropped)
    resetDatabase(); store.root.deleteRecursively()
    val fresh = freshDb()

    val outcome = restore(fresh, zip)
    assertEquals(RestoreOutcome.Restored(books = 2, diaries = 3, photoRows = 3, photoFiles = 2), outcome)
    assertTrue(snapshot(fresh).photos.any { it.path == "orig-b.jpg" })
    assertNull(store.fileFor("orig-b.jpg"))
    assertTrue(store.fileFor("orig-a.jpg") != null)
  }

  @Test
  fun restoreReplacesInsteadOfMerging_andRemovesOldPhotoFiles() {
    val db = freshDb()
    seed(db, "bak")
    val zip = backup(db)
    val backupState = snapshot(db)
    resetDatabase(); store.root.deleteRecursively()
    val current = freshDb()
    seed(current, "cur")
    assertTrue(restore(current, zip) is RestoreOutcome.Restored)
    assertEquals(backupState, snapshot(current))
    assertEquals(setOf("bak-a.jpg", "bak-b.jpg", "bak-c.jpg"), store.root.listFiles()!!.map { it.name }.toSet())
  }

  @Test
  fun roomVersionConstantMatchesLatestExportedSchema() {
    val latest = File("schemas/com.example.data.AppDatabase").listFiles()!!.mapNotNull { it.nameWithoutExtension.toIntOrNull() }.max()
    assertEquals(latest, BackupRestore.ROOM_VERSION)
  }
}
