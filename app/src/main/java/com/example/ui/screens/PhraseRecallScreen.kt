package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.photo.RecallPhoto
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.data.Book
import com.example.data.Diary
import com.example.data.knowledge.PhraseRecall
import com.example.data.knowledge.RecallFilter
import com.example.data.knowledge.RecallHit
import com.example.data.knowledge.RecallPeriod
import com.example.data.knowledge.RecallPeriods
import com.example.data.knowledge.SharedWord
import com.example.ui.viewmodel.ReadingViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 문구 회상(PHRASE-RECALL-SPEC §2·§3.2 P1) — 떠오른 문구·단어로 내 기록을 찾아 어느 책 몇 쪽, 언제 남겼는지 보여 준다.
 * 검색 계산은 [PhraseRecall.search] 순수 함수를 화면 `remember`로 부른다. 사진은 썸네일(맨 앞 1장)과 펼친 카드의 N장 넘겨 보기로 그린다(§3.5).
 */
@Composable
fun PhraseRecallScreen(
    viewModel: ReadingViewModel,
    initialQuery: String,
    initialFilter: RecallFilter,
    modifier: Modifier = Modifier
) {
    val books by viewModel.books.collectAsState()
    val diaries by viewModel.diaries.collectAsState()
    val sharedWords by viewModel.sharedWords.collectAsState()
    val photos by viewModel.recallPhotos.collectAsState()
    PhraseRecallContent(
        books = books,
        diaries = diaries,
        sharedWords = sharedWords,
        photosByDiary = photos,
        initialQuery = initialQuery,
        initialFilter = initialFilter,
        onOpenBook = { bookId, query, filter -> viewModel.openBookFromPhraseRecall(bookId, query, filter) },
        onBack = { viewModel.navigateBack() },
        modifier = modifier
    )
}

