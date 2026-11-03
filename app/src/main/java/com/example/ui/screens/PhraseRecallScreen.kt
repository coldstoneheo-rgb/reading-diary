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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * 사진은 P4에서 붙는다. 계산은 [PhraseRecall.search] 순수 함수를 화면 `remember`로 부른다(ViewModel 상태 추가 0).
 */
@Composable
fun PhraseRecallScreen(
    viewModel: ReadingViewModel,
    initialQuery: String,
    modifier: Modifier = Modifier
) {
    val books by viewModel.books.collectAsState()
    val diaries by viewModel.diaries.collectAsState()
    val sharedWords by viewModel.sharedWords.collectAsState()
    PhraseRecallContent(
        books = books,
        diaries = diaries,
        sharedWords = sharedWords,
        initialQuery = initialQuery,
        onOpenBook = { bookId -> viewModel.navigateTo(com.example.ui.viewmodel.Screen.BookDetail(bookId)) },
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
    onOpenBook: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialQuery: String = "",
    /** 테스트가 필터 상태를 바로 그리기 위한 초기값. 기간이 들어 있으면 «직접 지정»으로 본다. */
    initialFilter: RecallFilter = RecallFilter()
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()) }

    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var bookIds by remember { mutableStateOf(initialFilter.bookIds) }
    var period by rememberSaveable {
        mutableStateOf(if (initialFilter.fromMillis != null || initialFilter.toMillis != null) RecallPeriod.CUSTOM else RecallPeriod.ALL)
    }
    var customFrom by remember { mutableStateOf(initialFilter.fromMillis) }
    var customTo by remember { mutableStateOf(initialFilter.toMillis) }
    var pageRange by remember { mutableStateOf(initialFilter.pageRange) }
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
                            expanded = expandedId == hit.diaryId,
                            dateFormat = dateFormat,
                            onToggle = { expandedId = if (expandedId == hit.diaryId) null else hit.diaryId },
                            onOpenBook = { onOpenBook(hit.bookId) }
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
