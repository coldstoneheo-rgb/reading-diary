package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.Book
import com.example.data.Bookcase
import com.example.data.Diary
import com.example.data.ReadingRepository
import com.example.data.api.GeminiApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class Screen {
    object Dashboard : Screen()
    data class BookDetail(val bookId: Int) : Screen()
    data class AddEditBook(val bookId: Int? = null, val startWithSearch: Boolean = false) : Screen()
    data class AddDiary(val bookId: Int, val diaryId: Int? = null) : Screen()
    object Settings : Screen()
    object Statistics : Screen()
    object KnowledgeDrawer : Screen()
}

sealed class OcrState {
    object Idle : OcrState()
    object Processing : OcrState()
    data class Success(val text: String) : OcrState()
    data class Error(val message: String) : OcrState()
}

class ReadingViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = ReadingRepository(database)

    init {
        viewModelScope.launch {
            val bookcaseDao = database.bookcaseDao()
            val existing = bookcaseDao.getAllBookcasesOneShot()
            val defaults = listOf("기본 책장", "소설", "재테크", "자기계발", "인문")
            if (existing.isEmpty()) {
                defaults.forEach { name ->
                    bookcaseDao.insert(Bookcase(name = name, isSystem = true))
                }
            } else {
                val existingNames = existing.map { it.name }
                defaults.forEach { name ->
                    if (!existingNames.contains(name)) {
                        bookcaseDao.insert(Bookcase(name = name, isSystem = true))
                    }
                }
            }
        }
    }

    // Flow listings from Database
    val bookcases: StateFlow<List<Bookcase>> = repository.allBookcases
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val books: StateFlow<List<Book>> = repository.allBooks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val diaries: StateFlow<List<Diary>> = repository.allDiaries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Navigation / State Controllers
    val currentScreenState = MutableStateFlow<Screen>(Screen.Dashboard)
    private val backStack = mutableListOf<Screen>()
    val selectedFilter = MutableStateFlow("ALL") // "ALL", "READING", "TO_READ", "COMPLETED"
    val selectedBookcaseId = MutableStateFlow<Int?>(null) // null = all bookcases
    val currentThemeId = MutableStateFlow(1) // 1: Warm, 2: Midnight, 3: Swiss, 4: Pastel, 5: Classic

    // On-device persistent general settings (SharedPreferences backed)
    private val prefs = application.getSharedPreferences("diary_general_settings", android.content.Context.MODE_PRIVATE)
    
    val diarySortNewestFirst = MutableStateFlow(prefs.getBoolean("diary_sort_newest_first", true))
    val diaryFontSize = MutableStateFlow(prefs.getFloat("diary_font_size", 14f))
    val readingGoalYearly = MutableStateFlow(prefs.getInt("reading_goal_yearly", 30))

    fun selectTheme(id: Int) {
        currentThemeId.value = id
    }

    fun setDiarySortNewestFirst(newestFirst: Boolean) {
        diarySortNewestFirst.value = newestFirst
        prefs.edit().putBoolean("diary_sort_newest_first", newestFirst).apply()
    }

    fun setDiaryFontSize(size: Float) {
        diaryFontSize.value = size
        prefs.edit().putFloat("diary_font_size", size).apply()
    }

    fun setReadingGoalYearly(goal: Int) {
        readingGoalYearly.value = goal.coerceIn(1, 999)
        prefs.edit().putInt("reading_goal_yearly", goal).apply()
    }

    // Dynamic filtering combined state of books
    val filteredBooks: StateFlow<List<Book>> = combine(
        repository.allBooks,
        selectedFilter,
        selectedBookcaseId
    ) { allBooks, filter, bookcaseId ->
        var list = allBooks
        if (bookcaseId != null) {
            list = list.filter { it.bookcaseId == bookcaseId }
        }
        when (filter) {
            "READING" -> list.filter { it.status == "READING" }
            "TO_READ" -> list.filter { it.status == "TO_READ" }
            "COMPLETED" -> list.filter { it.status == "COMPLETED" }
            else -> list
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Details states
    val activeBook = MutableStateFlow<Book?>(null)
    val activeBookDiaries = MutableStateFlow<List<Diary>>(emptyList())

    // OCR visual flow state
    val ocrState = MutableStateFlow<OcrState>(OcrState.Idle)

    // Navigation triggers
    fun navigateTo(screen: Screen, addToBackStack: Boolean = true) {
        if (addToBackStack) {
            val current = currentScreenState.value
            if (current != screen) {
                if (backStack.isEmpty() || backStack.last() != current) {
                    backStack.add(current)
                }
            }
        }
        currentScreenState.value = screen
        // If navigating to BookDetail, fetch content
        if (screen is Screen.BookDetail) {
            loadBookDetail(screen.bookId)
        }
    }

    fun navigateBack() {
        if (backStack.isNotEmpty()) {
            val previousScreen = backStack.removeAt(backStack.lastIndex)
            navigateTo(previousScreen, addToBackStack = false)
        } else {
            navigateTo(Screen.Dashboard, addToBackStack = false)
        }
    }

    private fun loadBookDetail(bookId: Int) {
        viewModelScope.launch {
            repository.getBookById(bookId).collect { book ->
                activeBook.value = book
            }
        }
        viewModelScope.launch {
            repository.getDiariesForBook(bookId).collect { diariesList ->
                activeBookDiaries.value = diariesList
            }
        }
    }

    // Slider Progress modification
    fun updateBookProgress(bookId: Int, newPage: Int, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val book = repository.getBookByIdOneShot(bookId)
            if (book != null) {
                val newStatus = when {
                    newPage >= book.totalPages -> "COMPLETED"
                    newPage > 0 -> "READING"
                    else -> book.status // keep same status if To Read
                }
                
                val updated = book.copy(
                    currentPage = newPage.coerceIn(0, book.totalPages),
                    status = newStatus,
                    endDate = if (newPage >= book.totalPages) java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()) else book.endDate,
                    startDate = if (book.startDate.isEmpty() && newPage > 0) java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()) else book.startDate
                )
                repository.updateBook(updated)
                activeBook.value = updated
                onComplete()
            }
        }
    }

    // Dynamic Category / Bookcase Management
    fun createBookcase(name: String) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                repository.insertBookcase(Bookcase(name = name, isSystem = false))
            }
        }
    }

    fun renameBookcase(bookcase: Bookcase, newName: String) {
        viewModelScope.launch {
            if (newName.isNotBlank() && !bookcase.isSystem) {
                repository.updateBookcase(bookcase.copy(name = newName))
            }
        }
    }

    fun deleteBookcase(bookcaseId: Int) {
        viewModelScope.launch {
            // Filter out default case deletion on UI, but safe-check here
            repository.deleteBookcase(bookcaseId)
            if (selectedBookcaseId.value == bookcaseId) {
                selectedBookcaseId.value = null
            }
        }
    }

    // Book ADD / EDIT / DELETE Operations
    fun saveBook(
        id: Int? = null,
        title: String,
        author: String,
        totalPages: Int,
        currentPage: Int,
        coverUrl: String,
        bookcaseId: Int,
        status: String,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            // Safely resolve bookcaseId before saving to prevent SQLiteConstraintException (ForeignKey failed)
            var resolvedBookcaseId = bookcaseId
            val existingBookcases = database.bookcaseDao().getAllBookcasesOneShot()
            if (existingBookcases.isEmpty()) {
                val defaultId = database.bookcaseDao().insert(Bookcase(name = "기본 책장", isSystem = true)).toInt()
                resolvedBookcaseId = defaultId
            } else if (resolvedBookcaseId <= 0 || !existingBookcases.any { it.id == resolvedBookcaseId }) {
                resolvedBookcaseId = existingBookcases.first().id
            }

            if (id == null) {
                repository.insertBook(
                    Book(
                        title = title,
                        author = author,
                        totalPages = totalPages,
                        currentPage = currentPage,
                        coverUrl = coverUrl,
                        bookcaseId = resolvedBookcaseId,
                        status = status,
                        startDate = if (currentPage > 0) java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()) else ""
                    )
                )
            } else {
                val currentBook = repository.getBookByIdOneShot(id)
                val existingStartDate = currentBook?.startDate ?: ""
                val existingRating = currentBook?.rating ?: 0
                repository.updateBook(
                    Book(
                        id = id,
                        title = title,
                        author = author,
                        totalPages = totalPages,
                        currentPage = currentPage,
                        coverUrl = coverUrl,
                        bookcaseId = resolvedBookcaseId,
                        status = status,
                        startDate = existingStartDate,
                        endDate = if (status == "COMPLETED") java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()) else null,
                        rating = existingRating
                    )
                )
            }
            onComplete()
        }
    }

    fun updateBookRating(bookId: Int, rating: Int, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val book = repository.getBookByIdOneShot(bookId)
            if (book != null) {
                val updated = book.copy(rating = rating.coerceIn(0, 5))
                repository.updateBook(updated)
                activeBook.value = updated
                onComplete()
            }
        }
    }

    fun deleteBook(bookId: Int, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.deleteBook(bookId)
            onComplete()
        }
    }

    // Diary CRUD Operations
    fun saveDiary(bookId: Int, page: Int, selectedText: String, notes: String, id: Int? = null, createdAt: Long? = null, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.insertDiary(
                Diary(
                    id = id ?: 0,
                    bookId = bookId,
                    page = page,
                    selectedText = selectedText,
                    notes = notes,
                    createdAt = createdAt ?: System.currentTimeMillis()
                )
            )
            onComplete()
        }
    }

    fun deleteDiary(diaryId: Int, bookId: Int) {
        viewModelScope.launch {
            repository.deleteDiary(diaryId)
            // Reload active diaries
            repository.getDiariesForBook(bookId).collect { diariesList ->
                activeBookDiaries.value = diariesList
            }
        }
    }

    // AI Underline Processing Flow (Gemini Image OCR)
    fun processUnderlineOcr(bitmap: Bitmap, bookTitle: String) {
        viewModelScope.launch {
            ocrState.value = OcrState.Processing
            try {
                val extractedText = GeminiApiClient.extractUnderlinedText(getApplication(), bitmap, bookTitle)
                ocrState.value = OcrState.Success(extractedText)
            } catch (e: Exception) {
                ocrState.value = OcrState.Error(e.message ?: "추출 실패")
            }
        }
    }

    fun resetOcrState() {
        ocrState.value = OcrState.Idle
    }
}
