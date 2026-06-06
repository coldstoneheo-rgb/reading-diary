package com.example.data

import kotlinx.coroutines.flow.Flow

class ReadingRepository(private val db: AppDatabase) {
    private val bookcaseDao = db.bookcaseDao()
    private val bookDao = db.bookDao()
    private val diaryDao = db.diaryDao()

    val allBookcases: Flow<List<Bookcase>> = bookcaseDao.getAllBookcases()
    val allBooks: Flow<List<Book>> = bookDao.getAllBooks()
    val allDiaries: Flow<List<Diary>> = diaryDao.getAllDiaries()

    fun getBooksByBookcase(bookcaseId: Int): Flow<List<Book>> = bookDao.getBooksByBookcase(bookcaseId)

    fun getBookById(bookId: Int): Flow<Book?> = bookDao.getBookById(bookId)

    suspend fun getBookByIdOneShot(bookId: Int): Book? = bookDao.getBookByIdOneShot(bookId)

    fun getDiariesForBook(bookId: Int): Flow<List<Diary>> = diaryDao.getDiariesForBook(bookId)

    suspend fun insertBookcase(bookcase: Bookcase): Long = bookcaseDao.insert(bookcase)

    suspend fun updateBookcase(bookcase: Bookcase) = bookcaseDao.update(bookcase)

    suspend fun deleteBookcase(id: Int) = bookcaseDao.deleteById(id)

    suspend fun insertBook(book: Book): Long = bookDao.insert(book)

    suspend fun updateBook(book: Book) = bookDao.update(book)

    suspend fun deleteBook(id: Int) = bookDao.deleteById(id)

    suspend fun insertDiary(diary: Diary): Long = diaryDao.insert(diary)

    suspend fun deleteDiary(id: Int) = diaryDao.deleteById(id)
}
