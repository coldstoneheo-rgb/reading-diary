package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.Book
import com.example.data.Bookcase
import com.example.data.Diary
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
    val diaries by viewModel.activeBookDiaries.collectAsState()
    val bookcases by viewModel.bookcases.collectAsState()

    var showMenu by remember { mutableStateOf(false) }
    var showDeleteAlert by remember { mutableStateOf(false) }
    var showBookcaseTransferDialog by remember { mutableStateOf(false) }

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
    val currentBookcase = bookcases.find { it.id == currentBook.bookcaseId }?.name ?: "기본 책장"

    if (showDeleteAlert) {
        AlertDialog(
            onDismissRequest = { showDeleteAlert = false },
            title = { Text("도서 삭제", fontWeight = FontWeight.Bold) },
            text = { Text("'${currentBook.title}'을 내 목록에서 영구히 삭제할까요? 기록해 둔 모든 독서 다이어리도 함께 삭제됩니다.") },
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
                                    viewModel.saveBook(
                                        id = currentBook.id,
                                        title = currentBook.title,
                                        author = currentBook.author,
                                        totalPages = currentBook.totalPages,
                                        currentPage = currentBook.currentPage,
                                        coverUrl = currentBook.coverUrl,
                                        bookcaseId = bookcase.id,
                                        status = currentBook.status
                                    ) {
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("도서 상세 정보", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(Screen.Dashboard) }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.testTag("more_options_button")
                    ) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "더보기")
                    }

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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Book Information Card
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Cover Thumbnail Container
                    Box(
                        modifier = Modifier
                            .width(110.dp)
                            .height(160.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
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

                    // Metadata Text Column
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentBook.title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("book_detail_title")
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "저자: ${currentBook.author}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Category Badge
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = currentBookcase,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }

                        // Dates started/ended
                        val datesText = when {
                            currentBook.status == "COMPLETED" -> {
                                "독서 기간: ${currentBook.startDate} ~ ${currentBook.endDate ?: "완료"}"
                            }
                            currentBook.startDate.isNotEmpty() -> {
                                "독서 시작: ${currentBook.startDate}"
                            }
                            else -> "아직 읽지 않은 도서"
                        }
                        Text(
                            text = datesText,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            // Slider Reading Progress Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "실시간 독서 진척도",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "${(currentBook.progressPercent * 100).toInt()}% 완료",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Progress Slider
                        Slider(
                            value = sliderPage,
                            onValueChange = { sliderPage = it },
                            valueRange = 0f..currentBook.totalPages.toFloat(),
                            steps = if (currentBook.totalPages > 1) currentBook.totalPages - 1 else 0,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("progress_slider")
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "0 쪽",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                            Text(
                                text = "현재 ${sliderPage.toInt()} / ${currentBook.totalPages} 쪽",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "${currentBook.totalPages} 쪽",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Save changed progress button
                        val isProgressChanged = sliderPage.toInt() != currentBook.currentPage
                        Button(
                            onClick = {
                                viewModel.updateBookProgress(currentBook.id, sliderPage.toInt()) {
                                    Toast.makeText(context, "독서 진척도가 업데이트 되었습니다.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = isProgressChanged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_progress_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("진척도 상태 저장")
                        }
                    }
                }
            }

            // Book Diary List Header with Plus action
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.RateReview,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "독서 펜기록 & 다이어리 (${diaries.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Button(
                        onClick = { viewModel.navigateTo(Screen.AddDiary(currentBook.id)) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("add_diary_button")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("기록하기", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }

            // List of associated Diary elements
            if (diaries.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Article,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "아직 기록된 다이어리가 없습니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "책의 영감 깊은 밑줄 구절을 카메라 OCR로 편리하게 추출해 다이어리를 적어 보세요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(diaries, key = { it.id }) { diary ->
                    DiaryItemCard(
                        diary = diary,
                        onDeleteClick = {
                            viewModel.deleteDiary(diary.id, currentBook.id)
                            Toast.makeText(context, "기록이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DiaryItemCard(
    diary: Diary,
    onDeleteClick: () -> Unit
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
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Page and Delete Option
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "p. ${diary.page}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete entry",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Subtitle: Underlined Extracted Paragraph
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ) {
                Row(modifier = Modifier.padding(12.dp)) {
                    Icon(
                        imageVector = Icons.Default.FormatQuote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(24.dp)
                            .padding(end = 6.dp)
                    )
                    Text(
                        text = diary.selectedText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 20.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Body: User's personal diary thoughts
            if (diary.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = diary.notes,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Footer: Timestamp
            val timeString = try {
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(diary.createdAt))
            } catch (e: Exception) {
                ""
            }
            Text(
                text = timeString,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End
            )
        }
    }
}
