package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Book
import com.example.data.Diary
import com.example.data.knowledge.QuoteSource
import com.example.data.knowledge.SharedWord
import com.example.ui.viewmodel.ReadingViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 기억 서랍 — 책 사이의 연결(ADR-003).
 * "연결"은 서로 다른 책 2권 이상의 기록에 함께 나온 단어다(Q1). 계산은 [ReadingViewModel.sharedWords]가 DB 변경 시에만 한다(Q4).
 * 단어는 형태소 분석 없는 휴리스틱으로 뽑으므로 "함께 나온 단어"라 부르고 항상 원문 구절과 함께 보인다(Q2).
 */
@Composable
fun KnowledgeDrawerScreen(
    viewModel: ReadingViewModel,
    modifier: Modifier = Modifier
) {
    val books by viewModel.books.collectAsState()
    val diaries by viewModel.diaries.collectAsState()
    val sharedWords by viewModel.sharedWords.collectAsState()
    KnowledgeDrawerContent(
        books = books,
        diaries = diaries,
        sharedWords = sharedWords,
        onBack = { viewModel.navigateBack() },
        modifier = modifier
    )
}

/** 검색어가 기록의 어디에 맞았는지. 고정 점수 대신 근거를 보여준다(ADR-003 Q5). */
enum class MatchKind(val label: String) {
    TITLE_AND_TEXT("제목·구절 일치"),
    TEXT("구절 일치"),
    NOTES("메모 일치"),
    TITLE("제목 일치")
}

/**
 * 순수 함수: 검색어가 비면 null(전체 표시), 아니면 일치 종류. 어디에도 안 맞으면 [MatchKind]가 아니라 결과에서 제외된다.
 * 조합 라벨은 "제목·구절"만 둔다(가장 흔한 조합). 제목+메모처럼 다른 조합은 우선순위가 높은 한 가지만 표시한다 — 알려진 단순화.
 */
internal fun matchKind(diary: Diary, bookTitle: String, query: String): MatchKind? {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return null
    val inText = diary.selectedText.lowercase().contains(q)
    val inNotes = diary.notes.lowercase().contains(q)
    val inTitle = bookTitle.lowercase().contains(q)
    return when {
        inTitle && inText -> MatchKind.TITLE_AND_TEXT
        inText -> MatchKind.TEXT
        inNotes -> MatchKind.NOTES
        inTitle -> MatchKind.TITLE
        else -> null
    }
}

private const val CONNECTIONS_COLLAPSED = 3

/** ViewModel 없이 그릴 수 있는 본문. 스크린샷 테스트가 고정 데이터로 이 함수를 그린다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeDrawerContent(
    books: List<Book>,
    diaries: List<Diary>,
    sharedWords: List<SharedWord>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val titleById = remember(books) { books.associate { it.id to it.title } }
    val dateFormat = remember { SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()) }

    var searchKeyword by rememberSaveable { mutableStateOf("") }
    var showAllConnections by rememberSaveable { mutableStateOf(false) }
    val query = searchKeyword.trim()

    val results: List<Pair<Diary, MatchKind?>> = remember(query, diaries, titleById) {
        if (query.isEmpty()) {
            diaries.map { it to null }
        } else {
            diaries.mapNotNull { d ->
                val kind = matchKind(d, titleById[d.bookId] ?: "", query) ?: return@mapNotNull null
                d to kind
            }
        }
    }
    val visibleConnections = if (showAllConnections) sharedWords else sharedWords.take(CONNECTIONS_COLLAPSED)
    val booksWithRecords = remember(diaries) { diaries.map { it.bookId }.toSet().size }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("기억 서랍", fontWeight = FontWeight.Bold)
                        Text(
                            "책 사이의 연결",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("knowledgedrawer_back_button")
                    ) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "뒤로 가기")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (diaries.isEmpty()) {
                                Toast.makeText(context, "내보낼 기록이 없습니다.", Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }
                            clipboardManager.setText(AnnotatedString(buildMarkdown(books, diaries)))
                            Toast.makeText(context, "기록을 마크다운으로 복사했습니다. 메모 앱에 붙여넣으세요.", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.testTag("knowledgedrawer_copy_markdown")
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "마크다운으로 복사", tint = MaterialTheme.colorScheme.primary)
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 가치 제안 배너(ADR-003 Q9)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoStories,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "읽은 책의 밑줄이 서로 이어질 때, 새로운 생각이 태어납니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        lineHeight = 15.sp
                    )
                }
            }

            // 내 기록 검색 + 함께 나온 단어 칩(누르면 검색어로)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "내 기록 검색",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = searchKeyword,
                    onValueChange = { searchKeyword = it },
                    placeholder = { Text("구절, 메모, 책 제목으로 찾기") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchKeyword.isNotEmpty()) {
                            IconButton(onClick = { searchKeyword = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "지우기")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("knowledgedrawer_search_field"),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
                if (sharedWords.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("knowledgedrawer_word_chips"),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(sharedWords, key = { it.word }) { sw ->
                            val chipIcon: (@Composable () -> Unit)? = if (sw.userTagged) {
                                { Icon(Icons.Default.Tag, contentDescription = "내 태그", modifier = Modifier.size(14.dp)) }
                            } else null
                            SuggestionChip(
                                onClick = { searchKeyword = sw.word },
                                label = { Text("#${sw.word}") },
                                icon = chipIcon
                            )
                        }
                    }
                }
            }

            if (results.isEmpty()) {
                Text(
                    text = if (query.isEmpty()) "기록 0개" else "일치하는 기록 0개",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
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
                            text = if (diaries.isEmpty()) "아직 기록이 없습니다. 책 페이지를 찍어 밑줄 구절을 남겨 보세요."
                            else "일치하는 기록이 없습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("knowledgedrawer_results_list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 연결 발견: 검색 중이 아닐 때만. 서로 다른 책에 함께 나온 단어(ADR-003 Q1·Q3 1단계)
                    if (query.isEmpty()) {
                        item(key = "connections_header") {
                            SectionHeader(
                                title = if (sharedWords.isEmpty()) "연결 발견" else "연결 발견 ${sharedWords.size}개",
                                subtitle = "서로 다른 책의 기록에 함께 나온 단어"
                            )
                        }
                        if (sharedWords.isEmpty()) {
                            item(key = "connections_empty") {
                                Text(
                                    text = if (booksWithRecords < 2) "다른 책에서 구절을 한 개 더 모으면 연결이 나타납니다."
                                    else "아직 두 책에 함께 나온 단어가 없습니다. 메모에 #태그를 붙이면 바로 연결됩니다.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    modifier = Modifier.testTag("knowledgedrawer_connections_empty")
                                )
                            }
                        } else {
                            items(visibleConnections, key = { "conn_" + it.word }) { sw ->
                                ConnectionCard(sharedWord = sw)
                            }
                            if (sharedWords.size > CONNECTIONS_COLLAPSED) {
                                item(key = "connections_toggle") {
                                    TextButton(
                                        onClick = { showAllConnections = !showAllConnections },
                                        modifier = Modifier.testTag("knowledgedrawer_connections_toggle")
                                    ) {
                                        Text(if (showAllConnections) "접기" else "연결 ${sharedWords.size - CONNECTIONS_COLLAPSED}개 더 보기")
                                    }
                                }
                            }
                        }
                        item(key = "records_header") {
                            SectionHeader(title = "기록 ${results.size}개", subtitle = null)
                        }
                    } else {
                        item(key = "records_header") {
                            SectionHeader(title = "일치하는 기록 ${results.size}개", subtitle = null)
                        }
                    }
                    items(results, key = { "diary_" + it.first.id }) { (diary, kind) ->
                        DiaryResultCard(diary = diary, bookTitle = titleById[diary.bookId], matchKind = kind, dateFormat = dateFormat)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String?) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

/**
 * 연결 카드: 단어 하나와 그 단어가 나온 서로 다른 책의 구절들.
 * 접힌 상태에서는 책마다 첫 구절만, 펼치면 전부. 원문이 항상 보이므로 휴리스틱 오탐은 사용자 눈에 곧 걸러진다(ADR-003 Q2).
 */
