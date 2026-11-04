package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Book
import com.example.data.Bookcase
import com.example.data.Diary
import com.example.data.DiaryPhoto
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** DiaryPhotoDao(Room v3) — insertAll → photosFor 정렬 → deleteByIds → allPaths, 일기 삭제 CASCADE. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DiaryPhotoDaoTest {

  private lateinit var db: AppDatabase
  private var diaryA = 0
  private var diaryB = 0

  @Before
  fun setUp() = runBlocking {
    db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    val caseId = db.bookcaseDao().insert(Bookcase(name = "책장")).toInt()
    val bookId = db.bookDao().insert(Book(title = "책", author = "", totalPages = 10, bookcaseId = caseId, status = "READING")).toInt()
    diaryA = db.diaryDao().insert(Diary(bookId = bookId, page = 1, selectedText = "가", notes = "")).toInt()
    diaryB = db.diaryDao().insert(Diary(bookId = bookId, page = 2, selectedText = "나", notes = "")).toInt()
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun insertAll_photosForOrdersBySortOrder_deleteByIds_allPaths() = runBlocking {
    val dao = db.diaryPhotoDao()
    dao.insertAll(
      listOf(
        DiaryPhoto(diaryId = diaryA, path = "a2.jpg", sortOrder = 2),
        DiaryPhoto(diaryId = diaryA, path = "a0.jpg", sortOrder = 0),
        DiaryPhoto(diaryId = diaryA, path = "a1.jpg", sortOrder = 1),
        DiaryPhoto(diaryId = diaryB, path = "b0.jpg", sortOrder = 0),
      )
    )

    val a = dao.photosFor(diaryA)
    assertEquals(listOf("a0.jpg", "a1.jpg", "a2.jpg"), a.map { it.path })
    assertEquals(listOf("b0.jpg"), dao.photosFor(diaryB).map { it.path })

    dao.deleteByIds(listOf(a[0].id, a[2].id))

    assertEquals(listOf("a1.jpg"), dao.photosFor(diaryA).map { it.path })
    assertEquals(setOf("a1.jpg", "b0.jpg"), dao.allPaths().toSet())
  }

  @Test
  fun deletingDiary_cascadesItsPhotoRowsOnly() = runBlocking {
    val dao = db.diaryPhotoDao()
    dao.insertAll(listOf(DiaryPhoto(diaryId = diaryA, path = "a.jpg", sortOrder = 0), DiaryPhoto(diaryId = diaryB, path = "b.jpg", sortOrder = 0)))

    db.diaryDao().deleteById(diaryA)

    assertEquals(listOf("b.jpg"), dao.allPaths())
  }
}
