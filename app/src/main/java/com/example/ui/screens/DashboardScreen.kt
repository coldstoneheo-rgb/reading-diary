package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.Book
import com.example.data.Bookcase
import com.example.ui.viewmodel.ReadingViewModel
import com.example.ui.viewmodel.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: ReadingViewModel,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val books by viewModel.filteredBooks.collectAsState()
    val bookcases by viewModel.bookcases.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val selectedBookcaseId by viewModel.selectedBookcaseId.collectAsState()

    val currentBookcaseName = bookcases.find { it.id == selectedBookcaseId }?.name ?: "전체 내역"

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "독서 다이어리",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        Text(
                            text = currentBookcaseName,
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = MaterialTheme.colorScheme.secondary
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onMenuClick,
                        modifier = Modifier.testTag("drawer_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    if (selectedBookcaseId != null) {
                        IconButton(onClick = { viewModel.selectedBookcaseId.value = null }) {
                            Icon(
                                imageVector = Icons.Default.FilterListOff,
                                contentDescription = "Clear Category Base Filter",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.Settings) },
                        modifier = Modifier.testTag("dashboard_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "설정",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.navigateTo(Screen.AddEditBook()) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("add_book_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "책 등록")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Stats Indicator Row
            DashboardStatsRow(books = books)

            Spacer(modifier = Modifier.height(8.dp))

            // State Category Selector (Chips)
            FilterChipRow(
                currentFilter = selectedFilter,
                onFilterSelected = { viewModel.selectedFilter.value = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Grid of Books
            if (books.isEmpty()) {
                EmptyStateView(
                    filter = selectedFilter,
                    bookcaseName = currentBookcaseName,
                    onAddClick = { viewModel.navigateTo(Screen.AddEditBook()) }
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("books_grid")
                ) {
                    items(books, key = { it.id }) { book ->
                        BookCardItem(
                            book = book,
                            onClick = { viewModel.navigateTo(Screen.BookDetail(book.id)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardStatsRow(books: List<Book>) {
    val total = books.size
    val readingCount = books.count { it.status == "READING" }
    val completedCount = books.count { it.status == "COMPLETED" }
    val toReadCount = books.count { it.status == "TO_READ" }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatItem(label = "전체", count = total, icon = Icons.Default.MenuBook, color = MaterialTheme.colorScheme.primary)
            Divider(modifier = Modifier.height(30.dp).width(1.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            StatItem(label = "읽는 중", count = readingCount, icon = Icons.Default.PlayArrow, color = MaterialTheme.colorScheme.secondary)
            Divider(modifier = Modifier.height(30.dp).width(1.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            StatItem(label = "완독", count = completedCount, icon = Icons.Default.CheckCircleOutline, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun StatItem(label: String, count: Int, imageVector: androidx.compose.ui.graphics.vector.ImageVector? = null, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = color)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = count.toString(), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = color)
    }
}

@Composable
fun FilterChipRow(
    currentFilter: String,
    onFilterSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val filters = listOf(
            Triple("ALL", "전체 책", Icons.Outlined.FormatListBulleted),
            Triple("READING", "읽는 중", Icons.Outlined.ChromeReaderMode),
            Triple("TO_READ", "읽을 책", Icons.Outlined.BookmarkBorder),
            Triple("COMPLETED", "읽은 책", Icons.Outlined.TaskAlt)
        )

        filters.forEach { (tag, label, icon) ->
            val isSelected = currentFilter == tag
            FilterChip(
                selected = isSelected,
                onClick = { onFilterSelected(tag) },
                label = { 
                    Text(
                        text = label, 
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    ) 
                },
                leadingIcon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        modifier = Modifier.size(14.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    iconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("filter_chip_$tag")
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookCardItem(
    book: Book,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(12.dp),
                ambientColor = Color.Black.copy(alpha = 0.05f),
                spotColor = Color.Black.copy(alpha = 0.08f)
            )
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick
            )
            .testTag("book_card_${book.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(book.cleanCoverUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = book.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Banner indicating status tag
                val badgeColor = when (book.status) {
                    "READING" -> MaterialTheme.colorScheme.primary
                    "COMPLETED" -> Color(0xFF4CAF50)
                    else -> MaterialTheme.colorScheme.secondary
                }
                val badgeText = when (book.status) {
                    "READING" -> "읽는 중"
                    "COMPLETED" -> "완독"
                    else -> "대기"
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeColor)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (book.status == "READING" || book.currentPage > 0) {
                    // Reading Progress Visual Indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinearProgressIndicator(
                            progress = book.progressPercent,
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${(book.progressPercent * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${book.currentPage} / ${book.totalPages} 쪽",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                } else if (book.status == "COMPLETED") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFF4CAF50)
                        )
                        Text(
                            text = "전체 ${book.totalPages}쪽 읽음",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        )
                    }
                } else {
                    Text(
                        text = "대기 (${book.totalPages}쪽 분량)",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(
    filter: String,
    bookcaseName: String,
    onAddClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoStories,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            val message = when (filter) {
                "READING" -> "현재 읽는 중인 책이 없습니다."
                "TO_READ" -> "다음에 읽을 대기 목록이 비어 있습니다."
                "COMPLETED" -> "아직 완독한 도서가 없습니다."
                else -> "'$bookcaseName' 책장에 등록된 책이 없습니다."
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "새로운 도서를 추가해 독서의 발자취를 남겨보세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onAddClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("첫 도서 등록하기")
            }
        }
    }
}
