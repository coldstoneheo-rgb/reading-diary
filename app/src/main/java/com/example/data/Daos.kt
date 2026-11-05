package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookcaseDao {
    @Query("SELECT * FROM bookcases ORDER BY isSystem DESC, id ASC")
    fun getAllBookcases(): Flow<List<Bookcase>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookcase: Bookcase): Long

    @Update
    suspend fun update(bookcase: Bookcase)

    @Query("DELETE FROM bookcases WHERE id = :id AND isSystem = 0")
    suspend fun deleteById(id: Int)

    @Query("SELECT COUNT(*) FROM bookcases")
    suspend fun getCount(): Int

    @Query("SELECT * FROM bookcases ORDER BY isSystem DESC, id ASC")
    suspend fun getAllBookcasesOneShot(): List<Bookcase>
}

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY id DESC")
    fun getAllBooks(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun getBookById(id: Int): Flow<Book?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookByIdOneShot(id: Int): Book?

    @Query("SELECT * FROM books WHERE bookcaseId = :bookcaseId")
    fun getBooksByBookcase(bookcaseId: Int): Flow<List<Book>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: Book): Long

    @Update
    suspend fun update(book: Book)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: Int)
}

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diaries ORDER BY createdAt DESC")
    fun getAllDiaries(): Flow<List<Diary>>

    @Query("SELECT * FROM diaries WHERE bookId = :bookId ORDER BY page ASC, createdAt DESC")
    fun getDiariesForBook(bookId: Int): Flow<List<Diary>>

    /** 새 행 전용. 기존 id를 넘기면 실패한다(REPLACE는 삭제 단계가 사진 행을 CASCADE로 지운다, F6). 편집은 [update]. */
    @Insert
    suspend fun insert(diary: Diary): Long

    /**
     * 편집 저장. REPLACE(삭제 후 삽입)로 편집하면 삭제 단계가 자식 행의 ON DELETE CASCADE를 일으킨다(F6) — 편집은 반드시 이것으로.
     * 반환 = 바뀐 행 수(행이 없으면 0).
     */
    @Update
    suspend fun update(diary: Diary): Int

    @Query("DELETE FROM diaries WHERE id = :id")
    suspend fun deleteById(id: Int)
}

@Dao
interface DiaryPhotoDao {
    /** 화면 표시 순서(sortOrder) 그대로. */
    @Query("SELECT * FROM diary_photos WHERE diaryId = :diaryId ORDER BY sortOrder ASC, id ASC")
    suspend fun photosFor(diaryId: Int): List<DiaryPhoto>

    @Insert
    suspend fun insertAll(photos: List<DiaryPhoto>)

    @Query("DELETE FROM diary_photos WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Int>)

    /** 고아 파일 정리(DiaryPhotoStore.sweep)의 참조 집합. */
    @Query("SELECT path FROM diary_photos")
    suspend fun allPaths(): List<String>

    /** 회상 화면용 전체 목록. 일기별로 묶으면 그대로 표시 순서다. */
    @Query("SELECT * FROM diary_photos ORDER BY diaryId ASC, sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<DiaryPhoto>>

    // 삭제 연동: CASCADE가 행을 지우기 전에 파일 경로를 먼저 읽는다.
    @Query("SELECT path FROM diary_photos WHERE diaryId = :diaryId")
    suspend fun pathsForDiary(diaryId: Int): List<String>

    @Query("SELECT p.path FROM diary_photos p INNER JOIN diaries d ON p.diaryId = d.id WHERE d.bookId = :bookId")
    suspend fun pathsForBook(bookId: Int): List<String>

    /** [BookcaseDao.deleteById]와 같은 조건(시스템 책장은 지워지지 않으므로 그 사진 파일도 대상이 아니다). */
    @Query(
        "SELECT p.path FROM diary_photos p INNER JOIN diaries d ON p.diaryId = d.id " +
            "INNER JOIN books b ON d.bookId = b.id INNER JOIN bookcases c ON b.bookcaseId = c.id " +
            "WHERE c.id = :bookcaseId AND c.isSystem = 0"
    )
    suspend fun pathsForDeletableBookcase(bookcaseId: Int): List<String>
}