@Composable
private fun ConnectionCard(sharedWord: SharedWord) {
    var expanded by rememberSaveable(sharedWord.word) { mutableStateOf(false) }
    val quotes = remember(sharedWord, expanded) {
        if (expanded) sharedWord.quotes else sharedWord.quotes.distinctBy { it.bookId }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = if (expanded) "구절 접기" else "구절 모두 보기") { expanded = !expanded }
            .testTag("knowledgedrawer_connection_${sharedWord.word}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "#${sharedWord.word}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (sharedWord.userTagged) {
                    Text(
                        text = "내 태그",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "책 ${sharedWord.bookCount}권 · 구절 ${sharedWord.quotes.size}개",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            quotes.forEach { q ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "${q.bookTitle} p.${q.page}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        modifier = Modifier.width(96.dp)
                    )
                    // 단어가 실제로 들어 있는 원문. 메모·태그에서 나온 단어는 메모를 보여 근거가 비지 않게 한다(ADR-003 Q2).
                    Text(
                        text = if (q.source == QuoteSource.TEXT) q.text else "메모 · ${q.text}",
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = if (q.source == QuoteSource.TEXT) FontStyle.Italic else FontStyle.Normal),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (!expanded && sharedWord.quotes.size > quotes.size) {
                Text(
                    text = "구절 ${sharedWord.quotes.size - quotes.size}개 더 보기",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun DiaryResultCard(diary: Diary, bookTitle: String?, matchKind: MatchKind?, dateFormat: SimpleDateFormat) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = bookTitle ?: "책 정보 없음",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (matchKind != null) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = matchKind.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.FormatQuote,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = diary.selectedText,
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (diary.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "나의 생각: ${diary.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "p.${diary.page} • " + dateFormat.format(Date(diary.createdAt)),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End
            )
        }
    }
}

/** 기록 전체를 마크다운으로. `[[책 제목]]`은 범용 위키링크 문법이다(ADR-003 Q6). */
internal fun buildMarkdown(books: List<Book>, diaries: List<Diary>): String {
    val sb = StringBuilder()
    sb.append("# 나만의 기억 서랍\n\n")
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    diaries.forEach { d ->
        val b = books.find { it.id == d.bookId }
        sb.append("## [[${b?.title ?: "독서기록"}]] - p.${d.page}\n")
        sb.append("- 저자: ${b?.author ?: "미상"}\n")
        sb.append("- 발췌:\n  > ${d.selectedText}\n")
        if (d.notes.isNotEmpty()) sb.append("- 나의 생각:\n  ${d.notes}\n")
        sb.append("- 기록 시각: ${fmt.format(Date(d.createdAt))}\n\n")
    }
    return sb.toString()
}
