package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Book
import com.example.data.Bookcase
import com.example.data.Diary
import com.example.data.DiaryPhoto
import com.example.data.PhotoEdit
import com.example.data.PhotoSlot
import com.example.data.ReadingRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 명세 §3.5 삭제 연동: 지운 기록의 사진 파일만 지우고, 지우지 않은 쪽의 파일은 남긴다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhotoDeleteLinkTest {

  private lateinit var db: AppDatabase
  private lateinit var store: FakeDiaryPhotoStore
  private lateinit var repo: ReadingRepository
  private var userCase = 0
  private var otherCase = 0
  private var book = 0
  private var otherBook = 0
  private var diary = 0
  private var sibling = 0
  private var otherDiary = 0

  @Before
  fun setUp() = runBlocking {
    db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    store = FakeDiaryPhotoStore()
    repo = ReadingRepository(db, store)
    userCase = db.bookcaseDao().insert(Bookcase(name = "내 책장", isSystem = false)).toInt()
    otherCase = db.bookcaseDao().insert(Bookcase(name = "다른 책장", isSystem = false)).toInt()
    book = db.bookDao().insert(Book(title = "책", author = "", totalPages = 10, bookcaseId = userCase, status = "READING")).toInt()
    otherBook = db.bookDao().insert(Book(title = "다른 책", author = "", totalPages = 10, bookcaseId = otherCase, status = "READING")).toInt()
    diary = db.diaryDao().insert(Diary(bookId = book, page = 1, selectedText = "가", notes = "")).toInt()
    sibling = db.diaryDao().insert(Diary(bookId = book, page = 2, selectedText = "나", notes = "")).toInt()
    otherDiary = db.diaryDao().insert(Diary(bookId = otherBook, page = 3, selectedText = "다", notes = "")).toInt()
    store.seed("a.jpg", "b.jpg", "s.jpg", "o.jpg")
    db.diaryPhotoDao().insertAll(
      listOf(
        DiaryPhoto(diaryId = diary, path = "a.jpg", sortOrder = 0),
        DiaryPhoto(diaryId = diary, path = "b.jpg", sortOrder = 1),
        DiaryPhoto(diaryId = sibling, path = "s.jpg", sortOrder = 0),
        DiaryPhoto(diaryId = otherDiary, path = "o.jpg", sortOrder = 0)
      )
    )
  }

  @After
  fun tearDown() = db.close()

  @Test
  fun deleteDiary_removesOnlyItsFiles() = runBlocking {
    repo.deleteDiary(diary)
    assertEquals(setOf("a.jpg", "b.jpg"), store.deleted.toSet())
    assertEquals(setOf("s.jpg", "o.jpg"), db.diaryPhotoDao().allPaths().toSet())
  }

  @Test
  fun deleteBook_removesFilesOfAllItsDiaries_keepsOtherBook() = runBlocking {
    repo.deleteBook(book)
    assertEquals(setOf("a.jpg", "b.jpg", "s.jpg"), store.deleted.toSet())
    assertEquals(listOf("o.jpg"), db.diaryPhotoDao().allPaths())
  }

  @Test
  fun deleteBookcase_removesFilesOfItsBooks_keepsOtherBookcase() = runBlocking {
    repo.deleteBookcase(userCase)
    assertEquals(setOf("a.jpg", "b.jpg", "s.jpg"), store.deleted.toSet())
    assertEquals(listOf("o.jpg"), db.diaryPhotoDao().allPaths())
  }

  /** 시스템 책장은 DAO가 지우지 않는다. 그때 파일만 지우면 행은 남고 사진만 사라진다. */
  @Test
  fun deleteSystemBookcase_isNoOp_keepsFiles() = runBlocking {
    val system = db.bookcaseDao().insert(Bookcase(name = "기본 책장", isSystem = true)).toInt()
    val b = db.bookDao().insert(Book(title = "시스템 책", author = "", totalPages = 10, bookcaseId = system, status = "READING")).toInt()
    val d = db.diaryDao().insert(Diary(bookId = b, page = 1, selectedText = "라", notes = "")).toInt()
    store.seed("sys.jpg")
    db.diaryPhotoDao().insertAll(listOf(DiaryPhoto(diaryId = d, path = "sys.jpg", sortOrder = 0)))

    repo.deleteBookcase(system)

    assertTrue("지워진 파일 없음: ${store.deleted}", store.deleted.isEmpty())
    assertTrue(db.diaryPhotoDao().allPaths().contains("sys.jpg"))
  }

  /** critic 지적: 트랜잭션 원자성과 «파일은 커밋 뒤 삭제»를 고정한다. */
  @Test
  fun saveFailure_rollsBackTextAndPhotoRows_andDeletesNoFile() = runBlocking {
    val photos = db.diaryPhotoDao().photosFor(diary)
    db.openHelper.writableDatabase.execSQL(
      "CREATE TRIGGER fail_photo_insert BEFORE INSERT ON diary_photos BEGIN SELECT RAISE(ABORT, 'disk full'); END"
    )
    val original = Diary(id = diary, bookId = book, page = 1, selectedText = "고친 글", notes = "")

    val failed = runCatching {
      repo.saveDiary(original, PhotoEdit.Set(listOf(PhotoSlot.Existing(photos[1].id), PhotoSlot.New("x.jpg"))))
    }

    assertTrue("저장은 실패해야 한다", failed.isFailure)
    assertEquals(photos, db.diaryPhotoDao().photosFor(diary))
    assertTrue("빠진 사진 파일도 지우면 안 된다: ${store.deleted}", store.deleted.isEmpty())
    assertEquals(
      "글도 함께 되돌아간다",
      "가",
      db.openHelper.writableDatabase.query("SELECT selectedText FROM diaries WHERE id = $diary").use { it.moveToFirst(); it.getString(0) }
    )
  }
}
