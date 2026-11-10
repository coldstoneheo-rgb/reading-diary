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
import com.example.data.PhotoEdit
import com.example.data.PhotoSlot
import com.example.data.ReadingRepository
import com.example.data.SampleBooks
import com.example.data.SecureKeyManager
import com.example.data.export.DocumentTree
import com.example.data.export.MarkdownExport
import com.example.data.knowledge.KeywordLinks
import com.example.data.knowledge.SharedWord
import com.example.data.ocr.BitmapDecoding
import com.example.data.ocr.GeminiTextExtractor
import com.example.data.ocr.MlKitTextExtractor
import com.example.data.ocr.OcrOutcome
import com.example.data.ocr.TextExtractor
import com.example.data.photo.CameraTempFiles
import com.example.data.photo.DiaryPhotoStore
import com.example.data.photo.InternalDiaryPhotoStore
import com.example.data.photo.RecallPhoto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.room.withTransaction

sealed class Screen {
    object Dashboard : Screen()
    data class BookDetail(val bookId: Int) : Screen()
    data class AddEditBook(val bookId: Int? = null, val startWithSearch: Boolean = false) : Screen()
    data class AddDiary(val bookId: Int, val diaryId: Int? = null) : Screen()
    object Settings : Screen()
    object Statistics : Screen()
    object KnowledgeDrawer : Screen()
    /** 문구 회상(PHRASE-RECALL-SPEC P1). [initialQuery]는 기억 서랍 검색에서 넘어올 때의 검색어. */
    data class PhraseRecall(
        val initialQuery: String = "",
        val initialFilter: com.example.data.knowledge.RecallFilter = com.example.data.knowledge.RecallFilter()
    ) : Screen()
}

/**
 * 화면 위치를 Activity 저장 상태에 넣을 문자열로 바꾼다. 카메라 앱이 떠 있는 동안 앱이 내려가도 같은 화면으로 돌아오게 한다.
 * 문구 회상의 필터는 보존하지 않고 검색어만 남긴다.
 */
internal object ScreenCodec {
    fun encode(screen: Screen): String = when (screen) {
        Screen.Dashboard -> "dashboard"
        is Screen.BookDetail -> "book|${screen.bookId}"
        is Screen.AddEditBook -> "addEdit|${screen.bookId ?: ""}|${screen.startWithSearch}"
        is Screen.AddDiary -> "diary|${screen.bookId}|${screen.diaryId ?: ""}"
        Screen.Settings -> "settings"
        Screen.Statistics -> "statistics"
        Screen.KnowledgeDrawer -> "knowledge"
        is Screen.PhraseRecall -> "recall|${screen.initialQuery}"
    }

    /** 알 수 없는 값이면 null. */
    fun decode(value: String): Screen? {
        val parts = value.split("|", limit = 3)
        return when (parts[0]) {
            "dashboard" -> Screen.Dashboard
            "book" -> parts.getOrNull(1)?.toIntOrNull()?.let { Screen.BookDetail(it) }
            "addEdit" -> Screen.AddEditBook(parts.getOrNull(1)?.toIntOrNull(), parts.getOrNull(2) == "true")
            "diary" -> parts.getOrNull(1)?.toIntOrNull()?.let { Screen.AddDiary(it, parts.getOrNull(2)?.toIntOrNull()) }
            "settings" -> Screen.Settings
            "statistics" -> Screen.Statistics
            "knowledge" -> Screen.KnowledgeDrawer
            "recall" -> Screen.PhraseRecall(value.removePrefix("recall|"))
            else -> null
        }
    }
}

