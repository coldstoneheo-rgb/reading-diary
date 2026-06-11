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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(diary: Diary): Long

    @Query("DELETE FROM diaries WHERE id = :id")
    suspend fun deleteById(id: Int)
}
