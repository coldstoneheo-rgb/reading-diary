package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ReadingStart
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.components.CoverPlaceholder
import com.example.data.Book
import com.example.data.Bookcase
import com.example.data.Diary
import com.example.data.photo.RecallPhoto
import com.example.ui.viewmodel.ReadingViewModel
import com.example.ui.viewmodel.Screen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    viewModel: ReadingViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val book by viewModel.activeBook.collectAsState()
    val sampleIds by viewModel.sampleBookIds.collectAsState()
    val diaries by viewModel.activeBookDiaries.collectAsState()
    val diariesLoadedFor by viewModel.activeBookDiariesLoadedFor.collectAsState()
    val bookcases by viewModel.bookcases.collectAsState()

    val diarySortNewestFirst by viewModel.diarySortNewestFirst.collectAsState()
    val diaryFontSize by viewModel.diaryFontSize.collectAsState()

    val sortedDiaries = remember(diaries, diarySortNewestFirst) {
        if (diarySortNewestFirst) {
            diaries.sortedByDescending { it.createdAt }
        } else {
            diaries.sortedBy { it.createdAt }
        }
    }

    var showMenu by remember { mutableStateOf(false) }
    var showDeleteAlert by remember { mutableStateOf(false) }
    var showBookcaseTransferDialog by remember { mutableStateOf(false) }
    var showPageDialog by remember { mutableStateOf(false) }
    val photosByDiary by viewModel.recallPhotos.collectAsState()

    // Backup state for slider page modification
    var sliderPage by remember { mutableStateOf(0f) }

    // Synchronize slider state when book detail loads
    LaunchedEffect(book) {
        book?.let {
            sliderPage = it.currentPage.toFloat()
        }
    }

    if (book == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val currentBook = book!!
    // «읽기 시작» 전에는 진척도 · 기록하기를 열지 않는다(HO-2026-1127 조건 4). 이미 있는 책의 옮김 규칙은 ReadingStart.isStarted.
    val started = ReadingStart.isStarted(currentBook, diaries.size)
    // 기록을 아직 받지 못했으면 시작 카드를 그리지 않는다(기록이 있는 옛 책에서 카드가 잠깐 떴다 사라지지 않게).
    val diariesLoaded = diariesLoadedFor == currentBook.id

    /** 시작 날짜 → 시각을 차례로 고른다. 옛 값(날짜만)은 그날 0시에서 시작한다. */
    fun editReadingStart() {
        val cal = java.util.Calendar.getInstance().apply {
            // 시작이 빈 책은 고를 수 있는 가장 늦은 시각(지금 · 완독일 그날 끝 중 이른 것)에서 연다 — 완독일이 지난 책에서 초기값이 상한 밖이 되지 않게.
            timeInMillis = ReadingStart.parse(currentBook.startDate) ?: ReadingStart.latestAllowed(System.currentTimeMillis(), currentBook.endDate)
        }
        val dialog = android.app.DatePickerDialog(context, { _, y, m, d ->
            cal.set(y, m, d)
            android.app.TimePickerDialog(context, { _, h, min ->
                cal.set(java.util.Calendar.HOUR_OF_DAY, h)
                cal.set(java.util.Calendar.MINUTE, min)
                viewModel.updateReadingStart(currentBook.id, cal.timeInMillis) { ok ->
                    if (!ok) Toast.makeText(context, "시작 시각은 지금이나 완독일보다 늦을 수 없어요.", Toast.LENGTH_SHORT).show()
                }
            }, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), true).show()
        }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH))
        // 고를 수 있는 마지막 날 = 오늘 · 완독일 중 이른 날(시각까지는 ViewModel이 다시 막는다).
        dialog.datePicker.maxDate = ReadingStart.latestAllowed(System.currentTimeMillis(), currentBook.endDate)
        dialog.show()
    }
    val currentBookcase = bookcases.find { it.id == currentBook.bookcaseId }?.name ?: "기본 책장"

    if (showDeleteAlert) {
        AlertDialog(
            onDismissRequest = { showDeleteAlert = false },
            title = { Text("도서 삭제", fontWeight = FontWeight.Bold) },
            text = { Text("'${currentBook.title}'을 내 목록에서 영구히 삭제할까요? 남겨 둔 모든 독서 기록도 함께 삭제됩니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAlert = false
                        viewModel.deleteBook(currentBook.id) {
                            viewModel.navigateTo(Screen.Dashboard)
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("삭제", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAlert = false }) {
                    Text("취소")
                }
            }
        )
    }

    if (showBookcaseTransferDialog) {
        AlertDialog(
            onDismissRequest = { showBookcaseTransferDialog = false },
            title = { Text("책장 이동", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("이 도서를 어느 책장으로 이동시킬까요?", modifier = Modifier.padding(bottom = 12.dp))
                    bookcases.forEach { bookcase ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showBookcaseTransferDialog = false
                                    // 책장만 옮긴다(상태 · 시작 값은 그대로 — 옮기는 것만으로 «읽기 시작»이 생기지 않게).
                                    viewModel.moveBookToBookcase(currentBook.id, bookcase.id) {
                                        Toast.makeText(context, "'${bookcase.name}' 책장으로 이동했습니다.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (bookcase.id == currentBook.bookcaseId) Icons.Default.FolderSpecial else Icons.Default.Folder,
                                contentDescription = null,
                                tint = if (bookcase.id == currentBook.bookcaseId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Text(
                                bookcase.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (bookcase.id == currentBook.bookcaseId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (bookcase.id == currentBook.bookcaseId) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBookcaseTransferDialog = false }) {
                    Text("닫기")
                }
            }
        )
    }

    // 토큰(REDESIGN §3): 바깥 여백 12dp · 같은 묶음 사이 8dp · 다른 묶음 사이 16dp · 카드 안 12dp.
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentBook.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("book_detail_title")
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }, modifier = Modifier.size(48.dp)) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    Box {
                        IconAction(
                            label = "더 보기",
                            icon = Icons.Default.MoreVert,
                            onClick = { showMenu = true },
                            modifier = Modifier.testTag("more_options_button")
                        )

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("도서 정보 직접 수정") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.navigateTo(Screen.AddEditBook(currentBook.id))
                                },
                                modifier = Modifier.testTag("edit_book_option")
                            )
                            DropdownMenuItem(
                                text = { Text("책장 카테고리 이동") },
                                leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showBookcaseTransferDialog = true
                                },
                                modifier = Modifier.testTag("transfer_bookcase_option")
                            )
                            DropdownMenuItem(
                                text = { Text("도서 삭제") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showDeleteAlert = true
                                },
                                modifier = Modifier.testTag("delete_book_option")
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            // 시작 전에는 기록하기를 열지 않는다(«읽기 시작» 카드만).
            if (started) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.navigateTo(Screen.AddDiary(currentBook.id)) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("기록하기", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("add_diary_button")
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .testTag("book_detail_list"),
            // 아래 여백 = FAB(56dp) + FAB 바깥 16dp + 12dp — 마지막 기록이 FAB에 가리지 않게.
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = if (started) 84.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 작은 머리: 표지 64×92 | 저자 · 책장 · 시작(✎)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().testTag("book_detail_header"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(64.dp)
                            .height(92.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        if (currentBook.cleanCoverUrl.isEmpty()) {
                            CoverPlaceholder(title = currentBook.title, modifier = Modifier.fillMaxSize())
                        } else {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(currentBook.cleanCoverUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = currentBook.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        if (currentBook.id in sampleIds) {
                            SampleBadge(modifier = Modifier.padding(bottom = 4.dp))
                        }
                        Text(
                            text = "${currentBook.author} · $currentBookcase",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("book_detail_author")
                        )
                        // 시작 · 기간 줄. 시작한 책이면 ✎(48dp · 낭독 이름 · 툴팁)로 날짜 · 시각을 고친다 — 시작 값이 빈 옛 책은 여기서 처음 적는다.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = ReadingStart.periodLabel(currentBook, started),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f, fill = false).testTag("reading_start_text")
                            )
                            if (started) {
                                IconAction(
                                    label = if (currentBook.startDate.isBlank()) "시작 시각 적기" else "시작 시각 고치기",
                                    icon = Icons.Outlined.Edit,
                                    onClick = { editReadingStart() },
                                    iconSize = 20.dp,
                                    modifier = Modifier.testTag("reading_start_edit")
                                )
                            }
                        }
                    }
                }
            }

            if (!started && diariesLoaded) {
                item {
                    StartReadingCard(onStart = { viewModel.startReading(currentBook.id) })
                }
            }

            // 한 줄 진척도: «어디까지?» 슬라이더 + «96 / 320 쪽»(누르면 쪽 수를 직접 넣는다 — 보정 2)
            if (started) item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "어디까지?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // 슬라이더의 의미 노드는 손잡이 대상만큼 제 자리 밖으로 나온다 — 자리(겹침 단언)는 감싸는 칸으로 잰다.
                        Box(modifier = Modifier.weight(1f).testTag("progress_slider_area")) {
                        Slider(
                            value = sliderPage,
                            onValueChange = { sliderPage = it },
                            onValueChangeFinished = {
                                viewModel.updateBookProgress(currentBook.id, sliderPage.toInt())
                            },
                            valueRange = 0f..currentBook.totalPages.toFloat(),
                            steps = if (currentBook.totalPages > 1) currentBook.totalPages - 1 else 0,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                activeTickColor = Color.Transparent,
                                inactiveTickColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = "어디까지 읽었나" }
                                .testTag("progress_slider")
                        )
                        }
                        Text(
                            text = "${sliderPage.toInt()} / ${currentBook.totalPages} 쪽",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClickLabel = "쪽 수 직접 넣기") { showPageDialog = true }
                                .wrapContentHeight(Alignment.CenterVertically)
                                .padding(horizontal = 4.dp)
                                .testTag("progress_page_text")
                        )
                    }
                    if (showPageDialog) {
                        PageInputRow(
                            totalPages = currentBook.totalPages,
                            initialPage = sliderPage.toInt(),
                            onDismiss = { showPageDialog = false },
                            onSave = { page ->
                                showPageDialog = false
                                sliderPage = page.toFloat()
                                viewModel.updateBookProgress(currentBook.id, page)
                            }
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            if (started && (currentBook.status == "COMPLETED" || sliderPage.toInt() >= currentBook.totalPages)) item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "나의 도서 평가",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val bookRating = currentBook.rating
                        repeat(5) { index ->
                            val starIndex = index + 1
                            val isSelected = starIndex <= bookRating
                            IconButton(
                                onClick = {
                                    viewModel.updateBookRating(currentBook.id, starIndex) {
                                        Toast.makeText(context, "${starIndex}점으로 별점을 반영했습니다.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "별 ${starIndex}개",
                                    tint = if (isSelected) Color(0xFFFFD54F) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = if (currentBook.rating > 0) "${currentBook.rating}점 주었습니다" else "별을 눌러 평점을 남겨 보세요",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 기록 머리(다른 묶음 → 위 16dp = 사이 8 + 8)
            item {
                Text(
                    text = "기록 ${diaries.size}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp).testTag("diary_list_header")
                )
            }

            if (diaries.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Article,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                        )
                        Text(
                            text = "아직 남긴 기록이 없어요.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (started) "책 페이지를 찍어 구절이 있는 부분을 잘라 내면 글자를 읽어 와 기록을 적을 수 있어요."
                                else "«읽기 시작»을 누르면 기록을 적을 수 있어요.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(sortedDiaries, key = { it.id }) { diary ->
                    DiaryItemCard(
                        diary = diary,
                        fontSize = diaryFontSize,
                        photos = photosByDiary[diary.id].orEmpty(),
                        onDeleteClick = {
                            viewModel.deleteDiary(diary.id)
                            Toast.makeText(context, "기록이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                        },
                        onEditClick = {
                            viewModel.navigateTo(Screen.AddDiary(currentBook.id, diary.id))
                        }
                    )
                }
            }
        }
    }
}

/**
 * «96 / 320 쪽»을 누르면 진척도 줄 아래에 여는 쪽 수 입력(REDESIGN §7-1 보정 2 — 슬라이더만으로는 1쪽 단위가 맞지 않는다).
 * 대화상자가 아니라 줄 안에서 연다: 입력칸이 든 대화상자는 닫을 때 시험 환경에서 화면이 멈췄다(원인 미확정 · 줄 안 입력은 기록하기 화면과 같은 꼴).
 */
@Composable
private fun PageInputRow(totalPages: Int, initialPage: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by remember { mutableStateOf(initialPage.toString()) }
    val page = text.toIntOrNull()
    val valid = page != null && page in 0..totalPages
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { v -> text = v.filter { it.isDigit() }.take(6) },
            singleLine = true,
            label = { Text("읽은 쪽 (0 ~ $totalPages)") },
            suffix = { Text("/ $totalPages 쪽") },
            isError = !valid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (valid) onSave(page!!) }),
            modifier = Modifier.weight(1f).testTag("progress_page_input")
        )
        TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp).testTag("progress_page_cancel")) { Text("취소") }
        TextButton(onClick = { onSave(page!!) }, enabled = valid, modifier = Modifier.heightIn(min = 48.dp).testTag("progress_page_save")) {
            Text("저장", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DiaryItemCard(
    diary: Diary,
    fontSize: Float,
    onDeleteClick: () -> Unit,
    onEditClick: () -> Unit,
    photos: List<RecallPhoto> = emptyList()
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("기록 삭제") },
            text = { Text("이 다이어리와 발췌된 텍스트를 영구적으로 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteClick()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("삭제", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("취소")
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("diary_item_${diary.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        // 카드 안 12dp 하나. 머리 줄은 아이콘 대상(48dp)이 위 · 오른쪽 여백을 대신한다.
        Column(modifier = Modifier.padding(start = 12.dp, bottom = 12.dp)) {
            // 머리: 날짜 시각 · p.쪽 | 고치기 · 지우기(48dp 대상 · 그림 20dp · 툴팁)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val timeString = try {
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(diary.createdAt))
                } catch (e: Exception) {
                    ""
                }
                Text(
                    text = if (timeString.isEmpty()) "p.${diary.page}" else "$timeString · p.${diary.page}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).testTag("diary_meta_${diary.id}")
                )
                IconAction(
                    label = "기록 고치기",
                    icon = Icons.Outlined.Edit,
                    onClick = onEditClick,
                    iconSize = 20.dp,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("diary_edit_${diary.id}")
                )
                IconAction(
                    label = "기록 지우기",
                    icon = Icons.Outlined.Delete,
                    onClick = { showDeleteConfirm = true },
                    iconSize = 20.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("diary_delete_${diary.id}")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // 인용 2줄 · 메모 1줄(나머지는 고치기 화면에서 본다)
                    Text(
                        text = diary.selectedText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Medium,
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * 1.4f).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("diary_quote_${diary.id}")
                    )
                    if (diary.notes.isNotEmpty()) {
                        Text(
                            text = diary.notes,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = fontSize.sp,
                                lineHeight = (fontSize * 1.4f).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("diary_note_${diary.id}")
                        )
                    }
                }
                photos.firstOrNull()?.file?.let { file ->
                    Box {
                        AsyncImage(
                            model = file,
                            contentDescription = "기록 사진",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .testTag("diary_photo_${diary.id}")
                        )
                        if (photos.size > 1) {
                            Text(
                                text = "+${photos.size - 1}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .clip(RoundedCornerShape(topStart = 6.dp, bottomEnd = 6.dp))
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 시작 전 카드: 누르면 지금 날짜 · 시각이 시작 값으로 저장되고 진척도 · 기록하기가 열린다. */
@Composable
private fun StartReadingCard(onStart: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("start_reading_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "아직 시작 전이에요",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "누르면 지금 날짜와 시각이 시작으로 기록되고, 읽은 쪽 · 메모 · 사진 기록이 열려요. 시작 시각은 나중에 고칠 수 있어요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("start_reading_button")) {
                Text("읽기 시작", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}
