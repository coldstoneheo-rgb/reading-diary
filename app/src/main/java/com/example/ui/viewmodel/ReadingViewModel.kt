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
    data class AddEditBook(val bookId: Int? = null) : Screen()
    data class AddDiary(val bookId: Int) : Screen()
    object Settings : Screen()
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

    // Flow listings from Database
    val bookcases: StateFlow<List<Bookcase>> = repository.allBookcases
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val books: StateFlow<List<Book>> = repository.allBooks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val diaries: StateFlow<List<Diary>> = repository.allDiaries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Navigation / State Controllers
    val currentScreenState = MutableStateFlow<Screen>(Screen.Dashboard)
    val selectedFilter = MutableStateFlow("ALL") // "ALL", "READING", "TO_READ", "COMPLETED"
    val selectedBookcaseId = MutableStateFlow<Int?>(null) // null = all bookcases
    val currentThemeId = MutableStateFlow(1) // 1: Warm, 2: Midnight, 3: Swiss, 4: Pastel, 5: Classic

    fun selectTheme(id: Int) {
        currentThemeId.value = id
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
    fun navigateTo(screen: Screen) {
        currentScreenState.value = screen
        // If navigating to BookDetail, fetch content
        if (screen is Screen.BookDetail) {
            loadBookDetail(screen.bookId)
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
            if (id == null) {
                repository.insertBook(
                    Book(
                        title = title,
                        author = author,
                        totalPages = totalPages,
                        currentPage = currentPage,
                        coverUrl = coverUrl,
                        bookcaseId = bookcaseId,
                        status = status,
                        startDate = if (currentPage > 0) java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()) else ""
                    )
                )
            } else {
                val currentBook = repository.getBookByIdOneShot(id)
                val existingStartDate = currentBook?.startDate ?: ""
                repository.updateBook(
                    Book(
                        id = id,
                        title = title,
                        author = author,
                        totalPages = totalPages,
                        currentPage = currentPage,
                        coverUrl = coverUrl,
                        bookcaseId = bookcaseId,
                        status = status,
                        startDate = existingStartDate,
                        endDate = if (status == "COMPLETED") java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()) else null
                    )
                )
            }
            onComplete()
        }
    }

    fun deleteBook(bookId: Int, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.deleteBook(bookId)
            onComplete()
        }
    }

    // Diary CRUD Operations
    fun saveDiary(bookId: Int, page: Int, selectedText: String, notes: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.insertDiary(
                Diary(
                    bookId = bookId,
                    page = page,
                    selectedText = selectedText,
                    notes = notes
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
