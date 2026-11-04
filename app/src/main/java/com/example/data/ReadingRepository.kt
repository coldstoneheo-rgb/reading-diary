package com.example.data

import androidx.room.withTransaction
import com.example.data.photo.DiaryPhotoStore
import kotlinx.coroutines.flow.Flow

class ReadingRepository(
    private val db: AppDatabase,
    /** 사진 파일 삭제용([PhotoEdit.Set]에서 빠진 사진). 행은 DB, 파일은 저장소. */
    private val photoStore: DiaryPhotoStore
) {
    private val bookcaseDao = db.bookcaseDao()
    private val bookDao = db.bookDao()
    private val diaryDao = db.diaryDao()
    private val diaryPhotoDao = db.diaryPhotoDao()

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

    /**
     * 일기 저장: 편집(`id != 0`)은 `@Update`, 새 기록은 insert(F6 — REPLACE 편집은 자식 행 CASCADE를 일으킨다).
     * 편집 대상 행이 사라졌으면(update 0행) 예전 REPLACE 동작과 같게 그 id로 넣는다. 반환 = 일기 id.
     */
    suspend fun saveDiary(diary: Diary, photos: PhotoEdit = PhotoEdit.Unchanged): Int {
        val removedPaths = mutableListOf<String>()
        val id = db.withTransaction {
            val id = if (diary.id != 0 && diaryDao.update(diary) > 0) diary.id else diaryDao.insert(diary).toInt()
            if (photos is PhotoEdit.Set) removedPaths += applyPhotos(id, photos)
            id
        }
        // 파일은 커밋 뒤에 지운다(롤백되면 행이 남으므로 파일도 남아야 한다). 놓친 파일은 앱 시작 sweep이 걷는다.
        removedPaths.forEach { photoStore.delete(it) }
        return id
    }

    /**
     * 사진 행을 [edit] 목록대로 맞춘다: 기존 행을 지우고 목록 순서(sortOrder)로 다시 넣는다.
     * Existing은 원래 id·path·createdAt을 유지하고, 이 일기의 사진이 아닌 id는 무시한다. 반환 = 빠진 사진의 경로.
     */
    private suspend fun applyPhotos(diaryId: Int, edit: PhotoEdit.Set): List<String> {
        val existing = diaryPhotoDao.photosFor(diaryId)
        val byId = existing.associateBy { it.id }
        val rows = edit.slots.mapNotNull { slot ->
            when (slot) {
                is PhotoSlot.Existing -> byId[slot.photoId]
                is PhotoSlot.New -> DiaryPhoto(diaryId = diaryId, path = slot.path, sortOrder = 0)
            }
        }.distinctBy { if (it.id != 0) "id:${it.id}" else "new:${it.path}" }
            .mapIndexed { index, row -> row.copy(sortOrder = index) }
        val keptIds = rows.map { it.id }.filter { it != 0 }.toSet()
        val keptPaths = rows.map { it.path }.toSet()
        if (existing.isNotEmpty()) diaryPhotoDao.deleteByIds(existing.map { it.id })
        if (rows.isNotEmpty()) diaryPhotoDao.insertAll(rows)
        // 새 행이 같은 경로를 가리키면 그 파일은 지우지 않는다.
        return existing.filter { it.id !in keptIds && it.path !in keptPaths }.map { it.path }
    }

    suspend fun photosFor(diaryId: Int): List<DiaryPhoto> = diaryPhotoDao.photosFor(diaryId)

    suspend fun allPhotoPaths(): List<String> = diaryPhotoDao.allPaths()

    suspend fun deleteDiary(id: Int) = diaryDao.deleteById(id)
}
