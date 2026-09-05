package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.Book
import com.example.data.Bookcase
import com.example.data.Diary
import com.example.data.ReadingRepository
import com.example.data.SecureKeyManager
import com.example.data.knowledge.KeywordLinks
import com.example.data.knowledge.SharedWord
import com.example.data.ocr.BitmapDecoding
import com.example.data.ocr.GeminiTextExtractor
import com.example.data.ocr.MlKitTextExtractor
import com.example.data.ocr.OcrOutcome
import com.example.data.ocr.TextExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
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

/**
 * @param textExtractor 밑줄 구절 추출 엔진. 기본은 온디바이스 ML Kit(ADR-002). 테스트는 Fake를 주입한다.
 *   Compose의 `viewModel()`은 (Application) 생성자를 리플렉션으로 찾으므로 @JvmOverloads가 필요하다.
 */
class ReadingViewModel @JvmOverloads constructor(
    application: Application,
    private val textExtractor: TextExtractor = MlKitTextExtractor(),
    /** "정밀 분석" 전용. 키 등록 + 사진 전송 동의가 있을 때만 [processPreciseAnalysis]가 호출한다. */
    private val preciseExtractor: TextExtractor = GeminiTextExtractor(application),
    /** 기기 암호화 저장소에 Gemini 키가 있는지. 테스트에서 주입한다. IO 스레드에서 호출된다. */
    private val geminiKeyPresence: () -> Boolean = { SecureKeyManager.getGeminiApiKey(application).isNotEmpty() }
) : AndroidViewModel(application) {
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

    // ---- AI 정밀 분석(선택): 사용자 본인 Gemini 키 (ADR-001 D, ADR-002 Q3) ----
    /**
     * 기기 암호화 저장소에 키가 있는가. 키 값 자체는 ViewModel에 올리지 않는다.
     * EncryptedSharedPreferences/KeyStore 접근은 느릴 수 있어 메인 스레드에서 열지 않는다 — 초기값 false(fail-closed)로 두고 IO에서 갱신.
     */
    val geminiKeyRegistered = MutableStateFlow(false)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val present = geminiKeyPresence()
            withContext(Dispatchers.Main) {
                geminiKeyRegistered.value = present
                // 동의는 일반 설정(백업됨)에, 키는 백업 제외 저장소에 있다. 키 없이 동의만 복원되면 동의를 내린다:
                // 새 기기/새 키에는 새 동의가 필요하다.
                if (!present && geminiPhotoConsent.value) setGeminiPhotoConsent(false)
            }
        }
    }
    /** "정밀 분석 시 사진이 Google Gemini로 전송된다"에 대한 사용자 동의. 키와 별개로 명시적으로 받는다. */
    val geminiPhotoConsent = MutableStateFlow(prefs.getBoolean("gemini_photo_consent", false))
    /** 정밀 분석 버튼 노출 조건. 둘 다 있어야 한다. */
    val preciseAnalysisAvailable: StateFlow<Boolean> = combine(geminiKeyRegistered, geminiPhotoConsent) { key, consent -> key && consent }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 저장은 IO에서. onResult(false) = 저장 거부(암호화 저장소를 열 수 없거나 헤더에 실을 수 없는 문자). 평문으로 저장하지 않는다. */
    fun saveGeminiApiKey(apiKey: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { SecureKeyManager.saveGeminiApiKey(getApplication(), apiKey) }
            if (saved) geminiKeyRegistered.value = true
            onResult(saved)
        }
    }

    /** 삭제 실패(암호화 저장소를 열 수 없음)면 등록 상태를 유지하고 onResult(false). 성공 시 동의도 함께 내린다. */
    fun clearGeminiApiKey(onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val cleared = withContext(Dispatchers.IO) { SecureKeyManager.clearGeminiApiKey(getApplication()) }
            if (cleared) {
                geminiKeyRegistered.value = false
                setGeminiPhotoConsent(false)
            }
            onResult(cleared)
        }
    }

    fun setGeminiPhotoConsent(granted: Boolean) {
        geminiPhotoConsent.value = granted
        prefs.edit().putBoolean("gemini_photo_consent", granted).apply()
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

    /**
     * 책 사이의 연결(ADR-003 Q1·Q4): 서로 다른 책 2권 이상의 기록에 함께 나온 단어.
     * DB가 바뀔 때만 Default 디스패처에서 재계산한다. 화면은 이 값을 읽기만 한다.
     */
    val sharedWords: StateFlow<List<SharedWord>> = combine(repository.allBooks, repository.allDiaries) { allBooks, allDiaries ->
        KeywordLinks.build(allBooks, allDiaries)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    /**
     * 크롭된 페이지 이미지에서 텍스트를 추출한다. 결과는 있는 그대로 전달하며 가짜 문장으로 채우지 않는다(ADR-002 Q2).
     * Gemini 경로는 여기서 호출하지 않는다 — 키 등록·동의·사진마다 확인을 거치는 [processPreciseAnalysis]만 쓴다(ADR-002 Q3).
     */
    /** 진행 중인 인식 작업의 소유자. 새 요청·리셋 시 취소해 오래된 결과가 다른 화면의 편집창을 덮어쓰지 못하게 한다. */
    private var ocrJob: Job? = null

    fun processUnderlineOcr(bitmap: Bitmap) {
        if (anyOcrRunning()) return // 재진입 방지(공개 상태가 아니라 Job으로 판정)
        ocrJob = viewModelScope.launch {
            ocrState.value = OcrState.Processing
            val result = extractSafely(bitmap, textExtractor)
            ensureActive()
            ocrState.value = result
        }
    }

    /**
     * 사진 URI에서 디코드(다운샘플+EXIF) → 회전/플립 → 크롭 → 추출까지 viewModelScope에서 수행한다.
     * 화면 스코프가 아니라 ViewModel 스코프에서 돌기 때문에 회전·뒤로가기로 화면이 사라져도 Processing에 고착되지 않는다.
     */
    fun processUnderlineOcr(
        imageUri: Uri,
        rotationDegrees: Float,
        flipped: Boolean,
        cropLeft: Float,
        cropTop: Float,
        cropRight: Float,
        cropBottom: Float
    ) {
        if (anyOcrRunning()) return // 같은 사진에 두 경로가 동시에 돌아 결과가 서로 덮어쓰는 것을 막는다
        ocrJob = runOcrPipeline(ocrState, textExtractor, imageUri, rotationDegrees, flipped, cropLeft, cropTop, cropRight, cropBottom)
    }

    private fun anyOcrRunning() = ocrJob?.isActive == true || preciseJob?.isActive == true

    /**
     * 공용 파이프라인: 디코드 → 추출 → 상태 기록. 두 경로(온디바이스/정밀)가 상태·Job·추출기만 다르게 공유한다.
     * - 크롭 비트맵은 취소돼도 반드시 회수(try/finally)
     * - 결과는 자기 Job이 아직 현재 Job일 때만 기록(취소된 옛 작업이 새 작업의 상태를 덮어쓰지 않게)
     */
    private fun runOcrPipeline(
        state: MutableStateFlow<OcrState>,
        extractor: TextExtractor,
        imageUri: Uri,
        rotationDegrees: Float,
        flipped: Boolean,
        cropLeft: Float,
        cropTop: Float,
        cropRight: Float,
        cropBottom: Float
    ): Job = viewModelScope.launch {
        state.value = OcrState.Processing
        val cropped = withContext(Dispatchers.Default) {
            BitmapDecoding.decodeForOcr(getApplication(), imageUri, rotationDegrees, flipped, cropLeft, cropTop, cropRight, cropBottom)
        }
        val result = if (cropped == null) {
            // 디코드 실패는 그 자리에서 오류로 끝낸다. 대체 이미지로 인식을 돌리지 않는다.
            OcrState.Error(DECODE_ERROR_MESSAGE)
        } else {
            try {
                extractSafely(cropped, extractor)
            } finally {
                cropped.recycle()
            }
        }
        ensureActive() // 취소됐으면 여기서 끝. 옛 작업의 결과가 새 상태를 덮어쓰지 않는다
        state.value = result
    }

    // ---- 정밀 분석 경로: 기본 경로(ocrState)와 상태를 분리해 결과가 편집창을 조용히 덮어쓰지 않게 한다 ----
    val preciseOcrState = MutableStateFlow<OcrState>(OcrState.Idle)
    private var preciseJob: Job? = null
    private var preciseOwner: String? = null

    /**
     * 화면 진입 시 호출. 소유자(책/일기)가 바뀐 경우에만 진행 중 작업·결과를 버린다.
     * 회전 등 화면 재생성에서는 소유자가 같으므로 유료 요청과 그 결과가 살아남는다.
     */
    fun enterPreciseScope(owner: String) {
        if (preciseOwner != owner) {
            resetPreciseOcrState()
            preciseOwner = owner
        }
    }

    /**
     * 사진을 사용자 본인 키로 Google Gemini에 보내 분석한다. 키 등록과 사진 전송 동의가 모두 없으면 **호출하지 않고** 오류로 끝난다.
     * 화면은 이 함수를 호출하기 전에 매번 확인 다이얼로그를 띄운다(사진마다 명시적 행위).
     */
    fun processPreciseAnalysis(
        imageUri: Uri,
        rotationDegrees: Float,
        flipped: Boolean,
        cropLeft: Float,
        cropTop: Float,
        cropRight: Float,
        cropBottom: Float
    ) {
        // stateIn 파생값이 아니라 원천 상태를 직접 본다(동의 토글 직후의 지연에 영향받지 않게)
        if (!(geminiKeyRegistered.value && geminiPhotoConsent.value)) {
            preciseOcrState.value = OcrState.Error(CONSENT_REQUIRED_MESSAGE)
            return
        }
        if (anyOcrRunning()) return
        preciseJob = runOcrPipeline(preciseOcrState, preciseExtractor, imageUri, rotationDegrees, flipped, cropLeft, cropTop, cropRight, cropBottom)
    }

    fun resetPreciseOcrState() {
        preciseJob?.cancel()
        preciseJob = null
        preciseOcrState.value = OcrState.Idle
    }

    /** 어떤 엔진을 쓰는지는 호출부에서 항상 명시한다(기본값 없음 — 온디바이스/외부 전송 선택이 diff에 드러나게). */
    private suspend fun extractSafely(bitmap: Bitmap, extractor: TextExtractor): OcrState = try {
        when (val outcome = extractor.extract(bitmap)) {
            is OcrOutcome.Text -> OcrState.Success(outcome.text)
            OcrOutcome.NoText -> OcrState.Error(NO_TEXT_MESSAGE)
            is OcrOutcome.Failed -> OcrState.Error(outcome.message)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // 인터페이스는 "던지지 않는다"를 강제하지 못하므로 마지막 방어선. Processing에 고착되지 않게 한다.
        OcrState.Error(ENGINE_ERROR_MESSAGE)
    }

    companion object {
        const val NO_TEXT_MESSAGE = "글자를 찾지 못했어요. 더 밝게, 더 가까이 찍어 다시 시도하거나 직접 입력해 주세요."
        const val ENGINE_ERROR_MESSAGE = "글자 인식 중 오류가 났어요. 다시 시도하거나 직접 입력해 주세요."
        const val DECODE_ERROR_MESSAGE = "사진을 읽지 못했어요. 다시 촬영하거나 다른 사진을 골라 주세요."
        const val CONSENT_REQUIRED_MESSAGE = "정밀 분석을 쓰려면 설정에서 Gemini API 키를 등록하고 사진 전송에 동의해야 해요."
    }

    fun resetOcrState() {
        ocrJob?.cancel() // 화면 이탈/진입 시 진행 중 작업도 버린다. 완료돼도 다른 화면에 결과를 흘리지 않는다
        ocrJob = null
        ocrState.value = OcrState.Idle
    }
}