/** 마크다운 내보내기 상태(STAGE2-PLAN ②). 파일 이름은 확장자 포함. */
sealed class ExportState {
    object Idle : ExportState()
    object Running : ExportState()
    /** [renamed]: (요청한 이름, 프로바이더가 실제로 붙인 이름) — 그 사이에 같은 이름이 생긴 경우. */
    data class Done(val created: List<String>, val skipped: List<String>, val renamed: List<Pair<String, String>>) : ExportState()
    /** [failedFile]: 쓰지 못한 파일(폴더 접근 자체가 실패하면 null). [created]: 실패 전에 이미 만든 파일. */
    data class Error(val failedFile: String?, val created: List<String>) : ExportState()
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
    /** 일기 사진 파일 저장소(P2). 사진 행은 Room, 파일은 여기. */
    private val photoStore: DiaryPhotoStore = InternalDiaryPhotoStore(application),
    /** 기기 암호화 저장소에 Gemini 키가 있는지. 테스트에서 주입한다. IO 스레드에서 호출된다. */
    private val geminiKeyPresence: () -> Boolean = { SecureKeyManager.getGeminiApiKey(application).isNotEmpty() }
) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = ReadingRepository(database, photoStore)

    init {
        // 프로세스당 1회(명세 §2.4). 사용자의 첫 저장이 먼저 시작돼도 photoIoMutex가 순서를 지킨다.
        if (photoSweepStarted.compareAndSet(false, true)) {
            viewModelScope.launch {
                try {
                    sweepOrphanPhotos()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 정리 실패는 다음 앱 시작으로 미룬다. 기록에는 영향이 없다.
                }
            }
        }
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

    /** 아직 DB에서 받기 전이면 null. 복원 직후의 빈 목록과 «그 책이 정말 없음»을 가른다. */
    val loadedBooks: StateFlow<List<Book>?> = repository.allBooks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val diaries: StateFlow<List<Diary>> = repository.allDiaries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 일기별 보관 사진(표시 순서). 파일 확인이 디스크를 읽으므로 IO에서 계산한다. */
    val recallPhotos: StateFlow<Map<Int, List<RecallPhoto>>> = repository.allPhotos
        .map { rows -> rows.groupBy({ it.diaryId }, { RecallPhoto(it.id, photoStore.fileFor(it.path)) }) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _diarySaving = MutableStateFlow(false)
    /** 사진 저장에 시간이 걸리므로 저장 중에는 버튼을 막는다(연타로 기록이 두 건 생기지 않게). */
    val diarySaving: StateFlow<Boolean> = _diarySaving

    // 아래 두 값은 메인 스레드에서만 읽고 쓴다(viewModelScope·화면 onDispose).
    private var photoSavesInFlight = 0
    private val deferredCameraTempFiles = mutableListOf<java.io.File>()

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

    // 예시 2권(ADR-003-1). 표식은 "id|title" 집합, 판정은 SampleBooks.isSample(id·제목 둘 다 일치).
    private val sampleMarks = MutableStateFlow(readSampleMarks())
    /** 넣기·지우기 연타로 표식 없는 예시가 생기지 않게 한 번에 하나만 돈다. */
    private val sampleMutex = Mutex()

    /** 지금 서재에 있는 예시 책 id. 배지·통계 제외에 쓴다. */
    val sampleBookIds: StateFlow<Set<Int>> = combine(books, sampleMarks) { allBooks, marks ->
        SampleBooks.sampleIds(allBooks, marks)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private fun readSampleMarks(): Set<String> =
        prefs.getStringSet(SampleBooks.KEY, emptySet()).orEmpty().toSet()

    private suspend fun writeSampleMarks(marks: Set<String>) {
        withContext(Dispatchers.IO) { prefs.edit().putStringSet(SampleBooks.KEY, marks).commit() }
        sampleMarks.value = marks
    }

    /** 예시 2권과 일기를 한 트랜잭션으로 넣는다. 이미 예시가 하나라도 있으면 아무것도 넣지 않는다(중복 0). 넣은 책 수를 돌려준다. */
    fun insertSampleBooks(onComplete: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val added = sampleMutex.withLock {
                val marks = withContext(Dispatchers.IO) { readSampleMarks() }
                if (repository.allBooks.first().any { SampleBooks.isSample(it, marks) }) return@withLock 0
                // Flow 조회는 트랜잭션 밖에서(트랜잭션 안의 Flow 수집은 연결을 기다리며 멈출 수 있다)
                val bookcases = repository.allBookcases.first()
                val existingBookcaseId = (bookcases.firstOrNull { it.name == "기본 책장" } ?: bookcases.firstOrNull())?.id
                val newMarks = database.withTransaction {
                    val bookcaseId = existingBookcaseId
                        ?: repository.insertBookcase(Bookcase(name = "기본 책장", isSystem = true)).toInt()
                    SampleBooks.BOOKS.map { sample ->
                        val book = Book(
                            title = sample.title,
                            author = sample.author,
                            totalPages = sample.totalPages,
                            currentPage = sample.currentPage,
                            bookcaseId = bookcaseId,
                            status = "READING"
                        )
                        val id = repository.insertBook(book).toInt()
                        sample.diaries.forEach { d ->
                            repository.insertDiary(Diary(bookId = id, page = d.page, selectedText = d.selectedText, notes = d.notes))
                        }
                        SampleBooks.markOf(book.copy(id = id))
                    }.toSet()
                }
                // 표식은 커밋된 뒤에만 쓴다. 이전 표식 중 서재에 없는 것은 버린다.
                writeSampleMarks(newMarks)
                newMarks.size
            }
            onComplete(added)
        }
    }

    /**
     * 예시 책을 지운다(일기는 CASCADE). 사용자가 예시 책에 직접 쓴 기록이 있으면 그 책은 지우지 않고 표식만 떼어
     * 사용자 책으로 남긴다. 결과: (지운 책 수, 남긴 책 수).
     */
    fun deleteSampleBooks(onComplete: (removed: Int, kept: Int) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val (removed, kept) = sampleMutex.withLock {
                val marks = withContext(Dispatchers.IO) { readSampleMarks() }
                val targets = repository.allBooks.first().filter { SampleBooks.isSample(it, marks) }
                val allDiaries = repository.allDiaries.first()
                val diariesWithPhotos = repository.allPhotos.first().map { it.diaryId }.toSet()
                // 예시 기록에 붙인 사진도 사용자가 남긴 것이다. 글이 그대로여도 그 책은 지우지 않는다(지우면 복구할 수 없다).
                val (untouched, touched) = targets.partition { book ->
                    val bookDiaries = allDiaries.filter { it.bookId == book.id }
                    SampleBooks.isUntouched(book, bookDiaries) && bookDiaries.none { it.id in diariesWithPhotos }
                }
                untouched.forEach { repository.deleteBook(it.id) }
                writeSampleMarks(emptySet())
                untouched.size to touched.size
            }
            onComplete(removed, kept)
        }
    }

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
     * DB가 바뀔 때만 Default 디스패처에서 재계산한다. 화면은 이 값을 읽기만 한다. 이미 구독 중인 [books]/[diaries]를 재사용해 쿼리를 중복하지 않는다.
     */
    val sharedWords: StateFlow<List<SharedWord>> = combine(books, diaries) { allBooks, allDiaries ->
        KeywordLinks.build(allBooks, allDiaries)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    /** 마크다운 내보내기 진행·결과(STAGE2-PLAN ②). 화면은 Done/Error면 결과 다이얼로그를 띄우고 닫을 때 [dismissExportResult]. */
    val exportState: StateFlow<ExportState> = _exportState

    /**
     * 책당 1파일 + 인덱스 노트를 [tree]에 **새 파일로만** 쓴다(ADR-004 Q7). 이미 있는 이름은 쓰지 않고 건너뛴다 —
     * [DocumentTree]에는 기존 문서를 여는 API가 없다. 쓰기 실패가 나면 거기서 멈추고 Error(그때까지 만든 파일 포함).
     */
    fun exportMarkdown(tree: DocumentTree, exportedDate: String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())) {
        if (_exportState.value == ExportState.Running) return
        _exportState.value = ExportState.Running
        viewModelScope.launch {
            _exportState.value = try {
                withContext(Dispatchers.IO) {
                    val allBooks = repository.allBooks.first()
                    val allDiaries = repository.allDiaries.first()
                    val plan = MarkdownExport.plan(
                        allBooks, allDiaries, KeywordLinks.build(allBooks, allDiaries), tree.existingNames(), exportedDate
                    )
                    val created = mutableListOf<String>()
                    val renamed = mutableListOf<Pair<String, String>>()
                    for (file in plan.toCreate) {
                        val actual = tree.createAndWrite(file.fileName, MarkdownExport.MIME_TYPE, file.content)
                            ?: return@withContext ExportState.Error(file.fileName, created.toList())
                        created += actual
                        if (actual != file.fileName) renamed += file.fileName to actual
                    }
                    ExportState.Done(created, plan.skipped, renamed)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ExportState.Error(null, emptyList())
            }
        }
    }

    fun dismissExportResult() {
        if (_exportState.value != ExportState.Running) _exportState.value = ExportState.Idle
    }

    // Active Details states
    val activeBook = MutableStateFlow<Book?>(null)
    val activeBookDiaries = MutableStateFlow<List<Diary>>(emptyList())

    // OCR visual flow state
    val ocrState = MutableStateFlow<OcrState>(OcrState.Idle)

    private var navigationRestored = false

    /**
     * 기록 화면 입력의 저장 키에 넣는다. 진입할 때마다 새로 만들고 Activity 저장 상태로 복원될 때만 이어진다.
     * 번들에 남은 옛 입력이 나중에 연 새 폼에 되살아나지 않게 한다.
     */
    private val _diaryFormToken = MutableStateFlow("")
    val diaryFormToken: StateFlow<String> = _diaryFormToken

    /** 저장할 화면 위치. 뒤로 가기 순서대로, 마지막이 현재 화면이다. */
    fun navigationSnapshot(): List<String> = (backStack + currentScreenState.value).map(ScreenCodec::encode)

    /**
     * 새 ViewModel에서 한 번만 적용한다. 회전처럼 ViewModel이 살아남은 재생성에서는 지금 위치가 더 최신이므로 무시한다.
     * 해석할 수 없는 값이 하나라도 있으면 처음 화면으로 둔다.
     */
    fun restoreNavigation(saved: List<String>?, formToken: String? = null) {
        if (navigationRestored) return
        navigationRestored = true
        if (formToken != null) _diaryFormToken.value = formToken
        val screens = saved.orEmpty().map { ScreenCodec.decode(it) ?: return }
        val current = screens.lastOrNull() ?: return
        backStack.clear()
        backStack.addAll(screens.dropLast(1))
        navigateTo(current, addToBackStack = false)
    }

    // Navigation triggers
    fun navigateTo(screen: Screen, addToBackStack: Boolean = true) {
        if (addToBackStack && screen is Screen.AddDiary && currentScreenState.value != screen) {
            _diaryFormToken.value = java.util.UUID.randomUUID().toString()
        }
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

    /** 문구 회상으로 간다. 기억 서랍 검색어를 이어받을 수 있다. */
    fun openPhraseRecall(query: String = "") {
        navigateTo(Screen.PhraseRecall(query.trim()))
    }

    /**
     * 문구 회상 → 책 상세. 떠나기 전에 현재 화면을 지금 검색어·필터로 바꿔 두어, 뒤로 오면 같은 결과가 보이게 한다
     * (화면 상태는 AnimatedContent를 떠나면 사라진다). 미리 정한 기간은 그 시점의 날짜 범위로 고정돼 돌아온다.
     */
    fun openBookFromPhraseRecall(bookId: Int, query: String, filter: com.example.data.knowledge.RecallFilter) {
        if (currentScreenState.value is Screen.PhraseRecall) {
            currentScreenState.value = Screen.PhraseRecall(query, filter)
        }
        navigateTo(Screen.BookDetail(bookId))
    }

    /**
     * [screen]이 아직 현재 화면일 때만 닫고 true를 돌려준다. 저장 완료처럼 늦게 오는 콜백용으로, 사용자가 이미 떠났으면 아무것도 하지 않는다.
     * 같은 내용의 화면을 다시 열었을 수 있어 동등성이 아니라 인스턴스로 비교한다.
     */
    fun navigateBackIfCurrent(screen: Screen): Boolean {
        if (currentScreenState.value !== screen) return false
        navigateBack()
        return true
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
    /** [photos]: 사진 처리. 기본 [PhotoEdit.Unchanged]는 사진 행을 건드리지 않는다(화면 연결은 P4). */
    fun saveDiary(bookId: Int, page: Int, selectedText: String, notes: String, id: Int? = null, createdAt: Long? = null, photos: PhotoEdit = PhotoEdit.Unchanged, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.saveDiary(
                Diary(
                    id = id ?: 0,
                    bookId = bookId,
                    page = page,
                    selectedText = selectedText,
                    notes = notes,
                    createdAt = createdAt ?: System.currentTimeMillis()
                ),
                photos
            )
            onComplete()
        }
    }

    /**
     * 사진과 함께 저장한다(명세 §3.5). 사진 쓰기를 ViewModel 스코프에서 하므로 회전·뒤로가기에도 끊기지 않는다.
     * 사진 파일 저장과 행 커밋을 [photoIoMutex]로 묶어, sweep이 아직 행이 없는 새 파일을 지우지 못하게 한다.
     * 파일을 쓴 뒤 저장이 실패해 남은 파일은 다음 앱 시작의 sweep이 걷는다.
     * @param onComplete 저장하지 못한 사진 수를 받는다(사진이 실패해도 글은 저장된다).
     */
    fun saveDiaryWithPhotos(
        bookId: Int,
        page: Int,
        selectedText: String,
        notes: String,
        id: Int? = null,
        createdAt: Long? = null,
        photos: PhotoInput,
        onFailure: () -> Unit = {},
        onComplete: (failedPhotos: Int) -> Unit
    ) {
        if (_diarySaving.value) return
        _diarySaving.value = true
        photoSavesInFlight++
        viewModelScope.launch {
            try {
                var failed = 0
                suspend fun store(source: Uri, rotation: Float, flipped: Boolean): String? =
                    photoStore.save(source, rotation, flipped).also { if (it == null) failed++ }

                val committed = try {
                    photoIoMutex.withLock {
                        val edit = when (photos) {
                            PhotoInput.Unchanged -> PhotoEdit.Unchanged
                            is PhotoInput.Slots -> PhotoEdit.Set(
                                photos.slots.mapNotNull { slot ->
                                    when (slot) {
                                        is PhotoInputSlot.Existing -> PhotoSlot.Existing(slot.photoId)
                                        is PhotoInputSlot.New -> store(slot.source, slot.rotation, slot.flipped)?.let { PhotoSlot.New(it) }
                                        is PhotoInputSlot.Replace -> store(slot.source, slot.rotation, slot.flipped)?.let { PhotoSlot.New(it) }
                                            ?: PhotoSlot.Existing(slot.photoId)
                                    }
                                }
                            )
                        }
                        repository.saveDiary(
                            Diary(
                                id = id ?: 0,
                                bookId = bookId,
                                page = page,
                                selectedText = selectedText,
                                notes = notes,
                                createdAt = createdAt ?: System.currentTimeMillis()
                            ),
                            edit
                        )
                    }
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 디스크 부족·책 동시 삭제 등. 앱을 죽이지 않고 화면의 입력을 남긴다. 행은 트랜잭션이라 남지 않는다.
                    false
                }
                if (committed) onComplete(failed) else onFailure()
            } finally {
                _diarySaving.value = false
                photoSavesInFlight--
                if (photoSavesInFlight == 0) {
                    deferredCameraTempFiles.forEach { it.delete() }
                    deferredCameraTempFiles.clear()
                }
            }
        }
    }

    /**
     * 화면을 떠날 때 촬영 임시 파일을 지운다. 저장이 아직 그 파일을 읽고 있으면 저장이 끝난 뒤로 미룬다(명세 P4 선행).
     */
    fun releaseCameraTempFiles(cacheDir: java.io.File) {
        if (photoSavesInFlight == 0) CameraTempFiles.deleteAll(cacheDir)
        else deferredCameraTempFiles += CameraTempFiles.list(cacheDir)
    }

    /** 편집 화면이 처음 그릴 사진(표시 순서). 진행 중인 저장이 끝난 뒤 읽는다(옛 목록으로 이어서 저장하면 방금 넣은 사진이 지워진다). */
    suspend fun loadPhotos(diaryId: Int): List<RecallPhoto> {
        val rows = photoIoMutex.withLock { repository.photosFor(diaryId) }
        return withContext(Dispatchers.IO) { rows.map { RecallPhoto(it.id, photoStore.fileFor(it.path)) } }
    }

    /**
     * 복원된 새 기록 초안이 이미 저장됐는가. 저장 중 앱이 내려가면 번들에는 저장 전 화면과 초안이 남는다.
     * 작성 시각은 폼을 연 순간에 정해져 저장에 그대로 쓰이므로 책 + 작성 시각으로 정확히 찾는다.
     * 저장이 끝나지 않았으면 행이 없어(트랜잭션) false이고 초안을 잃지 않는다.
     * 편집에는 쓰지 않는다. 다시 저장해도 기록이 중복되지 않고, 사진 변경의 커밋 여부는 화면이 기준 사진 id로 가린다(OcrDiaryScreen.draftBaseIds).
     */
    suspend fun isNewDiaryAlreadySaved(bookId: Int, createdAt: Long): Boolean = photoIoMutex.withLock {
        repository.allDiaries.first().any { it.bookId == bookId && it.createdAt == createdAt }
    }

    /** 고아 파일 정리. 반환 = 지운 개수. */
    internal suspend fun sweepOrphanPhotos(): Int = photoIoMutex.withLock {
        photoStore.sweep(repository.allPhotoPaths().toSet())
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
        /**
         * 사진 파일 저장 + 행 커밋 ↔ sweep 직렬화. 참조 목록을 읽은 뒤 저장된 새 파일을 sweep이 지우는 경쟁을 막는다.
         * Activity가 다시 만들어지면 ViewModel이 둘일 수 있어 프로세스 단위로 둔다.
         */
        private val photoIoMutex = Mutex()
        private val photoSweepStarted = java.util.concurrent.atomic.AtomicBoolean(false)

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