/** ViewModel 없이 그릴 수 있는 본문. 스크린샷 테스트가 고정 데이터로 그린다([KnowledgeDrawerContent] 관례). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhraseRecallContent(
    books: List<Book>,
    diaries: List<Diary>,
    sharedWords: List<SharedWord>,
    /** 책 상세로 간다. 지금 검색어·필터를 함께 넘겨 돌아왔을 때 그대로 보이게 한다. */
    onOpenBook: (bookId: Int, query: String, filter: RecallFilter) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialQuery: String = "",
    /** 테스트가 필터 상태를 바로 그리기 위한 초기값. 기간이 들어 있으면 «직접 지정»으로 본다. */
    initialFilter: RecallFilter = RecallFilter(),
    /** 일기별 보관 사진(표시 순서). 없는 일기는 «사진 없음». */
    photosByDiary: Map<Int, List<RecallPhoto>> = emptyMap()
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()) }

    var query by rememberSaveable { mutableStateOf(initialQuery) }
    // 회전(Activity 재생성)에도 필터가 함께 살아남도록 전부 저장 가능한 상태로 둔다.
    var bookIds by rememberSaveable(stateSaver = BookIdsSaver) { mutableStateOf(initialFilter.bookIds) }
    var period by rememberSaveable {
        mutableStateOf(if (initialFilter.fromMillis != null || initialFilter.toMillis != null) RecallPeriod.CUSTOM else RecallPeriod.ALL)
    }
    var customFrom by rememberSaveable { mutableStateOf(initialFilter.fromMillis) }
    var customTo by rememberSaveable { mutableStateOf(initialFilter.toMillis) }
    var pageRange by rememberSaveable(stateSaver = PageRangeSaver) { mutableStateOf(initialFilter.pageRange) }
    var expandedId by remember { mutableStateOf<Int?>(null) }

    var bookMenuOpen by remember { mutableStateOf(false) }
    var periodMenuOpen by remember { mutableStateOf(false) }
    var pageDialogOpen by remember { mutableStateOf(false) }

    // 지워진 책이 선택에 남지 않게 한다.
    val validBookIds = remember(bookIds, books) { bookIds.filter { id -> books.any { it.id == id } }.toSet() }
    val filter = remember(validBookIds, period, customFrom, customTo, pageRange) {
        val tz = TimeZone.getDefault() // 기기 로컬 시간대(LC 판정)
        when (period) {
            RecallPeriod.ALL -> RecallFilter(bookIds = validBookIds, pageRange = pageRange)
            RecallPeriod.CUSTOM -> RecallFilter(bookIds = validBookIds, fromMillis = customFrom, toMillis = customTo, pageRange = pageRange)
            else -> RecallFilter(
                bookIds = validBookIds,
                fromMillis = RecallPeriods.presetFrom(period, System.currentTimeMillis(), tz),
                pageRange = pageRange
            )
        }
    }
    val hits = remember(query, filter, books, diaries) { PhraseRecall.search(query, filter, books, diaries) }
    val trimmed = query.trim()

    fun pickDay(initial: Long?, onPicked: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = initial ?: System.currentTimeMillis() }
        DatePickerDialog(
            context,
            { _, y, m, d -> onPicked(Calendar.getInstance().apply { clear(); set(y, m, d, 12, 0) }.timeInMillis) },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("문구 회상", fontWeight = FontWeight.Bold)
                        Text(
                            "떠오른 문장을 언제 어느 책에서 남겼는지",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("phraserecall_back_button")) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "뒤로 가기")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("떠오른 문구나 단어") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, contentDescription = "지우기") }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("phraserecall_search_field"),
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )

            if (sharedWords.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("phraserecall_word_chips"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(sharedWords, key = { it.word }) { sw ->
                        SuggestionChip(onClick = { query = sw.word }, label = { Text("#${sw.word}") })
                    }
                }
            }

            // 필터 줄: [책] [기간] [쪽(책 1권일 때만)]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    FilterChip(
                        selected = validBookIds.isNotEmpty(),
                        onClick = { bookMenuOpen = true },
                        label = { Text(bookFilterLabel(validBookIds, books), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                        modifier = Modifier
                            .widthIn(max = 180.dp)
                            .testTag("phraserecall_filter_book")
                    )
                    DropdownMenu(expanded = bookMenuOpen, onDismissRequest = { bookMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("전체 책") },
                            leadingIcon = { Checkbox(checked = validBookIds.isEmpty(), onCheckedChange = null) },
                            onClick = { bookIds = emptySet(); pageRange = null }
                        )
                        books.sortedBy { it.title }.forEach { book ->
                            DropdownMenuItem(
                                text = { Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingIcon = { Checkbox(checked = book.id in validBookIds, onCheckedChange = null) },
                                onClick = {
                                    bookIds = if (book.id in validBookIds) validBookIds - book.id else validBookIds + book.id
                                    pageRange = null // 쪽 범위는 고른 한 권에만 의미가 있다
                                }
                            )
                        }
                    }
                }

                Box {
                    FilterChip(
                        selected = period != RecallPeriod.ALL,
                        onClick = { periodMenuOpen = true },
                        label = { Text(periodLabel(period, customFrom, customTo, dateFormat), maxLines = 1) },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                        modifier = Modifier.testTag("phraserecall_filter_period")
                    )
                    DropdownMenu(expanded = periodMenuOpen, onDismissRequest = { periodMenuOpen = false }) {
                        RecallPeriod.values().forEach { p ->
                            DropdownMenuItem(
                                text = { Text(if (p == RecallPeriod.CUSTOM) "직접 지정…" else p.label) },
                                onClick = {
                                    periodMenuOpen = false
                                    if (p != RecallPeriod.CUSTOM) {
                                        period = p
                                    } else {
                                        // 시작일 → 끝일 순서로 고른다. 끝일은 그날 23:59:59.999까지 포함(LC 판정).
                                        pickDay(customFrom) { start ->
                                            pickDay(customTo ?: start) { end ->
                                                val tz = TimeZone.getDefault()
                                                val (a, b) = if (end < start) end to start else start to end
                                                customFrom = RecallPeriods.dayStart(a, tz)
                                                customTo = RecallPeriods.dayEnd(b, tz)
                                                period = RecallPeriod.CUSTOM
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                if (validBookIds.size == 1) {
                    FilterChip(
                        selected = pageRange != null,
                        onClick = { pageDialogOpen = true },
                        label = { Text(pageLabel(pageRange), maxLines = 1) },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                        modifier = Modifier.testTag("phraserecall_filter_page")
                    )
                }
            }

            Text(
                text = if (hits.isEmpty()) (if (trimmed.isEmpty() && diaries.isEmpty()) "기록 0개" else "일치하는 기록 0개")
                else "기록 ${hits.size}개 · 기록한 날 최신순",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            if (hits.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = when {
                                diaries.isEmpty() -> "아직 기록이 없습니다. 책 페이지를 찍어 밑줄 구절을 남겨 보세요."
                                trimmed.isNotEmpty() -> "다른 표현으로 찾아보세요."
                                else -> "이 조건에 맞는 기록이 없습니다."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("phraserecall_empty")
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("phraserecall_results_list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(hits, key = { it.diaryId }) { hit ->
                        RecallHitCard(
                            hit = hit,
                            photos = photosByDiary[hit.diaryId].orEmpty(),
                            expanded = expandedId == hit.diaryId,
                            dateFormat = dateFormat,
                            onToggle = { expandedId = if (expandedId == hit.diaryId) null else hit.diaryId },
                            onOpenBook = { onOpenBook(hit.bookId, query, filter) }
                        )
                    }
                }
            }
        }
    }

    if (pageDialogOpen) {
        PageRangeDialog(
            initial = pageRange,
            onDismiss = { pageDialogOpen = false },
            onConfirm = { pageRange = it; pageDialogOpen = false }
        )
    }
}

private val BookIdsSaver = Saver<Set<Int>, IntArray>(save = { it.toIntArray() }, restore = { it.toSet() })

/** 빈 배열 = 쪽 필터 없음. */
private val PageRangeSaver = Saver<IntRange?, IntArray>(
    save = { r -> r?.let { intArrayOf(it.first, it.last) } ?: IntArray(0) },
    restore = { a -> if (a.size == 2) a[0]..a[1] else null }
)

private fun bookFilterLabel(ids: Set<Int>, books: List<Book>): String = when (ids.size) {
    0 -> "책: 전체"
    1 -> books.firstOrNull { it.id in ids }?.title ?: "책 1권"
    else -> "책 ${ids.size}권"
}

private fun pageLabel(range: IntRange?): String = when {
    range == null -> "쪽: 전체"
    range.last == Int.MAX_VALUE -> "p.${range.first}~"
    range.first == 0 -> "~p.${range.last}"
    else -> "p.${range.first}–${range.last}"
}

private fun periodLabel(period: RecallPeriod, from: Long?, to: Long?, fmt: SimpleDateFormat): String = when (period) {
    RecallPeriod.ALL -> "기간: 전체"
    RecallPeriod.CUSTOM -> "${from?.let { fmt.format(Date(it)) } ?: "처음"}~${to?.let { fmt.format(Date(it)) } ?: "지금"}"
    else -> period.label
}

@Composable
private fun RecallHitCard(
    hit: RecallHit,
    photos: List<RecallPhoto>,
    expanded: Boolean,
    dateFormat: SimpleDateFormat,
    onToggle: () -> Unit,
    onOpenBook: () -> Unit
) {
    val highlightBg = MaterialTheme.colorScheme.primaryContainer
    val excerpt = remember(hit.excerpt, hit.highlight, highlightBg) {
        buildAnnotatedString {
            val hl = hit.highlight
            if (hl == null) {
                append(hit.excerpt)
            } else {
                append(hit.excerpt.substring(0, hl.first))
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, background = highlightBg)) { append(hit.excerpt.substring(hl)) }
                append(hit.excerpt.substring(hl.last + 1))
            }
        }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .testTag("phraserecall_result"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (expanded) {
                RecallPhotoPager(photos)
            } else {
                RecallThumbnail(photos.firstOrNull(), hasRows = photos.isNotEmpty())
            }
            if (expanded) {
                Text(text = "«${hit.selectedText}»", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    text = buildAnnotatedString { append("«"); append(excerpt); append("»") },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "${hit.bookTitle} · ${hit.author}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "기록한 날 ${dateFormat.format(Date(hit.createdAt))} · p.${hit.page}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                hit.matchKind?.let { kind ->
                    Text(
                        text = kind.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            if (hit.note.isNotBlank()) {
                Text(
                    text = "메모: ${hit.note}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (expanded) {
                TextButton(onClick = onOpenBook, modifier = Modifier.testTag("phraserecall_open_book")) {
                    Text("이 책 보기")
                }
            }
        }
    }
}

/** 접힌 카드: 맨 앞 사진 1장, 없으면 «사진 없음» 한 줄(기존 기록·dangling, 명세 §2.4). */
@Composable
private fun RecallThumbnail(photo: RecallPhoto?, hasRows: Boolean) {
    val file = photo?.file
    if (file != null) {
        AsyncImage(
            model = file,
            contentDescription = "기록 사진",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(6.dp))
                .testTag("phraserecall_thumbnail")
        )
    } else {
        NoPhotoLabel(if (hasRows) PHOTO_NOT_ON_DEVICE else "사진 없음")
    }
}

@Composable
private fun NoPhotoLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
        modifier = Modifier.testTag("phraserecall_no_photo")
    )
}

/** 펼친 카드(복기): N장을 넘겨 보고, 누르면 전체 화면에서 확대한다. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecallPhotoPager(photos: List<RecallPhoto>) {
    if (photos.isEmpty()) {
        NoPhotoLabel("사진 없음")
        return
    }
    var viewerStart by remember { mutableStateOf<Int?>(null) }
    val pager = rememberPagerState { photos.size }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        HorizontalPager(state = pager, modifier = Modifier
            .fillMaxSize()
            .testTag("phraserecall_photo_pager")) { page ->
            val file = photos[page].file
            if (file != null) {
                AsyncImage(
                    model = file,
                    contentDescription = "기록 사진 ${page + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { viewerStart = page }
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { NoPhotoLabel(PHOTO_NOT_ON_DEVICE) }
            }
        }
        if (photos.size > 1) {
            Text(
                text = "${pager.currentPage + 1} / ${photos.size}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
    viewerStart?.let { start -> RecallPhotoViewer(photos, start) { viewerStart = null } }
}

/** 전체 화면 보기. 두 손가락으로 확대하고, 확대 중에는 끌어서 옮긴다(확대가 아닐 때 한 손가락은 페이지 넘김). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecallPhotoViewer(photos: List<RecallPhoto>, start: Int, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var zoomed by remember { mutableStateOf(false) }
        val pager = rememberPagerState(initialPage = start) { photos.size }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("phraserecall_photo_viewer")
        ) {
            HorizontalPager(state = pager, userScrollEnabled = !zoomed, modifier = Modifier.fillMaxSize()) { page ->
                ZoomablePhoto(photos[page], isCurrent = page == pager.currentPage, onZoomChanged = { zoomed = it })
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Default.Close, contentDescription = "닫기", tint = Color.White)
            }
        }
    }
}

@Composable
private fun ZoomablePhoto(photo: RecallPhoto, isCurrent: Boolean, onZoomChanged: (Boolean) -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    // 넘어간 페이지의 확대는 풀어 둔다(돌아왔을 때 페이지 넘김이 막히지 않게).
    LaunchedEffect(isCurrent) {
        if (!isCurrent) {
            scale = 1f
            offset = Offset.Zero
            onZoomChanged(false)
        }
    }
    val file = photo.file
    if (file == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(PHOTO_NOT_ON_DEVICE, color = Color.White) }
        return
    }
    AsyncImage(
        model = file,
        contentDescription = "기록 사진 확대",
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1 || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            offset = if (scale > 1f) offset + event.calculatePan() else Offset.Zero
                            onZoomChanged(scale > 1f)
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
    )
}

@Composable
private fun PageRangeDialog(initial: IntRange?, onDismiss: () -> Unit, onConfirm: (IntRange?) -> Unit) {
    var fromText by remember { mutableStateOf(initial?.first?.toString() ?: "") }
    var toText by remember { mutableStateOf(initial?.last?.toString() ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("쪽 범위") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = fromText,
                    onValueChange = { fromText = it.filter(Char::isDigit).take(5) },
                    label = { Text("부터") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                Text("~")
                OutlinedTextField(
                    value = toText,
                    onValueChange = { toText = it.filter(Char::isDigit).take(5) },
                    label = { Text("까지") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = fromText.toIntOrNull()
                val b = toText.toIntOrNull()
                onConfirm(
                    when {
                        a == null && b == null -> null
                        else -> {
                            val lo = a ?: 0
                            val hi = b ?: Int.MAX_VALUE
                            if (lo <= hi) lo..hi else hi..lo
                        }
                    }
                )
            }) { Text("적용") }
        },
        dismissButton = {
            TextButton(onClick = { onConfirm(null) }) { Text("전체 쪽") }
        }
    )
}
